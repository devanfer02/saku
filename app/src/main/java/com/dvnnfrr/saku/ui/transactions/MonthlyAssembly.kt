package com.dvnnfrr.saku.ui.transactions

import com.dvnnfrr.saku.domain.DailyTotals
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
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
    val month: Month get() = range.start.month
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

/** The days one Monthly query covers: [year] widened to the weeks its edges fall in. */
fun monthlyQueryRange(year: Int): DateRange = DateRange.custom(
    DateRange.ofWeek(LocalDate(year, 1, 1)).start,
    DateRange.ofWeek(LocalDate(year, 12, 31)).endInclusive,
)

/**
 * Folds [days] into the year. Months that have not started by [today] are left out, and
 * so are weeks that have not started; a past year lists all twelve months.
 */
fun assembleMonthlyYear(
    year: Int,
    days: List<DailyTotals>,
    today: LocalDate,
    expandedMonth: Month?,
): MonthlyYear {
    val lastMonth = when {
        year < today.year -> 12
        year == today.year -> today.monthNumber
        else -> 0
    }
    val months = (lastMonth downTo 1).map { number ->
        val range = DateRange.ofMonth(LocalDate(year, number, 1))
        MonthRow(
            range = range,
            totals = days.totalsWithin(range),
            weeks = weeksOf(range, days, today),
            expanded = range.start.month == expandedMonth,
        )
    }
    return MonthlyYear(
        year = year,
        totals = days.totalsWithin(DateRange.ofYear(year)) ?: PeriodTotals.EMPTY,
        months = months,
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
