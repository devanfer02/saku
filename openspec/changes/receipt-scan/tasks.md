## 1. Dependency and the network gate

- [x] 1.1 Measure the current release APK size (`assembleRelease`, unsigned is fine) and write it down for the PR
- [x] 1.2 Add `com.google.mlkit:text-recognition` to `gradle/libs.versions.toml` and `app/build.gradle.kts`
- [x] 1.3 Remove `INTERNET` and `ACCESS_NETWORK_STATE` in `AndroidManifest.xml` with `tools:node="remove"` and a comment naming `transport-backend-cct`; leave `CheckNoNetworkPermission` unchanged
- [x] 1.4 (release 34.5 MB vs 13.6 MB; size choice pending with the owner) Add `abiFilters` (release: `arm64-v8a`, `armeabi-v7a`; debug adds `x86_64`), measure the release APK again, record both sizes
- [x] 1.5 Run `checkDebugNoNetworkPermission` and `NoNetworkPermissionTest`; both pass

## 2. Parser in `receipt/` (pure JVM)

- [x] 2.1 `OcrLine` and `ReceiptReading` types; `ReceiptRows.kt` rebuilds printed rows from line boxes, with tests for out-of-order blocks
- [x] 2.2 `ReceiptAmounts.kt`: read `25.000`, `25,000`, `Rp25.000`, `25000`, `25.000,00`, `O`-for-`0`; ignore values under Rp 100; tests for each form, returning `Money`
- [x] 2.3 `ReceiptTotal.kt`: label matching with OCR slips, exclusions, grand-total preference, next-row rule, repeat and largest fallbacks, and the 3 ranked chip amounts; tests from the spec scenarios
- [x] 2.4 `ReceiptDate.kt`: numeric and Indonesian month formats, the one-year and no-future window; tests
- [x] 2.5 `ReceiptStore.kt`: top-quarter row and the known-chain list with fuzzy match (`lNDOMARET`); tests
- [x] 2.6 `ReceiptParser.read(lines): ReceiptReading` with the unplaced list (printed order, at most 40); tests

## 3. Fixtures and the accuracy bar

- [ ] 3.1 (deferred: the spike's `OcrSpikeTest` produced the dumps; port it into androidTest when the fixtures are re-recorded) Instrumented `ReceiptAccuracyTest` (androidTest, not in CI): reads every image in `/data/local/tmp/receipts/` through `ReceiptRecognizer`, writes OCR lines and the reading per image, prints exact-total and pick-or-chip rates
- [x] 3.2 Run it on the emulator over the CORD v2 test split (done with the spike's identical `OcrSpikeTest` output, ML Kit 16.0.1); save the OCR-line files as `app/src/test/resources/receipt/cord/*.txt` with `totals.tsv`, and add `CORD-NOTICE.md` (CC BY 4.0 credit to NAVER CLOVA, link, "OCR text derived from the images")
- [x] 3.3 `ReceiptFixtureAccuracyTest` (JVM): fails below 70% exact or 75% pick-or-chip on the CORD fixtures and prints both rates
- [x] 3.4 Run the accuracy test over the local web sets: paper (Indomaret, Alfamart, Solaria, Waroeng Steak & Shake and others) and screenshots (m-BCA, BRImo, Livin', BNI, GoPay, OVO, ShopeePay, DANA); record per-store and per-app rates for the PR; commit nothing from either set
- [x] 3.5 Fix the worst misses from 3.2 and 3.4 in the parser and keep both bars green; note what was tried in `design.md`

## 4. Recognizer and wiring

- [x] 4.1 `data/receipt/ReceiptRecognizer.kt`: decode a `Uri` downscaled to a 2,000 px long side, run ML Kit, adapt `Text` to `List<OcrLine>`; the only file importing ML Kit
- [x] 4.2 Provide it from `AppContainer`
- [x] 4.3 `FileProvider` for `cache/receipts/`; delete the camera file after reading and clear the folder on app start
- [x] 4.4 `ui/entry/ReceiptScanAssembly.kt`: `ReceiptReading` plus form to the fill (amount unless typed, date in window, note if empty) and the chips and lines; JVM tests for each spec scenario

## 5. Entry sheet UI

- [ ] 5.1 (copy done with `better-writing`; `impeccable` critique of the screenshots still to do) Load `impeccable` (Android) and `better-writing`; place Scan receipt in the lower half of the sheet for a new Expense only; write copy for the action, reading state, no-total message, chips and the Lines Saku could not place list
- [x] 5.2 ViewModel: scan events, reading state, result handling that never overwrites a typed amount; ViewModel tests with a fake recognizer
- [x] 5.3 Stateless composables for the chips and the lines list; tapping a line appends `, <line>` to the note
- [x] 5.4 Camera (`TakePicture`) and photo picker (`PickVisualMedia`) launchers; cancel leaves the sheet unchanged
- [ ] 5.5 (labels added; the `android-accessibility` check is not run yet) TalkBack labels for the action, chips and lines; check with `android-accessibility`

## 6. End-to-end

- [x] 6.1 Script that draws `data/mock-receipt-indomaret.png`: invented items, `TOTAL 52.300`, `TUNAI 100.000`, `KEMBALI 47.700`, a date, `INDOMARET` header, one item line used by the flow
- [x] 6.2 `.maestro/flows/scan-receipt.yaml`: header documents the `adb push` to `/sdcard/Pictures/` and the photo-picker block; scan, assert `Rp 52.300` and `Indomaret`, tap an unplaced line, pick a category, Save, assert the row
- [x] 6.2a Script that draws `data/mock-receipt-ewallet.png` (DANA-style screen, invented merchant, `Total Bayar Rp46.500`), and a second case in the flow (or `scan-ewallet.yaml`) asserting the amount and the merchant note
- [ ] 6.3 (blocked: Maestro is not installed; the same steps were run by hand through adb and passed) Run the new flow and the existing 7 flows on the emulator; all pass
- [x] 6.4 Check the camera path once by hand on the emulator and with logcat open (no crash, no network retries)

## 7. Done

- [x] 7.1 Full build: `:app:lintDebug :app:testDebugUnitTest :app:assembleDebug` green
- [x] 7.2 Update `README.md` features and the root `CLAUDE.md` package map with `receipt/`
- [ ] 7.3 PR with APK sizes, CORD and web-set accuracy, and the e2e result; wait for checks
