## Context

`receipt-scan` (branch `feat/receipt-scan`, not merged yet) reads one photo into `ReceiptReading(total, otherAmounts, date, store, unplaced)` with ML Kit on the device and fills the entry sheet. Its parser rebuilds printed rows from line boxes (`receiptRows`), reads amounts (`amountsIn`) and picks a total; exact total is 90% on CORD test, 87% on unseen CORD validation, 95% on 20 web paper photos and 100% on 25 wallet screenshots.

The owner's decisions, 31 Oct 2026:
- Scan starts from a button beside + on the Trans tab (option B of the alignment page).
- A long receipt is photographed top to bottom in several takes ("double takes"); Saku joins them. Photos may overlap, or a part may be missing.
- The user chooses one row for the whole receipt or one row per item. Per item, each row has its own category, recognized from a dictionary built from past choices, and every row is also marked "Monthly grocery". A tag does that (`transaction-tags`); tags are optional.
- Purpose of per item: follow an item's price over periods (`item-price-history`).
- Per-item discounts apply to their item. A whole-receipt discount is spread proportionally ("yea that looks good").
- Real photos are angled and in hand, not the flat mocks.
- Minimum 80% on every test source.

## Goals / Non-Goals

**Goals:**
- A 40-item receipt in 3 photos becomes 40 correct rows in under a minute of user time, with the rows adding up to what was paid.
- When something is missing or misread, the user sees it before saving, with the amount it is off by.
- Categories need choosing once per new item, never again.

**Non-Goals:**
- Stitching photos into one image. Saku joins read rows, not pixels.
- An in-app camera, live guide or auto-capture (CameraX needs the CAMERA permission).
- Linking the same item named differently at two stores (`INDOMIE GRG SPCL` vs an Alfamart name). Price history matches by name; linking is later.
- A product-code database.
- Storing the photo, or a receipt record. Only the transactions are stored.

## Decisions

### 1. Entry point and flow
```
Trans tab  [⎙] [+]
             │
      camera / photo picker
             │
      Receipt screen ──Add next part──▶ camera / picker ─┐
      store · date · total check          ◀──────────────┘
      items (name, qty, price, discount, category)
      Record as: One row | One row per item
      Tag · Account · Save
             │
      rows saved, back to Trans at the receipt's date
```
- The scan button is a small FAB to the left of +, same row, thumb reach (`thumb-reach` rule). Tap shows the same two choices v1 had: Take a photo, Choose from gallery.
- My call: the entry sheet loses its Scan receipt action. One scanning place is easier to find and to explain, and One row on the Receipt screen covers the quick single total. v1's chips (other amounts) move to the Receipt screen's total field.
- The Receipt screen is a full screen, not a sheet, because a 40-item list needs the height.

### 2. Joining photos (`ReceiptJoin.kt`)
- Each photo is read on its own into printed rows (existing `receiptRows`). Photos are in the order taken, top to bottom.
- To join part B under part A, compare the last up to 8 rows of A with the first up to 8 rows of B, normalized (upper case, letters and digits only, look-alike digits mapped as `fixDigits` does). The longest run of matching rows where B's run starts at B's first rows is the overlap; B's copy is dropped. Two rows match when their normalized text is at least 85% similar (edit distance) and their amounts, if both have one, are equal.
- No match: rows are appended and a gap marker sits between them. The gap is reported on the screen as "Part may be missing between photo 2 and 3".
- A single matching row counts as overlap only if it carries an amount, so a repeated `DISKON` line does not join two photos.
- Duplicate items legitimately printed twice in a row (`SUSU UHT 1L` bought as two lines) are kept when they occur inside one photo; only the overlap window between photos is deduplicated.
- My call: after joining, item lines with the same name and the same unit price and no item discount are merged into one item with the quantities summed (`SUSU UHT 1L ×2`). Two identical rows with the same scan time would share a dedup hash, and `RoomImportRepository.commit` keeps one row per hash, so a backup round trip would drop one. Merging also gives price history one point per trip.

### 3. Reading items (`ReceiptItems.kt`)
- The item zone runs from the first row with an amount after the header to the first row matching a subtotal, total or payment label (existing label sets, plus `HARGA JUAL`, `TOTAL ITEM`).
- An item row has a name and a price. Forms seen on Indonesian receipts:
  - `NAME QTY UNIT TOTAL` on one row (Indomaret, Alfamart): `INDOMIE GRG SPCL 5 3.100 15.500`.
  - Name on one row, `QTY x UNIT TOTAL` on the next (Superindo, Hypermart).
  - `NAME TOTAL` with quantity 1.
- Quantity is kept only when `qty × unit = total` checks out; otherwise quantity 1 and the line total.
- An item discount is a row in the zone whose label is `DISKON`, `DISC`, `POTONGAN`, `HEMAT`, `PROMO` or whose amount is negative or in brackets; it attaches to the item above.
- Receipt discounts are rows between the item zone and the total with the same labels or `DISKON KARTU`, `VOUCHER`, a bank name with `PROMO`. Each is one `receiptDiscount`.
- Rows in the zone that are none of these (`PPN`, `DPP`, member lines) go to unplaced as today.

