## Why

`receipt-scan` reads one photo into one total. The owner's real case is a monthly grocery trip (31 Oct 2026): one long receipt that needs several photos, taken in hand at an angle, and a wish to record each item under its own category so its price can be followed month by month, with every row of the trip marked "Monthly grocery". The scan action is also hard to find: a text link above the keypad, inside a sheet built for typing one amount.

## What Changes

- **Scan button beside +** on the Trans tab (option B, agreed). It opens the camera or the photo picker, then a new Receipt screen. The Scan receipt text action leaves the entry sheet.
- **Several photos per receipt.** On the Receipt screen, Add next part takes another photo. Photos are taken top to bottom. Saku joins them: rows printed in two photos are kept once, and when two photos do not connect Saku says a part may be missing.
- **Item rows.** Saku reads each item: name, quantity, line price, and a discount printed under it (`DISKON : (4.000)`). A discount on the whole receipt (`DISKON KARTU`, a bank promotion, a voucher) is spread over the items in proportion to their price, largest remainder, so the rows add up to exactly the total paid.
- **A check against the printed total.** The screen shows the items' sum against the receipt total. A gap shows the missing amount, and the user can take the missing part or save the gap as one extra row.
- **Category per item from a dictionary.** An item name used before gets the category it was most often saved with, from note history, ignoring a ` ×n` quantity. A new item asks once.
- **One row or one row per item**, chosen on the Receipt screen. One row saves the total with one category and the store as the note, like v1. Per item saves a row per item, with the item name as the note.
- **A tag on every row**, default the tag used on the last receipt save, for example Monthly grocery; can be cleared. Needs `transaction-tags`.
- A screenshot or a one-photo receipt with no item rows goes through the same screen with One row only.
- Test data: synthetic long receipts drawn angled, curled and shadowed, split into overlapping and gapped photos, committed; real web photos as a local-only set held to the owner's 80% minimum.

## Capabilities

### New Capabilities
- `receipt-items`: the scan button, the Receipt screen, joining photos, reading items and discounts, the total check, the category dictionary, one row or per item, the tag, and the accuracy bars for items.

### Modified Capabilities
- `receipt-scan`: the entry-sheet Scan receipt action is removed. Reading a total, date and store, the chips, the unplaced lines and the offline rules stay. Applied after `receipt-scan` is archived.

## Impact

- `receipt/` (pure JVM): `ReceiptItems.kt` (item rows, quantities, item discounts, receipt discounts), `ReceiptJoin.kt` (overlap and gap), `DiscountSpread.kt`; `ReceiptReading` gains `items` and `receiptDiscounts`.
- `ui/receipt/` (new): `ReceiptScreen`, `ReceiptViewModel`, `ReceiptAssembly.kt`; a route from Trans.
- `ui/transactions/TransactionsScreen.kt`: the scan button. `ui/entry/`: scan action removed, `ReceiptScanAssembly` kept for the One row hand-off only if it stays useful (see design).
- `data/repository/`: `TransactionRepository.saveAll(rows, tagIds)` in one database transaction; the dictionary reads the existing note usage fold.
- `data/make-mock-receipts.py`: a long receipt and its angled, overlapping photo set; fixtures and tests.
- No new permission, no new dependency. Room changes come from `transaction-tags` only.
