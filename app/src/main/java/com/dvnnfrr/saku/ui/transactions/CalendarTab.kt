package com.dvnnfrr.saku.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.formatIdrCell
import com.dvnnfrr.saku.ui.theme.formatIdrSigned
import kotlinx.datetime.LocalDate
import kotlin.math.floor

/**
 * The month as a grid, one cell per day, with the picked dates listed beneath it.
 *
 * This tab exists because half of a year of reference data was logged from
 * memory days later, with one twenty-day hole. A day with no transactions renders
 * blank rather than as a zero, so those holes read as holes.
 *
 * A tap picks a day and a second tap closes a range; a long press then drag sweeps one.
 * Tapping is the accessible path: every cell is a button to TalkBack, and the drag only
 * adds a faster way to do the same thing. Below the grid the range is summed and listed
 * exactly as Daily lists a day, through the same [daySectionItems].
 */
@Composable
fun CalendarTab(
    month: CalendarMonth,
    modifier: Modifier = Modifier,
    /** The picked dates, folded. Null when nothing is picked. */
    selection: CalendarRangeSummary? = null,
    onDayTap: (LocalDate) -> Unit = {},
    onSweep: (from: LocalDate, to: LocalDate) -> Unit = { _, _ -> },
    onClearSelection: () -> Unit = {},
    onRowClick: (Long) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
) {
    val spacing = SakuTheme.spacing

    // One list for grid, summary and rows, so the rows scroll with the grid instead of
    // sharing a third of the screen under it.
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        // Clears the floating add button, which otherwise sits on the last row.
        contentPadding = PaddingValues(bottom = FAB_CLEARANCE),
    ) {
        item(key = "weekdays") { WeekdayHeadings(Modifier.padding(horizontal = spacing.sm)) }
        item(key = "grid") {
            MonthGrid(
                month = month,
                selected = selection?.range,
                onDayTap = onDayTap,
                onSweep = onSweep,
                modifier = Modifier.padding(horizontal = spacing.sm),
            )
        }
        item(key = "summary") {
            if (selection == null) {
                Text(
                    text = stringResource(R.string.calendar_hint),
                    style = SakuTheme.typography.caption,
                    color = SakuTheme.colors.ink2,
                    modifier = Modifier.padding(horizontal = spacing.screen, vertical = spacing.md),
                )
            } else {
                RangeSummary(selection, onClearSelection)
            }
        }
        if (selection != null) daySectionItems(selection.days, onRowClick)
    }
}

@Composable
private fun WeekdayHeadings(modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        WEEKDAY_HEADINGS.forEach { heading ->
            Text(
                text = heading,
                style = SakuTheme.typography.caption,
                color = SakuTheme.colors.ink2,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(vertical = SakuTheme.spacing.sm),
            )
        }
    }
}

/**
 * The weeks as plain rows rather than a lazy grid: at most 42 cells, and a lazy grid
 * cannot sit inside the list that carries the rows below it.
 *
 * Taps and the long-press drag are read once, on the whole grid, and mapped to a cell
 * by position. A drag that crosses cells cannot be told apart from a tap by the cells
 * themselves, and a long press released in place would otherwise also fire the cell's
 * tap and undo the selection it just made.
 */
