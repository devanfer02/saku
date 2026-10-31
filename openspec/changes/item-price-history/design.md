## Context

`receipt-items` saves a grocery item as an expense row whose note is the item name, with ` ×n` added when n units were bought (`SUSU UHT 1L ×2`). `transaction-search` matches note text across all history and shows matching rows grouped by day with a count and totals.

## Goals / Non-Goals

**Goals:**
- See what one unit of an item cost on each trip, and how it moved, in one search.

**Non-Goals:**
- Linking different names for the same item (store A vs store B). Later, if the owner asks.
- Price alerts, inflation numbers, per-store comparison.
- Items saved before `receipt-items` with free-typed notes are included only when their note matches the item name.

## Decisions

### 1. Where it lives: search, not a new screen
- Search already answers "every row with this note". The card appears when every expense result has the same item key, so typing `minyak goreng 2l` shows it, and typing `minyak` (two different items) does not.
- My call: no separate Items screen in this change.

### 2. Item key
- Note → trim, collapse spaces, upper case, drop a trailing ` ×n` or ` x n` (n 1 to 999). Rows with equal keys are one item.

### 3. Unit price and points
- Unit price = amount ÷ n, rounded half up to whole rupiah (n from the suffix, else 1). One point per row, at its date. Two rows of the same item on one day show as one point at their average.
- The card: the line, the latest unit price, the lowest and highest with their dates, and the change from the first point to the latest as rupiah and percent (`wholePercents` is for shares that sum to 100, so this percent is one plain rounded division, kept in the assembly).
- Spread card discounts lower a point (accepted trade-off in `receipt-items`).

### 4. Chart
- Reuse an existing chart from `ui/shared/` if one draws a line over time; otherwise add a small `PriceLine` there, colours from the theme. Under 2 points, the card shows the single price and no line.

### 5. Decisions made while building (1 Nov 2026, owner asleep)
- **Placement.** The card sits between the fixed summary (and the hidden-by-filter line) and the Daily/Monthly/Yearly tabs, so it stays put whichever tab is open. Rejected: the first item of each tab's list (it scrolls away and would be built three times); above the summary (the count and totals are the answer to the search, the card is a detail of it).
- **Collapsible, open by default.** The header line (Unit price, the latest price, a chevron) is the toggle and stays when folded, so a folded card still answers "what does it cost now". The choice is kept with `rememberSaveable` on the screen, not in the ViewModel: it is view state like the open sheet, and a new query keeps it.
- **One item key.** `receipt/ItemKey.kt` is the only key. Its trailing-quantity regex now captures n, and `itemQuantity` beside it reads the same suffix, so key and quantity cannot disagree. n outside 1 to 999 counts as 1.
- **Which rows.** Only expense results count toward the card; income, transfers and adjustments in the same results are ignored, not a reason to hide it. A blank note is never an item.
- **Lowest and highest on a tie** name the most recent day at that price ("lowest in October" says when it was last that cheap).
- **Change percent** is `round(change / first × 100)`, half away from zero, in `Long` arithmetic; null when the first price is zero (then only rupiah show).
- **Chart.** No line chart existed in `ui/shared/` (Stats draws bars). New `PriceLine`: x by date so a long gap reads as a gap; 2 dp line in `accent`, 4 dp markers with a 2 dp `surface` ring, faint `line` guides at the lowest and highest; fixed 88 dp height so it keeps its shape at 200% font while the words around it grow. No axis text: the first and last dates sit under it and the figures below name the extremes. The dataviz validator passes the light accent; the dark accent `#6C9BF0` fails only the categorical lightness band (L 0.69), which is a rule for keeping several series equal in weight; for one series the check that matters, contrast against the dark surface of at least 3:1, passes.
- **Text colour.** The change reads in ink, not income green or expense red: a price going up is not income, and the dataviz rule keeps text in text tokens.
- **TalkBack.** The header carries one sentence for the card, for example "Unit price Rp 38.900, lowest Rp 35.000 in July, highest Rp 41.000 in September, up 11% since July", plus Expanded or Collapsed and a "Show/Hide the price chart" click label. The figures below only repeat it, so they are hidden from TalkBack. Highest is spoken too, which the brief's example left out, because once the figures are hidden it would otherwise be unreachable. A month in the same month as the latest point is spoken as a date instead. A change under half a percent is spoken in rupiah, not "up 0%".
- **Price history action.** In the edit sheet, on a saved expense with a non-blank note, a text button just above the keypad (thumb reach), not in the header beside Delete. It opens on the note's item key. From Trans it pushes search with the key as the query (`transactions/search?q=`, an optional argument read through `SavedStateHandle`); from search it closes the sheet and replaces the query in place, so back still returns to Trans. Opened on an item, search does not raise the keyboard, which would cover the card. Unsaved edits in the sheet are dropped, as on any other dismiss.
- **TalkBack wording found on the emulator.** "highest Rp 18.200 in 25 Oct 2026" read wrong; a date in the latest point's month is now spoken "on 25 Oct 2026", a month "in July".
- **E2E rows by hand.** The Receipt screen is not on this branch, so tasks 3.x used rows typed in the entry sheet rather than scans; `adb input text` cannot type `×`, so the quantity row used ` x2`, which the same key reads.

## Risks / Trade-offs

- [Misread item names split one item into two] → the user sees two items in search; renaming a note fixes it. Accepted.

## Open Questions

None.
