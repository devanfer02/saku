# Tasks — Money Tracker v1

## Build sequence

Four phases. Phase 3 is four independent tracks sized to run as **four concurrent agents, one per worktree** —
the ceiling for this project. Each track owns a disjoint set of directories, so four agents can work without
touching each other's files or each other's branches.

```
Phase 1  FOUNDATION          groups 1–5, sequential          — everything depends on this
Phase 1b EXCEL SPIKE         group 6, concurrent with 1–5    — isolated; only gates track F
Phase 2  PROVING SLICE       groups 7–8, sequential          — read path + write path through the whole stack
─────────────────────────────── fan-out point ───────────────────────────────
Phase 3  FEATURE TRACKS      groups 9–12, PARALLEL — 4 agents, 4 worktrees
           agent 1 · track C  category management  group 9   → ui/categories/
           agent 2 · track D  accounts             group 10  → ui/accounts/
           agent 3 · track E  stats                group 11  → ui/stats/
           agent 4 · track F  excel import/export  group 12  → excel/, ui/importexport/
Phase 4  INTEGRATION         group 13, sequential
```

**Why these four split cleanly.** Each track's screens live in one directory, each depends only on frozen
foundation APIs, and no track needs another track's output. Track F is the one with an external precondition —
group 6 must have passed before its agent starts, or it may be building on the wrong Excel library.

**Track sizes are uneven and that is fine.** F is roughly twice C. Launch all four together anyway; C and D
finishing early costs nothing, whereas splitting F across two agents would put two of them in the same files.

**Why a proving slice rather than straight to fan-out.** Groups 3 and 4 build shared components with no consumer
yet, which is where speculative APIs come from. Groups 7 and 8 push a read path and a write path all the way
through those components before four tracks commit to them. Rework found here costs one screen; found after
fan-out it costs four.

**What each track owns exclusively.** Track C owns `ui/categories/`, D owns `ui/accounts/`, E owns `ui/stats/`,
F owns `excel/` and `ui/importexport/`. No track edits `ui/shared/`, `ui/theme/` or `data/` after phase 2 — a
track that finds it needs a shared change stops and raises it rather than editing in place, because that file is
four tracks' common ground.

---

## 1. Foundation — project setup

- [ ] 1.1 Initialise the git repository, commit the current template state as the baseline, and confirm `.gitignore` covers `local.properties` and `.idea/`
- [ ] 1.2 Rename the package from `com.example.myapplication` to the agreed id across sources, manifest, `namespace` and `applicationId`; set `rootProject.name` in `settings.gradle.kts`
- [ ] 1.3 Set `minSdk 26` and `targetSdk 35`; verify `java.time` resolves without desugaring
- [ ] 1.4 Add Room, KSP, Navigation Compose and kotlinx-datetime to `gradle/libs.versions.toml` and `app/build.gradle.kts`
- [ ] 1.5 Delete the template `MainActivity` greeting, `ui/theme/{Color,Theme,Type}.kt` contents and the two example tests; leave a scaffold that builds
- [ ] 1.6 Create the package skeleton — `data/`, `domain/`, `ui/theme/`, `ui/shared/`, `ui/transactions/`, `ui/categories/`, `ui/accounts/`, `ui/stats/`, `ui/importexport/`, `excel/` — so track boundaries exist before any track starts
- [ ] 1.7 Confirm the merged manifest declares no `INTERNET` permission, and add a unit test asserting its absence

## 2. Foundation — domain and data layer

Locks the repository contracts every track codes against. Nothing here may change once phase 3 begins.

