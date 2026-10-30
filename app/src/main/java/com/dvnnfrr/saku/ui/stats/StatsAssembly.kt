package com.dvnnfrr.saku.ui.stats

import com.dvnnfrr.saku.domain.CategoryTotal
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.DailyTotals
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.PeriodMode
import com.dvnnfrr.saku.domain.Transaction
import com.dvnnfrr.saku.domain.TransactionFilter
import com.dvnnfrr.saku.domain.wholePercents
import com.dvnnfrr.saku.domain.PeriodTotals
import com.dvnnfrr.saku.domain.barPermille
import kotlinx.datetime.LocalDate

/*
 * Pure assembly for the stats screen. No Compose, no Android, no coroutines — the cap,
 * the ranking, the shares and the period arithmetic are all decided here and tested on
 * the JVM against plain lists.
 *
 * What is NOT here is any summing of transactions into period or category totals. The
 * transaction list folds a month in Kotlin because it already has the rows on screen;
 * stats reports on periods it does not list, so an annual view must not load a year of
 * rows to total them. Those figures arrive from the repository's SQL aggregates.
 */

/** The palette has eight slots, so the ninth category onward is aggregated. */
const val CHART_SLOT_CAP = ColorSlot.COUNT

/**
 * Ranks a period's categories and caps the chart at [cap] slices.
 *
 * The shares come from [wholePercents] over the FULL list rather than over the capped
 * one, and "Other" then takes whatever is left of the hundred. Two things follow, both
 * of them requirements:
 *
 * - The column sums to exactly 100. Rounding each row on its own gives a chart that
 *   reads 99 or 101, which design.md forbids.
 * - Expanding "Other" shows the same percentage for a category that the collapsed view
 *   accounted for. Re-running largest-remainder over nine rows and again over the tail
 *   would produce two different numbers for one category, which is worse than either.
 *
 * Rows arrive from SQL ordered by amount, but the order is re-established here with an
 * explicit tie-break so that two categories on the same figure rank identically every
 * time; an unstable order would reshuffle the bars on a query that changed nothing.
 */
fun rankComposition(totals: List<CategoryTotal>, cap: Int = CHART_SLOT_CAP): RankedComposition {
    if (totals.isEmpty()) return RankedComposition.EMPTY

    val ordered = totals.sortedWith(
        compareByDescending<CategoryTotal> { it.total.rupiah }
            .thenBy { it.name }
            .thenBy { it.categoryId },
    )
    val percents = wholePercents(ordered)
    val ranked = ordered.mapIndexed { index, total ->
        RankedCategory(
            categoryId = total.categoryId,
            name = total.name,
            colorSlot = total.colorSlot,
            total = total.total,
            percent = percents[index],
        )
    }
    val sum = Money(ordered.sumOf { it.total.rupiah })

    if (ranked.size <= cap) {
        return RankedComposition(rows = ranked.withBars(), otherMembers = emptyList(), total = sum)
    }

    val charted = ranked.take(cap)
    val members = ranked.drop(cap)
    val other = RankedCategory(
        categoryId = null,
        name = null,
        colorSlot = null,
        total = Money(members.sumOf { it.total.rupiah }),
        percent = 100 - charted.sumOf { it.percent },
    )
    return RankedComposition(
        rows = (charted + other).withBars(),
        otherMembers = members.withBars(),
        total = sum,
    )
}

/**
 * Each row's bar against the largest row in the same list. The largest, not the first:
 * an aggregated "Other" can outweigh the top category it follows.
 */
private fun List<RankedCategory>.withBars(): List<RankedCategory> {
    val max = maxOfOrNull { it.total } ?: return this
    val sweeps = pieSweeps(map { it.total })
    return mapIndexed { index, row ->
        row.copy(barPermille = barPermille(row.total, max), sweepTenths = sweeps[index])
    }
}

/** A full turn in tenths of a degree, the unit a pie slice's sweep is carried in. */
const val PIE_WHOLE = 3_600

/**
 * Each value's slice of a full turn, in tenths of a degree, from `Long` arithmetic.
 *
 * Each boundary is the running total's exact share of the turn, and a sweep is the gap
 * between two boundaries, so the sweeps add up to exactly [PIE_WHOLE] whatever the
 * rounding: the last slice cannot come up short or overlap the first. A value of zero
 * or below takes no slice; with nothing positive at all every sweep is zero.
 */
fun pieSweeps(values: List<Money>): List<Int> {
    val drawn = values.map { it.rupiah.coerceAtLeast(0L) }
    val total = drawn.sum()
    if (total <= 0L) return List(values.size) { 0 }

    var running = 0L
    var previous = 0
    return drawn.map { value ->
        running += value
        val boundary = ((running * PIE_WHOLE) / total).toInt()
        (boundary - previous).also { previous = boundary }
    }
}

/**
 * A category's whole percent of its side, as the ranked list showed it. Taken from the
 * composition rather than recomputed, so the detail and the row that opened it cannot
 * disagree by a rounding step. Zero when the category is not in the composition.
 */
