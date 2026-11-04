## Context

Saku records a transaction as one amount, a date, a type, an account, a category and a note. There are no line items, and this change adds none. A receipt therefore only has to yield an amount, maybe a date, and maybe a store name. Everything else on it is text the user may want in the note.

Saku is offline by construction: no `INTERNET` permission, and `checkDebugNoNetworkPermission` fails the build if one reaches the merged manifest. Any OCR engine must run on the device and must not bring a network permission with it.

The spike on 31 Oct 2026 (`spike/ocr-mlkit`, throwaway) answered the three unknowns:

| Question | Result |
| --- | --- |
| Does ML Kit add a network permission? | Yes: `INTERNET` and `ACCESS_NETWORK_STATE`, both from `com.google.android.datatransport:transport-backend-cct:2.3.3` (its logging backend) |
| Does OCR still work with both removed? | Yes. With `tools:node="remove"` on both, the gate passed and all 100 CORD images were read |
| How fast? | 794 ms median, 2,081 ms max per image on the Pixel 8 API 35 emulator (x86_64, 2 GB) |
| How accurate is a first total picker? | 68 of 95 labelled CORD v2 test receipts (71%); the true total was read somewhere on 76 (80%) |

A second set of 20 public web photos (5 Indomaret, 5 Alfamart, 3 Solaria, 1 Waroeng Steak & Shake, 2 KFC, 4 DANA or GoPay QRIS screens; local only) first scored 12 of 20. Two fixes, reading `Total Bayar Rp631.000` as a total rather than a payment row and looking past a bare `Total` row (`Total`, `Cash`, `322,000` on KFC), took it to 16 of 20 (80%) with CORD unchanged at 71%. All 4 remaining misses are receipts where the total was never read (cut, shadowed or blurred), so on this set the picker is at the recognition ceiling. The same 20 photos guided those fixes, so 80% is optimistic for unseen receipts. The slowest web photo took 3.1 s at full size, which is why the recognizer downscales.

The gap between 71% and 80% is the picker; the gap above 80% is recognition (blur, angle, faded thermal print, and labels such as `GRANDTOTAL 24.0` cut off mid-number).

## Goals / Non-Goals

