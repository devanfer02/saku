## ADDED Requirements

### Requirement: Scan a receipt from the entry sheet
The entry sheet SHALL offer a Scan receipt action when recording a new Expense. The action SHALL offer the system camera and the photo picker, and SHALL NOT require any runtime permission. The action SHALL NOT appear while editing an existing transaction or on the Income or Transfer type.

#### Scenario: Action shown for a new expense
- **WHEN** the user opens the entry sheet with Add transaction and the type is Expense
- **THEN** the Scan receipt action is visible

#### Scenario: Action hidden while editing
- **WHEN** the user opens an existing transaction
- **THEN** the Scan receipt action is not shown

#### Scenario: Picking a photo
- **WHEN** the user taps Scan receipt and chooses a photo from the photo picker
- **THEN** Saku reads the photo on the device and the sheet shows a reading state until the result arrives, without a permission prompt

#### Scenario: User cancels
- **WHEN** the user backs out of the camera or the photo picker
- **THEN** the sheet is unchanged

### Requirement: Fill the sheet from the receipt
After reading, Saku SHALL set the amount to the receipt total it picked, SHALL set the date to the printed date when one is read and falls between one year ago and today, and SHALL set the note to the store name when the note is empty. Type, account and category SHALL NOT change. An amount the user typed before the result arrived SHALL NOT be overwritten. Nothing SHALL be saved until the user taps Save.

#### Scenario: Clear Indomaret receipt
- **WHEN** the user scans a receipt whose rows include `TOTAL 52.300`, a date `31/10/2026` and `INDOMARET` at the top
- **THEN** the amount shows Rp 52.300, the date is 31 Oct 2026 with the current time of day, and the note is Indomaret

#### Scenario: Subtotal, tax and cash rows
- **WHEN** the receipt shows `SUBTOTAL 50.000`, `PPN 5.000`, `TOTAL 55.000`, `TUNAI 100.000` and `KEMBALI 45.000`
- **THEN** the amount is Rp 55.000

#### Scenario: Thousands separators and sen
- **WHEN** the total reads `25,000`, `Rp25.000`, `25000` or `25.000,00`
- **THEN** the amount is Rp 25.000 in every case

#### Scenario: OCR slip in the label
- **WHEN** the total row reads `T0TAL 28,000`
- **THEN** the amount is Rp 28.000

#### Scenario: E-wallet screenshot
- **WHEN** the user picks a DANA screenshot that reads `Total Bayar Rp46.882`, `Pembayaran ke` followed by a merchant name, and `15 Okt 2026 • 13:33`
- **THEN** the amount is Rp 46.882, the note is the merchant name, and the date is 15 Oct 2026

#### Scenario: Mobile-banking screenshot with an admin fee
- **WHEN** a mobile-banking screenshot shows `Nominal Rp 50.000,00`, `Biaya Admin Rp 2.500,00` and `Total Rp 52.500,00`
- **THEN** the amount is Rp 52.500 and Rp 50.000 is offered as a chip

#### Scenario: Future or stale date
- **WHEN** the printed date is in the future or more than one year ago
- **THEN** the sheet keeps its current date

#### Scenario: Amount typed first
- **WHEN** the user typed Rp 40.000 before the reading finished and the receipt total is Rp 42.000
- **THEN** the amount stays Rp 40.000 and Rp 42.000 is offered as a chip

#### Scenario: No total found
- **WHEN** no amount can be read from the photo
- **THEN** the amount stays as it was, the sheet says no total was found, and the unplaced lines are still shown

### Requirement: Detect the receipt kind automatically
Saku SHALL read paper receipts and e-wallet or mobile-banking screenshots with the same action and the same reading, and SHALL NOT ask the user which kind the image is. Total labels, store labels and date formats of both kinds SHALL be tried on every image.

#### Scenario: No kind to choose
- **WHEN** the user taps Scan receipt
- **THEN** the next step is the camera or the photo picker, with no choice between receipt and screenshot

#### Scenario: Same flow for both
- **WHEN** the user scans an Indomaret photo and then a GoPay screenshot
- **THEN** both fill the sheet through the same steps, each with its own total and store

### Requirement: Offer other amounts as chips
After a reading, the sheet SHALL show up to 3 other distinct amounts from the receipt as chips, ranked after the picked total. Tapping a chip SHALL replace the amount with that value.

#### Scenario: Wrong pick fixed in one tap
- **WHEN** the picked total is Rp 100.000 (the cash paid) and a chip shows Rp 28.000
- **THEN** tapping the Rp 28.000 chip sets the amount to Rp 28.000

### Requirement: Show lines Saku could not place
The sheet SHALL list, under Lines Saku could not place, every read row that is not the total, a payment row, the date or the store, in printed order, at most 40. Tapping a line SHALL append it to the note, separated from existing text by a comma and a space. Lines SHALL NOT be stored unless the user taps them and saves.

#### Scenario: Item codes shown
- **WHEN** the receipt has the row `INDOMIE GRG SPCL 2 3.100`
- **THEN** that row appears under Lines Saku could not place

#### Scenario: Line added to the note
- **WHEN** the note is Indomaret and the user taps the line `INDOMIE GRG SPCL 2 3.100`
- **THEN** the note becomes `Indomaret, INDOMIE GRG SPCL 2 3.100`

### Requirement: Read receipts offline and keep no photo
Reading SHALL run on the device with no network access. The merged manifest SHALL contain no network permission, and the build gate that enforces it SHALL stay unchanged. A camera photo SHALL be written only to the app cache and deleted after reading; a picked photo SHALL NOT be copied. The photo SHALL NOT be stored with the transaction.

#### Scenario: Build gate
- **WHEN** the app is assembled with the OCR dependency
- **THEN** `checkDebugNoNetworkPermission` passes and the merged manifest has no `INTERNET` or `ACCESS_NETWORK_STATE`

#### Scenario: Camera file removed
- **WHEN** a camera photo has been read
- **THEN** no file remains under the app cache's receipts folder

### Requirement: Accuracy is measured against fixtures
The project SHALL keep OCR-text fixtures from the CORD v2 test split with their labelled totals, and a JVM test SHALL fail if the exact-total rate drops below 80% or the rate where the true total is the pick or one of the chips drops below 85%. Before release, the exact-total rate SHALL be at least 80% on each local source as well: web photos of paper receipts and e-wallet or mobile-banking screenshots.

#### Scenario: Regression caught
- **WHEN** a parser change makes the exact-total rate on the CORD fixtures fall below 80%
- **THEN** the unit test suite fails and names the rate

#### Scenario: Misread numbers repaired
- **WHEN** the total row reads `TOTAL 6B,000`, `4 Total 123. 585` or `TOTAL 63.00`
- **THEN** the amount is Rp 68.000, Rp 123.585 or Rp 63.000
