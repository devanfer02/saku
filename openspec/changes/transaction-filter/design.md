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

- Week start: Monday (chosen, matches Calendar and Stats Weekly) or Sunday as in Money Manager. If Sunday, it should change for the whole app, in `DateRange.ofWeek`, not only here.
- Should search also match category, subcategory and account names? Specified as note text and amount only, as asked.
- The ACCOUNT tab shows a Liability group when a liability account exists. Money Manager's screenshot shows Cash, Bank and Investment only.
