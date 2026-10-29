## ADDED Requirements

### Requirement: Opening the filter
The Trans screen and the Stats screen SHALL each offer a filter action that opens the filter sheet. The sheet SHALL have three tabs: INCOME, EXPENSES and ACCOUNT, opening on INCOME.

#### Scenario: Opening from Trans
- **WHEN** the user taps the filter action on the Trans screen
- **THEN** the filter sheet opens on the INCOME tab, with the Trans screen's current period in its header

#### Scenario: Opening from Stats
- **WHEN** the user taps the filter action on the Stats screen while the period is Annually 2026
- **THEN** the filter sheet opens with 2026 as the period in its header

### Requirement: Checkbox lists with an All row
The INCOME tab SHALL list every non-archived income category, and the EXPENSES tab every non-archived expense category, each in the user's category order with a checkbox. The ACCOUNT tab SHALL list every non-archived account with a checkbox, grouped under Cash, Bank, Investment and Liability headings in that order, showing only groups that hold an account. Each tab SHALL start with an "All" row.

#### Scenario: Account grouping
- **WHEN** the user has BCA and Jago (Bank), Cash (Cash) and RDPU SucorInvest (Investment), and no liability account
- **THEN** the ACCOUNT tab shows "All", then Cash with Cash beneath it, Bank with BCA and Jago, and Investment with RDPU SucorInvest, and no Liability heading

#### Scenario: Default selection
- **WHEN** the user opens the sheet without having filtered before
- **THEN** "All" and every item are ticked on all three tabs

### Requirement: All row semantics
Each tab's selection SHALL be either "All" or an explicit set of items. "All" SHALL include archived items and items created later. Unticking "All" SHALL untick every item on that tab. Ticking "All" SHALL tick every item. Unticking one item while "All" is ticked SHALL leave every other listed item ticked and untick "All". Ticking the last unticked item SHALL make the selection "All" again.

#### Scenario: Narrowing to one category
- **WHEN** on the EXPENSES tab the user unticks "All" and ticks Makan & Minum
- **THEN** only Makan & Minum is ticked, and the expense selection is that one category

#### Scenario: Re-ticking every item
- **WHEN** the user has unticked Pemberian and then ticks it again
- **THEN** "All" is ticked, and an expense category archived earlier is again included

#### Scenario: New category while filtered
- **WHEN** the expense selection is Makan & Minum only and the user creates a new expense category
- **THEN** the new category is unticked and its rows are not shown

### Requirement: The filter applies at once and to Trans and Stats together
A change in the sheet SHALL apply immediately, without a confirm step, to the Daily, Calendar and Monthly views of Trans and to every period mode of Stats. There SHALL be one filter for both screens: a filter set from Stats SHALL be in force on Trans, and the reverse.

#### Scenario: Live update
- **WHEN** the user unticks BCA on the ACCOUNT tab while the sheet is open over Trans
- **THEN** the Trans header and list behind the sheet update without BCA's rows before the sheet is closed

#### Scenario: Shared between tabs
- **WHEN** the user filters to Jago on the Stats tab and then opens the Trans tab
- **THEN** Trans shows only rows involving Jago, and its active-filter bar is visible

### Requirement: Which income and expense rows pass the filter
An income transaction SHALL pass when its category is in the income selection and its account is in the account selection. An expense transaction SHALL pass when its category is in the expense selection and its account is in the account selection. An empty income or expense selection SHALL hide that whole side.

#### Scenario: Category and account together
- **WHEN** the expense selection is Makan & Minum and the account selection is Cash
- **THEN** a Makan & Minum expense paid by Cash is shown, and a Makan & Minum expense paid by BCA is not

#### Scenario: Expenses only
- **WHEN** the user unticks "All" on the INCOME tab and leaves EXPENSES and ACCOUNT at "All"
- **THEN** no income row is shown, every income figure reads zero, and every expense is shown

### Requirement: Transfers and adjustments under the filter
A transfer SHALL pass the account selection when either its source or its destination account is selected. An adjustment SHALL pass when its account is selected. Because transfers and adjustments have no category, they SHALL be shown only while both the income and expense selections are "All". Neither SHALL count toward any income, expense or total figure, filtered or not.

#### Scenario: Transfer under an account filter
- **WHEN** the account selection is RDPU SucorInvest only and a month holds a 500.000 transfer from BCA to RDPU SucorInvest
- **THEN** the transfer row is listed, and the month's Income, Expenses and Total are unchanged by it

