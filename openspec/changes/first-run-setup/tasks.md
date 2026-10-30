## 1. Data

- [x] 1.1 Add `TxnDao.hasAny()` (`SELECT EXISTS(SELECT 1 FROM txn)`) and `TransactionRepository.hasAny()`
- [x] 1.2 Add `data/FirstRunStore.kt` (SharedPreferences `saku_first_run`: `isComplete()`, `markComplete()`) and wire `firstRun` into `AppContainer`; tests for a fresh store, persistence across instances, and `hasAny` on empty, adjustment-only and normal ledgers

## 2. Assembly

- [x] 2.1 Add `ui/firstrun/FirstRunAssembly.kt`: `shouldOfferFirstRun`, `AccountDraft`, `accountDraftsFrom`, `openingFor`, `amountDigitsFrom`, `validateAccountDrafts`, `planAccountWrites`, `FIRST_RUN_CATEGORY_SUGGESTIONS`, `planCategoryWrites`, `startOfToday`
- [x] 2.2 Test the assembly as plain functions: which accounts are listed, validation (blank, duplicate, archived clash), liability sign, empty amount as zero, what gets written for unchanged, renamed, retyped and added accounts, category de-duplication

## 3. ViewModels

- [x] 3.1 Add `FirstRunGateViewModel` (`CHECKING`, `OFFER`, `CLOSED`; `skip`, `complete`, `onImportLeft`) and `FirstRunSetupViewModel` (drafts, step, ticks, custom names, `next`, `back`, `done`)
- [x] 3.2 Robolectric tests on the in-memory database: Done writes opening balances as of the start of today and creates the chosen categories; Skip writes nothing and marks setup complete; setup is not offered when transactions exist; the flag persists; leaving an import with rows completes first run, without rows does not; a retried Done creates no duplicate
- [x] 3.3 Test that history imported after setup keeps the stated balance, and that an older transfer is refused with nothing written

## 4. UI

- [x] 4.1 Add `strings_first_run.xml`
- [x] 4.2 Add `WelcomeScreen`, `SakuImportChooser` and `FirstRunSetupScreen` (accounts step, categories step), each with a stateless overload and `@SakuPreview` previews in light and dark

## 5. Navigation

- [x] 5.1 Add `FirstRunRoute` and `FirstRunNavHost`, mounting the existing import, restore and database-import screens
- [x] 5.2 `SakuApp` shows the first-run flow before the shell while the gate offers it

## 6. Thumb zone

- [x] 6.1 Add `ThumbZonePane` and `PickCard` to `ui/shared/PickCard.kt`, copy as parameters, with light and dark previews
- [x] 6.2 Import (Money Manager and Excel), Restore, Import database: Idle, Reading and Refused become the centred pick card; existing test tags stay on the pick buttons
- [x] 6.3 Restore and Import database previews move Cancel and Restore or Import to a bottom action bar shared with the import preview
- [x] 6.4 Back up and Export: "Choose where to save" becomes the centred card, with the privacy notes inside it
- [x] 6.5 Welcome and Import from Saku: choices sit in the lower middle; welcome rows have one height
- [x] 6.6 Categories: "New category" moves from the header to a bottom-end FAB
- [x] 6.7 Record the rule and decisions in design.md

## 7. Verify

- [x] 7.1 `openspec validate first-run-setup --strict`
- [x] 7.2 No em dash in touched files; no colour literal outside `ui/theme/`
- [ ] 7.3 Unit tests and debug build (main session; this branch did not run Gradle)
