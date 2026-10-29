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

## Decisions made while building

Recorded during `/opsx:apply`, 30 Oct 2026.

**`XlsxReader.readAll` returns a list, not a map.** Workbook order matters (the transaction sheet must be first), and a list keeps it. Restore looks sheets up by normalised name. A sheet with no rows comes back with empty headers instead of failing the workbook; the caller decides whether that is an error. `XlsxWriter.write(table, output)` now delegates to `write(tables, output)`.
- Rejected: a `Workbook` wrapper type. One more type for a `List<SheetTable>` that callers filter by name in one line.

**The `Backup` sheet also records the time zone.** Timestamps in a spreadsheet are wall-clock readings. The record sheets and the transaction sheet are written in the writer's zone, and restore parses them in that same zone (`Time zone` row), so a backup made in Jakarta and restored on a phone set to another zone lands on the same instants. Without it the round trip is exact only when both phones share a zone.
- Rejected: epoch seconds in the record sheets. Exact, but the owner asked for a readable spreadsheet, and the transaction sheet already uses timestamps.

**Field layout of the `Backup` sheet is key and value rows** (`Field`, `Value`): `Format version`, `App version`, `Written at`, `Time zone`. Read by field name, so a later field is additive.

**Record sheets are read by header name.** Unknown columns are ignored and column order is free, which is what makes "adding a column is not a breaking change" true. Type and kind are written as fixed file-format tokens (`Bank`, `Expense`), like `TxnType.token`, not as enum names.

**Name matching uses one `nameKey` function** in `excel/BackupSheets.kt`: trim, collapse inner whitespace, lowercase. There was no shared normaliser to reuse: the import view model uses `trim().lowercase()` in private code, and the DAOs use `LOWER(TRIM())` in SQL. Matching backup names against the same `nameKey` on both sides keeps restore self-consistent. Unifying import onto `nameKey` was out of scope (another change owns `ImportViewModel`). Archived records match too, and a live record wins over an archived one of the same name, as `findAnyByName` does.

**A category on a transaction is resolved by row type.** The transaction sheet names a category but not its kind. Income rows use the income category, expense rows the expense one. A transfer or adjustment takes the expense category of that name if the backup has one, else the income one. This mirrors import, which treats a categorised transfer as an expense category.

**Cross-sheet references are checked at parse time.** A transaction naming an account, category or subcategory the record sheets do not list, or a subcategory whose parent is missing, refuses the whole file (`UnknownName`). Restore has no settings to create such a record with, and inventing defaults would be a silent guess.

**Conflicts compare six settings and skip sort order.** Type, opening balance, opening date, colour, archived and last reconciled. Sort order is cosmetic and always differs from a seeded account, so listing it would put noise on every restore.

**Created records keep the backup's sort order and colour verbatim.** On an empty database that is what makes the round trip exact. On a populated one it can tie with an existing sort order; ties already break by id.
- Rejected: offsetting created sort orders after the existing maximum. It would break the exact round trip for the one case that matters most.

**The repository rebuilds the plan inside the restore transaction.** `RestorePlan.of` is pure and runs twice: once for the preview from repository reads, once in `RoomImportRepository.restore` inside `withTransaction`. Commit therefore matches against what exists at commit time. Transactions go through the existing `commit`, nested in the same transaction, so a refused row also rolls back the accounts and categories created before it.
- Rejected: passing the preview's plan into the repository. Shorter, but a record created between preview and confirm would be duplicated.

**`data.repository` now depends on `excel` for restore.** `ImportRepository.restore` takes `BackupContents`, so the repository can map backup records to entities, as this design says. `excel` already depends on `data.repository` (`PendingRow`), so the two packages now reference each other.
- Rejected: copies of `BackupAccount`, `BackupCategory` and `BackupSubcategory` in `data/`. They would be field-for-field duplicates.

**Backup snapshot is one read in one transaction.** `ImportRepository.backupSnapshot()` reads accounts, categories, subcategories and transactions inside `withTransaction`, so a transaction can never name an account the file lacks. It needed a one-shot `SubcategoryDao.all()`, because the existing `allSubcategories()` collects a Flow, which deadlocks inside a transaction (see `AccountDao.balanceOf`). No schema change.

**Preview counts reuse `ImportPlan.tally`**, so "new transactions" is computed exactly as `commit` filters. Dedup hashes for accounts still to be created use negative placeholder ids, as `ImportPlan.previewHashes` does. Confirm is disabled when the preview has nothing new.

**Picker cancel is a null argument.** `BackupViewModel.backUp(sink: DocumentSink?)` and `RestoreViewModel.load(source: DocumentSource?)` ignore null, so the cancel scenario is a view model test instead of a property of the composable.

**A failed restore commit stays on the preview** with the commit's message (for example a transfer predating a kept account's opening date). Nothing was written, so retrying after editing the account is safe.
- Rejected for now: detecting transfers that predate a kept account's opening date in the preview, as the import preview does. It is the one failure the preview cannot yet name in advance; recorded below.

**UI copy lives in `res/values/strings_backup.xml`** and backup screens in new files, so this change merges cleanly with concurrent work on the import screens and `strings.xml`.

## Risks / Trade-offs

- [Two accounts with the same name in one database] → Names are already unique by the account-management rules; restore refuses a backup whose `Accounts` sheet repeats a name, naming the duplicate.
- [A large backup on a slow phone] → 1,300 rows imported in a few seconds on the emulator; the write runs off the main thread with progress, the same as import.
- [Unencrypted file holds all financial history] → Called out on the backup screen in one sentence; encryption is a follow-up.
- [fastexcel multi-sheet support on Android] → The StAX shim is only proven for one sheet; the Android runtime test gains a multi-sheet case.

## Migration Plan

No schema change. Shipping is additive: two More entries and two screens.

## Open Questions

- Whether to remind the owner to back up (for example after a large import or monthly). Not in scope; recorded for later.
- **A "fresh install" is not empty.** `FirstRunSeed` creates BCA, Jago, Cash and RDPU SucorInvest (opening 0 at the epoch) and the default categories on first launch. Restoring a real backup onto a fresh install therefore matches those seeded accounts by name, keeps their zero opening balances (existing wins), and lists them as conflicts. The spec's "Restore onto an empty install" scenario holds for a truly empty database (the round-trip test uses one), but not after the seed has run, so the Maestro flow in 4.1 (clear app data, restore, check balances) will see seeded balances. Options for the owner: (a) treat an untouched seed account (no transactions, opening 0, never reconciled) as adoptable and take the backup's settings, which is a scoped update and needs the "never overwrites" requirement amended; (b) skip the seed when the first thing the user does is restore; (c) keep the current behaviour and fix balances by hand. Not decided here because (a) contradicts an approved requirement.
- Renamed categories on the database that wrote the backup: a stored dedup hash uses the category name at entry time, so restoring a backup onto its own origin database after a rename can report those rows as new. Restoring onto any other database, and restoring the same file twice, are unaffected.
- A transfer that predates a kept account's opening date fails the commit with a message instead of being named in the preview.
