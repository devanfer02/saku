## MODIFIED Requirements

### Requirement: Per-month breakdown of matches
The search results SHALL have Daily, Monthly and Yearly tabs under the summary. Monthly SHALL hold one row per month from the newest month with a match back to the oldest, newest first, including every month in between that has no match. Each row SHALL show the month and year, the matching rows' money out, the money in when it is not zero, and the number of matching rows. Transfers and adjustments SHALL count toward a month's number of rows but SHALL NOT count toward its money in or out, the same rule as the summary totals. Yearly SHALL hold one row per year the same way. Both SHALL reflect the active filter. Search SHALL open on Monthly when two or more months have matches, else on Daily, and SHALL keep a tab the user picks while the query changes.

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
- **THEN** search opens on Daily, and Monthly shows one row for October

#### Scenario: Years
- **WHEN** "arisan" matches in 2024 and 2026 only
- **THEN** Yearly reads 2026, 2025 with no matches, 2024

### Requirement: Jump to a month
Tapping a month row that has matches SHALL open Daily at that month's newest day. Tapping a year row that has matches SHALL open Monthly at that year's newest month with a match. A month or year with no matches SHALL NOT be tappable. The summary and the tabs SHALL stay in place while a tab's list scrolls.

#### Scenario: Jumping back
- **WHEN** the user taps August in the list
- **THEN** Daily opens with August's newest day header at the top

#### Scenario: From a year
- **WHEN** the user taps 2025 in Yearly
- **THEN** Monthly opens with 2025's newest month with a match at the top
