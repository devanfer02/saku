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
