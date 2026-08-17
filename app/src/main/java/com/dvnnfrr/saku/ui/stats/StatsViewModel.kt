package com.dvnnfrr.saku.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dvnnfrr.saku.data.repository.AccountRepository
import com.dvnnfrr.saku.data.repository.CategoryRepository
import com.dvnnfrr.saku.data.repository.TransactionRepository
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.PeriodMode
import com.dvnnfrr.saku.domain.PeriodTotals
import com.dvnnfrr.saku.sakuViewModelFactory
import com.dvnnfrr.saku.ui.transactions.DisplayIndex
import com.dvnnfrr.saku.ui.transactions.toRowUi
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

    private fun today(): LocalDate = clock.now().toLocalDateTime(zone).date

    /** The period and side, which is all the overview's three queries depend on. */
    private data class PeriodKey(val range: DateRange, val side: StatsSide)

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
        selection
            .map { PeriodKey(it.range, it.side) }
            .distinctUntilChanged()
            .flatMapLatest { key ->
                val previous = key.range.previous()
                combine(
                    transactionRepository.observePeriodTotals(key.range, zone),
                    transactionRepository.observePeriodTotals(previous, zone),
                    transactionRepository.observeCategoryTotals(key.range, zone, key.side.txnType),
                ) { totals, previousTotals, categoryTotals ->
                    PeriodData(
                        key = key,
                        totals = totals,
                        comparison = PeriodComparison(previous, previousTotals, totals),
                        composition = rankComposition(categoryTotals),
                    )
                }
            }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val drillData: Flow<DrillData> =
        // No distinctUntilChanged: a StateFlow already conflates equal values, so the
        // drill re-subscribes only when the selection genuinely differs.
        selection
            .flatMapLatest { current ->
                val key = PeriodKey(current.range, current.side)
                when (val target = current.drill) {
                    // "Other" needs no query of its own: the categories it combines are
                    // already in the composition the overview loaded.
                    null, DrillTarget.Other -> flowOf(DrillData(key, null))
                    is DrillTarget.Category -> categoryDrill(key, target.id)
                }
            }

    private fun categoryDrill(key: PeriodKey, categoryId: Long): Flow<DrillData> = combine(
        transactionRepository.observeForCategory(key.range, zone, categoryId, key.side.txnType),
        categoryRepository.observeAllSubcategories(categoryId),
        categoryRepository.observeAll(),
        accountRepository.observeAll(),
    ) { transactions, subcategories, categories, accounts ->
        val category = categories.firstOrNull { it.id == categoryId }
        val slot = category?.colorSlot ?: ColorSlot.FIRST
        val index = DisplayIndex(
            accounts = accounts,
            categories = categories,
            subcategories = subcategories,
        )
        DrillData(
            key = key,
            state = DrillUiState.Category(
                categoryId = categoryId,
                title = category?.name,
                colorSlot = slot,
                // Summed from the rows the list is rendering anyway. Bounded to one
                // category in one period, so this is not the memory fold the screen
                // avoids elsewhere, and it cannot disagree with the list beneath it.
                total = Money(transactions.sumOf { it.amount.rupiah }),
                subcategories = foldSubcategories(transactions, subcategories, slot),
                rows = transactions.map { it.toRowUi(index) },
            ),
        )
    }

    val state: StateFlow<StatsUiState> =
        combine(selection, periodData, drillData) { current, period, drill ->
            StatsUiState(
                // The period the data is for, not the one just selected — otherwise the
                // header flips before its figures arrive.
                range = period.key.range,
                side = period.key.side,
                totals = period.totals,
                comparison = period.comparison,
                composition = period.composition,
                drill = resolveDrill(current.drill, period, drill),
                loading = false,
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = StatsUiState(range = selection.value.range),
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
        is DrillTarget.Category -> drill.state.takeIf { drill.key == period.key }
    }

    fun previousPeriod() = selection.update { it.copy(range = it.range.previous()) }

    fun nextPeriod() = selection.update { it.copy(range = it.range.next()) }

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

    /** Switching side closes the drill: the rows behind it are the other side's. */
    fun selectSide(side: StatsSide) = selection.update {
        if (it.side == side) it else it.copy(side = side, drill = null)
    }

    fun openCategory(categoryId: Long) = selection.update {
        it.copy(drill = DrillTarget.Category(categoryId))
    }

    fun openOther() = selection.update { it.copy(drill = DrillTarget.Other) }

    fun closeDrill() = selection.update { it.copy(drill = null) }

    companion object {
        val Factory = sakuViewModelFactory {
            StatsViewModel(transactions, accounts, categories)
        }
    }
}
