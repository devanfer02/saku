package com.dvnnfrr.saku.domain

import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/** How long an account may go unreconciled before the list flags it. */
val STALE_AFTER: Duration = 30.days

data class Account(
    val id: Long,
    val name: String,
    val type: AccountType,
    val colorSlot: ColorSlot,
    val sortOrder: Int,
    val archived: Boolean,
    val openingBalance: Money,
    val openingAsOf: Instant,
    val lastReconciled: Instant?,
    /** Derived, never stored. See AccountDao.observeBalances. */
    val balance: Money,
) {
    val group: AccountGroup get() = type.group

    /** Accounts drift because entries get logged from memory; 30 days is the nudge. */
    fun isStale(now: Instant, after: Duration = STALE_AFTER): Boolean =
        lastReconciled?.let { now - it > after } ?: true

    val neverReconciled: Boolean get() = lastReconciled == null
}

data class Category(
    val id: Long,
    val name: String,
    val kind: CategoryKind,
    val colorSlot: ColorSlot,
    val sortOrder: Int,
    val archived: Boolean,
)

data class Subcategory(
    val id: Long,
    val categoryId: Long,
    val name: String,
    val sortOrder: Int,
    val archived: Boolean,
)

data class Transaction(
    val id: Long,
    val occurredAt: Instant,
    val type: TxnType,
    val amount: Money,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val subcategoryId: Long? = null,
    val note: String? = null,
    val createdAt: Instant,
    val importBatchId: Long? = null,
)

/** One donut slice plus its ranked-list row. */
data class CategoryTotal(
    val categoryId: Long,
    val name: String,
    val colorSlot: ColorSlot,
    val total: Money,
    /**
     * Share of the period total in basis points, 0..10000. Integer rather than a
     * float so the ranked list's percentages are computed from an exact value and the
     * app keeps its no-floating-point rule.
     */
    val shareBasisPoints: Int,
) {
    /**
     * Rounded half-up, so 0.5% reads as 1% rather than vanishing.
     *
     * Per row, so a column of these does NOT necessarily sum to 100 — six equal
     * slices round to 17% each and the column reads 102%. Use [wholePercents] for a
     * whole list; this is for a figure shown on its own.
     */
    val wholePercent: Int
        get() = sharePercent(Money(shareBasisPoints.toLong()), Money(BASIS_POINTS_WHOLE)) ?: 0

    /** For the donut's arc lengths, where a fraction is what the geometry needs. */
    val shareFraction: Float get() = shareBasisPoints / 10_000f
}

/**
 * Whole percentages for a ranked list, summing to exactly 100.
 *
 * Largest remainder: floor everything, then hand the leftover points to the rows with
 * the largest fractional parts. Rounding each row on its own is what made a column of
 * six equal slices read 102%, which design.md forbids — a chart that does not add up
 * is a chart nobody trusts.
 *
 * Returns percentages positionally matching [totals]. An empty or all-zero list gives
 * back zeros rather than dividing by nothing.
 */
fun wholePercents(totals: List<CategoryTotal>): List<Int> {
    if (totals.isEmpty()) return emptyList()
    val basis = totals.map { it.shareBasisPoints }
    if (basis.sum() == 0) return List(totals.size) { 0 }

    val floors = basis.map { it / 100 }
    val remaining = 100 - floors.sum()
    if (remaining <= 0) return floors

    // Largest fractional part first, ties by original order so the result is stable.
    val order = basis.indices.sortedWith(
        compareByDescending<Int> { basis[it] % 100 }.thenBy { it },
    )
    val result = floors.toMutableList()
    order.take(remaining).forEach { result[it]++ }
    return result
}

private const val BASIS_POINTS_WHOLE = 10_000L

/**
 * [part] as a whole percent of [whole], for one figure shown on its own: the filter
 * sheet's rings, a single category's share.
 *
 * Rounded half-up, so 0.5% reads as 1% rather than vanishing. Null when [whole] is zero,
 * because "0%" and "there is nothing to take a share of" are different statements.
 * Integer arithmetic only. For a column that must sum to 100, use [wholePercents].
 */
fun sharePercent(part: Money, whole: Money): Int? {
    if (whole.isZero) return null
    return ((part.rupiah * 100L + whole.rupiah / 2) / whole.rupiah).toInt()
}

data class PeriodTotals(
    val income: Money,
    val expense: Money,
) {
    val net: Money get() = income - expense

    companion object {
        val EMPTY = PeriodTotals(Money.ZERO, Money.ZERO)
    }
}

data class DailyTotals(
    val date: kotlinx.datetime.LocalDate,
    val income: Money,
    val expense: Money,
) {
    val net: Money get() = income - expense
    val isEmpty: Boolean get() = income.isZero && expense.isZero
}

/**
 * The accounts screen header. [owed] is null while no liability account exists, which
 * is how the Liabilities row stays hidden rather than sitting at zero forever.
 *
 * **Sign convention.** A liability account's stored balance is NEGATIVE when money is
 * owed: spending on a credit card is an EXPENSE against it, which the balance query
 * subtracts, so debt lowers the balance exactly as it lowers net worth. [owed] is the
 * display figure and is therefore POSITIVE — the amount owed — obtained by negating
 * the sum of liability balances.
 *
 * Getting this backwards made net worth rise when debt was taken on: BCA 1.000.000
 * plus 500.000 spent on a card reported a total of 1.500.000.
 */
data class AccountSummary(
    val spendable: Money,
    val invested: Money,
    val owed: Money?,
) {
    val total: Money get() = spendable + invested - (owed ?: Money.ZERO)
}

data class ImportBatch(
    val id: Long,
    val fileName: String,
    val importedAt: Instant,
    val rowCount: Int,
    val insertedCount: Int,
    val skippedCount: Int,
    val reversedAt: Instant? = null,
) {
    val isReversed: Boolean get() = reversedAt != null
}
