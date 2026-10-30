# Saku

Saku is an offline, ad-free Android money tracker for rupiah (IDR only). It replaces the Money
Manager app and imports its Excel exports. Kotlin, Jetpack Compose, Room.

How to work here (commits, branches, the build command) lives in `.claude/CLAUDE.md`.

## At a glance

`com.dvnnfrr.saku`, minSdk 26, compileSdk 35, Room + KSP, Navigation Compose, IDR only, offline.
**No INTERNET permission, and a Gradle task fails the build if one reaches the merged manifest.**
Four bottom-bar destinations, each mounting a real screen.

```
com.dvnnfrr.saku
├── domain/         Money, DateRange, TxnType, Account… pure Kotlin, no Android
├── data/           Room entities, DAOs, repositories, FirstRunSeed, AppearanceStore
├── excel/          import parsing and export, pure JVM, no Android and no UI copy
└── ui/
    ├── theme/      SakuTheme: colours, spacing, typography; the only place colour literals live
    ├── shared/     MoneyText, Donut, RankedRow, SegmentedTabs, EmptyState…
    ├── navigation/ SakuDestination, MoreRoute, SakuNavHost
    ├── shell/      the Scaffold and bottom bar; owns no feature
    └── <feature>/  transactions, entry, stats, accounts, categories, importexport
```

Excel import uses fastexcel, which needs a StAX shim because Android ships no `javax.xml.stream`
(see `excel/XlsxReader.kt`). Only a classloader test covers the shim, so check import on a device
or emulator after touching it.

## Versions

Skills and docs may describe newer APIs. The versions here win; check `gradle/libs.versions.toml`
before using an API, plugin or library.

| What | Version |
| --- | --- |
| Gradle / AGP | 8.9 / 8.7.3 |
| Kotlin / KSP | 2.0.21 / 2.0.21-1.0.28 (no kapt) |
| JDK | 17 to 22 to run Gradle; the app compiles to Java 11 bytecode |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 |
| Compose BOM | 2024.10.01 (Material 3) |
| Navigation Compose | 2.8.4, string routes; no kotlinx-serialization plugin, so no `@Serializable` routes |
| Lifecycle / Activity Compose | 2.8.7 / 1.9.3 |
| Room | 2.6.1 with KSP; not the Room 2.7+ multiplatform APIs |
| kotlinx-coroutines / kotlinx-datetime | 1.9.0 / 0.6.1 |
| Tests | JUnit 4.13.2 (not JUnit 5), Turbine 1.2.0, Robolectric 4.14.1 |

Not used, so do not add without asking: Hilt, Koin, Retrofit, OkHttp, Coil, Compose Multiplatform,
Kotlin 2.1+ only features.

## Architecture rules

Non-negotiable. A change that breaks one of these is wrong even if it compiles and passes. They
outrank any skill.

**MVVM, strictly.** A `ViewModel` holds only what the user has chosen: the selected month, the open
tab, the pending dialog. Everything else is derived. Composables receive state and emit events; they
never touch a repository, a DAO or a `Clock`. Repositories expose `Flow`s and suspend functions and
know nothing about Compose. Nothing in `domain/` imports Android.

Every feature gets a stateless overload taking a plain state object, and a stateful one that builds
its own ViewModel. `StatsScreen` and `AccountsScreen` are the worked examples. The stateless overload
is what previews and tests use.

**Adapter at every boundary.** Foreign shapes stop at the edge; they never leak inward. Excel rows
become `PendingRow` in `excel/`, entities become domain types in `data/`, domain types become UI
state in a feature's `*Assembly.kt`. Those adapters are pure functions in their own file, tested
without a database or a ViewModel; see `TransactionListAssembly.kt`, `StatsAssembly.kt`,
`AccountsAssembly.kt`. (Compose has no `RecyclerView.Adapter`; this is the pattern, not the class.)
If a fold is happening inside a composable or a ViewModel body, it belongs in an assembly file.

**KISS.** Prefer a pure function to a class, a `when` to a strategy interface, and a data class to a
builder. No dependency-injection framework: `AppContainer` is a hand-written container and that is
deliberate. No abstraction whose only implementation is the one that exists. No interface added for a
second implementation nobody has asked for.

