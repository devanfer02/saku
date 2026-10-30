package com.dvnnfrr.saku.ui.filter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dvnnfrr.saku.data.repository.AccountRepository
import com.dvnnfrr.saku.data.repository.CategoryRepository
import com.dvnnfrr.saku.data.repository.TransactionRepository
import com.dvnnfrr.saku.domain.Account
import com.dvnnfrr.saku.domain.AccountFlows
import com.dvnnfrr.saku.domain.Category
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.PeriodTotals
import com.dvnnfrr.saku.domain.TransactionFilter
import com.dvnnfrr.saku.sakuViewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone

/**
 * State for the filter sheet.
 *
 * Built with the opening screen's [range] and holds only the open tab. The filter itself
 * lives in [SharedFilter]: every tick writes straight to it, so the screen behind the sheet
 * updates while the user is still ticking. There is no draft copy and no Apply button,
 * which is what keeps a swiped-away sheet from silently losing the user's choices.
 */
class FilterSheetViewModel(
    private val range: DateRange,
    transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    private val sharedFilter: SharedFilter,
    zone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val tab = MutableStateFlow(FilterTab.INCOME)

    /** The filtered totals travel with the filter they were computed under. */
    private data class Filtered(val filter: TransactionFilter, val totals: PeriodTotals)

    private data class Listed(
        val whole: PeriodTotals,
        val flows: List<AccountFlows>,
        val income: List<Category>,
        val expense: List<Category>,
        val accounts: List<Account>,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val filtered = sharedFilter.state.flatMapLatest { filter ->
        transactionRepository.observePeriodTotals(range, zone, filter).map { Filtered(filter, it) }
    }

    private val listed = combine(
        transactionRepository.observePeriodTotals(range, zone, TransactionFilter.ALL),
        transactionRepository.observeAccountFlows(range, zone),
        categoryRepository.observeActiveByKind(CategoryKind.INCOME),
        categoryRepository.observeActiveByKind(CategoryKind.EXPENSE),
        accountRepository.observeActive(),
        ::Listed,
    )

    val state: StateFlow<FilterSheetUiState> =
        combine(tab, filtered, listed) { tab, filtered, listed ->
            assembleFilterSheet(
                range = range,
                tab = tab,
                filter = filtered.filter,
                filtered = filtered.totals,
                whole = listed.whole,
                flows = listed.flows,
                incomeCategories = listed.income,
                expenseCategories = listed.expense,
                accounts = listed.accounts,
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = FilterSheetUiState(range = range),
            )

    fun selectTab(tab: FilterTab) {
        this.tab.value = tab
    }

    /** Ticks or unticks [id] on the open tab, applying at once. */
    fun toggle(id: Long) {
        val open = tab.value
        sharedFilter.set(sharedFilter.state.value.toggled(open, id, listedIds(open)))
    }

    /** The open tab's "All" row. */
    fun toggleAll() {
        sharedFilter.set(sharedFilter.state.value.toggledAll(tab.value))
    }

    private fun listedIds(tab: FilterTab): List<Long> {
        val current = state.value
        return when (tab) {
            FilterTab.INCOME -> current.incomeRows.map { it.id }
            FilterTab.EXPENSES -> current.expenseRows.map { it.id }
            FilterTab.ACCOUNT -> current.accountGroups.flatMap { group -> group.rows.map { it.id } }
        }
    }

    companion object {
        fun factory(range: DateRange) = sakuViewModelFactory {
            FilterSheetViewModel(range, transactions, accounts, categories, filter)
        }
    }
}
