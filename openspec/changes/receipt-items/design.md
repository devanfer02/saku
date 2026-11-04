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

## Built: the parser half (31 Oct 2026)

Calls made while building task groups 1 and 2, rejected options included.

### Test data
- `make-mock-receipts.py` draws three long receipts: `indomaret` (20 one-row items, two item discounts, `TEH BOTOL 450ML` printed twice in a row), `superindo` (14 two-row items, two `HEMAT` savings) and `diskon` (the alignment page example: six items, `DISKON : (4.000)`, `DISKON KARTU`, 206.100). Each is photographed as an overlap set (three parts, two items shared at each seam) and a gap set (two parts, cut between items, a run of items missing). 15 JPEGs, 1.2 MB, committed under `data/receipt-long/`; the truth (`items.tsv`, `sets.tsv`) is written straight into `app/src/test/resources/receipt/long/` so there is one copy.
- Warping: a parabolic bow with foreshortened edges, a perspective tilt, up to 1.6° rotation, a shadow ramp, noise, blur, JPEG 72. The font prints a dotted zero, which ML Kit often reads as 8 or 6 at the magnified right edge (`12.408`). Kept on purpose: thermal printers print dotted and slashed zeros, and the repair below is worth having for real receipts.
- `ReceiptAccuracyTest` (androidTest, the deferred task 3.1 of `receipt-scan`) reads `/data/local/tmp/receipts/` through `ReceiptRecognizer` and writes dumps in the CORD format. It was run with `adb install -r` and `am instrument`, not `connectedDebugAndroidTest`, because the connected task uninstalls the app afterwards and so deletes its database.
- CORD item truth: `cord/items.tsv` (menu `nm` and `price` from the CORD labels, same CC BY 4.0 terms).

### Rows and joining
- Rows of a tilted photo: `receiptRows` now measures the tilt as the median slope between lines that sit side by side and are each other's nearest neighbour in height, and groups rows with the tilt taken out (clamped to 0.2). Without it a price at the right edge started its own row. Pairing every side-by-side line was tried first and failed on two-row items, where a name pairs with the price under it. CORD totals are unchanged (90% exact, 95% pick or chip).
- A tall, narrow line (taller than 2.5 times the median and taller than wide) is dropped: ML Kit sometimes reads a column of quantities as one line, `HH H`.
- Join: rows whose amounts agree are compared on their letters only, since the digits near a photo's edge are the ones misread (`27.80A`); a row without an amount is compared without amounts (at least 4 characters), because two photos may put a two-row item's price on its name row in one and its quantity row in the other. The overlap may skip one cut row at the top of the next photo, and at least 60% of the rows in the overlap must match. Each overlapped row is kept from the photo that read it better (ends in a price, quantity times unit exact).
- `ReceiptParser.readParts(parts, today)` reads several photos; `read(lines, today)` is one part. An overload named `read` was rejected: `read(emptyList(), today)` would be ambiguous.

