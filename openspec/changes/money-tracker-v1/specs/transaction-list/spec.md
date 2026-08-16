## ADDED Requirements

### Requirement: Browse transactions by month with two views
The transaction screen SHALL show exactly two tabs, Daily and Calendar, both scoped to a single selected month.
Month navigation SHALL be by previous and next arrows flanking the month label. The screen SHALL open on the
current month.

#### Scenario: Opening the app
- **WHEN** the app is launched
- **THEN** the transaction screen shows the Daily tab for the current month

#### Scenario: Navigating months
- **WHEN** the user taps the previous-month arrow
- **THEN** both tabs and the period header reload for that month without changing the selected tab

### Requirement: Show the month's income, expense and net totals
A header SHALL display three figures for the selected month: Income, Expenses, and Total. Transfers and adjustments
MUST be excluded from all three, because moving money between one's own accounts is neither income nor spending.

#### Scenario: Header for a month containing a transfer
- **WHEN** a month holds 5.061.400 of expenses, no income, and a 500.000 transfer from BCA to RDPU
- **THEN** the header reads Income 0, Expenses 5.061.400, Total −5.061.400

### Requirement: Group the Daily tab by day with per-day subtotals
The Daily tab SHALL group transactions under a header per day, in descending date order. Each day header SHALL show
the day number, weekday, month and year, and that day's income and expense subtotals. Transfers and adjustments MUST
be listed in the day's rows but excluded from its subtotals.

#### Scenario: A day mixing an expense and a transfer
- **WHEN** the 14th holds a 170.000 expense and a 500.000 transfer
- **THEN** the day header shows an expense subtotal of 170.000, and both rows appear beneath it

#### Scenario: Days with no transactions
- **WHEN** a day in the selected month has no transactions
- **THEN** no header is rendered for it in the Daily tab

### Requirement: Show account and subcategory on every row
Each transaction row SHALL display its category, note, amount, and its account identified by both name and the
account's colour swatch. Where a subcategory is set it SHALL be shown alongside the account. A transfer row SHALL
show both source and destination accounts.

#### Scenario: A row with a subcategory
- **WHEN** a row's transaction has category "Makan & Minum", subcategory "Restoran", and account BCA
- **THEN** the row reads the category, the note, and an account line showing BCA's colour swatch, "BCA" and "Restoran"

#### Scenario: A transfer row
- **WHEN** a transfer from BCA to RDPU is rendered
- **THEN** the row shows both account names with their swatches and an arrow between them, and the amount is styled neutrally rather than as income or expense

### Requirement: Expose logging gaps in the Calendar tab
The Calendar tab SHALL render the selected month as a grid of days, each cell showing that day's net figure. A day
with no transactions MUST render as visibly empty rather than as zero, so that stretches of unlogged days are
apparent at a glance.

#### Scenario: A month with a multi-day gap
- **WHEN** a month has no transactions between the 1st and the 20th
- **THEN** those twenty cells render empty, forming a visible block

#### Scenario: Opening a day from the calendar
- **WHEN** the user taps a day cell that has transactions
- **THEN** the Daily tab opens scrolled to that day

### Requirement: Edit or delete from the list
The user SHALL be able to reach edit and delete for any transaction directly from its row.

#### Scenario: Opening a transaction for edit
- **WHEN** the user taps a transaction row
- **THEN** the entry sheet opens populated with that transaction's values in edit mode
