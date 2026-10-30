## Context

`DateRange` (domain) builds week, month, year and custom ranges and shifts them; every query already takes a range, so the period boundary is a domain concern only. The owner asked on 30 Oct 2026, right after choosing the Buku Kas six-month strip, that a month can follow a payday ("someone might have their payday at 30th, someone at beginning of month"), with a default view and a configured view.

## Goals / Non-Goals

**Goals:**
- Compare spending against the salary that funds it.
- Keep calendar months the default and one switch away.
- One arithmetic rule, exhaustively tested.

**Non-Goals:**
- Shifting a payday that falls on a weekend or holiday (Indonesian payroll often pays early). Possible later as an option; recorded here.
- Detecting payday from income rows automatically.
- Pay weeks or pay years.

## Decisions

**Start day, not "payday".** The setting is the day a pay month starts, with the payday as its natural value. Simple, and it covers 1st, 25th and 30th alike.

**Clamp to the month's last day, recomputed per month.** D = 31 gives 31 Jan, 28 Feb, 31 Mar, not a drifting 28th. Each pay month's start is computed from its own calendar month, and its end is the day before the next start, so there are no gaps or overlaps.
- Rejected: forbid 29 to 31. People paid on the 30th or "end of month" are the motivating case.

**Majority-month label plus the span.** "October" for 5 Oct to 4 Nov, "November" for 25 Oct to 24 Nov. A tie is possible: with D = 16, 16 Sep to 15 Oct has 15 days on each side. A tie picks the later month, because the salary that starts the period pays for the month ahead; a test pins it. The span under the label removes any remaining ambiguity.
- Rejected: label by start month. A 30th payday would call 30 Oct to 29 Nov "October", which is 1 day of October.

**View choice shared and persisted; filter not persisted.** `transaction-filter` deliberately does not persist its filter so a reopened app never shows filtered totals. The month view differs: both views show complete totals and every pay month shows its span, so persisting it cannot mislead.

**Where it lives.** `DateRange.ofPayMonth(anyDay, startDay)` with mode `PAY_MONTH` and its own shift; `PeriodMode` gains `PAY_MONTH` only as a variant of Monthly in the ViewModels. A `PeriodStore` beside `AppearanceStore` (SharedPreferences, same pattern) holds the start day and the view. No settings table: two values, read before first frame.

## Risks / Trade-offs

- [Two views confuse figures] → Span always shown in Pay month; switch hidden until a pay cycle is set.
- [Monthly view week rows] → Week rows stay Monday to Sunday inside the pay month, clipped at its edges, matching how the Monthly view clips calendar months.
- [Ordering with in-flight work] → Implement after `transaction-filter`; the six-month strip from `buku-kas` takes the view from the same ViewModel input.

## Open Questions

- Whether the More entry should suggest the start day from the most common day of "Gaji" income rows. Not in scope.

## Decisions made while building

**A `payStartDay` field on `DateRange`, mode stays `MONTHLY`.** The range carries the unclamped start day, because the clamped start cannot give it back: 28 Feb is where 28, 29, 30 and 31 all land, and stepping needs the real day to return to 31 Mar. A pay month is a Monthly range with that field set.
- Rejected: `PeriodMode.PAY_MONTH`, as first written above. The Stats mode tabs index by `range.mode`, so a fifth value lights no tab, and every `when` over the mode (labels, shift, `rangeForMode`, tab labels) grows a branch that means "Monthly". It would also still need the field.

**Start day 1 returns the calendar month itself.** `ofPayMonth(day, 1)` returns `ofMonth(day)` with no `payStartDay`, so the two compare equal and the spec's "D = 1 is exactly calendar months" holds by construction, not only by value of the dates.