### Items
- Zeros read as 8: when quantity times unit price and the line price differ only by digits recognition confuses (0, 6, 8, 9; 3 and 8; 5 and 6; 1 and 7), the one a till would print (ending 00, then 50) wins. A lone price with no second reading gets a final `06`, `08`, `68`, `80`, `86` or `88` put back to zeros. Endings in 9 are left alone: restaurant bills print `10.909` before tax.
- A unit price equal (or look-alike) to the line price with no quantity is quantity 1. A lone number before the price that is not a quantity stays in the name (`MIE 200 11.200`). A leading quantity is stripped (`2 Iced Tea 40,000`). A letter O beside a digit in the name is a zero (`80OML`).
- A two-row item whose price was grouped with its name (`TEMPE PAPAN 18.000` over `3 x 6.000`) takes its quantity from the second row when it checks out.
- A discount in the item zone is a row with a negative or bracketed amount, or whose words are only discount words; `J.STB PROMO 17500` stays an item.
- Tax and service rows between the items and the total are additions only when the items do not already add up to the total, so a tax-inclusive receipt that prints PPN above the total is not counted twice.
- Item rows still appear in `unplaced`, because the entry sheet offers them as lines and its tests expect them (`ui/entry` was out of this step's scope). Task 5.1, which removes that sheet action, should add `items.rowIndices` to the used rows in `ReceiptParser` so the Receipt screen does not show items twice. `ReceiptReading` gains `items`, `receiptDiscounts`, `additions`, `gapsAfterPhoto` and `itemsNet`, all with defaults, so the entry sheet compiles untouched until task 5.1 removes it.
- `itemKey` (task 3.2's pure part) lives in `receipt/ItemKey.kt`.

### Measured
- Synthetic sets: diskon 11/11 items (100%), superindo 24/25 (96%), indomaret 34/34 (100%). The one miss is `TEMPE PAPAN`, whose price at the photo's bottom edge read `18.00A`. Every overlap joined with no item twice; every gap flagged; no false gap.
- CORD test split: 66% of 230 priced items right (price exact, name at least 80% alike), the price found for 82%. Printed, not held to a bar: CORD bills print modifiers, set menus and unit prices the labels treat differently, and the owner's case is a grocery receipt.
- Totals: CORD 90% exact, 95% pick or chip, as before.

## Built: saving and the Receipt screen (31 Oct to 1 Nov 2026)

Calls made while building task groups 3 to 7, rejected options included.

### Saving
- `TransactionRepository.saveAll(rows, tagIds, words)` runs `record` per row inside one `withTransaction`, so every row gets the same validation as a typed entry and a failure rolls back the rows, their tag links and the learned words. Test: a third row with no category leaves nothing.
- The dictionary is `observeItemCategories()`: expense note uses grouped by `itemKey`, then the usual category by the note-autocomplete rule (most used, ties to the latest). A new repository read rather than a second fold over `observeNoteUsage`, because pooling needs per-use rows, which `NoteUsage` has already folded away.
- The account and tags of the last receipt save live in `ReceiptDefaultsStore` (SharedPreferences, like `ChartStyleStore`). An archived default falls back to the first active account; a deleted tag simply drops out.

### Receipt screen
- The ViewModel holds the read lines per photo and `ReceiptChoices`; everything shown comes from `assembleReceipt`. Choices for an item are keyed by the printed name's item key, not its position, so they survive Add next part, which reads the items again.
- Photos are read one at a time behind a mutex, so parts join in the order taken. A photo with no text adds no part and says so. Only lines are kept; a camera photo's file is deleted by the recognizer once read.
- Trans launches the camera or picker itself (a `ReceiptCameraViewModel` lends the file URI) and passes the first photo's URI as the route argument, so backing out of the picker never shows an empty Receipt screen. Add next part uses the same launchers, including the OpenDocument fallback.
- After Save the Receipt screen sets the receipt's day on the Trans back stack entry's `SavedStateHandle`; Trans calls `openDay`, which opens Daily on that month scrolled to the day.
- Item rows are not removable on this screen. A wrong row is fixed after saving, like any row. Fewer controls per row kept each row one target (name and category sheet).
- One row with no total read saves the items' sum; with neither, Save is disabled and the screen says to add a photo or use +.
- The missing-category sheet's pick fills every unset row and saves in the same tap ("Use for all unset"). Closing it leaves the rows to choose one by one.
- The date is the receipt's when it lies within the last year and not in the future, else today, at the scan's time of day.
- Lines Saku could not place show in One row mode and add to the note in a tap, as v1 did on the sheet. Item rows are no longer among them (`ReceiptParser` marks them used).
- The other amounts (chips) sit under the total and replace it in one tap in both modes, so a misread total can be fixed before the check is trusted.
- Accepted risk: the photo lines live in the ViewModel only. Rotation and backgrounding keep them (checked on the emulator); process death drops them and the route reads the first photo again.

### Totals confirmed by the items
- Found on the emulator: the synthetic receipts' dotted zero made totals read `332.408` and `206.108`, so the check showed "Rp 8 not found". When the items' net and the read total are look-alike readings of each other (`lookAlike`), the rounder one wins and the reading as printed stays a chip. CORD and the synthetic bars are unchanged.

### Readable item names (owner, 1 Nov 2026)
- `receipt/ItemName.kt`: `readableItemName(printed, learned)` expands whole tokens from a small built-in map of Indonesian till abbreviations (GRG to Goreng, SPCL to Special, and the rest the owner listed), then title-cases. Added from the test receipts only where certain: MKRONI, RMPT, HZLNT, SHRMP, JMB, and UHT kept as an acronym. Tokens with a digit keep their form upper case (2L, 1.8KG, 6X8GR). A trailing `x2`/`X2`/`×2` becomes ` ×2`. Unknown tokens are title-cased, never guessed. Readable names come back unchanged (tested).
- Title case restarts after a separator other than an apostrophe: `G/F` stays, `EXT.MINYAK` reads `Ext.Minyak`.
- The Receipt screen shows and saves readable names; the item sheet has a Name field with "Printed as ..." under it. Category lookup tries the readable name's key, then the printed name's, so rows saved before readable names still match. Past rows are never renamed.

### Learned words (owner, 1 Nov 2026)
- Lookup order per token: learned, built-in, title case. An edit is learned at Save, per item renamed and only when saving per item: equal word counts learn each changed word under its printed token (`BRS` to `Buris`), never a token with a digit; different counts learn the whole name under the printed name's item key. A later edit of the same token overwrites; editing back to the built-in word stores that.
- Storage: `item_word(printed PK, readable, isWholeName, updatedAtEpochSec)`, added to schema 2 itself because version 2 has not shipped (tags are in the same PR). `MIGRATION_1_2` stays create-only; 2.json re-exported; identity hash `1248a98cbd015c80c4fc3fe4ee0fec3b`. Writes are upserts by key; nothing deletes.
- Backup format 2 gains a `Words` sheet (Printed, Readable, Whole name). Read when present, so a format 2 file written before it still restores. Restore inserts missing words and never overwrites (insert-only, like tags). Database import reads `item_word` when the file has the table.
- Emulator note: a device that ran an earlier v2 build has the old identity hash and would refuse to open. `/tmp/claude-1000/saku-v2/emu-db/set-hash.sh` adds the table (create only) and switches the hash; no shipped install is affected.

### Layout and accessibility (checked against `android-accessibility`, impeccable `reference/android.md`, uiautomator dumps)
- Thumb reach: Record as, Account, Tags, Add next part and Save sit in a bar at the bottom; the item list scrolls above it. Every choice opens as a bottom sheet. On a screen 600 dp wide or more (landscape) the bar is one row: three stacked rows left the list about 70 dp in landscape on the emulator.
- The Scan button is a small FAB left of +. Material 3 says one FAB per screen; the owner chose option B, so the owner's brief wins. It uses the quiet surface colour so + stays the primary action.
- Item rows are one node: "Indomie Goreng Special ×5, Rp 15.500, Makanan" (or "no category"), role Button, click label "Change name or category"; 56 dp minimum. Bar fields read "Account Card, change". Chips read "Use Rp 24.900 as the total". Store name and section titles are headings.
- The reading line ("Reading photo 2…"), the check line and the warnings are polite live regions, so TalkBack announces a finished read and a changed check.
- Icons beside text are decorative (null descriptions); the Scan FAB says "Scan a receipt".
- Dark theme and font scale 200%: no clipping; long names wrap; the chips row scrolls sideways. Save uses the accent with `onPrimary`, as the keypad does.
- Not checked: a live TalkBack pass and a physical device.

### Real photos (task 7.1, local only)
- Set: 7 web photos (`/tmp/claude-1000/saku-ocr/long-img`), each also cut into an overlap pair (15%) and a gap pair (12% missing). Read by ML Kit on the emulator with `ReceiptAccuracyTest` through `adb install -r` and `am instrument`; measured by `LocalReceiptItemsTest` (`RECEIPT_LONG_LOCAL_SET`), which reports and never fails.
- Three photos hold two receipts side by side (indomaret_02, superindo_01, superindo_02). ML Kit reads across both, so rows mix two receipts: 2/11, 0/15 and 0/8 items as whole photos. A scan is one receipt, so these were cut into left and right halves (and their own overlap and gap pairs) and measured per receipt; the whole-photo figures are kept here only as the case Saku does not support.
- Rate per source, whole photos plus overlap sets, items right (price exact, name at least 80% alike): Indomaret 51/56 (91%), Gelael 29/44 (65%), Superindo 20/46 (43%), Giant 2/14 (14%). Overlaps joined clean 8/10; gaps flagged 10/10. Under the 80% bar on three sources.
- Tried and kept (bars unchanged): a quantity column before the name (`4 GULA PASIR 1 KG 1,300 5.200`, Gelael 25% to 65%); `-` and `:` read as a group separator (`5-280`, `1:300`); a long product code before the name stripped (Giant); weighed lines' grams and price per 100 g dropped (`830 25.5`); a name row carrying the item above's `HEMAT -x` on a curled photo (Superindo), which gives the discount to the item above and the name to the price row below.
- Why the rest fails: Giant is crumpled with ticks drawn over it and ML Kit reads the names as noise (`MNGSHH SH KH` for NONGSHIM SHRMP KM); prices are right. Gelael is a 1992 dot-matrix print where digits are misread (`15335` for 1535). Superindo's curled halves shift the price column by a row; the grouping by a single tilt cannot follow a curl. Not tried: local tilt per neighbourhood in `receiptRows`, which is the next step for Superindo.

### Real photos, second pass
Same set, same strict scorer (price exact, name at least 80% alike). `LocalReceiptItemsTest` also prints a second rate beside it, price exact and name at least 50% alike, so a misread name can be told from a wrong price; the strict rate is the one held to the bar.

| Source | Before | After (strict) | After (price right, name half alike) |
| --- | --- | --- | --- |
| Indomaret | 51/56 (91%) | 51/56 (91%) | 52/56 (92%) |
| Gelael | 29/44 (65%) | 36/44 (81%) | 36/44 (81%) |
| Superindo | 20/46 (43%) | 35/46 (76%) | 35/46 (76%) |
| Giant | 2/14 (14%) | 2/14 (14%) | 4/14 (28%) |

Overlaps joined clean 8/10 to 9/10; gaps flagged 10/10. Synthetic sets 100/96/100%, CORD totals 90% exact and 95% pick or chip, CORD items 154/230 (66%): all unchanged.

Kept, each with a unit test that fails without it:
- Layout per receipt, read from the item zone only (header and payment rows left out). `QTY NAME UNIT TOTAL` when half the named priced rows start with a plain quantity and carry a unit price; `NAME QTY UNIT TOTAL` when a third check out as quantity times unit. A free layout keeps the old rules.
- `QTY NAME UNIT TOTAL`: a quantity glued to the name (`1GULA`, `16ULA` for `1 GULA`) is split off when quantity times unit checks out; a number before the unit is part of the name unless it checks out as a quantity; the number before the price is the unit price even when misread, so it never lands in the name.
- `NAME QTY UNIT TOTAL`: a lost line price (`2 10.790`, or a stray `6` after the unit) is quantity times unit; with quantity 1, a line price shorter than the unit (`1 7.390 200`) is the unit.
- A discount row read inside its item row (`LE MINERALE 330ML 24 HEMAT -11.060 2.290 54.960`) is taken out and given to that item, when the rest still reads as a named item.
- A quantity glued to the unit price (`17.390 7.390` for `1 7.390`), a line price cut at the photo's edge (`86.49` for 86.490, put back when quantity times unit starts with the digits read), `HEMAT-3.200` split at the minus, and `HEMAI` read for `HEMAT` on a curled name row.
- Join: a row's amount falls back to the item reader's (`1:300` is no amount to `amountsIn`), so a dot-matrix overlap has an anchor; two rows with the same amount to the rupiah need their letters 75% alike instead of 85% (`SMISS SOUPS` and `SWESS SOUPS`).

Tried and dropped:
- One tilt for the whole photo, chosen better. A scan of slopes from -0.2 to 0.2 per photo showed the measured tilt already sits on the best plateau for every source but two Superindo halves (superindo_01L, superindo_02R), where the best slope gains 2 and 3 items. Three ways to find it failed: the slope whose items add up to the read total (no slope checks out, because of discounts and misread digits), the slope with the most rows whose quantity times unit checks out (no signal on superindo_02R), and the tilt read from how much taller wide line boxes are than narrow ones (right on Superindo, but 0.045 on Gelael and 0.053 on indomaret_03, whose best slope is near zero; a curl makes boxes tall too).

Why the rest fails:
- Superindo (11 misses): superindo_02R as a whole photo has its unit and line price columns shifted by different amounts, so rows pair a name with the item above's price (0/4); superindo_01L has its price column above the names, so prices come before their names and the header `DESKRIPSI` takes the first one. Both need line-level pairing (prices to names by order, or a tilt per region from ML Kit's line angle, which `OcrLine` does not carry); the overlap sets of the same halves read 4/4 and 5/5. The rest are single digits misread (`112.650` for 112.690) or lost (`3.` for 3.190).
- Gelael (8 misses): two `INST N. RASA BA` lines at the same unit price are merged into one item by design (Decision 2), which the scorer counts as two misses in the overlap set; the others are digits a dot-matrix print turns into other digits (`253 763` for 255 765, `185 182`).
- Giant (12 misses): names still read as noise; the second rate shows prices right for 4 of 14, and two prices are themselves misread (`6s200`, `BIC4606`).

## Built: app order screens and the source (owner, 4 Nov 2026)

The owner orders through GoFood, ShopeeFood, Shopee and Tokopedia and pays with GoPay and ShopeePay. Their two screenshots (GoFood, ShopeeFood) read badly through the paper-receipt rules: GoFood's payment and fee rows became items and its one item was missed (its `1` is a count; its price is the `Price` row); ShopeeFood's total was Rp 9.000 (the repeated sauce price) and its fees were not additions.

### Decisions
- **Source apart from store** (`receipt/ReceiptSource.kt`). An enum with the brand's own spelling, so `receipt/` carries no UI copy: the screen string is `Read from %1$s`. Order of rules: order apps on any row (they beat the wallet that paid), banking apps by their app names on any row (`DEBIT BCA` on a paper slip is not m-BCA), then the top quarter's wallet or bank header or shop name, then `Saldo DANA`-style rows anywhere. A paper receipt prints its wallet at the foot, so a wallet only counts at the top. `Sumber Dana` is a bank's label, not DANA. Bare `BRI` and `Mandiri` headers are not mapped: an EDC slip is not BRImo or Livin'.
- **Shopee against ShopeeFood.** Both head the order `Rincian Pesanan`; the marketplace's own labels (`Koin Shopee`, `Subtotal untuk Produk`, `Subtotal Pengiriman`) are tried first, and `Rincian Pesanan` alone means ShopeeFood. GoFood in Indonesian is `Ringkasan pembayaran` with a `Harga` row, or `Biaya penanganan`.
- **GrabFood dropped** (owner, 4 Nov 2026: not used). The reader is not tied to an app, so a GrabFood screen with `1x` rows and a `Total` would mostly read, but it is not detected or tested.
- **A reader of its own** (`receipt/AppOrder.kt`), used only when the source is an order app. The paper-receipt rules stay as they were, so the CORD and synthetic bars cannot move: CORD totals 90% exact and 95% pick or chip, items 154/230, all unchanged.
- **Item shapes.** `n x Name  price` keeps the quantity with the line price (`ReceiptItem` holds the line price; the unit is the price over n). Shopee's `x2  unit` and Tokopedia's `2 x  unit` under the name rows are unit prices, so the line is n times unit; Tokopedia's `Total Harga` under each product equals that and is skipped. A `2 Mie Ayam` count with no `x` is an item only on a priced row, so a Shopee name such as `2 Pcs Kaos` waits for its price row.
- **Names over two rows.** ShopeeFood wraps a name to the next row, which joins the item above. Shopee and Tokopedia print the name above the price, so up to two name rows are kept; when two are kept and the first sits right under the item heading, the first is the shop (Tokopedia), and becomes the store. Shopee's shop row has a `Kunjungi Toko` button and is taken from that. Options and notes (`Flavour :`, `Variasi:`, cutlery) never join a name; they stay in the unplaced lines, where a user can add one to the note.
- **Priced elsewhere.** GoFood lists `Name  n` and prices only the whole order (`Price`). One such item takes that price less any priced items. Several are left out of the items rather than given a made-up split; with no items the screen offers One row only, and the names wait in the unplaced lines. A single merged item was rejected: it would save a row that is no item's real price.
- **Summary.** Discount words first (`Voucher Diskon Pengiriman` is a discount), then shipping (`Subtotal Pengiriman` is a fee, not the subtotal), then subtotal, then other fees. `setelah diskon` on a GoFood fee is not a discount: the fee is already net. GoFood's fee group (`Biaya layanan dan lainnya 7.000` over `Biaya jasa aplikasi 5.000` and `Biaya parkir 2.000`) counts once, through its parts. Cashback counts as a discount only when printed with a minus: the owner asked for it as a discount, but Tokopedia credits cashback later and never takes it off Total Tagihan (research TP-4), so a bare `Cashback Rp2.000` row is passed over.
- **Total.** `Total Tagihan`, then `Total Bayar` or `Total Dibayar`, then `Total pembayaran`, then `Total Belanja` (Tokopedia prints Belanja and Tagihan; Tagihan is the bill), then plain `Total`, then ShopeeFood's `Paid`, then the amount beside or above `Sudah termasuk pajak`. Rows after the total (`Low delivery fee offer ends in 30 min`) are never fees. The last amount on a row counts: GoFood strikes the pre-discount total through on the same row (`84.000 69.000`), ShopeeFood the old delivery fee.
- **Store on an order.** The top-row fallback is skipped: an app's top rows are its headings (`Purchase item(s)`, `Rincian Pesananmu`). A known chain, `Alamat Restoran` and the next row, or the shop row from the item list name it; otherwise the store is empty and the One row note falls back to the source (`GoFood`).
- **Research layouts (4 Nov 2026).** A research pass collected 36 layouts from help pages and articles (`food-formats.md`, kept in the session scratchpad, not committed). What it changed:
  - Amounts: `- Rp9.000` with a space, `Rp0` and `-Rp500` read; a number glued to a code (`INV/…/1234567890`, an 18-digit order id, `F-2570000000`) is never an amount.
  - A list folded behind `Lihat Lebih Banyak` (ShopeeFood): when the items come to less than the subtotal, the voucher and fees are left out, so the check shows one gap for the hidden items and no item is cut below zero by a discount bigger than it.
  - Shopee's checkout: `Rp89.900  x1` is a unit price and count; the summary is read from `Rincian Pembayaran` when the screen has it, because the vouchers, coin toggles (`Tukarkan 10181 Koin Shopee`, a coin count) and instalment tiles (`Rp33.308 x 3 bln`) above it are not charges. Badges (`Bebas Pengembalian`, `Mall`) and the unticked `Proteksi Kerusakan` add-on are not items. `Subtotal Ukuran Khusus` is shipping; `Subtotal Barang` is the 2025 name for the product subtotal.
  - ShopeeFood in English (`Shipping Fee`, `Service Fee`, `Tax included, where applicable.`) and its checkout, whose item price sits on a row of its own under the item; `Order Total (1 item):` ends the items and is no total. A modifier list (`Normal, Normal Ice`) is an option. A wrapped name joins only an item that has its price on its row, so modifiers under an unpriced `1x Pizza` stay out.
  - GoFood: `Pembelian` heads the items in GoPay's history, `Bayar Pakai GoPay …` is a payment row, `Platform fee` and `Restaurant takeaway charges` are fees, `Total XP Earned` is no total.
  - Tokopedia's invoice: the `INFO PRODUK` table row (`Name  1  Rp35.000  Rp35.000`) and `Penjual :` as the store.
  - Not done: refund screens and failed payments (`Gagal`, `Rincian Pengembalian`) are not recognized as non-spending; Shopee's cart (`Keranjang Saya`) and order-list cards are not read as orders; `Rp3RB` shorthand is not read.
- **Screen.** `Read from GoFood` is a caption under the total, inside the scrolling list, so at 200% font it wraps there and the Save bar, in the bottom bar, never moves. Checked in a `fontScale = 2f` preview (`ReceiptOrderLargeFontPreview`); not on a device (no emulator in this pass).
- **Fixtures.** Built from the owner's screenshots' row layout and 8 research layouts (GoFood checkout, order detail and GoPay history; ShopeeFood folded, English and checkout; Shopee order detail and checkout; Tokopedia order detail and invoice), with every restaurant, shop and item name invented and no address or order number copied (`OrderScreens.kt`). 24 order tests and 12 source tests.

## Risks / Trade-offs

- [A gap that is not flagged, because two different rows happen to look alike] → an overlap needs a row with an equal amount; the total check catches what the join misses.
- [Item names cut or misread, so the dictionary misses] → the user picks once; a misread name becomes its own dictionary entry. Accepted for v2.
- [Spread discounts change an item's apparent price] → accepted by the owner: on a month with a card discount, price history shows the discounted price.
- [Removing the entry-sheet action] → if the owner misses it, it can come back as a second entry point without touching the Receipt screen.

## Open Questions

None. Answered 31 Oct 2026:
- Real photos: the web collection is restarted (photos of a hand holding a long receipt), local only.
- Merge order: v1 is not merged alone. `receipt-scan`, `transaction-tags`, `receipt-items` and `item-price-history` ship together in one PR from `feat/receipt-scan-v2`, so the entry-sheet scan action never reaches `master`. The REMOVED block applies when `receipt-scan` is archived in the same PR, before this change.
