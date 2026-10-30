## MODIFIED Requirements

### Requirement: The month strip scrolls through all history
The Stats month strip (Monthly mode) SHALL hold every calendar month from the earlier of the month of the first income or expense that passes the active filter and the selected month, through the latest of the last month with such a transaction, the current month and the selected month, followed by three more months shown as zeros. It SHALL scroll horizontally and SHALL open scrolled so the selected month is visible, with the month after it beside it when there is one. Columns SHALL keep a fixed width so six fit on a 412 dp screen. All bars SHALL share one scale across every column. Tapping any column, a future one included, SHALL select its month.

#### Scenario: Opening October 2026
- **WHEN** the first transaction is in July 2025, today is in October 2026 and the user opens Stats on October 2026, Monthly
- **THEN** the strip holds Jul 2025 to Jan 2027, November 2026 to January 2027 read as zeros, and October 2026 is visible with November beside it

#### Scenario: Tapping an earlier month
- **WHEN** the user scrolls back and taps Jan 2026
- **THEN** January 2026 becomes the selected month, the strip still runs to Jan 2027, and February to the future months stay a scroll to the right

#### Scenario: A future-dated transaction
- **WHEN** today is in October 2026 and an expense is dated 5 March 2027
- **THEN** the strip runs to June 2027, March 2027 shows that expense, and the axis scales to it if it is the largest figure

### Requirement: The strip follows the period mode
The scrollable strip SHALL show in Weekly, Monthly and Annually modes, with one column per week, month or year respectively, across all history and on to three periods past the latest of the last period with data, the current period and the selected period, and SHALL NOT show in Period (custom range) mode. Its header SHALL name the unit ("Weeks", "Months", "Years"). Weeks SHALL be Monday to Sunday, as everywhere else in the app. Column labels SHALL be the week's start date (`26 Oct`), the month's short name, or the year. Tapping a column SHALL select that period. With no history at all, the strip SHALL be the selected period alone as an empty slot.

#### Scenario: Weekly mode
- **WHEN** today is 30 Oct 2026 and the user opens Stats in Weekly mode on the week of 26 Oct 2026
- **THEN** the strip shows one column per week from the first transaction's week to the week of 16 Nov, with 26 Oct in view, headed "Weeks"

#### Scenario: Annually mode
- **WHEN** data runs from 2025 to 2026, today is in 2026 and the user switches to Annually on 2026
- **THEN** the strip shows 2025 to 2029, headed "Years"

#### Scenario: Period mode
- **WHEN** the user picks a custom Period
- **THEN** no strip is shown
