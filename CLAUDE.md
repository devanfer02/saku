# my-money-tracker

Personal finance tracking app. Android, Kotlin, Jetpack Compose.

## Current state (17 Aug 2026)

**Nothing is built yet.** This is an unmodified Android Studio Compose template:

- Package `com.example.myapplication`, `rootProject.name = "My Application"` — both still need renaming
- One `MainActivity.kt` with the default "Hello Android" greeting
- 6 Kotlin files total, all template scaffolding (`MainActivity`, `ui/theme/{Color,Theme,Type}.kt`, two example tests)
- Not a git repository yet

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

## Open decisions

None of these have been made yet:

- App name, package name, min SDK
- Local-only vs sync; storage layer (Room assumed but not chosen)
- Whether to import from Money Manager exports or start clean
- Multi-currency, or IDR-only

## Domain notes

Categories in the existing data are Indonesian and worth keeping as defaults:
Pemberian (giving/family support), Makan & Minum, Kebutuhan Kos (boarding-house costs), Kuliah,
Perawatan Diri, Jajan & Hiburan, Baju dan Aksesoris, Kendaraan Pribadi, Kerjaan, Transportasi Umum,
Gym, Internet, Servis, Obat, Paket, Parkir.

Pemberian is the largest category by far (the largest share of spend) and needs subcategory support to be useful —
splitting recurring family obligation from one-off discretionary giving.
