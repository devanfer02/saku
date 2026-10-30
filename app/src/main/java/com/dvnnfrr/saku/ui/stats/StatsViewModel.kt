package com.dvnnfrr.saku.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dvnnfrr.saku.data.ChartStyleStore
import com.dvnnfrr.saku.data.repository.AccountRepository
import com.dvnnfrr.saku.data.repository.CategoryRepository
import com.dvnnfrr.saku.data.repository.TransactionRepository
import com.dvnnfrr.saku.domain.ChartStyle
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.PeriodMode
import com.dvnnfrr.saku.domain.PeriodTotals
import com.dvnnfrr.saku.sakuViewModelFactory
import com.dvnnfrr.saku.ui.filter.SharedFilter
import com.dvnnfrr.saku.ui.transactions.DisplayIndex
import com.dvnnfrr.saku.ui.transactions.toRowUi
import com.dvnnfrr.saku.domain.TransactionFilter
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
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * State for the stats screen.
 *
 * Holds only what the user has chosen — the period, the side, what they drilled into.
 * Every figure is a SQL aggregate and every fold is in [StatsAssembly], which is pure,
 * so the cap, the ranking and the shares are tested without a ViewModel or a database.
 */
class StatsViewModel(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val sharedFilter: SharedFilter,
    private val chartStyleStore: ChartStyleStore,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private data class Selection(
        val range: DateRange,
        val side: StatsSide,
        val drill: DrillTarget?,
    )

    private val selection = MutableStateFlow(
        Selection(
            range = DateRange.currentMonth(today()),
            side = StatsSide.EXPENSES,
            drill = null,
        ),
    )

    /** Kept apart from [selection] so collapsing the strip never re-runs a query. */
    private val stripExpanded = MutableStateFlow(true)

    private fun today(): LocalDate = clock.now().toLocalDateTime(zone).date

    /**
     * The period, the side and the filter, which is all the overview's three queries
     * depend on. The filter is in the key so the comparison runs under it too: August
     * against July compares like with like.
     */
    private data class PeriodKey(
        val range: DateRange,
        val side: StatsSide,
        val filter: TransactionFilter,
    )

    private data class PeriodData(
        val key: PeriodKey,
        val totals: PeriodTotals,
        val comparison: PeriodComparison,
        val composition: RankedComposition,
    )

    /**
     * Drilling in or out must not re-run the period queries, so the drill is keyed
     * separately — the same reason the transaction list keys its month data on the month
     * alone. It carries its own [key] so the final combine can tell whether the drill it
     * is holding belongs to the period currently on screen; the two streams settle
     * independently and would otherwise cross for one frame after an arrow tap.
     */
    private data class DrillData(val key: PeriodKey, val state: DrillUiState?)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val periodData: Flow<PeriodData> =
        combine(selection, sharedFilter.state) { current, filter ->
            PeriodKey(current.range, current.side, filter)
        }
            .distinctUntilChanged()
            .flatMapLatest { key ->
                val previous = key.range.previous()
                combine(
                    transactionRepository.observePeriodTotals(key.range, zone, key.filter),
                    transactionRepository.observePeriodTotals(previous, zone, key.filter),
                    transactionRepository.observeCategoryTotals(key.range, zone, key.side.txnType, key.filter),
                ) { totals, previousTotals, categoryTotals ->
                    PeriodData(
                        key = key,
                        totals = totals,
                        comparison = PeriodComparison(previous, previousTotals, totals),
                        composition = rankComposition(categoryTotals),
                    )
                }
            }

    /**
     * The strip, keyed on the period and the filter and queried in every mode but
     * Period. One filtered daily totals query from the start of history to the end of
     * the selected period, folded into weeks, months or years in [foldStrip]: one
     * aggregate instead of one per column, which over sixteen months of history would be
     * seventeen live queries (seventy weeks' worth in Weekly) all re-running on every
     * write. Switching side does not touch it: the strip shows both.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val stripData: Flow<List<StripColumn>> =
        combine(selection, sharedFilter.state) { current, filter ->
            current.range.takeIf { hasStrip(it.mode) }?.let { it to filter }
        }
            .distinctUntilChanged()
            .flatMapLatest { key ->
                if (key == null) return@flatMapLatest flowOf(emptyList())
                val (range, filter) = key
                transactionRepository.observeDailyTotals(stripHistory(range), zone, filter)
                    .map { days -> foldStrip(days, range) }
            }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val drillData: Flow<DrillData> =
        combine(selection, sharedFilter.state, ::Pair)
            .distinctUntilChanged()
            .flatMapLatest { (current, filter) ->
                val key = PeriodKey(current.range, current.side, filter)
                when (val target = current.drill) {
                    // "Other" needs no query of its own: the categories it combines are
                    // already in the composition the overview loaded.
                    null, DrillTarget.Other -> flowOf(DrillData(key, null))
                    is DrillTarget.Category -> categoryDrill(key, target.id)
                }
            }

    /**
     * One category: its rows for the period, and its own strip across all history. The
     * strip is the overview's daily totals query under the filter narrowed to this
     * category, so it needs no query of its own kind and follows the period mode as the
     * overview's strip does.
     */
    private fun categoryDrill(key: PeriodKey, categoryId: Long): Flow<DrillData> = combine(
        transactionRepository.observeForCategory(key.range, zone, categoryId, key.side.txnType, key.filter),
        categoryRepository.observeAll(),
        accountRepository.observeAll(),
        categoryStrip(key, categoryId),
    ) { transactions, categories, accounts, strip ->
        val category = categories.firstOrNull { it.id == categoryId }
        val slot = category?.colorSlot ?: ColorSlot.FIRST
        val index = DisplayIndex(accounts = accounts, categories = categories)
        // Summed from the rows the list is rendering anyway. Bounded to one category in
        // one period, so this is not the memory fold the screen avoids elsewhere, and it
        // cannot disagree with the list beneath it.
        val total = Money(transactions.sumOf { it.amount.rupiah })
        DrillData(
            key = key,
            state = DrillUiState.Category(
                categoryId = categoryId,
                title = category?.name,
                colorSlot = slot,
                total = total,
                count = transactions.size,
                average = averagePerTransaction(total, transactions.size),
                strip = strip,
                rows = transactions.map { it.toRowUi(index) },
            ),
        )
    }

    private fun categoryStrip(key: PeriodKey, categoryId: Long): Flow<List<StripColumn>> {
        if (!hasStrip(key.range.mode)) return flowOf(emptyList())
        val filter = categoryFilter(key.filter, key.side, categoryId)
        return transactionRepository.observeDailyTotals(stripHistory(key.range), zone, filter)
            .map { days -> foldStrip(days, key.range) }
    }

    /** The two display choices, kept apart from [selection] so neither re-runs a query. */
    private val display: Flow<Pair<Boolean, ChartStyle>> =
        combine(stripExpanded, chartStyleStore.style, ::Pair)

    val state: StateFlow<StatsUiState> =
        combine(selection, periodData, drillData, stripData, display) { current, period, drill, strip, (expanded, style) ->
            StatsUiState(
                // The period the data is for, not the one just selected — otherwise the
                // header flips before its figures arrive.
                range = period.key.range,
                side = period.key.side,
                totals = period.totals,
                comparison = period.comparison,
                composition = period.composition,
                // The previous month's strip stays up for the frame it takes the new one
                // to land, rather than the strip vanishing and the list jumping under it.
                // A strip of another mode's columns is dropped at once, so a mode switch
                // never shows months under a header that now reads weeks.
                strip = strip.takeIf { it.firstOrNull()?.range?.mode == period.key.range.mode }.orEmpty(),
                stripExpanded = expanded,
                chartStyle = style,
                filter = period.key.filter,
                drill = resolveDrill(current.drill, period, drill),
                loading = false,
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = StatsUiState(range = selection.value.range, chartStyle = chartStyleStore.current()),
            )

    /**
     * Shows a drill only once its data is for the period on screen.
     *
     * Stepping the period with a category open is a legitimate thing to do — it is how
     * you compare one category across months — so the drill survives the step rather
     * than closing. It just renders nothing until its own query catches up, which is
     * better than briefly attributing last month's rows to this month's header.
     */
    private fun resolveDrill(
        target: DrillTarget?,
        period: PeriodData,
        drill: DrillData,
    ): DrillUiState? = when (target) {
        null -> null
        DrillTarget.Other -> period.composition.other?.let { other ->
            DrillUiState.Other(total = other.total, members = period.composition.otherMembers)
        }
        is DrillTarget.Category -> (drill.state as? DrillUiState.Category)
            ?.takeIf { drill.key == period.key }
            ?.let { it.copy(percent = shareOf(period.composition, it.categoryId)) }
    }

    fun previousPeriod() = selection.update { it.copy(range = it.range.previous()) }

    fun nextPeriod() = selection.update { it.copy(range = it.range.next()) }

    /**
     * A strip tap. Takes the tapped column's period as is, which is a week, month or
     * year of the current mode. Behaves as stepping does, so an open category drill
     * stays open.
     */
    fun selectPeriod(period: DateRange) = selection.update { it.copy(range = period) }

    /**
     * Switching mode closes an open drill. The category is still there, but the figure
     * behind it is not — a Pemberian total for August means nothing under a header that
     * now reads 2026.
     */
    fun selectMode(mode: PeriodMode) = selection.update {
        if (it.range.mode == mode) it else it.copy(
            range = rangeForMode(it.range, mode, today()),
            drill = null,
        )
    }

    fun selectRange(start: LocalDate, endInclusive: LocalDate) = selection.update {
        // Reversed picks are straightened rather than rejected; DateRange requires
        // start <= end and would throw on the way in.
        val ordered = if (start <= endInclusive) start to endInclusive else endInclusive to start
        it.copy(range = DateRange.custom(ordered.first, ordered.second), drill = null)
    }

    fun toggleStrip() = stripExpanded.update { !it }

    /** Written straight to the store, which emits it back; it outlives this screen. */
    fun selectChartStyle(style: ChartStyle) = chartStyleStore.set(style)

    /** Switching side closes the drill: the rows behind it are the other side's. */
    fun selectSide(side: StatsSide) = selection.update {
        if (it.side == side) it else it.copy(side = side, drill = null)
    }

    fun openCategory(categoryId: Long) = selection.update {
        it.copy(drill = DrillTarget.Category(categoryId))
    }

    fun openOther() = selection.update { it.copy(drill = DrillTarget.Other) }

    fun closeDrill() = selection.update { it.copy(drill = null) }

    fun clearFilter() = sharedFilter.clear()

    init {
        // A drilled category that leaves the selection closes, the same way switching
        // side does: its rows would all be filtered away under a header that still
        // named it.
        viewModelScope.launch {
            sharedFilter.state.collect { filter ->
                selection.update { current ->
                    val target = current.drill as? DrillTarget.Category ?: return@update current
                    if (filter.includesCategory(current.side.txnType, target.id)) current
                    else current.copy(drill = null)
                }
            }
        }
    }

    companion object {
        val Factory = sakuViewModelFactory {
            StatsViewModel(transactions, accounts, categories, filter, chartStyle)
        }
    }
}
