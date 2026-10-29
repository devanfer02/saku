## Why

A calendar month splits a salary from the spending it pays for. The owner's "EC Payday" lands near the end of the month, so October's income and September's spending sit in different months, and the six-month strip on Stats compares halves of two pay periods. People are paid on different days (the 1st, the 25th, the 30th), so the month boundary must be configurable, while the calendar month stays the default.

## What Changes

- A pay-cycle setting: the day of the month a pay month starts (1 to 31). Unset by default, which means calendar months.
- Once set, Trans and Stats offer two month views: **Calendar month** (default) and **Pay month**. The choice is shared by both tabs and remembered.
- Pay months use the setting everywhere a month is computed while that view is on: Trans month navigation, the Monthly view, Stats Monthly mode, the six-month strip, and the "vs previous month" comparison.
- Every pay month always shows its exact date span under its label, so it is never mistaken for a calendar month.
- A start day past the end of a short month falls on that month's last day.

## Capabilities

### New Capabilities
- `pay-cycle`: the setting, the two month views and their switch, pay-month arithmetic, labelling, and which screens follow the view.

### Modified Capabilities
None. `openspec/specs/` has no synced capability; the effect on Trans, Stats, the Monthly view and the six-month strip is specified inside `pay-cycle`.

## Impact

- `domain/DateRange.kt`: a pay-month constructor and shift that respect the start day; calendar-month behaviour unchanged.
- A small settings store beside `AppearanceStore` (SharedPreferences): start day and the selected view.
- Trans and Stats ViewModels read the view and the start day when building their ranges; assemblies unchanged apart from labels.
- More > Organise gains "Pay cycle".
- No Room entity, DAO or repository interface change: queries already take a date range.
- Lands after `transaction-filter` (Monthly view) and with or after `buku-kas` (six-month strip).
