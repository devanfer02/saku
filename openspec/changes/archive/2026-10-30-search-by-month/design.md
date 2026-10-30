## Context

`ui/search/` matches text in `SearchAssembly.kt` over rows the filter already narrowed in SQL, then shows a summary (`SearchUiState.count`, `totals`) and `DailyTab`. `DailyTab` owns its own `LazyColumn`. Stats has a month strip (`PeriodStrip` in `ui/stats/StatsStrip.kt`) and a ranked-row component (`ui/shared/RankedRow.kt`).

## Goals / Non-Goals

**Goals:**
- See the month-by-month trend of a search without leaving search.
- Reuse the existing month labels, money formatting, bar scaling and row component.

**Non-Goals:**
- Filtering the result rows to a month.
- A per-week or per-year breakdown.
- Percentages per month. A share of the total says less than the amount for a trend, and `wholePercents` is for parts of a whole.

## Decisions

### Ranked rows, not the Stats strip
`PeriodStrip` was the first choice. It does not fit cleanly for three reasons:
- It shows no count, and the count per month is part of what was asked for.
- It assumes one column is selected: unselected columns are drawn at 40% strength and the scroll state is keyed on the selection. Search has no selected month, so every bar would read as muted, or the strip would need a "nothing selected" mode.
- Its columns speak Stats copy ("Income … Expenses … Net") through Stats string resources.

`RankedRow` already takes a name, a value, a supporting line and a bar in thousandths from `barPermille`. Each month becomes one row: the month name from `periodLabel`, the value as money, the count and the money in as the supporting line through `formatIdr`. The swatch and bar take the expense or income colour token, so the colour means the same as everywhere else.

### Scroll to the month, not filter by it
Scrolling needs no ViewModel state and no "show all" control to undo it, and the rows of other months stay visible above and below. Filtering would add a selected month to the ViewModel, a chip to clear it, and a second rule for what the summary totals mean. The scroll index is a pure function (`searchScrollIndex`) built on the existing `scrollIndexFor`, so it is tested without Compose.

### One list
The month rows live in the same `LazyColumn` as the day rows, using the existing `daySectionItems`, instead of sitting above `DailyTab`. With a year the list is about 700 dp tall; placed above a separate list it would leave no room for the results on a phone. In one list it scrolls away like a header.

### Shown only across two or more months
One month's breakdown repeats the summary line, so it is hidden.

### Months with no match are listed
The trend is the point, and a month at zero is part of it. A gap would make August and October look adjacent. Months are listed from the newest match back to the oldest, never past them.

### Bar side
Money out is the default side because most searches are for spending. When the matches' total money in is larger, the bar and value switch to money in, so a search for "gaji" does not draw thirteen empty bars.

## Risks / Trade-offs

- [A search spanning many years produces a long list] → It scrolls away with the results; acceptable for a personal ledger of a few years.
- [Scroll index drifts if the list's leading items change] → The index is computed from the same item count the screen emits, and both live beside each other in `SearchAssembly.kt` with a test.
