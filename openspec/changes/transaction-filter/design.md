## Context

Trans and Stats read different shapes from `TransactionRepository`. Trans loads one month of rows with `observeInRange` and folds the header, the day subtotals and the Calendar from that one list in `TransactionListAssembly.kt`, so the three can never disagree. Stats reads SQL aggregates (`observePeriodTotals`, `observeCategoryTotals`) because, as `StatsAssembly.kt` records, an annual view must not load a year of rows to total them. Every aggregate in `TxnDao` already excludes TRANSFER and ADJUSTMENT, and `TxnType.countsAsFlow` is the one place that rule is written in Kotlin. `DateRange.ofWeek` defines weeks as Monday to Sunday, and the Calendar grid's `leadingBlanks` follows it. `observeDailyTotals` already buckets days in the caller's zone. User choices live in each screen's ViewModel `Selection`; the only cross-screen setting today, the theme, lives in `AppearanceStore`, built once in `AppContainer`.

Scale: 1,200 transactions over a year, about 1,500 a year, around 20 categories and 4 to 6 accounts.

## Goals / Non-Goals

**Goals:**
- One filter, set from either tab, that every figure on Trans and Stats respects, with no figure ever computed from a differently filtered set than the one beside it.
- One implementation of the filter rule, checked by the compiler where it can be.
- A filtered figure that can never be mistaken for a whole one.
- Monthly and week figures that agree with what Stats shows for the same month and week.

**Non-Goals:**
- Filtering by subcategory. Pemberian's split (gap 1) needs it eventually; it is a separate change once subcategories are actually filled in.
- Saved or named filters, and a filter that survives a restart.
- Full-text search infrastructure (FTS tables), search over category or account names.
- Budget caps (gap 5).

## Decisions

**The filter model is a domain data class with nullable sets.** `domain/TransactionFilter(incomeCategoryIds: Set<Long>?, expenseCategoryIds: Set<Long>?, accountIds: Set<Long>?)`, where null means "All" and an empty set means "none". `ALL` is the constant with three nulls; `isActive` is true when any set is non-null. "All" as null rather than as the full set of current ids is what lets archived and later-created items stay included, as the spec requires. Normalising "every listed item ticked" back to null is a pure function in the sheet's assembly, not in the model.
- Rejected: a sealed `All | Only(ids)` type per tab. It says the same thing in three more types; KISS prefers the nullable set with one doc comment.

**The filter lives in one process-scoped holder in `AppContainer`, shared by Trans, Stats and search.** `ui/filter/SharedFilter` is a plain class wrapping a `MutableStateFlow<TransactionFilter>` with `set` and `clear`. `AppContainer` builds it once, next to `appearance`, and the ViewModel factories pass it to `TransactionListViewModel`, `StatsViewModel`, `SearchViewModel` and `FilterSheetViewModel`. Each ViewModel combines `SharedFilter.state` into its query key exactly as it already combines its own `Selection`. The ViewModels still hold only their own choices (month, tab, side, drill); the filter is one more user choice, held in one place because two screens read it. `AppearanceStore` is the precedent for a choice owned by the container.
- Rejected: a filter per tab. The owner asked for one filter over both, and two independent filters double the chances of reading a filtered figure as a whole one.
- Rejected: an activity-scoped `FilterViewModel`. Another ViewModel cannot be a constructor argument of `TransactionListViewModel`, so the filter would have to travel down through composables and back up as an event into each screen's ViewModel, which then keeps its own copy. That is two copies of one choice, kept in step by UI wiring.
- Rejected: a nav-graph-scoped ViewModel. Same copying problem, and the tabs are sibling destinations with `saveState`, not one nested graph.
- Rejected: `SavedStateHandle` in each ViewModel. Survives process death, but again two copies.

**The filter is not persisted.** A new process starts at `ALL`. A finance app reopened days later and showing filtered totals is exactly the misreading this change must prevent, and the "Filtered" bar is easy to scroll past on a screen the user thinks they know. Rotation and tab switches keep it, because the holder outlives every ViewModel.
- Rejected: SharedPreferences or DataStore persistence. It is little code, but its only benefit is not re-ticking a few boxes, and its cost is the risk above.

