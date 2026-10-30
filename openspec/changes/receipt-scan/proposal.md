## Why

Most spending the owner logs late comes with a paper receipt or a payment screen: Indomaret, Alfamart, Solaria, Waroeng Steak & Shake on paper, and m-BCA, BRImo, Livin' by Mandiri, GoPay, OVO, ShopeePay and DANA on screen. Typing the total from it is the slow part of an entry, and a total read off a crumpled receipt hours later is where typos come from. The owner asked for OCR: read the receipt, fill what can be filled, and show the lines Saku could not place so they can note them by hand.

A spike on 31 Oct 2026 (branch `spike/ocr-mlkit`, not merged) showed it is doable offline. ML Kit's bundled text recognizer read the 100 receipts of the CORD v2 test split on the emulator in 794 ms median, 2.1 s at most. A first total picker got the right total on 68 of 95 labelled receipts (71%), and the true total was read somewhere on 76 (80%).

## What Changes

- The entry sheet for a new transaction gets a **Scan receipt** action. It offers the system camera or the photo picker. Neither needs a permission.
- A receipt is either a photo of paper or a screenshot of an e-wallet or mobile-banking payment confirmation. A screenshot arrives through the photo picker, since screenshots are in the gallery.
- Saku reads the photo on the device and fills the sheet: the amount from the receipt's total, the date if one is printed, and the store name as the note. Type stays Expense; account and category stay as they were.
- Under the amount, up to 3 other amounts read from the receipt show as chips. Tapping one replaces the amount, so a wrong pick costs one tap.
- A **Lines Saku could not place** list shows the item lines and codes it read but does not use (`INDOMIE GRG SPCL 2 3.100`). Tapping a line adds it to the note. Nothing is filled from a line the user did not tap.
- If no total is found, the sheet says so, keeps the amount empty and still shows the lines.
- The photo is never stored. Only what the user saves is kept, as an ordinary transaction.
- New dependency `com.google.mlkit:text-recognition` (bundled Latin model). It merges `INTERNET` and `ACCESS_NETWORK_STATE` through `com.google.android.datatransport:transport-backend-cct`; the app manifest removes both with `tools:node="remove"`. The merged-manifest gate is not changed and still fails on any network permission.
- Accuracy is measured, not assumed: a pure JVM test suite over saved OCR text, and an emulator accuracy run over the CORD v2 test split plus web photos of Indonesian receipts.
- A Maestro end-to-end flow scans a synthetic mock receipt through the photo picker and saves the transaction.

## Capabilities

### New Capabilities
- `receipt-scan`: taking or picking a receipt photo from the entry sheet, reading it on the device, what fills the sheet, the other-amount chips, the unplaced-lines list, and the no-network and no-storage guarantees.

### Modified Capabilities
None. The entry sheet's existing requirements (types, amount, category, account, edit and delete) do not change; the scan is an addition to a new entry only.

## Impact

- `receipt/` (new, pure JVM like `excel/`): `OcrLine` input type, row rebuilding, amount reading, total picking, date and store reading, `ReceiptReading` result. No Android, no ML Kit.
- `data/receipt/` (new): `ReceiptRecognizer`, the only file that touches ML Kit; adapts ML Kit `Text` to `OcrLine` and decodes the image from a `Uri`.
- `ui/entry/`: `ReceiptScanAssembly.kt` (reading to sheet fill), scan action, chips and lines list in `EntrySheet.kt`, ViewModel events, strings.
- `AppContainer`: provides the recognizer.
- `AndroidManifest.xml`: 2 `tools:node="remove"` lines and a `FileProvider` for the camera photo (cache path only).
- `app/build.gradle.kts`: the ML Kit dependency; release `abiFilters` arm64-v8a only and compressed native libraries (owner's choice), so the release is 21.0 MB against v1.0's 13.6 MB. One download; 32-bit phones are no longer supported.
- Tests: JVM parser tests with OCR-text fixtures derived from CORD v2 (CC BY 4.0, credited); an instrumented accuracy run on the emulator, not in CI; `.maestro/flows/scan-receipt.yaml` with a synthetic receipt image.
- No Room entity change, no migration, no repository interface change.
