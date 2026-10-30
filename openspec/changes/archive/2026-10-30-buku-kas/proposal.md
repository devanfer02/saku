## Why

The first on-device review (30 Oct 2026, 1,300 imported rows) scored the four tabs 24/40 on Nielsen's heuristics. The figures are right but hard to trust at a glance: money is written three different ways, red marks a non-problem on every account, secondary text fails contrast, and donuts carry two tabs where five of eight slices are under 10%. The owner chose direction A, "Buku Kas", from the UI directions page: keep Saku's look and Material 3 structure, and make every tab read like a tidy cashbook.

## What Changes

- One money format everywhere: always `Rp`, always a sign on in and out figures (`+Rp 2.473.600`, `−Rp 196.000`), through `MoneyText` only.
- Direction is carried by the sign. Income amounts stay in the income colour; expense amounts in rows render in ink; the expense colour stays on expense totals and expense-side labels.
- Trans header leads with "Left over" as the largest figure, with In and Out beside it. Day headers read `Thu 29 Oct` with a signed day net.
- Every row still names its account, as quiet secondary text, not a coloured chip.
- Stats leads with "Spent in October" and a one-line comparison with the previous period, then ranked category bars (colour, name, whole percent, amount, bar). The donut is removed from Stats.
- Accounts leads with net worth and a Spendable / Invested split bar instead of the donut. "Never reconciled" becomes neutral "Balance not checked yet"; a balance below zero gets the warning colour and a one-line reason. "Reconcile" is renamed "Check balance".
- More is grouped into Organise, Your data and App, each row with a one-line subtitle.
- `ink3` is no longer used for text anywhere; text uses `ink` or `ink2` (contrast 4.5:1 or better in both themes).
- Copy rewrites for empty states and labels, listed in design.md.

## Capabilities

### New Capabilities
- `buku-kas`: the money display rule, the per-tab hierarchy for Trans, Stats, Accounts and More, status wording, and the text contrast floor.

### Modified Capabilities
None formally, because `openspec/specs/` has no synced capability. This change supersedes two v1 requirements, recorded in design.md: "Blue accent, separate semantic colours for money direction" (app-shell-theme) and "Show account and subcategory on every row" (transaction-list, presentation only).

## Impact

- `ui/theme/` (text token use, no new colour literals expected), `ui/shared/` (`MoneyText`, `RankedRow`, `TransactionRow`, `Donut` callers), `ui/transactions/`, `ui/stats/`, `ui/accounts/`, the More menu in `ui/navigation/` or `ui/shell/`, `res/values/strings.xml`.
- Assembly files gain the derived fields the new headers need (left over, comparison line, split bar shares).
- No Room entity, DAO or repository interface change.
- Lands after `transaction-filter` and `full-backup`, which touch the same screens.
