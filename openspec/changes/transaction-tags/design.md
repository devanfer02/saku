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

## Decisions made while building (31 Oct 2026)

Calls the decisions above did not settle, made without the owner.

### Storage
- Tags are matched by `TagName.key` in Kotlin, not SQL `LOWER`, which folds ASCII only. No unique index on `tag.name`: case-insensitive uniqueness is not expressible as a plain index, and the table holds tens of rows.
- Lists get their tags by combining each row Flow with the whole `txn_tag` table, not by a join per query. Tagging a row then re-emits the list though no `txn` row changed.
- The tag filter is one more clause in `TXN_FILTER_WHERE`, naming `txn.id`. The category-totals query lost its `t` alias for that, since `category` has an `id` too.
- `findOrCreate` returns an archived match as it is and does not unarchive it, so restore and import never change an existing tag.

### Entry sheet
- A tag typed in Add tag becomes a chip with no id and is created at Save, so a sheet closed without saving leaves no tag. A save that then fails validation can leave a tag on no row; the Tags screen shows it with 0 transactions.
- Keyboard Done adds the typed name and keeps the picker open for another. The Done button adds it and closes. The picker's close (X) drops anything still typed.
- An archived tag is hidden while browsing but listed when its whole name is typed, and typing it reuses it. Offering Create there would make a second tag with one name.
- The entry ViewModel observes tags live. Found on the emulator (5.8): it read them once in `open()`, which skips reloading a sheet it already holds, so an unarchived tag stayed hidden.

### List rows
- Tags render as their own Text after the account and note, so a long note is cut and the tags are not. Stats drill-through and Search rows show them too, through the same row.
- TalkBack reads "Tags Monthly grocery, Ramadan and 1 more", not the `#` marks. The copy lives in `ui/transactions/RowTagsSpoken.kt`, because the shared row takes copy as a parameter.

### Filter
- Tags are a fourth tab (TAGS) of the sheet's segmented control, after ACCOUNT, rather than a section: the sheet is tabbed.
- Under All no tag shows ticked. The first tick narrows to that tag, unticking the last returns to All, and the All row clears the selection. Ticking every tag is not All, because All also passes untagged rows; an empty tag selection would hide everything, so it is never produced.

### Tags screen
- Tapping a row opens a bottom sheet holding the name field, Save name and Archive or Unarchive, so both actions sit in the lower half. No dialogs.
- Save name is offered only for a valid, different name. A clash shows under the field before saving ("Another tag is already called Trip Bali"); a case change of the tag's own name is allowed.

### Interchange
- The export lists a row's tags in the user's tag order. A plain export carries no archived flag; only the backup's `Tags` sheet does.
- Format 2 refuses a backup without its `Tags` sheet and a row naming a tag the sheet does not list, as it already does for accounts and categories.
- Restore inserts the backup's missing tags with their archived flag and sort order before committing rows. The restore preview does not count tags.
- Database import refuses an invalid tag name (`InvalidValue`) and a link to a missing tag or row (`MissingReference`).

### Testing
- The androidTest `MigrationTest` ran with `am instrument --user 12` on a throwaway user, not `connectedDebugAndroidTest`, which uninstalls the app after the run and would have wiped the mock database on user 0.

### Accessibility check
Checked against the `android-accessibility` checklist and the impeccable Android reference, using uiautomator content descriptions on the emulator.
- A tag chip is one node, "Tag Trip Bali, remove", role Button. Material chips keep a 48 dp touch target over their 32 dp visual height.
- Add tag's Done, Save name and Archive tag are full-width 48 dp buttons in the lower half. With the keyboard open, Done sits just above it (y 1412 of 2400 on the Pixel 8).
- Tags screen rows are 56 dp, read name and count as one item, and have a click label. The Archived header and the sheet title are headings.
- Icons inside chips are decorative; the chip carries the words.
- Done uses the accent with `onPrimary`, as the keypad's Save does, because white on the dark accent fails contrast.
- Not checked: a live TalkBack pass and font scale 1.3.
