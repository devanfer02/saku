## ADDED Requirements

### Requirement: Opening search
The Trans screen SHALL offer a search action that opens a search screen with a text field focused and the keyboard shown. Back SHALL return to Trans exactly as it was left.

#### Scenario: Opening search
- **WHEN** the user taps the search action on Trans
- **THEN** the search screen opens with an empty, focused query field and no results

#### Scenario: Leaving search
- **WHEN** the user goes back from search
- **THEN** Trans shows the same sub-tab, month and scroll position as before

### Requirement: Search covers all history
Search SHALL look through every transaction of every type, regardless of the period shown on Trans. Results SHALL be listed newest first, grouped under day headers like the Daily view, with the result count and the matching rows' Income and Expenses totals above the list. Transfers and adjustments SHALL be listed when they match but SHALL NOT count toward those totals.

#### Scenario: Matches in different years
- **WHEN** the query matches a note from March 2025 and one from October 2026
- **THEN** both are listed, the October 2026 row first, and the count reads 2

### Requirement: Note text matching
The query SHALL be split on spaces into words, and a transaction SHALL match only when every word matches. A word of letters SHALL match when the transaction's note contains it, ignoring letter case, accents and repeated spaces. A blank query SHALL show no results.

#### Scenario: Case is ignored
- **WHEN** a note reads "Kopi Kenangan" and the query is "kopi"
- **THEN** that transaction matches

#### Scenario: Accents are ignored
- **WHEN** a note reads "Café Batavia" and the query is "cafe"
- **THEN** that transaction matches, and the query "café" matches a note reading "cafe" too

#### Scenario: Every word must match
- **WHEN** the query is "kopi susu" and one note reads "kopi susu gula aren" and another reads "kopi hitam"
- **THEN** only the first matches

#### Scenario: Part of a word
- **WHEN** the query is "kenang" and a note reads "Kopi Kenangan"
- **THEN** that transaction matches

### Requirement: Amount matching
A word made only of digits, optionally with "." or "," thousands separators or an "Rp" prefix, SHALL match a transaction whose amount, written as plain digits, contains those digits, or whose note contains the word. Separators and "Rp" SHALL be ignored before comparing.

#### Scenario: Exact amount
- **WHEN** the query is "47.500" and a transaction's amount is 47.500
- **THEN** that transaction matches

#### Scenario: Partial amount
- **WHEN** the query is "475" and amounts of 47.500, 1.475.000 and 45.700 exist
- **THEN** the 47.500 and 1.475.000 transactions match and the 45.700 one does not

#### Scenario: Amount and text together
- **WHEN** the query is "grab 25000" and one Grab ride cost 25.000 and another 32.000
- **THEN** only the 25.000 ride matches

#### Scenario: Rupiah prefix
- **WHEN** the query is "Rp25.000"
- **THEN** it matches the same transactions as "25000"

### Requirement: Search respects the filter
Search results SHALL include only transactions that pass the active filter, and the active-filter bar SHALL be shown above the results while a filter is active. When the filter hides matching transactions, the screen SHALL say how many matches it hides and offer to clear the filter.

#### Scenario: Matches hidden by the filter
- **WHEN** the account selection is Cash and "kopi" matches 3 Cash transactions and 2 BCA transactions
- **THEN** 3 results are listed, and a line reads "2 more outside the filter" with a clear action

#### Scenario: No match at all
- **WHEN** the query matches nothing, filtered or not
- **THEN** the screen shows an empty state naming the query

### Requirement: Opening a result
Tapping a result SHALL open the entry sheet in edit mode for that transaction. After a save or delete, the results SHALL update without the query being retyped.

#### Scenario: Editing from search
- **WHEN** the user opens a result, changes its amount, and saves
- **THEN** the search screen shows the same query with the updated amount, and the row drops out if it no longer matches