**One label rule, in the domain.** `DateRange.majorityMonth()` counts days per calendar month, ties to the later one. `periodLabel` uses it for Monthly (a calendar month is its own majority, so nothing changes there) and for Annually (a year of pay months lands in its year). The span line is the existing `spanLabel` with a `" to "` separator (`paySpanLabel`), and Stats' `explicitRangeLabel` delegates to it for a pay month, so one pay month is written one way on both tabs.
- Rejected: a second date formatter for pay months. The repo rule is one implementation per date label.

**Start days 15 and 16 can repeat a name.** With D = 16, 16 Apr to 15 May ties and goes to May, and 16 May to 15 Jun is also mostly May; April gets no pay month. The label rule is the owner's call and the span beneath disambiguates, so the rule stands. What changed because of it: a Monthly row keys expansion on the month it *starts* in (unique per year), and shows the majority month as its name (`MonthRow.labelMonth`). The Monthly year is the run of pay months whose majority month falls in that year; the run is contiguous because a later pay month never takes an earlier name (tested for every D over 2025 to 2027).
- Rejected: one row per calendar month, picking the pay month of that name. It drops a pay month and shows another twice in exactly the D = 15/16 case.

**A pay year header.** In Trans Monthly under a pay cycle the header covers the first to last day of that year's pay months (`MonthlyYear.range`, mode Annually), reads "2026" through the majority rule, and shows the span ("30 Dec 2025 to 29 Dec 2026") under the switch. The filter sheet opens on the same span.

**`PeriodStore` is a concrete class with an in-memory `StateFlow`.** One implementation, built once in `AppContainer`; every write goes through it and updates the flow directly. Tests run the real store under Robolectric.
- Rejected: an interface plus a fake (a second implementation nobody asked for). Rejected: a `callbackFlow` over a preference listener like `AppearanceStore`. There is one writer, so the listener buys nothing, and it would add main-looper delivery to the ViewModel tests.

**Changing the basis keeps your place.** When the start day or the view changes, each ViewModel's collector calls `DateRange.rebased(activeStartDay, today)`: a month holding today becomes the month holding today; any other month keeps its name, so switching on "March 2026" with D = 5 lands on 5 Mar to 4 Apr (called March), not on the pay month holding 1 Mar (called February). Stats rebases only Monthly ranges; Weekly, Annually and Period stay put. An open drill survives, as it does an arrow step. Switching Stats into Monthly uses the same rule through `rangeForMode(..., payStartDay)`.
- Rejected: always jumping to today (loses the month being looked at). Rejected: anchoring on the old range's start (lands a month early for small D).

**Actions build from the range, not from the store.** Steps, the twelve-month Monthly arrows (`shift(12)`), and a Calendar or Monthly tap (`monthOf(date, range.payStartDay)`) all read the range's own start day, so no action reads settings and nothing can mix bases mid-step. Only the opening range and `selectMode` read `PeriodStore.current()`.

**Where the switch goes, and merging with `buku-kas`.** `MonthViewSwitch` is its own file in `ui/paycycle/`, not `ui/shared/`, because it carries its own copy and shared components may not. Each screen gets one inserted line plus a callback parameter:
- Trans: between `PeriodHeader` and `FilterBar`, passing `span = state.headerRange` so the pay month's days show under the switch. When `buku-kas` rebuilds the Trans header, the span line can move up under the label.
- Stats: under `RangeDates`, with no span, because `RangeDates` already shows the pay span. Hidden outside Monthly mode, where it would change nothing.

**The six-month strip reads `StatsUiState.monthWindow`.** The strip does not exist on this branch. `monthWindow(range)` in `StatsAssembly` gives the six ranges ending with the selected one, oldest first, by stepping the selected range back, so it is six pay months under a pay cycle and six calendar months otherwise, and empty outside Monthly. Once `buku-kas` merges, its strip must take its months from this list (and name each bar with `periodLabel`) rather than building calendar months itself.

**Calendar tab in Pay month.** The grid runs from the pay month's first day, with leading blanks for its weekday, so it reads 30, 31, 1, 2 across the month edge. Marking the 1st with its month name is a possible follow-up; not done here.
