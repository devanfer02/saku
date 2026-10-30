## ADDED Requirements

### Requirement: Pick the period from the header
In Weekly, Monthly and Annually modes, the period label in the Stats header SHALL be a button that opens a picker for the active mode. Monthly SHALL show a year with previous and next year controls over a grid of the twelve months, three across and four down. Annually SHALL show the years from the first year with data (five years back when there is none) through next year, always including the selected year. Weekly SHALL show a date picker and select the Monday to Sunday week holding the picked date. In Monthly and Annually the period containing today SHALL be marked and the selected period highlighted, and neither SHALL be conveyed by colour alone. Every picker SHALL offer an action that selects the period containing today ("This week", "This month", "This year"). Picking SHALL select the period as stepping does, so an open category detail stays open. In Period mode the label SHALL NOT open a picker; the Period tab keeps its date range picker. The transaction list's period label SHALL stay plain.

#### Scenario: Jumping to an earlier month
- **WHEN** the mode is Monthly on October 2026 and the user taps the label, steps the year back to 2025 and taps Mar
- **THEN** the selected period becomes March 2025 and every figure recomputes for it

#### Scenario: Back to now
- **WHEN** the mode is Annually on 2023 and the user taps the label and then "This year"
- **THEN** the selected period becomes the year holding today

#### Scenario: A week from a date
- **WHEN** the mode is Weekly and the user picks Thursday 15 Oct 2026
- **THEN** the selected period becomes 12 to 18 Oct 2026

#### Scenario: Reading the month grid
- **WHEN** today is in October 2026, August 2026 is selected and the grid shows 2026
- **THEN** Oct is marked as current and Aug is highlighted as selected, and TalkBack says so for each