@Composable
private fun MonthGrid(
    month: CalendarMonth,
    selected: DateRange?,
    onDayTap: (LocalDate) -> Unit,
    onSweep: (LocalDate, LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowGap = SakuTheme.spacing.xs
    val rowGapPx = with(LocalDensity.current) { rowGap.toPx() }
    val haptics = LocalHapticFeedback.current
    val weeks = (month.leadingBlanks + month.cells.size + 6) / 7

    // The gesture loop outlives recompositions; these keep it reading the latest values.
    val currentMonth by rememberUpdatedState(month)
    val currentTap by rememberUpdatedState(onDayTap)
    val currentSweep by rememberUpdatedState(onSweep)

    Column(
        verticalArrangement = Arrangement.spacedBy(rowGap),
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val grid = currentMonth
                    val gridWeeks = (grid.leadingBlanks + grid.cells.size + 6) / 7
                    fun slotAt(at: Offset) =
                        calendarSlotAt(at.x, at.y, size.width, size.height, gridWeeks, rowGapPx)

                    val longPress = awaitLongPressOrCancellation(down.id)
                    if (longPress == null) {
                        // Released before the long-press timeout: a tap, unless the list
                        // took the gesture as a scroll, which consumes the change.
                        // A finger that slid onto another cell before lifting picks nothing.
                        val up = currentEvent.changes.firstOrNull { it.id == down.id }
                        val slot = slotAt(down.position)
                        if (up == null || !up.changedToUp() || up.isConsumed) return@awaitEachGesture
                        if (slotAt(up.position) != slot) return@awaitEachGesture
                        grid.dayAtSlot(slot)?.let(currentTap)
                        up.consume()
                        return@awaitEachGesture
                    }

                    // A long press on a blank lead-in cell starts on the 1st.
                    val anchor = grid.nearestDayAtSlot(slotAt(longPress.position))
                    var end = anchor
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentSweep(anchor, anchor)
                    longPress.consume()
                    drag(longPress.id) { change ->
                        val day = grid.nearestDayAtSlot(slotAt(change.position))
                        if (day != end) {
                            end = day
                            currentSweep(anchor, day)
                        }
                        change.consume()
                    }
                }
            },
    ) {
        for (week in 0 until weeks) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (column in 0 until 7) {
                    val cell = month.cells.getOrNull(week * 7 + column - month.leadingBlanks)
                    val cellModifier = Modifier.weight(1f).aspectRatio(1f)
                    if (cell == null) {
                        // Blanks carry no date, so they cannot be mistaken for days of
                        // this month, and TalkBack skips them.
                        Box(cellModifier)
                    } else {
                        DayCell(
                            cell = cell,
                            column = column,
                            selected = selected,
                            onTap = { onDayTap(cell.date) },
                            modifier = cellModifier,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    cell: CalendarCell,
    column: Int,
    selected: DateRange?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing
    val net = cell.net
    val inRange = selected != null && cell.date in selected
    val isStart = inRange && cell.date == selected?.start
    val isEnd = inRange && cell.date == selected?.endInclusive
    val isRangeEnd = isStart || isEnd

    val selectLabel = stringResource(R.string.calendar_select_date)
    val description = buildString {
        append(cell.date.dayHeaderLabel())
        append(if (net == null) ", nothing recorded" else ", net ${formatIdrSigned(net)}")
        if (isStart && !isEnd) append(", range start")
        if (isEnd && !isStart) append(", range end")
    }

    // The band runs unbroken along a week and rounds off where the range ends and where
    // the week wraps, so a range crossing a Sunday still reads as one piece per row.
    val roundStart = isStart || column == 0
    val roundEnd = isEnd || column == 6
    val band = RoundedCornerShape(
        topStart = if (roundStart) CELL_RADIUS else 0.dp,
        bottomStart = if (roundStart) CELL_RADIUS else 0.dp,
        topEnd = if (roundEnd) CELL_RADIUS else 0.dp,
        bottomEnd = if (roundEnd) CELL_RADIUS else 0.dp,
    )

    Column(
        modifier = modifier
            // Seven columns on a 360dp screen give a 45dp cell, under the 48dp target
            // the rest of the app holds to; the height at least makes the 48.
            .sizeIn(minHeight = spacing.touchTarget)
            .then(if (inRange) Modifier.background(colors.accentWash, band) else Modifier)
            // The ends carry an outline as well as the fill, so they read without colour.
            .then(
                if (isRangeEnd) {
                    Modifier.border(RANGE_END_STROKE, colors.accent, RoundedCornerShape(CELL_RADIUS))
                } else {
                    Modifier
                },
            )
            // Taps arrive through the grid's gesture handler; this is TalkBack's way in.
            // Without the date TalkBack read "14, -5m" with no month.
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                this.selected = inRange
                onClick(label = selectLabel) {
                    onTap()
                    true
                }
            }
            .padding(spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = cell.date.dayOfMonth.toString(),
            style = SakuTheme.typography.caption,
            color = if (cell.hasTransactions || inRange) colors.ink else colors.ink2,
            maxLines = 1,
        )
        if (net != null) {
            Text(
                text = formatIdrCell(net),
                style = SakuTheme.typography.caption,
                color = netColor(net),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/** The picked dates, what they add up to, and a way to drop them. */
@Composable
private fun RangeSummary(summary: CalendarRangeSummary, onClear: () -> Unit) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing
    val clearDescription = stringResource(R.string.calendar_clear_description)

    Column(modifier = Modifier.fillMaxWidth().background(colors.surface)) {
        HorizontalDivider(thickness = 1.dp, color = colors.line)
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = spacing.screen, end = spacing.sm, top = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val spoken = summary.range.spokenSelectionLabel()
            Text(
                text = summary.range.selectionLabel(),
                style = SakuTheme.typography.label,
                color = colors.ink,
                modifier = Modifier.weight(1f).semantics { contentDescription = spoken },
            )
            TextButton(
                onClick = onClear,
                modifier = Modifier.semantics { contentDescription = clearDescription },
            ) {
                Text(text = stringResource(R.string.calendar_clear), color = colors.accent)
            }
        }
        LeftOverFigures(
            totals = summary.totals,
            leftOverStyle = SakuTheme.typography.amountMedium,
            modifier = Modifier.padding(start = spacing.screen, end = spacing.screen, bottom = spacing.md),
        )
        if (summary.days.isEmpty()) {
            Text(
                text = stringResource(R.string.calendar_range_empty),
                style = SakuTheme.typography.caption,
                color = colors.ink2,
                modifier = Modifier.padding(start = spacing.screen, end = spacing.screen, bottom = spacing.md),
            )
        }
        HorizontalDivider(thickness = 1.dp, color = colors.line)
    }
}

@Composable
private fun netColor(net: Money) = when {
    net.isPositive -> SakuTheme.colors.income
    net.isNegative -> SakuTheme.colors.expense
    else -> SakuTheme.colors.ink2
}

/**
 * The grid slot under a point, counted from the top-left blank, row by row. Columns are
 * equal with no gap; rows are equal with [rowGapPx] between them, and a point in a gap
 * belongs to the row above. Points outside the grid clamp to its edge, so a drag that
 * strays off the side keeps tracking the nearest cell.
 */
internal fun calendarSlotAt(x: Float, y: Float, width: Int, height: Int, weeks: Int, rowGapPx: Float): Int {
    if (width <= 0 || height <= 0 || weeks <= 0) return 0
    val column = floor(x / (width / 7f)).toInt().coerceIn(0, 6)
    val rowHeight = (height - rowGapPx * (weeks - 1)) / weeks
    val row = floor(y / (rowHeight + rowGapPx)).toInt().coerceIn(0, weeks - 1)
    return row * 7 + column
}

/** The day in [slot], or null for a blank before the 1st or after the last day. */
internal fun CalendarMonth.dayAtSlot(slot: Int): LocalDate? = cells.getOrNull(slot - leadingBlanks)?.date

/** The day in [slot], or the nearest day of the month when [slot] is a blank. */
internal fun CalendarMonth.nearestDayAtSlot(slot: Int): LocalDate =
    cells[(slot - leadingBlanks).coerceIn(0, cells.lastIndex)].date

private val CELL_RADIUS = 10.dp
private val RANGE_END_STROKE = 2.dp

@SakuPreview
@Composable
private fun CalendarTabPreview() {
    val august = DateRange.ofMonth(LocalDate(2026, 8, 1))
    val month = emptyList<com.dvnnfrr.saku.domain.Transaction>()
        .toCalendarMonth(august, kotlinx.datetime.TimeZone.UTC)
    SakuPreviewSurface(padded = false) {
        CalendarTab(
            month = month,
            selection = CalendarRangeSummary(
                range = DateRange.custom(LocalDate(2026, 8, 6), LocalDate(2026, 8, 12)),
                totals = com.dvnnfrr.saku.domain.PeriodTotals(Money(1_000_000), Money(261_000)),
                days = emptyList(),
            ),
        )
    }
}
