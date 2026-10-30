## Why

A fresh install opens on an empty Trans tab with two accounts, Cash and Card, at zero, and no categories. The user has to find Accounts > account > Edit to set real balances, and the Accounts screen meanwhile says "Below zero. Set an opening balance". Task 13.1 of `money-tracker-v1` (first-run setup) was never built. Since 30 Oct 2026 first run is generic, so the first launch is also the moment to ask where the user's money history lives: in a Saku backup, in Money Manager, or nowhere yet.

## What Changes

- A welcome screen on the first launch of a fresh install, shown before the main shell, with three paths and a Skip:
  - **Import from Saku**: a short chooser leading to the existing Restore from backup, Import database (.db) and Import Excel screens.
  - **Import from Money Manager**: the existing Import from Money Manager screen.
  - **Set up manually**: two steps. Accounts: rename, retype and add accounts, and say how much is in each today. Categories: tick a few common expense and income categories and add your own.
  - **Skip**: straight to the app, nothing written.
- Finishing any path marks first run complete: an import or restore that leaves transactions in the ledger, or Done on the manual path. Backing out of an import with nothing imported returns to the welcome screen.
- The welcome screen is never shown when the ledger already has a transaction, and never again once completed or skipped. The flag is kept in SharedPreferences, like the other small settings.
- Manual Done writes each account's opening balance as of the start of today through the existing `AccountRepository` calls, and creates the chosen categories through `CategoryRepository.findOrCreate`.
- `TransactionRepository` gains `hasAny()`, a one-row existence check for the gate.

## Capabilities

### New Capabilities
- `first-run-setup`: when the welcome screen is offered, its three paths and Skip, the manual accounts and categories steps, what Done writes, and when first run counts as complete.

### Modified Capabilities
- `account-management`: opening balances can now be set during first-run setup as well as on the accounts screen; the shared-cutover rule now covers accounts added during setup.
- `app-shell-theme`: the first launch of a fresh install shows the welcome screen before the shell.
- `category-management`: first run still seeds no categories, but the manual setup path can create the ones the user picks.

## Impact

- New: `data/FirstRunStore.kt`, `ui/firstrun/` (assembly, gate and setup ViewModels, welcome, Saku import chooser and setup screens, first-run nav host), `res/values/strings_first_run.xml`, tests.
- Changed: `TxnDao` and `TransactionRepository` (`hasAny`), `AppContainer` (`firstRun` store), `ui/SakuApp.kt` (shows the first-run flow before the shell), `ui/navigation/` (`FirstRunRoute`).
- Reused unchanged: `ImportScreen`, `RestoreScreen`, `DatabaseImportScreen` and their ViewModels; `MoreEntry` labels, subtitles and icons; `SegmentedTabs`, `MoneyText`, `ScreenHeader`.
- No schema change, no migration, no new library.
