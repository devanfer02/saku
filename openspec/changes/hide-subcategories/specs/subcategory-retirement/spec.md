## ADDED Requirements

### Requirement: No subcategory in the entry sheet
The entry sheet SHALL NOT show a subcategory field, a subcategory picker or a "New subcategory" action for any transaction type. A new transaction SHALL be saved with no subcategory. This supersedes the `money-tracker-v1` transaction-entry requirements "Assign a category, with subcategory optional" (its subcategory parts) and "Create a category or subcategory without leaving the sheet" (its subcategory parts).

#### Scenario: Recording an expense
- **WHEN** the user records an expense with a category, an account and an amount
- **THEN** the sheet offered no subcategory field and the saved transaction has a null subcategory

#### Scenario: Choosing a category recalls nothing
- **WHEN** the user picks a category that earlier transactions used with a subcategory
- **THEN** no subcategory is pre-filled and the new transaction is saved with a null subcategory

### Requirement: Editing keeps a stored subcategory while the category is unchanged
Editing a transaction that already carries a subcategory SHALL keep that subcategory when the saved category is the same as the stored one, and SHALL clear it when the category changes or the transaction no longer has a category.

#### Scenario: Fixing the note of an old transaction
- **WHEN** the user edits only the note of a transaction stored with category Pemberian and subcategory Arisan
- **THEN** the transaction is saved with subcategory Arisan still set

#### Scenario: Moving an old transaction to another category
- **WHEN** the user changes that transaction's category from Pemberian to Makan & Minum
- **THEN** the transaction is saved with a null subcategory, because a subcategory belongs to exactly one category

#### Scenario: Turning an old expense into a transfer
- **WHEN** the user changes that transaction's type to Transfer
- **THEN** the transaction is saved with no category and a null subcategory

### Requirement: Category manager manages categories only
The category manager SHALL list categories without a subcategory count, an expand control, a subcategory list, an "Add subcategory" action or subcategory menus. Each row SHALL show its colour, name and transaction count. Rename, recolour, reorder, archive, delete and merge of categories SHALL work as before. This supersedes the subcategory parts of the `money-tracker-v1` category-management requirements.

#### Scenario: Opening the manager
- **WHEN** the user opens Categories on the Expense tab
- **THEN** each row shows its swatch, its name and "N transactions", and no row can be expanded

#### Scenario: Merging a category that has stored subcategories
- **WHEN** the user merges a category whose stored subcategories are not visible
- **THEN** its transactions move to the target and its stored subcategories are carried across as before, and the confirmation text does not mention subcategories

### Requirement: Transaction rows name the account and the note only
Every transaction row in Daily, search and the Stats category detail SHALL show the category (or the type fallback) as its first line and `Account · note` as its second line, with no subcategory, even when the transaction has a stored subcategory. A transfer row SHALL still name both accounts. This supersedes "Show account and subcategory on every row" (`money-tracker-v1`) and "Account named on every row, quietly" (`buku-kas`) where they mention the subcategory.

#### Scenario: An old row with a subcategory
- **WHEN** a transaction has category Pemberian, stored subcategory Arisan, account BCA and note "Uang Arisan"
- **THEN** its row reads "Pemberian" over "BCA · Uang Arisan"

### Requirement: Category detail without a subcategory split
The Stats category detail SHALL show, in order: the category total, its share, count and average; its strip (Monthly mode only); then its transactions. It SHALL NOT show a "By subcategory" section in either chart style. This supersedes "Category detail shows trend and subcategories" (`buku-kas`) where it lists the subcategory split.

#### Scenario: Drilling into Pemberian
- **WHEN** the user taps Pemberian on Stats for October 2026
- **THEN** the detail shows the headline figures, the strip and the October transactions, and no "By subcategory" heading

### Requirement: Stored subcategories survive every round trip
The app SHALL NOT drop the `subcategory` table, the `subcategoryId` column or any stored subcategory row. Excel import SHALL still read the `Subcategory` column and file a non-blank value under its category. Export SHALL still write a `Subcategory` column with the stored name. Backup SHALL still write the `Subcategories` sheet and restore SHALL still read it and relink transactions to their subcategory. The backup and restore screens SHALL NOT list subcategory counts, and a restore that would create only subcategories SHALL still count as having something to restore.

#### Scenario: Restoring a backup made before this change
- **WHEN** the user restores a backup whose `Subcategories` sheet lists Arisan under Pemberian and whose transactions reference it
- **THEN** the subcategory row is created and those transactions are linked to it, and the preview does not show a subcategory count

#### Scenario: Backing up after this change
- **WHEN** the user backs up a database holding stored subcategories
- **THEN** the workbook still contains the `Subcategories` sheet with every row, and the `Saku` sheet's `Subcategory` column carries each transaction's stored name

#### Scenario: Importing a Money Manager export
- **WHEN** the user imports the reference export, whose `Subcategory` column is empty
- **THEN** every row is imported with a null subcategory, exactly as before
