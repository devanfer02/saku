# category-management Specification

## Purpose
How the user creates, renames, recolours, reorders, archives, deletes and merges income and expense categories.
Subcategories are not managed here; see `subcategory-retirement`.
## Requirements
### Requirement: Manage categories from a dedicated screen
The app SHALL provide a category management screen, reachable from the More tab, listing expense and income
categories on separate tabs. Each category row SHALL show its colour, its name and its transaction count.

#### Scenario: Viewing categories
- **WHEN** the user opens the category screen
- **THEN** expense categories are listed in their configured sort order, each showing its colour swatch, its name and "N transactions"

### Requirement: Create categories
The user SHALL be able to create a category from the management screen and from the entry sheet's category picker.
A category MUST have a name and a kind of Expense or Income.

#### Scenario: Creating a category
- **WHEN** the user creates a category named "Asuransi" on the Expense tab
- **THEN** it is stored as an expense category, assigned the next colour slot in creation order, appended to the sort order, and made available in every expense category picker

#### Scenario: Rejecting a blank name
- **WHEN** the user attempts to create a category with an empty or whitespace-only name
- **THEN** creation is refused with an inline message

#### Scenario: Rejecting a duplicate name
- **WHEN** the user creates a category whose name matches an existing category of the same kind, archived or not, ignoring case and surrounding whitespace
- **THEN** creation is refused with a message naming the existing category

### Requirement: No categories on first run
First run SHALL create no categories. Categories come from the entry sheet's "New category" action, from the
management screen, or from an import, which creates every category its file uses.

#### Scenario: First run
- **WHEN** the app is launched for the first time
- **THEN** both category tabs are empty, and the entry sheet's category picker offers "New category"

### Requirement: Rename, recolour and reorder
The user SHALL be able to rename any category, change its colour slot, and reorder categories by dragging. Every
reorder SHALL also be reachable without a drag, as move up and move down actions. Renaming MUST preserve every
transaction's association.

#### Scenario: Renaming a category in use
- **WHEN** the user renames a category that 443 transactions reference
- **THEN** all 443 transactions continue to reference it, and the new name appears everywhere the old one did

#### Scenario: Changing a colour slot
- **WHEN** the user assigns a different colour slot to a category
- **THEN** every chart, list chip and row swatch for that category adopts the new colour, and no other category's colour changes

#### Scenario: Reordering by drag
- **WHEN** the user long-presses a category's handle and drops it two rows lower
- **THEN** the new order is stored once, on the drop, and every category picker follows it

### Requirement: Archive rather than delete where history exists
A category referenced by at least one transaction MUST NOT be deletable; it SHALL be archivable instead. Archiving
SHALL remove it from all pickers while leaving every existing transaction unchanged. Only a category with zero
referencing transactions SHALL offer deletion.

#### Scenario: Archiving a category in use
- **WHEN** the user archives a category referenced by 13 transactions
- **THEN** it disappears from the entry sheet pickers, those 13 transactions still show it, and it still appears in stats for periods that contain them

#### Scenario: Delete is unavailable for a used category
- **WHEN** the user opens the actions for a category referenced by at least one transaction
- **THEN** no delete action is offered, and archive is offered in its place

#### Scenario: Deleting an unused category
- **WHEN** the user deletes a category referenced by no transactions and confirms
- **THEN** the category is removed

#### Scenario: Restoring an archived category
- **WHEN** the user unarchives a category
- **THEN** it returns to the pickers with its previous colour slot and sort position

### Requirement: Merge one category into another
The user SHALL be able to merge a category into another category of the same kind, reassigning every referencing
transaction. The merge SHALL state the number of affected transactions before it runs and SHALL require confirmation.

#### Scenario: Confirming a merge
- **WHEN** the user merges a category holding 27 transactions into another
- **THEN** the confirmation names the count, and on confirmation all 27 transactions reference the target category and the source category is archived

#### Scenario: Merging carries stored subcategories across
- **WHEN** the merged category has stored subcategories
- **THEN** those subcategories are moved to the target category, and any whose name collides with an existing subcategory there is merged into it rather than duplicated

#### Scenario: Merge is restricted to the same kind
- **WHEN** the user selects a merge target
- **THEN** only categories of the same kind, Expense or Income, are offered
