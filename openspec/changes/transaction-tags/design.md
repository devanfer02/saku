## Context

Saku stores a transaction with one category. Subcategories exist in storage only and are hidden (`hide-subcategories`). The database has never migrated: `SakuDatabase.VERSION = 1`, and `import-database` refuses any file whose `user_version` or identity hash differs. Backup format version is 1 (`BackupSheets.FORMAT_VERSION`). The export's `Saku` sheet layout is shared by export, backup and ordinary import.

## Goals / Non-Goals

**Goals:**
- Zero or more tags per transaction, optional everywhere.
- One tag's rows and totals in Trans and Stats through the existing filter.
- No data loss on upgrade; old backups and old database files still come in.

**Non-Goals:**
- Tag colours, tag icons, nested tags.
- Budgets per tag.
- Showing tags in Stats as their own chart. The filter covers "how much was Monthly grocery".
- Bringing subcategories back. The `subcategory` table stays as it is.

## Decisions

### 1. Two tables, not a text column
- `tag(id, name, sortOrder, archived)` and `txn_tag(txnId, tagId)` with primary key `(txnId, tagId)`, an index on `tagId`, `ON DELETE CASCADE` from `txn` and `RESTRICT` from `tag`.
- Alternative: a `tags` text column on `txn` ("Monthly grocery; Trip Bali"). Rejected: renaming a tag would rewrite every row, and filtering would be a `LIKE` scan.
- Tags are archived, never deleted, as categories are (destructive-writes rule). An archived tag stays on its rows and in the filter, but is not offered in Add tag.

### 2. Name rules
- Trimmed, inner spaces collapsed, 1 to 30 characters, unique ignoring case. Display keeps the case first typed. "monthly grocery" typed in Add tag picks the existing "Monthly grocery".

### 3. The first migration
- `MIGRATION_1_2` runs two `CREATE TABLE` and one `CREATE INDEX` statements and nothing else, so it cannot touch existing rows.
- It runs once, the first time the upgraded app opens the database after an install over the old one (same signing key). No user step.
- Never add `fallbackToDestructiveMigration`: a missing migration must crash, not wipe the ledger. Installing an older Saku over version 2 is not supported; Room refuses the newer database, and nothing is lost. An older Saku also refuses a format 2 backup, so the README says: back up before upgrading.
- `SchemaIdentityTest` gets the new hash. A Room `MigrationTestHelper` test (androidTest) opens a version 1 file with rows, migrates, and checks every row is still there; a JVM test checks the SQL text is create-only.

### 4. Database import accepts versions 1 and 2
- `DatabaseImport` keeps a table of known `(user_version, identity hash)` pairs: the version 1 hash and the version 2 hash. A version 1 file is read with no tags. Anything else is refused with the existing messages.

### 5. Export, backup, restore
- The `Saku` sheet gains a last column, `Tags`, holding tag names joined with `; `. A name cannot contain `;` (rule 2 rejects it), so the split is exact. The reader treats the column as optional, so Money Manager files and version 1 exports still import.
- Backup format version 2 adds a `Tags` sheet: name, archived, sort order. Restore matches tags by name like categories, creates missing ones, and never changes an existing tag.
- Restore stays insert-only: a transaction already present (same dedup hash) does not gain tags from the backup. Stated in the spec so nobody "fixes" it later.
- Ordinary import (Import Excel) reads the `Tags` column too, creating missing tags.
- The dedup hash does not include tags, so tagging a row later does not make a re-import duplicate it.

### 6. Entry sheet
- A Tags row below Note: chips with a remove ×, then an Add tag chip. Add tag opens a small picker with a text field on top and matching existing tags below; Enter or Done on a new name creates it. Archived tags are not listed.
- The row is shown for all three types. A transfer can carry a tag (a trip's cash withdrawal, for example).
- My call: the entry sheet does not pre-fill tags from the last entry. Scanned receipts pre-fill their own tag (`receipt-items`).

### 7. Filter
- The filter sheet gets a Tags section after Categories, same checkbox list with an All row. All means "do not filter by tag", so untagged rows pass. Choosing tags passes rows having any of the chosen tags.

### 8. List row
- Tags show after the note in the row's secondary text, prefixed `#`, muted colour from the theme. More than 2 shows the first 2 and `+n`.

## Risks / Trade-offs

- [The first migration ships] → create-only SQL, a migration test from a real version 1 file, and the emulator mock database upgraded by hand before the PR.
- [A phone on the old app gets a version 2 backup] → refused with the existing "needs a newer Saku" message; nothing is lost.
- [Restore cannot add tags to rows that already exist] → accepted; restore is insert-only by rule.

## Open Questions

None from the owner. Name length (30) and the 2-tag row limit are my calls.
