# account-management Specification

## Purpose
Typed accounts, balances derived from an opening balance and an as-of date, reconciliation through dated adjustments, and the Spendable, Invested and Net worth figures.
## Requirements
### Requirement: Accounts are typed and user-managed
Every account MUST have a name and one of four types: Cash, Bank, Investment or Liability. The user SHALL be able
to create, rename, retype, recolour and archive accounts. On first run the app SHALL create two accounts, Cash
(Cash) and Card (Bank), and nothing else.

#### Scenario: First run
- **WHEN** the app is launched for the first time
- **THEN** Cash and Card exist with their types set, each holding a colour slot and an opening balance of zero, and both share one as-of date

#### Scenario: Adding an account
- **WHEN** the user creates an account named "Gopay" of type Bank with an opening balance and an as-of date
- **THEN** it appears in the accounts list, in every account picker, and in the Spendable figure

#### Scenario: Archiving an account with history
- **WHEN** the user archives an account referenced by transactions
- **THEN** it is removed from the pickers, its transactions are unchanged, and it no longer contributes to the Spendable, Invested or Net worth figures

### Requirement: Derive balances from an opening balance and an as-of date
Each account MUST carry an opening balance and an `openingAsOf` date. The account's balance SHALL be the opening
balance plus every inflow minus every outflow among transactions dated on or after `openingAsOf`. Transactions
dated before `openingAsOf` MUST NOT affect the balance.

#### Scenario: Historical rows do not move balances
- **WHEN** an account has an opening balance of 8.420.000 as of 17 Aug 2026, and 1.150 imported transactions all dated before that
- **THEN** the account's balance remains 8.420.000

#### Scenario: A transaction on the as-of date counts
- **WHEN** an expense of 187.000 is dated exactly on the account's `openingAsOf`
- **THEN** the balance is reduced by 187.000

#### Scenario: A transfer moves both balances
- **WHEN** a transfer of 500.000 is recorded from BCA to RDPU, both after their as-of dates
- **THEN** BCA decreases by 500.000, RDPU increases by 500.000, and Net worth is unchanged

### Requirement: Reconcile an account against its real balance
The user SHALL be able to enter an account's true current balance. Where it differs from the derived balance, the
app MUST record the difference as a dated transaction of type `ADJUSTMENT` against that account. The app MUST NOT
silently overwrite a balance.

#### Scenario: Reconciling downward
- **WHEN** the derived balance is 1.038.500, the user enters a true balance of 985.000, and confirms
- **THEN** an `ADJUSTMENT` transaction of −53.500 is created, dated now, and the derived balance becomes 985.000

#### Scenario: Reconciling with no difference
- **WHEN** the entered balance equals the derived balance
- **THEN** no adjustment transaction is created and the reconciliation date is still recorded

#### Scenario: Adjustments are visible and reversible
- **WHEN** an adjustment has been created
- **THEN** it appears in the transaction list like any other row and can be edited or deleted

#### Scenario: Adjustments are excluded from spending
- **WHEN** a period's stats are computed
- **THEN** adjustment transactions are excluded from income and expense totals, because they correct a balance rather than record a flow

### Requirement: Flag accounts that have drifted out of check
An account not reconciled within the last 30 days SHALL be flagged in the accounts list. An account never
reconciled SHALL be shown in a distinct state. The wording is set by the `buku-kas` "Balance status wording"
requirement.

#### Scenario: A stale account
- **WHEN** an account was last reconciled 45 days ago
- **THEN** its row reads "Checked" with the last reconciliation date, in the warning colour

#### Scenario: A never-reconciled account
- **WHEN** an account has never been reconciled
- **THEN** its row reads "Balance not checked yet" in secondary text, which is distinct from both the checked and the stale states

### Requirement: Summarise holdings as Spendable, Invested and Net worth
The accounts screen SHALL show three figures: Spendable, the sum of Cash and Bank balances; Invested, the sum of
Investment balances; and Net worth, Spendable plus Invested minus the amount owed. A Liabilities figure SHALL be
shown only when at least one non-archived liability account exists.

A liability account's stored balance MUST be negative while money is owed: spending against it is an expense, which
the balance formula subtracts, so debt lowers the balance exactly as it lowers net worth. The Liabilities figure
displayed to the user is the negation of that sum, and is therefore positive.

#### Scenario: Taking on debt lowers net worth
- **WHEN** the user holds BCA 1.000.000 and spends 500.000 on a liability account
- **THEN** the header reads Spendable 1.000.000, Liabilities 500.000, Net worth 500.000

#### Scenario: Summary with no liabilities
- **WHEN** the user holds BCA 8.420.000, Jago 2.150.000, Cash 385.000 and RDPU 5.000.000, and no liability account exists
- **THEN** the header reads Spendable 10.955.000, Invested 5.000.000, Net worth 15.955.000, and no Liabilities figure is rendered

#### Scenario: Summary once a liability exists
- **WHEN** the user adds a liability account carrying a balance
- **THEN** a Liabilities figure appears and Net worth is reduced by it

### Requirement: A transfer must postdate both accounts' as-of dates
The app MUST refuse to record a transfer dated before either the source or the destination account's
`openingAsOf`. Because the as-of filter is applied per account, such a transfer is counted on one leg and not the
other, which invents or destroys money in Net worth. Anything that early is already reflected in the opening
balances. The first-run seed gives both of its accounts the same as-of date, so the case is unreachable there.

#### Scenario: Refusing a transfer that predates an opening date
- **WHEN** the user records a transfer from an account whose as-of date is later than the transfer's date
- **THEN** the save is blocked with a message naming the account whose opening date is too late

### Requirement: Set the opening balance after an account exists
The user SHALL be able to change an account's opening balance and as-of date after creation, from the accounts
screen. The first-run seed opens every account at zero, so this is how real figures are entered.

#### Scenario: Setting a seeded account's real balance
- **WHEN** the user edits the seeded Card account and enters its true opening balance and an as-of date
- **THEN** the account's opening balance and as-of date are stored, and its derived balance reflects them immediately
