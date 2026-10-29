package com.dvnnfrr.saku.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.transactions.DailyTab

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
    // NOT_OPEN rather than null, as on Trans: the sheet here only ever edits.
    var editing by rememberSaveable { mutableStateOf(NOT_OPEN) }

    SearchScreen(
        query = query,
        state = state,
        onQueryChange = viewModel::setQuery,
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
    onQueryChange: (String) -> Unit,
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
                placeholder = { Text(stringResource(R.string.search_hint), color = colors.ink3) },
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

            else -> {
                Summary(state)
                if (state.hiddenCount > 0) HiddenLine(state.hiddenCount, onClearFilter)
                if (state.count > 0) DailyTab(days = state.days, onRowClick = onRowClick)
            }
        }
    }
}

/** The count and the matching rows' Income and Expenses; transfers count toward neither. */
@Composable
private fun Summary(state: SearchUiState) {
    val spacing = SakuTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screen, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(
            text = pluralStringResource(R.plurals.search_count, state.count, state.count),
            style = SakuTheme.typography.label,
            color = SakuTheme.colors.ink2,
            modifier = Modifier.weight(1f),
        )
        MoneyText(state.totals.income, type = TxnType.INCOME, style = SakuTheme.typography.amountSmall, withPrefix = false)
        MoneyText(state.totals.expense, type = TxnType.EXPENSE, style = SakuTheme.typography.amountSmall, withPrefix = false)
    }
}

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
            state = SearchUiState(
                filter = TransactionFilter(accountIds = setOf(1L)),
                searched = true,
                count = 0,
                totals = PeriodTotals(Money.ZERO, Money.ZERO),
                hiddenCount = 2,
            ),
            onQueryChange = {},
            onBack = {},
            onClearFilter = {},
            onRowClick = {},
            autoFocus = false,
        )
    }
}
