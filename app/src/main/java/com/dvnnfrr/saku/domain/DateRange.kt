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
) {
    init {
        require(start <= endInclusive) { "Range start $start is after end $endInclusive" }
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
     * length, which keeps the arrows useful for an arbitrary span.
     */
    private fun shift(periods: Int): DateRange = when (mode) {
        PeriodMode.WEEKLY -> ofWeek(start.plus(periods, DateTimeUnit.WEEK))
        PeriodMode.MONTHLY -> ofMonth(start.plus(periods, DateTimeUnit.MONTH))
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

        fun ofYear(year: Int): DateRange =
            DateRange(LocalDate(year, 1, 1), LocalDate(year, 12, 31), PeriodMode.ANNUALLY)

        fun custom(start: LocalDate, endInclusive: LocalDate): DateRange =
            DateRange(start, endInclusive, PeriodMode.CUSTOM)

        /** The default the stats screen and transaction list both open on. */
        fun currentMonth(today: LocalDate): DateRange = ofMonth(today)
    }
}

private fun LocalDate.daysUntilInclusive(other: LocalDate): Int = daysUntil(other) + 1

/** Truncates to whole seconds, discarding the microsecond tail the export carries. */
fun Instant.truncatedToSeconds(): Instant = Instant.fromEpochSeconds(epochSeconds)

fun LocalDateTime.toInstantIn(zone: TimeZone): Instant = toInstant(zone)
