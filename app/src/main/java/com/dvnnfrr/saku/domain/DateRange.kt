package com.dvnnfrr.saku.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

/** The period modes offered by the stats screen. */
enum class PeriodMode {
    WEEKLY,
    MONTHLY,
    ANNUALLY,
    CUSTOM,
}

/**
 * An inclusive span of days.
 *
 * Shared by the transaction list's month header and the stats period selector, so
 * the two can never disagree about where a month starts. All arithmetic is on
 * [LocalDate]; conversion to the [Instant] bounds a query needs happens once, in
 * [startInstant] and [endInstantExclusive].
 */
data class DateRange(
    val start: LocalDate,
    val endInclusive: LocalDate,
    val mode: PeriodMode = PeriodMode.CUSTOM,
    /**
     * Set on a pay month: the day of the month it starts on, before clamping. Carried
     * because the clamped [start] cannot give it back (28 Feb is where 28, 29, 30 and 31
     * all land), and stepping needs the unclamped day. Null on every other range,
     * including a calendar month.
     */
    val payStartDay: Int? = null,
) {
    init {
        require(start <= endInclusive) { "Range start $start is after end $endInclusive" }
        require(payStartDay == null || (mode == PeriodMode.MONTHLY && payStartDay in 2..31)) {
            "Pay start day $payStartDay on a $mode range"
        }
    }

    operator fun contains(date: LocalDate): Boolean = date in start..endInclusive

    fun startInstant(zone: TimeZone): Instant = start.atStartOfDayIn(zone)

    /** Exclusive upper bound, so a query is `occurredAt >= start && occurredAt < end`. */
    fun endInstantExclusive(zone: TimeZone): Instant =
        endInclusive.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)

    fun previous(): DateRange = shift(-1)

    fun next(): DateRange = shift(1)

    /**
     * Steps the range by whole periods. A [PeriodMode.CUSTOM] range shifts by its own
     * length, which keeps the arrows useful for an arbitrary span. A pay month steps from
     * its calendar month rather than from its clamped start, so a 31st start that fell on
     * 28 Feb comes back to 31 Mar instead of drifting to the 28th.
     */
    fun shift(periods: Int): DateRange = when (mode) {
        PeriodMode.WEEKLY -> ofWeek(start.plus(periods, DateTimeUnit.WEEK))
        PeriodMode.MONTHLY -> if (payStartDay == null) {
            ofMonth(start.plus(periods, DateTimeUnit.MONTH))
        } else {
            val month = start.firstOfMonth().plus(periods, DateTimeUnit.MONTH)
            ofPayMonth(payStartIn(month, payStartDay), payStartDay)
        }
        PeriodMode.ANNUALLY -> ofYear(start.plus(periods, DateTimeUnit.YEAR).year)
        PeriodMode.CUSTOM -> {
            val length = start.daysUntilInclusive(endInclusive)
            DateRange(
                start = start.plus(periods * length, DateTimeUnit.DAY),
                endInclusive = endInclusive.plus(periods * length, DateTimeUnit.DAY),
                mode = PeriodMode.CUSTOM,
            )
        }
    }

    /**
     * The first day of the calendar month holding most of this range's days, which is
     * what a pay month is named after: 25 Oct to 24 Nov is November. A tie goes to the
     * later month, because the salary that opens the period pays for the month ahead. A
     * calendar month is its own majority, and a year of pay months lands inside its year.
     */
    fun majorityMonth(): LocalDate {
        var best = start.firstOfMonth()
        var bestDays = 0
        var month = best
        while (month <= endInclusive) {
            val last = month.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
            val days = maxOf(month, start).daysUntilInclusive(minOf(last, endInclusive))
            if (days >= bestDays) {
                best = month
                bestDays = days
            }
            month = month.plus(1, DateTimeUnit.MONTH)
        }
        return best
    }

    /**
     * The same month under another month basis, for when the pay cycle or the month view
     * changes underneath a screen. A month holding [today] becomes the month holding today;
     * any other keeps its name, so switching on "October 2026" lands on the pay month
     * called October rather than on whichever one holds 1 Oct.
     */
    fun rebased(payStartDay: Int?, today: LocalDate): DateRange {
        val target = payStartDay.takeIf { it != 1 }
        if (target == this.payStartDay) return this
        if (today in this) return monthOf(today, target)
        val name = majorityMonth()
        val first = monthOf(name, target)
        // A pay month named after [name] overlaps it, so it holds the 1st or follows the
        // one that does. Start days 15 and 16 can leave a month with no pay month of its
        // name at all; the one holding the 1st is then the nearest.
        return listOf(first, first.next()).firstOrNull { it.majorityMonth() == name } ?: first
    }

    companion object {
        /** Weeks run Monday to Sunday. */
        fun ofWeek(anyDayInWeek: LocalDate): DateRange {
            val monday = anyDayInWeek.minus(
                anyDayInWeek.dayOfWeek.isoDayNumber - DayOfWeek.MONDAY.isoDayNumber,
                DateTimeUnit.DAY,
            )
            return DateRange(monday, monday.plus(6, DateTimeUnit.DAY), PeriodMode.WEEKLY)
        }

        fun ofMonth(anyDayInMonth: LocalDate): DateRange {
            val first = LocalDate(anyDayInMonth.year, anyDayInMonth.month, 1)
            val last = first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
            return DateRange(first, last, PeriodMode.MONTHLY)
        }

        /**
         * The pay month holding [anyDay], for pay months starting on day [startDay].
         *
         * Each start is computed from its own calendar month and clamped to that month's
         * last day, and a pay month ends the day before the next start, so consecutive
         * pay months never gap or overlap. A start day of 1 is the calendar month, and is
         * returned as one so the two compare equal.
         */
        fun ofPayMonth(anyDay: LocalDate, startDay: Int): DateRange {
            require(startDay in 1..31) { "Pay start day $startDay is not a day of the month" }
            if (startDay == 1) return ofMonth(anyDay)
            val inThisMonth = payStartIn(anyDay.firstOfMonth(), startDay)
            val start = if (anyDay >= inThisMonth) {
                inThisMonth
            } else {
                payStartIn(anyDay.firstOfMonth().minus(1, DateTimeUnit.MONTH), startDay)
            }
            val nextStart = payStartIn(start.firstOfMonth().plus(1, DateTimeUnit.MONTH), startDay)
            return DateRange(start, nextStart.minus(1, DateTimeUnit.DAY), PeriodMode.MONTHLY, startDay)
        }

        /** The calendar month when [payStartDay] is null, otherwise the pay month. */
        fun monthOf(anyDay: LocalDate, payStartDay: Int?): DateRange =
            if (payStartDay == null) ofMonth(anyDay) else ofPayMonth(anyDay, payStartDay)

        fun ofYear(year: Int): DateRange =
            DateRange(LocalDate(year, 1, 1), LocalDate(year, 12, 31), PeriodMode.ANNUALLY)

        fun custom(start: LocalDate, endInclusive: LocalDate): DateRange =
            DateRange(start, endInclusive, PeriodMode.CUSTOM)

        /** The default the stats screen and transaction list both open on. */
        fun currentMonth(today: LocalDate): DateRange = ofMonth(today)
    }
}

private fun LocalDate.daysUntilInclusive(other: LocalDate): Int = daysUntil(other) + 1

private fun LocalDate.firstOfMonth(): LocalDate = LocalDate(year, month, 1)

/** Day [startDay] of [firstOfMonth]'s month, or that month's last day when it is shorter. */
private fun payStartIn(firstOfMonth: LocalDate, startDay: Int): LocalDate {
    val length = firstOfMonth.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth
    return LocalDate(firstOfMonth.year, firstOfMonth.month, minOf(startDay, length))
}

/** Truncates to whole seconds, discarding the microsecond tail the export carries. */
fun Instant.truncatedToSeconds(): Instant = Instant.fromEpochSeconds(epochSeconds)

fun LocalDateTime.toInstantIn(zone: TimeZone): Instant = toInstant(zone)
