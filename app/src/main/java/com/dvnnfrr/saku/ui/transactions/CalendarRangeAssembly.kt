package com.dvnnfrr.saku.ui.transactions

import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.PeriodTotals
import com.dvnnfrr.saku.domain.Transaction
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/*
 * Pure assembly for the Calendar's date selection: how a tap moves it, and what a
 * selected range folds into. No Compose, no coroutines.
 */

/**
 * The dates picked on the Calendar, as the user picked them.
 *
 * [first] is the first tap and [second] the second, in tap order rather than date order,
 * so a range picked backwards still knows which end came first. [second] is null while
 * only one date is picked.
 */
data class CalendarSelection(val first: LocalDate, val second: LocalDate? = null) {

    /** The picked dates as a range, earlier end first, whichever order they were tapped in. */
    val range: DateRange
        get() {
            val other = second ?: first
            return DateRange.custom(minOf(first, other), maxOf(first, other))
        }

    companion object {
        /** A range swept by a drag. A drag that never left its cell is a single tap. */
        fun swept(from: LocalDate, to: LocalDate): CalendarSelection =
            CalendarSelection(from, to.takeIf { it != from })
    }
}

/**
 * Where a tap on [date] leaves [current].
 *
 * - nothing picked: [date] becomes a one-day selection
 * - one date picked: tapping it again clears it; any other date closes the range
 * - a range picked: [date] starts a new one-day selection
 */
fun CalendarSelection?.tap(date: LocalDate): CalendarSelection? = when {
    this == null -> CalendarSelection(date)
    second == null && date == first -> null
    second == null -> copy(second = date)
    else -> CalendarSelection(date)
}

/** A selected range folded for display: its totals, then its days as Daily shows them. */
data class CalendarRangeSummary(
    val range: DateRange,
    val totals: PeriodTotals,
    val days: List<DaySection>,
)

/**
 * Folds the rows inside [range] into a [CalendarRangeSummary].
 *
 * [transactions] are the month's rows already narrowed by the active filter, so the
 * range respects the filter without a query of its own. The totals and the day groups go
 * through [periodTotals] and [toDaySections], the same folds as the period header and
 * Daily, so a transfer counts toward neither In nor Out here either.
 */
fun List<Transaction>.toCalendarRangeSummary(
    range: DateRange,
    zone: TimeZone,
    index: DisplayIndex,
): CalendarRangeSummary {
    val inRange = filter { it.occurredAt.toLocalDateTime(zone).date in range }
    return CalendarRangeSummary(
        range = range,
        totals = inRange.periodTotals(),
        days = inRange.toDaySections(zone, index),
    )
}
