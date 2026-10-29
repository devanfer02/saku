## Context

The export in `excel/ExportSheet.kt` writes one `Saku` sheet of transactions, by name rather than id, and `ImportParser` reads it back; `XlsxRoundTripTest` keeps the two honest. `XlsxWriter` writes one sheet and `XlsxReader` reads `firstSheet` only. Import already commits insert-only as an `import_batch` that Import history can reverse. Room schema is version 1 with `account`, `category`, `subcategory`, `txn` and `import_batch`.

The owner asked that the backup "match the format" of what Saku imports, and chose a full backup in Saku's own format over a Money Manager-shaped one.

## Goals / Non-Goals

**Goals:**
- One file that rebuilds the app on a new phone.
- The file stays a readable spreadsheet and stays importable by the existing "Import Excel" path.
- Restore reuses the import pipeline so it is insert-only and reversible.

**Non-Goals:**
- Cloud, scheduled or automatic backups (the app has no network and the manifest gate forbids it).
- Encrypting the backup file. It lands wherever the user saves it; Android's picker locations are the user's choice. Recorded as a follow-up.
- Backing up the theme setting.
- An in-app passcode. Rejected on 30 Oct 2026: Android's Private space (stock Android 15+) and OEM app locks already cover it.
- Restoring import batch history.

## Decisions

**Workbook, not a database file copy.** Copying the Room `.db` would be exact, but it ties the backup to the schema version, needs a closed database to copy safely, is unreadable to the owner, and would replace rather than merge. A workbook keeps names instead of ids, as the export already does, and merges.
- Rejected: JSON. It is exact and simple, but the owner wants the backup to match the importable format and to open in a spreadsheet.

**Transactions sheet first and unchanged.** `XlsxReader` reads the first sheet, so a backup opened through "Import Excel" behaves exactly like an export with no code change on that path. The extra sheets are invisible to that flow.

**Names as keys, matched the way import matches.** Accounts by name; categories by name within kind; subcategories by name within parent category. The same normalisation (trim, collapse whitespace, case-fold) as the import mapping, reused rather than reimplemented.

**Existing records win on conflict.** If an account of the same name exists with a different opening balance, type or colour, restore keeps the existing one and lists the difference in the preview. Overwriting would be an unscoped update in spirit, and it would silently change balances. The user can edit the account afterwards.

**Restore commits through the import batch path.** Transactions go in as one `import_batch` named after the file, de-duplicated by `DedupHash`. Created accounts, categories and subcategories are inserted first in the same Room transaction. Batch reversal removes only the transactions, matching how imports behave today; created records stay and can be archived.

**Version sheet.** A `Backup` sheet with `format = 1`, the app `versionName` and the write time. Unknown newer versions are refused before parsing. Version 1 readers ignore unknown extra columns on the record sheets, so adding a column later is not a breaking change.

**Pure builders and parsers.** `excel/BackupSheets.kt` builds the four record sheets from domain lists and parses them back into domain-shaped rows, with no Android or Room types, in the same style as `ExportSheet`. The repository method maps them to entities.

## Risks / Trade-offs

- [Two accounts with the same name in one database] → Names are already unique by the account-management rules; restore refuses a backup whose `Accounts` sheet repeats a name, naming the duplicate.
- [A large backup on a slow phone] → 1,300 rows imported in a few seconds on the emulator; the write runs off the main thread with progress, the same as import.
- [Unencrypted file holds all financial history] → Called out on the backup screen in one sentence; encryption is a follow-up.
- [fastexcel multi-sheet support on Android] → The StAX shim is only proven for one sheet; the Android runtime test gains a multi-sheet case.

## Migration Plan

No schema change. Shipping is additive: two More entries and two screens.

## Open Questions

- Whether to remind the owner to back up (for example after a large import or monthly). Not in scope; recorded for later.
