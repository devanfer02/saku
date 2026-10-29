package com.dvnnfrr.saku.domain

/**
 * Which transactions Trans, Stats and search are showing.
 *
 * Each set is either null, meaning "All", or the explicit ids the user ticked. An empty
 * set is a real choice, "none", and hides that whole side. "All" is null rather than the
 * full set of current ids so that archived items and items created later stay included;
 * a snapshot of ids would silently drop a category the moment it was added.
 *
 * Transfers and adjustments carry no category, so they pass only while both category
 * selections are "All". A transfer passes the account selection on either leg. The one
 * implementation of that rule is `TXN_FILTER_WHERE` in `TxnDao`.
 */
data class TransactionFilter(
    val incomeCategoryIds: Set<Long>? = null,
    val expenseCategoryIds: Set<Long>? = null,
    val accountIds: Set<Long>? = null,
) {
    /** True while any tab is narrowed, which is when every screen must say so. */
    val isActive: Boolean
        get() = incomeCategoryIds != null || expenseCategoryIds != null || accountIds != null

    companion object {
        val ALL = TransactionFilter()
    }
}
