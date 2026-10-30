package com.dvnnfrr.saku.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.MonthView
import com.dvnnfrr.saku.domain.PeriodTotals
import com.dvnnfrr.saku.domain.TransactionFilter
import com.dvnnfrr.saku.ui.entry.EntrySheet
import com.dvnnfrr.saku.ui.filter.FilterAction
import com.dvnnfrr.saku.ui.filter.FilterBar
import com.dvnnfrr.saku.ui.filter.FilterSheet
import com.dvnnfrr.saku.ui.paycycle.MonthViewSwitch
import com.dvnnfrr.saku.ui.shared.SegmentedTabs
import com.dvnnfrr.saku.ui.shared.periodName
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/**
 * The Trans. destination: a period header over one of three views of that period.
 *
 * Daily and Calendar read the same month of rows, so the header, the day subtotals and
 * the calendar cells cannot disagree about what the month holds. Monthly reads the year's
 * day totals under the same filter.
 */
@Composable
fun TransactionsScreen(
    modifier: Modifier = Modifier,
    onOpenSearch: () -> Unit = {},
    viewModel: TransactionListViewModel = viewModel(factory = TransactionListViewModel.Factory),
) {
    // collectAsState rather than collectAsStateWithLifecycle: the upstream is already
    // scoped by stateIn(WhileSubscribed), and the lifecycle-aware variant would add a
    // dependency for no behaviour this screen needs.
    val state by viewModel.state.collectAsState()

    // NOT_OPEN rather than a nullable id: null is a legitimate value here, meaning
    // "open on a new transaction", so nullability cannot also encode "closed".
    var editing by rememberSaveable { mutableStateOf(NOT_OPEN) }
    var filtering by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        TransactionsScreen(
            state = state,
            onPrevious = viewModel::previousMonth,
            onNext = viewModel::nextMonth,
            onSelectTab = viewModel::selectTab,
            onDayClick = viewModel::openDay,
            onScrollHandled = viewModel::consumeScrollTarget,
            onRowClick = { editing = it },
            onOpenFilter = { filtering = true },
            onClearFilter = viewModel::clearFilter,
            onOpenSearch = onOpenSearch,
            onToggleMonth = viewModel::toggleMonth,
            onSelectMonthView = viewModel::selectMonthView,
        )

        FloatingActionButton(
            onClick = { editing = NEW },
            containerColor = SakuTheme.colors.accent,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(SakuTheme.spacing.lg),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add))
        }
    }

    if (editing != NOT_OPEN) {
        EntrySheet(
            editingId = editing.takeIf { it != NEW },
            onDismiss = { editing = NOT_OPEN },
        )
    }

    if (filtering) {
        FilterSheet(range = state.headerRange, onDismiss = { filtering = false })
    }
}

/** Sentinels for the entry sheet's open state. Real transaction ids are positive. */
private const val NOT_OPEN = -1L
private const val NEW = 0L

@Composable
fun TransactionsScreen(
    state: TransactionListUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectTab: (TransactionTab) -> Unit,
    onDayClick: (LocalDate) -> Unit,
    onScrollHandled: () -> Unit,
    onRowClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onOpenFilter: () -> Unit = {},
    onClearFilter: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onToggleMonth: (Month) -> Unit = {},
    onSelectMonthView: (MonthView) -> Unit = {},
) {
    val tabs = listOf(
        stringResource(R.string.transactions_tab_daily),
        stringResource(R.string.transactions_tab_calendar),
        stringResource(R.string.transactions_tab_monthly),
    )
    // Hoisted out of DailyTab. Inside the `when`, the tab leaves composition on every
    // switch and its saved scroll entry is discarded, so Daily -> Calendar -> Daily
    // came back at the top.
    val dailyListState = rememberLazyListState()
    val filtered = state.filter.isActive
    val inMonthly = state.tab == TransactionTab.MONTHLY

    Column(modifier = modifier.fillMaxSize()) {
        PeriodHeader(
            range = state.headerRange,
            totals = state.headerTotals,
            onPrevious = onPrevious,
            onNext = onNext,
            previousDescription = stringResource(
                if (inMonthly) R.string.transactions_previous_year else R.string.stats_previous_period,
            ),
            nextDescription = stringResource(
                if (inMonthly) R.string.transactions_next_year else R.string.stats_next_period,
            ),
            trailing = {
                IconButton(onClick = onOpenSearch) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = stringResource(R.string.search_action),
                        tint = SakuTheme.colors.ink2,
                    )
                }
                FilterAction(active = filtered, onClick = onOpenFilter)
            },
        )
        MonthViewSwitch(view = state.monthView, onSelect = onSelectMonthView, span = state.headerRange)
        FilterBar(filter = state.filter, onClear = onClearFilter)

        SegmentedTabs(
            options = tabs,
            selectedIndex = state.tab.ordinal,
            onSelect = { onSelectTab(TransactionTab.entries[it]) },
            modifier = Modifier.padding(SakuTheme.spacing.md),
        )

        when (state.tab) {
            TransactionTab.DAILY -> DailyTab(
                days = state.days,
                listState = dailyListState,
                scrollTo = state.pendingScrollTo,
                onScrollHandled = onScrollHandled,
                onRowClick = onRowClick,
                onClearFilter = onClearFilter.takeIf { filtered },
                emptyMessage = stringResource(R.string.transactions_empty, periodName(state.range)),
            )

            TransactionTab.CALENDAR -> state.calendar?.let { month ->
                CalendarTab(month = month, onDayClick = onDayClick)
            }

            TransactionTab.MONTHLY -> state.monthly?.let { year ->
                MonthlyTab(
                    year = year,
                    onToggleMonth = onToggleMonth,
                    onOpenWeek = onDayClick,
                    onClearFilter = onClearFilter.takeIf { filtered },
                )
            }
        }
    }
}

@SakuPreview
@Composable
private fun TransactionsScreenPreview() {
    val august = DateRange.ofMonth(LocalDate(2026, 8, 1))
    SakuPreviewSurface(padded = false) {
        TransactionsScreen(
            state = TransactionListUiState(
                range = august,
                totals = PeriodTotals(income = Money(8_000_000), expense = Money(5_061_400)),
                calendar = emptyList<com.dvnnfrr.saku.domain.Transaction>()
                    .toCalendarMonth(august, kotlinx.datetime.TimeZone.UTC),
                filter = TransactionFilter(accountIds = setOf(1L, 2L)),
                loading = false,
            ),
            onPrevious = {},
            onNext = {},
            onSelectTab = {},
            onDayClick = {},
            onScrollHandled = {},
            onRowClick = {},
        )
    }
}
