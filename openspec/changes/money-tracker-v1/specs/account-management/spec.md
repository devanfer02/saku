## ADDED Requirements

### Requirement: Accounts are typed and user-managed
Every account MUST have a name and one of four types: Cash, Bank, Investment or Liability. The user SHALL be able
to create, rename, retype, recolour and archive accounts. On first run the app SHALL create BCA (Bank),
Jago (Bank), Cash (Cash) and RDPU SucorInvest (Investment).

#### Scenario: First run
- **WHEN** the app is launched for the first time
- **THEN** the four default accounts exist with their types set, each holding a colour slot and an opening balance of zero

#### Scenario: Adding an account
- **WHEN** the user creates an account named "Gopay" of type Bank with an opening balance and an as-of date
- **THEN** it appears in the accounts list, in every account picker, and in the balance donut

#### Scenario: Archiving an account with history
- **WHEN** the user archives an account referenced by transactions
- **THEN** it is removed from the pickers, its transactions are unchanged, and it no longer contributes to the Spendable, Invested or Total figures

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
- **THEN** BCA decreases by 500.000, RDPU increases by 500.000, and the Total figure is unchanged

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
reconciled SHALL be flagged distinctly.

#### Scenario: A stale account
- **WHEN** an account was last reconciled 45 days ago
- **THEN** its row carries a visible staleness indicator naming the last reconciliation date

#### Scenario: A never-reconciled account
- **WHEN** an account has never been reconciled
- **THEN** its row reads "never reconciled" in the alert colour

### Requirement: Summarise holdings as Spendable, Invested and Total
The accounts screen SHALL show three figures: Spendable — the sum of Cash and Bank balances; Invested — the sum of
Investment balances; and Total — Spendable plus Invested minus Liability balances. A Liabilities figure SHALL be
shown only when at least one non-archived liability account exists.

#### Scenario: Summary with no liabilities
- **WHEN** the user holds BCA 8.420.000, Jago 2.150.000, Cash 385.000 and RDPU 5.000.000, and no liability account exists
- **THEN** the header reads Spendable 10.955.000, Invested 5.000.000, Total 15.955.000, and no Liabilities figure is rendered

#### Scenario: Summary once a liability exists
- **WHEN** the user adds a liability account carrying a balance
- **THEN** a Liabilities figure appears and Total is reduced by it

### Requirement: Chart balances by account
The accounts screen SHALL render a donut of balance by account, with the total in its centre and the account list
beneath acting as its legend. Each segment SHALL carry its account's colour slot, segments SHALL be separated by a
visible gap, and identity MUST NOT be conveyed by colour alone.

#### Scenario: Rendering the donut
- **WHEN** four accounts hold positive balances
- **THEN** four segments are drawn in their accounts' colours with a gap between each, the centre reads the total, and every account is named in the list beneath

#### Scenario: An account with a zero or negative balance
- **WHEN** an account's balance is zero or negative
- **THEN** it is omitted from the donut but still listed with its figure, because a donut cannot represent a negative share
