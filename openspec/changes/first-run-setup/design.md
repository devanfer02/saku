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

**What finishes a path: the ledger, checked when an import route is left.** `FirstRunNavHost` watches its back stack; whenever the current route moves from an import route to any other, it calls `FirstRunGateViewModel.onImportLeft()`, which asks `hasAny()` again. Transactions now exist, so first run completes and the shell opens on Trans; otherwise the user is on the welcome screen (or the Saku chooser) again.
- `ImportScreen`'s Done, View transactions and Import history callbacks pop back to the welcome screen, which counts as leaving. Import history is not in the first-run graph; the shell opens on Trans instead.
- `RestoreScreen` and `DatabaseImportScreen` have only `onBack`, and system back does not call it. Watching the back stack catches both.
- Rejected: add an `onRestored` callback to the restore screens. It changes two screens other features own, and "the ledger now has rows" is the fact that matters, whatever screen produced it.
- Rejected: complete on `ImportScreen`'s Done directly. An import that wrote no rows would then end first run with an empty ledger, unlike every other path.
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

**Thumb zone.** The owner holds the phone in one hand, and the thumb reaches only to about the middle of the screen. Three rules follow, set by the owner on 30 Oct 2026 from screenshots, and they cover every first-run screen and every screen More pushes:
1. A screen that waits on one input (pick a file, choose where to save) shows one card, centred horizontally and vertically in the space under the header: a bold title, one short line, a tinted drop zone with a dashed border holding the icon, and the filled action at the card's foot. This is `PickCard` inside `ThumbZonePane`, both in `ui/shared/PickCard.kt`, with all copy passed in.
2. Primary actions sit in the lower half. A preview with confirm and cancel pins them in a bottom action bar, as the Money Manager import preview already did. Restore and database import now share that bar (`BottomActionBar`, internal to `importexport`), and the import preview and result use it too, so there is one implementation.
3. The welcome screen and the Import from Saku chooser sit their choices in the lower middle, not under the top edge.

Decisions:
- Reading and Refused reuse the same card as Idle. The file being read shows in the drop zone with a spinner, and a refusal replaces the short line with the reason in the error colour and relabels the button "Choose another file". The button never moves between the three states, so a second attempt is the same thumb position. The import refusal keeps its expected and found column lists, inside the card.
- The drop zone is a second, larger tap target for the same action. It is hidden from accessibility services, apart from its label, because the button already names the action; a screen reader user meets one button, not two.
- Backup and export keep both privacy notes (not encrypted, goes only where you put it) inside the card under the button, so they are read at the moment of choosing. A failed write shows under the card; a written file replaces the card with its result (see Result screens below).
- The intros were long paragraphs; the card holds one short line, so each was rewritten shorter, keeping its promise (nothing deleted, reversible in Import history, preview first). The export line no longer lists the columns; the file shows them.
- `ThumbZonePane` takes a vertical bias. Pick cards use 0 (centred, as the owner asked); the welcome screen and the chooser use 0.35, which lands the three welcome rows across the midline of a Pixel 8 with Skip under them. The pane still scrolls, so a large font scale never clips. On a wide screen the block keeps to a 480dp column.
- Welcome rows had uneven heights because Material's `ListItem` switches to its three-line layout (taller minimum, top-aligned content) when a subtitle wraps. The rows are now a plain `Row` with one 80dp minimum, enough for a title and a two-line subtitle, so every row matches at the default font size.
- Categories moved "New category" from the header's top-right icon to a bottom-end FAB, as Accounts already has; the empty state no longer carries its own copy of the button. The Maestro flow taps it by its label, which is unchanged.
- Rejected: a bottom action bar for the pick screens. One centred card with the button inside was the owner's reference, and a bar under an empty screen reads as a preview with nothing in it.
- Left as they are: Import history (a list; Reverse sits on each row), Appearance and the logging reminder (dialogs, already centred), the manual setup steps (Next and Done are already full width at the bottom). The result screens were in this list until the owner asked for them to change; see Result screens below.

**Welcome art.** On 30 Oct 2026 the owner found the welcome screen's top 40% empty and pale and asked for a background about finance, money and life in blue, yellow and red.

