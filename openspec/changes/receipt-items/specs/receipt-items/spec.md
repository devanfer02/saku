## ADDED Requirements

### Requirement: Scan from the Trans tab
The Trans tab SHALL show a Scan button beside the add button. Tapping it SHALL offer Take a photo and Choose from gallery, SHALL need no runtime permission, and SHALL open the Receipt screen with the first photo's reading. Backing out of the camera or picker SHALL leave Trans unchanged.

#### Scenario: Scan a receipt
- **WHEN** the user taps Scan on Trans and picks a receipt photo
- **THEN** the Receipt screen opens showing a reading state, then the store, date, total and items

#### Scenario: Cancel
- **WHEN** the user backs out of the photo picker
- **THEN** Trans is unchanged and no screen opens

### Requirement: Continue a receipt across several photos
The Receipt screen SHALL offer Add next part, which takes or picks another photo of the same receipt, placed below the previous ones. Rows printed in both of two neighbouring photos SHALL be kept once. When two neighbouring photos share no row, the screen SHALL say a part may be missing between them. An overlap SHALL need at least one shared row that carries an amount.

#### Scenario: Overlapping photos
- **WHEN** photo 1 ends with `BERAS PREMIUM 5KG 1 72.500 72.500` and `SUSU UHT 1L 2 18.200 36.400`, and photo 2 starts with the same two rows
- **THEN** the item list has each of those items once

#### Scenario: Missing part
- **WHEN** photo 2 ends at `SABUN CUCI 800ML 1 21.900 21.900` and photo 3 starts at `HARGA JUAL : 226.100`
- **THEN** the screen says a part may be missing between photo 2 and 3

#### Scenario: Same item twice in one photo
- **WHEN** one photo has the row `SUSU UHT 1L 1 18.200 18.200` twice in a row
- **THEN** the join keeps both lines, and item reading merges them into quantity 2

### Requirement: Read items, quantities and discounts
Saku SHALL read each item between the receipt header and the subtotal or total as a name, a quantity and a line price. A quantity SHALL be kept only when quantity times unit price equals the line price; otherwise the quantity SHALL be 1. A discount row directly under an item SHALL reduce that item. A discount row between the items and the total SHALL be a receipt discount.

#### Scenario: Indomaret item with a discount
- **WHEN** the rows are `SUSU UHT 1L 2 18.200 36.400` then `DISKON : (4.000)`
- **THEN** the item is SUSU UHT 1L, quantity 2, price Rp 36.400, discount Rp 4.000, net Rp 32.400

#### Scenario: Same item on two lines
- **WHEN** one photo has the row `SUSU UHT 1L 1 18.200 18.200` twice and no discount under either
- **THEN** they become one item SUSU UHT 1L, quantity 2, price Rp 36.400

#### Scenario: Two-row item
- **WHEN** the rows are `MINYAK GORENG 2L` then `2 x 38.900 77.800`
- **THEN** the item is MINYAK GORENG 2L, quantity 2, price Rp 77.800

#### Scenario: Card discount
- **WHEN** after the items the rows are `HARGA JUAL : 226.100`, `DISKON KARTU : (20.000)` and `TOTAL : 206.100`
- **THEN** Rp 20.000 is a receipt discount and the total is Rp 206.100

### Requirement: Spread a receipt discount over items
A receipt discount SHALL be shared over the items in proportion to each item's net price, in whole rupiah, with leftover rupiah going one each to the items with the largest remainders, ties to the earlier item. The items' saved amounts SHALL add up exactly to the item net sum minus the receipt discounts. Tax or service rows added on top of the items SHALL be shared the same way, as additions.

#### Scenario: Card discount spread
- **WHEN** a Rp 20.000 receipt discount is spread over items of Rp 15.500, 38.900, 72.500, 32.400, 21.900 and 44.900
- **THEN** the amounts are Rp 14.129, 35.459, 66.087, 29.534, 19.963 and 40.928, totalling Rp 206.100

### Requirement: Check the items against the total
The Receipt screen SHALL show the items' sum after all discounts next to the receipt total. When they differ, it SHALL show the difference, and SHALL offer to save the difference as one extra row, off by default. Saving SHALL never be blocked by a difference.

#### Scenario: Items add up
- **WHEN** the items after discounts add up to the total
- **THEN** the check shows they match

#### Scenario: A part not photographed
- **WHEN** the items add up to Rp 161.200 and the total is Rp 206.100
- **THEN** the screen shows Rp 44.900 not found, with Add next part and the option to save the difference as a row

### Requirement: Category per item from history
Each item SHALL get the category most often used by expense rows whose note equals its name, ignoring case, repeated spaces and a trailing ` ×n` quantity, marked as coming from history. An item with no such note SHALL have no category, and Save SHALL ask for the missing categories, with one choice that applies to all items still unset. The user SHALL be able to change any item's category.

#### Scenario: Known item
- **WHEN** an earlier expense with note `MINYAK GORENG 2L` was saved under Makan & Minum
- **THEN** the item MINYAK GORENG 2L shows Makan & Minum from history

#### Scenario: Known item with a quantity
- **WHEN** an earlier expense with note `SUSU UHT 1L ×2` was saved under Makan & Minum
- **THEN** the item SUSU UHT 1L bought once shows Makan & Minum from history

