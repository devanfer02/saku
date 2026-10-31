Depends on `receipt-items`.

## 1. Assembly

- [x] 1.1 `PriceHistoryAssembly.kt`: item key, unit price, points per day, latest, lowest, highest, change; tests from the spec scenarios

## 2. UI

- [x] 2.1 Price card in search when all expense results share one item key and there are at least 2; reuse or add a line chart in `ui/shared/`
- [x] 2.2 Price history action on an expense row, opening search on its item key

## 3. End-to-end on the emulator (mock data only, run by adb, screenshot per step)

- [x] 3.1 After 3 synthetic scans in different months, search `minyak goreng 2l`: card with 3 points, latest, lowest, highest, change
- [x] 3.2 `susu uht 1l` with a `×2` row: unit price halves that row
- [x] 3.3 Search `minyak` across two items: no card, results as before
- [x] 3.4 One matching row: no card
- [x] 3.5 Open an item row, tap Price history: search opens on its item name with the card
- [x] 3.6 Dark theme and font size 200%: card readable, nothing clipped

## 4. Done

- [x] 4.1 `lintDebug`, `testDebugUnitTest`, `assembleDebug` green; README lists the feature
