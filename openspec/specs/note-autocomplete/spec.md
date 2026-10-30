# note-autocomplete Specification

## Purpose
Suggesting past notes in the entry sheet, each with its usual category and last amount, and filling the sheet from a pick.
## Requirements
### Requirement: Suggest past notes while typing
The entry sheet SHALL show past notes under the Note field once the typed note, with case, accents and repeated spaces ignored, is at least 2 characters long. It SHALL show at most 5. Notes that start with the typed text SHALL come before notes that only contain it. Within each group, notes used more often SHALL come first, then the more recently used. No suggestion SHALL show while the note has fewer than 2 characters.

#### Scenario: Typing two characters
- **WHEN** the user types "be" on a new expense and past expenses have the notes "bensin motor" (22 uses) and "beli galon" (3 uses)
- **THEN** the sheet shows "bensin motor" first and "beli galon" second

#### Scenario: Prefix matches rank above contains matches
- **WHEN** the user types "teh" and past notes include "teh botol" and "es teh"
- **THEN** "teh botol" is shown before "es teh"

#### Scenario: Case and accents are ignored
- **WHEN** the user types "UANG" and a past note is "Uang Arisan"
- **THEN** "Uang Arisan" is suggested

#### Scenario: One character shows nothing
- **WHEN** the user has typed only "b"
- **THEN** no suggestion is shown

#### Scenario: More than five matches
- **WHEN** eight past notes match the typed text
- **THEN** only the five best-ranked are shown

### Requirement: A suggestion shows its usual category and last amount
Each suggestion SHALL show the note as it was most recently typed, the category it was used with most often (colour dot and name), and the amount from its most recent use. Ties in category use SHALL go to the category used most recently. A suggestion whose category is archived or no longer exists SHALL show no category.

#### Scenario: Usual category
- **WHEN** "laundry" was recorded 9 times under Kebutuhan Kos and once under Servis
- **THEN** its suggestion shows Kebutuhan Kos

#### Scenario: Last amount
- **WHEN** "bensin motor" was last recorded at Rp 30.000 and earlier at Rp 25.000
- **THEN** its suggestion shows Rp 30.000

#### Scenario: Archived category
- **WHEN** the most-used category of a note has been archived
- **THEN** its suggestion shows the note and amount and no category

### Requirement: Suggestions follow the sheet's type
Suggestions SHALL come only from transactions of the sheet's current type. Expense SHALL suggest expense notes, Income SHALL suggest income notes, and Transfer SHALL suggest nothing.

#### Scenario: Income does not suggest expense notes
- **WHEN** the sheet is on Income and the user types "ua", and "uang arisan" exists only on expenses
- **THEN** "uang arisan" is not suggested

#### Scenario: Transfer
- **WHEN** the sheet is on Transfer and the user types a note
- **THEN** no suggestion is shown

### Requirement: Picking a suggestion fills note, category and amount
Picking a suggestion SHALL set the note to the suggestion's note, SHALL set the category to the suggestion's category, and SHALL set the amount to its last amount only when the amount is still zero with no pending operator. It SHALL NOT change the account, the destination, the date or the type. When the suggestion has no active category, picking it SHALL fill the note only. Picking SHALL close the list.

#### Scenario: Picking on an empty sheet
- **WHEN** the amount is empty and the user picks "bensin motor" (Kendaraan Pribadi, last Rp 30.000)
- **THEN** the note is "bensin motor", the category is Kendaraan Pribadi, the amount is Rp 30.000, and the account is unchanged

#### Scenario: An amount already typed is kept
- **WHEN** the user has typed Rp 50.000 and then picks "bensin motor"
- **THEN** the amount stays Rp 50.000 and the note and category are filled

#### Scenario: Archived category fills the note only
- **WHEN** the user picks a suggestion whose category is archived
- **THEN** only the note changes; the category and amount stay as they were

### Requirement: The list closes and reopens predictably
The list SHALL close on Enter, on a tap outside the note field and its list, and after a pick. Typing in the note field SHALL reopen it. Opening the sheet, including to edit a transaction that has a note, SHALL NOT show suggestions until the user types. Suggestions SHALL NOT take focus from the note field, so the keyboard stays open while the list shows.

#### Scenario: Enter closes the list
- **WHEN** suggestions are showing and the user presses Enter
- **THEN** the list closes and the typed note is kept

#### Scenario: Editing a transaction with a note
- **WHEN** the user opens an existing transaction whose note is "bensin motor"
- **THEN** no suggestion shows until the user changes the note

### Requirement: Suggestions are accessible
Each suggestion SHALL be reachable by TalkBack in reading order after the note field, and SHALL be announced as one item speaking its note, its category (when it has one) and its amount, with a button role. Each suggestion SHALL be at least 48 dp tall. Suggestion text SHALL meet 4.5:1 contrast against the sheet.

#### Scenario: TalkBack reads a suggestion
- **WHEN** TalkBack focus moves onto the "bensin motor" suggestion
- **THEN** it speaks "bensin motor, Kendaraan Pribadi, Rp 30.000" and announces it as a button

### Requirement: One cached read per type
The repository SHALL expose distinct notes for a type, each with its use count, last used time, last amount and most-used category, as one Flow. Notes SHALL be grouped with case, accents and repeated spaces ignored, and blank notes SHALL be left out. Filtering while typing SHALL work on that cached list in memory and SHALL NOT query the database per keystroke.

#### Scenario: Grouping spellings
- **WHEN** expenses carry the notes "Es Teh", "es teh" and "es  teh "
- **THEN** the repository returns one note with a use count of 3, spelled as the most recent of the three

#### Scenario: A new transaction updates the list
- **WHEN** the user saves an expense with a new note and opens a new sheet
- **THEN** that note is suggested when its first two characters are typed

