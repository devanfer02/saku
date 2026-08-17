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
more. Indexes on `txn(occurredAt)`, `txn(accountId)`, `txn(categoryId)` and `txn(dedupHash)` — the last one plain,
not unique; see the review section below for why.

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

Dedup key: a hash over `occurredAt` truncated to seconds, type, `amountIdr`, `accountId`, `toAccountId`, category
name and note. Leaving the account out was provably wrong — see the review section below.

Two mapping steps run before any write. Accounts: every distinct name in the file maps to an existing or new
account, with `Card → BCA` prefilled, covering 1,150 of 1,200 rows. Investment destinations: `Transfer-Out` rows
carry no destination in the export, so they are grouped by category — `RDPU SucorInvest`, `Saham` — and each group
is asked once for a destination account. Net worth becomes computable during import rather than after it.

Every confirmed import writes a batch record and stamps its rows, so a bad import is reversible as a unit.

### Excel via fastexcel, plus a StAX shim. POI is not a fallback

**Resolved by the group 6 spike.** `fastexcel` and `fastexcel-reader` read all 1,200 rows of the real export, see
all eleven headers including `Account` at both position 2 and position 11, and round-trip a written file. Cost is
**+1,337,200 bytes**, 11.8% on an 11.3 MB release APK.

It does not work out of the box. `fastexcel-reader` parses through `javax.xml.stream`, and **Android has never
shipped StAX** — `android.jar` declares zero classes across `javax.xml.stream`, `.events` and `.util` while
providing every other `javax.xml.*` package in the chain. On a device the first touch of `ReadableWorkbook` throws
`NoClassDefFoundError`, and because the factory is held in a `static` field it fails at class-init rather than at
the call site.

`org.apache.geronimo.specs:geronimo-stax-api_1.2_spec` supplies it — 37 KB, API-only, Apache-2.0. fastexcel
instantiates aalto's `InputFactoryImpl` directly instead of going through `XMLInputFactory.newInstance()`, so no
`ServiceLoader` lookup has to resolve on Android.

**Nothing in the normal toolchain catches the missing dependency.** `assembleDebug` succeeds, D8 dexes the dangling
references silently with minification off, and both JVM unit tests and Robolectric pass because the desktop JDK
carries its own StAX. `XlsxAndroidRuntimeTest` reproduces Android's classpath with a classloader that removes
exactly those three packages and loads the libraries child-first, so dropping `libs.stax.api` fails the build. It
cannot cover ART verifier behaviour or on-device throughput — track F should still run one import on hardware early.

**The POI fallback this document previously named does not exist.** `poi-ooxml` needs the *same* StAX API and adds
71 unresolvable `java.awt` references that no small shim replaces, at +7,728,585 bytes — 5.8× the cost of fastexcel,
and still broken. The ~8 MB size estimate was right; the premise that POI was the safe alternative was not.

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

### CI builds the APK; Maestro covers e2e

GitHub Actions runs on every push to `main`, `foundation/**` and `agent/**`. The build job runs unit tests, then
assembles debug and release and uploads both as downloadable artifacts. Because the no-network check is wired into
`assemble`, CI cannot produce an APK that declares network access.

Running on `agent/**` matters for the fan-out: four concurrent tracks each get an independent green or red signal
without waiting on a merge.

E2E is Maestro rather than Espresso. Flows are YAML, they exercise the app the way it is actually used, and they
do not couple to Compose internals — which matters when four tracks are writing screens in parallel and an
Espresso matcher written against another track's tree would break constantly. The harness and a shell smoke flow
land in foundation; each track adds its own flow beside them, in its own file, so flows never conflict either.

The e2e job is gated to `main` and manual dispatch. An emulator boot per push would dominate the feedback loop
for no benefit while foundation work is still moving.

Release APKs are unsigned. Signing needs a keystore in repository secrets, which is a decision for whenever the
app first goes to a device that is not the owner's.

### Decisions forced by the foundation review

Two independent reviews of PR #1 landed before the freeze, one design-focused and one adversarial. The adversarial
pass proved eleven bugs with failing tests. These are the design-level consequences, recorded because the fixes are
not obvious from the code alone.

*The dedup hash includes the account, and is not a storage constraint.* The original key — timestamp, type, amount,
category, note — omitted the account, and a unique index enforced it over every write path. Two Rp 5.000 parkir
entries at the same picked minute therefore crashed on save, and on import two real rows differing only by which
account paid were collapsed to one and reported as a duplicate. Since the reference export has `Subcategory` and
`Description` empty on all 1,200 rows, that collision is likely rather than exotic. The account is now in the key,
the index is no longer unique, and deduplication lives only in the importer, inside one transaction. The fields are
length-prefixed rather than delimiter-joined, because imported text can contain the delimiter.

*A liability's stored balance is negative while money is owed.* Nothing previously stated the convention, and both
readings were wrong somewhere: `AccountSummary.total` subtracted an already-negative balance, so taking on debt
raised net worth. Spending against a liability is an expense, the balance query subtracts it, and the summary
negates the sum to present a positive "amount owed".

*A transfer must postdate both accounts' as-of dates.* The filter is applied per account, so a transfer between two
accounts with different cutovers is counted on one leg and not the other, inventing or destroying money in the
Total. The repository refuses to record one, and first-run setup gives every seeded account the same cutover, which
makes the case unreachable in practice. What the balance SQL does with such a row is unchanged and still correct:
anything that early is already inside the opening balance.

