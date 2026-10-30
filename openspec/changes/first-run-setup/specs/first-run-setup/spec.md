## ADDED Requirements

### Requirement: Offer first-run setup once, on an unused install
On launch the app SHALL show the welcome screen, before the main shell, only when first run has not been completed or skipped and the ledger holds no transaction of any type. Otherwise the app SHALL open the shell directly. Completion SHALL be persisted on the device so that it survives a restart.

#### Scenario: A fresh install
- **WHEN** the app is launched for the first time on a new install
- **THEN** the welcome screen is shown with Import from Saku, Import from Money Manager, Set up manually and Skip, and no bottom navigation bar

#### Scenario: Data already exists
- **WHEN** the app is launched, first run has never been completed, and the ledger holds at least one transaction
- **THEN** the shell opens on Trans and the welcome screen is not shown

#### Scenario: Completed earlier
- **WHEN** first run was completed or skipped and the app is launched again
- **THEN** the shell opens on Trans and the welcome screen is not shown, even when the ledger is empty

### Requirement: Skip leaves everything as it is
The welcome screen SHALL offer Skip. Skip MUST write nothing to the database, SHALL mark first run complete, and SHALL open the shell.

#### Scenario: Skipping
- **WHEN** the user taps Skip on the welcome screen
- **THEN** Cash and Card keep their opening balance and as-of date, no category is created, the shell opens on Trans, and the next launch does not offer setup

### Requirement: Import paths reuse the existing import screens
Import from Saku SHALL open a chooser listing Restore from backup, Import database and Import Excel, each opening the same screen More opens. Import from Money Manager SHALL open the same Import from Money Manager screen More opens. When the user leaves an import screen and the ledger now holds a transaction, first run SHALL be marked complete and the shell SHALL open. When nothing was imported, the user SHALL return to the screen they came from.

#### Scenario: Importing from Money Manager
- **WHEN** the user picks Import from Money Manager, imports a file, and taps Done
- **THEN** first run is marked complete and the shell opens on Trans showing the imported rows

#### Scenario: Restoring a backup
- **WHEN** the user picks Import from Saku, then Restore from backup, restores a backup holding transactions, and goes back
- **THEN** first run is marked complete and the shell opens

#### Scenario: Backing out of an import
- **WHEN** the user opens Import from Money Manager and goes back without importing
- **THEN** the welcome screen is shown again and first run is not complete

### Requirement: Set up accounts by hand
Set up manually SHALL first list every active account with its name, its type and an amount field for how much is in it today. The user SHALL be able to rename an account, change its type, add an account with a name, type and amount, and remove an account added during setup. A liability's field SHALL ask how much is owed today and SHALL take a positive figure, stored as a negative opening balance. Every name MUST be non-blank and MUST NOT match another account's name ignoring case and surrounding whitespace, archived accounts included. An empty amount SHALL mean zero.

#### Scenario: The seeded accounts are listed
- **WHEN** the user opens Set up manually on a fresh install
- **THEN** Cash (Cash) and Card (Bank) are listed, each with an empty amount field

#### Scenario: Adding an account
- **WHEN** the user taps Add account, names it "Gopay", keeps type Bank and enters 150.000
- **THEN** a third row for Gopay is listed with 150.000 beneath its field

#### Scenario: A duplicate name
- **WHEN** the user renames Card to "cash "
- **THEN** Next is refused with a message on that row naming the clash

### Requirement: Choose categories by hand
After the accounts step the user SHALL be offered expense and income categories: a short list of common suggestions, none ticked, and a field to add a name of their own to either kind. The user MAY continue with none chosen. Names SHALL be de-duplicated per kind ignoring case and surrounding whitespace, including against categories that already exist.

#### Scenario: Ticking suggestions and adding one
- **WHEN** the user ticks "Food" and "Transport" under Expense, adds "Freelance" under Income, and taps Done
- **THEN** expense categories Food and Transport and income category Freelance exist, and no other category was created

#### Scenario: Choosing none
- **WHEN** the user taps Done with nothing ticked or added
- **THEN** no category is created and setup completes

### Requirement: Done writes opening balances as of today
Done SHALL write, through the existing account repository calls, every changed name and type, and every account's opening balance with an as-of date of the start of the current day in the device time zone, the same date for every account. Added accounts SHALL be created with that same as-of date. Zero SHALL be written like any other amount. On success first run SHALL be marked complete and the shell SHALL open; on a refusal the message SHALL be shown and first run SHALL stay incomplete. Pressing Done again after a partial failure MUST NOT create a second account or category.

#### Scenario: Setting real balances
- **WHEN** on 30 Oct 2026 the user enters Cash 250.000 and Card 4.100.000 and taps Done
- **THEN** Cash's opening balance is 250.000 and Card's 4.100.000, both as of 30 Oct 2026 00:00 local time, and the Accounts screen shows those balances

#### Scenario: Leaving an account at zero
- **WHEN** the user leaves Card's field empty and taps Done
- **THEN** Card's opening balance is 0 as of the start of today

### Requirement: History imported after setup keeps the stated balances
After a manual setup, an import of older history SHALL NOT change any balance the user stated. Imported income and expense dated before the as-of date SHALL appear in Trans and Stats without moving a balance. An imported transfer dated before a set-up account's as-of date SHALL be refused by the existing transfer rule, and the import SHALL then write nothing.

#### Scenario: Older expenses after setup
- **WHEN** the user set Cash to 250.000 as of 30 Oct 2026 and then imports expenses on Cash dated in September 2026
- **THEN** the expenses are listed under September and Cash still reads 250.000

#### Scenario: An older transfer after setup
- **WHEN** the user set Cash and Card as of 30 Oct 2026 and then imports a file holding a transfer from Card to Cash dated 1 Oct 2026
- **THEN** the import is refused with a message naming the account, no row from the file is written, and both balances are unchanged
