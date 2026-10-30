## 1. Shared

- [x] 1.1 Give the money format a signed mode (`+Rp`, `−Rp`) and a balance mode (sign only when negative) in its single implementation; route every amount through `MoneyText`
- [x] 1.2 Add a proportional bar to `RankedRow`, scaled to the largest row
- [x] 1.3 Replace the account chip in `TransactionRow` with secondary text `Account · Subcategory · note`; transfers name both accounts
- [x] 1.4 Contrast test: every text token against `background` and `surface` in both themes at 4.5:1 or more; move remaining `ink3` text uses to `ink2`

## 2. Trans

- [x] 2.1 Header: "Left over" net as the largest figure, In and Out beside it, from the transaction-list assembly
- [x] 2.2 Day headers as `Thu 29 Oct` with a signed day net, through the one day-label implementation
- [x] 2.3 Row amounts: income in the income colour, expenses in ink, both signed
- [x] 2.4 Empty state copy

## 3. Stats

- [x] 3.1 Total as the largest figure with the comparison line, from `StatsAssembly`
- [x] 3.2 Ranked rows with bars; remove the donut from Stats
- [x] 3.3 Comparison-line scenarios tested in `StatsAssembly` tests
- [x] 3.4 Compact money mode (`+2,5 jt`) in the single formatter, integer-only, with the rounding scenarios tested
- [x] 3.5 Six-month window fold in `StatsAssembly`: per-month income, expense, net, bar shares from `Long`, empty slots before the first transaction, filter respected; tested against the October 2026 figures
- [x] 3.6 Six-month strip composable on Stats (Monthly mode only): paired bars, muted non-selected months, net labels, tap selects the month, content description per month for TalkBack

## 4. Accounts

- [x] 4.1 Net worth with a Spendable / Invested (and Liabilities when present) split bar, from `AccountsAssembly`; remove the donut
- [x] 4.2 Status wording and colours per the spec; rename Reconcile to Check balance in the screen, dialog and detail
- [x] 4.3 Delete `Donut` and `DonutGeometry` and their tests if no caller remains

## 5. More

- [x] 5.1 Group into Organise, Your data, App with subtitles, including the full-backup entries

## 6. Verification

- [ ] 6.1 Update Maestro flows for renamed labels (Check balance, Left over)
- [ ] 6.2 Emulator pass in light and dark at font scale 1.0 and 1.3 on all four tabs; compare against the directions page
- [x] 6.3 `ANDROID_HOME=/home/dvnnfrr/Android/Sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug` green
