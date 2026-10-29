## Context

On 30 Oct 2026 the owner reviewed three UI directions on the "Saku UI Directions" page (https://claude.ai/artifact/Q2gfayXyaTfURPaWdokgsF) and chose A, Buku Kas. The page holds the critique (24/40 Nielsen), the contrast measurements and the mockups this change implements. Theme tokens live in `ui/theme/SakuColors.kt`; money formatting has one implementation behind `MoneyText`; percentages go through `wholePercents`.

## Goals / Non-Goals

**Goals:**
- Every figure is written one way and its direction is readable without colour.
- Each tab's most important figure is its largest.
- Red and amber mean "look at this", nothing else.

**Non-Goals:**
- New colours, fonts or a new visual identity (that was direction C).
- Budget caps (direction B), tracked separately as gap 5.
- Changing data, queries or repositories.

## Decisions

**Sign carries direction; colour supports it.** v1 required separate semantic colours for income and expense so that "a figure's direction never depends on chrome". That intent holds better with a sign on every in/out figure. Rows then render expenses in ink, which frees the expense colour for totals and keeps red from losing meaning. This supersedes v1 app-shell-theme "Blue accent, separate semantic colours for money direction" for row amounts only.
- Rejected: keep red on every expense row (today's state; the critique's P1 "red is spent on a non-problem").
- Rejected: drop the income colour too. Income is rare (38 of 1,300 rows) and worth spotting.

**Account stays on every row, as text.** The mockup hid the account when it was the most-used one. That contradicts v1 transaction-list "Show account and subcategory on every row", which exists because of gap 2: 97% of Money Manager rows collapsed to one account, and a visible account is how a wrong one gets noticed. Kept on every row, demoted from a coloured chip to secondary text `BCA · note`.
- Rejected: hide the default account (re-opens gap 2).

**Ranked bars replace the Stats donut.** With 8 slots and most slices under 10%, angle comparison fails; bars on a shared scale do not. `RankedRow` already renders colour, name, percent and amount; it gains a proportional bar scaled to the largest row.
- Rejected: keep a small donut above the bars. It repeats the bars and pushes the list below the fold on a 412 dp phone.

**Accounts uses a split bar.** Net worth is one number; its composition is two or three shares, which a single bar shows exactly.
- Rejected: keep the account donut with its new centre-fit fix. It answers "which account is biggest", which the sorted list already answers.

**`Donut` stays in `ui/shared/`** if any caller remains after this change; if none does, delete it and `DonutGeometry` in the same change rather than keep dead code.

**Copy, from the better-writing review.**

| Today | Buku Kas |
|---|---|
| Never reconciled | Balance not checked yet |
| Reconcile | Check balance |
| Nothing recorded this month | No transactions in October. Tap + to add one. |
| Total (Trans header) | Left over |
| Nothing to chart yet | Add an opening balance to see your total |

**Contrast.** Measured with the WCAG formula: `ink3` is 3.0:1 on light and 3.8:1 on dark, so it leaves text duty; `ink2` (6.0:1 and 6.8:1) takes it. A unit test over the token sets asserts every text token against both surfaces at 4.5:1 or more, beside `SakuColorsTest`.

## Risks / Trade-offs

- [Transaction rows lose the account colour swatch] → The account colour still appears on Accounts and in the filter; rows keep the category colour dot, which is the more useful one when scanning.
- [Owner habit: red rows as the expense cue] → The minus sign and Out total carry it; reversible by one token use if it reads wrong on device.
- [Conflicts with in-flight changes] → Implement after `transaction-filter` and `full-backup` are merged into `feat/v1-feedback`.

## Open Questions

None blocking.
