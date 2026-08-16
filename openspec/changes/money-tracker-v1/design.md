# Design — Money Tracker v1

## Context

The repo currently holds an unmodified Android Studio Compose template: package `com.example.myapplication`,
`rootProject.name = "My Application"`, six Kotlin files, no git history. Everything below is greenfield.

Two inputs shape the design. `a private analysis of the export` documents why the incumbent app failed, and
`data/mock-money-manager.xlsx` is both the seed data and the importer's fixture: 1,200 rows, 11 columns, two
of them named `Account`, `Subcategory` and `Description` empty on every row, and amounts in IDR only.

A visual design agreement covering all six screens, both theme token sets and the validated chart palette was
approved before this document was written. It is the reference for anything visual that this document leaves
unstated.

Constraints, all owner-set:

- Ad-free is a hard requirement, not a preference.
- The layout follows the incumbent app closely; the owner has a year of muscle memory in it.
- IDR only. Every row in the reference data is IDR and there is no second currency in view.
- Accounts are BCA, RDPU SucorInvest, Cash and Jago at first run, with more addable.
- Blue accent, both light and dark themes.

## Goals / Non-Goals

**Goals:**

- Four functions: record and list transactions, period stats, accounts, Excel import and export.
- Account balances that reconcile against reality, so "how much money do I have" is answerable.
- Close three of the five tracking gaps: subcategory becomes usable, accounts become real, investment transfers
  gain a destination so net worth computes.
- Import the a year of history without letting its unreliable account data corrupt current balances.
- Make ad-free structural rather than promised.

**Non-Goals:**

- Budget caps and progress bars. This is the strongest recommendation in the analysis and the first thing in v2,
  but it is a fifth function and would widen v1 materially.
- Recurring and instalment transactions, multi-currency, cloud sync and backup, receipt photos, widgets and
  reminders, sinking funds and goals.
- The reference app's Monthly, Total and Note tabs.
- Fixing gap 4 — batch entry from memory — by product means. A reminder or widget would attack it directly and both
  are deferred; the reconcile flow is the partial answer v1 does ship.

## Decisions

### Balance model: opening balance with an as-of date, plus reconciliation adjustments

`balance = openingBalance + Σ inflow − Σ outflow`, over transactions where `occurredAt >= account.openingAsOf`.

*Why the date clause.* It is what lets a year of history and a trustworthy balance coexist. Every imported
row feeds stats; none of them touch a balance, because none is dated on or after the cutover. Without it, importing
history would produce a BCA balance derived from a `Card → BCA` mapping that is a convenient fiction.

*Why reconciliation writes a transaction.* Gap 4 says roughly half of all entries were reconstructed from memory
days later, and small cash spend is systematically under-reported. Balances will drift. Silently overwriting the
balance would hide the drift; writing an `ADJUSTMENT` row makes it a measurement. The size of the monthly cash
adjustment is a number the owner currently cannot see at all.

*Alternatives considered.* Manual balance per account, disconnected from transactions — rejected because it splits
the app into two systems that never agree. Opening balance with no reconcile — rejected because there would be no
record of how wrong it got.

### Amounts are `Long` rupiah with no subunit

The reference export renders `Rp 187.000,00`, but the sen is long obsolete and every one of the 1,200 rows is a
whole number of rupiah. Storing integers means no floating point anywhere and no rounding drift across aggregation.
The `,00` tail is dropped on import and never rendered.

### Balances and period totals are SQL aggregates, not in-memory sums

Room DAOs expose `Flow` over aggregate queries. At 1,200 rows either approach works; the app is meant to hold years
more. Indexes on `txn(occurredAt)`, `txn(accountId)`, `txn(categoryId)` and a unique index on `txn(dedupHash)`.

### Transfers are one row with two account references

`accountId` plus a nullable `toAccountId`. A transfer decrements the source and increments the destination from a
single record, so the two halves can never diverge and deleting one leg is impossible. This is the direct fix for
gap 3, where Rp 5,050,800 of investment transfers currently read as money gone.

*Alternative considered.* Two linked rows, one per side, as some ledger apps do. Rejected — it doubles the row
count, complicates every aggregate with a "exclude the mirror" clause, and creates a class of bug where the halves
disagree.

### Transfers and adjustments are excluded from every income and expense figure

