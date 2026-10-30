## 1. Assembly

- [ ] 1.1 Add `SearchMonth` and `List<Transaction>.toSearchMonths(zone)` to `SearchAssembly.kt`: the span from newest to oldest match, empty months included, transfers counted but not summed, bar side and `barPermille`; set `SearchUiState.months` in `assembleSearch`
- [ ] 1.2 Add `searchScrollIndex(months, days, month)` for the one-list layout
- [ ] 1.3 Tests: empty months inside the span, transfers excluded from money, filter respected (rows the SQL already dropped never appear), single month gives no list, income-side bars, scroll index

## 2. Screen

- [ ] 2.1 Rebuild the results as one `LazyColumn`: summary, hidden line, "By month" heading and `RankedRow`s, then `daySectionItems`
- [ ] 2.2 Tapping a month with matches scrolls to its newest day; empty months are not clickable
- [ ] 2.3 Strings for the heading and the supporting line (plural count, optional money in)

## 3. Verify

- [ ] 3.1 `:app:testDebugUnitTest :app:assembleDebug` green
