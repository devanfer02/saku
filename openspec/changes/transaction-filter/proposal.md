## Why

Saku can show a month and rank its categories, but it cannot answer the narrower questions the owner asked Money Manager every week: what went through Jago this month, what Pemberian came to this year with transfers set aside, when a given 47.500 was paid. Money Manager has a checkbox filter, a Monthly view, search and per-account in/out totals; the owner showed screenshots of all four and wants each one before switching for good.

## What Changes

- A filter sheet with three tabs, INCOME, EXPENSES and ACCOUNT. Each lists its items with a checkbox and an "All" row; accounts are grouped by type. The selection applies to the Trans tab (Daily, Calendar, Monthly) and the Stats tab together.
- The sheet header shows the period, an Income ring and an Expenses ring with the filtered share of the unfiltered figure, and the filtered Total.
- In the ACCOUNT tab, each account row shows Income and Expenses for the period, and Transfer-In and Transfer-Out on a second line.
- A visible "Filtered" bar on Trans and Stats whenever a filter is active, with a one-tap clear, so filtered totals are never read as whole ones.
- A third Trans sub-tab, Monthly, beside Daily and Calendar. It steps by year, lists the year's months newest first with income, expenses and net, and expands a month into Monday to Sunday week rows with the current week highlighted.
- A search screen opened from Trans that matches note text (ignoring case and accents) and amounts (by digits), across all history, under the active filter.
- The filter is held in memory only. It resets when the app process ends.

## Capabilities

### New Capabilities
- `transaction-filter`: the filter sheet, its tabs and "All" rows, the header rings, per-account in/out totals, how the filter treats transfers and adjustments, its scope over Trans and Stats, and the active-filter bar.
- `transaction-monthly-view`: the Monthly sub-tab in Trans, year stepping, month rows, week rows and the current-week highlight.
- `transaction-search`: searching by note text and amount, matching rules, how search combines with the filter, and opening a result.

### Modified Capabilities
None. `openspec/specs/` has no synced capability yet; the changed behaviour of the Trans and Stats screens is specified as ADDED requirements in the three capabilities above.

## Impact

- **Repository interface change.** `TransactionRepository` read methods that feed Trans and Stats (`observeInRange`, `observePeriodTotals`, `observeDailyTotals`, `observeCategoryTotals`, `observeForCategory`, `observeForCategories`) gain a required `TransactionFilter` parameter. Two methods are added: `observeAccountFlows` (per-account income, expense, transfer-in and transfer-out for a period) and `observeAll(filter)` for search. `TxnDao` gains the same parameters and two queries. `all()` for export is unchanged.
- New `domain/TransactionFilter.kt` and `domain/TextFold.kt` (pure Kotlin).
- New `ui/filter/` (filter sheet, its ViewModel and assembly, the shared holder, the active-filter bar), new `ui/search/`, and a new `MonthlyTab` with `MonthlyAssembly.kt` in `ui/transactions/`.
- `TransactionListViewModel` and `StatsViewModel` read the shared filter; `TransactionTab` gains `MONTHLY`.
- `AppContainer` constructs the shared filter holder.
- No Room entity change, no migration, no new dependency, no new permission.
