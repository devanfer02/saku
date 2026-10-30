## Why

Search shows one count, one Income and Expenses pair, and the matching rows day by day. A search like "arisan" over a year answers "how much in total" but not "is it going up". The owner asked for search results to show a per-month breakdown so the trend is visible.

## What Changes

- Under the search summary, a "By month" list: one row per month from the newest month with a match back to the oldest, including months inside that span with no match. Each row shows the month, its money out, its money in when non-zero, and its match count, with a bar against the largest month.
- Tapping a month scrolls the results to that month's first day.
- The list, the summary and the day rows scroll together as one list.
- Shown only when the matches span two or more months.

## Capabilities

### New Capabilities
- `search-by-month`: the per-month breakdown of search results and its tap behaviour.

### Modified Capabilities
<!-- None: openspec/specs/ holds no synced capability yet. transaction-search lives in the unarchived transaction-filter change. -->

## Impact

- `ui/search/SearchAssembly.kt`: `SearchMonth`, the month fold, the scroll index; `SearchUiState.months`.
- `ui/search/SearchScreen.kt`: one `LazyColumn` holding the summary, the month rows (`RankedRow`) and the day rows (`daySectionItems`).
- `res/values/strings.xml`: the heading and the row's supporting line.
- No repository, entity or dependency change.
