## Context

The entry sheet (`ui/entry/`) has a single-line Note field, a category picker and an in-sheet keypad for the amount. Notes are free text. The owner's history shows they are highly repetitive and predict the category almost perfectly (744 of 778 repeated-note rows always used one category), so the note is the cheapest place to recover the category and the amount.

Constraints: minSdk 26 ships SQLite 3.18, which has no window functions (they arrived in 3.25, API 30). The ViewModel holds only user choices; derived lists come from assembly functions. The `txn` table has about 2,000 rows today and grows by roughly 130 a month.

## Goals / Non-Goals

**Goals:**
- Suggest past notes after 2 typed characters; picking one fills note, category and last amount.
- Keep every keystroke in memory: one cached Flow per type, filtered by a pure function.
- Ranking and the fill rules live in pure functions with unit tests.

**Non-Goals:**
- Suggestions on an empty note (the owner declined chips).
- Suggesting an account. The account stays what the sheet chose.
- Transfers. They carry no category and their notes do not repeat in the data.
- Fuzzy or typo-tolerant matching. Prefix then contains is predictable and enough for short notes.
- Reintroducing subcategories in any form.

## Decisions

**One lean DAO read, folded in Kotlin.** `TxnDao.observeNoteUses(type)` selects `note, categoryId, amountIdr, occurredAtEpochSec, id` for non-blank notes of one type, newest first. A pure `foldNoteUsage(rows)` in `data/repository/NoteUsageFold.kt` groups by the folded note and returns `NoteUsage(key, note, useCount, lastUsedAt, lastAmount, categoryId)`. `TransactionRepository.observeNoteUsage(type)` is the DAO Flow mapped through the fold.
- Rejected: do it all in SQL. "Most-used category, ties to the most recent" per note needs a window function or a correlated subquery per group. The first does not exist on API 26 to 29; the second is slow and hard to read. SQL `lower()` also folds ASCII only, and grouping must match the search's `fold()` (case, accents, spaces).
- Rejected: reuse `observeAll(TransactionFilter.ALL)` and fold in the ViewModel. It maps every full row to a domain `Transaction` and would put a fold in a ViewModel body, which the architecture rules forbid.
- The projection is 5 columns over about 2,000 rows; the fold is one pass plus a per-group category count. Room re-runs it only when `txn` changes, so after a save the next sheet sees the new note.

**Grouping key is `domain.fold(note)`.** The same function search uses, so "Es Teh", "es teh" and "es  teh " are one note. The display spelling is the most recent use's trimmed text, so the user sees what they typed last time.

**Most-used category, ties to the most recent.** Counted only over uses with a category. The row order (newest first) makes "first seen" equal "most recent", so the tie-break is the category whose latest use is newest. A note used with no category at all gets `categoryId = null`.

**Ranking (pure, `suggestNotes` in `NoteSuggestionAssembly.kt`).** Query is `fold(typed)`. Under 2 characters: nothing. Prefix matches on the key come first, then contains matches. Within each group: use count descending, then last used descending, then key ascending so the order is stable. At most 5. An exact match is still shown, because picking it is how the user asks for the category and amount.

**Fill rules (pure, `applySuggestion` in the same file).** Recorded here because the owner asked for them explicitly:
1. Note is set to the suggestion's note (the most recent spelling).
2. Category is set to the suggestion's most-used category.
3. Amount is set to the most recent amount only when the form's amount is zero and has no pending operator. A non-zero amount, or "5.000 +" mid-expression, means the user already typed, and that wins.
4. Account, destination, date and type never change.
5. If the suggestion's category is archived or deleted, picking fills the note only. The amount is not filled either: a note whose category was retired is a weak signal, and its last amount is likely from before the user reorganised. The user still has the note and picks a category by hand.
- The suggestion carries the resolved active `Category` or null. `suggestNotes` resolves it against the sheet's active categories list (`EntryUiState.categories`, already loaded per kind), so archived and deleted categories both become null without an extra query.

**Type scoping in the ViewModel.** The ViewModel derives a `StateFlow<List<NoteSuggestion>>` from `_state` and `noteUsage`, where `noteUsage` is `_state.map { it.form.type }.distinctUntilChanged().flatMapLatest { observeNoteUsage(it) or emptyFlow for Transfer }`. It is a separate `suggestions` flow, not a field folded into `state`, so `state.value` stays synchronous for the key handlers and their tests.

**Open/closed is a user choice in `EntryUiState`.** `noteSuggestionsOpen` is set by `setNote` and cleared by `open`, `pickSuggestion`, `dismissSuggestions` and `setType`. That is why editing a transaction with a note shows nothing until the user types.
- Rejected: keep it in the composable's `remember`. Then opening an edit could not be told apart from typing, and the rule would not be testable.

**Dismiss on Enter, outside tap, or focus loss.** The field uses `ImeAction.Done`; Done dismisses and clears focus. The sheet body watches pointer downs on the initial pass without consuming them; a down outside the note field and its list dismisses and clears focus. Focus loss also dismisses. Picking clears focus too, so the keyboard drops and the in-sheet keypad is visible for the amount.
- Rejected: a Material `ExposedDropdownMenu` popup. A popup window takes accessibility focus and can steal IME focus, and inside a `ModalBottomSheet` it positions poorly. A plain column under the field keeps reading order natural and the keyboard up.

**Accessibility.** The list is a plain `Column` of at most 5 rows inside the sheet's scroll column, right after the note field, so TalkBack reaches it in reading order. Each row is at least 48 dp, uses `clearAndSetSemantics` with one spoken string ("note, category, amount", via a string resource and `formatIdr`), `Role.Button` and an `onClick` action. Text uses `ink` and `ink2`, which already meet 4.5:1 on `surface`; the amount uses `MoneyText` in neutral ink.

## Risks / Trade-offs

- [Five rows plus the keyboard may push the note field off screen on a small phone] → The list sits inside the existing scroll column. Check on device.
- [Outside-tap detection uses layout bounds] → Unit tests cannot cover it; listed for the on-device check.
- [A note that once had a typo groups separately] → Accepted. Prefix matching still finds it, and its use count ranks it below the real one.
- [Growing history] → At 10,000 rows the fold is still one pass in milliseconds, and it runs only when `txn` changes, never per keystroke.

## Migration Plan

None. No schema change. Rolling back is reverting the commits.

## Open Questions

None.
