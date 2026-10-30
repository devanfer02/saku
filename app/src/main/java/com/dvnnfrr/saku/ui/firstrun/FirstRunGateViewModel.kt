package com.dvnnfrr.saku.ui.firstrun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dvnnfrr.saku.data.FirstRunStore
import com.dvnnfrr.saku.data.repository.TransactionRepository
import com.dvnnfrr.saku.sakuViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the app root shows. */
enum class FirstRunGate {
    /** The one database read before either is known. */
    CHECKING,

    /** The welcome screen and its paths, instead of the shell. */
    OFFER,

    /** The shell. */
    CLOSED,
}

/**
 * Decides, once per process, whether this launch shows first-run setup, and closes it.
 *
 * Decided once rather than observed: an import that lands while the welcome flow is open
 * must not pull the screen away mid-flow. The flow says when it is finished, through
 * [complete], [skip] or [onImportLeft].
 */
class FirstRunGateViewModel(
    private val store: FirstRunStore,
    private val transactions: TransactionRepository,
) : ViewModel() {

    private val _gate = MutableStateFlow(FirstRunGate.CHECKING)
    val gate: StateFlow<FirstRunGate> = _gate.asStateFlow()

    init {
        viewModelScope.launch {
            val offer = shouldOfferFirstRun(
                completed = store.isComplete(),
                ledgerHasRows = transactions.hasAny(),
            )
            _gate.value = if (offer) FirstRunGate.OFFER else FirstRunGate.CLOSED
        }
    }

    /** Skip writes nothing; it only records that the question was answered. */
    fun skip() = complete()

    fun complete() {
        store.markComplete()
        _gate.value = FirstRunGate.CLOSED
    }

    /**
     * The user left an import, restore or database-import screen. Rows in the ledger now
     * mean that path finished; none mean they backed out, and the welcome screen stays.
     * Restore and database import report nothing on their own, and "the ledger has rows"
     * is the fact that matters whichever screen produced them.
     */
    fun onImportLeft() {
        viewModelScope.launch {
            if (transactions.hasAny()) complete()
        }
    }

    companion object {
        val Factory = sakuViewModelFactory { FirstRunGateViewModel(firstRun, transactions) }
    }
}
