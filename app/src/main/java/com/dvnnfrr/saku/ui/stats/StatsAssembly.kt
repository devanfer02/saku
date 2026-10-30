package com.dvnnfrr.saku.ui.stats

import com.dvnnfrr.saku.domain.CategoryTotal
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.DailyTotals
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.PeriodMode
import com.dvnnfrr.saku.domain.Subcategory
import com.dvnnfrr.saku.domain.Transaction
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
 * rows to total them. Those figures arrive from the repository's SQL aggregates. The one
 * fold that survives is the subcategory split, which is scoped to a single category in a
 * single period — rows the drill-through has already loaded to render anyway.
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
    return map { it.copy(barPermille = barPermille(it.total, max)) }
}

/**
 * Splits one category's transactions by subcategory, largest first.
 *
 * Transactions with no subcategory collect into a single bucket carrying a null id.
 * Dropping them would be the same mistake the old app made: subcategory was optional
 * there and every one of 1,200 rows left it blank, so a screen that only charts the
 * tagged rows reports a category as fully split when none of it is.
 *
 * Archived subcategories keep their names — archiving does not rewrite the history that
 * references them, so excluding them would blank the label on real rows.
 */
fun foldSubcategories(
    transactions: List<Transaction>,
    subcategories: List<Subcategory>,
    slot: ColorSlot,
): List<SubcategorySlice> {
    if (transactions.isEmpty()) return emptyList()

    val names = subcategories.associate { it.id to it.name }
    val sums = LinkedHashMap<Long?, Long>()
    for (txn in transactions) {
        sums[txn.subcategoryId] = (sums[txn.subcategoryId] ?: 0L) + txn.amount.rupiah
    }

    // Named slices before the unassigned bucket on an exact tie, then by name, so the
    // order does not depend on which row the database happened to return first.
    val ordered = sums.entries.sortedWith(
        compareByDescending<Map.Entry<Long?, Long>> { it.value }
            .thenBy { it.key == null }
            .thenBy { names[it.key].orEmpty() },
    )

    val total = ordered.sumOf { it.value }
    // A category whose rows cancel to zero — possible only through a signed adjustment
    // — would otherwise divide by nothing here.
    val asTotals = ordered.map { (id, amount) ->
        CategoryTotal(
            categoryId = id ?: 0L,
            name = names[id].orEmpty(),
            colorSlot = slot,
            total = Money(amount),
            shareBasisPoints = if (total == 0L) 0 else ((amount * 10_000L) / total).toInt(),
        )
    }
    val percents = wholePercents(asTotals)
    val largest = Money(ordered.maxOf { it.value })

    return ordered.mapIndexed { index, (id, amount) ->
        SubcategorySlice(
            subcategoryId = id,
            name = id?.let { names[it] },
            total = Money(amount),
            percent = percents[index],
            barPermille = barPermille(Money(amount), largest),
        )
    }
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
 * Everything up to the end of [selected], which is what the strip reads. The strip ends
 * on the selected period, so nothing after it is queried.
 */
fun stripHistory(selected: DateRange): DateRange =
    DateRange.custom(HISTORY_START, selected.endInclusive)

/** The week, month or year holding [date], the same ranges the period arrows step. */
fun periodContaining(date: LocalDate, mode: PeriodMode): DateRange = when (mode) {
    PeriodMode.WEEKLY -> DateRange.ofWeek(date)
    PeriodMode.MONTHLY -> DateRange.ofMonth(date)
    PeriodMode.ANNUALLY -> DateRange.ofYear(date.year)
    PeriodMode.CUSTOM -> DateRange.custom(date, date)
}

/**
 * Folds daily totals into one strip column per period of [selected]'s mode, from the
 * period of the first income or expense through [selected]. One fold for weeks, months
 * and years: the mode decides only how a day is bucketed and how a column steps.
 *
 * [days] is one filtered daily totals query over [stripHistory], so the first column is
 * the first period anything passing the filter happened, and every bar counts the same
 * rows the header does. Bars share one scale, the largest income or expense figure
 * across the whole history, as thousandths from `Long` arithmetic; only the drawing
 * makes geometry of them.
 *
 * A quiet period after history began is a real zero and draws as one. With no history
 * up to [selected] at all, the strip is the selected period alone as an empty slot, so
 * the period the user is on still has a column to read.
 *
 * Transfers and adjustments are already out, as they are from every daily total.
 */
fun foldStrip(days: List<DailyTotals>, selected: DateRange): List<StripColumn> {
    if (!hasStrip(selected.mode)) return emptyList()
    val first = days.filter { !it.isEmpty && it.date <= selected.endInclusive }.minOfOrNull { it.date }
        ?: return listOf(emptySlot(selected))

    val byPeriod = HashMap<LocalDate, PeriodTotals>()
    for (day in days) {
        val key = periodContaining(day.date, selected.mode).start
        val sum = byPeriod[key] ?: PeriodTotals.EMPTY
        byPeriod[key] = PeriodTotals(sum.income + day.income, sum.expense + day.expense)
    }

    val periods = ArrayList<DateRange>()
    var period = periodContaining(first, selected.mode)
    while (period.start <= selected.start) {
        periods += period
        period = period.next()
    }
    val totals = periods.map { byPeriod[it.start] ?: PeriodTotals.EMPTY }
    val max = totals.maxOfOrNull { maxOf(it.income, it.expense) } ?: Money.ZERO

    return periods.mapIndexed { index, range ->
        val figures = totals[index]
        StripColumn(
            range = range,
            income = figures.income,
            expense = figures.expense,
            incomePermille = barPermille(figures.income, max),
            expensePermille = barPermille(figures.expense, max),
            selected = range == selected,
            empty = false,
        )
    }
}

private fun emptySlot(selected: DateRange) = StripColumn(
    range = selected,
    income = Money.ZERO,
    expense = Money.ZERO,
    incomePermille = 0,
    expensePermille = 0,
    selected = true,
    empty = true,
)
