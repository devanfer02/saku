## 1. Data

- [x] 1.1 Add `NoteUsage` to `domain/`, `NoteUseRow` and `TxnDao.observeNoteUses(type)`
- [x] 1.2 Add the pure `foldNoteUsage` in `data/repository/NoteUsageFold.kt` with unit tests (grouping by fold, most recent spelling, count, last amount, most-used category with tie to most recent, null category)
- [x] 1.3 Add `TransactionRepository.observeNoteUsage(type)` and its Room implementation; a Robolectric test that it scopes by type and skips blank notes

## 2. Assembly

- [x] 2.1 Add `NoteSuggestion`, `suggestNotes` and `applySuggestion` in `ui/entry/NoteSuggestionAssembly.kt` with unit tests for every ranking and fill rule in design.md

## 3. ViewModel

- [x] 3.1 Add `noteSuggestionsOpen` to `EntryUiState`, the `suggestions` flow scoped by type, `pickSuggestion` and `dismissSuggestions`; ViewModel tests for type scoping, Transfer, edit-open, pick and dismiss

## 4. Sheet

- [x] 4.1 Add the stateless `NoteSuggestions` list with a preview and strings; one spoken description per row
- [x] 4.2 Wire it under the note field: Done dismisses, outside tap and focus loss dismiss, pick clears focus

## 5. Verification

- [x] 5.1 `:app:testDebugUnitTest :app:assembleDebug` green with the low-RAM flags
- [ ] 5.2 On device: type "be" on Expense, pick a suggestion, check the fill; Enter and outside tap close the list; TalkBack reads each row; five rows with the keyboard up on a small screen (not done: device check)
