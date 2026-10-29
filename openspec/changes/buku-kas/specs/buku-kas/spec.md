## ADDED Requirements

### Requirement: One money format
Every amount the app shows SHALL be rendered by the shared money component as `Rp` followed by full-stop thousands and no decimals. An amount that represents money coming in or going out SHALL carry a sign: `+` for income and a minus sign for expense. Balances SHALL show a sign only when negative. Transfers and adjustments SHALL show no sign and render in neutral ink.

#### Scenario: Headers, day totals and rows agree
- **WHEN** October 2026 has income 16.500.000 and expenses 14.026.400, and 29 Oct nets 261.000 out
- **THEN** the Trans header shows `+Rp 2.473.600` left over, the 29 Oct day header shows `−Rp 261.000`, and a 196.000 expense row shows `−Rp 196.000`

#### Scenario: A negative balance
- **WHEN** the Cash account balance is −1.038.500
- **THEN** it shows `−Rp 1.038.500`

### Requirement: Direction is carried by the sign
Income amounts SHALL use the income colour. Expense amounts in transaction rows SHALL use the primary ink, so that the expense colour is not spent on every row. Expense totals and expense-side labels SHALL keep the expense colour. Colour SHALL never be the only signal of direction.

#### Scenario: A list with income and expense
- **WHEN** a day holds a salary income and two expenses
- **THEN** the income row reads `+Rp …` in the income colour and both expense rows read `−Rp …` in ink

### Requirement: Trans header leads with what is left over
The Trans tab SHALL show, under the month selector, the month's net as "Left over" in the largest type on the screen, with "In" and "Out" totals beside it. Day group headers SHALL read weekday, day and short month (`Thu 29 Oct`) with the signed day net.

#### Scenario: Reading October
- **WHEN** the user opens Trans on October 2026
- **THEN** the first figure read is `Left over +Rp 2.473.600`, then `In Rp 16.500.000` and `Out Rp 14.026.400`

### Requirement: Account named on every row, quietly
Every transaction row SHALL name its account, and its subcategory when set, as secondary text under the note, not as a coloured chip. A transfer row SHALL name both accounts.

#### Scenario: An ordinary BCA expense
- **WHEN** a row is a Spotify Premium expense on BCA
- **THEN** the row shows the category, then `BCA · Spotify Premium` in secondary text, then `−Rp 30.000`

### Requirement: Stats leads with the period total and ranked bars
The Stats tab SHALL show, for the selected side and period, the total as the largest figure, a one-line comparison with the previous period of the same length, and the categories as ranked rows with colour, name, whole percent, amount and a proportional bar, in descending amount order. Percentages SHALL come from `wholePercents`. The Stats tab SHALL NOT show a donut.

#### Scenario: October expenses
- **WHEN** the user opens Stats on October 2026, Expenses
- **THEN** it shows `Spent in October Rp 14.026.400`, `21% less than September`, then Pemberian 34%, Gadget 20%, Jajan & Hiburan 16%, Gym 16%, Makan & Minum 9% with bars scaled to Pemberian

#### Scenario: No previous period
- **WHEN** the previous period has no transactions
- **THEN** the comparison line says there is nothing in that period to compare with

### Requirement: Accounts leads with net worth and a split bar
The Accounts tab SHALL show net worth as the largest figure, then one horizontal bar split into Spendable and Invested shares with both amounts labelled. Liabilities, when any exist, SHALL appear as a third labelled figure. The Accounts tab SHALL NOT show a donut.

#### Scenario: October balances
- **WHEN** Spendable is 3.006.815 and Invested is 14.550.800
- **THEN** net worth reads `Rp 17.557.615` and the bar is about 17% Spendable and 83% Invested

### Requirement: Balance status wording
An account never checked SHALL read "Balance not checked yet" in secondary text. A checked account SHALL read "Checked" with the date. An account past the staleness threshold SHALL read "Checked" with the date in the warning colour. A balance below zero SHALL read "Below zero. Set an opening balance" in the warning colour. The action SHALL be named "Check balance".

#### Scenario: Freshly imported accounts
- **WHEN** six accounts have imported history and none was checked
- **THEN** each reads "Balance not checked yet" in secondary text, and only Cash, at −Rp 1.038.500, shows a warning

### Requirement: More is grouped
The More tab SHALL group its entries under Organise, Your data and App, and each entry SHALL carry a one-line subtitle saying what it does.

#### Scenario: Opening More
- **WHEN** the user opens More
- **THEN** Categories sits under Organise; backup, restore, the two imports, export and import history under Your data; Appearance under App

### Requirement: Text contrast floor
All text SHALL meet a contrast ratio of at least 4.5:1 against its background in both themes. The `ink3` token SHALL NOT be used for text.

#### Scenario: Secondary text in dark theme
- **WHEN** secondary text renders on the dark surface
- **THEN** its measured contrast is at least 4.5:1