**The filter reaches the data in SQL, through one shared `WHERE` fragment.** `TxnDao` declares `const val TXN_FILTER_WHERE` and every filtered `@Query` concatenates it (`"... WHERE occurredAtEpochSec >= :startSec AND occurredAtEpochSec < :endSec " + TXN_FILTER_WHERE + " ..."`). Kotlin accepts `const val` concatenation in annotation arguments, so Room still verifies each full query at compile time. The fragment:

```sql
AND (:allAccounts OR accountId IN (:accountIds) OR toAccountId IN (:accountIds))
AND (    (type = 'INCOME'  AND (:allIncome  OR categoryId IN (:incomeIds)))
      OR (type = 'EXPENSE' AND (:allExpense OR categoryId IN (:expenseIds)))
      OR (type IN ('TRANSFER', 'ADJUSTMENT') AND :allIncome AND :allExpense) )
```

Column names are unqualified so the fragment works in the aliased, joined `observeCategoryTotals`; a filtered query must not join a table carrying `accountId`, `toAccountId`, `categoryId` or `type`. The repository maps a `TransactionFilter` to these six arguments in one function in `Mappers.kt`. Trans keeps folding its header, day subtotals and Calendar from one list, which now arrives already filtered, so the three still cannot disagree.
- Rejected: filtering in the assembly functions. Trans could, but Stats would then load a period's rows to total them, which reverses the decision recorded in `StatsAssembly.kt`.
- Rejected: Kotlin for Trans and SQL for Stats. Two implementations of one rule; DRY on behaviour forbids it, and the first divergence would show as Trans and Stats disagreeing about the same month.
- Rejected: `@RawQuery` with a Kotlin-built clause. One implementation too, but Room no longer checks the SQL, and each Flow query needs `observedEntities` set by hand.
- Rejected: `filter: TransactionFilter = ALL` as a default argument. A screen that forgets it compiles and silently ignores the filter. A required parameter makes the compiler list every caller.

**Repository and DAO interface changes.** This is what makes the change large.
- `TransactionRepository.observeInRange`, `observePeriodTotals`, `observeDailyTotals`, `observeCategoryTotals`, `observeForCategory` and `observeForCategories` gain a required `filter: TransactionFilter`. The matching `TxnDao` queries gain the six filter arguments.
- New `TransactionRepository.observeAccountFlows(range, zone): Flow<List<AccountFlows>>` and `TxnDao.observeAccountFlows(startSec, endSec)`. One query: a `UNION ALL` of the source leg (INCOME, EXPENSE and transfer-out by `accountId`) and the destination leg (transfer-in by `toAccountId`), grouped by account. Adjustments are excluded. It takes no filter, because the ACCOUNT tab shows every account so the user can choose among them, and the spec keeps these figures independent of the category tabs. New `domain/AccountFlows(accountId, income, expense, transferIn, transferOut)`.
- New `TransactionRepository.observeAll(filter): Flow<List<Transaction>>` and `TxnDao.observeAll(...)`, newest first, for search. `all()` for export is unchanged.
- No entity, index or migration change. `accountId`, `toAccountId`, `categoryId` and `occurredAtEpochSec` are already indexed.
- Rejected: deriving account flows from the balance query. That query is lifetime and as-of-filtered, not period-scoped.

**Transfers and adjustments.** A transfer passes the account selection on either leg, and an adjustment on its account. Both have no category, so they pass only while both category selections are "All". They count toward no figure, filtered or not, because `countsAsFlow` and the DAO aggregates already exclude them and the filter changes which rows exist, not how a row counts. Showing a transfer when its destination alone is selected is deliberate: filtering to RDPU is how the owner checks what reached the investment account (gap 3).
- Rejected: hiding every transfer whenever any filter is active. That breaks the RDPU case above.
- Rejected: showing transfers under a category filter. "Pemberian only" listing a BCA to RDPU transfer reads as a Pemberian row.

**Stats under the filter.** The previous-period comparison runs with the same filter. Ranked shares are `wholePercents` over the filtered composition, so they sum to 100. If an open category drill's category leaves the expense or income selection, `StatsViewModel` closes the drill, the same way switching side does.