**Goals:**
- One tap from the entry sheet to a filled amount on a clear receipt, with the user always confirming before Save.
- A wrong total costs one tap to fix (the other-amount chips), never retyping.
- Lines Saku cannot place are shown, not dropped, so the user can note them by hand (owner's request).
- Accuracy is a measured number with a fixture suite that fails on a regression.
- No network permission, no stored photo, no Room change.

**Non-Goals:**
- Line items, per-item categories or splitting one receipt into several transactions.
- A product-code dictionary for Indomaret or Alfamart. No public list exists, and the short names (`INDOMIE GRG SPCL`) vary by store; such lines go to the unplaced list.
- Guessing the category or the account from the store. Possible later from note history (`note-autocomplete` already maps notes to categories), not in this change.
- An in-app camera preview (CameraX), live detection, or cropping.
- Scanning while editing an existing transaction.

## Decisions

### 1. ML Kit bundled Latin text recognition
- Chosen: `com.google.mlkit:text-recognition` (bundled model inside the APK, no Play Services download, works with no network).
- Alternatives: Tesseract4Android (no network code, but lower accuracy on phone photos and a slower, larger engine); the unbundled Play Services model (downloads on first use, so needs network: ruled out); a cloud OCR or vision LLM (ruled out by the offline rule).
- Network permissions: remove both merged permissions in `AndroidManifest.xml` with `tools:node="remove"`, and leave the gate exactly as it is, so a new network permission from any later dependency still fails. Without the permission the logging backend cannot send anything; the spike shows recognition does not depend on it.
- Size: ML Kit adds `libmlkit_google_ocr_pipeline.so` at 6.8 to 11.6 MB per ABI. Planned: release keeps `arm64-v8a` and `armeabi-v7a`. Built: arm64-v8a only with compressed native libraries, 21.0 MB (see Built); debug also keeps `x86_64` for the emulator.

### 2. Camera through the system intent, gallery through the photo picker
- Chosen: `ActivityResultContracts.TakePicture` into a `FileProvider` URI under `cache/receipts/`, and `PickVisualMedia(ImageOnly)`. Neither needs a permission, so the allowlist does not change.
- Alternative: CameraX inside Saku. Better framing control, but it needs `android.permission.CAMERA`, a new allowlist entry and a preview screen. Not worth it for v1.
- The cached camera file is deleted after reading, and on the next app start if a read was interrupted.

### 3. The parser is pure JVM, in `receipt/`, behind an `OcrLine` boundary
- `data/receipt/ReceiptRecognizer.kt` is the only file that imports ML Kit. It decodes the `Uri` (downscaled so the long side is at most 2,000 px), runs recognition, and returns `List<OcrLine(text, left, top, right, bottom)>`.
- `receipt/` takes `List<OcrLine>` and returns `ReceiptReading(total: Money?, otherAmounts: List<Money>, date: LocalDate?, store: String?, unplaced: List<String>)`. No Android, no ML Kit, so CI tests it on the JVM.
- `ui/entry/ReceiptScanAssembly.kt` turns a `ReceiptReading` into a form update. This follows the adapter-at-every-boundary rule: foreign shapes stop at `data/receipt/`.

### 4. How the total is picked (from the spike, to be ported and extended)
- One reading for every image, with no receipt-or-screenshot switch (owner, 31 Oct 2026: "it should autodetect too"). Paper and screenshot labels sit in one list, and the spike already scored paper and DANA screens with the same code. If a label means different things on the two kinds, the parser may tell them apart by itself (a status-bar time row at the top, or `Transaksi berhasil`), but the user never chooses.
1. Rebuild printed rows: sort lines by vertical centre and join those whose centres are within 0.6 of a line height. ML Kit returns blocks out of reading order, so a label and its amount often arrive apart.
2. Read amounts: `25.000`, `25,000`, `Rp25.000`, `25000`, with a trailing `,00` or `.00` treated as sen and dropped. Values under Rp 100 are ignored (quantities, item counts). `O` between digits reads as `0`.
3. Match total labels allowing OCR slips: `T0TAL`, `TUTAI`, `TOTA1`, plus `GRAND TOTAL`, `TOTAL BELANJA`, `TOTAL BAYAR`, `TOTAL PEMBAYARAN`, `TOTAL TRANSAKSI`, `TAGIHAN`, `JUMLAH`, and the screenshot labels `NOMINAL`, `NOMINAL TRANSAKSI`, `JUMLAH PEMBAYARAN`. Exclude `SUBTOTAL`, `TOTAL DISC`, `TOTAL ITEM`, `TOTAL HEMAT`, `BIAYA ADMIN`, `SALDO`, and payment rows (`TUNAI`, `CASH`, `KEMBALI`, `CHANGE`, `DEBIT`, `EDC`, `QRIS`, e-wallet names), except that a `TOTAL BAYAR` or `TOTAL PEMBAYARAN` label always counts as a total even on a payment row.
   - Screenshots: when both `NOMINAL` and a `TOTAL` that includes an admin fee are shown, pick the `TOTAL` (what left the account) and offer `NOMINAL` as a chip. The full label list is checked against the screenshot test set before it is fixed.
4. Prefer a grand-total style label over a plain `TOTAL`, and a later row over an earlier one. Take the rightmost amount on the row; if the row has none, take the next row unless it is a payment row.
5. If no label matched, take the largest amount that appears at least twice (the total repeats as the card or QRIS payment), else the largest amount outside payment rows.
- Other-amount chips: the next 3 distinct amounts by the same ranking, so the right total is usually one tap away when step 4 picks wrong.
- Money stays a `Long` of rupiah end to end; no `Double` anywhere in the parser.

### 5. Date and store
- Date: `dd/MM/yyyy`, `dd-MM-yyyy`, `dd.MM.yy`, `yyyy-MM-dd`, and Indonesian month names (`31 Okt 2026`). A date more than 1 year back or any date in the future is ignored. If found, it replaces the date and keeps the current time of day; the user sees it before saving.
- Store on a screenshot: the text after `Pembayaran ke`, `Bayar ke`, `Merchant`, `Nama Merchant`, `Penerima` or `Ke`, on the same row or the next. A private person's name from a transfer fills the note like a store name; the user sees it before saving.
- Store on paper: the first row in the top quarter of the receipt that has letters and no amount, matched first against a small list of known chains (Indomaret, Alfamart, Alfamidi, Solaria, Waroeng Steak & Shake, KFC, McDonald's, Starbucks, Janji Jiwa, Kopi Kenangan, Gacoan) so `lNDOMARET` still reads `Indomaret`. Fills the note only if the note is empty.

### 6. Unplaced lines
- Every row that is not the total, a payment row, the date or the store goes to `unplaced`, in printed order, trimmed, at most 40 rows.
- The sheet shows them under the heading **Lines Saku could not place**. A tap appends the line to the note, separated by `, `. They are not saved anywhere unless the user taps them.

### 7. Test data and where it may live
| Source | Licence | Use | In the repo? |
| --- | --- | --- | --- |
| CORD v2 test split, 100 Indonesian receipts (NAVER CLOVA) | CC BY 4.0 | OCR text fixtures (ML Kit output, saved once from the emulator) with ground-truth totals; JVM parser tests | Yes, the text fixtures only, credited in `app/src/test/resources/receipt/CORD-NOTICE.md`; not the images |
| Web photos of Indomaret, Alfamart, Solaria, Waroeng Steak & Shake and similar receipts | Not ours | Emulator accuracy run only | No. Kept in a local scratch folder; text fixtures from them are not committed either |
| Web screenshots of m-BCA, BRImo, Livin' by Mandiri, BNI, GoPay, OVO, ShopeePay and DANA payment confirmations | Not ours | Emulator accuracy run only, reported per app | No, same as the paper photos |
| Synthetic mock screenshot (a DANA-style payment screen with an invented merchant) | Ours | One JVM fixture and a second Maestro case | Yes, `data/mock-receipt-ewallet.png` |
| Synthetic mock receipt, drawn by a script with invented items | Ours | Maestro e2e and one instrumented test | Yes, `data/mock-receipt-indomaret.png` beside `mock-money-manager.xlsx` |
| The owner's own receipts | Private | Never used in tests | Never |

- Accuracy bar for the JVM suite on the CORD fixtures: at least 80% exact total and at least 85% "true total is the pick or one of the 3 chips" (owner, 31 Oct 2026: "minimum OCR pass 80% from all sources"). The first bar was 70% and 75%, set from the spike's 71% and 78%; the number repairs below took it to 90% and 95%. The suite fails below either, so a parser change cannot quietly lower accuracy.
- The emulator accuracy run (`ReceiptAccuracyTest`, instrumented, not in CI) prints the same two numbers for the CORD images and the web set, and the PR reports both.

### 8. End-to-end test
- `.maestro/flows/scan-receipt.yaml`: `clearState`, push `data/mock-receipt-indomaret.png` to `/sdcard/Pictures/` beforehand (documented in the flow header like `import-money-manager.yaml` does), open **Add transaction**, tap **Scan receipt**, choose the photo picker, pick the mock receipt, assert the amount `Rp 52.300`, the note `Indomaret`, and one unplaced line; tap it, assert it joined the note; pick a category; Save; assert the row in the list.
- The photo picker steps are the system's, so the flow marks that block like the document-picker block in the import flow.
- The camera path is checked by hand on the emulator once (its camera shows a virtual scene); it is not automated.

### 9. Where the action sits in the sheet
- **Scan receipt** is shown only for a new transaction, on the Expense type, in the lower half of the sheet near Save, following the thumb-reach rule. Exact placement and copy go through `impeccable` and `better-writing` during implementation.
- While reading, the sheet shows a progress state on the action and stays usable; a result arriving after the user typed an amount does not overwrite it, and offers the read total as a chip instead.

## Risks / Trade-offs

- [ML Kit's logging code tries to send without the permission] → It cannot open a socket without `INTERNET`; the spike ran clean. Check logcat for a crash or repeated retries during the accuracy run.
- [A future ML Kit update adds another network permission] → The unchanged gate fails the build, by design.
- [APK grows from 13.6 MB] → Measured 34.5 MB with two ARM ABIs; the owner chose arm64 only with compressed native libraries: 21.0 MB.
- [The 80% recognition ceiling] → Chips and the unplaced list make a miss cheap; the user always confirms. Suggest a flat, well-lit photo in the action's helper text.
- [CORD is mostly restaurants and cafés, older receipts] → The web set adds Indomaret, Alfamart and chain restaurants; report both numbers separately.
- [Fixture text is ML Kit output from one model version] → Re-record the fixtures when the dependency is upgraded; the accuracy test does that in one run.
- [A date misread moves a transaction to the wrong month] → Date sanity window (not future, not over a year back) and the date stays visible on the sheet before Save.

## Built (31 Oct 2026)

Notes from implementation, rejected turns included:

- **Accuracy, Kotlin parser.** CORD fixtures: 68 of 95 exact (71%), 75 of 95 pick or chip (78%),
  equal to the spike. Local set: 41 of 45 (91%). Paper photos 16 of 20, all 4 misses having no
  readable total. Wallet and banking screenshots 25 of 25 (m-BCA 3, BRImo 3, Livin' 3, wondr 2,
  GoPay 3, OVO 3, ShopeePay 3, DANA 3, SeaBank 1, Jago 1), none of them used to tune the parser.
- **Rows anchor on their first line.** The first port compared each line with the last line of the
  row, so a slanted photo chained three printed rows into one and 2 CORD receipts regressed. The
  row's first line is the anchor, as in the spike.
- **Dates are removed before amounts are read.** The emulator run showed `30 Okt 2026` offered as a
  Rp 2.026 chip. The month pattern takes whole month words only, so `1 DESSERT 25.000` keeps its
  price; a test pins both.
- **The photo picker can be missing.** On the Pixel 8 API 35 emulator image, `PICK_IMAGES` has no
  handler and the launch threw `ActivityNotFoundException`. The sheet falls back to the document
  picker (`OpenDocument`, `image/*`). A missing camera app does nothing.
- **ML Kit's logger without the permission.** Each scan logs about 5 `TransportRuntime` warnings
  ("Neither user nor current process has ACCESS_NETWORK_STATE") and gives up. No crash, no retry
  loop, recognition unaffected.
- **Camera path.** Checked by hand on the emulator: the photo is read, `cache/receipts/` is empty
  afterwards, and a scene with no receipt shows "No total found. Enter the amount yourself."
- **The no-network test learned removals.** `NoNetworkPermissionTest` matched the permission name
  anywhere in the manifest, so the removal lines failed it. It now tells a `tools:node="remove"`
  entry from a declaration, and a new test requires both removals to stay.
- **APK size.** v1.0 is 13.6 MB. With ML Kit and both ARM ABIs the release is 34.5 MB, over the
  15 MB growth limit in the risks. The native library is stored uncompressed (11.1 MB arm64,
  6.8 MB armeabi-v7a; 4.4 and 3.5 MB compressed). Options measured or estimated: compress the
  libraries (about 24.5 MB), arm64 only (about 27.7 MB), both (about 21.1 MB). Owner chose both:
  release `abiFilters` arm64-v8a only and `jniLibs.useLegacyPackaging = true`. Measured release:
  21.0 MB (20,971,576 bytes), 7.4 MB over v1.0. Debug keeps x86_64 for the emulator.
- **Maestro not run.** It is not installed, and the owner chose not to install it now. The steps of
  `scan-receipt.yaml` were run by hand through adb on the emulator (paper receipt, line to note,
  category, save, row on the list; wallet screenshot; Income hides the action) and passed.

- **80% on every source (owner).** Strictly read, OCR printed the true total on only 80% of
  the CORD receipts, so the parser alone could not pass 80%. The misses were recognizer slips,
  repaired before parsing: a space inside a number (`123. 585`, `54 ,000`), look-alike letters in
  a number token (`6B,000`, `Ii1,000`, `118.D00`, `Rpl65000`; only when real digits outnumber the
  letters, so `BOX` and `ID` stay words), a total cut short (`63.00`, `17,50`, `30. 0`, padded to a
  full group of three on a total row only), a space as the thousands separator on a total row
  (`63 000`), and misread labels (`TOTA.`, `10TAL`, `Tolal`, `GRAD`/`GND TOTAL`, `TL` and `DU` at
  the start of a row, `SUETOTAL`/`SUB, TOTAL`/`SUBTTL` as subtotals). A bare GRAND TOTAL takes a
  following card or QRIS amount, never cash.
- **Results after the repairs.** CORD test fixtures 86 of 95 (90%) exact, 91 (95%) pick or chip.
  Paper photos 19 of 20 (95%); the miss has its total cut off the photo. Screenshots 25 of 25.
  These sets guided the repairs, so as a check on overfitting the CORD validation split (98
  labelled receipts, never looked at) scored 86 (87%) exact and 92 (93%) pick or chip.
- **A regression the local set caught.** Allowing `TL` anywhere matched a phone status bar
  (`18.19 tl LTE`) and read 18.190 off a wondr screenshot; `TL` and `DU` now count only at the
  start of a row.
- **Two builds, tried and dropped.** The owner first asked for two downloads, standard (as v1) and
  scan. It was built as product flavors (standard 13.6 MB for any CPU, scan 21.0 MB arm64) and
  reverted the same day once both turned out to run on Android 8.0 and up: the owner chose one
  download. Cost of that choice: 32-bit phones, which v1.0 ran on, are no longer supported.

## Migration Plan

No data migration. The feature ships with the next release. Rollback is removing the action; nothing is stored that depends on it.

## Open Questions

- Owner to confirm the release APK size after measuring (decision 1).
- Decided 31 Oct 2026: e-wallet and mobile-banking screenshots are in scope (owner: "e wallet too, like mbca, gopay, ovo"). They have their own test set and per-app numbers.

## Superseded by receipt-items (1 Nov 2026)

The entry-sheet Scan receipt action described above was removed before release. Scanning now starts
from the Scan button beside + on Trans and opens the Receipt screen (`ui/receipt/`), which keeps
this change's reading of total, date and store, the chips (under the Receipt screen's total) and the
unplaced lines (in One row mode). `ReceiptScanAssembly.kt`, `ReceiptScanParts.kt` and their tests
were deleted. See `openspec/changes/receipt-items/design.md`.
