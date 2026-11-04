## ADDED Requirements

### Requirement: Rows of one item are matched by name
An item key SHALL be the note in its readable form (receipt short forms expanded, learned words applied), upper case, without spaces, and without a trailing quantity written as ` ×n`, ` x n`, ` nx`, ` nX`, ` n biji`, ` n pcs`, ` n porsi` or ` n bks`. Expense rows with equal keys SHALL be the same item; no fuzzier match SHALL join two keys. The unit price of a row SHALL be its amount divided by n, rounded half up to whole rupiah, or its amount when there is no quantity.

#### Scenario: Quantity suffix
- **WHEN** rows have notes `SUSU UHT 1L ×2` at Rp 29.534 and `susu uht 1l` at Rp 18.200
- **THEN** both are the item SUSU UHT 1L, with unit prices Rp 14.767 and Rp 18.200

#### Scenario: Count written first and spaces
- **WHEN** rows have notes `Es Teh` at Rp 5.000, `Esteh 2X` at Rp 10.000 and `es teh` at Rp 6.000
- **THEN** all three are one item with unit prices Rp 5.000, Rp 5.000 and Rp 6.000

#### Scenario: Short forms
- **WHEN** rows have notes `INDOMIE GRG SPCL ×2` and `Indomie goreng special`
- **THEN** both are one item, and `Indomie Goreng` is a different item

### Requirement: Price card in search
Search SHALL count the expense results per item key. Items with at least 2 rows SHALL be offered, most rows first, then most recently bought. With one such item, search SHALL show its price card above the tabs; with two or more, it SHALL show one chip per item with its row count, select the first, and show the selected item's card. The card SHALL show a line of unit price per date, the latest unit price, the lowest and highest with their dates, and the change from the first to the latest in rupiah and percent. Two rows of the item on one day SHALL be one point at their average. A new query SHALL select the first item again.

#### Scenario: One item
- **WHEN** the user searches `minyak goreng 2l` and 4 rows from Jul to Oct match
- **THEN** the card shows 4 points, the October price as latest, and the change since July

#### Scenario: Several items
- **WHEN** the search `minyak` matches MINYAK GORENG 2L three times and MINYAK GORENG 1L twice
- **THEN** chips read Minyak Goreng 2L · 3 and Minyak Goreng 1L · 2, the 2L card shows, and tapping the 1L chip shows its card

#### Scenario: Nothing bought twice
- **WHEN** every item among the results appears once
- **THEN** no card and no chips show

### Requirement: Price history from a row
Opening an expense row SHALL offer Price history, which SHALL open search with that row's note, without its quantity, as the query.

#### Scenario: From the list
- **WHEN** the user opens the row MINYAK GORENG 2L and taps Price history
- **THEN** search opens on MINYAK GORENG 2L with the price card
