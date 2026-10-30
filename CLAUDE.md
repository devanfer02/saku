# my-money-tracker

Personal finance tracking app. Android, Kotlin, Jetpack Compose.

## Current state (17 Aug 2026)

Saku. `com.dvnnfrr.saku`, minSdk 26, compileSdk 35, Room + KSP, Navigation Compose, IDR-only,
offline. **No INTERNET permission, and a Gradle task fails the build if one reaches the merged
manifest.** All four destinations mount real screens; 437 unit tests.

```
com.dvnnfrr.saku
├── domain/         Money, DateRange, TxnType, Account… pure Kotlin, no Android
├── data/           Room entities, DAOs, repositories, FirstRunSeed, AppearanceStore
├── excel/          import parsing and export, pure JVM, no Android and no UI copy
└── ui/
    ├── theme/      SakuTheme: colours, spacing, typography — the only place colour literals live
    ├── shared/     MoneyText, Donut, RankedRow, SegmentedTabs, EmptyState…
    ├── navigation/ SakuDestination, MoreRoute, SakuNavHost
    ├── shell/      the Scaffold and bottom bar; owns no feature
    └── <feature>/  transactions, entry, stats, accounts, categories, importexport
```

Nothing has yet run on real hardware. Excel import in particular is unproven on-device: fastexcel
needs a StAX shim because Android ships no `javax.xml.stream`, and only a classloader test covers it.

## Architecture rules

Non-negotiable. A change that breaks one of these is wrong even if it compiles and passes.

**MVVM, strictly.** A `ViewModel` holds only what the user has chosen — the selected month, the open
tab, the pending dialog. Everything else is derived. Composables receive state and emit events; they
never touch a repository, a DAO or a `Clock`. Repositories expose `Flow`s and suspend functions and
know nothing about Compose. Nothing in `domain/` imports Android.

Every feature gets a stateless overload taking a plain state object, and a stateful one that builds
its own ViewModel. `StatsScreen` and `AccountsScreen` are the worked examples. The stateless overload
is what previews and tests use.

**Adapter at every boundary.** Foreign shapes stop at the edge; they never leak inward. Excel rows
become `PendingRow` in `excel/`, entities become domain types in `data/`, domain types become UI
state in a feature's `*Assembly.kt`. Those adapters are pure functions in their own file, tested
without a database or a ViewModel — see `TransactionListAssembly.kt`, `StatsAssembly.kt`,
`AccountsAssembly.kt`. (Compose has no `RecyclerView.Adapter`; this is the pattern, not the class.)
If a fold is happening inside a composable or a ViewModel body, it belongs in an assembly file.

**KISS.** Prefer a pure function to a class, a `when` to a strategy interface, and a data class to a
builder. No dependency-injection framework — `AppContainer` is a hand-written container and that is
deliberate. No abstraction whose only implementation is the one that exists. No interface added for a
second implementation nobody has asked for.

**DRY, but on behaviour, not on shape.** Two screens rendering an amount share `MoneyText`; two
screens that happen to have a title do not share a `TitledScreen`. Before writing a component, search
`ui/shared/` — the proving slice bypassed `MoneyText` five times and that counted as a defect. Money
formatting, percentage rounding (`wholePercents`), colour-slot mapping and date labelling each have
exactly one implementation. Duplicating one is a bug, not a style preference.

**Money.** `Money` wraps a `Long` of rupiah. No `Float`, no `Double`, no `BigDecimal` in any balance,
total or percentage. Percentages go through `wholePercents`, which uses largest-remainder so they sum
to exactly 100.

**Colour.** Only `ui/theme/` may contain a colour literal. `NoHardCodedColorsTest` scans `ui/` and
fails the build otherwise.

**Destructive writes.** Archive with a flag; never delete. Merging moves rows with a scoped `WHERE`
inside one transaction. No `deleteAll`, no unscoped `UPDATE`, no `TRUNCATE` — see the global rules.

## Why this project exists

Replacing the Money Manager app. a year of real usage (Jul 2025 – Aug 2026, 1,200 transactions) exposed
concrete gaps in how it tracks money — those gaps are the reason for building a replacement, and they should
drive the feature set.

**Read `a private analysis of the export` first.** It holds the full analysis of the exported data:
dataset shape, spending baselines, category breakdowns, and — most relevant to building this app — the
"Tracking gaps found" section at the end.

The five gaps, in short:

1. Subcategory was never filled in (0 of 1,200 rows) — the UI made it optional and skippable
2. The account field collapsed to "Card" on 97% of rows; no balance tracking, nothing reconciles
3. Investment transfers have no destination account, so net worth is uncomputable
4. Half of all entries were batch-logged from memory days later; one 20-day gap. Small cash spend is under-reported
5. No budget caps, so the app recorded history instead of steering behavior

## Reference data

`data/mock-money-manager.xlsx` — the real export, copied in as seed/import-test data.
1,200 rows, IDR, Indonesian category names. Useful for building and testing an importer, and for
having realistic data on screen during development.

Export quirks to handle on import:
- Two columns are both literally named `Account` (positions 2 and 11); position 11 duplicates the IDR amount
- `Subcategory` and `Description` are entirely empty
- Transaction types are `Expense`, `Income`, `Transfer-Out`
- Timestamps carry microsecond precision and reflect *entry* time, not spend time

## Decisions already made

Settled — reopen only with a reason, not by drift:

- **Saku**, `com.dvnnfrr.saku`, minSdk 26. Room for storage.
- **Local-only.** No sync, no account, no network, no ads. The build enforces the last three.
- **IDR only.** `Money` is a `Long` of rupiah; there is no currency field to add later cheaply.
- **Import from Money Manager exports**, merging into existing months rather than replacing them.
- **Generic first run** (30 Oct 2026). First run seeds two accounts, `Cash` and `Card`, and no
  categories or subcategories, so the app is not tailored to its first owner. Categories come from
  the entry sheet's "New category" or from an import; the owner's own data arrives by import.
- Seeded accounts open at the epoch, because every row worth importing predates any install.
- **No subcategories in the product** (30 Oct 2026). The owner found tagging them a chore and
  answers "how much went to Arisan" with search, which shows a count, totals and day-grouped rows.
  The UI neither shows nor asks for them. The `subcategory` table, `TxnEntity.subcategoryId` and
  every stored row stay, and import, export, backup and restore still carry them, so old backups
  restore unchanged. See `openspec/changes/hide-subcategories/design.md`.

## Still open

- First-run setup (group 13): real opening balances are set by hand on the accounts screen today.
- Budget caps — gap 5 in the analysis below, and the one gap nothing yet addresses.
- The owner's own data import (decided 30 Oct 2026, features first): a prepared database built from
  the Money Manager export. It no longer infers subcategories from notes: the owner decided on
  30 Oct 2026 to drop subcategories in favour of search. Pemberian is split into categories
  instead (see Domain notes).

## Domain notes

Categories in the existing data are Indonesian. They are not seeded (since 30 Oct 2026 first run
seeds only Cash and Card, so other users get no owner-specific setup); an import of the owner's
export recreates them:
Pemberian (giving/family support), Makan & Minum, Kebutuhan Kos (boarding-house costs), Kuliah,
Perawatan Diri, Jajan & Hiburan, Baju dan Aksesoris, Kendaraan Pribadi, Kerjaan, Transportasi Umum,
Gym, Internet, Servis, Obat, Paket, Parkir.

Pemberian is the largest category by far (the largest share of spend). The owner will split it into three
categories, Keluarga, Gereja and Teman, in the data import, which separates recurring family
obligation from other giving without subcategories.