**Sheet header rings: filtered share of the unfiltered figure.** `FilterSheetViewModel` is created with the opening screen's `DateRange` and holds only the open tab. It observes `observePeriodTotals` twice, filtered and with `ALL`, plus `observeAccountFlows`, active categories and active accounts; `FilterSheetAssembly.kt` folds those into the sheet state. The share is a new `domain` function `sharePercent(part: Money, whole: Money): Int?` (half-up, null when `whole` is zero), and `CategoryTotal.wholePercent` is rewritten to call it, so single-figure rounding keeps one implementation beside `wholePercents`. The rings are a new `ui/shared/ShareRing`: one arc on a track, a figure and a percentage in words.
- Rejected: reusing `Donut`. It is a composition chart with segment gaps, colour slots per item and hit-testing, and its contract requires a legend list. A part-of-whole ring is a different behaviour, so sharing would be DRY on shape.

**Changes apply live; there is no Apply button.** Each tick calls `SharedFilter.set` with the new filter. The screens behind the sheet update as the user ticks, which is what the header's rings are for anyway.
- Rejected: a draft selection with Apply and Cancel. It needs a second copy of the filter in the sheet and a rule for what dismissing means, and swiping the sheet away would silently lose the user's ticks.

**Making an active filter obvious.** `ui/filter/FilterBar` sits directly under the period header on Trans (all three views), Stats and search, whenever `isActive`. It names the selection ("2 accounts · 1 expense category", built by a pure `filterSummary` in `FilterSheetAssembly.kt`) and has a clear action. The filter icon carries a dot and the content description "Filter, active". Empty states under a filter say the filter hides everything and offer to clear it.
- Rejected: recolouring the header figures. Colour alone fails the accessibility rule the Donut already follows, and a tint is easy to read as a theme quirk.
- Rejected: a badge on the icon only. Too small to stop a misreading; the bar names what is in force.
- Rejected: a toast on change. It is gone by the time anyone reads the figure.

**Monthly view: weeks from `DateRange.ofWeek`, whole weeks, figures from day totals.** Weeks are Monday to Sunday, the same as the Calendar grid and Stats Weekly. A week overlapping the month is labelled with its full span and counts all seven days, so the 28.09 ~ 04.10 row equals the Stats Weekly figure for that week. The year's data is one `observeDailyTotals` call (filtered) over the year widened to whole weeks, `ofWeek(1 Jan).start` to `ofWeek(31 Dec).endInclusive`, so the first and last weeks have their neighbour-year days. `ui/transactions/MonthlyAssembly.kt` folds those day rows into the year header (days inside the year only), month rows and week rows. About 370 day rows a year is nothing to fold.
- Rejected: Sunday-start weeks as in the owner's Money Manager screenshot (25.10 ~ 31.10 is Sunday to Saturday). It would be the app's second week convention, and the Monthly week row and the Stats Weekly figure for "the same week" would differ. Listed as an open question.
- Rejected: clipping weeks to the month. Week rows would sum to the month row, but a row labelled 28.09 ~ 04.10 would show only four days, and it would disagree with Stats Weekly.
- Rejected: loading a year of rows, for the reason in `StatsAssembly.kt`.
- Rejected: SQL `GROUP BY` month and week. Week bucketing in SQLite with a zone offset is fragile, and day buckets already handle the zone.

**Monthly state in `TransactionListViewModel`.** `TransactionTab` gains `MONTHLY`. `Selection` keeps its month range as the anchor; in Monthly the year arrows step that range by 12 months, so returning to Daily shows the same month in the new year. `Selection` gains `expandedMonth: Month?`, set to the current month on entering Monthly or stepping into the current year, and null in other years. The year query is keyed on the year and subscribed only while the tab is `MONTHLY`, so Daily does not pay for it, following the comment that keeps tab switches off the database. A week tap reuses `openDay`, extended to also set the month, with the date clamped to the month's last day.

