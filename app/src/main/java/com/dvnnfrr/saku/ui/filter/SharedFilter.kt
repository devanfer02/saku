package com.dvnnfrr.saku.ui.filter

import com.dvnnfrr.saku.domain.TransactionFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one filter Trans, Stats and search all read.
 *
 * Built once in `AppContainer` and handed to each ViewModel, which combines [state] into
 * its query key the same way it combines its own selection. One holder rather than a copy
 * per screen, because two copies of one choice kept in step by UI wiring is exactly how a
 * filtered figure ends up read as a whole one.
 *
 * Memory only. A new process starts at [TransactionFilter.ALL]: a finance app reopened days
 * later showing filtered totals is the misreading this whole feature has to prevent.
 */
class SharedFilter(initial: TransactionFilter = TransactionFilter.ALL) {

    private val current = MutableStateFlow(initial)

    val state: StateFlow<TransactionFilter> = current.asStateFlow()

    fun set(filter: TransactionFilter) {
        current.value = filter
    }

    fun clear() = set(TransactionFilter.ALL)
}
