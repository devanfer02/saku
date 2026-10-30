## 1. Rows

- [x] 1.1 Remove the `subcategory` parameter from `transactionRowLabels` and `TransactionRow`; rows read `Account · note`; update `TransactionRowLabelsTest`
- [x] 1.2 Remove `TransactionRowUi.subcategoryName` and the subcategory lookup in `DisplayIndex`; drop the subcategory stream from `TransactionListViewModel`, `SearchViewModel` and the Stats drill; update their tests

## 2. Entry sheet

- [x] 2.1 Remove `subcategoryId` from `EntryFormState` and its clearing rules; `toTransaction` keeps `original.subcategoryId` only while the category is unchanged; tests for the note-only edit, the category change and the type change
- [x] 2.2 Remove the Subcategory field, picker and create action from `EntrySheet`, and the subcategory state and actions from `TransactionEntryViewModel`; delete `SubcategoryPicker`; update the ViewModel tests

## 3. Category manager

- [x] 3.1 Remove `SubcategoryRow`, the subcategory prompts, `expandedId`, `menuForSubcategoryId` and `subcategoryFor` from the state; `buildCategoryRows` takes categories and counts only; update `CategoryManagerStateTest`
- [x] 3.2 Remove the subcategory actions from `CategoryManagerViewModel` and `CategoriesScreen`; rows show swatch, name and transaction count with no expand; dialogs and copy drop subcategories; update `CategoryManagerViewModelTest`

## 4. Stats

- [x] 4.1 Remove `foldSubcategories`, `SubcategorySlice`, `subcategoryStrength` and `DrillUiState.Category.subcategories`, and their tests
- [x] 4.2 Remove the "By subcategory" section from `StatsDrill` and the strings it used

## 5. Data and import/export screens

- [x] 5.1 Remove repository methods nothing reaches any more, and DAO queries only they used; keep what import, export, backup, restore and merge use
- [x] 5.2 Backup and restore screens stop listing subcategory counts; `hasAnythingNew` still counts new subcategories; intros drop the word
- [x] 5.3 Confirm import, export, backup and restore tests still pass with subcategory values, unchanged

## 6. Docs and flows

- [x] 6.1 Root `CLAUDE.md`: replace the "Still open" subcategory-inference bullet with the 30 Oct 2026 decision; Pemberian note now points at Keluarga, Gereja and Teman categories in the data import
- [x] 6.2 Maestro: `record-expense`, `stats` and `manage-categories` stop creating and asserting subcategories, and assert their absence where useful

## 7. Verification

- [x] 7.1 `:app:testDebugUnitTest :app:assembleDebug` green with the low-RAM flags
- [ ] 7.2 On device: edit an old transaction carrying a subcategory (from a restored backup) and confirm it survives a note edit and clears on a category change; run the three changed Maestro flows
