## Why

Saku is offline and has no account, so the phone is the only copy of a year of history. Today's export writes transactions only: a restore onto a new phone loses opening balances, reconciliations, account types, colours, archived records and the subcategory tree.

## What Changes

- A "Back up everything" action in More that writes one `.xlsx` file through the system file picker.
- Sheet 1 of that file is the existing Saku transaction sheet, unchanged. The plain "Import Excel" flow keeps reading it, and the file still opens as an ordinary spreadsheet.
- Further sheets hold accounts, categories, subcategories and a format-version sheet.
- A "Restore from backup" action that reads every sheet, previews the result, and then merges. It inserts only and never deletes or overwrites. The transactions land as one import batch, so Import history can reverse them.
- The reader can read a named sheet as well as the first one. The writer can write several sheets.

## Capabilities

### New Capabilities
- `full-backup`: the backup file format, writing it, restoring from it, and how a restore merges into a database that already has data.

### Modified Capabilities
None. `openspec/specs/` has no synced capability yet. The new More entries are specified inside `full-backup`.

## Impact

- `excel/`: multi-sheet `XlsxWriter`, sheet-by-name `XlsxReader`, new pure `BackupSheets` builder and parser.
- `data/repository/`: a restore method on the import repository that creates missing accounts, categories and subcategories, then commits transactions through the existing batch path in one Room transaction.
- `ui/importexport/`: backup and restore screens, plus More entries.
- No Room entity change and no migration. Backup is a read; restore uses existing tables.
