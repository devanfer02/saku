## Why

Owner feedback on the Stats tab, 30 Oct 2026:

1. "In stats, I should be able to pick the current year/month/week on the top bar based on what tab is active." Today the only ways to reach another period are the arrows, one step at a time, and the strip. Getting from October 2026 to March 2025 is nineteen taps.
2. "Stats horizontal bar chart scroll should not be locked into the current month; I should be able to still see the 'future' of the bar chart." The strip ends on the selected period, so selecting January hides February onwards, and a future-dated transaction (a planned bill, a typo) never shows in it at all.

## What Changes

- The period label in the Stats header becomes a button. Tapping it opens a picker that matches the active mode tab: a year header with a 3 x 4 month grid in Monthly, a grid of years in Annually, a date picker that selects the week holding the date in Weekly. Each picker has a "This month" / "This week" / "This year" action. Period (custom) mode keeps its range picker unchanged.
- `PeriodBar` gains an optional `onLabelClick`. Null keeps the label plain, so the transaction list is unchanged.
- The strip runs from the first period with data through the latest of: the last period with data, today's period, and the selected period, then three more empty periods. The strip query reaches far enough forward to include future-dated rows.
- The strip opens scrolled so the selected column is in view, with one later column beside it when there is one, instead of relying on the newest column being the selected one.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `period-stats`: adds picking a period from the header.
- `buku-kas`: the strip scroll and the strip mode requirements now run past the selected period.

## Impact

- `ui/shared/PeriodBar.kt`: one optional parameter and its description, no string resources.
- `ui/stats/`: new `PeriodPickerAssembly.kt` (pure) and `PeriodPicker.kt` (dialogs); `StatsAssembly.kt` (`foldStrip`, `stripHistory`); `StatsStrip.kt` (scroll to the selected column); `StatsViewModel.kt` and `StatsUiState.kt` (today in the state); `StatsScreen.kt` (wire the picker).
- `res/values/strings.xml`: picker copy.
- No Room entity, DAO, repository interface or migration change.
