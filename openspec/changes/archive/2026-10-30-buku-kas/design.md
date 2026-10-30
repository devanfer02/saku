## Context

On 30 Oct 2026 the owner reviewed three UI directions on a private "Saku UI Directions" page (deleted after the build, at the owner's request) and chose A, Buku Kas. The page compared A (refine, cashbook), B "Amplop" (budget envelopes, needs caps) and C "Arus" (six-month trend on every tab), each as light and dark mockups of the four tabs. The page holds the critique (24/40 Nielsen), the contrast measurements and the mockups this change implements. Theme tokens live in `ui/theme/SakuColors.kt`; money formatting has one implementation behind `MoneyText`; percentages go through `wholePercents`.

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

**Six-month strip from direction C, added by the owner after choosing A.** On the directions page the owner singled out C's "Six months" chart. It goes on Stats in Monthly mode only, above the period total: that is where a month is read against its neighbours, and Weekly/Annual modes have no natural six-unit window.
- Data: one query of income and expense totals per month over the six-month window, through the same filter-aware totals path the period header uses (from `transaction-filter`), folded in `StatsAssembly`. If `transaction-filter` exposes per-day totals only, fold those; no new repository method unless the fold proves too slow on the 1,300-row database.
- Bar heights: the assembly emits each bar as a share of the window maximum computed from `Long` values; only the drawing turns it into a float for geometry. No `Float` or `Double` touches a total or a label.
- Compact labels (`+2,5 jt`) are a new mode of the one money formatter, not a second formatter. `jt` (juta) and the decimal comma match how Indonesian amounts are spoken and written.
- Rejected: putting the strip on Trans as in direction C. Trans already leads with "Left over"; two headline devices compete, and Trans is the logging screen.
- Rejected: a line chart. Paired bars show income against expense per month, which a line pair hides when they cross.

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

## Decisions made while building

**One formatter, three sign rules.** `formatMoney(money, sign, type)` in `ui/theme/Format.kt` takes a `MoneySign`: `BALANCE` (minus only when negative; balances and labelled totals), `NET` (plus or minus; Left over, day nets, Monthly nets, ledger effects) and `FLOW` (sign from the type; transaction rows). `MoneyText` takes `sign` instead of the old `signed: Boolean` and lost `withPrefix`, so no figure on screen can drop `Rp`. `formatIdr` and `formatIdrSigned` stay as the two common shortcuts over it.
- Rejected: separate `formatIncome` / `formatExpense` functions. Three entry points for one rule is the duplication the architecture rules forbid.
- Rejected: inferring the sign from `type` alone. The Trans header passes a type for colour on "In" and "Out", and the spec scenario shows those labelled totals unsigned (`In Rp 16.500.000`). Whether a figure is signed depends on whether its label already names the direction, which only the caller knows.
- Kept `withPrefix` on `formatIdr` only, for amounts inside a sentence or an expression that already names the currency: the entry keypad's pending expression and the import discrepancy row ("IDR %2$s").

**The minus is U+2212.** The spec writes `−Rp 196.000` with the typographic minus. It is the width of `+`, so signed columns stay aligned under tabular figures, and TalkBack reads it as "minus". Every negative figure in the app changed from a hyphen to it, including the calendar cells.

**Adjustments keep their stored sign (interpretation, please confirm).** The spec says transfers and adjustments show no sign. An adjustment is stored signed (a downward correction is negative), so showing `Rp 25.000` for a −25.000 correction would hide which way the balance moved. Under `FLOW` an adjustment reads like a balance: a minus when negative, nothing when positive. Transfers are unsigned as specified. If the owner wants the literal rule, it is one line in `signFor`.

**Labelled totals are unsigned.** "In", "Out", "Spent in October" and the account balances use `BALANCE`. The spec's first requirement says in and out amounts carry a sign; its own scenarios show these labelled totals without one. Read as: a sign appears where no label states the direction (rows, nets).

**The calendar cell formatter moved into `Format.kt`.** `compactNet` lived in `CalendarTab.kt`, a second money formatter outside the one file. It is now `formatIdrCell` beside `formatIdrCompact`, output unchanged except for the minus sign. Its `k` / `m` form stays: a seventh of the screen cannot fit `−1,4 jt`.
- Rejected: replacing it with the `jt` form. Out of scope, and too wide for a cell.

**Compact form rounds half away from zero on the magnitude.** `formatIdrCompact` divides the rupiah into tenths of a juta with integer arithmetic. A figure that rounds to nothing reads `0 jt` with no sign, because `+0,0 jt` claims a direction. Amounts of a thousand juta and more keep full-stop grouping (`+1.234,6 jt`).

**Bars come from one integer helper.** `barPermille(part, max)` in `domain/Models.kt`, next to `sharePercent`, returns thousandths from `Long` arithmetic. Ranked rows, subcategory rows, the strip and the account split all use it; the drawing is the only place a fraction exists. Ranked bars scale to the largest row in the list rather than the first, because the aggregated "Other" can outweigh the top category. The Other drill draws its members on their own scale.

**Rows: one secondary line, account first, plus a category dot.** Task 1.3's order `Account · Subcategory · note` is used. The spec sentence "as secondary text under the note" conflicts with its own scenario (`BCA · Spotify Premium`, where Spotify Premium is the note), so the scenario and the task win: the note moves onto the account line, after the subcategory. The line ellipsises from its end, so a long note is cut before the account or the subcategory. The account swatch is gone; a category colour dot leads the row, as the risk note below assumed. `TransactionRowUi` gained `categorySlot`, resolved in `DisplayIndex`.
- Rejected: account swatch kept beside the account text. Two dots per row, and the spec demotes the account to plain text.

**Day header has no year; standalone dates do.** `dayHeaderLabel()` is now `Thu 29 Oct`. Accounts ("Checked 14 Oct 2026"), the entry sheet's When field and the Stats range line use the new `dateLabel()` (`29 Oct 2026`), which also replaced a private month list in `StatsLabels.kt`, so date wording has one home in `DateLabels.kt` plus `periodLabel` / `periodName` in `PeriodBar.kt`.

**Trans header wraps rather than clips.** "Left over" in `amountLarge` with In and Out beside it is about 330 dp at font scale 1.0 and over 400 dp at 1.3 on a 412 dp phone. The header is a `FlowRow`: In and Out sit beside the figure when they fit and wrap beneath it when they do not. Check on device at 1.3.

**Day header shows the signed day net only when the day has a flow.** A transfer-only day shows no figure, because `Rp 0` would read as a day that cancelled out.

**Stats overview scrolls as one list.** The strip, side tabs, headline and ranked rows are items of one `LazyColumn`; only the period bar, range line, filter bar and mode tabs stay fixed. With the strip in Monthly mode, a fixed header left the ranked list a sliver at font scale 1.3. In a drill the side tabs stay fixed above the drill as before.
- Rejected: the strip above the mode tabs. It belongs to Monthly mode, so it sits under the control that summons it.

**The strip reads six `observePeriodTotals` calls plus one for history.** The brief required the strip to use the same filter-aware totals path as the Stats header, so each month is the header's own query under the same filter; a strip bar cannot disagree with the header. Seven small aggregates instead of one grouped query; no repository or DAO change.
- Rejected: one `observeDailyTotals` over the window, folded per month. Same WHERE fragment, but a different query from the header's, which is the drift the brief ruled out.
- Rejected: a new "first transaction date" repository method. It changes a repository interface, which the proposal excludes.

**"Before the first transaction" means before the first flow that passes the filter.** Months are empty slots while nothing that the bars count has happened yet: no income or expense before the window (one aggregate from 1900-01-01 to the day before the window) and none in this or an earlier window month. Under a filter that means nothing matching the filter. A transfer-only history counts as no history, because transfers are not drawn. A quiet month after history began is a real zero: two absent bars on the baseline with `0 jt` under it, where an empty slot has no net label.

**The strip stays up for the frame a month change takes.** The ViewModel shows the previous strip until the new one lands, rather than removing the item and jumping the list; leaving Monthly hides it at once. A strip tap behaves like the arrows: it keeps an open category drill open.

**Strip colours are the income and expense tokens, muted by alpha.** No new colours. The five non-selected months draw at 40% alpha; their labels use `ink2` and the selected month's `ink`. Each month column is one 48 dp tap target speaking "October 2026. In Rp …, out Rp …, net …" and its selected state.

**Accounts split bar is ink and secondary ink.** Not chart slots: slots are pinned to accounts for life, so a blue Spendable segment would read as "BCA". Not income or expense: neither is a flow. The bar splits Spendable against Invested only; Liabilities is a third labelled figure, since a debt is not a share of what is held. A group below zero draws no segment (its figure still shows the negative amount). The two segments are a share and its remainder, so they always fill the bar.
- Rejected: percentages on the split. "Both amounts labelled" is the requirement; a percent beside each amount crowded the row at 1.3.

**Below zero excludes liabilities.** A liability's balance is negative by construction while money is owed, so "Below zero. Set an opening balance" on a credit card would be wrong. `Account.isBelowZero()` is balance below zero and type not LIABILITY. Below zero takes precedence over the check status; the balance itself also turns amber. List and detail share `statusText()` / `statusColor()`, so the two cannot word a state differently.

**Donut removal took the account mix and the segment selection with it.** `accountMix`, `AccountShare`, `sharePercent` on account rows, `selectSegment` and `selectedAccountId` existed only for the donut and its legend. Deleted with `Donut`, `DonutGeometry`, their two test files, the three `donut*` spacing tokens and `CategoryTotal.shareFraction`. `ShareRing` stays: the filter sheet uses it and it is a single share, not a composition.

**More is grouped in `ui/shell/Placeholders.kt`.** `MoreEntry` gained a subtitle and a `MoreGroup`; the enum order is now the listing order. `MorePlaceholder` became `MoreMenu`. Group headings are marked as headings for TalkBack.

**`ink3` stays for icons and control outlines.** It measures 3.0:1 and 3.8:1, which passes the 3:1 floor for non-text UI, so empty-state icons, the search icon and unchecked checkbox outlines keep it. `TextContrastTest` asserts every text token at 4.5:1 on `background` and `surface` in both themes, and scans `ui/` for `ink3` reaching a `color =` argument.

**Owner review on device, 30 Oct 2026.** After using the build, the owner removed pay-cycle months entirely (calendar months only; a custom span uses Period mode) and asked for: the strip to scroll through all history; a chart style choice between ranked bars and a pie, remembered; a visible cue that category rows open a detail; a category detail with trend and subcategories; Spendable and Invested in distinct hues; and a larger "Net worth" label.
- The pie comes back as an option, not the default, so the earlier "ranked bars replace the donut" decision still holds for the default view. The deleted `Donut` is not restored as is: a new pie lives in `ui/stats/` with the centre-fit rule the old donut fix found (measure, shrink, never wrap).
- Rejected for the category detail: subcategories only, or trend only. The owner chose both.

**Owner review build, 30 Oct 2026 (tasks 7.1 to 7.4 and 7.8).**

**The strip reads one filtered daily totals query over all history.** It supersedes "the strip reads six `observePeriodTotals` calls plus one for history" above. With the strip covering every month since July 2025, one aggregate per column would be 17 live queries in Monthly and about 70 in Weekly, each re-running on every write. Instead `observeDailyTotals` runs once from 1970-01-01 to the end of the selected period under the active filter, and `foldStrip` buckets the days into weeks, months or years. On 1,300 rows that is about 400 day rows. The drift the earlier decision feared does not arise: the daily query has the same `TXN_FILTER_WHERE` fragment and its `type IN ('INCOME','EXPENSE')` matches the period query's `CASE`, and `StatsViewModelTest` asserts the selected column equals the header.
- Rejected: one `observePeriodTotals` per column. Same numbers, 17 to 70 queries.
- Rejected: a grouped-by-month DAO query. It changes a repository interface for a fold the existing query already supports.

**History starts at 1970, not 1900.** The daily query buckets days with the zone offset at its range start. From 1900 that is Jakarta's local mean time, +07:07:12, which pushed every day bucket seven minutes back into the previous day: income at 09:00 on 1 August landed in July. A strip test caught it. Jakarta has been +07:00 since 1964 and seeded accounts open at the epoch.

**One fold for every period mode (7.8).** `foldStrip(days, selected)` reads the mode off the selected range; `periodContaining(date, mode)` buckets a day and `DateRange.next()` steps a column, the same ranges the period arrows use. Period mode has no strip (`hasStrip`). `StripMonth` became `StripColumn`; the header names the unit through `stripTitleRes`; column labels live in `DateLabels.kt` as `stripColumnLabel()` (`26 Oct`, `Oct`, `Jan 26`, `2026`). January carries its two-digit year so a scroll back never loses the year. A strip tap now takes the tapped range as is (`selectPeriod`), since it may be a week or a year.

**The strip opens at its end by laying out in reverse.** A `LazyRow` with `reverseLayout = true` and the columns newest first puts the selected period at the right edge as the list's natural first position, with no scroll that has to land after layout. A new selection creates a new `LazyListState`, so after tapping January 2026 January is rightmost. Columns are a fixed 64 dp: six plus 8 dp padding is 400 dp on a 412 dp phone. With no flow up to the selected period at all the strip is that period alone as an empty slot; empty slots before history no longer exist, because history now starts where the strip starts.

**Chart style lives in `ChartStyleStore`, a class, not an interface.** Same `SharedPreferences` and `callbackFlow` shape as `AppearanceStore`, own file `saku_stats`, wired in `AppContainer`. No interface because nothing asks for a second implementation; the ViewModel test runs it on Robolectric's preferences. The ViewModel writes the choice straight to the store and reads it back as a flow, so it is a user choice the ViewModel holds without owning.

**The pie is a ring with the total in its hole.** `StatsPie` in `ui/stats/` takes resolved colours and sweeps. Sweeps are tenths of a degree from `pieSweeps`, which takes each boundary as the running total's exact `Long` share of the turn, so slices always add to exactly 3,600 and the last cannot fall short. The overview pie draws `composition.rows`, so it has the same eight-slot cap and "Other" as the ranked rows below it, which stay as the legend. The centre keeps the old donut fix: measure, shrink to the chord of the hole, never wrap or clip. A 2 dp gap separates slices. The toggle is two 48 dp icon buttons beside the headline, a radio pair for TalkBack.
- Rejected: a segmented "Bars / Pie" control under the side tabs. It would be a third row of tabs above the list, which already scrolls under the period bar, filter bar and mode tabs.

**Subcategories draw in steps of the parent's colour.** A slot is a category's identity, so subcategories cannot borrow other slots. Slices step down from full strength by 18 points to a floor of 28 (`subcategoryStrength`), and "No subcategory" takes the neutral chart colour. `RankedRow` and `ColorSwatch` gained an optional `color`, so the rows below match the pie slice for slice, in both styles. The lighter steps fall under 3:1 against the surface; the rows carry name, percent and amount, so the colour is never the only cue.
- Rejected: the parent colour for every slice. With gaps alone the slices were indistinguishable.

**Category detail order and sources.** Header total, then share, count and average, then the category's strip, then the split, then transactions. The share is looked up in the overview's composition (`shareOf`), not recomputed, so it is the 34% the row showed. The average rounds half up to the rupiah from `Long`. The category strip is the overview's daily totals query under `categoryFilter`: the side narrowed to this category, the other side emptied, the accounts kept. The strip follows the period mode like the overview's (interpretation: the spec's "Monthly mode only" predates 7.8; please confirm). It draws one bar per column, the side's, labelled with an unsigned compact total (`formatIdrCompact(money, MoneySign.BALANCE)`, a mode of the one formatter). An empty category still shows its strip above the empty message.

**Chevron and label on every row that opens a detail.** `CategoryRow` passes a trailing chevron to `RankedRow`, and its merged description ends "Opens the category", or "Opens the categories in Other" on Other, with the button role. The pressed state is the Material ripple from `clickable`. Members listed under Other use the same row, so they carry the chevron too.

## Risks / Trade-offs

- [Transaction rows lose the account colour swatch] → The account colour still appears on Accounts and in the filter; rows keep the category colour dot, which is the more useful one when scanning.
- [Owner habit: red rows as the expense cue] → The minus sign and Out total carry it; reversible by one token use if it reads wrong on device.
- [Conflicts with in-flight changes] → Implement after `transaction-filter` and `full-backup` are merged into `feat/v1-feedback`.

## Open Questions

None blocking.