Uniformly: the month header, day subtotals, and both stats tabs. They appear in lists but never in totals. Moving
money between one's own accounts is not spending, and an adjustment corrects a balance rather than recording a flow.

### Category and subcategory creation is available inline from the entry sheet

Gap 1 is that subcategory was empty on all 1,200 rows because the incumbent made it optional and skippable. Keeping
it optional is right — a required field on a fast-entry sheet gets fought, not filled. What changes is the cost of
using it: both pickers end in a create action that persists, selects and returns without losing the sheet's state.
If creating a subcategory means a trip to settings, it stays empty forever.

### Archive, never delete, wherever history exists

Categories, subcategories and accounts referenced by at least one transaction cannot be deleted; they archive out of
the pickers with history intact. Only zero-reference records offer a real delete. Merge is the escape hatch for
duplicates and states its affected count before running. This also satisfies the project's standing rule against
operations whose blast radius is "all rows".

### Import previews, maps and deduplicates; it only ever inserts

No import path updates, overwrites or deletes an existing transaction. A duplicate is skipped. The preview's deleted
count is therefore structurally zero rather than incidentally zero.

Dedup key: a hash over `occurredAt` truncated to seconds, type, `amountIdr`, category name and note. The reference
data contains no two rows identical on all five, so collisions mean genuine duplicates.

Two mapping steps run before any write. Accounts: every distinct name in the file maps to an existing or new
account, with `Card → BCA` prefilled, covering 1,150 of 1,200 rows. Investment destinations: `Transfer-Out` rows
carry no destination in the export, so they are grouped by category — `RDPU SucorInvest`, `Saham` — and each group
is asked once for a destination account. Net worth becomes computable during import rather than after it.

Every confirmed import writes a batch record and stamps its rows, so a bad import is reversible as a unit.

### Excel via fastexcel, with Apache POI as the named fallback

`fastexcel` and `fastexcel-reader` are pure Java, small, and have no POI dependency. Apache POI is the obvious
alternative and the safer-looking one, but `poi-ooxml` on Android is heavy — roughly 8 MB of APK even with
`poi-ooxml-lite`, plus method-count and desugaring friction. This is the one library risk in the plan and it is
called out as such: the very first Excel task is a spike on device against the real 1,200-row file, before anything
is built on top of it.

### No `INTERNET` permission in the manifest

Ad-free enforced by the platform rather than asserted in a README. Nothing can phone home because nothing is
permitted to. The cost is that cloud sync and backup are off the table for v1, which they already were.

### minSdk 26, single Gradle module, packages by feature

API 26 provides `java.time` with no desugaring and covers effectively every device in scope. One module with
`transactions`, `stats`, `accounts`, `categories`, `excel`, `data` and `ui` packages; Navigation Compose between the
four destinations. Multi-module would buy build-time isolation this app is too small to need.

### Chart colour is identity, pinned per entity

Eight categorical slots per theme, sharing one slot order so an entity keeps its hue across themes. A slot is
assigned at creation and never reassigned, so a filter that reorders the ranking never repaints the chart. Beyond
eight, the ninth and later categories fold into an expandable "Other" rather than generating a ninth hue.

Both sets were run through a colour-vision validator rather than eyeballed, and each passes the lightness band,
chroma floor, normal-vision separation and contrast-against-surface checks. Two adjacent pairs land in the marginal
6–8 ΔE band under deuteranopia, which is only acceptable alongside a second cue — hence the mandatory 2px segment
gap and the labelled ranked list beside every donut. Colour alone never carries identity.

### Build sequencing: foundation, then one proving slice, then a four-way fan-out

The work splits into four feature tracks — category management, accounts, stats, and Excel interchange — sized to
run as four concurrent agents in four worktrees. That only holds if the layer beneath them is settled first.

*What has to be shared.* Three couplings would otherwise produce duplicate implementations. Stats' drill-through
renders the same transaction row as the Daily list. Import's mapping steps need the same account and category
pickers as the entry sheet. Accounts and stats need the same donut, with identical gap, centre-label and
slot-colour requirements. So `TransactionRow`, the pickers, `Donut`, `RankedRow`, `PeriodBar`, `SegmentedTabs`,
`ConfirmDialog`, `EmptyState` and `MoneyText` are foundation, not track-local. So are the pure utilities every
track re-derives otherwise: `DateRange` period arithmetic, `dedupHash`, `nextColorSlot`, `formatIdr`.