### 4. Discount spread (`DiscountSpread.kt`)
- The receipt discount total is shared in proportion to each item's price after its own discount. Shares are floored, then the leftover rupiah go one each to the items with the largest remainders (ties: earlier item). Pure `Long` arithmetic; `Money` stays a `Long`.
- Same method as `wholePercents`, but over rupiah, not percent. It gets its own function in `receipt/` because the inputs and the unit differ; the CLAUDE.md rule "one implementation of percentage rounding" is about percents and is not broken.
- The alignment page example is the test: Rp 20.000 over 15.500, 38.900, 72.500, 32.400, 21.900, 44.900 gives 14.129, 35.459, 66.087, 29.534, 19.963, 40.928.

### 5. The total check
- `itemsNet = sum(items after their discounts) − receiptDiscounts`. Shown against the picked total: "Items Rp 161.200 · Receipt Rp 206.100 · Rp 44.900 not found" (the alignment page example with DETERJEN 1.8KG in the missing part).
- Equal: a check mark. Different: the gap amount, Add next part, and a switch "Save the gap as one row" (category chosen by the user, note "<store> other items"). My call: the switch is off by default, so a misread never silently becomes a row.
- Save is never blocked. The user decides.
- My call: the receipt discount is spread over the found items, plus the gap row when its switch is on. With the switch off, the found items carry the whole discount so the rows add up to what was paid; their price-history points are then slightly low.
- Tax-inclusive receipts (Indonesian retail prints prices with PPN included, `DPP` and `PPN` lines are information only) check out without tax. A restaurant bill with service and tax added on top is not a grocery receipt; it would show a gap equal to the tax. My call: tax and service rows above the total are spread like a negative discount (added in proportion), so per-item rows still add up. Covered by a CORD fixture test.

### 6. Category dictionary
- The dictionary is note history. `foldNoteUsage` already gives each past note its usual category (the category picked most often). Lookup uses the item key from `item-price-history` (trim, collapse spaces, upper case, drop a trailing ` ×n`), so `SUSU UHT 1L` and `SUSU UHT 1L ×2` are one entry. Usage of all notes with the same key is pooled before picking the usual category. The key function lives in `receipt/` and `item-price-history` reuses it; `foldNoteUsage` and note autocomplete are unchanged.
- Found: that category, marked "from your history". Not found: empty, the row says Choose, and Save asks for the missing ones before saving (a single "Use for all unset" chooser is offered).
- Saved per-item rows have the item name as their note, so the next receipt finds them. No new table.
- My call: a default category for unknown items is not guessed from the store.

### 7. Saving
- Per item: one expense row per item, amount = item price − its discount − its spread share; note = item name, with ` ×n` when the quantity is above 1 (`SUSU UHT 1L ×2`), which `item-price-history` uses for unit price; date and time = the receipt's date with the scan time (as v1); account = chosen on the screen (default: the last account used); tag = chosen.
- One row: amount = total; note = store; category = chosen (default: the category holding the largest share of items); same tag and account rules.
- All rows in one database transaction through `TransactionRepository.saveAll`. Either all are saved or none.
- Tag default: the tag used on the last receipt save, remembered in SharedPreferences like other small settings; empty on the first scan.
- After saving, Trans opens on the receipt's month, scrolled to its day.

### 8. Accuracy and test data
- Synthetic set, committed: `make-mock-receipts.py` draws 3 long receipts with invented items (Indomaret-style one-row items, Superindo-style two-row items, a card discount), then makes photo sets: 3 overlapping parts, 2 parts with a gap, each warped with perspective, a curl, a shadow gradient and JPEG noise. Read by ML Kit on the emulator once, OCR lines saved as fixtures like CORD, so JVM tests run without the emulator.
- Bars (owner: 80% minimum on every source):
  - Items read with the right name and price: at least 80% of items per source.
  - Joins: every overlap joined with no duplicate row and every gap flagged, on the synthetic set.
  - Totals: the existing 80% exact bar stays on CORD and the local sets.
- Local-only set: real web photos of long grocery receipts, in hand and angled, with any multi-photo sets found. Held to the same 80% item bar before release; never committed. The collection run was stopped on 31 Oct 2026; it is restarted or replaced by the owner's own photos before task group 6.
- CORD receipts are restaurant bills with item rows, so the item reader is also measured on the CORD test fixtures (`menu` labels give names and prices).

## Risks / Trade-offs

- [A gap that is not flagged, because two different rows happen to look alike] → an overlap needs a row with an equal amount; the total check catches what the join misses.
- [Item names cut or misread, so the dictionary misses] → the user picks once; a misread name becomes its own dictionary entry. Accepted for v2.
- [Spread discounts change an item's apparent price] → accepted by the owner: on a month with a card discount, price history shows the discounted price.
- [Removing the entry-sheet action] → if the owner misses it, it can come back as a second entry point without touching the Receipt screen.

## Open Questions

None. Answered 31 Oct 2026:
- Real photos: the web collection is restarted (photos of a hand holding a long receipt), local only.
- Merge order: v1 is not merged alone. `receipt-scan`, `transaction-tags`, `receipt-items` and `item-price-history` ship together in one PR from `feat/receipt-scan-v2`, so the entry-sheet scan action never reaches `master`. The REMOVED block applies when `receipt-scan` is archived in the same PR, before this change.