- [ ] 2.1 Define pure domain types in `domain/`: `Money` as a `Long` rupiah value class, `TxnType`, `AccountType`, `CategoryKind`, `ColorSlot`
- [ ] 2.2 Implement `DateRange` and period arithmetic — week, month and year boundaries, step forward and back, custom range — as pure functions with unit tests. Consumed by both the transaction list and stats
- [ ] 2.3 Implement `dedupHash(occurredAt, type, amount, categoryName, note)` as a pure function with unit tests
- [ ] 2.4 Implement `nextColorSlot(existingSlots)` — creation order, wrapping after the eighth — with unit tests
- [ ] 2.5 Define the Room entities `account`, `category`, `subcategory`, `txn`, `import_batch` with the columns named in `design.md`
- [ ] 2.6 Add indexes on `txn(occurredAt)`, `txn(accountId)`, `txn(categoryId)` and a unique index on `txn(dedupHash)`
- [ ] 2.7 Implement the balance aggregate query — opening balance plus inflow minus outflow, filtered to `occurredAt >= openingAsOf` — and unit-test it against the scenarios in `specs/account-management/spec.md`
- [ ] 2.8 Implement period aggregate queries for income, expense and per-category totals, excluding transfers and adjustments; unit-test the exclusion
- [ ] 2.9 Define and implement the repository interfaces — `TransactionRepository`, `AccountRepository`, `CategoryRepository`, `ImportRepository` — exposing `Flow`. **These are the contracts phase 3 depends on; review them before proceeding**
- [ ] 2.10 Implement the first-run seed: sixteen expense categories and four accounts, each with a colour slot
- [ ] 2.11 Unit-test that amounts are `Long` throughout and that no aggregate path uses floating point

## 3. Foundation — design system

- [ ] 3.1 Define the light and dark token sets — background, surface, ink, ink-2, ink-3, line, accent, income, expense, transfer — as complete sets, neither derived from the other
- [ ] 3.2 Define the eight-slot categorical chart palette per theme in shared slot order, using the validated hex values from the design agreement, and expose `colorForSlot(slot)` resolving against the active theme
- [ ] 3.3 Define the type scale and spacing scale
- [ ] 3.4 Implement `formatIdr(Money)`: `Rp 5.061.400`, full-stop thousands separator, no decimal component, with a compact variant omitting the `Rp` prefix for dense rows
- [ ] 3.5 Implement `colorForDirection(TxnType)` returning the income, expense or neutral token — never the accent
- [ ] 3.6 Add the appearance setting: follow system, always light, always dark
- [ ] 3.7 Screenshot-test a token reference screen in both themes to catch any colour defined for only one theme

## 4. Foundation — shared components

Every component here has at least two consumers. Each ships with a Compose preview in both themes.

- [ ] 4.1 `MoneyText` — formats a `Money` with direction colour. Consumers: every screen
- [ ] 4.2 `TransactionRow` — category, note, amount, account name with colour swatch, optional subcategory, both accounts for a transfer. Consumers: Daily list, stats drill-through
- [ ] 4.3 `Donut` — segments from (slot, value) pairs, 2px gaps, centre label slot, zero and negative values omitted from the ring. Consumers: stats, accounts
- [ ] 4.4 `RankedRow` — colour chip, name, optional share percentage, value. Consumers: stats category list, accounts list
- [ ] 4.5 `CategoryPicker` and `SubcategoryPicker` — filtered by kind and parent, archived excluded, each ending in a create action. Consumers: entry sheet, import mapping, stats filters
- [ ] 4.6 `AccountPicker` — archived excluded, colour swatch per row. Consumers: entry sheet, import mapping, accounts
- [ ] 4.7 `PeriodBar` — range display with step-back and step-forward, backed by `DateRange`. Consumers: transaction list month header, stats period selector
- [ ] 4.8 `SegmentedTabs` — used for the entry sheet's type selector, the category screen's kind tabs, and the stats income/expense tabs
- [ ] 4.9 `ConfirmDialog` — title, body, affected-count line, confirm and cancel. Consumers: delete, merge, reconcile, batch reversal
- [ ] 4.10 `EmptyState` — icon, message, optional action. Consumers: stats, list, import history
- [ ] 4.11 Write component tests covering the donut's gap and omission rules, and `TransactionRow`'s transfer variant

## 5. Foundation — app shell

- [ ] 5.1 Build the four-destination bottom navigation with per-destination state retention
- [ ] 5.2 Wire Navigation Compose with a placeholder screen per destination, so every track has a mount point that already exists
- [ ] 5.3 Build the More menu listing category management, import from Money Manager, import Excel, export Excel, import history and appearance
- [ ] 5.4 Verify the shell builds and runs with all four placeholders before phase 2 starts

