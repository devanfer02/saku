## ADDED Requirements

### Requirement: A transaction carries zero or more tags
A transaction SHALL carry zero or more tags. A tag SHALL have a name of 1 to 30 characters, trimmed with inner spaces collapsed, containing no `;`, and unique ignoring case. Tags SHALL be optional on every transaction type. A tag SHALL never be deleted; it MAY be archived.

#### Scenario: Several tags on one row
- **WHEN** the user saves an expense with tags Monthly grocery and Ramadan
- **THEN** the row carries both tags and its category is unchanged

#### Scenario: Same name in other case
- **WHEN** Monthly grocery exists and the user types `monthly  grocery` in Add tag
- **THEN** the existing tag is used and no second tag is created

### Requirement: Tag a transaction in the entry sheet
The entry sheet SHALL show a Tags row with the transaction's tags as removable chips and an Add tag action. Add tag SHALL list unarchived tags matching the typed text and SHALL create a new tag from the typed text when none matches. Saving SHALL write the transaction and its tags in one database transaction. Editing a transaction SHALL show and change its tags.

#### Scenario: Add a new tag
- **WHEN** the user taps Add tag, types `Trip Bali` and confirms
- **THEN** the chip Trip Bali appears, and after Save the tag exists and the row carries it

#### Scenario: Save with no tag
- **WHEN** the user saves without touching the Tags row
- **THEN** the row is saved with no tags

#### Scenario: Remove a tag while editing
- **WHEN** the user opens a row tagged Monthly grocery, removes the chip and saves
- **THEN** the row carries no tags and the tag Monthly grocery still exists

### Requirement: Tags show on list rows
A row in the Trans list SHALL show its tags after the note as `#name`, at most 2, followed by `+n` for the rest.

#### Scenario: Three tags
- **WHEN** a row carries 3 tags
- **THEN** it shows the first 2 and `+1`

### Requirement: Filter by tag
The filter sheet SHALL have a Tags section with an All row and one checkbox per tag, archived tags included. With All, rows SHALL pass regardless of tags. With tags chosen, only rows carrying at least one chosen tag SHALL pass, and Trans and Stats SHALL both follow, as with the other filter sections.

#### Scenario: One trip's spending by category
- **WHEN** the user filters by Monthly grocery and opens Stats for October
- **THEN** the expense breakdown shows only rows tagged Monthly grocery, split by their categories, adding up to 100%

### Requirement: Manage tags
More SHALL list Tags. The Tags screen SHALL show every tag with its row count and SHALL allow rename and archive or unarchive. Rename SHALL refuse a name already used by another tag.

#### Scenario: Rename
- **WHEN** the user renames Monthly grocery to Belanja bulanan
- **THEN** every row that carried it shows Belanja bulanan

#### Scenario: Archive
- **WHEN** the user archives Trip Bali
- **THEN** rows keep it, the filter still lists it, and Add tag no longer offers it

### Requirement: Upgrade keeps every row
Upgrading from database schema version 1 to 2 SHALL only create the tag tables and SHALL leave every existing account, category, subcategory and transaction unchanged.

#### Scenario: Upgrade with data
- **WHEN** the app with schema version 2 opens a version 1 database holding 2,000 transactions
- **THEN** all 2,000 are present with the same fields, and none carries a tag

### Requirement: Tags travel through export, backup, restore and import
Export and backup SHALL write a `Tags` column as the last column of the `Saku` sheet, holding the row's tag names joined by `; `. Backup SHALL use format version 2 and add a `Tags` sheet with each tag's name, archived flag and sort order. Restore SHALL accept format versions 1 and 2, create missing tags by name, never change an existing tag, and never add tags to a transaction that already exists. Import Excel SHALL read the `Tags` column when present and import files without it as before. Import database SHALL accept files of schema version 1 (read with no tags) and 2, and refuse any other version as before.

#### Scenario: Round trip
- **WHEN** a database with tagged rows and an archived tag is backed up and restored onto an empty install
- **THEN** every row carries the same tags and the archived tag is archived

#### Scenario: Version 1 backup
- **WHEN** the user restores a backup with format version 1
- **THEN** it restores as before and no tags are created

#### Scenario: Version 1 database file
- **WHEN** the user imports a `.db` file with schema version 1
- **THEN** the preview and import work as before, with no tags

#### Scenario: Money Manager file
- **WHEN** the user imports a Money Manager export, which has no `Tags` column
- **THEN** it imports exactly as before
