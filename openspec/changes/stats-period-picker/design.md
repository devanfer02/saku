## Context

Stats has a period header (`PeriodBar`: back arrow, label, forward arrow, filter action), four mode tabs, and a strip of one column per week, month or year (`PeriodStrip`, folded by `foldStrip` from one daily totals query over `stripHistory`). `StatsViewModel.selectPeriod(DateRange)` already takes any week, month or year; the strip uses it for a tap.

The task brief asked to respect a pay-cycle month view if one exists. It does not: the owner removed pay-cycle months on 30 Oct 2026 (see `archive/2026-10-30-buku-kas/design.md`). Months are calendar months from `DateRange.ofMonth`, the same as the arrows use, so the picker builds its ranges with `DateRange.ofMonth`, `ofWeek` and `ofYear` and nothing else.

## Decisions

**One picker per mode, opened from the label.** Monthly opens a year header with previous and next arrows over a 3 x 4 grid of short month names. Annually opens a grid of years. Weekly opens the Material 3 `DatePicker` and selects `DateRange.ofWeek` of the picked date. Period mode keeps its `RangePickerDialog`, opened from the Period tab as before, and its label stays plain. Rejected: one generic picker that switches its content by mode. The three have nothing in common but the "This ..." action.

**Tapping a month or a year picks it at once.** No Done button. The grid is a list of choices, and a second tap to confirm adds nothing. The Weekly date picker keeps Done, because a calendar tap is a day, not a week, and the user may want to look before committing.

**The current period is marked, the selected one highlighted.** Selected uses `accentWash` behind accent text, as the shared picker grid does. Current gets an accent outline. Both are also spoken (`selected` semantics, and ", Current" after the period's name), so neither is colour-only. The cells are a plain three-across `Column` of `Row`s rather than a lazy grid: at most twelve months, and a lazy grid inside a dialog needs a fixed height.

**The open picker is screen state.** Whether the picker is showing lives in a `remember` in the stateless `StatsScreen`, beside `pickingRange` for the Period range picker and `filtering` for the filter sheet, rather than in the ViewModel. It follows the existing pattern on this screen; moving all three dialogs into the ViewModel is a separate change.

**Today comes from the ViewModel.** Composables never read a `Clock`, so `StatsUiState` carries `today`, set from the ViewModel's clock on every emission. Its default (the range start) exists for previews only.

**Year range: first year with data through next year.** The first year with data is read off the strip (its first non-empty column), which is already loaded and already under the filter; no new query. With no data it falls back to five years back. The selected year and today's year are always included, so the list can never omit the period on screen. Next year is the last offered because the strip already shows up to three future periods and a year further is enough to plan a budget.

**The month grid's year arrows are not bounded.** A month picker that refused to go back before the first year with data would block reviewing an empty year, which the arrows in the header allow. The grid header steps freely.

**Strip end: the latest of last data, today and the selected period, plus three.** Three future columns is half of the six that fit on a phone: enough to show the strip continues, not so many that the present scrolls off the screen. They are real zeros (`empty = false`), the same as a quiet period after history began. Rejected: two (does not read as "more to the right" when the selected column sits second from the edge) and a full screen of six (pushes the present off the first view).

**The strip starts at the earlier of the first data and the selected period.** Before this change, selecting a period before any history gave a lone empty slot. Now the strip starts at the selected period and runs on through the history after it, so selecting April 2026 when data starts in May shows April as a zero followed by the months with data. With no history at all the strip is still the selected period alone as an empty slot: there is nothing to scroll to.

**`stripHistory` reads to a fixed far future.** To show a future-dated row the query must reach past it before knowing where it is. The end is 31 Dec 2199, or the selected period's end if that is later. The daily totals query returns only days with rows, so the wider range costs nothing on an empty future. Accepted risk: a typo dated 2099 in Weekly mode makes about 3,800 columns. `LazyRow` composes only what is on screen, and the column is a visible clue to fix the row.

**Scroll: the selected column, with one later column beside it.** The row stays laid out newest first (`reverseLayout`), and its `LazyListState` is created at the selected column's index less one, so the selected column is second from the right edge whenever a later column exists. When the selection changes and the selected column is not fully visible, a `LaunchedEffect` scrolls it back to that place. A tap on a column already in view does not move the row.

**`PeriodBar` takes a click and its action label.** `onLabelClick: (() -> Unit)?` defaults to null, which keeps the label a plain `Text` for the transaction list. When set, the label shows a drop-down arrow, has the button role, and takes `labelClickDescription` as its click action label, so TalkBack reads the period and then "choose a month". The description is a parameter: `ui/shared/` takes no string resources.

## Risks

- Nothing has run on a device. The scroll placement in particular is reasoned, not observed.
- Category drill strips use the same fold, so they also show three future columns.
