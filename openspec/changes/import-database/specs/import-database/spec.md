## ADDED Requirements

### Requirement: A database file is picked and opened read-only
More > Your data SHALL list "Import database (.db)". Choosing it SHALL open the Storage Access Framework open-document picker. The picked file SHALL be copied to the app's cache directory and opened read-only from there. The app's own database file SHALL never be replaced, overwritten or attached.

#### Scenario: Picker cancelled
- **WHEN** the user cancels the picker
- **THEN** nothing is read, nothing is written and no error is shown

#### Scenario: Not a database
- **WHEN** the picked file is not a SQLite database
- **THEN** the screen says the file could not be read as a database and nothing is written

### Requirement: The file must match the app's schema before anything is read
The import SHALL compare the file's `PRAGMA user_version` with the app's Room schema version and the identity hash in its `room_master_table` with the app's schema identity hash. Either mismatch, or a missing `room_master_table`, SHALL refuse the file before any row is read.

#### Scenario: Different schema version
- **WHEN** the file's `user_version` is not the app's schema version
- **THEN** the screen says the database was made for a different version of Saku and nothing is written

#### Scenario: Same version, different layout
- **WHEN** the `user_version` matches but the identity hash does not
- **THEN** the file is refused as not matching Saku's database layout

#### Scenario: Not a Saku database
- **WHEN** the file has no `room_master_table`
- **THEN** the file is refused as not a Saku database

### Requirement: Rows are checked and adapted to the backup restore's shapes
Accounts, categories, subcategories and transactions SHALL be adapted to the same records a backup restore reads, keyed by name. The import SHALL refuse a file whose rows hold an unknown type or kind, an invalid colour slot, a reference to a missing row, two records of one name, or a transaction whose category kind does not match its type.

#### Scenario: Archived records and opening balances carry over
- **WHEN** the file holds an archived category and an account with an opening balance and date, and neither exists here
- **THEN** both are created with those settings

#### Scenario: Transfers and signed adjustments are preserved
- **WHEN** the file holds a transfer between two accounts and a negative adjustment
- **THEN** the transfer arrives with both accounts and the adjustment keeps its sign

### Requirement: Import previews before it writes
The import SHALL show a preview with the file name, the counts of accounts, categories and transactions in the file, the first and last transaction dates, the new accounts, categories and transactions to be added, the transactions already present, and every account whose settings differ from the existing account of the same name. Nothing SHALL be written until the user confirms.

#### Scenario: Preview then confirm
- **WHEN** the user picks a valid file and confirms
- **THEN** the missing records are created, the new transactions are inserted as one import batch named after the file, and the screen reports what was added

#### Scenario: Existing account keeps its settings
- **WHEN** the file and this phone share an account name with different opening balances
- **THEN** the preview lists that account, and the import keeps this phone's settings

### Requirement: Import is insert-only and repeatable
The import SHALL only insert. Records SHALL merge into existing accounts and categories by name, and transactions already present SHALL be skipped by their de-duplication hash. The batch SHALL appear in Import history and can be reversed there.

#### Scenario: Importing the same file twice
- **WHEN** the user imports a file and then imports it again
- **THEN** the second preview shows nothing new and confirm is disabled
