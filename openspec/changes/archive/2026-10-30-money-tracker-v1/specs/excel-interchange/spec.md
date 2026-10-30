## ADDED Requirements

### Requirement: Two import entry points, one parser
The app SHALL offer "Import from Money Manager" and "Import Excel" as separate entry points backed by a single
parser. The layout SHALL be detected from the header row, so either entry point accepts either file shape and the
labelling only sets expectations.

#### Scenario: Importing a Money Manager export
- **WHEN** the user picks a file whose header row matches the Money Manager layout
- **THEN** the Money Manager column handling is applied regardless of which entry point was used

#### Scenario: An unrecognised layout
- **WHEN** the selected file's header row matches neither layout
- **THEN** the import is refused before any parsing, with a message naming the columns that were expected and the ones that were found

### Requirement: Map the Money Manager column layout
The parser SHALL handle the reference export's eleven columns as follows: `Date` becomes the transaction timestamp
with sub-second precision truncated to seconds; the first `Account` runs through the account mapping step;
`Category` is matched by name and created when new; `Subcategory` is read though empty in the reference data;
`Note` becomes the note; `IDR` becomes the amount; `Income/Expense` maps `Expense` and `Income` directly and
`Transfer-Out` to a transfer; `Description` is ignored; `Amount` is cross-checked against `IDR`; `Currency` must be
IDR; and the second `Account` column, which duplicates the amount rather than naming an account, is discarded.

#### Scenario: The duplicate Account column
- **WHEN** a row's eleventh column contains the numeric amount rather than an account name
- **THEN** that column is discarded and the row's account comes from the second column alone

#### Scenario: A mismatch between IDR and Amount
- **WHEN** a row's `Amount` differs from its `IDR`
- **THEN** the row is listed in the preview as a discrepancy with both values shown, and the user decides whether to include it before the import runs

#### Scenario: A non-IDR row
- **WHEN** a row's `Currency` is anything other than IDR
- **THEN** the row is held back, reported in the preview, and never converted

### Requirement: Preview and confirm before writing
No import SHALL write any record until the user confirms. The preview SHALL show the file name, row count, date
span, the account mapping step, the category mapping outcome, the investment-destination mapping step, and an
exact tally of rows to be inserted, rows already present, and rows to be deleted.

#### Scenario: Reviewing an import
- **WHEN** the user selects a file of 1.200 rows of which 54 already exist
- **THEN** the preview reports 1.540 new, 54 already here and 0 deleted, and the database is unchanged until the user taps the import action

#### Scenario: Abandoning an import
- **WHEN** the user leaves the preview without confirming
- **THEN** no record of any kind has been created or modified

#### Scenario: The deleted count is structurally zero
- **WHEN** any import preview is shown
- **THEN** the deleted count reads zero, because no import path deletes or overwrites an existing transaction

### Requirement: Map unknown accounts, prefilling Card to BCA
The preview SHALL list every distinct account name found in the file and require each to be mapped to an existing
account or to a new one. `Card` SHALL be prefilled to BCA.

#### Scenario: Mapping the reference export
- **WHEN** a file contains the account names Card, Cash and Gopay
- **THEN** Card is prefilled to BCA, Cash is matched to the existing Cash account by name, and Gopay is offered as a new account to create or as a mapping to an existing one

#### Scenario: Import blocked on an unmapped account
- **WHEN** any account name in the file has no mapping selected
- **THEN** the import action is disabled and the unmapped name is highlighted

### Requirement: Give investment transfers a destination
`Transfer-Out` rows carry no destination in the reference export. The preview SHALL group them by category and
require a destination account for each group, so that imported transfers leave net worth unchanged.

#### Scenario: Mapping the six transfer rows
- **WHEN** the file contains Transfer-Out rows in the categories "RDPU SucorInvest" and "Saham"
- **THEN** the preview asks once per category for a destination account, and on import each row becomes a transfer whose destination is that account

#### Scenario: Import blocked on an unmapped transfer group
- **WHEN** a Transfer-Out category has no destination selected
- **THEN** the import action is disabled and that group is highlighted

### Requirement: Skip duplicates and merge into existing months
Each incoming row SHALL be reduced to a deduplication hash over its timestamp truncated to seconds, its type, its
amount, its **account**, its destination account, its category name and its note. A row whose hash matches an
existing transaction MUST be skipped. Importing a file overlapping a month that already holds data MUST add only
the rows not already present.

The hash is an import-time heuristic and MUST NOT be enforced as a uniqueness constraint on storage: two
transactions may legitimately share one, and refusing the second breaks ordinary entry.

#### Scenario: Two rows differing only by account are both imported
- **WHEN** the file holds two rows with the same timestamp, type, amount, category and note, paid from different accounts
- **THEN** both are imported, because they are two real transactions rather than one duplicate

#### Scenario: Re-importing an overlapping export
- **WHEN** the user imports a file containing August 2026 into a database that already holds 54 of that month's rows
- **THEN** those 54 are skipped, the remaining rows are inserted, and no existing transaction is modified

#### Scenario: A genuinely repeated purchase
- **WHEN** two rows in the file share a timestamp to the second, a type, an amount, a category and a note
- **THEN** they collapse to a single transaction, and the preview reports the collapsed count

### Requirement: Record every import as a reversible batch
Each confirmed import SHALL create a batch record holding the file name, the time, and the row, inserted and
skipped counts, with every inserted transaction referencing it. The user SHALL be able to reverse an entire batch.

#### Scenario: Reviewing past imports
- **WHEN** the user opens the import history
- **THEN** each past import is listed with its file name, date and counts

#### Scenario: Reversing a batch
- **WHEN** the user reverses an import batch and confirms
- **THEN** only the transactions inserted by that batch are removed, transactions created by hand or by other batches are untouched, and affected balances recompute

### Requirement: Export to a losslessly re-importable sheet
Export SHALL write a single sheet with the columns Date, Type, Amount IDR, Account, To Account, Category,
Subcategory and Note, through the system file picker to a location the user chooses. A Saku export re-imported into
an empty database MUST reproduce the exported transactions exactly.

#### Scenario: Round-tripping
- **WHEN** a database of transactions is exported and that file is imported into an empty database
- **THEN** every transaction is reproduced with its timestamp, type, amount, account, destination account, category, subcategory and note intact

#### Scenario: Exporting a transfer
- **WHEN** a transfer is exported
- **THEN** its row carries both the source account in Account and the destination in To Account

#### Scenario: No network involvement
- **WHEN** an export completes
- **THEN** the file has been written only to the location the user chose, with no upload and no share step