fun shareOf(composition: RankedComposition, categoryId: Long): Int =
    (composition.rows + composition.otherMembers).firstOrNull { it.categoryId == categoryId }?.percent ?: 0

/** The average transaction, rounded half up to the rupiah. Zero with no transactions. */
fun averagePerTransaction(total: Money, count: Int): Money {
    if (count <= 0) return Money.ZERO
    val half = count / 2
    val rupiah = total.rupiah
    return Money(if (rupiah >= 0) (rupiah + half) / count else -((-rupiah + half) / count))
}

/**
 * [filter] narrowed to one category on one side, for that category's strip. The other
 * side is emptied rather than left at "All", and because a category selection is then
 * set, transfers and adjustments drop out by the filter's own rule. The account part is
 * kept, so the strip respects it exactly as the overview does.
 */
fun categoryFilter(filter: TransactionFilter, side: StatsSide, categoryId: Long): TransactionFilter =
    when (side) {
        StatsSide.EXPENSES -> filter.copy(expenseCategoryIds = setOf(categoryId), incomeCategoryIds = emptySet())
        StatsSide.INCOME -> filter.copy(incomeCategoryIds = setOf(categoryId), expenseCategoryIds = emptySet())
    }

/**
 * How much [current] moved against [previous], as a whole percent.
 *
 * Null when there is no baseline. A period that held nothing gives no percentage at all
 * rather than a zero or an infinity, because "unchanged" and "nothing to compare with"
 * are different answers and only one of them is true.
 *
 * Integer arithmetic, rounded half away from zero, so a 0.5% move reads as 1% in the
 * direction it actually went instead of vanishing.
 */
fun percentChange(current: Money, previous: Money): Int? {
    val base = previous.rupiah
    if (base == 0L) return null

    val delta = current.rupiah - base
    val divisor = if (base < 0L) -base else base
    val scaled = delta * 100L
    val magnitude = ((if (scaled < 0L) -scaled else scaled) + divisor / 2) / divisor
    return if (scaled < 0L) (-magnitude).toInt() else magnitude.toInt()
}

/**
 * The range to show after the user picks a different mode.
 *
 * Anchored on today when today falls inside the range being left, and on the range's
 * own start otherwise. Switching Monthly to Weekly while looking at the current month
 * should land on the current week; doing it while looking at March 2024 should land in
 * March 2024, not jump the user back to now.
 *
 * [PeriodMode.CUSTOM] keeps the dates it was given — there is no canonical custom range
 * to derive, so the screen opens its date picker instead.
 */
fun rangeForMode(current: DateRange, mode: PeriodMode, today: LocalDate): DateRange {
    val anchor = if (today in current) today else current.start
    return when (mode) {
        PeriodMode.WEEKLY -> DateRange.ofWeek(anchor)
        PeriodMode.MONTHLY -> DateRange.ofMonth(anchor)
        PeriodMode.ANNUALLY -> DateRange.ofYear(anchor.year)
        PeriodMode.CUSTOM -> DateRange.custom(current.start, current.endInclusive)
    }
}

/**
 * The one line under the period total: how the selected side moved against the previous
 * period of the same length. Null only when there is no previous period at all.
 *
 * "Nothing to compare" covers a previous side total of zero as well as an empty previous
 * period: either way there is no denominator, and "+100%" against nothing is invented.
 */
fun comparisonLine(comparison: PeriodComparison, side: StatsSide): ComparisonLine? {
    val previous = comparison.previousRange ?: return null
    val change = comparison.changePercentFor(side) ?: return ComparisonLine.NothingToCompare(previous)
    return when {
        change < 0 -> ComparisonLine.Less(-change, previous)
        change > 0 -> ComparisonLine.More(change, previous)
        else -> ComparisonLine.Same(previous)
    }
}

/**
 * Far enough back that no imported row predates it, and no further. The daily totals
 * query buckets days with the zone offset at its range start; from 1900 that offset is
 * Jakarta's local mean time, +07:07:12, which put every day's bucket seven minutes into
 * the previous day. Jakarta has been +07:00 since 1964, and the seeded accounts open at
 * the epoch anyway.
 */
private val HISTORY_START = LocalDate(1970, 1, 1)

/**
 * Whether [mode] has a strip. Period has none: a custom span has no natural neighbours
 * to line up beside it.
 */
fun hasStrip(mode: PeriodMode): Boolean = mode != PeriodMode.CUSTOM

/**
 * Far enough forward that no future-dated row is missed. The strip runs three periods
 * past the last one with data, so the query cannot stop at the selected period or at
 * today: it has to reach a row before knowing where that row is. The daily totals query
 * returns only days that hold rows, so an empty future costs nothing.
 */
private val HISTORY_END = LocalDate(2199, 12, 31)

/** How many empty periods the strip shows after the latest of data, today and the selection. */
const val STRIP_FUTURE_PERIODS = 3

