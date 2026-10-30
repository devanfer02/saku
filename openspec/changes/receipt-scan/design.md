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
- Size: ML Kit adds `libmlkit_google_ocr_pipeline.so` at 6.8 to 11.6 MB per ABI. Release keeps `arm64-v8a` and `armeabi-v7a` through `abiFilters`; debug also keeps `x86_64` for the emulator. Measure the release APK before and after and record it in the PR.

### 2. Camera through the system intent, gallery through the photo picker
- Chosen: `ActivityResultContracts.TakePicture` into a `FileProvider` URI under `cache/receipts/`, and `PickVisualMedia(ImageOnly)`. Neither needs a permission, so the allowlist does not change.
- Alternative: CameraX inside Saku. Better framing control, but it needs `android.permission.CAMERA`, a new allowlist entry and a preview screen. Not worth it for v1.
- The cached camera file is deleted after reading, and on the next app start if a read was interrupted.

### 3. The parser is pure JVM, in `receipt/`, behind an `OcrLine` boundary
- `data/receipt/ReceiptRecognizer.kt` is the only file that imports ML Kit. It decodes the `Uri` (downscaled so the long side is at most 2,000 px), runs recognition, and returns `List<OcrLine(text, left, top, right, bottom)>`.
- `receipt/` takes `List<OcrLine>` and returns `ReceiptReading(total: Money?, otherAmounts: List<Money>, date: LocalDate?, store: String?, unplaced: List<String>)`. No Android, no ML Kit, so CI tests it on the JVM.
- `ui/entry/ReceiptScanAssembly.kt` turns a `ReceiptReading` into a form update. This follows the adapter-at-every-boundary rule: foreign shapes stop at `data/receipt/`.

### 4. How the total is picked (from the spike, to be ported and extended)
1. Rebuild printed rows: sort lines by vertical centre and join those whose centres are within 0.6 of a line height. ML Kit returns blocks out of reading order, so a label and its amount often arrive apart.
2. Read amounts: `25.000`, `25,000`, `Rp25.000`, `25000`, with a trailing `,00` or `.00` treated as sen and dropped. Values under Rp 100 are ignored (quantities, item counts). `O` between digits reads as `0`.
3. Match total labels allowing OCR slips: `T0TAL`, `TUTAI`, `TOTA1`, plus `GRAND TOTAL`, `TOTAL BELANJA`, `TOTAL BAYAR`, `TAGIHAN`, `JUMLAH`. Exclude `SUBTOTAL`, `TOTAL DISC`, `TOTAL ITEM`, `TOTAL HEMAT`, and payment rows (`TUNAI`, `CASH`, `KEMBALI`, `CHANGE`, `DEBIT`, `EDC`, `QRIS`, e-wallet names).
4. Prefer a grand-total style label over a plain `TOTAL`, and a later row over an earlier one. Take the rightmost amount on the row; if the row has none, take the next row unless it is a payment row.
5. If no label matched, take the largest amount that appears at least twice (the total repeats as the card or QRIS payment), else the largest amount outside payment rows.
- Other-amount chips: the next 3 distinct amounts by the same ranking, so the right total is usually one tap away when step 4 picks wrong.
- Money stays a `Long` of rupiah end to end; no `Double` anywhere in the parser.

### 5. Date and store
- Date: `dd/MM/yyyy`, `dd-MM-yyyy`, `dd.MM.yy`, `yyyy-MM-dd`, and Indonesian month names (`31 Okt 2026`). A date more than 1 year back or any date in the future is ignored. If found, it replaces the date and keeps the current time of day; the user sees it before saving.
- Store: the first row in the top quarter of the receipt that has letters and no amount, matched first against a small list of known chains (Indomaret, Alfamart, Alfamidi, Solaria, Waroeng Steak & Shake, KFC, McDonald's, Starbucks, Janji Jiwa, Kopi Kenangan, Gacoan) so `lNDOMARET` still reads `Indomaret`. Fills the note only if the note is empty.

### 6. Unplaced lines
- Every row that is not the total, a payment row, the date or the store goes to `unplaced`, in printed order, trimmed, at most 40 rows.
- The sheet shows them under the heading **Lines Saku could not place**. A tap appends the line to the note, separated by `, `. They are not saved anywhere unless the user taps them.

### 7. Test data and where it may live
| Source | Licence | Use | In the repo? |
| --- | --- | --- | --- |
| CORD v2 test split, 100 Indonesian receipts (NAVER CLOVA) | CC BY 4.0 | OCR text fixtures (ML Kit output, saved once from the emulator) with ground-truth totals; JVM parser tests | Yes, the text fixtures only, credited in `app/src/test/resources/receipt/CORD-NOTICE.md`; not the images |
| Web photos of Indomaret, Alfamart, Solaria, Waroeng Steak & Shake and similar receipts | Not ours | Emulator accuracy run only | No. Kept in a local scratch folder; text fixtures from them are not committed either |
| Synthetic mock receipt, drawn by a script with invented items | Ours | Maestro e2e and one instrumented test | Yes, `data/mock-receipt-indomaret.png` beside `mock-money-manager.xlsx` |
| The owner's own receipts | Private | Never used in tests | Never |

- Accuracy bar for the JVM suite on the CORD fixtures: at least 70% exact total and at least 75% "true total is the pick or one of the 3 chips". The spike measured 71% and 78% (the ceiling is 80%, the share where the true total was read at all). The suite fails below either, so a parser change cannot quietly lower accuracy. Raise the bar as the parser improves.
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
- [APK grows from 13.6 MB] → `abiFilters` to the two ARM ABIs; measure in the PR. If the growth exceeds about 15 MB, revisit (split APKs per ABI on the release).
- [The 80% recognition ceiling] → Chips and the unplaced list make a miss cheap; the user always confirms. Suggest a flat, well-lit photo in the action's helper text.
- [CORD is mostly restaurants and cafés, older receipts] → The web set adds Indomaret, Alfamart and chain restaurants; report both numbers separately.
- [Fixture text is ML Kit output from one model version] → Re-record the fixtures when the dependency is upgraded; the accuracy test does that in one run.
- [A date misread moves a transaction to the wrong month] → Date sanity window (not future, not over a year back) and the date stays visible on the sheet before Save.

## Migration Plan

No data migration. The feature ships with the next release. Rollback is removing the action; nothing is stored that depends on it.

## Open Questions

- Owner to confirm the release APK size after measuring (decision 1).
- Owner to confirm whether e-wallet and m-banking screenshots (GoPay, OVO, DANA, BCA) should be in the web set; they are easier to read than paper and may be the more common input.
