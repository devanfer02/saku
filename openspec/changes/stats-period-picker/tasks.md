## 1. Spec

- [x] 1.1 Proposal, design and delta specs for `period-stats` and `buku-kas`

## 2. Assembly

- [x] 2.1 `foldStrip` takes today; the strip runs from the earlier of first data and the selected period to three past the latest of last data, today and the selected period
- [x] 2.2 `stripHistory` reads to a fixed far future so future-dated rows reach the fold
- [x] 2.3 `PeriodPickerAssembly.kt`: the month grid, the years to offer, the first year with data
- [x] 2.4 Tests: strip tests changed to the new end, future columns, a future-dated row, a past selection with later columns, the picker assembly

## 3. UI

- [ ] 3.1 `PeriodBar`: optional `onLabelClick` and `labelClickDescription`
- [ ] 3.2 `StatsUiState.today`, set by the ViewModel from its clock
- [ ] 3.3 `PeriodPicker.kt`: month grid, year grid and week date picker dialogs, each with its "This ..." action
- [ ] 3.4 Stats wires the label to the picker for the active mode, not in Period mode
- [ ] 3.5 `PeriodStrip` scrolls the selected column into view
- [x] 3.6 ViewModel tests updated for the strip running past the selection
