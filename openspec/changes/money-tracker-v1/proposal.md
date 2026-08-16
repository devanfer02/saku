# Money Tracker v1

## Why

A year and 1,200 transactions in Money Manager produced a spending record that can be read but not acted on.
`a private analysis of the export` documents five concrete failures: subcategory was empty on every row, 97% of
rows collapsed the account to "Card" so nothing reconciles against a real balance, investment transfers had no
destination so net worth is uncomputable, half the entries were batch-logged from memory, and no budget caps existed
so the app recorded history instead of steering behaviour.

This change builds the replacement: an ad-free, offline Android app covering the four functions the owner actually
uses, with the account model fixed so balances mean something and the Money Manager export importable as seed data.

## What Changes

- New Android app replacing the unmodified Compose template currently in the repo.
- **BREAKING** — package renamed from `com.example.myapplication`, `rootProject.name` renamed from "My Application".
  The template `MainActivity` greeting and its two example tests are replaced.
- Transaction recording with Income / Expense / Transfer, an optional subcategory field, and a numeric keypad
  carrying a `000` key and inline `+ −` arithmetic.
- Transaction list with two tabs: Daily (day-grouped, per-day subtotals) and Calendar (month grid where blank days
  are visible, surfacing logging gaps).
- User-managed categories and subcategories: create, rename, recolour, reorder, archive, merge. Creation is available
  inline from the entry sheet's pickers, not only from a settings screen.
- Typed accounts (Cash / Bank / Investment / Liability) with an opening balance carrying an as-of date, running
  balances derived from transactions, and a Reconcile action that writes a visible `ADJUSTMENT` transaction for any
  drift.
- Accounts screen headed Spendable · Invested · Total, with a donut chart of balance by account.
- Stats screen defaulting to the current month, with Weekly / Monthly / Annually / Period selection, Income and
  Expenses tabs, a donut, and a ranked category list.
- Excel import with a preview step: row counts, account mapping (`Card → BCA` prefilled), category mapping,
  investment-transfer destination mapping, and a new / already-here / deleted tally. Import only ever inserts.
- Excel export to a Saku-shaped single sheet that re-imports losslessly.
- Blue accent theme in both light and dark, with a colour-vision-validated 8-slot categorical chart palette.
- No `INTERNET` permission is declared, making ad-free and offline structural rather than promised.

Deferred to v2 and explicitly out of scope: budget caps, recurring/instalment transactions, multi-currency,
cloud sync and backup, receipt photos, home-screen widgets and reminders, sinking funds and goals, and the
reference app's Monthly / Total / Note tabs.

## Capabilities

### New Capabilities

- `transaction-entry`: recording, editing and deleting a transaction — the three-way type selector, amount keypad,
  date/time, category and optional subcategory selection, account selection, transfer destination, and note.
- `transaction-list`: browsing recorded transactions — the Daily day-grouped list with subtotals, the Calendar month
  grid, month navigation, and the period income/expense/total header.
- `category-management`: the category and subcategory lifecycle — create (inline and from the manage screen), rename,
  recolour, reorder, archive, merge, and the rules preventing deletion where history exists.
- `account-management`: accounts as typed entities with opening balance and as-of date, derived running balances,
  the Reconcile flow and its adjustment transactions, the Spendable/Invested/Total summary, and the balance donut.
- `period-stats`: aggregate spending and income over a selectable period — period selector, income/expense tabs,
  category donut, ranked category list, and drill-through to the underlying transactions.
- `excel-interchange`: importing and exporting `.xlsx` — the Money Manager column mapping, preview and confirmation
  step, deduplication, merge-into-existing-month behaviour, import batch tracking, and the export sheet shape.
- `app-shell-theme`: the four-tab bottom navigation, the light and dark blue theme token sets, the validated
  categorical chart palette and its assignment rules, and IDR number formatting.

### Modified Capabilities

<!-- None. openspec/specs/ is empty; this is the first change in the project. -->

## Impact

- **Replaces**: `app/src/main/java/com/example/myapplication/MainActivity.kt`, `ui/theme/{Color,Theme,Type}.kt`, and
  the two template test files.
- **Build**: `settings.gradle.kts` (`rootProject.name`), `app/build.gradle.kts` (namespace, applicationId, minSdk 26,
  targetSdk 35), `gradle/libs.versions.toml` (Room, KSP, Navigation Compose, fastexcel, kotlinx-datetime).
- **New dependencies**: Room + KSP, Navigation Compose, and `fastexcel` / `fastexcel-reader` for `.xlsx`. fastexcel
  is the one library risk in this plan — if it fails on device, the fallback is Apache POI with `poi-ooxml-lite`,
  costing roughly 8 MB of APK.
- **Manifest**: no `INTERNET` permission; the app declares no network access at all.
- **Seed data**: `data/mock-money-manager.xlsx` becomes the importer's fixture — 1,200 rows across 11 columns,
  two of them named `Account`, two of them empty on every row.
- **Not a git repository yet.** Initialising one is a prerequisite for the first task.
