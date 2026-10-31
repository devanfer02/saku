Ships in one PR with `receipt-scan`, `transaction-tags` and `item-price-history` (owner, 31 Oct 2026). `transaction-tags` lands on the branch first; archive `receipt-scan` before this change, because the REMOVED block edits its spec.

## 1. Test data

- [x] 1.1 `make-mock-receipts.py`: 3 long receipts with invented items (one-row items, two-row items, a card discount), and photo sets (3 overlapping parts; 2 parts with a gap) warped with perspective, curl, shadow and JPEG noise
- [x] 1.2 Read every part on the emulator with ML Kit; save the OCR lines and an `items.tsv` truth file under `app/src/test/resources/receipt/long/`
- [x] 1.3 Local-only real set: web photos of a hand holding a long grocery receipt (owner's choice); never commit (7 photos with overlap and gap cuts, `/tmp/claude-1000/saku-ocr/`)

## 2. Parser (pure JVM, `receipt/`)

- [x] 2.1 `ReceiptItems.kt`: item zone, one-row and two-row items, quantity check, item discounts, receipt discounts, tax and service additions; tests from the spec scenarios
- [x] 2.2 `DiscountSpread.kt`: proportional, largest remainder, `Long` only; the alignment-page example as a test
- [x] 2.3 `ReceiptJoin.kt`: overlap by normalized similar rows with an amount anchor, gap markers; tests for overlap, gap, and the same item twice in one photo
- [x] 2.4 `ReceiptReading` gains `items` and `receiptDiscounts`; `ReceiptParser.read` accepts several photos' lines
- [x] 2.5 `ReceiptItemsAccuracyTest`: synthetic fixtures at 80% items, no duplicate, every gap flagged; CORD item rate printed; existing total bars unchanged

## 3. Saving

- [x] 3.1 `TransactionRepository.saveAll(rows, tagIds)` in one database transaction; test that a failure leaves nothing
- [x] 3.2 Dictionary lookup from note usage by normalized name; tests (the key, `receipt/ItemKey.kt`, is built and tested)

## 4. Receipt screen (`ui/receipt/`)

- [x] 4.1 `ReceiptAssembly.kt`: reading to screen state (items, check, gap, defaults), per-item rows and one-row row; tests
- [x] 4.2 `ReceiptViewModel`: photos list, Add next part, item edits, mode, tag, account, save; tests
- [x] 4.3 `ReceiptScreen`: stateless overload and preview; reading state; missing-category prompt
- [x] 4.4 Scan button beside + on Trans with Take a photo / Choose from gallery; route to the Receipt screen; Trans opens on the receipt's day after save

## 4b. Readable item names and learned words (owner, 1 Nov 2026)

- [x] 4b.1 `receipt/ItemName.kt`: `readableItemName`, built-in abbreviations, title case, sizes, ` ×n`; `learnedWords`; tests incl. idempotence
- [x] 4b.2 Receipt screen shows readable names, Name field in the item sheet; dictionary tries the readable key, then the printed key; tests
- [x] 4b.3 `item_word` table in schema 2 (create only), 2.json, identity hash, migration tests; `saveAll` stores the words in the same transaction
- [x] 4b.4 Backup `Words` sheet, insert-only restore, database import reads the table; round-trip tests
- [x] 4b.5 E2E: readable names on Trans rows; an edited word applied on the next receipt

## 5. Remove the sheet action

- [x] 5.1 Remove Scan receipt from the entry sheet; move chips to the Receipt screen's total; delete what nothing uses

## 6. End-to-end on the emulator (synthetic receipts only, run by adb, screenshot per step)

Photos are pushed into the emulator's gallery from `data/`; the owner's own files are never opened or recorded.

- [x] 6.1 Scan button: shows beside + on Trans, in thumb reach; Take a photo and Choose from gallery both open; backing out leaves Trans unchanged
- [x] 6.2 Three overlapping parts: Add next part twice; no item twice; check shows a match; save per item; row count, amounts and the total on Trans equal the truth file
- [x] 6.3 Gap: two parts with the middle missing; the missing-part message and the gap amount show; Add next part with the middle photo clears both; second run saves with the gap-row switch on and gets one extra row of the gap amount
- [x] 6.4 Item discount and card discount receipt: rows equal the spread example (14.129 … 40.928, total 206.100)
- [x] 6.5 Two-row items receipt (Superindo style): quantities and prices right
- [x] 6.6 Dictionary: first scan, pick categories for new items and save; second scan of another receipt with the same items shows them from history with no prompt
- [x] 6.7 Missing category prompt: Save with unset items asks; "use for all unset" fills them
- [x] 6.8 One row: saves one expense with the total, store note and chosen category; chips on the total replace it in one tap
- [x] 6.9 Tag: first scan has no tag; set Monthly grocery; next scan defaults to it; clearing it saves untagged rows
- [x] 6.10 GoPay and m-BCA mock screenshots: One row only, total, merchant and date right
- [x] 6.11 Angled, curled, shadowed mock photo of a single short receipt still reads the total
- [x] 6.12 Rotate the emulator and send the app to background on the Receipt screen mid-scan; photos, edits and mode survive
- [x] 6.13 After save, Trans opens on the receipt's day; the entry sheet has no Scan receipt action
- [x] 6.14 Filter by Monthly grocery after the scans: Stats shows only those rows, split by category
- [x] 6.15 No file left under the app cache `receipts` folder after each scan; `checkDebugNoNetworkPermission` passes

## 7. Done

- [ ] 7.1 Run the local real set; at least 80% items right per source; record the rates here
  - 1 Nov 2026: Indomaret 91%, Gelael 65%, Superindo 43%, Giant 14% (whole photos and overlap sets); overlaps clean 8/10, gaps flagged 10/10. Under the bar on three sources; what was tried is in design.md, "Real photos". Left open for the owner.
- [x] 7.2 `lintDebug`, `testDebugUnitTest`, `assembleDebug` green; README, CLAUDE.md and the `receipt-scan` design updated
