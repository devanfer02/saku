package com.dvnnfrr.saku.ui.stats

import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.ui.transactions.shortLabel
import kotlinx.datetime.LocalDate

/*
 * Pure assembly for the period picker the Stats header opens. No Compose, no Android: which
 * months and years to offer, and which one is current and which selected, are decided here
 * and tested on the JVM. The ranges come from `DateRange.ofMonth` and `ofYear`, the same
 * ones the period arrows step, so a picked month and a stepped-to month are equal.
 */

/** One cell of the month or year grid. */
data class PeriodChoice(
    val range: DateRange,
    val label: String,
    /** Holds today. Marked, so "now" is findable in a grid of twelve. */
    val current: Boolean,
    /** The period on screen. Highlighted. */
    val selected: Boolean,
)

/** How far back the year grid reaches when there is no history to start it from. */
const val PICKER_DEFAULT_YEARS_BACK = 5

/** The twelve calendar months of [year], January first, laid out three across by the screen. */
fun monthChoices(year: Int, selected: DateRange, today: LocalDate): List<PeriodChoice> =
    (1..12).map { month ->
        val range = DateRange.ofMonth(LocalDate(year, month, 1))
        PeriodChoice(
            range = range,
            label = range.start.month.shortLabel(),
            current = today in range,
            selected = range == selected,
        )
    }

/**
 * The years the year grid offers: from [firstDataYear] (or [PICKER_DEFAULT_YEARS_BACK]
 * years back when there is no data) through next year. The selected year and this year
 * are always in the list, so the grid can never leave out the period on screen.
 */
fun yearChoices(firstDataYear: Int?, selected: DateRange, today: LocalDate): List<PeriodChoice> {
    val first = minOf(firstDataYear ?: (today.year - PICKER_DEFAULT_YEARS_BACK), selected.start.year, today.year)
    val last = maxOf(today.year + 1, selected.start.year)
    return (first..last).map { year ->
        val range = DateRange.ofYear(year)
        PeriodChoice(
            range = range,
            label = year.toString(),
            current = today in range,
            selected = range == selected,
        )
    }
}

/**
 * The first year the strip covers, read off columns already loaded and already filtered,
 * so the year grid needs no query of its own. Null when the strip has no history: an
 * empty slot is not data.
 */
fun firstDataYear(strip: List<StripColumn>): Int? =
    strip.firstOrNull { !it.empty }?.range?.start?.year
