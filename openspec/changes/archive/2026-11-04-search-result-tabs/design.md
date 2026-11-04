## Decisions

- Tabs, not one long list: the owner asked for the Trans pattern. Each tab keeps its own scroll position.
- Default tab: Monthly when two or more months have matches (the trend is the answer to "how much to X per month"), else Daily. The owner did not say; my call.
- The summary and tabs stay fixed above the lists, so totals stay visible while scrolling.
- `SearchMonth` becomes `SearchPeriod`, used for months and years; one builder steps by month or by year.
- 20% larger summary: `label` and `amountSmall` scaled by 1.2 in the summary only, not new theme tokens, since nothing else uses that size.
- Jumps use `LazyListState.requestScrollToItem` before switching tabs, so the target list opens at the right place with no animation from the top.