**DRY, but on behaviour, not on shape.** Two screens rendering an amount share `MoneyText`; two
screens that happen to have a title do not share a `TitledScreen`. Before writing a component, search
`ui/shared/`; bypassing `MoneyText` counts as a defect. Money formatting, percentage rounding
(`wholePercents`), colour-slot mapping and date labelling each have exactly one implementation.
Duplicating one is a bug, not a style preference.

**Money.** `Money` wraps a `Long` of rupiah. No `Float`, no `Double`, no `BigDecimal` in any balance,
total or percentage. Percentages go through `wholePercents`, which uses largest-remainder so they sum
to exactly 100.

**Colour.** Only `ui/theme/` may contain a colour literal. `NoHardCodedColorsTest` scans `ui/` and
fails the build otherwise.

**Destructive writes.** Archive with a flag; never delete. Merging moves rows with a scoped `WHERE`
inside one transaction. No `deleteAll`, no unscoped `UPDATE`, no `TRUNCATE`, no `DROP`. If a change
seems to need one, stop and ask.

## Skills: which to load

Load the skills for the kind of request, in the order given.

| Request | Skills |
| --- | --- |
| Design, redesign, critique or polish a screen | `impeccable` (Android platform), then `better-colors` for colour and contrast, `better-writing` for UI copy |
| A large change or a new feature | `superpowers:brainstorming` if installed, then `openspec-propose` (or `/opsx:propose`), then `openspec-apply-change` |
| ViewModel, repository or Room work | `android-architecture`, `android-viewmodel`, `android-data-layer` |
| Coroutines and Flow | `android-coroutines`, `kotlin-concurrency-expert` |
| Compose UI, navigation, performance | `compose-ui`, `compose-navigation`, `compose-performance-audit` |
| Tests | `android-testing` |
| Accessibility | `android-accessibility` |
| Security review | `android-security-scan` |
| Slow or memory-heavy builds | `gradle-build-performance` (Saku's 1536m heap rule wins) |

Every skill listed ships in `.claude/skills/` except `superpowers`, which is a user-level plugin.
Each third-party skill carries a "Saku install note" under its frontmatter where its advice
conflicts with Saku; the note wins over the skill body. Sources and licences are in
`.claude/skills/SOURCES.md`.

## Test data

`data/mock-money-manager.xlsx` is a generated mock Money Manager export, built by
`tools/mockdata/make_mock_export.py` and used by tests and Maestro. It keeps the real export's
quirks, which the importer must handle:

- Two columns are both literally named `Account` (positions 2 and 11); position 11 duplicates the IDR amount
- `Subcategory` and `Description` are entirely empty
- Transaction types are `Expense`, `Income`, `Transfer-Out`
- Timestamps carry microsecond precision and reflect *entry* time, not spend time

## Decisions already made

Settled. Reopen only with a reason, not by drift:

- **Saku**, `com.dvnnfrr.saku`, minSdk 26. Room for storage.
- **Local-only.** No sync, no account, no network, no ads. The build enforces the last three.
- **IDR only.** `Money` is a `Long` of rupiah; there is no currency field to add later cheaply.
- **Import from Money Manager exports**, merging into existing months rather than replacing them.
- **Generic first run.** First run seeds two accounts, `Cash` and `Card`, and no categories or
  subcategories. Categories come from the entry sheet's "New category" or from an import; a user's
  history arrives by import.
- Seeded accounts open at the epoch, because every row worth importing predates any install.
- **No subcategories in the product.** Tagging them was a chore; search answers the same question
  with a count, totals and day-grouped rows. The UI neither shows nor asks for them. The
  `subcategory` table, `TxnEntity.subcategoryId` and every stored row stay, and import, export,
  backup and restore still carry them, so old backups restore unchanged. See
  `openspec/changes/hide-subcategories/design.md`.

## Still open

- First-run setup: opening balances are set by hand on the accounts screen today.
- Budget caps: nothing yet limits or warns on spending per category.

## Domain notes

Money Manager exports from Indonesian users carry Indonesian category names, and an import recreates
them as they are. None are seeded. Typical names: Makan & Minum, Kebutuhan Kos (boarding-house
costs), Kuliah, Perawatan Diri, Jajan & Hiburan, Baju dan Aksesoris, Kendaraan Pribadi, Kerjaan,
Transportasi Umum, Gym, Internet, Servis, Obat, Paket, Parkir, Pemberian (giving and family support).
