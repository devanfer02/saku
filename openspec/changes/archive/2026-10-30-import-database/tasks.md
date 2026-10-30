## 1. Data

- [x] 1.1 Name the schema version and identity hash on `SakuDatabase`; test the hash against the exported schema JSON
- [x] 1.2 Add the pure adapter in `excel/`: header check, row checks, rows to `BackupContents`
- [x] 1.3 Add `data/local/DatabaseFileReader`: open a file read-only, read header and rows, hand them to the adapter
- [x] 1.4 Tests on a temp SQLite built from the schema JSON: version and hash refusal, archived categories, opening balances, transfers and signed adjustments, dedup on a second import, kind mismatch

## 2. UI

- [x] 2.1 Add the preview assembly with file counts, date range, new and present counts, conflicts
- [x] 2.2 Add `DatabaseImportViewModel`: copy to cache, read, preview, confirm, discard
- [x] 2.3 Add `DatabaseImportScreen`, stateless and stateful, with copy in `strings_database_import.xml`
- [x] 2.4 Add "Import database (.db)" to More > Your data and its route
- [x] 2.5 ViewModel tests for picker cancel, refusal, preview, confirm and a second import

## 3. Verification

- [x] 3.1 Import the owner's sample file in a unit test outside the repo, never committed (manual check only)
- [x] 3.2 `./gradlew :app:testDebugUnitTest :app:assembleDebug` green
- [ ] 3.3 On a device: fresh install, import a database file holding the mock fixture `data/mock-money-manager.xlsx`, check balances and Import history (not done: device check)
