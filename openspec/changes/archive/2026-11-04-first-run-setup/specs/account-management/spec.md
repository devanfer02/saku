## MODIFIED Requirements

### Requirement: Set the opening balance after an account exists
The user SHALL be able to change an account's opening balance and as-of date after creation, from the accounts
screen, and on the first launch of a fresh install from first-run setup (see `first-run-setup`). The first-run seed
opens every account at zero, so one of these is how real figures are entered. First-run setup writes through the same
repository calls as the accounts screen and uses the start of the current day as the as-of date.

#### Scenario: Setting a seeded account's real balance
- **WHEN** the user edits the seeded Card account and enters its true opening balance and an as-of date
- **THEN** the account's opening balance and as-of date are stored, and its derived balance reflects them immediately

#### Scenario: Setting balances during first-run setup
- **WHEN** the user enters Card's balance in first-run setup and taps Done
- **THEN** Card's opening balance is stored as of the start of that day, and the accounts screen shows it as the balance

### Requirement: A transfer must postdate both accounts' as-of dates
The app MUST refuse to record a transfer dated before either the source or the destination account's
`openingAsOf`. Because the as-of filter is applied per account, such a transfer is counted on one leg and not the
other, which invents or destroys money in Net worth. Anything that early is already reflected in the opening
balances. The first-run seed gives both of its accounts the same as-of date, and first-run setup gives every account
it writes or creates the same as-of date, so the case is unreachable there.

#### Scenario: Refusing a transfer that predates an opening date
- **WHEN** the user records a transfer from an account whose as-of date is later than the transfer's date
- **THEN** the save is blocked with a message naming the account whose opening date is too late

#### Scenario: Refusing an imported transfer that predates first-run setup
- **WHEN** first-run setup set Card as of today and the user then imports a transfer from Card dated last month
- **THEN** the import is refused as a whole and no row from the file is written
