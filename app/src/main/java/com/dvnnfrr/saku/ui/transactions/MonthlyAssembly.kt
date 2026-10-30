package com.dvnnfrr.saku.ui.transactions

import com.dvnnfrr.saku.domain.DailyTotals
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.PeriodMode
import com.dvnnfrr.saku.domain.PeriodTotals
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.plus

/*
 * Pure assembly for the Monthly sub-tab. No Compose, no Android, no coroutines.
 *
 * Every figure is folded from one list of filtered day totals over the year widened to
 * whole weeks, so a week that starts in December of the year before still counts all
 * seven days. Weeks come from DateRange.ofWeek, Monday to Sunday, the same weeks the
 * Calendar grid and Stats Weekly use: the 28.09 ~ 04.10 row reads what Stats Weekly reads
 * for that week. Week rows therefore need not sum to their month row, and the full-span
 * label is what says why.
 */

/** The year shown in Monthly, with its months newest first. */
data class MonthlyYear(
    val year: Int,
    /** Days inside the year only, unlike the edge weeks. */
    val totals: PeriodTotals,
    val months: List<MonthRow>,
    /**
     * The days the year covers, for the header: the calendar year, or with a pay cycle the
     * first to the last day of the pay months named after its months.
     */
    val range: DateRange = DateRange.ofYear(year),
)

/**
 * One month. [totals] is null when the month holds no income and no expense, which the
 * screen renders as empty rather than as three zeros: an unlogged month is the gap the
 * owner is looking for, the same rule the Calendar follows for days.
 */
data class MonthRow(
    val range: DateRange,
    val totals: PeriodTotals?,
    val weeks: List<WeekRow>,
    val expanded: Boolean,
) {
    /**
     * The calendar month the row starts in, which keys expanding it. Unique within a year
     * even for pay months, where two can share a name with start day 15 or 16.
     */
    val month: Month get() = range.start.month

    /** The name the row shows: the month holding most of its days. */
    val labelMonth: Month get() = range.majorityMonth().month
}

/**
 * One Monday to Sunday week under a month, over all seven days. [openDate] is the week's
 * latest day inside the month it is listed under, which is where a tap scrolls Daily to.
 */
data class WeekRow(
    val range: DateRange,
    val totals: PeriodTotals?,
    val isCurrent: Boolean,
    val openDate: LocalDate,
)

/**
 * The months of [year], oldest first: the twelve calendar months, or with a pay cycle the
 * pay months named after a month of [year]. That is usually twelve, though start days 15
 * and 16 can give a year a name twice and another none, and the run is contiguous either
 * way because a later pay month never takes an earlier name.
 */
fun monthsOfYear(year: Int, payStartDay: Int?): List<DateRange> {
    var month = DateRange.monthOf(LocalDate(year, 1, 1), payStartDay)
    if (month.majorityMonth().year < year) month = month.next()
    val months = mutableListOf<DateRange>()
    while (month.majorityMonth().year == year) {
        months += month
        month = month.next()
    }
    return months
}

/** The days [monthsOfYear] covers, as one range the header can name. */
fun yearSpan(year: Int, payStartDay: Int?): DateRange {
    if (payStartDay == null) return DateRange.ofYear(year)
    val months = monthsOfYear(year, payStartDay)
    return DateRange(months.first().start, months.last().endInclusive, PeriodMode.ANNUALLY)
}

/** The days one Monthly query covers: [year]'s span widened to the weeks its edges fall in. */
fun monthlyQueryRange(year: Int, payStartDay: Int? = null): DateRange {
    val span = yearSpan(year, payStartDay)
    return DateRange.custom(
        DateRange.ofWeek(span.start).start,
        DateRange.ofWeek(span.endInclusive).endInclusive,
    )
}

/**
 * Folds [days] into the year. Months that have not started by [today] are left out, and
 * so are weeks that have not started; a past year lists all twelve months.
 */
fun assembleMonthlyYear(
    year: Int,
    days: List<DailyTotals>,
    today: LocalDate,
    expandedMonth: Month?,
    payStartDay: Int? = null,
): MonthlyYear {
    val months = monthsOfYear(year, payStartDay)
        .filter { it.start <= today }
        .reversed()
        .map { range ->
            MonthRow(
                range = range,
                totals = days.totalsWithin(range),
                weeks = weeksOf(range, days, today),
                expanded = range.start.month == expandedMonth,
            )
        }
    val span = yearSpan(year, payStartDay)
    return MonthlyYear(
        year = year,
        totals = days.totalsWithin(span) ?: PeriodTotals.EMPTY,
        months = months,
        range = span,
    )
}

private fun weeksOf(month: DateRange, days: List<DailyTotals>, today: LocalDate): List<WeekRow> {
    val weeks = mutableListOf<WeekRow>()
    var week = DateRange.ofWeek(month.start)
    while (week.start <= month.endInclusive && week.start <= today) {
        weeks += WeekRow(
            range = week,
            totals = days.totalsWithin(week),
            isCurrent = today in week,
            openDate = minOf(week.endInclusive, month.endInclusive),
        )
        week = DateRange.ofWeek(week.start.plus(1, DateTimeUnit.WEEK))
    }
    return weeks.reversed()
}

/** Null when nothing in [range] was income or expense: empty, not zero. */
private fun List<DailyTotals>.totalsWithin(range: DateRange): PeriodTotals? {
    var income = 0L
    var expense = 0L
    for (day in this) {
        if (day.date !in range) continue
        income += day.income.rupiah
        expense += day.expense.rupiah
    }
    if (income == 0L && expense == 0L) return null
    return PeriodTotals(Money(income), Money(expense))
}
