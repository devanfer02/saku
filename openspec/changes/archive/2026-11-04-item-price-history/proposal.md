## Why

The owner records groceries per item (`receipt-items`) to "gauge the price over periods" (31 Oct 2026): what a pack of INDOMIE GRG SPCL or a 2 L bottle of cooking oil cost on each trip. Search already finds every row with a note and shows a count and a total, but not how the price moved.

## What Changes

- When a search matches rows of one item name (the note, ignoring case, repeated spaces and a ` ×n` quantity suffix), the results show a price card above the rows: a line of the unit price per trip, the latest price, the lowest and highest, and the change since the first.
- Unit price = row amount ÷ n for a note ending in ` ×n`, else the row amount.
- A row's menu (long press, or the edit sheet) gets Price history, which opens search on that row's item name.
- Expense rows only. No new storage.

## Capabilities

### New Capabilities
- `item-price-history`: the price card in search, how rows of one item are matched, unit price, and the Price history entry from a row.

### Modified Capabilities
None. Search behaviour is unchanged; the card is an addition when the results are one item.

## Impact

- `ui/search/`: a price card and its assembly (`PriceHistoryAssembly.kt`); uses the existing chart drawing in `ui/shared/` if one fits, else a small line chart there.
- `ui/entry/` or the row menu: Price history entry.
- No Room change, no repository interface change beyond an existing search query.
