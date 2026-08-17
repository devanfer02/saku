package com.dvnnfrr.saku.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dvnnfrr.saku.data.repository.AccountRepository
import com.dvnnfrr.saku.data.repository.CategoryRepository
import com.dvnnfrr.saku.data.repository.TransactionRepository
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.PeriodTotals
import com.dvnnfrr.saku.sakuViewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * State for the transaction screen.
 *
 * Holds only what the user has chosen — the month, the tab, a pending scroll target.
 * Everything derived comes out of [TransactionListAssembly], which is pure, so the
 * grouping and subtotal rules are tested without a ViewModel or a database.
 */
class TransactionListViewModel(
    transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    clock: Clock = Clock.System,
) : ViewModel() {

    private data class Selection(
        val range: DateRange,
        val tab: TransactionTab,
        val pendingScrollTo: LocalDate?,
    )

    private val selection = MutableStateFlow(
        Selection(
            range = DateRange.currentMonth(clock.now().toLocalDateTime(zone).date),
            tab = TransactionTab.DAILY,
            pendingScrollTo = null,
        ),
    )

    /**
     * Only the month drives the query.
     *
     * Keying `flatMapLatest` on the whole selection meant switching tab — which needs
     * no different data — cancelled and re-subscribed all four repository flows, and
     * the segmented control did not move until the round trip landed. On a busy month
     * the tap read as dropped. Tab and scroll target are combined in afterwards, so
     * they never touch the database.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val monthData =
        selection
            .map { it.range }
            .distinctUntilChanged()
            .flatMapLatest { range ->
                combine(
                    transactionRepository.observeInRange(range, zone),
                    accountRepository.observeAll(),
                    categoryRepository.observeAll(),
                    categoryRepository.observeAllSubcategories(),
                ) { transactions, accounts, categories, subcategories ->
                    val index = DisplayIndex(accounts, categories, subcategories)
                    MonthData(
                        range = range,
                        totals = transactions.periodTotals(),
                        days = transactions.toDaySections(zone, index),
                        calendar = transactions.toCalendarMonth(range, zone),
                    )
                }
            }

    private data class MonthData(
        val range: DateRange,
        val totals: PeriodTotals,
        val days: List<DaySection>,
        val calendar: CalendarMonth,
    )

    val state: StateFlow<TransactionListUiState> =
        combine(selection, monthData) { current, data ->
            TransactionListUiState(
                // The month the data is for, not the month just selected — otherwise
                // the header flips before its figures arrive.
                range = data.range,
                tab = current.tab,
                totals = data.totals,
                days = data.days,
                calendar = data.calendar,
                pendingScrollTo = current.pendingScrollTo,
                loading = false,
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = TransactionListUiState(range = selection.value.range),
            )

    /**
     * Month navigation keeps the selected tab. Changing months is browsing, not
     * switching views, and resetting to Daily on every arrow tap made the Calendar
     * unusable for comparing consecutive months.
     */
    fun previousMonth() = selection.update { it.copy(range = it.range.previous()) }

    fun nextMonth() = selection.update { it.copy(range = it.range.next()) }

    fun selectTab(tab: TransactionTab) = selection.update { it.copy(tab = tab) }

    /** Calendar day tap: switch to Daily and ask it to scroll to that day. */
    fun openDay(date: LocalDate) = selection.update {
        it.copy(tab = TransactionTab.DAILY, pendingScrollTo = date)
    }

    /** Called once the list has scrolled, so a recomposition does not scroll again. */
    fun consumeScrollTarget() = selection.update { it.copy(pendingScrollTo = null) }

    companion object {
        val Factory = sakuViewModelFactory {
            TransactionListViewModel(transactions, accounts, categories)
        }
    }
}
