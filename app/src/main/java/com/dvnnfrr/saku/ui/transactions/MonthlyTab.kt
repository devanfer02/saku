package com.dvnnfrr.saku.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.PeriodTotals
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.ui.filter.FilteredEmptyState
import com.dvnnfrr.saku.ui.shared.EmptyState
import com.dvnnfrr.saku.ui.shared.MoneyText
import com.dvnnfrr.saku.ui.theme.MoneySign
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/**
 * The year as month rows, newest first, one of them expanded into its Monday to Sunday
 * weeks. A month or week with nothing in it shows no figures rather than zeros, the same
 * rule the Calendar follows, so an unlogged stretch reads as one.
 */
@Composable
fun MonthlyTab(
    year: MonthlyYear,
    onToggleMonth: (Month) -> Unit,
    onOpenWeek: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    /** Set while a filter is active, so an empty year says the filter is why. */
    onClearFilter: (() -> Unit)? = null,
) {
    val nothingAtAll = year.months.all { it.totals == null }
    if (nothingAtAll && onClearFilter != null) {
        FilteredEmptyState(
            message = stringResource(R.string.filter_empty_year),
            onClear = onClearFilter,
            modifier = modifier.fillMaxSize(),
        )
        return
    }
    if (year.months.isEmpty()) {
        EmptyState(
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            message = stringResource(R.string.monthly_empty_year, year.year),
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = FAB_CLEARANCE),
    ) {
        year.months.forEach { month ->
            item(key = "month-${month.range.start}") {
                MonthLine(month = month, onClick = { onToggleMonth(month.month) })
            }
            if (month.expanded) {
                month.weeks.forEach { week ->
                    item(key = "week-${month.range.start}-${week.range.start}") {
                        WeekLine(week = week, onClick = { onOpenWeek(week.openDate) })
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthLine(month: MonthRow, onClick: () -> Unit) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing
    val name = month.labelMonth.shortLabel()
    val action = stringResource(
        if (month.expanded) R.string.monthly_collapse else R.string.monthly_expand,
        name,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = action, onClick = onClick)
            .defaultMinSize(minHeight = spacing.rowMinHeight)
            .padding(horizontal = spacing.screen, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = SakuTheme.typography.bodyStrong, color = colors.ink, maxLines = 1)
            Text(
                text = month.range.dottedSpanLabel(),
                style = SakuTheme.typography.caption,
                color = colors.ink2,
                maxLines = 1,
            )
        }
        Figures(month.totals)
        Icon(
            imageVector = if (month.expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = colors.ink3,
        )
    }
    HorizontalDivider(thickness = 1.dp, color = colors.line)
}

/**
 * A week under its month. The current week is tinted and also labelled "This week" in
 * words and to a screen reader, so the highlight does not depend on colour alone.
 */
@Composable
private fun WeekLine(week: WeekRow, onClick: () -> Unit) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing
    val thisWeek = stringResource(R.string.monthly_this_week)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (week.isCurrent) colors.accentWash else colors.surfaceAlt)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (week.isCurrent) Modifier.semantics { stateDescription = thisWeek } else Modifier)
            .defaultMinSize(minHeight = spacing.touchTarget)
            .padding(start = spacing.xl, end = spacing.screen, top = spacing.xs, bottom = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = week.range.dottedSpanLabel(),
                style = SakuTheme.typography.body,
                color = colors.ink,
                maxLines = 1,
            )
            if (week.isCurrent) {
                Text(text = thisWeek, style = SakuTheme.typography.caption, color = colors.accent, maxLines = 1)
            }
        }
        Figures(week.totals)
    }
    HorizontalDivider(thickness = 1.dp, color = colors.line)
}

/** Income, expenses and net, or a quiet "Nothing recorded" when there were none. */
@Composable
private fun Figures(totals: PeriodTotals?) {
    if (totals == null) {
        Text(
            text = stringResource(R.string.monthly_nothing),
            style = SakuTheme.typography.caption,
            color = SakuTheme.colors.ink2,
            maxLines = 1,
        )
        return
    }
    Column(horizontalAlignment = Alignment.End) {
        Row(horizontalArrangement = Arrangement.spacedBy(SakuTheme.spacing.sm)) {
            MoneyText(totals.income, type = TxnType.INCOME, style = SakuTheme.typography.amountSmall, sign = MoneySign.FLOW)
            MoneyText(totals.expense, type = TxnType.EXPENSE, style = SakuTheme.typography.amountSmall, sign = MoneySign.FLOW)
        }
        // A net, not a direction: signed and neutral, as in the period header.
        MoneyText(totals.net, style = SakuTheme.typography.amountSmall, sign = MoneySign.NET)
    }
}

/** Room for the floating add button, which is 56dp plus its 16dp margin. */
private val FAB_CLEARANCE = 88.dp

@SakuPreview
@Composable
private fun MonthlyTabPreview() {
    val today = LocalDate(2026, 10, 30)
    val days = listOf(
        com.dvnnfrr.saku.domain.DailyTotals(LocalDate(2026, 10, 2), Money(8_000_000), Money(150_000)),
        com.dvnnfrr.saku.domain.DailyTotals(LocalDate(2026, 9, 28), Money.ZERO, Money(100_000)),
    )
    SakuPreviewSurface(padded = false) {
        MonthlyTab(
            year = assembleMonthlyYear(2026, days, today, Month.OCTOBER),
            onToggleMonth = {},
            onOpenWeek = {},
        )
    }
}
