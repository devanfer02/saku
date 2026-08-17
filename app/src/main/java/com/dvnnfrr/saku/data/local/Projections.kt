package com.dvnnfrr.saku.data.local

/**
 * An account with its derived balance.
 *
 * Carries the real `openingBalance` and `openingAsOfEpochSec` rather than letting the
 * mapper invent placeholders. An earlier version filled them with `Money.ZERO` and
 * `Instant.DISTANT_PAST`, which meant `byId()` and `observeAll()` returned different
 * values for the same account with nothing marking which one was true — an edit sheet
 * prefilled from the list would have silently destroyed the real opening balance.
 */
data class AccountBalanceRow(
    val id: Long,
    val name: String,
    val type: String,
    val colorSlot: Int,
    val sortOrder: Int,
    val archived: Boolean,
    val openingBalance: Long,
    val openingAsOfEpochSec: Long,
    val lastReconciledEpochSec: Long?,
    val balance: Long,
)

/** One slice of the stats donut and one row of the ranked list beneath it. */
data class CategoryTotalRow(
    val categoryId: Long,
    val categoryName: String,
    val colorSlot: Int,
    val total: Long,
)

/** Per-day income and expense subtotals for the Daily headers and Calendar cells. */
data class DailyTotalRow(
    val dayEpochSec: Long,
    val income: Long,
    val expense: Long,
)

/** Income and expense for a whole period, for the list header and stats tabs. */
data class PeriodTotalsRow(
    val income: Long,
    val expense: Long,
) {
    val net: Long get() = income - expense
}

/** A grouped `COUNT(*)` keyed by id, for the per-row counts the category screen shows. */
data class IdCount(
    val id: Long,
    val count: Int,
)
