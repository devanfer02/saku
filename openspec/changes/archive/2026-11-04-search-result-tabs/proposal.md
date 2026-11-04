## Why

The owner, 1 Nov 2026, on a phone search for "mama" with 32 results over 16 months: the results start right under the search field with no room, the summary is small, and the By month list sits above the day rows in one long scroll. Trans already switches views with tabs; search should too.

## What Changes

- Search results get Daily, Monthly and Yearly tabs (the shared `SegmentedTabs`), under the summary.
- Daily lists the matching rows by day. Monthly lists every month from the newest match to the oldest. Yearly does the same per year.
- Tapping a year opens Monthly at that year's newest month with a match; tapping a month opens Daily at that month's newest day.
- Search opens on Monthly when the matches span two months or more, else on Daily. A tab the user picks is kept while they type.
- The summary gets more space above it and its text is 20% larger.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `search-by-month`: the breakdown moves into a Monthly tab, a Yearly tab is added, and jumps switch tabs.

## Impact

`ui/search/` only: `SearchAssembly.kt` (`SearchTab`, `SearchPeriod`, years, scroll indexes), `SearchViewModel.kt` (picked tab), `SearchScreen.kt`, strings. No storage change.
