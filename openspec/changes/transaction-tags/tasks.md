## 1. Storage

- [x] 1.1 `TagEntity`, `TxnTagEntity`, `TagDao`; `SakuDatabase.VERSION = 2`; `MIGRATION_1_2` (create-only); export schema `2.json`; update `IDENTITY_HASH` and `SchemaIdentityTest`
- [ ] 1.2 Migration test (androidTest, `MigrationTestHelper`) from a version 1 file with rows; JVM test that the migration SQL is create-only
- [x] 1.3 Domain `Tag`, `Transaction.tagIds`; mappers; `TagRepository` (find-or-create by normalized name, rename, archive, counts)
- [x] 1.4 `TransactionRepository` save and update write `txn_tag` in the same database transaction; tests

## 2. Entry sheet and list

- [x] 2.1 Tags row and Add tag picker in `EntrySheet`; ViewModel events; assembly tests for name normalizing and matching
- [x] 2.2 `#tag` and `+n` on list rows (`TransactionListAssembly`); tests
- [x] 2.3 Tags section in the filter sheet; Trans and Stats follow it; tests for All and for chosen tags

## 3. Tags screen

- [x] 3.1 More > Tags: list with row counts, rename (refuses a used name), archive and unarchive; stateless overload with a preview

## 4. Interchange

- [x] 4.1 `Tags` column in `SheetLayout.SAKU` and `ExportSheet`; `ImportParser` reads it when present; round-trip test
- [x] 4.2 `BackupSheets` format 2 with a `Tags` sheet; `RestorePlan` creates missing tags, insert-only; tests for format 1 and 2
- [x] 4.3 `DatabaseImport` accepts schema versions 1 and 2; test with a version 1 file

## 5. End-to-end on the emulator (mock data only, run by adb, screenshot per step)

- [ ] 5.1 Upgrade: install the v1 build with the mock database, install this build over it; row count, balances and Stats totals are unchanged and no row has a tag
- [ ] 5.2 Add: new expense with a new tag `Trip Bali` and an existing tag; list row shows `#Trip Bali` and the other tag
- [ ] 5.3 Case match: type `monthly  grocery` in Add tag; the existing tag is picked, the Tags screen still shows one
- [ ] 5.4 Edit: remove a tag from a row and save; the row has no tag, the tag still exists with its count down by 1
- [ ] 5.5 Three tags on one row show 2 and `+1`
- [ ] 5.6 Filter: Tags = Monthly grocery; Trans shows only tagged rows; Stats splits them by category to 100%; All restores every row
- [ ] 5.7 Tags screen: rename (list rows follow), rename to a used name (refused), archive (Add tag hides it, filter keeps it), unarchive
- [ ] 5.8 Transfer with a tag saves and shows the tag
- [ ] 5.9 Backup then restore onto a second Android user on the emulator (never clear the mock database): same tags on the same rows, archived tag archived
- [ ] 5.10 Restore a format 1 backup and import a schema 1 `.db`: both work, no tags created
- [ ] 5.11 Export, then Import Excel of that file as that second user: tags come back; import the Money Manager mock: unchanged counts

## 6. Done

- [ ] 6.1 `lintDebug`, `testDebugUnitTest`, `assembleDebug` green; README and CLAUDE.md say what a tag is
