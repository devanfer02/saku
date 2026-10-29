package com.dvnnfrr.saku.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dvnnfrr.saku.data.repository.AccountRepository
import com.dvnnfrr.saku.data.repository.CategoryRepository
import com.dvnnfrr.saku.data.repository.TransactionRepository
import com.dvnnfrr.saku.domain.DailyTotals
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.PeriodTotals
import com.dvnnfrr.saku.domain.TransactionFilter
import com.dvnnfrr.saku.sakuViewModelFactory
import com.dvnnfrr.saku.ui.filter.SharedFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * State for the transaction screen.
 *
 * Holds only what the user has chosen: the month, the tab, the expanded Monthly month, a
 * pending scroll target. The filter is a choice too, but one Trans shares with Stats, so
 * it lives in [SharedFilter] and is combined into the query keys here. Everything derived
 * comes out of [TransactionListAssembly] and [MonthlyAssembly], which are pure.
 */
class TransactionListViewModel(
    transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    private val sharedFilter: SharedFilter,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
) : ViewModel() {

    /**
     * [range] is the month and stays the anchor in Monthly too: the year arrows step it by
     * twelve months, so going back to Daily shows the same month in the new year.
     */
    private data class Selection(
        val range: DateRange,
        val tab: TransactionTab,
        val pendingScrollTo: LocalDate?,
        val expandedMonth: Month?,
    )

    private val selection = MutableStateFlow(
        Selection(
            range = DateRange.currentMonth(today()),
            tab = TransactionTab.DAILY,
            pendingScrollTo = null,
            expandedMonth = null,
        ),
    )

    private fun today(): LocalDate = clock.now().toLocalDateTime(zone).date

    /**
     * Only the month and the filter drive the query.
     *
     * Keying `flatMapLatest` on the whole selection meant switching tab — which needs
     * no different data — cancelled and re-subscribed all four repository flows, and
     * the segmented control did not move until the round trip landed. On a busy month
     * the tap read as dropped. Tab and scroll target are combined in afterwards, so
     * they never touch the database.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val monthData =
        combine(selection.map { it.range }, sharedFilter.state, ::Pair)
            .distinctUntilChanged()
            .flatMapLatest { (range, filter) ->
                combine(
                    transactionRepository.observeInRange(range, zone, filter),
                    accountRepository.observeAll(),
                    categoryRepository.observeAll(),
                    categoryRepository.observeAllSubcategories(),
                ) { transactions, accounts, categories, subcategories ->
                    val index = DisplayIndex(accounts, categories, subcategories)
                    MonthData(
                        range = range,
                        filter = filter,
                        totals = transactions.periodTotals(),
                        days = transactions.toDaySections(zone, index),
                        calendar = transactions.toCalendarMonth(range, zone),
                    )
                }
            }

    private data class MonthData(
        val range: DateRange,
        val filter: TransactionFilter,
        val totals: PeriodTotals,
        val days: List<DaySection>,
        val calendar: CalendarMonth,
    )

    private data class YearData(val year: Int, val days: List<DailyTotals>)

    /**
     * The Monthly year's day totals, keyed on the year and the filter, and subscribed only
     * while Monthly is open, so Daily and Calendar do not pay for a year they never show.
     * Expanding a month is not part of the key; it is applied in the final combine.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val yearData: Flow<YearData?> =
        combine(
            selection.map { if (it.tab == TransactionTab.MONTHLY) it.range.start.year else null },
            sharedFilter.state,
            ::Pair,
        )
            .distinctUntilChanged()
            .flatMapLatest { (year, filter) ->
                if (year == null) {
                    flowOf(null)
                } else {
                    transactionRepository
                        .observeDailyTotals(monthlyQueryRange(year), zone, filter)
                        .map { YearData(year, it) }
                }
            }

    val state: StateFlow<TransactionListUiState> =
        combine(selection, monthData, yearData) { current, data, year ->
            TransactionListUiState(
                // The month the data is for, not the month just selected — otherwise
                // the header flips before its figures arrive.
                range = data.range,
                tab = current.tab,
                totals = data.totals,
                days = data.days,
                calendar = data.calendar,
                pendingScrollTo = current.pendingScrollTo,
                filter = data.filter,
                monthly = year?.let {
                    assembleMonthlyYear(it.year, it.days, today(), current.expandedMonth)
                },
                loading = false,
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = TransactionListUiState(range = selection.value.range),
            )

    /**
     * The arrows step a month in Daily and Calendar and a year in Monthly. Navigation keeps
     * the selected tab: changing period is browsing, not switching views, and resetting to
     * Daily on every arrow tap made the Calendar unusable for comparing consecutive months.
     */
    fun previousMonth() = step(-1)

    fun nextMonth() = step(1)

    private fun step(direction: Int) = selection.update {
        if (it.tab != TransactionTab.MONTHLY) {
            it.copy(range = if (direction < 0) it.range.previous() else it.range.next())
        } else {
            val range = DateRange.ofMonth(it.range.start.plus(12 * direction, DateTimeUnit.MONTH))
            it.copy(range = range, expandedMonth = openingMonthFor(range))
        }
    }

    /** The current month in the current year, nothing in any other. */
    private fun openingMonthFor(range: DateRange): Month? {
        val today = today()
        return if (range.start.year == today.year) today.month else null
    }

    fun selectTab(tab: TransactionTab) = selection.update {
        when {
            it.tab == tab -> it
            tab == TransactionTab.MONTHLY -> it.copy(tab = tab, expandedMonth = openingMonthFor(it.range))
            else -> it.copy(tab = tab)
        }
    }

    /** Expands [month], or collapses it when it is already the one expanded. */
    fun toggleMonth(month: Month) = selection.update {
        it.copy(expandedMonth = if (it.expandedMonth == month) null else month)
    }

    /**
     * Opens Daily on [date]'s month and asks it to scroll there. A Calendar day tap stays
     * in its month; a Monthly week tap passes the week's last day inside the month it was
     * listed under, which is how a week crossing a month edge opens the right month.
     */
    fun openDay(date: LocalDate) = selection.update {
        it.copy(range = DateRange.ofMonth(date), tab = TransactionTab.DAILY, pendingScrollTo = date)
    }

    /** Called once the list has scrolled, so a recomposition does not scroll again. */
    fun consumeScrollTarget() = selection.update { it.copy(pendingScrollTo = null) }

    fun clearFilter() = sharedFilter.clear()

    companion object {
        val Factory = sakuViewModelFactory {
            TransactionListViewModel(transactions, accounts, categories, filter)
        }
    }
}
