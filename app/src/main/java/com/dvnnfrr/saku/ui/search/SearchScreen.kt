package com.dvnnfrr.saku.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.PeriodTotals
import com.dvnnfrr.saku.domain.TransactionFilter
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.ui.entry.EntrySheet
import com.dvnnfrr.saku.ui.filter.FilterBar
import com.dvnnfrr.saku.ui.shared.EmptyState
import com.dvnnfrr.saku.ui.shared.MoneyText
import com.dvnnfrr.saku.ui.shared.RankedRow
import com.dvnnfrr.saku.ui.shared.SegmentedTabs
import com.dvnnfrr.saku.ui.shared.periodLabel
import com.dvnnfrr.saku.ui.theme.MoneySign
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.formatIdr
import com.dvnnfrr.saku.ui.transactions.daySectionItems

/**
 * Search over all history, pushed from Trans. Back returns to Trans as it was left,
 * because Trans stays on the back stack underneath with its state saved.
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
    val query by viewModel.query.collectAsState()
    val state by viewModel.state.collectAsState()
    val pickedTab by viewModel.tab.collectAsState()
    // NOT_OPEN rather than null, as on Trans: the sheet here only ever edits.
    var editing by rememberSaveable { mutableStateOf(NOT_OPEN) }

    SearchScreen(
        query = query,
        state = state,
        tab = pickedTab ?: state.defaultTab,
        onQueryChange = viewModel::setQuery,
        onSelectTab = viewModel::selectTab,
        onBack = onBack,
        onClearFilter = viewModel::clearFilter,
        onRowClick = { editing = it },
        modifier = modifier,
    )

    // After a save or delete the observed rows re-emit, so the results update under the
    // same query without it being retyped.
    if (editing != NOT_OPEN) {
        EntrySheet(editingId = editing, onDismiss = { editing = NOT_OPEN })
    }
}

private const val NOT_OPEN = -1L

@Composable
fun SearchScreen(
    query: String,
    state: SearchUiState,
    tab: SearchTab,
    onQueryChange: (String) -> Unit,
    onSelectTab: (SearchTab) -> Unit,
    onBack: () -> Unit,
    onClearFilter: () -> Unit,
    onRowClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    /** False in previews, which have no window to raise a keyboard in. */
    autoFocus: Boolean = true,
) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        if (autoFocus) {
            focus.requestFocus()
            keyboard?.show()
        }
    }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(colors.surface).padding(end = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.search_back),
                    tint = colors.ink2,
                )
            }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint), color = colors.ink2) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = colors.ink3) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.search_clear_query),
                                tint = colors.ink2,
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.surfaceAlt,
                    unfocusedContainerColor = colors.surfaceAlt,
                    focusedIndicatorColor = colors.accent,
                    cursorColor = colors.accent,
                    focusedTextColor = colors.ink,
                    unfocusedTextColor = colors.ink,
                ),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
        }
        FilterBar(filter = state.filter, onClear = onClearFilter)

        when {
            !state.searched -> EmptyState(
                icon = Icons.Filled.Search,
                message = stringResource(R.string.search_prompt),
            )

            state.count == 0 && state.hiddenCount == 0 -> EmptyState(
                icon = Icons.Filled.Search,
                message = stringResource(R.string.search_empty, query.trim()),
            )

            else -> Results(state, tab, onSelectTab, onClearFilter, onRowClick)
        }
    }
}

/**
 * The summary, then Daily, Monthly and Yearly tabs like Trans. Each tab keeps its own
 * scroll position. Tapping a year opens Monthly at that year; tapping a month opens
 * Daily at that month's newest day.
 */
