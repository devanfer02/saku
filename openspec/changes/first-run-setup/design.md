## Context

`FirstRunSeed` creates `Cash` (Cash) and `Card` (Bank) at opening balance 0, as of the epoch, and no categories. The epoch is deliberate (`SEED_CUTOVER` in `AppContainer.kt`): balances count only rows dated on or after an account's `openingAsOf`, and a transfer dated before either account's `openingAsOf` is refused by `RoomTransactionRepository.validate`, which aborts a whole import batch. So the seed opens early enough that any history imported later counts and is accepted.

The real figures are entered today on Accounts > account > Edit (`AccountsViewModel.save`, which calls `rename`, `retype`, `recolor`, then `setOpening`). The existing import screens (`ImportScreen` for Money Manager and Excel, `RestoreScreen`, `DatabaseImportScreen`) each build their own ViewModel from `AppContainer` and take only callbacks, so they can be mounted in another nav graph unchanged.

The owner widened the scope on 30 Oct 2026: the first screen is a choice between importing from Saku, importing from Money Manager, setting up by hand, or skipping.

## Goals / Non-Goals

**Goals:**
- A new user states real balances and a few categories in under a minute, or brings their history in, before seeing an empty ledger.
- Nobody with data is ever sent through it.
- No new write path: the setup uses the repository calls the accounts and category screens already use.
- Importing history after a manual setup never corrupts a balance or the Total.

**Non-Goals:**
- Changing the seed (still Cash and Card at the epoch, no categories).
- Changing the import, restore or database-import screens.
- Colour choice, reordering or archiving in setup. The accounts and category screens do that.
- A way to reopen first-run setup later. Every step it offers exists in Accounts, Categories and More.

## Decisions

**When it is offered: not completed, and the ledger has no transaction.** `FirstRunGateViewModel` reads `FirstRunStore.isComplete()` and `TransactionRepository.hasAny()` once at start-up. If either says "already in use", the shell opens directly and nothing is written.
- The "has a transaction" half is what keeps out an upgrade from a build without this screen, and a user who restored a backup or imported a .db from More on an older build. They already have history.
- Rejected: gate on `lastEnteredAt() == null`. It ignores ADJUSTMENT rows, so a ledger holding only reconciliations would be offered setup. `hasAny()` is `SELECT EXISTS(SELECT 1 FROM txn)`, one indexed row.
- Rejected: gate on the flag alone. An upgrade from a build without the flag would send the owner, with 1,594 rows, through a welcome screen.
- Rejected: gate on the ledger alone, no flag. A user who skips with an empty ledger would see the welcome screen on every launch.
- Rejected: gate on accounts having zero opening balances. Zero is a valid answer, and the seed's state is indistinguishable from a user who set zero.
- Decided once per process, not observed. An import that lands while the flow is open must not tear the screen away under the user; the flow decides when it is finished (below).

**The flag lives in SharedPreferences (`FirstRunStore`, file `saku_first_run`).** Same shape and reason as `ReminderStore` and `ChartStyleStore`: one boolean, needed before the first frame of the app, not worth a Room table. A class with no interface, because nothing asks for a second implementation. Not part of backup: a restored phone has transactions, so the ledger half of the gate already covers it.

**Where it lives: its own nav host, mounted by `SakuApp` instead of the shell.** `FirstRunNavHost` holds the welcome screen, the Saku import chooser, the manual setup, and the reused import screens under `FirstRunRoute` strings. While the gate is still reading, `SakuApp` paints the theme background only, for the one database read.
- Rejected: a route inside `SakuNavHost`. The bottom bar would be visible, the user could tab away mid-setup, and `SakuDestination.owning` would light Trans. The four-destination set is closed by design.
- Rejected: a second Activity. Two theme roots, intent plumbing for the reminder tap, and a recreate on completion, for what is a conditional root composable.

**Reuse the import screens by mounting them, not by linking into More.** `FirstRunRoute.IMPORT_MONEY_MANAGER`, `IMPORT_EXCEL`, `RESTORE` and `IMPORT_DATABASE` mount `ImportScreen`, `RestoreScreen` and `DatabaseImportScreen` with their own ViewModel factories. Labels, subtitles and icons on the chooser come from `MoreEntry`, so More and the chooser cannot drift.
- Rejected: complete first run, open the shell and navigate to More > Restore. Backing out would leave the user in the app with no way back to the choice, which the owner asked for.

**What finishes a path.**
- `ImportScreen`'s Done, View transactions and Import history callbacks mean the import committed: first run completes and the shell opens. Import history is not reachable from the first-run graph, so that link opens the shell on Trans.
- `RestoreScreen` and `DatabaseImportScreen` have only `onBack`. Leaving any import route (back or done) calls `FirstRunGateViewModel.onImportLeft()`, which asks `hasAny()` again: transactions now exist, so first run completes; otherwise the user is back on the welcome screen.
- Rejected: add an `onRestored` callback to the restore screens. It changes two screens other features own, and "the ledger now has rows" is the fact that matters, whatever screen produced it.
- A restore that adds only accounts or categories and no transaction leaves the user on the welcome screen. They can Skip or set up by hand; nothing is lost either way.

