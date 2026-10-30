package com.dvnnfrr.saku.ui.stats

import com.dvnnfrr.saku.domain.ChartStyle
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.PeriodTotals
import com.dvnnfrr.saku.domain.TransactionFilter
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.ui.transactions.TransactionRowUi
import kotlinx.datetime.LocalDate

/**
 * Which side of the ledger the headline and the ranked bars are reporting on.
 *
 * Expenses is first, and therefore the default, because that is the side the owner
 * looks at: a year of reference data holds one income category and sixteen
 * expense ones.
 */
enum class StatsSide(val txnType: TxnType) {
    EXPENSES(TxnType.EXPENSE),
    INCOME(TxnType.INCOME),
}

fun PeriodTotals.forSide(side: StatsSide): Money = when (side) {
    StatsSide.EXPENSES -> expense
    StatsSide.INCOME -> income
}

/**
 * Everything the stats screen draws, already resolved.
 *
 * [range] is the period the figures are FOR, never the one just tapped. It is carried
 * out of the query rather than out of the user's selection, so the header cannot name
 * September while still showing August's bars.
 */
data class StatsUiState(
    val range: DateRange,
    val side: StatsSide = StatsSide.EXPENSES,
    val totals: PeriodTotals = PeriodTotals.EMPTY,
    val comparison: PeriodComparison = PeriodComparison.EMPTY,
    val composition: RankedComposition = RankedComposition.EMPTY,
    /**
     * Every week, month or year from the first with data to three past the latest of the
     * last with data, today's and the selected one. Empty in Period mode.
     */
    val strip: List<StripColumn> = emptyList(),
    /** Whether the strip shows its bars or only its header line. */
    val stripExpanded: Boolean = true,
    /** Ranked bars, or a pie above them. Remembered across restarts. */
    val chartStyle: ChartStyle = ChartStyle.BARS,
    /** Null while the overview is showing; set once the user drills into a slice. */
    val drill: DrillUiState? = null,
    /** The filter the figures were computed under, for the bar and the action. */
    val filter: TransactionFilter = TransactionFilter.ALL,
    val loading: Boolean = true,
    /**
     * The device's date, from the ViewModel's clock, so the period picker can mark the
     * current period and offer "This month" without a composable reading a clock. The
     * default serves previews only; the ViewModel always sets it.
     */
    val today: LocalDate = range.start,
) {
    /** The headline figure, and the whole the shares are taken against. */
    val sideTotal: Money get() = totals.forSide(side)

    val comparisonLine: ComparisonLine? get() = comparisonLine(comparison, side)
}

/** What the user drilled into. Used as a key, so it holds ids rather than rows. */
sealed interface DrillTarget {
    data class Category(val id: Long) : DrillTarget

    /** The aggregated ninth slice, which is not a category and has no transactions. */
    data object Other : DrillTarget
}

/**
 * The drill-through, resolved.
 *
 * Two shapes, because the spec asks for two different things. A category opens its
 * summary, strip and transactions; "Other" opens the categories it combines and
 * no transaction list at all; those are separate figures and merging them would show a
 * total nobody asked for.
 */
sealed interface DrillUiState {
    val title: String?
    val total: Money

    /** One category, for [range]. */
    data class Category(
        val categoryId: Long,
        override val title: String?,
        val colorSlot: ColorSlot,
        override val total: Money,
        /** Whole percent of the side total, exactly as the ranked row that opened it. */
        val percent: Int = 0,
        val count: Int = 0,
        val average: Money = Money.ZERO,
        /** This category's own strip across all history. Empty in Period mode. */
        val strip: List<StripColumn> = emptyList(),
        val rows: List<TransactionRowUi>,
    ) : DrillUiState

    /** The constituents of the "Other" aggregate, each still its own figure. */
    data class Other(
        override val total: Money,
        val members: List<RankedCategory>,
    ) : DrillUiState {
        override val title: String? get() = null
    }
}

