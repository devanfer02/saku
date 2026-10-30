## Why

On 30 Oct 2026 the owner decided against subcategories: "I hate tagging subcategories, it feels like another thing. Just make sure I can search the spending: when I type 'arisan' it shows the summary and the individual items." Search already does that (count, totals, day-grouped rows over note text), so the subcategory field is now a cost with no user: an extra picker on every entry, an extra management level on the category screen, and a Stats section that, with no tagged rows, always reads "No subcategory 100%".

## What Changes

- **Subcategories leave the product UI.** The entry sheet loses its Subcategory field, picker and "New subcategory" action. The category manager loses expand, the subcategory list, "Add subcategory" and the subcategory menus; a category row shows its transaction count only. Transaction rows show `Account · note` only. The Stats category detail loses its "By subcategory" section and keeps the headline, share, count, average, strip and transactions. The backup and restore screens stop listing subcategory counts.
- New transactions save `subcategoryId = null`. Editing a transaction keeps the stored subcategory while its category is unchanged, and clears it when the category changes.
- **Nothing is deleted from the database.** The `subcategory` table, `TxnEntity.subcategoryId` and every stored row stay. No migration.
- Excel import still parses the `Subcategory` column. Export still writes it. Backup still writes the `Subcategories` sheet and restore still reads it, so every backup made before this change restores unchanged.
- UI code, strings, assembly functions, tests and repository methods that nothing reaches any more are deleted. DAO queries that backup, restore, import or merge use are kept.
- Root `CLAUDE.md` records the decision and the Pemberian plan (split into Keluarga, Gereja and Teman categories in the data import).

## Capabilities

### New Capabilities
- `subcategory-retirement`: what the app shows and saves now that subcategories are out of the product, and what it must keep doing with the stored values (edit, import, export, backup, restore).

### Modified Capabilities
None. `openspec/specs/` has no synced capability yet. The `money-tracker-v1`, `buku-kas` and `full-backup` requirements that mention subcategories in the UI are superseded by ADDED requirements in `subcategory-retirement`, the same approach `transaction-filter` took.

## Impact

- `ui/entry/`: `EntryFormState`, `TransactionEntryViewModel`, `EntrySheet`.
- `ui/categories/`: state, ViewModel, screen and dialogs.
- `ui/shared/`: `TransactionRow` (one parameter removed), `SubcategoryPicker` deleted.
- `ui/stats/`: drill state, assembly, ViewModel, `StatsDrill`.
- `ui/transactions/` and `ui/search/`: `TransactionRowUi.subcategoryName` and the subcategory lookup in `DisplayIndex` removed.
- `ui/importexport/`: backup and restore screens drop the subcategory count rows.
- `data/repository/`: `CategoryRepository` and `TransactionRepository` lose the methods only the removed UI called. This is a repository interface change.
- No Room entity change, no migration, no new dependency, no new permission.
- Maestro flows `record-expense`, `stats` and `manage-categories` no longer create or assert subcategories.
