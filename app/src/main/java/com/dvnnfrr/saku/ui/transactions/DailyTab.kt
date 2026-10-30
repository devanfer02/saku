package com.dvnnfrr.saku.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.ui.filter.FilteredEmptyState
import com.dvnnfrr.saku.ui.shared.EmptyState
import com.dvnnfrr.saku.ui.shared.MoneyText
import com.dvnnfrr.saku.ui.shared.TransactionRow
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.MoneySign
import kotlinx.datetime.LocalDate

/**
 * Day-grouped history, newest first.
 *
 * A day with no transactions gets no header at all — the Daily tab shows what
 * happened, and padding it with empty days would bury the days that did. The
 * Calendar tab is where absence is the point.
 */
@Composable
fun DailyTab(
    days: List<DaySection>,
    modifier: Modifier = Modifier,
    scrollTo: LocalDate? = null,
    onScrollHandled: () -> Unit = {},
    onRowClick: (Long) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    /** Set while a filter is active, so an empty month says the filter is why. */
    onClearFilter: (() -> Unit)? = null,
    /** What an empty, unfiltered month says. It names the month, so the caller supplies it. */
    emptyMessage: String = "",
) {
    LaunchedEffect(scrollTo, days) {
        val target = scrollTo ?: return@LaunchedEffect
        scrollIndexFor(days, target)?.let { listState.animateScrollToItem(it) }
        onScrollHandled()
    }

    if (days.isEmpty() && onClearFilter != null) {
        FilteredEmptyState(
            message = stringResource(R.string.filter_empty_month),
            onClear = onClearFilter,
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    if (days.isEmpty()) {
        EmptyState(
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            message = emptyMessage,
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        // Clears the floating add button. Without it the oldest row of the month sat
        // under the button, and tapping it opened a new transaction instead of that one.
        contentPadding = PaddingValues(bottom = FAB_CLEARANCE),
    ) {
        days.forEach { section ->
            item(key = "header-${section.date}") { DayHeader(section) }
            items(
                count = section.rows.size,
                key = { i -> section.rows[i].id },
            ) { i ->
                val row = section.rows[i]
                TransactionRow(
                    amount = row.amount,
                    type = row.type,
                    accountName = row.account.name,
                    categorySlot = row.categorySlot,
                    category = row.categoryName,
                    subcategory = row.subcategoryName,
                    note = row.note,
                    toAccountName = row.toAccount?.name,
                    onClick = { onRowClick(row.id) },
                )
            }
        }
    }
}

@Composable
private fun DayHeader(section: DaySection) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceAlt)
            .padding(horizontal = spacing.screen, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(
            text = section.date.dayHeaderLabel(),
            style = SakuTheme.typography.label,
            color = colors.ink2,
            modifier = Modifier.weight(1f),
        )
        // The day's net, signed. A day of nothing but transfers has no flow to net, and
        // a "Rp 0" beside it would read as a day that cancelled out.
        val hasFlow = !section.income.isZero || !section.expense.isZero
        if (hasFlow) {
            MoneyText(
                money = section.net,
                sign = MoneySign.NET,
                style = SakuTheme.typography.amountSmall,
                color = colors.ink2,
            )
        }
    }
    HorizontalDivider(thickness = 1.dp, color = colors.line)
}

/** Room for the floating add button, which is 56dp plus its 16dp margin. */
private val FAB_CLEARANCE = 88.dp
