## ADDED Requirements

### Requirement: Per-month breakdown of matches
When the matches of a search fall in two or more calendar months, the search screen SHALL show a "By month" list under the summary. It SHALL hold one row per month from the newest month with a match back to the oldest, newest first, including every month in between that has no match. Each row SHALL show the month and year, the matching rows' money out, the money in when it is not zero, and the number of matching rows. Transfers and adjustments SHALL count toward a month's number of rows but SHALL NOT count toward its money in or out, the same rule as the summary totals. The list SHALL reflect the active filter.

#### Scenario: A trend over three months
- **WHEN** "arisan" matches Rp 500.000 in August, nothing in September and Rp 750.000 in October
- **THEN** the list reads October Rp 750.000, September with no matches, August Rp 500.000, in that order

#### Scenario: Money in shown only when present
- **WHEN** a month's matches are all expenses
- **THEN** its row shows the money out and the count, and no money-in figure

#### Scenario: Transfers counted but not summed
- **WHEN** a month holds one matching expense of Rp 100.000 and one matching transfer of Rp 1.000.000
- **THEN** that row reads Rp 100.000 out and 2 transactions

#### Scenario: Filter respected
- **WHEN** a filter on the Card account is active and "arisan" matches Cash rows in September only outside it
- **THEN** September's row counts only the Card matches

#### Scenario: One month only
- **WHEN** every match falls in October
- **THEN** no "By month" list is shown

### Requirement: Bars on one scale
Each month row SHALL draw a bar against the largest month on the same side. The side SHALL be money out, unless the matches' total money in is larger than their total money out, in which case it SHALL be money in.

#### Scenario: Largest month fills the bar
- **WHEN** October has the largest money out among the months listed
- **THEN** October's bar is full and every other bar is shorter in proportion

#### Scenario: An income search
- **WHEN** "gaji" matches only income
- **THEN** each row's value and bar show the money in

### Requirement: Jump to a month
Tapping a month row that has matches SHALL scroll the results to that month's newest day. A month with no matches SHALL NOT be tappable. The breakdown, the summary and the day rows SHALL scroll as one list.

#### Scenario: Jumping back
- **WHEN** the user taps August in the list
- **THEN** the results scroll so August's newest day header is at the top
