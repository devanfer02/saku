## Context

Subcategories were built into v1 because the Money Manager export left `Subcategory` empty on all 1,200 rows (gap 1), and Pemberian, 50% of spend, could not be split. The owner has now tried the flow and rejected tagging: search over notes ("arisan") already answers the question, with count, totals and day-grouped rows. So the field goes from the product.

The data model has subcategories in four places that are not UI: the `subcategory` table and `TxnEntity.subcategoryId` (foreign key, `SET_NULL` on delete); the Excel import, which reads a `Subcategory` column; the export, which writes one; and the full backup, which writes a `Subcategories` sheet that restore reads back. Nothing has run on real hardware yet, so in practice stored subcategories exist only where a test, an old Saku export or an old backup put them.

## Goals / Non-Goals

**Goals:**
- No subcategory anywhere the owner looks: entry, category manager, rows, Stats, backup and restore screens.
- No stored data lost, and no backup or export made before this change that stops restoring.
- Dead code removed, so the next reader does not maintain a feature nobody can reach.

**Non-Goals:**
- Removing subcategories from the schema, the Excel formats or the backup format.
- Splitting Pemberian. That happens in the owner's data import, as separate Keluarga, Gereja and Teman categories.
- Searching subcategory names. Search matches notes and amounts, which is what the owner asked for.

## Decisions

**Hide, never delete. The table, the column and every row stay; there is no migration.** The UI stops reading and writing subcategories and the data layer keeps carrying them.
- Rejected: drop the `subcategory` table and `TxnEntity.subcategoryId` with a Room migration. It is the tidier schema, but it destroys stored values with no way back, which the owner's global rule forbids (`DROP TABLE`, irreversible migrations). SQLite before 3.35 cannot drop a column, so Room would rebuild the `txn` table by copy, a risky migration on the one table that matters. It would also break restore of every backup made before it, whose `Subcategories` sheet and `Subcategory` column would then have nowhere to go, or would need a special case in the restore plan to discard them. Keeping the schema costs one nullable column and one small table.

**Editing keeps the stored subcategory while the category is unchanged, and clears it when the category changes.** `EntryFormState` no longer has a `subcategoryId` field. `toTransaction(original, ...)` sets `subcategoryId = original.subcategoryId` when the saved `categoryId` equals `original.categoryId`, and null otherwise. New transactions pass nothing and save null.
- Rejected: always save null on edit. Simplest, but fixing a typo in a note would silently wipe a value the user can no longer see or restore. That is the kind of silent destructive write the project forbids.
- Rejected: always keep the stored value. Moving a transaction from Pemberian to Makan & Minum would leave it pointing at a subcategory of Pemberian, a state v1's entry spec explicitly prevented ("a subcategory belongs to exactly one category"). Export and backup would then write a pair that restore files under the wrong parent.
- Rejected: keep `subcategoryId` in the form state. It works, but it keeps a field and its `withType` and `withCategory` clearing rules alive in a form that never shows it. Deriving it from the original at save time is one line and one place.

**Excel import keeps parsing the `Subcategory` column and keeps filing non-blank values.** Money Manager exports leave it empty, so for the owner's data nothing changes. A Saku export made before this change still re-imports exactly. The code path is small and already tested; removing it would make an old export lossy for no gain.

**Export keeps the `Subcategory` column.** The layout is shared with the import's column map and with the `Saku` sheet of the backup, and a file written by this version must import into an older one and the other way round. The column carries stored names and is blank for everything recorded from now on. The export screen's intro no longer names the column, because it lists what the user recorded.

**Backup keeps the `Subcategories` sheet; restore keeps reading it.** The backup format version does not change. The backup and restore screens stop listing subcategory counts, since the owner has no word for them any more. `RestorePreview.newSubcategories` stays and still counts toward `hasAnythingNew`: a backup that would only add subcategory rows still needs its confirm button, or restore would silently do nothing.

**Category delete keeps its rule: a category with no transactions may be deleted, and the foreign key cascades its subcategory rows.** Those rows are referenced by no transaction (a transaction with a subcategory always has that subcategory's category, so the category would have a count and could not be deleted). The user confirms the delete. This is unchanged v1 behaviour.
- Rejected: refuse delete while hidden subcategory rows exist. Safe for the rows, but the user would see a Delete action that does nothing, or no Delete with no visible reason. The rows lost are names with no history.

**Merge keeps carrying subcategories across**, inside the existing transaction. It keeps stored data consistent with its parent; only the confirmation copy stops mentioning it.

**Dead code goes; DAOs used by data paths stay.** Removed: `SubcategoryPicker`, the subcategory rows, prompts, expand state and actions in the category manager, `foldSubcategories`, `SubcategorySlice`, `subcategoryStrength`, `RankedRow`'s colour override (only the split used it), `TransactionRowUi.subcategoryName`, the subcategory lookup in `DisplayIndex`, the strings, and the repository methods only those called (`lastSubcategoryFor`, `observeSubcategories`, `observeAllSubcategories` both overloads, `subcategoryById`, `createSubcategory`, `renameSubcategory`, `reorderSubcategories`, `setSubcategoryArchived`, `isSubcategoryDeletable`, `deleteSubcategory`, `transactionCountsBySubcategory`). DAO queries whose last caller was one of those went with them (`SubcategoryDao.observeActive`, `observeActiveForCategory`, `observeAllForCategory`, `updateAll`, `activeForCategory`, `moveToCategory`, `deleteUnused`; `TxnDao.countForSubcategory`, `countsBySubcategory`, `lastSubcategoryFor`). Removing a query deletes no data, and it takes the only single-row subcategory delete out of the app. Kept: `findOrCreateSubcategory` (import), `allSubcategories` (export, backup, restore), every DAO query that merge, restore, import or backup reaches, and the optional `subcategoryId` parameter of `TransactionRepository.record`.
- Rejected: removing `record`'s `subcategoryId` parameter. No production caller passes it any more, but it defaults to null, costs nothing, and the export, backup and stats tests use it to set up rows that carry a stored subcategory. Without it each of them would need a second write per row.

**`TransactionRow` loses one parameter and nothing else.** Another change is building calendar range selection on the Daily row composables at the same time, so the row's layout, other parameters and call order stay untouched to keep that merge to a one-line conflict at most.

## Risks / Trade-offs

- [A future owner wants subcategories back] → The data model and formats are intact; the UI removal is in git history and can be reverted per feature.
- [Stored subcategories become invisible but still exported] → Intended: export and backup are the lossless paths. The values are visible in the exported sheet.
- [An edit that changes category drops a hidden value] → Same rule v1 had with the field visible; recorded above. The value was already wrong for the new category.
- [Maestro flows changed without an emulator run] → They run on `master` and `workflow_dispatch` only; the first run is at merge. Flagged for an on-device check.

## Migration Plan

None. No schema change, no data change. Rolling back is reverting the commits.

## Open Questions

None.
