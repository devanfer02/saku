package com.dvnnfrr.saku.ui.search

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.ui.shared.MoneyText
import com.dvnnfrr.saku.ui.shared.cappedForSlot
import com.dvnnfrr.saku.ui.shared.periodLabel
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.formatIdr
import com.dvnnfrr.saku.ui.theme.formatIdrAxis
import com.dvnnfrr.saku.ui.transactions.dateLabel
import com.dvnnfrr.saku.ui.transactions.shortLabel
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

/**
 * The price card's chart: unit price over time on a Y axis of round rupiah, the first, a
 * middle and the last date under it. A 1.5 dp line, small markers while the points are far
 * enough apart to tell, and always one on the latest point.
 *
 * Tapping or dragging across the plot selects the nearest point by date: a guide drops
 * through it, its marker grows a ring, and a label above the plot reads its date and price.
 * Tapping the selected point again clears it. TalkBack reads the chart as one adjustable
 * control: swiping up or down (or the Next and Previous actions) steps through the points
 * and speaks each one, `28 Jul 2025, Rp 100.000`.
 *
 * Fixed dp heights, and axis text capped like other slot labels, so the plot keeps its
 * shape at 200% font.
 */
@Composable
fun PriceLineChart(
    chart: PriceChart,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SakuTheme.colors
    val labelStyle = SakuTheme.typography.caption.cappedForSlot()
    val points = chart.points
    val positions = remember(points) { points.map { it.position } }
    val ticks = chart.axis.ticks
    val xLabels = xAxisLabels(chart)
    val current by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)

    val spokenPoints = points.map { spokenPoint(it, chart.monthly) }
    val description = stringResource(
        if (chart.monthly) R.string.price_chart_spoken_monthly else R.string.price_chart_spoken,
        points.size,
        pointLabel(points.first(), chart.monthly),
        pointLabel(points.last(), chart.monthly),
    )
    val noneSelected = stringResource(R.string.price_chart_none_selected)
    val nextLabel = stringResource(R.string.price_point_next)
    val previousLabel = stringResource(R.string.price_point_previous)
    val last = points.lastIndex

    Layout(
        content = {
            ticks.forEach { tick ->
                Text(text = formatIdrAxis(tick), style = labelStyle, color = colors.ink2, maxLines = 1, softWrap = false)
            }
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(positions) {
                        detectTapGestures { offset ->
                            val index = indexAt(offset.x, size.width.toFloat(), positions, PLOT_INSET.toPx())
                            select(if (index == current) null else index)
                        }
                    }
                    .pointerInput(positions) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                select(indexAt(offset.x, size.width.toFloat(), positions, PLOT_INSET.toPx()))
                            },
                        ) { change, _ ->
                            change.consume()
                            select(indexAt(change.position.x, size.width.toFloat(), positions, PLOT_INSET.toPx()))
                        }
                    },
            ) {
                val geometry = PlotGeometry(size.width, size.height, PLOT_INSET.toPx(), PLOT_PAD.toPx(), chart)
                val hairline = GRID_WIDTH.toPx()
                ticks.forEach { tick ->
                    val y = geometry.y(tick)
                    drawLine(colors.line, Offset(0f, y), Offset(size.width, y), strokeWidth = hairline)
                }
                selected?.let { index ->
                    val x = geometry.x(points[index].position)
                    drawLine(colors.ink3, Offset(x, 0f), Offset(x, size.height), strokeWidth = hairline)
                }
                val path = Path().apply {
                    points.forEachIndexed { i, point ->
                        val x = geometry.x(point.position)
                        val y = geometry.y(point.unitPrice)
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
                drawPath(
                    path,
                    color = colors.accent,
                    style = Stroke(width = LINE_WIDTH.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                val ring = RING_WIDTH.toPx()
                val marker = MARKER_RADIUS.toPx()
                // Markers only while neighbours sit a few marker widths apart on average;
                // past that they merge into a bead chain and the line says it better.
                val roomy = (size.width - 2 * geometry.inset) / last >= MIN_MARKER_GAP.toPx()
                points.forEachIndexed { i, point ->
                    if (!roomy && i != last) return@forEachIndexed
                    val centre = Offset(geometry.x(point.position), geometry.y(point.unitPrice))
                    drawCircle(colors.surface, radius = marker + ring, center = centre)
                    drawCircle(colors.accent, radius = marker, center = centre)
                }
                selected?.let { index ->
                    val point = points[index]
                    val centre = Offset(geometry.x(point.position), geometry.y(point.unitPrice))
                    val dot = SELECTED_RADIUS.toPx()
                    drawCircle(colors.surface, radius = SELECTED_RING_RADIUS.toPx() + ring, center = centre)
                    drawCircle(colors.accent, radius = SELECTED_RING_RADIUS.toPx(), center = centre, style = Stroke(RING_STROKE.toPx()))
                    drawCircle(colors.accent, radius = dot, center = centre)
                }
            }
            if (selected != null) {
                Readout(points[selected], chart.monthly)
            } else {
                Text(text = stringResource(R.string.price_chart_hint), style = labelStyle, color = colors.ink2, maxLines = 1)
            }
            xLabels.forEach { label ->
                Text(text = label.text, style = labelStyle, color = colors.ink2, maxLines = 1, softWrap = false)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = description
                stateDescription = selected?.let { spokenPoints[it] } ?: noneSelected
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = (selected ?: 0).toFloat(),
                    range = 0f..last.toFloat(),
                    steps = (last - 1).coerceAtLeast(0),
                )
                setProgress { target ->
                    onSelect(target.roundToInt().coerceIn(0, last))
                    true
                }
                customActions = listOf(
                    CustomAccessibilityAction(nextLabel) {
                        onSelect(((selected ?: -1) + 1).coerceAtMost(last))
                        true
                    },
                    CustomAccessibilityAction(previousLabel) {
                        onSelect(((selected ?: last + 1) - 1).coerceAtLeast(0))
                        true
                    },
                )
            },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val tickPlaceables = measurables.take(ticks.size).map { it.measure(loose) }
        val gutter = (tickPlaceables.maxOfOrNull { it.width } ?: 0) + AXIS_GAP.roundToPx()
        val plotWidth = (width - gutter).coerceAtLeast(0)
        val plotHeight = PLOT_HEIGHT.roundToPx()
        val plot = measurables[ticks.size].measure(androidx.compose.ui.unit.Constraints.fixed(plotWidth, plotHeight))
        val readout = measurables[ticks.size + 1].measure(loose)
        val xPlaceables = measurables.drop(ticks.size + 2).map { it.measure(loose) }

        val band = maxOf(readout.height, READOUT_BAND.roundToPx())
        val plotTop = band + AXIS_GAP.roundToPx()
        val xTop = plotTop + plotHeight + AXIS_GAP.roundToPx()
        val height = xTop + (xPlaceables.maxOfOrNull { it.height } ?: 0)
        val geometry = PlotGeometry(plotWidth.toFloat(), plotHeight.toFloat(), PLOT_INSET.toPx(), PLOT_PAD.toPx(), chart)

        layout(width, height) {
            tickPlaceables.forEachIndexed { i, placeable ->
                val y = plotTop + geometry.y(ticks[i]).roundToInt() - placeable.height / 2
                placeable.place(x = gutter - AXIS_GAP.roundToPx() - placeable.width, y = y)
            }
            plot.place(gutter, plotTop)
            val readoutX = if (selected != null) {
                val centre = gutter + geometry.x(points[selected].position).roundToInt()
                (centre - readout.width / 2).coerceIn(0, (width - readout.width).coerceAtLeast(0))
            } else {
                gutter
            }
            readout.place(readoutX, (band - readout.height) / 2)

            // First at the plot's left, last at its right, the middle centred when it fits.
            val gap = AXIS_GAP.roundToPx()
            val first = xPlaceables.first()
            val lastLabel = xPlaceables.last()
            first.place(gutter, xTop)
            lastLabel.place(width - lastLabel.width, xTop)
            if (xPlaceables.size == 3) {
                val middle = xPlaceables[1]
                val x = gutter + plotWidth / 2 - middle.width / 2
                val fits = x >= gutter + first.width + gap && x + middle.width + gap <= width - lastLabel.width
                if (fits) middle.place(x, xTop)
            }
        }
    }
}

/** Where a price and a date land in a plot [width] by [height], in px. */
private class PlotGeometry(
    width: Float,
    height: Float,
    val inset: Float,
    pad: Float,
    chart: PriceChart,
) {
    private val first = chart.points.first().position
    private val span = (chart.points.last().position - first).coerceAtLeast(1L).toFloat()
    private val low = chart.axis.low.rupiah
    private val range = (chart.axis.high.rupiah - low).coerceAtLeast(1L).toFloat()
    private val left = inset
    private val usable = (width - 2 * inset).coerceAtLeast(1f)
    private val top = pad
    private val tall = (height - 2 * pad).coerceAtLeast(1f)

    fun x(position: Long): Float = left + usable * (position - first) / span
    fun y(price: Money): Float = top + tall * (1f - (price.rupiah - low) / range)
}

/** The point whose date is nearest a touch at [x] px across a plot [width] px wide. */
private fun indexAt(x: Float, width: Float, positions: List<Long>, inset: Float): Int {
    val first = positions.first()
    val span = (positions.last() - first).toDouble()
    val fraction = ((x - inset) / (width - 2 * inset).coerceAtLeast(1f)).toDouble()
    return nearestPoint(positions, first + fraction * span)
}

/** The selected point's date and price, the price in [MoneyText] like every amount. */
@Composable
private fun Readout(point: ChartPoint, monthly: Boolean) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing
    Row(
        modifier = Modifier
            .background(colors.surfaceAlt, RoundedCornerShape(spacing.sm))
            .padding(horizontal = spacing.sm, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val label = pointLabel(point, monthly)
        Text(
            text = if (monthly) stringResource(R.string.price_point_month_average, label) else label,
            style = SakuTheme.typography.caption.cappedForSlot(),
            color = colors.ink2,
            maxLines = 1,
        )
        MoneyText(point.unitPrice, style = SakuTheme.typography.amountSmall.cappedForSlot())
    }
}

private class AxisLabel(val text: String)

/** First, middle and last dates under the plot: days on a daily chart, months on a monthly one. */
private fun xAxisLabels(chart: PriceChart): List<AxisLabel> {
    val first = chart.points.first()
    val last = chart.points.last()
    val middle = LocalDate.fromEpochDays(((first.position + last.position) / 2).toInt())
    return listOf(first.date, middle, last.date).map { AxisLabel(dateText(it, chart.monthly)) }
}

private fun pointLabel(point: ChartPoint, monthly: Boolean): String = dateText(point.date, monthly)

/** `28 Jul 2025` on a daily chart, `Jul 2025` on a monthly one. */
private fun dateText(date: LocalDate, monthly: Boolean): String =
    if (monthly) "${date.month.shortLabel()} ${date.year}" else date.dateLabel()

/** `28 Jul 2025, Rp 100.000`, or `July 2025, average Rp 100.000`. */
@Composable
private fun spokenPoint(point: ChartPoint, monthly: Boolean): String =
    if (monthly) {
        stringResource(R.string.price_point_spoken_month, periodLabel(DateRange.ofMonth(point.date)), formatIdr(point.unitPrice))
    } else {
        stringResource(R.string.price_point_spoken, point.date.dateLabel(), formatIdr(point.unitPrice))
    }

// Fixed dp, not sp: the plot keeps its shape at any font size.
private val PLOT_HEIGHT = 136.dp
private val READOUT_BAND = 28.dp
private val AXIS_GAP = 6.dp
private val PLOT_INSET = 10.dp
private val PLOT_PAD = 8.dp
private val LINE_WIDTH = 1.5.dp
private val GRID_WIDTH = 1.dp
private val MARKER_RADIUS = 3.dp
private val RING_WIDTH = 1.5.dp
private val SELECTED_RADIUS = 4.dp
private val SELECTED_RING_RADIUS = 7.dp
private val RING_STROKE = 1.5.dp
private val MIN_MARKER_GAP: Dp = 12.dp

@SakuPreview
@Composable
private fun PriceLineChartPreview() {
    val points = listOf(
        PricePoint(LocalDate(2025, 7, 28), Money(35_000)),
        PricePoint(LocalDate(2025, 10, 9), Money(36_000)),
        PricePoint(LocalDate(2026, 3, 14), Money(41_000)),
        PricePoint(LocalDate(2026, 10, 25), Money(38_900)),
    )
    var selected by remember { mutableStateOf<Int?>(2) }
    SakuPreviewSurface {
        PriceLineChart(chart = priceChart(points), selected = selected, onSelect = { selected = it })
    }
}
