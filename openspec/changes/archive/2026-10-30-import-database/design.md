## Context

Restore from backup (`full-backup`) already does the hard part: `BackupContents` holds accounts, categories, subcategories and a `ParsedSheet` of transactions, all keyed by name; `RestorePlan` matches names, lists conflicts and builds `PendingRow`s; `ImportRepository.restore()` inserts missing records and commits the transactions as one `import_batch`, inside one Room transaction. The owner's script writes a SQLite file in the exact schema of `app/schemas/com.dvnnfrr.saku.data.local.SakuDatabase/1.json`: same tables, `user_version = 1`, `room_master_table` with the identity hash, no `android_metadata`.

## Goals / Non-Goals

**Goals:**
- Take the owner's prepared file in after install, without touching the app's own database file.
- Refuse a file made for another schema before reading a row.
- Merge exactly as restore merges: insert-only, by name, de-duplicated, reversible.

**Non-Goals:**
- Replacing the app's database with the file (the fast path, rejected below).
- Migrating an older or newer schema version. There is only version 1.
- Importing `import_batch` history from the file. Restore does not carry it either.
- Adopting the file's opening balance onto an account that already exists here (see Open Questions).

## Decisions

**Adapt to `BackupContents`, then reuse restore unchanged.** The database rows become `BackupAccount`, `BackupCategory`, `BackupSubcategory` and `ParsedRow`, the shapes the backup restore reads. `RestorePlan.of`, `restorePreview`'s tally and `ImportRepository.restore()` run as they do for a backup. Archived flags and opening balances therefore behave exactly as in restore: created records carry them; matched records keep this phone's settings and are listed as conflicts.
- Rejected: replacing or copying the file over `saku.db`. Fast and exact, but it destroys what is on the phone, cannot merge, cannot be reversed, and breaks the owner's rule against destructive writes.
- Rejected: `ATTACH DATABASE` and `INSERT ... SELECT` into Room's tables. It bypasses the name matching, the dedup and the validator that every other import goes through.
- Rejected: a second restore path writing entities directly from the file. It would duplicate `RestorePlan`.

**Validation is `user_version` plus the identity hash.** Room writes both; the script writes both from the schema JSON. The identity hash is a digest of every table, column, type, key and index, so a matching hash is the strongest available statement that the tables and columns are what the app expects, and it costs one query. The app's hash is a constant, `SakuDatabase.IDENTITY_HASH`, because Room does not expose it at runtime; `SchemaIdentityTest` reads `schemas/.../<VERSION>.json` and fails if the constant drifts, so a schema change cannot ship with a stale check.
- Rejected: comparing `PRAGMA table_info` for every table and column. More code for a weaker check (it ignores types, keys and indices), and the hash already covers it.
- Defence in depth: the reader selects named columns, so a file that lies about its hash and lacks a column fails as unreadable rather than reading garbage.

**Two refusals, not one, for a mismatch.** A different `user_version` is "made for a different version of Saku". The same version with a different hash means the file was built from the wrong schema, which is a script bug, not an app update the user can fix, so it gets its own message. No `room_master_table` at all is "not a Saku database".

**Structural checks belong to the adapter, value checks to the validator.** The adapter refuses what restore cannot express: an unknown enum string, a colour slot outside 0..7, a reference to a missing row, two records of one name (accounts by name, categories by name within kind, subcategories by name within parent, as `BackupSheets` refuses), and a subcategory whose parent differs from the transaction's category. Amount signs, required categories and transfer dates are left to the commit validator, which already rejects them with a message and rolls the whole import back.

**A transaction's category is checked against its kind.** `ParsedRow` names a category but not its kind; `BackupContents.categoryRefOf` resolves the kind from the row type. The database knows the real kind, so the adapter refuses a row where the two disagree (for example a transfer carrying an income category while an expense category of the same name exists). Restoring it silently would move the row to the wrong category.

**The pure adapter lives in `excel/` next to `RestorePlan`.** `excel/` is the import package; the adapter produces `BackupContents` and has no Android or SQLite type. The Android reader lives in `data/local/DatabaseFileReader.kt` and does only I/O: open, read the header, ask the adapter whether to continue, read rows.
- Rejected: a new `dbimport/` package for two files.

**Opened with `OPEN_READONLY | NO_LOCALIZED_COLLATORS`.** The script's file has no `android_metadata` table, and without `NO_LOCALIZED_COLLATORS` Android tries to create one, which fails on a read-only handle.

**The copy goes to `cacheDir` and is removed after reading.** The view model receives a scratch directory, not a `Context`. The copy is the app's own temporary file; removing it is not a database write.

**`BackupInfo` for a database file.** A database records no write time and no app version. The adapter fills `formatVersion` with the `user_version`, `appVersion` with an empty string, `writtenAt` with the epoch and the zone with UTC. Nothing on the database path reads them: timestamps are epoch seconds, so no zone conversion happens, and the preview has its own shape.

**Own preview, own screen, shared copy where the meaning is shared.** `DatabaseImportPreview` adds file counts and a date range that the backup preview lacks, and drops the backup date and format. Conflict lines reuse `AccountConflict.line()` and the restore strings for settings. The view model repeats the ten lines of confirm logic from `RestoreViewModel` rather than sharing a base class that would couple the two screens.

## Risks / Trade-offs

- [The fresh-install seed conflicts with the file] → First run seeds Cash and Card at opening 0 on the epoch. The file's Cash matches the seeded Cash by name and keeps the seeded opening balance; the preview lists it under "Accounts that differ". The owner sets the real opening balance on the accounts screen afterwards. Card has no counterpart in the owner's file and stays.
- [A large file on a slow phone] → about 1,800 rows; reading is one pass per table off the main thread, and the commit is the same as restore's.
- [The owner's file holds full financial history] → it is copied to the cache directory only for the read and removed afterwards.

## Migration Plan

No schema change. Additive: one More entry, one route, one screen.

## Open Questions

- Opening balance of an existing account: restore keeps this phone's settings. For the owner's first import that means the seeded Cash keeps 0. Taking the file's opening balance for an untouched seeded account would need a scoped update and an amendment to the restore requirement "never overwrites"; same open question as `full-backup`. Owner to decide.
