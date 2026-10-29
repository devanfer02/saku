## 1. Excel layer

- [ ] 1.1 Let `XlsxWriter` write several `SheetTable`s into one workbook, keeping the single-sheet call working
- [ ] 1.2 Let `XlsxReader` return all sheets by name, keeping `read` (first sheet) unchanged for import
- [ ] 1.3 Extend `XlsxAndroidRuntimeTest` with a multi-sheet write and read through the StAX shim
- [ ] 1.4 Add `excel/BackupSheets.kt`: build and parse the `Accounts`, `Categories`, `Subcategories` and `Backup` sheets; refuse unknown newer format versions and duplicate names
- [ ] 1.5 Unit-test `BackupSheets` against the format and versioning scenarios in `specs/full-backup/spec.md`

## 2. Data

- [ ] 2.1 Add a backup read to the repositories: every account, category, subcategory and transaction, archived included
- [ ] 2.2 Add a restore plan (pure, in `excel/` beside `ImportPlan`): match by name, list records to create, list conflicting account settings, de-duplicate transactions
- [ ] 2.3 Add the restore commit to the import repository: create missing records, then commit transactions as one batch, in one Room transaction
- [ ] 2.4 Round-trip test: populate an in-memory database, back up, restore into an empty one, compare every field except ids, creation timestamps and batches
- [ ] 2.5 Tests for restore-twice (zero new), conflict kept, and rollback on a failed commit

## 3. UI

- [ ] 3.1 Backup screen: create-document picker with `Saku backup YYYY-MM-DD.xlsx`, progress, counts written, one-line note that the file is not encrypted
- [ ] 3.2 Restore screen: open-document picker, preview (date, version, counts, conflicts), confirm, result
- [ ] 3.3 Add "Back up everything" and "Restore from backup" to More and `MoreRoute`
- [ ] 3.4 ViewModel tests for picker cancel, newer-version refusal and the preview states

## 4. Verification

- [ ] 4.1 Maestro flow: back up, clear app data, restore, check the accounts screen balances
- [ ] 4.2 Emulator check with the real 1,300-row import: backup, restore onto a clean install, compare the accounts and stats tabs
- [ ] 4.3 `ANDROID_HOME=/home/dvnnfrr/Android/Sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug` green