**Manual setup is one route with two steps held in the ViewModel.** The step is the user's choice, so it is ViewModel state (`FirstRunSetupViewModel`), not two routes; one ViewModel then holds both steps' drafts without a shared-scope trick. System back on the categories step returns to accounts; on the accounts step it returns to the welcome screen. Nothing is written until Done on the categories step.

**Accounts step.** One row per active account, in sort order: name, a type selector (`SegmentedTabs`, the same four types and labels as the account editor), and "In it today", an amount field typed as whole rupiah with `MoneyText` beneath it. "Add account" appends a blank Bank row. A row added in setup can be removed again; seeded rows cannot (archiving is the accounts screen's job).
- A liability row's field reads "Owed today" and takes a positive figure, stored negative, which is the convention `AccountSummary` depends on. The editor sheet asks for a signed number; a first-time user should not need to know that.
- Validation is pure (`validateAccountDrafts`): every name non-blank, and no two names equal ignoring case and surrounding whitespace, archived accounts included, so `findOrCreate` can never map a new row onto an existing account.
- Rejected: the entry sheet's keypad. It is a bottom sheet bound to one amount; four amounts on one screen want plain fields with a number keyboard.

**Categories step.** Two groups, Expense and Income, each a list of suggestions with a tick, plus a text field and Add for the user's own name. Suggestions are generic English names in `FIRST_RUN_CATEGORY_SUGGESTIONS`, data like `FirstRunSeed.DEFAULT_ACCOUNTS`, none ticked by default. Ticking nothing is allowed. Names are de-duplicated per kind ignoring case and whitespace, and against categories that already exist.
- Rejected: pre-ticking suggestions. The 30 Oct 2026 decision is that first run is not tailored; the user opts in.

**What Done writes, in order.** `planAccountWrites` and `planCategoryWrites` (pure) produce the list; the ViewModel only runs it.
1. Each seeded or existing account: `rename` if the name changed, `retype` if the type changed, then `setOpening(amount, startOfToday)`.
2. Each added account: `findOrCreate(name, type, startOfToday)` then `setOpening(amount, startOfToday)`.
3. Each chosen category: `CategoryRepository.findOrCreate(name, kind)`.
`findOrCreate` rather than `create` makes the whole run idempotent: if a write fails half way, Done can be pressed again without creating a second account or category. A refusal is shown as a message and first run stays incomplete.
- Zero is a valid answer and is written like any other: the account is then explicitly "zero as of today".

**The as-of date is the start of today in the device zone, the same for every account.** It matches the account editor, which stores a date as the start of that day, and v1's rule that first-run accounts share one cutover, so a transfer can never fall between two accounts' cutovers.
- Rejected: the instant Done is pressed. Something spent this morning and logged tonight, dated today, would then fall before the cutover and never move the balance; it also disagrees with the date-only editor.
- Rejected: keep the epoch and write the amount as the epoch opening balance. Any history imported later would then be subtracted from a figure that already reflects it.
- Rejected: write the amount as a reconcile ADJUSTMENT. Same double count on a later import, and the adjustment is itself a transaction, which would make the ledger "in use" for the gate before the user has logged anything.

**Importing history after a manual setup.** Balance = opening at `openingAsOf` + rows on or after it. With setup as of the start of today:
- Imported income and expense dated before today land in Trans and Stats and do not move any balance. The user's figure for today already contains them. This is the spec's "Historical rows do not move balances" scenario, and it is the safe outcome: the stated balance stays true.
- An imported transfer dated before today that touches a set-up account is refused by the existing transfer rule, and because import validates inside one transaction, the whole file is refused and nothing is written. Balances and the Total are unchanged. The user can move that account's as-of date back on the accounts screen (with the balance as of that date) and import again.
- Rows dated today or later do count, as they should for anything after the moment the user stated the balance.
- This is why the welcome screen lists both imports above "Set up manually": a user with history is steered to import it first, when every account still opens at the epoch.
- Rejected: relax the transfer rule for imports. It is what stops a transfer counting on one leg only and inventing money in the Total.

**Accessibility.** Each screen title is a heading (`ScreenHeader` or a `heading()` semantics on the welcome title); group titles in the categories step are headings. Every field carries its label as `contentDescription` and as visible placeholder text, like the account editor. Every tappable row, tick and button is at least `touchTarget` (48dp) tall. Ticks are `Role.Checkbox` with `toggleable`. All colour comes from `SakuTheme`; previews render light and dark through `@SakuPreview`.

## Risks / Trade-offs

- [A user sets up by hand, then imports history with old transfers] The import is refused as a whole, with the existing per-row message naming the account. Nothing is corrupted; the fix is a manual as-of change on Accounts. Recorded above; the welcome screen order reduces how often it happens.
- [Done is not one database transaction] Account and category writes go through two repositories, each with its own transactions. A failure part way leaves some writes done; `findOrCreate` makes a retry safe.
- [The gate reads the database before the first frame of the shell] One `EXISTS` query; the welcome or the shell appears after it. The theme background is painted meanwhile, so there is no white flash.
- [Nothing has run on hardware] Previews and unit tests only; the first device run is at merge.

## Migration Plan

None. No schema change. An existing install with transactions never sees the screen. An existing install with no transactions sees it once.

## Open Questions

- Suggested category names are English and generic. If the owner wants Indonesian suggestions, the list in `FirstRunAssembly.kt` is the one place to change.
