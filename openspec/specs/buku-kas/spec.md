# buku-kas Specification

## Purpose
The Buku Kas visual pass: one money format, signed amounts, the leading figure on each tab, the Stats strip and pie toggle, the More grouping, and the text contrast floor.
## Requirements
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
Every transaction row SHALL name its account and its note as secondary text under the category, not as a coloured chip. A transfer row SHALL name both accounts.

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

### Requirement: Six-month strip on Stats
In the Monthly period mode, the Stats tab SHALL show, above the period total, a month strip whose first view is the six months ending with the selected month, as paired bars: income and expense per month on one shared scale, the month's short name under each pair, and the month's signed net under the name in compact form (`+2,5 jt`, `−1,4 jt`). The selected month SHALL be drawn at full strength and the other five muted. Tapping a month SHALL select it. The strip SHALL respect the active transaction filter, and SHALL exclude transfers and adjustments as the period totals do. Months before the first transaction SHALL render as empty slots, not as zero bars.

#### Scenario: October 2026 selected
- **WHEN** the user opens Stats in Monthly mode on October 2026
- **THEN** May to October are drawn, October at full strength, and the labels under the months read `+0,8 jt`, `+7,1 jt`, `−0,3 jt`, `−1,4 jt`, `+1,4 jt`, `+2,5 jt`

#### Scenario: Tapping a month
- **WHEN** the user taps Aug in the strip
- **THEN** the selected period becomes August 2026 and the strip now ends with August

#### Scenario: Early history
- **WHEN** the selected month is August 2025 and data starts in July 2025
- **THEN** March to June 2025 show as empty slots and July and August show bars

### Requirement: Compact money form
The single money implementation SHALL offer a compact form for chart labels: the amount in millions (`jt`) with one decimal and a decimal comma, signed, computed from the rupiah `Long` with half-up rounding and no floating point.

#### Scenario: Rounding
- **WHEN** a month nets 2.473.600 and another nets −1.383.600
- **THEN** they read `+2,5 jt` and `−1,4 jt`

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
- **THEN** Categories sits under Organise; backup, restore, import database, the two Excel imports, export and import history under Your data; Appearance and the reminder under App

### Requirement: Text contrast floor
All text SHALL meet a contrast ratio of at least 4.5:1 against its background in both themes. The `ink3` token SHALL NOT be used for text.

#### Scenario: Secondary text in dark theme
- **WHEN** secondary text renders on the dark surface
- **THEN** its measured contrast is at least 4.5:1

### Requirement: The month strip scrolls through all history
The Stats month strip (Monthly mode) SHALL hold every calendar month from the month of the first income or expense that passes the active filter through the selected month, scroll horizontally, and open scrolled so the selected month is the rightmost visible column. Columns SHALL keep a fixed width so six fit on a 412 dp screen. All bars SHALL share one scale across the whole history.

#### Scenario: Opening October 2026
- **WHEN** the first transaction is in July 2025 and the user opens Stats on October 2026, Monthly
- **THEN** the strip holds Jul 2025 to Oct 2026, shows May to Oct 2026 without scrolling, and scrolling right-to-left reveals the earlier months

#### Scenario: Tapping an earlier month
- **WHEN** the user scrolls back and taps Jan 2026
- **THEN** January 2026 becomes the selected month and the strip ends at January 2026, scrolled so it is the rightmost visible column

### Requirement: Chart style choice on Stats
The Stats overview SHALL offer a single pie toggle beside the headline that shows or hides a pie chart above the ranked rows, defaulting to off. The choice SHALL persist across restarts. The pie SHALL use the category colour slots, cap at eight slices with the rest as "Other" exactly as the ranked rows do, and show the side total in its centre without clipping. The ranked rows SHALL stay below the pie as its legend.

#### Scenario: Switching to pie
- **WHEN** the user turns the pie toggle on for October 2026 expenses
- **THEN** a pie with Pemberian at 34% and the other slices appears above the ranked rows, and Stats opens in pie style after a restart

### Requirement: Tappable rows look tappable
Every category row on Stats that opens a detail SHALL show a trailing chevron and a pressed state, and its accessibility label SHALL say it opens the category.

#### Scenario: Reading the overview
- **WHEN** the user looks at the ranked rows
- **THEN** each row ends with a chevron, and "Other" shows one too because it opens its members

### Requirement: Category detail shows trend
Tapping a category SHALL open its detail for the selected period, showing in order: the category's total, its share of the side total, its transaction count and average per transaction; that category's own strip across all history, in every mode but Period; then its transactions. It SHALL NOT show a subcategory split. The filter SHALL apply as on the overview.

#### Scenario: Pemberian in October 2026
- **WHEN** the user taps Pemberian on October 2026 expenses
- **THEN** the detail shows Rp 4.821.600, 34% of spending, its count and average, Pemberian's strip, then its October transactions

### Requirement: Spendable and Invested are told apart by colour
The net worth split bar SHALL draw Spendable and Invested in two colours of clearly different hue, each at least 3:1 against the surface in both themes, and the legend dots SHALL use the same colours. Liabilities, when present, SHALL use a third distinct colour.

#### Scenario: Dark theme
- **WHEN** the Accounts tab renders in dark theme
- **THEN** the two segments differ in hue, not only in lightness, and both meet 3:1 against the surface

### Requirement: Net worth label size
The "Net worth" label above the figure SHALL be set 20% larger than the caption style it used at first.

#### Scenario: Reading Accounts
- **WHEN** the user opens Accounts
- **THEN** the "Net worth" label is visibly larger than the legend text under the bar

### Requirement: The strip follows the period mode
The scrollable strip SHALL show in Weekly, Monthly and Annually modes, with one column per week, month or year respectively, across all history up to the selected period, and SHALL NOT show in Period (custom range) mode. Its header SHALL name the unit ("Weeks", "Months", "Years"). Weeks SHALL be Monday to Sunday, as everywhere else in the app. Column labels SHALL be the week's start date (`26 Oct`), the month's short name, or the year. Tapping a column SHALL select that period.

#### Scenario: Weekly mode
- **WHEN** the user opens Stats in Weekly mode on the week of 26 Oct 2026
- **THEN** the strip shows one column per week from the first transaction's week to 26 Oct, scrolled so 26 Oct is rightmost, headed "Weeks"

#### Scenario: Annually mode
- **WHEN** the user switches to Annually on 2026
- **THEN** the strip shows 2025 and 2026, headed "Years"

#### Scenario: Period mode
- **WHEN** the user picks a custom Period
- **THEN** no strip is shown
