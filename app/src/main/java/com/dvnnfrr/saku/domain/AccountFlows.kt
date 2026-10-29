package com.dvnnfrr.saku.domain

/**
 * What moved through one account in a period, for the filter sheet's ACCOUNT tab.
 *
 * Counts every category whatever the filter selects, and excludes adjustments: those
 * correct a balance, they are not money in or out. Transfers are split by leg, so a
 * BCA to RDPU transfer is BCA's [transferOut] and RDPU's [transferIn].
 */
data class AccountFlows(
    val accountId: Long,
    val income: Money,
    val expense: Money,
    val transferIn: Money,
    val transferOut: Money,
) {
    companion object {
        /** An account nothing moved through, which the query returns no row for. */
        fun none(accountId: Long) =
            AccountFlows(accountId, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO)
    }
}
