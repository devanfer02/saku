## MODIFIED Requirements

### Requirement: Price card in search
Search SHALL count the expense results per item key. Items with at least 2 rows SHALL be offered, most rows first, then most recently bought. With one such item, search SHALL show its price card above the tabs; with two or more, it SHALL show one chip per item with its row count, select the first, and show the selected item's card. The card SHALL show a line of unit price per date on a Y axis of 3 or 4 round gridlines, the latest unit price, the lowest and highest with their dates, and the change from the first to the latest in rupiah and percent. Two rows of the item on one day SHALL be one point at their average. A new query SHALL select the first item again.

The card SHALL offer range chips 6M, 1Y, 3Y and All, counted back from the latest point, showing only those that hold at least 2 points and fewer than the next longer range; it SHALL open on 1Y when offered, else All. The lowest, highest and change SHALL follow the range. When the shown points span more than 18 months and number more than 60, the line SHALL draw one point per month at the month's average. Tapping or dragging on the chart SHALL select the nearest point by date and show its date and unit price; TalkBack SHALL be able to step through the points.

#### Scenario: One item
- **WHEN** the user searches `minyak goreng 2l` and 4 rows from Jul to Oct match
- **THEN** the card shows 4 points, the October price as latest, and the change since July

#### Scenario: Several items
- **WHEN** the search `minyak` matches MINYAK GORENG 2L three times and MINYAK GORENG 1L twice
- **THEN** chips read Minyak Goreng 2L · 3 and Minyak Goreng 1L · 2, the 2L card shows, and tapping the 1L chip shows its card

#### Scenario: Nothing bought twice
- **WHEN** every item among the results appears once
- **THEN** no card and no chips show

#### Scenario: Ranges
- **WHEN** an item has rows from Jul 2025 to Oct 2026
- **THEN** the card offers 6M, 1Y and All, opens on 1Y, and its lowest and highest come from the last year

#### Scenario: Ten years
- **WHEN** an item has rows every month for ten years and All is picked
- **THEN** the line draws 120 monthly points

#### Scenario: Tapping a point
- **WHEN** the user taps the chart near 28 Jul 2025
- **THEN** a label above the chart reads 28 Jul 2025 and that day's unit price
