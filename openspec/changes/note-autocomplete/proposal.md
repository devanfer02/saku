## Why

The owner retypes the same notes, and the same category and amount with them, every week. In the 1,746 recorded expenses, 204 notes repeat and cover 778 rows; for 744 of those 778 the same note always went with the same category ("donasi gereja" 27 times, "bensin motor" 22, "uang arisan" 18, then "susu uht", "laundry", "es teh"). The owner asked: "as I type a note, matching past notes appear; picking one fills the category and the last amount." This also helps gap 4 in the analysis (entries logged late and from memory): a faster entry is an entry made on the spot.

## What Changes

- The entry sheet shows up to 5 past notes under the Note field once the user has typed 2 characters. Notes that start with the typed text come first, then notes that contain it. Matching ignores case, accents and repeated spaces.
- Each suggestion shows the note, the category it was used with most often (colour dot and name) and the amount from its most recent use.
- Suggestions come only from transactions of the sheet's current type: expense notes on Expense, income notes on Income. Transfer shows none.
- Picking a suggestion fills the note as it was last typed, the most-used category (ties go to the most recent) and the most recent amount. An amount the user already typed is kept. The account never changes. If the suggestion's category is archived or gone, only the note is filled.
- No suggestions before typing (the owner declined chips on an empty note). Enter, tapping outside the note area, or picking one closes the list; typing again reopens it.
- TalkBack reaches each suggestion and speaks its note, category and amount.
- `TransactionRepository` gains `observeNoteUsage(type)`: one Flow of distinct notes with use count, last used time, last amount and most-used category. **Repository interface change.**

## Capabilities

### New Capabilities
- `note-autocomplete`: suggesting past notes in the entry sheet, ranking them, and what picking one fills.

### Modified Capabilities
None. `openspec/specs/` has no synced capability yet.

## Impact

- `domain/`: new `NoteUsage` value type.
- `data/local/TxnDao`: one new read query and a small projection row. No entity change, no migration.
- `data/repository/`: `TransactionRepository.observeNoteUsage`, its Room implementation, and a pure fold (`NoteUsageFold.kt`) from rows to `NoteUsage`.
- `ui/entry/`: new `NoteSuggestionAssembly.kt` (ranking and fill), `NoteSuggestions.kt` (stateless list), ViewModel and sheet wiring, strings.
- No new dependency, no new permission. Maestro flows are not changed.