**Search: SQL for the filter, Kotlin for the text.** `SearchViewModel` holds only the query text. It observes `observeAll(filter)`, and while the filter is active also `observeAll(ALL)` to count hidden matches. `ui/search/SearchAssembly.kt` folds each note once per emission with `domain/TextFold.fold` (`Normalizer.Form.NFD`, strip `\p{Mn}`, `lowercase(Locale.ROOT)`, collapse whitespace), then matches the query's words: a letters word against the folded note, a digits word (after stripping ".", "," and a leading "Rp") against the amount's plain digits by substring or against the note. The query is debounced by 150 ms. Results reuse the Daily day grouping (`toDaySections`) and `periodTotals`, so search shows totals by the same rules as every other list. `java.text.Normalizer` is JVM, not Android, so `TextFold` stays in `domain/`; `Locale.ROOT` avoids locale-dependent lowercasing.
- Rejected: SQL `LIKE`. SQLite folds ASCII case only and does no accent folding.
- Rejected: an FTS table or a stored folded-note column. Both need a migration and an entity change for about 1,600 rows that fold in well under a frame.
- Rejected: exact-amount matching only. People recall "47" or "47.500", rarely the precise figure with confidence, and a partial match with a visible count is easy to scan.
- Rejected: an amount range with tolerance. It needs its own input UI for a case partial digits already covers.

**Search is a pushed destination under Trans.** A new route `transactions/search` resolves to the Trans tab in the bottom bar, like the More sub-screens resolve to More. A result opens the existing `EntrySheet` with its id.

## Risks / Trade-offs

- [Room expands an empty list to `IN ()`] → SQLite accepts an empty `IN` list and returns false. A DAO test under Robolectric, next to `PeriodAggregateTest`, covers "none ticked" for each side.
- [A future filtered query joins a table with a clashing column name] → The fragment's doc comment states the constraint, and the DAO test runs every filtered query with a non-trivial filter.
- [A short digit query such as "000" matches most rows] → Accepted. The count is shown, and another word narrows it.
- [Search reloads all history on every write] → Fine at about 1,600 rows and 1,500 a year. If it ever exceeds 20,000, scope `observeAll` by a date range. Not built now.
- [Week rows do not sum to the month row] → Accepted and stated in the spec; the full-span label shows why.
- [Process death clears the filter without notice] → Intended. The bar is absent, so no figure is mislabelled.
- [Maestro flows meet a stale filter] → Cannot happen across launches, because the filter is never persisted. New flows cover filtering, Monthly and search.

## Open Questions

Closed by the owner before implementation:

- Week start stays Monday, matching the Calendar and `DateRange.ofWeek`.
- Search matches note text and amount only, as specified.
- The ACCOUNT tab shows a Liability group only when a liability account exists, as specified.

## Decisions made while building

**The filter parameter goes last on every repository method.** `observeInRange(range, zone, filter)`, `observeForCategory(range, zone, categoryId, type, filter)` and so on. It reads as a qualifier on the query rather than part of what is asked for, and every caller had to be touched anyway.
- Rejected: placing it right after `range`. No caller reads better for it, and it would reorder arguments that tests pass positionally.

**DAO methods take six flat arguments; the repository maps them in one place.** `TransactionFilter.toArgs()` in `Mappers.kt` returns a `FilterArgs`; each repository method spreads it into the DAO call. Room cannot bind a data class to several named parameters, so the flat list is forced.
- Rejected: DAO default arguments for the six. They would let a DAO caller skip the filter as silently as a repository default would, which the design already rejected.

**`observeAll` uses `WHERE 1 = 1` before the fragment.** The fragment starts with `AND` so it concatenates after a range in every other query; a constant true clause is the smallest way to reuse it unchanged.

**`TransactionFilter.includesCategory(type, id)` lives in `domain/`.** Stats needs it to close a drill whose category left the selection. It reads the model only, so it belongs beside it rather than in a feature assembly. Stats closes the drill by collecting the filter in `init` and clearing `Selection.drill`, the same way switching side clears it.
- Rejected: hiding the drill in the final combine while leaving it in `Selection`. Re-ticking the category would then reopen a drill the user never asked for again.