- `WelcomeArt` (`ui/firstrun/WelcomeArt.kt`) is drawn on a Compose `Canvas`: a wallet like the launcher icon with a red and a yellow card tucked in, a tilted receipt, a coin stack, a small house and a three-bar chart, on soft discs of blue, yellow and red wash. No bitmap, no vector resource, no new dependency. Shapes are placed in a 320 by 200 design box, scaled to fit and centred.
- It fills the free space above the title and nothing else. The pane's 0.35 bias became two weights, 0.675 above the block (the art) and 0.325 below (a spacer), which is the split the bias gave, so the choices land where they did. The art never sits under text, and at a large font scale, when the block needs the whole height, both weights get nothing and the art is not drawn (below 72dp it draws nothing anyway).
- Hidden from accessibility services with `clearAndSetSemantics {}`: it repeats nothing the text needs.
- Six tokens, light and dark written out: `artBlue`, `artBlueWash`, `artYellow`, `artYellowWash`, `artRed`, `artRedWash`. Named by hue because the hue is the content, as the owner named it. Light: #3F78E0, #D8E3FA, #EFC650, #FBEDC4, #DE6479, #F9D5DB. Dark: #5A86E0, #1A2A50, #D9B44A, #3A3420, #CC6680, #3D1E2A.
- Meaning clash: red means `expense` and amber means `warning`. The art red is a rose, 17.8 degrees of OKLCH hue off `expense` in light and 20.6 in dark, and the yellow sits 19.6 and 15.2 degrees off `warning`, both at lower chroma than the solid status colours. `SakuColorsTest` holds the 15 degree gap. The art is on a screen with no figures, so nothing it touches carries money meaning. No contrast floor applies: it is decoration, not text or a control.
- Rejected: a full-bleed band behind the status bar (the first-run root applies safe-drawing insets, and the art inside the column reads the same); a leaf motif in green (green is `income`); the accent blue itself for the wallet (the accent means "tap here", so the art uses its own blue).

**Result screens.** The owner also found every success state crowded at the top with the bottom half empty, and asked for the information centred and a full-width Continue at the bottom.

- `ResultLayout` (internal to `importexport`, beside `BottomActionBar`) puts the result in a `ThumbZonePane` at bias 0 and pins one full-width "Continue" in the bottom action bar, tagged `result_continue`. A short result is centred; a long one (an import naming many categories) scrolls from the top. It covers Import (Money Manager and Excel) `Imported`, Restore `Restored`, Import database `Imported`, Back up `Written` and Export `Written`.
- Import's result had Done and View transactions in its bar. Both became the one Continue, which goes where View transactions went. Import history and Choose another file stay as links inside the centred block. The `onViewTransactions` and `onDone` callbacks became `onContinue`; the Maestro flow now asserts "Continue".
- Back up and Export: the written result replaces the pick card, since its job is done. A "Save another copy" link inside the result reopens the picker, and the not-encrypted or local-only note stays with the result.
- Where Continue goes. In the More stack, Import, Restore and Import database go to Trans (`continueToLedger`, which is `leaveMoreFor(TRANSACTIONS)`), so the new rows are the first thing seen and More reopens on its menu. Back up and Export go back to the More menu (`popToMoreMenu`): they change nothing in the ledger, so Trans would show nothing new, and the menu is where the user started. In first run, every Continue pops back to the welcome route. That counts as leaving an import, so the back-stack watcher asks the gate, rows in the ledger complete first run, and the shell opens on its start tab, Trans. One path for Continue, system back and the back arrow; no second way to complete first run.
- Every screen stays stateless: `onContinue` is a parameter on both overloads, defaulting to nothing, and the nav hosts pass it.
- Only one-shot screens change. Data lists (Trans, Stats, Accounts, Categories, Import history) stay top-aligned on purpose, since more data fills the space below.
- Rejected: Continue to Trans for backup and export (nothing new there); keeping Done beside Continue on the import result (two ways out that differ only in destination); centring the result with a fixed spacer (a long import result would then push Continue off screen instead of scrolling).

## Risks / Trade-offs

- [A user sets up by hand, then imports history with old transfers] The import is refused as a whole, with the existing per-row message naming the account. Nothing is corrupted; the fix is a manual as-of change on Accounts. Recorded above; the welcome screen order reduces how often it happens.
- [Done is not one database transaction] Account and category writes go through two repositories, each with its own transactions. A failure part way leaves some writes done; `findOrCreate` makes a retry safe.
- [The gate reads the database before the first frame of the shell] One `EXISTS` query; the welcome or the shell appears after it. The theme background is painted meanwhile, so there is no white flash.
- [Nothing has run on hardware] Previews and unit tests only; the first device run is at merge.

## Migration Plan

None. No schema change. An existing install with transactions never sees the screen. An existing install with no transactions sees it once.

## Open Questions

- Suggested category names are English and generic. If the owner wants Indonesian suggestions, the list in `FirstRunAssembly.kt` is the one place to change.