/**
 * One row of the ranked list.
 *
 * [categoryId] and [name] are null on the aggregated "Other" row. Colour is identity
 * and a slot is pinned to a category for life, so "Other" cannot borrow one; it takes
 * the neutral chart colour, and its copy comes from the screen rather than from here,
 * which is what keeps this file free of Android.
 */
data class RankedCategory(
    val categoryId: Long?,
    val name: String?,
    val colorSlot: ColorSlot?,
    val total: Money,
    val percent: Int,
    /** Bar length in thousandths of the largest row in the same list. */
    val barPermille: Int = 0,
    /** Pie slice in tenths of a degree, from `pieSweeps` over the same list. */
    val sweepTenths: Int = 0,
) {
    val isOther: Boolean get() = categoryId == null
}

/**
 * The ranked list for one side of one period.
 *
 * [rows] is at most [CHART_SLOT_CAP] categories plus an "Other" row; [otherMembers] is
 * what that row combines, kept so the drill can list them without a second query.
 */
data class RankedComposition(
    val rows: List<RankedCategory>,
    val otherMembers: List<RankedCategory>,
    val total: Money,
) {
    val isEmpty: Boolean get() = rows.isEmpty()

    val other: RankedCategory? get() = rows.lastOrNull()?.takeIf { it.isOther }

    companion object {
        val EMPTY = RankedComposition(emptyList(), emptyList(), Money.ZERO)
    }
}

/** The line under the period total, resolved; the screen supplies the words. */
sealed interface ComparisonLine {
    val previousRange: DateRange

    data class Less(val percent: Int, override val previousRange: DateRange) : ComparisonLine

    data class More(val percent: Int, override val previousRange: DateRange) : ComparisonLine

    data class Same(override val previousRange: DateRange) : ComparisonLine

    data class NothingToCompare(override val previousRange: DateRange) : ComparisonLine
}

/**
 * One week, month or year of the strip, whichever the period mode steps by.
 *
 * [empty] marks the lone slot shown when there is no history at all, drawn as a slot
 * rather than as two zero bars. A quiet or future period inside the strip is a real zero. The bar lengths are thousandths of the
 * strip's axis top, shared across its whole history.
 */
data class StripColumn(
    val range: DateRange,
    val income: Money,
    val expense: Money,
    val incomePermille: Int,
    val expensePermille: Int,
    val selected: Boolean,
    val empty: Boolean,
) {
    val net: Money get() = income - expense

    val totals: PeriodTotals get() = PeriodTotals(income, expense)
}

/** A strip's Y axis: gridlines at every [step] from zero up to [top], which is where bars end at full height. */
data class StripAxis(val step: Money, val top: Money) {
    val ticks: List<Money> get() = (0..top.rupiah / step.rupiah).map { Money(it * step.rupiah) }
}

/**
 * This period against the one before it.
 *
 * The percentages are null rather than zero when the previous period held nothing:
 * "up 0%" and "there is nothing to compare against" are different statements, and
 * dividing by an empty period is where the second becomes the first.
 */
data class PeriodComparison(
    val previousRange: DateRange?,
    val previous: PeriodTotals,
    val current: PeriodTotals,
) {
    val incomeDelta: Money get() = current.income - previous.income

    val expenseDelta: Money get() = current.expense - previous.expense

    val netDelta: Money get() = current.net - previous.net

    val incomeChangePercent: Int? get() = percentChange(current.income, previous.income)

    val expenseChangePercent: Int? get() = percentChange(current.expense, previous.expense)

    fun deltaFor(side: StatsSide): Money = when (side) {
        StatsSide.EXPENSES -> expenseDelta
        StatsSide.INCOME -> incomeDelta
    }

    fun changePercentFor(side: StatsSide): Int? = when (side) {
        StatsSide.EXPENSES -> expenseChangePercent
        StatsSide.INCOME -> incomeChangePercent
    }

    /** True when the previous period held nothing on either side. */
    val hasBaseline: Boolean get() = !previous.income.isZero || !previous.expense.isZero

    companion object {
        val EMPTY = PeriodComparison(null, PeriodTotals.EMPTY, PeriodTotals.EMPTY)
    }
}
