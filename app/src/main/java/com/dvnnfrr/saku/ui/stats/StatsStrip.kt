package com.dvnnfrr.saku.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.DailyTotals
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.PERMILLE_WHOLE
import com.dvnnfrr.saku.domain.PeriodMode
import com.dvnnfrr.saku.ui.shared.periodLabel
import com.dvnnfrr.saku.ui.theme.MoneySign
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.formatIdr
import com.dvnnfrr.saku.ui.theme.formatIdrAxis
import com.dvnnfrr.saku.ui.theme.formatIdrCompact
import com.dvnnfrr.saku.ui.theme.formatIdrSigned
import com.dvnnfrr.saku.ui.transactions.stripColumnLabel
import kotlinx.datetime.LocalDate

/**
 * Every week, month or year from the first income or expense through the selected one,
 * as paired income and expense bars on one scale, each column's label and compact net
 * beneath.
 *
 * Columns keep one width, so six fit a 412 dp phone, and the row opens scrolled to its
 * end: the selected period is the rightmost column and earlier ones are a scroll to the
 * left. The row is laid out in reverse, newest first, so "scrolled to the end" is the
 * list's natural first position rather than a scroll that has to land after layout. A
 * new selection starts a new scroll state, so tapping January leaves January rightmost.
 *
 * The selected column is drawn at full strength and the others muted, so the eye lands
 * on the period the figures below describe and reads the rest as context.
 *
 * With [side] set the strip draws that side alone and labels each column with its
 * total rather than a net: the category detail's strip, where the other side is zero.
 *
 * Every column is a tap target the full column wide and speaks its own figures, so the
 * chart is never colour-only and never pointer-only.
 *
 * A Y axis the app works out itself ([stripAxis]) sits fixed at the left while the
 * columns scroll past it, and each column draws its own share of the gridlines, so the
 * lines scroll with the bars and always meet the labels.
 */
@Composable
fun PeriodStrip(
    columns: List<StripColumn>,
    onSelectPeriod: (DateRange) -> Unit,
    modifier: Modifier = Modifier,
    side: StatsSide? = null,
) {
    val selected = columns.firstOrNull { it.selected }?.range
    val listState = remember(selected) { LazyListState() }
    val newestFirst = columns.asReversed()
    val axis = remember(columns) { stripAxis(columns) }
    val spacing = SakuTheme.spacing

    Row(modifier = modifier.fillMaxWidth()) {
        // Down by the row's and the column's top padding, so tick 0 meets the baseline.
        AxisLabels(axis, Modifier.padding(start = spacing.sm, top = spacing.sm + spacing.xs))
        LazyRow(
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(end = spacing.sm, top = spacing.sm, bottom = spacing.sm),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.weight(1f),
        ) {
            items(count = newestFirst.size, key = { index -> newestFirst[index].range.start.toEpochDays() }) { index ->
                val column = newestFirst[index]
                StripColumnView(
                    column = column,
                    axis = axis,
                    side = side,
                    onClick = { onSelectPeriod(column.range) },
                    modifier = Modifier.width(COLUMN_WIDTH),
                )
            }
        }
    }
}

/**
 * The tick labels, each centred on its gridline's height in the bar area. Hidden from
 * TalkBack: every column already speaks its exact figures, which say more than a scale.
 */
@Composable
private fun AxisLabels(axis: StripAxis, modifier: Modifier = Modifier) {
    val colors = SakuTheme.colors
    Layout(
        content = {
            axis.ticks.forEach { tick ->
                Text(
                    text = formatIdrAxis(tick),
                    style = SakuTheme.typography.caption,
                    color = colors.ink2,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        },
        modifier = modifier
            .width(AXIS_WIDTH)
            .height(BAR_AREA)
            .clearAndSetSemantics {},
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            val top = axis.top.rupiah
            placeables.forEachIndexed { index, placeable ->
                val y = constraints.maxHeight - (constraints.maxHeight * axis.ticks[index].rupiah / top).toInt()
                placeable.place(x = 0, y = y - placeable.height / 2)
            }
        }
    }
}

/**
 * The strip's own header, which names the unit and folds the bars away.
 *
 * Collapsed, it still names the selected period's net, so hiding the chart hides the
 * comparison but never the figure. The whole row is the tap target and speaks its state.
 */
@Composable
fun StripHeader(
    columns: List<StripColumn>,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing
    val selected = columns.firstOrNull { it.selected }
    val state = stringResource(if (expanded) R.string.stats_strip_expanded else R.string.stats_strip_collapsed)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = spacing.touchTarget)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics(mergeDescendants = true) { stateDescription = state }
            .padding(horizontal = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(stripTitleRes(selected?.range?.mode)),
            style = SakuTheme.typography.label,
            color = colors.ink2,
            modifier = Modifier.weight(1f),
        )
        if (!expanded && selected != null && !selected.empty) {
            Text(
                text = stringResource(
                    R.string.stats_strip_collapsed_net,
                    selected.range.stripColumnLabel(),
                    formatIdrCompact(selected.net),
                ),
                style = SakuTheme.typography.caption,
                color = colors.ink2,
                modifier = Modifier.padding(end = spacing.sm),
            )
        }
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = colors.ink2,
        )
    }
}

