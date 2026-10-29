package com.dvnnfrr.saku.ui.stats

import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.PeriodTotals
import com.dvnnfrr.saku.domain.TransactionFilter
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.ui.transactions.TransactionRowUi

/**
 * Which side of the ledger the donut and the ranked list are reporting on.
 *
 * Expenses is first, and therefore the default, because that is the side the owner
 * looks at — a year of reference data holds one income category and sixteen
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
 * September while still showing August's donut.
 */
data class StatsUiState(
    val range: DateRange,
    val side: StatsSide = StatsSide.EXPENSES,
    val totals: PeriodTotals = PeriodTotals.EMPTY,
    val comparison: PeriodComparison = PeriodComparison.EMPTY,
    val composition: RankedComposition = RankedComposition.EMPTY,
    /** Null while the overview is showing; set once the user drills into a slice. */
    val drill: DrillUiState? = null,
    /** The filter the figures were computed under, for the bar and the action. */
    val filter: TransactionFilter = TransactionFilter.ALL,
    val loading: Boolean = true,
) {
    /** The figure the donut carries in its centre and the shares are taken against. */
    val sideTotal: Money get() = totals.forSide(side)
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
 * subcategory split and its transactions; "Other" opens the categories it combines and
 * no transaction list at all — those are separate figures and merging them would show a
 * total nobody asked for.
 */
sealed interface DrillUiState {
    val title: String?
    val total: Money

    /**
     * One category, for [range].
     *
     * [subcategories] is the reason this screen exists. The old app made subcategory
     * skippable and all 1,200 exported rows left it blank, so the unassigned bucket is
     * carried as a first-class slice rather than dropped — seeing how much of Pemberian
     * is untagged is the finding.
     */
    data class Category(
        val categoryId: Long,
        override val title: String?,
        val colorSlot: ColorSlot,
        override val total: Money,
        val subcategories: List<SubcategorySlice>,
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
 * One row of the ranked list and one segment of the donut.
 *
 * [categoryId] and [name] are null on the aggregated "Other" row. Colour is identity
 * and a slot is pinned to a category for life, so "Other" cannot borrow one — it takes
 * the neutral chart colour, and its copy comes from the screen rather than from here,
 * which is what keeps this file free of Android.
 */
data class RankedCategory(
    val categoryId: Long?,
    val name: String?,
    val colorSlot: ColorSlot?,
    val total: Money,
    val percent: Int,
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

/**
 * One subcategory's share of its parent category.
 *
 * [subcategoryId] is null for the bucket of transactions that carry no subcategory at
 * all. That bucket is shown, not hidden: it is the measure of how much of a category is
 * still untagged.
 */
data class SubcategorySlice(
    val subcategoryId: Long?,
    val name: String?,
    val total: Money,
    val percent: Int,
) {
    val isUnassigned: Boolean get() = subcategoryId == null
}

/**
 * This period against the one before it.
 *
 * The percentages are null rather than zero when the previous period held nothing —
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
