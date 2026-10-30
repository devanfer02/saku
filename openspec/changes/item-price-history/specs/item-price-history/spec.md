## ADDED Requirements

### Requirement: Rows of one item are matched by name
An item key SHALL be the note trimmed, with repeated spaces collapsed, upper case, and without a trailing ` ×n` or ` x n` quantity. Expense rows with equal keys SHALL be the same item. The unit price of a row SHALL be its amount divided by n, rounded half up to whole rupiah, or its amount when there is no quantity.

#### Scenario: Quantity suffix
- **WHEN** rows have notes `SUSU UHT 1L ×2` at Rp 29.534 and `susu uht 1l` at Rp 18.200
- **THEN** both are the item SUSU UHT 1L, with unit prices Rp 14.767 and Rp 18.200

### Requirement: Price card in search
When every expense result of a search has the same item key and there are at least 2 results, search SHALL show a price card above the results with a line of unit price per date, the latest unit price, the lowest and highest with their dates, and the change from the first to the latest in rupiah and percent. Two rows of the item on one day SHALL be one point at their average. With results of more than one item key, no card SHALL show.

#### Scenario: One item
- **WHEN** the user searches `minyak goreng 2l` and 4 rows from Jul to Oct match
- **THEN** the card shows 4 points, the October price as latest, and the change since July

#### Scenario: Several items
- **WHEN** the search `minyak` matches MINYAK GORENG 2L and MINYAK GORENG 1L
- **THEN** no price card shows and the results show as before

### Requirement: Price history from a row
Opening an expense row SHALL offer Price history, which SHALL open search with that row's item key as the query.

#### Scenario: From the list
- **WHEN** the user opens the row MINYAK GORENG 2L and taps Price history
- **THEN** search opens on MINYAK GORENG 2L with the price card