**Normalising "every listed item ticked" to "All" happens only on a tick.** `toggledItem` in `FilterSheetAssembly.kt` returns null when a tick completes the listed set. A selection is never normalised passively, so "Makan & Minum only" does not become "All" if every other expense category is later archived.

**"Unticking one item under All" starts from the listed (non-archived) ids.** The resulting explicit set therefore excludes archived categories. That matches what the user sees ticked; "All" is the only state that includes archived items, as the spec says.

**`filterSummary` returns English copy from the assembly.** "2 accounts · 1 expense category" is the behaviour the spec's scenarios quote, so it is tested as a string. An empty selection reads "no income", "no expenses" or "no accounts" rather than "0 income categories". The app ships in English only; `periodLabel` and the date labels already build copy in Kotlin for the same reason.
- Rejected: Android plurals resources. They need a `Context`, which would push the rule into a composable and out of reach of a JVM test.

**The sheet's ViewModel is keyed by range and resets to INCOME once per opening.** `viewModel(key = "filter-sheet-$range")` gives Trans month, Trans year and each Stats period their own instance. The ViewModel outlives a closed sheet, so the composable selects INCOME on the first composition of each opening, guarded by a `rememberSaveable` flag so rotation keeps the open tab.
- Rejected: a fresh ViewModel key per opening. Each opening would leave a ViewModel in the destination's store until the destination is popped.

**Sheet header on Trans follows the visible view.** In Monthly the sheet opens on the year, because that is the period the header shows; in Daily and Calendar on the month. `TransactionListUiState.headerRange` is the one place that choice is made, and the header and the sheet both read it.

**`ShareRing` lives in `ui/shared/` and takes its copy and spoken description as parameters,** as every shared component does. Its arc colour comes from `forDirection`, the existing direction mapping.

**`FilteredEmptyState` sits beside `FilterBar` in `ui/filter/`.** Daily, Monthly, Stats and search all need the same "the filter hides everything, clear it" state; one composable keeps the copy and the action identical. Calendar shows no such state: under a filter its cells render empty as unlogged days do, which is what the spec asks for.

**Monthly empty versus filtered.** A year whose every month is empty shows the filtered empty state only while a filter is active. Without one, the month rows stay, each reading "Nothing recorded", because an unlogged month is information.

**Week rows are computed for every month, not only the expanded one.** About 60 week folds over 370 day rows a year is negligible, and it keeps the assembly free of the expansion rule except for the `expanded` flag.

**The week tap passes the open date; the ViewModel derives the month from it.** `openDay(date)` now sets `range = ofMonth(date)`. The assembly computes `openDate = min(week end, month end)`, which falls inside the month the row is listed under, so one function serves Calendar day taps and Monthly week taps.

**Daily's scroll fallback is a pure function.** `scrollIndexFor` in `TransactionListAssembly.kt` returns the header index of the target day or of the nearest earlier day with rows, and the oldest day when every day is later. Before this change Daily scrolled only on an exact date match.

**Search drops a bare "Rp" word.** "Rp 25.000", typed the way the app prints amounts, means the same as "Rp25.000" and "25000". A digits word also matches the note by its digits, so "Rp25.000" finds a note reading "25000".
- Rejected: treating "Rp" as a text word. It would match no note and make the whole query fail.

**Search's text field reads an undebounced `query` flow; results read the debounced one.** Binding a text field to a debounced or combined state drops keystrokes and moves the cursor.

**Search reuses `DailyTab` for its results.** The same day headers, rows and tap handling as Daily, so a result looks exactly like the row it is. Its own empty states are decided before `DailyTab` is called, so Daily's "Nothing recorded this month" never shows under search.

**`TransRoute` mirrors `MoreRoute`.** A one-entry enum, `SEARCH("transactions/search")`, that `SakuDestination.owning` maps to the Trans tab.
- Rejected: a string constant. The enum keeps the owning check symmetrical with More, and a second pushed Trans screen slots in without a new rule.

**New copy is in `res/values/strings_transaction_filter.xml`.** A concurrent change appends to `strings.xml`; a separate file keeps the two from conflicting. Android merges every file under `values/`, so nothing else changes.
