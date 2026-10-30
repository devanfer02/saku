# transaction-entry Specification

## Purpose
The entry sheet: three transaction types, whole-rupiah amounts with inline arithmetic, a category and an account on every row, and edit and delete.
## Requirements
### Requirement: Record a transaction of one of three types
The entry sheet SHALL offer exactly three transaction types, Income, Expense and Transfer, selected by a segmented
control. Expense SHALL be the default when the sheet is opened from the transaction list.

#### Scenario: Opening the entry sheet
- **WHEN** the user taps the add button on the transaction list
- **THEN** the entry sheet opens with Expense selected, the date and time defaulted to now, and the amount field focused

#### Scenario: Switching type preserves compatible input
- **WHEN** the user has entered an amount and a note, then switches from Expense to Income
- **THEN** the amount, date, time and note are retained and the category picker reloads with income categories

#### Scenario: Switching to Transfer replaces the category field
- **WHEN** the user switches the type to Transfer
- **THEN** the category field is replaced by a destination account field, because a transfer is not spending

### Requirement: Enter an amount in whole rupiah
Amounts SHALL be entered and stored as whole rupiah with no subunit. The keypad SHALL provide digits, a `000` key,
`+` and `−` for inline arithmetic, a backspace, and a save action.

#### Scenario: Using the 000 key
- **WHEN** the user types `187` then taps `000`
- **THEN** the amount field reads `187.000`

#### Scenario: Inline arithmetic
- **WHEN** the user enters `16.000`, taps `+`, enters `14.000`, and saves
- **THEN** the expression is evaluated and a single transaction of `30.000` is recorded

#### Scenario: Rejecting a zero or empty amount
- **WHEN** the user attempts to save with an empty amount or an amount of zero
- **THEN** the save is blocked and the amount field shows an inline message naming what is missing

### Requirement: Assign a category
An Income or Expense transaction MUST have a category. The sheet offers no subcategory; see
`subcategory-retirement`.

#### Scenario: Saving an expense without a category
- **WHEN** the user attempts to save an Expense with no category selected
- **THEN** the save is blocked and the category field shows an inline message

### Requirement: Create a category without leaving the sheet
The category picker SHALL end with a "New category" action. Creating from the picker SHALL persist
the new record, select it, and return the user to the entry sheet with all other field values intact.

#### Scenario: Creating a category inline
- **WHEN** the user opens the category picker, taps "New category", enters a name, and confirms
- **THEN** the category is created with the next available colour slot, is selected on the sheet, and the amount and note already entered are unchanged

#### Scenario: Rejecting a duplicate name
- **WHEN** the user tries to create a category whose name matches an existing non-archived category, ignoring case and surrounding whitespace
- **THEN** creation is refused with a message naming the existing category, and the existing one is offered for selection

### Requirement: Assign an account to every transaction
Every transaction MUST reference an account. A Transfer MUST additionally reference a destination account that differs
from the source. Archived accounts MUST NOT appear in the pickers.

#### Scenario: Recording a transfer between accounts
- **WHEN** the user records a Transfer of 500.000 from BCA to RDPU SucorInvest
- **THEN** one transaction is stored with both a source and a destination account, BCA decreases by 500.000, and RDPU increases by 500.000

#### Scenario: Blocking a transfer to the same account
- **WHEN** the user selects the same account as both source and destination
- **THEN** the save is blocked and the destination field shows an inline message

### Requirement: Edit and delete a recorded transaction
The user SHALL be able to edit any field of an existing transaction, and to delete it. Deletion SHALL require a single
confirmation and SHALL affect no other record.

#### Scenario: Editing an amount updates balances
- **WHEN** the user changes a recorded expense from 187.000 to 170.000
- **THEN** the transaction is updated and the affected account balance moves by 17.000

#### Scenario: Deleting a transaction
- **WHEN** the user deletes a transaction and confirms
- **THEN** that transaction alone is removed, its account balance is recalculated, and no other transaction is modified

#### Scenario: Deleting one leg of a transfer
- **WHEN** the user deletes a transfer
- **THEN** both the source and destination effects are removed together, because a transfer is stored as one record
