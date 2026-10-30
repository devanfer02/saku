## ADDED Requirements

### Requirement: Monthly sub-tab in Trans
The Trans screen SHALL show three sub-tabs: Daily, Calendar and Monthly, in that order. Selecting Monthly SHALL keep the month the user was viewing, and returning to Daily or Calendar SHALL show that same month.

#### Scenario: Switching to Monthly
- **WHEN** the user is on Daily for March 2026 and taps Monthly
- **THEN** the Monthly view for 2026 is shown

#### Scenario: Switching back
- **WHEN** the user goes from Daily for March 2026 to Monthly and back to Daily
- **THEN** Daily shows March 2026

### Requirement: Year header
In the Monthly view the period header SHALL read the year, with previous and next arrows that step one calendar year, and SHALL show Income, Expenses and Total for that year. Transfers and adjustments SHALL be excluded from all three, as in the month header.

#### Scenario: Stepping back a year
- **WHEN** the Monthly view shows 2026 and the user taps the previous arrow
- **THEN** the header reads 2025, its three figures cover 1 Jan 2025 to 31 Dec 2025, and the month rows are 2025's

#### Scenario: Year totals
- **WHEN** 2026 holds 96.000.000 of income and 61.000.000 of expenses
- **THEN** the header reads Income 96.000.000, Expenses 61.000.000, Total 35.000.000

### Requirement: Month rows
The Monthly view SHALL list one row per month of the year, newest first, from the latest month that has started down to January. Each row SHALL show the month name, its date span, and that month's income, expenses and net. A month with no income and no expense SHALL render its figures as empty, not as zero. Months that have not started SHALL NOT be listed.

#### Scenario: The current year
- **WHEN** today is 30 Oct 2026 and the view shows 2026
- **THEN** ten rows are listed, October first and January last, and November and December are absent

#### Scenario: A past year
- **WHEN** the view shows 2025
- **THEN** twelve rows are listed, December first

#### Scenario: An unlogged month
- **WHEN** a month in the year holds no income or expense
- **THEN** its row shows the month and span with empty figures rather than three zeros

### Requirement: Week rows within a month
A month row SHALL expand to show its weeks. Weeks SHALL run Monday to Sunday, the same weeks the Calendar grid and the Stats Weekly mode use. Every week that overlaps the month and has started SHALL be listed, newest first, labelled with its full span as "dd.MM ~ dd.MM" even when the span crosses into the next or previous month. Each week row SHALL show income, expenses and net for the whole week, including its days in the neighbouring month.

#### Scenario: October 2026 expanded
- **WHEN** today is 30 Oct 2026 and October is expanded
- **THEN** five week rows are listed: 26.10 ~ 01.11, 19.10 ~ 25.10, 12.10 ~ 18.10, 05.10 ~ 11.10 and 28.09 ~ 04.10

#### Scenario: A week crossing the month edge
- **WHEN** 28 Sep holds a 100.000 expense and 2 Oct holds a 50.000 expense
- **THEN** the 28.09 ~ 04.10 row under October reads Expenses 150.000, and the same week under September also reads 150.000

#### Scenario: A week that crosses the year edge
- **WHEN** January 2026 is expanded
- **THEN** its oldest week reads 29.12 ~ 04.01 and its figures include 29 to 31 Dec 2025

### Requirement: Which month is expanded
When the Monthly view shows the current year, the current month SHALL be expanded on opening. In any other year no month SHALL be expanded on opening. Tapping a month row SHALL expand it or, if it is expanded, collapse it. At most one month SHALL be expanded at a time.

#### Scenario: Opening on the current year
- **WHEN** the user opens Monthly in October 2026
- **THEN** October is expanded and every other month is collapsed

#### Scenario: Expanding another month
- **WHEN** October is expanded and the user taps August
- **THEN** August expands and October collapses

### Requirement: Current week highlight
The week row containing today SHALL be visually highlighted, and the highlight SHALL NOT depend on colour alone.

#### Scenario: Highlighted week
- **WHEN** today is 30 Oct 2026 and October is expanded
- **THEN** the 26.10 ~ 01.11 row is highlighted and marked as this week

### Requirement: Opening a week in Daily
Tapping a week row SHALL open the Daily view for the month the row is listed under, scrolled to the latest day of that week inside that month.

#### Scenario: Opening a week
- **WHEN** the user taps 12.10 ~ 18.10 under October 2026
- **THEN** Daily opens on October 2026 scrolled to 18 Oct, or to the nearest earlier day with rows

### Requirement: Monthly respects the filter
Every figure in the Monthly view SHALL be computed under the active filter, and the active-filter bar SHALL be shown above the month rows while a filter is active.

#### Scenario: Filtered year
- **WHEN** the account selection is Cash and the view shows 2026
- **THEN** the year header, every month row and every week row count only Cash rows
