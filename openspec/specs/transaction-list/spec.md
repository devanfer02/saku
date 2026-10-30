# transaction-list Specification

## Purpose
The Trans tab: Daily and Calendar views of one month, the month header, day groups, rows and opening a row for edit.
## Requirements
### Requirement: Browse transactions by month
The transaction screen SHALL show three tabs, Daily, Calendar and Monthly. Daily and Calendar are scoped to a single
selected month; Monthly is set out in `transaction-monthly-view`. Month navigation SHALL be by previous and next
arrows flanking the month label. The screen SHALL open on the current month.

#### Scenario: Opening the app
- **WHEN** the app is launched
- **THEN** the transaction screen shows the Daily tab for the current month

#### Scenario: Navigating months
- **WHEN** the user taps the previous-month arrow
- **THEN** both tabs and the period header reload for that month without changing the selected tab

### Requirement: Show the month's income, expense and net totals
A header SHALL display three figures for the selected month: Left over (the net), In and Out, laid out as the
`buku-kas` "Trans header leads with what is left over" requirement sets out. Transfers and adjustments MUST be
excluded from all three, because moving money between one's own accounts is neither income nor spending.

#### Scenario: Header for a month containing a transfer
- **WHEN** a month holds 5.061.400 of expenses, no income, and a 500.000 transfer from BCA to RDPU
- **THEN** the header reads Left over −Rp 5.061.400, In Rp 0, Out Rp 5.061.400

### Requirement: Group the Daily tab by day with per-day subtotals
The Daily tab SHALL group transactions under a header per day, in descending date order. Each day header SHALL show
the weekday, day and short month, and that day's signed net. Transfers and adjustments MUST be listed in the day's
rows but excluded from its net, and a day holding only transfers shows no net.

#### Scenario: A day mixing an expense and a transfer
- **WHEN** the 14th holds a 170.000 expense and a 500.000 transfer
- **THEN** the day header shows a net of −Rp 170.000, and both rows appear beneath it

#### Scenario: Days with no transactions
- **WHEN** a day in the selected month has no transactions
- **THEN** no header is rendered for it in the Daily tab

### Requirement: Show the account on every row
Each transaction row SHALL display its category with the category's colour swatch, then its account and note as
secondary text, then its amount. A subcategory is never shown; see `subcategory-retirement`. A transfer row SHALL
show both source and destination accounts.

#### Scenario: An ordinary row
- **WHEN** a row's transaction has category "Makan & Minum", account BCA and note "Lunch"
- **THEN** the row reads "Makan & Minum" over "BCA · Lunch", with the amount beside them

#### Scenario: A transfer row
- **WHEN** a transfer from BCA to RDPU is rendered
- **THEN** the row names both accounts, and the amount is styled neutrally rather than as income or expense

### Requirement: Expose logging gaps in the Calendar tab
The Calendar tab SHALL render the selected month as a grid of days, each cell showing that day's net figure. A day
with no transactions MUST render as visibly empty rather than as zero, so that stretches of unlogged days are
apparent at a glance.

#### Scenario: A month with a multi-day gap
- **WHEN** a month has no transactions between the 1st and the 20th
- **THEN** those twenty cells render empty, forming a visible block

#### Scenario: Picking dates on the calendar
- **WHEN** the user taps one day cell, or taps a second one, or long-presses and drags across cells
- **THEN** that day or the range between them is highlighted, and beneath the grid the range shows its Left over, In
  and Out, then its transactions grouped by day exactly as the Daily tab renders them, under the active filter

#### Scenario: Ending a calendar selection
- **WHEN** the user taps the only picked day again, taps Clear, or changes month
- **THEN** the selection is dropped and only the grid and a short hint remain

### Requirement: Edit or delete from the list
The user SHALL be able to reach edit and delete for any transaction directly from its row.

#### Scenario: Opening a transaction for edit
- **WHEN** the user taps a transaction row
- **THEN** the entry sheet opens populated with that transaction's values in edit mode