@Composable
private fun Results(
    state: SearchUiState,
    tab: SearchTab,
    onSelectTab: (SearchTab) -> Unit,
    onClearFilter: () -> Unit,
    onRowClick: (Long) -> Unit,
) {
    val dailyState = rememberLazyListState()
    val monthlyState = rememberLazyListState()
    val yearlyState = rememberLazyListState()
    val tabs = listOf(
        stringResource(R.string.search_tab_daily),
        stringResource(R.string.search_tab_monthly),
        stringResource(R.string.search_tab_yearly),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Summary(state)
        if (state.hiddenCount > 0) HiddenLine(state.hiddenCount, onClearFilter)
        SegmentedTabs(
            options = tabs,
            selectedIndex = tab.ordinal,
            onSelect = { onSelectTab(SearchTab.entries[it]) },
            modifier = Modifier.padding(SakuTheme.spacing.md),
        )
        val listPadding = PaddingValues(bottom = SakuTheme.spacing.xl)
        when (tab) {
            SearchTab.DAILY -> LazyColumn(state = dailyState, modifier = Modifier.fillMaxSize(), contentPadding = listPadding) {
                daySectionItems(state.days, onRowClick)
            }

            SearchTab.MONTHLY -> LazyColumn(state = monthlyState, modifier = Modifier.fillMaxSize(), contentPadding = listPadding) {
                items(count = state.months.size, key = { i -> "month-${state.months[i].range.start}" }) { i ->
                    val month = state.months[i]
                    val target = dailyScrollIndex(state.days, month)
                    PeriodRow(
                        period = month,
                        onClick = target?.let { index ->
                            {
                                dailyState.requestScrollToItem(index)
                                onSelectTab(SearchTab.DAILY)
                            }
                        },
                    )
                }
            }

            SearchTab.YEARLY -> LazyColumn(state = yearlyState, modifier = Modifier.fillMaxSize(), contentPadding = listPadding) {
                items(count = state.years.size, key = { i -> "year-${state.years[i].range.start}" }) { i ->
                    val year = state.years[i]
                    val target = monthlyScrollIndex(state.months, year)
                    PeriodRow(
                        period = year,
                        onClick = target?.let { index ->
                            {
                                monthlyState.requestScrollToItem(index)
                                onSelectTab(SearchTab.MONTHLY)
                            }
                        },
                    )
                }
            }
        }
    }
}

/** One month or year: its money on the search's side, the count, and the other side when present. */
@Composable
private fun PeriodRow(period: SearchPeriod, onClick: (() -> Unit)?) {
    val colors = SakuTheme.colors
    val count = pluralStringResource(R.plurals.search_month_count, period.count, period.count)
    val supporting = when {
        period.count == 0 -> stringResource(R.string.search_month_none)
        period.otherSide.isZero -> count
        period.side == TxnType.INCOME -> stringResource(R.string.search_month_with_out, count, formatIdr(period.otherSide))
        else -> stringResource(R.string.search_month_with_in, count, formatIdr(period.otherSide))
    }
    RankedRow(
        colorSlot = null,
        name = periodLabel(period.range),
        value = period.value,
        color = if (period.side == TxnType.INCOME) colors.income else colors.expense,
        barPermille = period.barPermille,
        supporting = supporting,
        onClick = onClick,
    )
}

/** The count and the matching rows' Income and Expenses; transfers count toward neither. */
@Composable
private fun Summary(state: SearchUiState) {
    val spacing = SakuTheme.spacing
    val typography = SakuTheme.typography
    // 20% above the list's own text (owner, 1 Nov 2026), so the totals read as the answer.
    val countStyle = typography.label.copy(fontSize = typography.label.fontSize * SUMMARY_SCALE)
    val amountStyle = typography.amountSmall.copy(fontSize = typography.amountSmall.fontSize * SUMMARY_SCALE)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = spacing.screen, end = spacing.screen, top = spacing.lg, bottom = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(
            text = pluralStringResource(R.plurals.search_count, state.count, state.count),
            style = countStyle,
            color = SakuTheme.colors.ink2,
            modifier = Modifier.weight(1f),
        )
        MoneyText(state.totals.income, type = TxnType.INCOME, style = amountStyle, sign = MoneySign.FLOW)
        MoneyText(state.totals.expense, type = TxnType.EXPENSE, style = amountStyle, sign = MoneySign.FLOW)
    }
}

private const val SUMMARY_SCALE = 1.2f

@Composable
private fun HiddenLine(count: Int, onClearFilter: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SakuTheme.colors.accentWash)
            .padding(start = SakuTheme.spacing.screen, end = SakuTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.search_hidden, count),
            style = SakuTheme.typography.body,
            color = SakuTheme.colors.ink,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onClearFilter) {
            Text(
                text = stringResource(R.string.filter_clear_description),
                style = SakuTheme.typography.bodyStrong,
                color = SakuTheme.colors.accent,
            )
        }
    }
}

@SakuPreview
@Composable
private fun SearchScreenPreview() {
    SakuPreviewSurface(padded = false) {
        SearchScreen(
            query = "kopi",
            tab = SearchTab.DAILY,
            state = SearchUiState(
                filter = TransactionFilter(accountIds = setOf(1L)),
                searched = true,
                count = 0,
                totals = PeriodTotals(Money.ZERO, Money.ZERO),
                hiddenCount = 2,
            ),
            onQueryChange = {},
            onSelectTab = {},
            onBack = {},
            onClearFilter = {},
            onRowClick = {},
            autoFocus = false,
        )
    }
}
