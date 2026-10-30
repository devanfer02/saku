## Why

First run is generic since 30 Oct 2026: it seeds Cash and Card and nothing else. The owner's own history arrives by import, and the owner prepares it outside the app: a script turns the Money Manager export into a SQLite file in Saku's own schema, with the accounts, opening balances, archived categories, transfers and adjustments the Excel import cannot express. The app needs a way to take that file in after install.

## What Changes

- A "Import database (.db)" entry in More > Your data, next to Restore and the Excel imports.
- The user picks a `.db` file with the system picker. The app copies it to its cache directory and opens the copy read-only. The app's own database file is never replaced or touched.
- Before anything is written, the file is checked: `PRAGMA user_version` must equal the app's schema version and `room_master_table` must hold the app's schema identity hash. A mismatch is refused with a plain message.
- The rows are adapted to the same `BackupContents` the backup restore uses, so the import reuses `RestorePlan` and `ImportRepository.restore()`: insert-only, names merged into existing accounts and categories, transactions de-duplicated by `DedupHash`, one import batch that Import history can reverse.
- A preview shows the file's counts, its date range, what will be added, what is already here, and accounts whose settings differ, then the user confirms.

## Capabilities

### New Capabilities
- `import-database`: checking, previewing and importing a SQLite file built in Saku's schema.

### Modified Capabilities
None.

## Impact

- `excel/`: a pure adapter from database rows to `BackupContents`, with its own refusals.
- `data/local/`: a read-only reader of a foreign SQLite file; the schema version and identity hash become named constants on `SakuDatabase`.
- `ui/importexport/`: a view model, assembly and screen for the database import; `ui/shell` and `ui/navigation` gain one More entry and route.
- No Room entity change and no migration.
