# period-stats Specification

## Purpose
The Stats tab: period modes, income and expense sides, categories ranked by amount, and drill-through to the transactions behind a figure.
## Requirements
### Requirement: Default to the current month, offer four period modes
The stats screen SHALL open showing the current calendar month. It SHALL offer four period modes, Weekly, Monthly,
Annually and Period, where Period accepts an arbitrary start and end date. The selected range SHALL be displayed
as explicit start and end dates.

#### Scenario: Opening stats
- **WHEN** the user opens the Stats tab
- **THEN** the mode is Monthly, the range reads the first and last day of the current month, and the figures cover that month

#### Scenario: Switching to a custom period
- **WHEN** the user selects Period and picks 01/02/2026 to 31/07/2026
- **THEN** all figures and the ranked list recompute for that range

#### Scenario: Navigating within a mode
- **WHEN** the mode is Monthly and the user steps to the previous period
- **THEN** the range moves back one calendar month and the figures recompute

### Requirement: Separate income and expense views
The screen SHALL provide an Income tab and an Expenses tab, each showing that side's total for the period and a
ranked category list. Expenses SHALL be the default tab. Transfers and adjustments MUST be excluded from both.

#### Scenario: A period containing transfers
- **WHEN** the period holds 5.061.400 of expenses and a 500.000 transfer to an investment account
- **THEN** the Expenses total reads 5.061.400, and the transfer appears in neither tab

### Requirement: Rank categories by amount with share and value
Each tab SHALL list categories for the period in descending order of amount, showing each category's colour, name,
percentage share of the period total, and amount.

#### Scenario: Ranked list for a month
- **WHEN** the period's expenses are Perawatan Diri 1.791.900, Makan & Minum 1.257.700, Pemberian 1.050.000, Jajan & Hiburan 843.800, Obat 80.000 and Kebutuhan Kos 38.000
- **THEN** the list shows those six in that order, each with its share rounded to a whole percent and its amount

#### Scenario: A period with no data
- **WHEN** the selected period contains no transactions of the selected side
- **THEN** the ranked list is replaced by an empty state naming the period, and the total reads zero

### Requirement: Chart category composition
Each ranked row, and each slice of the optional pie, SHALL be coloured by its category's pinned colour slot. The
ranked list SHALL name every category so that identity never depends on colour alone. The bars and the pie are set
out in the `buku-kas` requirements.

#### Scenario: Colour follows the category, not its rank
- **WHEN** the selected period changes such that a category moves from first to fourth place
- **THEN** that category keeps the same colour it had before, and no other category's colour changes

#### Scenario: More than eight categories in a period
- **WHEN** a period contains more than eight categories on one side
- **THEN** the largest eight are ranked individually and the remainder are combined into a single "Other" row, which opens a detailed list on tap

### Requirement: Drill through to the underlying transactions
Tapping a ranked list row SHALL open the category's detail, ending with the list of transactions making up that
figure, scoped to the selected period and category.

#### Scenario: Opening a category's transactions
- **WHEN** the user taps the "Makan & Minum" row for August 2026
- **THEN** a list of that month's Makan & Minum transactions opens, showing the same total as the row

#### Scenario: Drilling into Other
- **WHEN** the user taps the "Other" row
- **THEN** the categories it combines are listed with their individual figures before any transaction list is opened