*Repositories take the database, not DAOs.* `mergeCategory` moves hundreds of rows, migrates subcategories and
archives the source; unwrapped, process death mid-loop left a state no retry repairs. Reconcile had the same shape
and a worse failure: it stamped "reconciled just now" before writing the adjustment, so a failed insert left the
account looking clean with no correction. Both are now single transactions, and the reads inside them are one-shot
queries rather than `Flow.first()`, which deadlocks against the write lock.

*A container exists before the fan-out.* `AppContainer` is hand-rolled — four repositories do not justify an
annotation processor. Its value is timing, not design: without it each of the four tracks would invent its own path
from ViewModel to repository and all four would collide in `MainActivity`, the one file no track owns.

*Chart shares are integers.* `CategoryTotal` carries basis points rather than a float, with the rounding remainder
given to the largest slice, so a column of whole percentages sums to exactly 100 and the no-floating-point rule
holds all the way to the screen.

*The no-permission guard allows the app's own namespace.* Asserting zero `<uses-permission>` entries turned out to be
too strict: AndroidX adds a signature permission namespaced to the applicationId for runtime receiver registration.
The rule is now "nothing outside our own namespace", which still fails on every `android.permission.*`.

### Donuts rather than pies

The reference app uses a filled pie. A donut is used in both places because the hole carries the figure that
belongs at the centre of attention — the period total on stats, net worth on accounts — and because segment gaps
read more cleanly on a ring. This is a deliberate, and the only significant, departure from the reference layout.

### Shared components stay atomic and module-extractable

The app is one Gradle module today, and splitting it is not in scope. The package layout is nonetheless drawn on
the boundaries a split would use, so the option stays open and so parallel tracks cannot quietly couple to each
other.

`ui/shared/` may import Compose, `ui/theme/` and `domain/`. It may not import `data/`, `AppContainer`, any
ViewModel or any repository, and it takes its copy as parameters rather than reading `R.string` — a component that
hard-codes its own labels cannot be reused by a second caller that needs different ones. The working test for any
file in `ui/shared/`: could it move to a `:ui-core` module tomorrow with nothing but a package rename?

The same rule applies upward. `ui/shell/` composes shared components and owns no UI primitives of its own; the
Daily/Calendar tab row is a placeholder for `SegmentedTabs`, not a second implementation of it. When two screens
need the same control, it belongs in `ui/shared/` and both call it.

### The proving slice found one contract gap: bulk subcategory lookup

`CategoryRepository` exposed subcategories only per category. The transaction list needs a name for whatever
subcategory each row points at, and asking per category is an N+1 over a month of rows. Added
`observeAllSubcategories()` — no argument, archived included, backed by an existing `subcategory` table scan.

Archived rows are in scope deliberately. Archiving a subcategory does not rewrite the transactions already
referencing it, so filtering archived rows out of the lookup blanks the label on real history rather than hiding
anything.

This is what the proving slice is for. Finding it after four tracks had started would have meant editing a frozen
contract with four branches open against it.

### Two aggregation paths, and the test that keeps them honest

The transaction screen folds its header, day subtotals and calendar cells from the single list of rows it already
loaded for the Daily tab. The stats screen aggregates the same rules in SQL, because it reports on periods it does
not list — an annual view must not load a year of rows to total them.

Two implementations of one rule drift. `TransactionListViewModelTest` runs both over the same seeded month and
asserts they agree, figure for figure. `TxnType.countsAsFlow` is the single written form of the underlying rule,
and both paths route through it.

### The shell's state retention is a real check, not a comment

Bottom-bar navigation that forgets a destination still looks like it works — the user just loses their scroll
position and their selected sub-tab. `navigateTo` carries `saveState`/`restoreState` with
`popUpTo(findStartDestination())` and `launchSingleTop`, and dropping either flag is a silent regression. Two
things fail if it is dropped: an instrumented test in `SakuShellNavigationTest`, and a step in the Maestro smoke
flow that asserts `selected: true` on the Calendar tab after a round trip through Stats and Accounts. The Maestro
step is the one that runs in CI, since CI has an emulator on the e2e job and no device on the unit job.

### The fan-out gate: what is frozen, as of 5d197b0

`data/`, `domain/`, `ui/theme/` and `ui/shared/` are frozen. Tracks C, D, E and F build against them and do not edit
them. A track that needs a change stops and raises it, because that file is four branches' common ground.

Each track owns exactly one directory and no track edits another's: C owns `ui/categories/`, D owns `ui/accounts/`,
E owns `ui/stats/`, F owns `excel/` and `ui/importexport/`. Integration — routing the More menu entries and the
nav destinations at each screen — is done centrally afterwards, in `ui/navigation/` and `ui/shell/`, which no track
owns either.

Three reviews ran before this line was drawn and found 24 findings between them, of which the load-bearing ones
were three separate paths where money could silently vanish. That is the argument for the gate: each would have
cost four branches to fix afterwards.

**What the gate did not prove.** `Donut` has no consumer. The proving slice covered the pickers, `SegmentedTabs`,
`TransactionRow` and `PeriodBar`, and the API defects that surfaced there are fixed. It never rendered a chart.
Track D is the first real consumer and is expected to find things; a donut change it needs is a legitimate reason
to break the freeze, and the finding should be recorded here rather than patched quietly.

## Risks / Trade-offs

- ~~**fastexcel may not behave on Android**~~ → **Resolved.** It works, at +1.34 MB, once the geronimo StAX API is
  on the classpath. There is no viable POI fallback; see the Excel decision above. Residual risk: the shim is
  verified by a classloader that emulates Android's classpath, not by a device, so track F imports on hardware once
  before building on it.
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
