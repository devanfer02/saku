package com.dvnnfrr.saku.ui.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme

/**
 * One series over time: a 2 dp line through [values] at [positions] (days, say), with a
 * marker on every point and two faint guides at the lowest and highest value. No axis
 * text: the host labels the ends and the extremes in words, and speaks a summary, so the
 * drawing itself is hidden from TalkBack.
 *
 * x follows [positions], so a long gap between two trips reads as a gap. Equal values draw
 * a flat line across the middle. [positions] must be ascending and the same length as
 * [values]; under two points nothing is drawn.
 */
@Composable
fun PriceLine(
    positions: List<Long>,
    values: List<Long>,
    modifier: Modifier = Modifier,
) {
    val colors = SakuTheme.colors
    Canvas(modifier = modifier.fillMaxWidth().height(CHART_HEIGHT).clearAndSetSemantics {}) {
        if (values.size < 2 || positions.size != values.size) return@Canvas
        val marker = MARKER_RADIUS.toPx()
        val ring = RING_WIDTH.toPx()
        // Inset by a marker and its ring so the end points are not cut by the edge.
        val inset = marker + ring
        val left = inset
        val right = size.width - inset
        val top = inset
        val bottom = size.height - inset

        val minX = positions.first()
        val spanX = (positions.last() - minX).coerceAtLeast(1L)
        val minY = values.min()
        val spanY = values.max() - minY

        fun x(i: Int): Float = left + (right - left) * (positions[i] - minX).toFloat() / spanX
        fun y(i: Int): Float =
            if (spanY == 0L) (top + bottom) / 2 else bottom - (bottom - top) * (values[i] - minY).toFloat() / spanY

        val guide = GUIDE_WIDTH.toPx()
        drawLine(colors.line, Offset(0f, top), Offset(size.width, top), strokeWidth = guide)
        drawLine(colors.line, Offset(0f, bottom), Offset(size.width, bottom), strokeWidth = guide)

        val path = Path().apply {
            moveTo(x(0), y(0))
            for (i in 1 until values.size) lineTo(x(i), y(i))
        }
        drawPath(
            path,
            color = colors.accent,
            style = Stroke(width = LINE_WIDTH.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        for (i in values.indices) {
            val centre = Offset(x(i), y(i))
            drawCircle(colors.surface, radius = marker + ring, center = centre)
            drawCircle(colors.accent, radius = marker, center = centre)
        }
    }
}

// Fixed dp, not sp: the chart keeps its shape at any font size; the words around it grow.
private val CHART_HEIGHT = 88.dp
private val LINE_WIDTH = 2.dp
private val MARKER_RADIUS = 4.dp
private val RING_WIDTH = 2.dp
private val GUIDE_WIDTH = 1.dp

@SakuPreview
@Composable
private fun PriceLinePreview() {
    SakuPreviewSurface {
        PriceLine(
            positions = listOf(0, 31, 45, 92),
            values = listOf(35_000, 36_000, 41_000, 38_900),
        )
    }
}
