## 1. Domain

- [x] 1.1 Add `domain/TransactionFilter.kt`: three nullable id sets (null is "All", empty is "none"), `ALL`, `isActive`
- [x] 1.2 Add `domain/AccountFlows.kt`: account id, income, expense, transfer-in, transfer-out as `Money`
- [x] 1.3 Add `sharePercent(part, whole): Int?` beside `wholePercents` (half-up, null on a zero whole) and route `CategoryTotal.wholePercent` through it; extend `WholePercentsTest`
- [x] 1.4 Add `domain/TextFold.kt` (`fold`: NFD, strip combining marks, `Locale.ROOT` lowercase, collapse whitespace) with tests for case, accents and spacing

## 2. Data

- [x] 2.1 Add `TXN_FILTER_WHERE` to `TxnDao` and concatenate it into `observeInRange`, `observePeriodTotals`, `observeDailyTotals`, `observeCategoryTotals`, `observeInRangeForCategory` and `observeInRangeForCategories`, with the six filter arguments
- [x] 2.2 Add `TxnDao.observeAccountFlows(startSec, endSec)` (source and destination legs by `UNION ALL`, adjustments excluded) and `TxnDao.observeAll` with the filter, newest first
- [x] 2.3 Add the filter-to-arguments mapping in `Mappers.kt` and the required `filter` parameter on the six `TransactionRepository` methods; add `observeAccountFlows` and `observeAll(filter)`; update `RoomTransactionRepository`
- [x] 2.4 Robolectric DAO tests beside `PeriodAggregateTest`: category and account selections, either-leg transfers, transfers and adjustments hidden under a category filter, empty selections (`IN ()`), the joined category-totals query, and account flows with an adjustment present
- [ ] 2.5 Fix every existing caller to pass `TransactionFilter.ALL` until its screen reads the shared filter; `:app:testDebugUnitTest` green

## 3. Shared filter and sheet

- [x] 3.1 Add `ui/filter/SharedFilter` (a `StateFlow<TransactionFilter>` with `set` and `clear`) and build it in `AppContainer`
- [x] 3.2 Add `ui/filter/FilterSheetAssembly.kt`: tick and "All" transitions with normalisation to null, account grouping by type, ring shares, per-account rows, and `filterSummary`; unit-test against every scenario in `specs/transaction-filter/spec.md` it covers
- [x] 3.3 Add `FilterSheetViewModel` (built with the opening screen's `DateRange`, holds only the open tab) and its tests
- [x] 3.4 Add `ui/shared/ShareRing` and the `FilterSheet` composable (stateless overload plus stateful one): three tabs, checkbox lists with "All", header rings and Total, two-line account rows
- [x] 3.5 Add `ui/filter/FilterBar` (summary and clear) and the filter action with its active marker and content description

## 4. Trans and Stats under the filter

- [x] 4.1 `TransactionListViewModel`: combine `SharedFilter.state` into the month key; pass it to `observeInRange`; extend its tests
- [x] 4.2 Trans screen: filter action, `FilterBar` under the header, the filtered empty state with clear
- [x] 4.3 `StatsViewModel`: combine the filter into the period key, the comparison and the drill; close a category drill whose category leaves the selection; extend its tests
- [x] 4.4 Stats screen: filter action, `FilterBar`, the filtered empty state

## 5. Monthly view

- [x] 5.1 Add `ui/transactions/MonthlyAssembly.kt`: the year widened to whole weeks, year header, month rows (started months only, empty versus zero), week rows from `DateRange.ofWeek` (started weeks only, full-span labels), current week; unit-test the October 2026, September edge and January year-edge scenarios
- [x] 5.2 `TransactionTab.MONTHLY`; `Selection.expandedMonth`; year stepping by 12 months; the year query keyed on the year and subscribed only while Monthly is open; `openDay` also sets the month; ViewModel tests
- [x] 5.3 `MonthlyTab` composable with the year header, expandable month rows, highlighted current week (not by colour alone), and the week tap into Daily; confirm Daily's scroll target falls back to the nearest earlier day with rows

## 6. Search

- [ ] 6.1 Add `ui/search/SearchAssembly.kt`: word splitting, letters versus digits words, "Rp" and separator stripping, note and amount matching, hidden-match count; reuse `toDaySections` and `periodTotals`; unit-test every scenario in `specs/transaction-search/spec.md`
- [ ] 6.2 Add `SearchViewModel` (holds only the query, 150 ms debounce, second unfiltered stream only while a filter is active) and its tests
- [ ] 6.3 Add the `transactions/search` route resolving to the Trans tab, the search action on Trans, and `SearchScreen` (stateless plus stateful) with the focused field, results, `FilterBar`, "N more outside the filter", and `EntrySheet` on tap

## 7. Verification

- [ ] 7.1 Maestro flows: filter to one account on Trans and see it on Stats, then clear; open Monthly and expand a month; search a note and an amount
- [ ] 7.2 Emulator check with the real 1,200-row export: Pemberian-only Stats for 2026 matches the drill-through total; the BCA account row's four figures match a hand count for one month
- [ ] 7.3 `ANDROID_HOME=/home/dvnnfrr/Android/Sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug` green
