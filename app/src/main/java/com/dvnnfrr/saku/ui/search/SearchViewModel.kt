package com.dvnnfrr.saku.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dvnnfrr.saku.data.repository.AccountRepository
import com.dvnnfrr.saku.data.repository.CategoryRepository
import com.dvnnfrr.saku.data.repository.TransactionRepository
import com.dvnnfrr.saku.domain.TransactionFilter
import com.dvnnfrr.saku.sakuViewModelFactory
import com.dvnnfrr.saku.ui.filter.SharedFilter
import com.dvnnfrr.saku.ui.transactions.DisplayIndex
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone

/** How long typing must pause before the query is matched. */
internal const val SEARCH_DEBOUNCE_MS = 150L

/**
 * State for search. Holds only the query text; the filter comes from [SharedFilter].
 *
 * The query is matched after a short pause so a fast typist does not re-match every
 * keystroke, but [query] itself is exposed undebounced for the text field. Notes are
 * folded once per database emission, not once per keystroke. A second, unfiltered
 * stream runs only while a filter is active, to count the matches it hides.
 */
class SearchViewModel(
    transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    private val sharedFilter: SharedFilter,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val text = MutableStateFlow("")

    /** What the field shows, as typed. */
    val query: StateFlow<String> = text.asStateFlow()

    private data class Sources(
        val filter: TransactionFilter,
        val filtered: List<Searchable>,
        val unfiltered: List<Searchable>?,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val sources = sharedFilter.state.flatMapLatest { filter ->
        val filtered = transactionRepository.observeAll(filter).map { it.searchable() }
        val unfiltered = if (filter.isActive) {
            transactionRepository.observeAll(TransactionFilter.ALL).map { it.searchable() }
        } else {
            flowOf(null)
        }
        combine(filtered, unfiltered) { a, b -> Sources(filter, a, b) }
    }

    private val index = combine(
        accountRepository.observeAll(),
        categoryRepository.observeAll(),
        ::DisplayIndex,
    )

    @OptIn(FlowPreview::class)
    val state: StateFlow<SearchUiState> =
        combine(text.debounce(SEARCH_DEBOUNCE_MS).distinctUntilChanged(), sources, index) { q, src, idx ->
            assembleSearch(q, src.filter, src.filtered, src.unfiltered, zone, idx)
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SearchUiState(),
            )

    fun setQuery(value: String) {
        text.value = value
    }

    fun clearFilter() = sharedFilter.clear()

    companion object {
        val Factory = sakuViewModelFactory {
            SearchViewModel(transactions, accounts, categories, filter)
        }
    }
}
