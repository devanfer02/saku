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

## Risks / Trade-offs

- [Misread item names split one item into two] → the user sees two items in search; renaming a note fixes it. Accepted.

## Open Questions

None.