#### Scenario: New item
- **WHEN** no earlier note matches DETERJEN 1.8KG
- **THEN** the item shows Choose, and Save asks for its category

### Requirement: Record as one row or one row per item
The Receipt screen SHALL offer One row and One row per item. One row SHALL save one expense with the total as amount, the store as note and one chosen category. One row per item SHALL save one expense per item with its spread amount, the item name as note followed by ` ×n` when its quantity n is above 1, and its own category. Both SHALL use the receipt date when read and within the last year (else today), the scan time of day, and the chosen account, and SHALL save all rows in one database transaction. A reading with no items SHALL offer One row only.

#### Scenario: Per item
- **WHEN** the user saves the six items of the card-discount example per item to Card
- **THEN** six expense rows totalling Rp 206.100 are saved, one with note `SUSU UHT 1L ×2` and amount Rp 29.534

#### Scenario: One row
- **WHEN** the user saves the same receipt as One row under Makan & Minum
- **THEN** one expense of Rp 206.100 with note Indomaret is saved

#### Scenario: Payment screenshot
- **WHEN** the user scans a GoPay payment screenshot
- **THEN** the screen offers One row only, with the total, merchant and date as v1 reads them

#### Scenario: Write fails
- **WHEN** saving fails part-way
- **THEN** no row from that receipt is saved

### Requirement: Tag the receipt's rows
The Receipt screen SHALL have an optional tag for all rows it saves, defaulting to the tag used on the last receipt save. The user SHALL be able to change or clear it.

#### Scenario: Monthly grocery again
- **WHEN** the last receipt was saved with Monthly grocery and the user scans a new one
- **THEN** the tag shows Monthly grocery, and every saved row carries it

### Requirement: Item accuracy is measured
The project SHALL keep OCR fixtures of synthetic long receipts photographed in overlapping and gapped parts, angled, curled and shadowed, and a JVM test SHALL fail if fewer than 80% of their items are read with the right name and price, if any overlap leaves a duplicate item, or if any gap is not flagged. Before release, at least 80% of items SHALL be read right on the local set of real photos too.

#### Scenario: Regression caught
- **WHEN** a parser change drops item accuracy on the synthetic fixtures below 80%
- **THEN** the unit test suite fails and names the rate

### Requirement: Readable item names
Saku SHALL turn a printed item name into a readable one for display and for the saved note: known till abbreviations expanded by whole token, other words title-cased, tokens holding a digit kept as printed, and a trailing quantity written ` ×n`. A readable name SHALL read the same again. The user SHALL be able to edit the name on the Receipt screen before saving. The category lookup SHALL try the readable name's item key and then the printed name's.

#### Scenario: Owner's example
- **WHEN** the printed name is `INDOMIE GRG SPCL x2`
- **THEN** Saku shows and saves `Indomie Goreng Special ×2`

#### Scenario: Sizes stay
- **WHEN** the printed name is `DETERJEN 1.8KG`
- **THEN** the name is `Deterjen 1.8KG`

#### Scenario: Unknown word
- **WHEN** the printed name is `PRENDJAK TEH CELU`
- **THEN** the name is `Prendjak Teh Celu`

#### Scenario: Earlier rows still match
- **WHEN** an earlier expense with note `INDOMIE GRG SPCL` was saved under Makan & Minum
- **THEN** the item printed `INDOMIE GRG SPCL` shows `Indomie Goreng Special` under Makan & Minum from history

### Requirement: Learn words from edited names
When the user saves per item after editing an item's name, Saku SHALL learn from the edit: with as many words as the printed name, each changed word under its printed token, upper case, except tokens holding a digit; otherwise the whole name under the printed name's item key. Learned words SHALL override the built-in expansions and SHALL be used on later receipts. A later edit of the same token SHALL overwrite it. Past rows SHALL NOT be renamed. Learned words SHALL be kept in backups and restored without overwriting words the database already has.

#### Scenario: Learn a word
- **WHEN** the user edits `Beras Premium 5KG` (printed `BRS PREMIUM 5KG`) to `Buris Premium 5KG` and saves
- **THEN** a later receipt's `BRS PULEN 5KG` reads `Buris Pulen 5KG`

#### Scenario: Whole name
- **WHEN** the user edits `Idm F/T 2PLY 3X180'S` to `Tisu Toilet Indomaret 2 ply`
- **THEN** that printed name reads `Tisu Toilet Indomaret 2 ply` from then on

#### Scenario: Sizes are not learned
- **WHEN** the user edits `Beras Premium 5KG` to `Beras Premium 10KG`
- **THEN** nothing is learned

#### Scenario: Backup round trip
- **WHEN** a backup with learned words is restored onto a database that has none
- **THEN** the words are there; a word the database already had keeps its reading

## REMOVED Requirements

### Requirement: Scan a receipt from the entry sheet
**Reason**: Scanning starts from the Scan button on the Trans tab (owner, 31 Oct 2026); one place to scan, and the Receipt screen covers a single total with One row.
**Migration**: Use Scan on the Trans tab, then One row. The other-amount chips move to the Receipt screen's total.