/** The strip's heading names its unit: "Weeks", "Months", "Years". */
internal fun stripTitleRes(mode: PeriodMode?): Int = when (mode) {
    PeriodMode.WEEKLY -> R.string.stats_strip_title_weeks
    PeriodMode.ANNUALLY -> R.string.stats_strip_title_years
    PeriodMode.MONTHLY, PeriodMode.CUSTOM, null -> R.string.stats_strip_title_months
}

@Composable
private fun StripColumnView(
    column: StripColumn,
    axis: StripAxis,
    side: StatsSide?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing
    val name = periodLabel(column.range)
    val sideTotal = side?.let { column.totals.forSide(it) }
    val description = when {
        column.empty -> stringResource(R.string.stats_strip_month_empty, name)
        sideTotal != null -> stringResource(R.string.stats_strip_month_side, name, formatIdr(sideTotal))
        else -> stringResource(
            R.string.stats_strip_month,
            name,
            formatIdr(column.income),
            formatIdr(column.expense),
            formatIdrSigned(column.net),
        )
    }
    val label = when {
        column.empty -> ""
        sideTotal != null -> formatIdrCompact(sideTotal, MoneySign.BALANCE)
        else -> formatIdrCompact(column.net)
    }
    val selectLabel = stringResource(R.string.stats_strip_select)
    val textColor = if (column.selected) colors.ink else colors.ink2

    Column(
        modifier = modifier
            .heightIn(min = spacing.touchTarget)
            .clickable(onClickLabel = selectLabel, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                selected = column.selected
            }
            .padding(vertical = spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Bars(column, axis, side)
        Text(
            text = column.range.stripColumnLabel(),
            style = SakuTheme.typography.caption,
            color = textColor,
            maxLines = 1,
            softWrap = false,
        )
        // An empty slot keeps the line so every column stays the same height.
        Text(
            text = label,
            style = SakuTheme.typography.caption,
            color = textColor,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun Bars(column: StripColumn, axis: StripAxis, side: StatsSide?) {
    val colors = SakuTheme.colors
    val gridColor = colors.line
    // Muted by alpha on the same tokens, so both themes keep their own validated hues.
    val strength = if (column.selected) 1f else MUTED_ALPHA
    val income = colors.income.copy(alpha = strength)
    val expense = colors.expense.copy(alpha = strength)

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAR_AREA)
                .drawBehind {
                    // Tick 0 is the baseline below; the rest cross the full column width.
                    val top = axis.top.rupiah
                    axis.ticks.drop(1).forEach { tick ->
                        val y = size.height - size.height * tick.rupiah / top
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(BAR_GAP, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (!column.empty) {
                when (side) {
                    null -> {
                        Bar(column.incomePermille, income, BAR_WIDTH)
                        Bar(column.expensePermille, expense, BAR_WIDTH)
                    }
                    // One bar the width of the pair, so a category strip reads as firmly
                    // as the overview's.
                    StatsSide.INCOME -> Bar(column.incomePermille, income, PAIR_WIDTH)
                    StatsSide.EXPENSES -> Bar(column.expensePermille, expense, PAIR_WIDTH)
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.line),
        )
    }
}

@Composable
private fun Bar(permille: Int, color: Color, width: Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight(permille.coerceIn(0, PERMILLE_WHOLE) / PERMILLE_WHOLE.toFloat())
            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
            .background(color),
    )
}

private const val MUTED_ALPHA = 0.4f
private val BAR_AREA = 72.dp
private val BAR_WIDTH = 10.dp
private val BAR_GAP = 3.dp
private val PAIR_WIDTH = BAR_WIDTH * 2 + BAR_GAP

/** Six columns, the axis and the padding fit a 412 dp phone: 8 + 40 + 6 × 56 + 8 = 392. */
private val COLUMN_WIDTH = 56.dp
private val AXIS_WIDTH = 40.dp

@SakuPreview
@Composable
private fun PeriodStripPreview() {
    val october = DateRange.ofMonth(LocalDate(2026, 10, 1))
    val days = previewHistory()
    SakuPreviewSurface(padded = false) {
        Column {
            PeriodStrip(foldStrip(days, october), onSelectPeriod = {})
            PeriodStrip(foldStrip(days, october), onSelectPeriod = {}, side = StatsSide.EXPENSES)
            PeriodStrip(foldStrip(days, DateRange.ofWeek(LocalDate(2026, 10, 26))), onSelectPeriod = {})
            PeriodStrip(foldStrip(days, DateRange.ofYear(2026)), onSelectPeriod = {})
            PeriodStrip(foldStrip(emptyList(), october), onSelectPeriod = {})
        }
    }
}

/** May to October 2026, one day a month carrying the month's totals. */
internal fun previewHistory(): List<DailyTotals> = listOf(
    9_000_000L to 8_200_000L,
    16_000_000L to 8_900_000L,
    8_000_000L to 8_300_000L,
    8_000_000L to 9_400_000L,
    9_000_000L to 7_600_000L,
    16_500_000L to 14_026_400L,
).mapIndexed { index, (income, expense) ->
    DailyTotals(LocalDate(2026, 5 + index, 1), Money(income), Money(expense))
}
