## Why

A monthly grocery trip mixes categories: rice and cooking oil under Makan & Minum, soap under Kebutuhan Kos. The owner wants each item recorded under its own category and, at the same time, every row of the trip marked "Monthly grocery" (31 Oct 2026). A category cannot do both, because a row has exactly one and Stats splits spending by it to 100%. Subcategories were retired because they were a finer slice inside one category, which a trip is not. A tag is the missing piece: none, one or several per row, cutting across categories. The owner: tags are optional, and can be used on any transaction, not only scanned ones.

`receipt-items` saves its rows with a tag, so this change comes first.

## What Changes

- A transaction carries zero or more tags. A tag is a name, unique ignoring case and spaces, that can be renamed and archived but never deleted.
- The entry sheet gets an optional Tags row: chips for the row's tags, and Add tag, which lists existing tags and makes a new one from what was typed. Nothing requires a tag.
- The Trans list shows a row's tags after its note.
- The filter sheet gets a Tags section, so Trans and Stats can show one tag's rows and totals (for example, Monthly grocery in October, split by category).
- More > Tags lists tags with their row counts; rename and archive from there.
- **BREAKING (storage):** Room schema version 1 to 2, the first migration Saku ships. It only adds two tables (`tag`, `txn_tag`); no existing row changes.
- Export and backup write a `Tags` column at the end of the `Saku` sheet; backup format version 2 adds a `Tags` sheet. A version 1 backup still restores. An older Saku refuses a version 2 backup, as the format rule already says.
- Import database accepts a file from schema version 1 or 2.

## Capabilities

### New Capabilities
- `transaction-tags`: what a tag is, tagging in the entry sheet, tags on list rows, the tag filter, managing tags, and how tags travel through migration, export, backup, restore and database import.

### Modified Capabilities
None as delta blocks. The new backup format version and the extra accepted schema version are stated as requirements of `transaction-tags`, so `full-backup` and `import-database` keep their text; their version 1 rules still hold for version 1 files.

## Impact

- `data/local/`: `TagEntity`, `TxnTagEntity` (composite key, cascade on transaction delete, restrict on tag), `TagDao`, `SakuDatabase.VERSION = 2`, `MIGRATION_1_2`, new `IDENTITY_HASH`, exported `app/schemas/.../2.json`.
- `domain/`: `Tag`; `Transaction` gains `tagIds: Set<Long>`.
- `data/repository/`: `TagRepository` (Room); `TransactionRepository` save and update write tags in the same database transaction.
- `ui/entry/`, `ui/transactions/` (row), filter sheet, a new `ui/tags/` screen under More.
- `excel/`: `ExportSheet`, `SheetLayout`, `ImportParser` (optional `Tags` column), `BackupSheets` (format 2, `Tags` sheet), `RestorePlan`, `DatabaseImport` (two known schema versions).
- Tests: migration test from a version 1 file, backup round trip with tags, restore of a version 1 backup, import of a version 1 database.