*Why a proving slice sits between foundation and fan-out.* Shared components written before they have a consumer
are speculative, and the first real screen usually breaks their API. The transaction list is built first as a read
path and the entry sheet second as a write path; between them they exercise every shared component and every
repository method. Rework discovered there costs one screen. Discovered after fan-out, it costs four, in four
branches, with four merge conflicts in the same files.

*The freeze.* At the fan-out gate, `data/`, `domain/`, `ui/theme/` and `ui/shared/` are frozen. A track needing a
shared change raises it rather than editing in place; those are collected and applied during integration. Without
that rule, four agents editing common ground is the failure mode this structure exists to avoid.

*Track ownership is by directory* — `ui/categories/`, `ui/accounts/`, `ui/stats/`, and `excel/` plus
`ui/importexport/` — so the split is enforceable rather than merely intended. The package skeleton is created in
task 1.6, before any track exists, so the boundaries are real from the start.

*The Excel spike runs concurrently with the whole foundation phase.* It is isolated and gates only track F, but
its outcome can change a dependency, so it must resolve before that track's agent starts.

### Donuts rather than pies

The reference app uses a filled pie. A donut is used in both places because the hole carries the figure that
belongs at the centre of attention — the period total on stats, net worth on accounts — and because segment gaps
read more cleanly on a ring. This is a deliberate, and the only significant, departure from the reference layout.

## Risks / Trade-offs

- **fastexcel may not behave on Android** → The first Excel task is an on-device spike against the real file. If it
  fails, fall back to Apache POI with `poi-ooxml-lite` and accept roughly 8 MB of APK. Nothing else in the plan is
  built on top until the spike passes.
- **Imported history is attributed to BCA, which is partly fiction** → Contained by the as-of date: those rows can
  never move a balance. The honest framing, stated in the UI, is that pre-cutover rows describe spending that was
  logged, on accounts nobody can now reconstruct.
- **Balances drift because entries are logged from memory** → Reconcile makes the drift visible and dated rather
  than eliminating it. Accounts unreconciled for 30 days flag themselves. Cash will flag constantly, which is the
  correct signal.
- **Subcategory may stay empty despite inline creation** → It stays optional by design, so this is a real
  possibility. The mitigation is cost, not coercion: last-used recall pre-fills it, and creation never leaves the
  sheet. Worth revisiting after a month of real use.
- **The dedup hash includes the note, so editing a note in the source spreadsheet defeats it** → Accepted. The
  alternative, a looser key, risks skipping genuinely distinct transactions, and skipping real data is worse than
  importing a duplicate the user can see and delete.
- **Deferring budgets means v1 still mostly records history** → Accepted deliberately and scoped as the first item
  of v2. The account and category groundwork v1 lays is what budgets will need.
- **Rewriting the template package is a one-way change** → Trivial to do first, painful to do late. It is task one.

## Migration Plan

There is no existing installation to migrate. Sequence:

1. Initialise git before any code lands — the repo is not yet under version control.
2. Rename package and project, replace the template files, set minSdk and targetSdk.
3. Spike fastexcel on device against `data/mock-money-manager.xlsx` before building the importer.
4. Data layer, then the four functions, then import and export.
5. First run seeds the sixteen Indonesian categories and the four accounts, then asks for opening balances and a
   cutover date.
6. The owner imports the Money Manager export, maps `Card → BCA` and the two investment destinations, and confirms.

Rollback at any point is uninstalling; there is no server and no shared state.

## Open Questions

None block implementation. Each has a stated default that will be taken if unanswered.

- **App name.** Working name `Saku`. Touches strings and the package id only.
- **Package id.** Proposed `com.dvnnfrr.saku`, replacing `com.example.myapplication`.
- **Starter subcategories.** Whether first run pre-creates `Arisan · Rumah · Dika · Gereja` under Pemberian and
  `Restoran · Warung · Groceries` under Makan & Minum. The analysis argues for it — Pemberian is 50% of spend and
  unreadable without a split. Import cannot backfill which subcategory an old row belongs to either way. Default if
  unanswered: create them.
- **Gopay.** Three rows across a year. Default: offer it at the import mapping step, create nothing up
  front.
- **Opening balances and the cutover date.** Supplied by the owner in the app at first run; not needed to build.