#### Scenario: Transfer under a category filter
- **WHEN** the expense selection is Pemberian only
- **THEN** no transfer and no adjustment row is listed

### Requirement: Every figure respects the filter
While a filter is active, every figure on Trans and Stats SHALL be computed from the passing rows only: the period header, the Daily day subtotals, the Calendar cells, the Monthly rows, the Stats totals, the previous-period comparison, the donut, the ranked list with its percentages, and the drill-through. Ranked-list percentages SHALL be shares of the filtered total and SHALL sum to exactly 100.

#### Scenario: Stats under a category filter
- **WHEN** the expense selection is Makan & Minum and Jajan & Hiburan, and August holds 1.257.700 and 843.800 of them
- **THEN** the Expenses total reads 2.101.500, the donut has two segments, and the list reads 60% and 40%

#### Scenario: Comparison compares like with like
- **WHEN** the account selection is Cash and Stats compares August with July
- **THEN** both months' figures count only Cash rows

#### Scenario: Calendar under a filter
- **WHEN** a day holds only expenses on BCA and the account selection is Jago
- **THEN** that day's Calendar cell renders empty, as an unlogged day does

### Requirement: Sheet header shows the filtered share
The sheet header SHALL show the period of the screen it was opened from, an Income ring and an Expenses ring, and the filtered Total. Each ring SHALL show the filtered figure, and its share of the unfiltered figure for the same period as a whole percent. When the unfiltered figure is zero, the ring SHALL show no percentage.

#### Scenario: Header for a filtered month
- **WHEN** October holds 10.000.000 income and 6.000.000 expenses, and the filter passes 8.000.000 income and 1.500.000 expenses
- **THEN** the Income ring reads 8.000.000 and 80%, the Expenses ring reads 1.500.000 and 25%, and the Total reads 6.500.000

#### Scenario: Month with no income
- **WHEN** the period holds no income at all
- **THEN** the Income ring is empty and shows 0 with no percentage

### Requirement: Per-account in and out totals
On the ACCOUNT tab, each account row SHALL show, for the sheet's period, the account's Income and Expenses on the first line, and its Transfer-In and Transfer-Out on a second line. These figures SHALL count all categories, whatever the INCOME and EXPENSES tabs select, and SHALL exclude adjustments.

#### Scenario: An account's month
- **WHEN** in October BCA received 8.000.000 income, paid 2.400.000 of expenses, sent a 500.000 transfer to RDPU SucorInvest and received a 300.000 transfer from Jago
- **THEN** BCA's row reads Income 8.000.000 and Expenses 2.400.000, then Transfer-In 300.000 and Transfer-Out 500.000

#### Scenario: Category filter does not change account figures
- **WHEN** the expense selection is Pemberian only
- **THEN** the ACCOUNT tab figures are the same as with "All" selected

#### Scenario: Adjustment excluded
- **WHEN** Cash was reconciled in October with a −53.500 adjustment
- **THEN** none of Cash's four figures includes the 53.500

### Requirement: An active filter is always visible
While any tab's selection is not "All", the Trans and Stats screens SHALL show a "Filtered" bar directly beneath the period header, naming what is selected (for example "2 accounts · 1 expense category") with a clear action, and the filter action SHALL show an active marker. Clearing SHALL set all three tabs to "All". With no active filter, the bar SHALL NOT be shown.

#### Scenario: Filter set
- **WHEN** the account selection is BCA and Jago and both category tabs are "All"
- **THEN** Trans and Stats each show a bar reading "2 accounts" with a clear action

#### Scenario: Clearing
- **WHEN** the user taps clear on the bar
- **THEN** every tab returns to "All", the bar disappears, and the figures return to the unfiltered values

### Requirement: Empty result under a filter
When the filter leaves no row for the period, Trans and Stats SHALL show an empty state that says the filter hides everything in the period and offers to clear it, rather than the plain no-data state.

#### Scenario: Nothing passes
- **WHEN** the account selection is RDPU SucorInvest only and the month holds no row involving it
- **THEN** the Daily view shows "Nothing in this month matches the filter" with a clear action

### Requirement: Filter lifetime
The filter SHALL persist while the app process lives, across tab switches and screen rotation. It SHALL NOT be saved to storage, and a new app process SHALL start with every tab at "All".

#### Scenario: Switching tabs
- **WHEN** the user filters on Trans, opens Accounts, and returns to Trans
- **THEN** the filter is still active

#### Scenario: Restarting the app
- **WHEN** the user filters, force-stops the app, and opens it again
- **THEN** no filter is active and the "Filtered" bar is not shown
