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
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.ui.shared.EmptyState
import com.dvnnfrr.saku.ui.shared.TransactionRow
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.forDirection
import com.dvnnfrr.saku.ui.theme.formatIdr
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
) {
    // Each section contributes one header plus its rows, so a date's index has to be
    // counted rather than looked up.
    LaunchedEffect(scrollTo, days) {
        val target = scrollTo ?: return@LaunchedEffect
        var index = 0
        for (section in days) {
            if (section.date == target) {
                listState.animateScrollToItem(index)
                break
            }
            index += 1 + section.rows.size
        }
        onScrollHandled()
    }

    if (days.isEmpty()) {
        EmptyState(
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            message = stringResource(R.string.transactions_empty),
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
                    accountSlot = row.account.colorSlot,
                    category = row.categoryName,
                    subcategory = row.subcategoryName,
                    note = row.note,
                    toAccountName = row.toAccount?.name,
                    toAccountSlot = row.toAccount?.colorSlot,
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
        // Zero subtotals are omitted rather than shown as 0, so a day of pure
        // spending does not carry a meaningless "0" income figure beside it.
        if (!section.income.isZero) {
            Subtotal(section.income, TxnType.INCOME)
        }
        if (!section.expense.isZero) {
            Subtotal(section.expense, TxnType.EXPENSE)
        }
    }
    HorizontalDivider(thickness = 1.dp, color = colors.line)
}

@Composable
private fun Subtotal(amount: Money, type: TxnType) {
    Text(
        text = formatIdr(amount, withPrefix = false),
        style = SakuTheme.typography.amountSmall,
        color = SakuTheme.colors.forDirection(type),
        maxLines = 1,
    )
}

/** Room for the floating add button, which is 56dp plus its 16dp margin. */
private val FAB_CLEARANCE = 88.dp