## 6. Excel library spike — concurrent with groups 1–5, gates track F

- [ ] 6.1 Add `fastexcel` and `fastexcel-reader`; build a debug APK and confirm it assembles without method-count or desugaring errors
- [ ] 6.2 On a device or emulator, read `data/mock-money-manager.xlsx` end to end and assert 1,200 rows and 11 header names, two of which are `Account`
- [ ] 6.3 Write an `.xlsx` on device, read it back, and assert round-trip equality
- [ ] 6.4 Record the outcome in `design.md`. If any step fails, switch to Apache POI with `poi-ooxml-lite` and note the APK size cost before track F starts

## 7. Proving slice — transaction list (read path)

Pushes the data layer, `DateRange`, `MoneyText`, `TransactionRow` and `PeriodBar` through a real screen.

- [ ] 7.1 Build the month header using `PeriodBar`, with Income / Expenses / Total figures excluding transfers and adjustments
- [ ] 7.2 Build the Daily tab: day-grouped descending list, per-day subtotals excluding transfers and adjustments, no header for empty days
- [ ] 7.3 Render rows via the shared `TransactionRow`, including the transfer variant showing both accounts
- [ ] 7.4 Build the Calendar tab: month grid with per-day net, empty days rendered visibly empty rather than as zero
- [ ] 7.5 Wire calendar day tap through to the Daily tab scrolled to that day, and row tap through to edit
- [ ] 7.6 Write tests for every scenario in `specs/transaction-list/spec.md`
- [ ] 7.7 **Foundation review** — record which shared components needed changing and fix them in `ui/shared/` now, before the write path

## 8. Proving slice — transaction entry (write path)

Pushes the pickers, validation and every repository write through a real screen.

- [ ] 8.1 Build the entry sheet using `SegmentedTabs`: three-way type selector, date and time defaulted to now, amount, category, subcategory, account, note
- [ ] 8.2 Build the numeric keypad with digits, `000`, backspace, `+` and `−` inline arithmetic, and save
- [ ] 8.3 Wire the shared category, subcategory and account pickers into the sheet
- [ ] 8.4 Implement last-used subcategory recall per category, and clearing the subcategory when the category changes
- [ ] 8.5 Implement inline create from both pickers: persist, select, return with all other field values intact
- [ ] 8.6 Implement the Transfer variant: destination account replaces category, same-account transfers blocked
- [ ] 8.7 Implement validation — non-zero amount, category required for income and expense, destination required and distinct for transfers
- [ ] 8.8 Implement edit and delete, including single-confirmation delete via `ConfirmDialog` and balance recalculation
- [ ] 8.9 Write tests for every scenario in `specs/transaction-entry/spec.md`
- [ ] 8.10 **Fan-out gate** — freeze `data/`, `domain/`, `ui/theme/` and `ui/shared/`. Record the freeze in `design.md`. Tracks C, D, E and F may start

---

## 9. Track C — category management

Owns `ui/categories/`. Depends on: `CategoryRepository`, `ConfirmDialog`, `SegmentedTabs`, `colorForSlot`.

- [ ] 9.1 Build the management screen: Expense and Income tabs, category rows with colour, name and subcategory count, expanding to subcategories
- [ ] 9.2 Implement create, rename and recolour for categories and subcategories, with case- and whitespace-insensitive duplicate rejection
- [ ] 9.3 Implement drag reordering persisted to `sortOrder`
- [ ] 9.4 Implement archive and unarchive, and restrict delete to zero-reference records
- [ ] 9.5 Implement merge: same-kind targets only, affected count shown in `ConfirmDialog` before confirmation, subcategories carried across with name collisions merged
- [ ] 9.6 Write tests for every scenario in `specs/category-management/spec.md`

## 10. Track D — accounts

Owns `ui/accounts/`. Depends on: `AccountRepository`, `Donut`, `RankedRow`, `ConfirmDialog`, `MoneyText`.