/**
 * Everything the strip reads: all of history and every future-dated row, and the
 * selected period too should it lie past [HISTORY_END].
 */
fun stripHistory(selected: DateRange): DateRange =
    DateRange.custom(HISTORY_START, maxOf(HISTORY_END, selected.endInclusive))

/** The week, month or year holding [date], the same ranges the period arrows step. */
fun periodContaining(date: LocalDate, mode: PeriodMode): DateRange = when (mode) {
    PeriodMode.WEEKLY -> DateRange.ofWeek(date)
    PeriodMode.MONTHLY -> DateRange.ofMonth(date)
    PeriodMode.ANNUALLY -> DateRange.ofYear(date.year)
    PeriodMode.CUSTOM -> DateRange.custom(date, date)
}

/**
 * Folds daily totals into one strip column per period of [selected]'s mode. One fold for
 * weeks, months and years: the mode decides only how a day is bucketed and how a column
 * steps.
 *
 * The strip starts at the earlier of the first period with an income or expense and
 * [selected], and ends [STRIP_FUTURE_PERIODS] periods past the latest of the last period
 * with data, the period holding [today], and [selected]. So a past selection still shows
 * everything after it, a future-dated row shows in its own column, and there is always a
 * little future to scroll to.
 *
 * [days] is one filtered daily totals query over [stripHistory], so every bar counts the
 * same rows the header does. Bars share one scale, the top of the [stripAxis] for the
 * largest income or expense figure across every column, as thousandths from `Long`
 * arithmetic; only the drawing makes geometry of them. Scaling to the axis top rather
 * than to the largest figure is what makes the axis labels true.
 *
 * A quiet period inside the strip, future ones included, is a real zero and draws as one.
 * With no history at all, the strip is the selected period alone as an empty slot, so
 * the period the user is on still has a column to read.
 *
 * Transfers and adjustments are already out, as they are from every daily total.
 */
fun foldStrip(days: List<DailyTotals>, selected: DateRange, today: LocalDate): List<StripColumn> {
    if (!hasStrip(selected.mode)) return emptyList()
    val withData = days.filter { !it.isEmpty }
    val first = withData.minOfOrNull { it.date } ?: return listOf(emptySlot(selected))
    val last = withData.maxOf { it.date }
    val mode = selected.mode

    val byPeriod = HashMap<LocalDate, PeriodTotals>()
    for (day in days) {
        val key = periodContaining(day.date, mode).start
        val sum = byPeriod[key] ?: PeriodTotals.EMPTY
        byPeriod[key] = PeriodTotals(sum.income + day.income, sum.expense + day.expense)
    }

    var end = periodContaining(maxOf(last, today, selected.start), mode)
    repeat(STRIP_FUTURE_PERIODS) { end = end.next() }

    val periods = ArrayList<DateRange>()
    var period = periodContaining(minOf(first, selected.start), mode)
    while (period.start <= end.start) {
        periods += period
        period = period.next()
    }
    val totals = periods.map { byPeriod[it.start] ?: PeriodTotals.EMPTY }
    val top = stripAxis(totals.maxOfOrNull { maxOf(it.income, it.expense) } ?: Money.ZERO).top

    return periods.mapIndexed { index, range ->
        val figures = totals[index]
        StripColumn(
            range = range,
            income = figures.income,
            expense = figures.expense,
            incomePermille = barPermille(figures.income, top),
            expensePermille = barPermille(figures.expense, top),
            selected = range == selected,
            empty = false,
        )
    }
}

/** The axis [foldStrip] scaled [columns] against, for drawing its labels and gridlines. */
fun stripAxis(columns: List<StripColumn>): StripAxis =
    stripAxis(columns.maxOfOrNull { maxOf(it.income, it.expense) } ?: Money.ZERO)

/**
 * The Y axis for a strip whose tallest bar is [max]: the smallest round step (1, 2, 2,5 or
 * 5 times a power of ten, at least Rp 1.000) that covers [max] in at most
 * [MAX_AXIS_STEPS] steps, and the top as the first multiple of it at or above [max].
 * `Long` arithmetic only. An empty history still gets one step, so the axis never
 * divides by zero.
 */
internal fun stripAxis(max: Money): StripAxis {
    val target = max.rupiah.coerceAtLeast(1)
    var base = MIN_AXIS_STEP
    while (true) {
        for (step in longArrayOf(base, base * 2, base * 5 / 2, base * 5)) {
            if (step * MAX_AXIS_STEPS >= target) {
                val steps = ((target + step - 1) / step).coerceAtLeast(1)
                return StripAxis(step = Money(step), top = Money(step * steps))
            }
        }
        base *= 10
    }
}

private const val MIN_AXIS_STEP = 1_000L
private const val MAX_AXIS_STEPS = 4L

private fun emptySlot(selected: DateRange) = StripColumn(
    range = selected,
    income = Money.ZERO,
    expense = Money.ZERO,
    incomePermille = 0,
    expensePermille = 0,
    selected = true,
    empty = true,
)
