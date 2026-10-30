## ADDED Requirements

### Requirement: One backup file holds the whole app
The app SHALL write a backup as a single `.xlsx` workbook containing, in this order: a `Saku` sheet of transactions in exactly the layout of the existing Saku export, an `Accounts` sheet, a `Categories` sheet, a `Subcategories` sheet, and a `Backup` sheet recording the format version, the app version and the moment it was written. Archived records SHALL be included and marked as archived.

#### Scenario: Contents of a backup
- **WHEN** the user backs up a database with accounts, categories, subcategories and transactions, some of them archived
- **THEN** the file has the five sheets, every record appears once, and archived ones carry an archived flag

#### Scenario: Accounts keep what the transaction sheet cannot
- **WHEN** an account has an opening balance, an opening date, a type, a colour slot, a sort order and a last-reconciled date
- **THEN** its row in the `Accounts` sheet holds all six

#### Scenario: Ordinary import still reads a backup
- **WHEN** the user opens a backup file with "Import Excel" instead of "Restore from backup"
- **THEN** the `Saku` sheet is recognised and imported as transactions, exactly as an export is

### Requirement: Writing a backup goes through the system file picker
Backup SHALL save through the Storage Access Framework create-document picker, suggesting the name `Saku backup YYYY-MM-DD.xlsx`, and SHALL use no network. The More destination SHALL list "Back up everything" and "Restore from backup".

#### Scenario: Saving a backup
- **WHEN** the user taps More > Back up everything and picks a location
- **THEN** the file is written there and the screen reports the record counts written

#### Scenario: Picker cancelled
- **WHEN** the user cancels the picker
- **THEN** nothing is written and no error is shown

### Requirement: Restore previews before it writes
Restoring SHALL first show a preview: the backup's date and format version, counts of accounts, categories, subcategories and transactions to be created, transactions already present, and any account whose settings in the backup differ from the existing account of the same name. Nothing SHALL be written until the user confirms.

#### Scenario: Restore onto an empty install
- **WHEN** the user restores a backup on a fresh install
- **THEN** the preview shows every record as new, and after confirming, the accounts screen shows the same balances, types, colours and reconcile dates as the phone that made the backup

#### Scenario: Restore onto a database with data
- **WHEN** the backup and the database share an account name but differ in opening balance
- **THEN** the preview lists that difference, the restore keeps the existing account's settings, and only missing records and transactions are added

### Requirement: Restore never deletes or overwrites
Restore SHALL only insert. Accounts, categories and subcategories SHALL be matched by name (case- and whitespace-insensitive, within kind or parent) and created only when missing. Transactions SHALL be de-duplicated with the shared dedup hash and committed as one import batch in one database transaction.

#### Scenario: Restoring the same backup twice
- **WHEN** the user restores a backup and then restores it again
- **THEN** the second preview shows zero new records and zero new transactions

#### Scenario: Undoing a restore
- **WHEN** the user reverses the restore's batch from Import history
- **THEN** exactly the transactions that restore inserted are removed, and accounts and categories it created remain, so they can be archived by hand

#### Scenario: Failure part-way
- **WHEN** writing fails during the restore commit
- **THEN** no record from that restore remains

### Requirement: Format versioning
The `Backup` sheet SHALL carry an integer format version, starting at 1. Restore SHALL refuse a file whose version is newer than the app understands, and SHALL say which app version is needed.

#### Scenario: Backup from a newer app
- **WHEN** the user restores a file with format version 2 on an app that reads version 1
- **THEN** nothing is parsed or written and the screen says the backup needs a newer Saku

### Requirement: Round trip is exact
Backing up and restoring onto an empty install SHALL reproduce every account, category, subcategory and transaction field that Saku stores, except database ids, creation timestamps and import batch history.

#### Scenario: Round trip
- **WHEN** a populated database is backed up and restored onto an empty one
- **THEN** a field-by-field comparison of both databases, ignoring ids, creation timestamps and batches, finds no difference