- [ ] 10.1 Build the accounts screen with the Spendable / Invested / Total header, hiding Liabilities until a liability account exists
- [ ] 10.2 Render the balance donut via the shared `Donut` with centre total, and the account list via `RankedRow` acting as its legend
- [ ] 10.3 Implement account create, rename, retype, recolour and archive
- [ ] 10.4 Implement the Reconcile flow: enter true balance, write a dated `ADJUSTMENT` for the difference, no adjustment when the difference is zero
- [ ] 10.5 Implement staleness flagging at 30 days, with a distinct never-reconciled state
- [ ] 10.6 Write tests for every scenario in `specs/account-management/spec.md`

## 11. Track E — stats

Owns `ui/stats/`. Depends on: `TransactionRepository` aggregates, `PeriodBar`, `Donut`, `RankedRow`,
`SegmentedTabs`, `TransactionRow`, `EmptyState`.

- [ ] 11.1 Build the period selector on `PeriodBar`: Weekly, Monthly, Annually and custom Period, defaulting to the current month, with step-back and step-forward within a mode
- [ ] 11.2 Build the Income and Expenses tabs with per-side totals, excluding transfers and adjustments
- [ ] 11.3 Render the category donut via the shared `Donut`, with centre total and colours from each category's pinned slot
- [ ] 11.4 Render the ranked category list via `RankedRow` — colour, name, whole-percent share, amount — in descending amount order
- [ ] 11.5 Implement the eight-slot cap with an expandable "Other" aggregate beyond it
- [ ] 11.6 Implement drill-through from a segment or row to a transaction list built from the shared `TransactionRow`, and from "Other" to its constituent categories
- [ ] 11.7 Implement the empty state for a period with no data on the selected side
- [ ] 11.8 Write tests for every scenario in `specs/period-stats/spec.md`

## 12. Track F — excel import and export

Owns `excel/` and `ui/importexport/`. Depends on: group 6 having passed, `ImportRepository`,
`CategoryPicker`, `AccountPicker`, `ConfirmDialog`, `EmptyState`.

- [ ] 12.1 Implement layout detection from the header row, distinguishing the Money Manager shape from the Saku shape and refusing anything else with a message naming expected and found columns
- [ ] 12.2 Implement the Money Manager column mapping, including truncating sub-second precision, discarding the eleventh column, cross-checking `Amount` against `IDR`, and holding back non-IDR rows
- [ ] 12.3 Wire in the shared `dedupHash` and implement duplicate detection against existing transactions
- [ ] 12.4 Build the account mapping step on `AccountPicker` with `Card → BCA` prefilled, blocking import while any name is unmapped
- [ ] 12.5 Build the investment-destination mapping step, grouping `Transfer-Out` rows by category and requiring a destination per group
- [ ] 12.6 Build the preview: file name, row count, date span, category outcome, and the new / already-here / deleted tally with deleted structurally zero
- [ ] 12.7 Implement the commit as a single transaction that only inserts, writing an `import_batch` and stamping every inserted row
- [ ] 12.8 Build the import history screen and implement whole-batch reversal affecting only that batch's rows
- [ ] 12.9 Implement export to the Saku sheet shape through the system file picker, with both accounts written for transfers
- [ ] 12.10 Write a round-trip test: export a populated database, import into an empty one, assert every field matches
- [ ] 12.11 Write an integration test importing the real `data/mock-money-manager.xlsx`, asserting 1,200 parsed rows, the six transfers mapped, and a second import of the same file inserting zero
- [ ] 12.12 Write tests for the remaining scenarios in `specs/excel-interchange/spec.md`

---

## 13. Integration and first run

Runs once tracks C, D, E and F have all merged.

- [ ] 13.1 Build first-run setup: seed categories and accounts, then collect the four opening balances and the cutover date
- [ ] 13.2 Add the "Import from Money Manager" and "Import Excel" entry points to first-run and to More
- [ ] 13.3 Resolve any shared-component changes the tracks raised but did not apply, and re-run every track's tests
- [ ] 13.4 Verify the six approved screens against the design agreement in both themes on a real device
- [ ] 13.5 Run the full test suite and record the result; verify the release manifest still declares no network permission
- [ ] 13.6 Update `CLAUDE.md` to describe the built app rather than the template, and record the deferred v2 list
