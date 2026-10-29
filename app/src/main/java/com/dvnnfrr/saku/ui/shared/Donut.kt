package com.dvnnfrr.saku.ui.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.forSlotOrNeutral
import com.dvnnfrr.saku.ui.theme.formatIdr
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * A ring of categorical segments with a figure in the middle.
 *
 * The 2dp gap between segments is not decoration — it is the second cue that keeps the
 * chart legible for a reader who cannot separate two adjacent hues. Two of the palette's
 * adjacent pairs sit in the marginal band under deuteranopia, which is only acceptable
 * because of this gap and the labelled list every consumer places beside the ring. Never
 * ship this component without that list.
 */
@Composable
fun Donut(
    segments: List<DonutSegment>,
    centerLabel: String,
    centerValue: String,
    modifier: Modifier = Modifier,
    size: Dp = SakuTheme.spacing.donutSize,
    strokeWidth: Dp = SakuTheme.spacing.donutStroke,
    /**
     * Index into [segments] of the segment to emphasise, or null for none. Unselected
     * arcs thin out rather than the selected one thickening, which keeps every arc
     * inside the ring the geometry was laid out for.
     */
    selectedIndex: Int? = null,
    /**
     * Tapping an arc reports its index in [segments]; tapping the hole, or a point on
     * the ring no arc covers, reports null so the caller can clear its selection.
     *
     * Null leaves the ring inert, which is what it was before it had a consumer. The
     * chart must never be the only way to reach a segment: the labelled list beside it
     * is the path for anyone not aiming at an 18dp band, and this is an enhancement on
     * top of that rather than a replacement for it.
     */
    onSegmentSelect: ((Int?) -> Unit)? = null,
) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing
    val density = LocalDensity.current

    val strokePx = with(density) { strokeWidth.toPx() }
    val diameterPx = with(density) { size.toPx() }
    val gapPx = with(density) { spacing.donutSegmentGap.toPx() }
    val touchTargetPx = with(density) { spacing.touchTarget.toPx() }
    val centerlineDiameter = diameterPx - strokePx
    val gapDegrees = donutGapDegrees(gapPx, centerlineDiameter)
    val arcs = donutArcs(segments, gapDegrees)

    // donutArcs drops zero and negative segments, so arc i is not segment i. Mapping
    // back through the same predicate keeps every index this component hands out — and
    // takes in — an index into the caller's own list, which is the only one it can act
    // on. Reproduced rather than exported from the geometry, which no consumer but this
    // one needs to know about.
    val drawnIndices = remember(segments) { segments.indices.filter { segments[it].value > 0L } }
    val selectedArc = drawnIndices.indexOf(selectedIndex)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .size(size)
                .semantics { contentDescription = donutDescription(centerLabel, centerValue, arcs) }
                .then(
                    if (onSegmentSelect == null) {
                        Modifier
                    } else {
                        Modifier.pointerInput(arcs, gapDegrees) {
                            detectTapGestures { tap ->
                                val hit = donutHitTest(
                                    tap = tap,
                                    center = Offset(this.size.width / 2f, this.size.height / 2f),
                                    centerlineRadius = centerlineDiameter / 2f,
                                    // The ring is 18dp thick and a tap target is 48dp,
                                    // so the band that answers a tap is widened to the
                                    // target rather than to what is painted.
                                    bandWidth = max(strokePx, touchTargetPx),
                                    arcs = arcs,
                                    toleranceDegrees = gapDegrees,
                                )
                                onSegmentSelect(hit?.let(drawnIndices::get))
                            }
                        }
                    },
                ),
        ) {
            val inset = strokePx / 2f
            val arcSize = Size(centerlineDiameter, centerlineDiameter)
            val topLeft = Offset(inset, inset)
            val stroke = Stroke(width = strokePx)
            // Only once something is selected. With no selection every arc keeps the
            // full stroke, so a chart nobody has touched looks exactly as it always did.
            val mutedStroke =
                if (selectedArc < 0) stroke else Stroke(width = strokePx * MUTED_STROKE_SCALE)

            drawArc(
                color = colors.surfaceAlt,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
            arcs.forEachIndexed { index, arc ->
                drawArc(
                    color = colors.forSlotOrNeutral(arc.slot),
                    startAngle = arc.startDegrees,
                    sweepAngle = arc.sweepDegrees,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = if (index == selectedArc) stroke else mutedStroke,
                )
            }
        }

        // The centre has to sit inside the hole, not inside the box. An eight-digit
        // balance at amountLarge is wider than the hole, and left to wrap it broke after
        // "Rp" and showed nothing else, so each line is measured and shrunk until it fits.
        val typography = SakuTheme.typography
        val textMeasurer = rememberTextMeasurer()
        val lineWidthPx = with(density) {
            val blockHeight = typography.label.lineHeight.toPx() + spacing.xs.toPx() +
                typography.amountLarge.lineHeight.toPx()
            donutHoleTextWidth(diameterPx - 2 * strokePx, blockHeight) - 2 * spacing.xs.toPx()
        }
        val labelStyle = remember(centerLabel, typography.label, lineWidthPx, textMeasurer) {
            textMeasurer.fitOnOneLine(centerLabel, typography.label, lineWidthPx)
        }
        val valueStyle = remember(centerValue, typography.amountLarge, lineWidthPx, textMeasurer) {
            textMeasurer.fitOnOneLine(centerValue, typography.amountLarge, lineWidthPx)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = centerLabel,
                style = labelStyle,
                color = colors.ink2,
                textAlign = TextAlign.Center,
                softWrap = false,
                maxLines = 1,
            )
            Text(
                text = centerValue,
                style = valueStyle,
                color = colors.ink,
                textAlign = TextAlign.Center,
                softWrap = false,
                maxLines = 1,
            )
        }
    }
}

/**
 * [style] shrunk until [text] fits on one line [maxWidthPx] wide. Measured again after
 * each step because glyph widths round to whole pixels and do not scale exactly with
 * the font size.
 */
private fun TextMeasurer.fitOnOneLine(text: String, style: TextStyle, maxWidthPx: Float): TextStyle {
    var fitted = style
    repeat(FIT_ATTEMPTS) {
        val width = measure(text, fitted, softWrap = false, maxLines = 1).size.width.toFloat()
        val scale = textFitScale(width, maxWidthPx)
        if (scale >= 1f) return fitted
        fitted = fitted.copy(
            fontSize = fitted.fontSize * scale,
            lineHeight = if (fitted.lineHeight.isSpecified) fitted.lineHeight * scale else fitted.lineHeight,
        )
    }
    return fitted
}

private const val FIT_ATTEMPTS = 3

/** Overload for the common case where the centre figure is an amount. */
@Composable
fun Donut(
    segments: List<DonutSegment>,
    centerLabel: String,
    centerValue: Money,
    modifier: Modifier = Modifier,
    size: Dp = SakuTheme.spacing.donutSize,
    strokeWidth: Dp = SakuTheme.spacing.donutStroke,
    selectedIndex: Int? = null,
    onSegmentSelect: ((Int?) -> Unit)? = null,
) {
    Donut(
        segments = segments,
        centerLabel = centerLabel,
        centerValue = formatIdr(centerValue),
        modifier = modifier,
        size = size,
        strokeWidth = strokeWidth,
        selectedIndex = selectedIndex,
        onSegmentSelect = onSegmentSelect,
    )
}

/** How much of its stroke an unselected arc keeps while another one is selected. */
private const val MUTED_STROKE_SCALE = 0.55f

private const val FULL_CIRCLE_DEGREES = 360f

/**
 * Which arc a tap landed on, as an index into [arcs], or null for a miss.
 *
 * Pure, and separate from the composable, because this is the part worth testing: the
 * angle arithmetic wraps at twelve o'clock, where [DONUT_START_DEGREES] puts the first
 * arc's edge, and an off-by-one-revolution there is invisible until a user taps the
 * first slice and selects the last.
 *
 * [bandWidth] is the full radial thickness that answers a tap, centred on
 * [centerlineRadius] — wider than the painted ring, so the target can meet 48dp without
 * the chart having to be drawn that thick. [toleranceDegrees] closes the gaps between
 * arcs: they exist so adjacent hues stay distinguishable, not so taps can fall down
 * them, so a tap in a gap goes to the nearer of its neighbours.
 */
internal fun donutHitTest(
    tap: Offset,
    center: Offset,
    centerlineRadius: Float,
    bandWidth: Float,
    arcs: List<DonutArc>,
    toleranceDegrees: Float = 0f,
): Int? {
    if (arcs.isEmpty()) return null

    val dx = tap.x - center.x
    val dy = tap.y - center.y
    val distance = hypot(dx, dy)
    val halfBand = bandWidth / 2f
    // Never below zero: an inner radius that went negative would make the hole tappable
    // and a tap on the centre figure would select whichever arc happened to be behind it.
    val inner = max(0f, centerlineRadius - halfBand)
    if (distance < inner || distance > centerlineRadius + halfBand) return null

    // atan2 measures from three o'clock and grows clockwise once y points down, which is
    // exactly the convention drawArc uses, so the arc angles need no conversion.
    val angle = normalizeDegrees(Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat())

    var best = -1
    var bestDistance = Float.MAX_VALUE
    arcs.forEachIndexed { index, arc ->
        val offset = normalizeDegrees(angle - arc.startDegrees)
        val distanceToArc = if (offset <= arc.sweepDegrees) {
            0f
        } else {
            min(offset - arc.sweepDegrees, FULL_CIRCLE_DEGREES - offset)
        }
        if (distanceToArc < bestDistance) {
            bestDistance = distanceToArc
            best = index
        }
    }
    return if (bestDistance <= toleranceDegrees) best else null
}

private fun normalizeDegrees(degrees: Float): Float =
    ((degrees % FULL_CIRCLE_DEGREES) + FULL_CIRCLE_DEGREES) % FULL_CIRCLE_DEGREES

private fun donutDescription(label: String, value: String, arcs: List<DonutArc>): String =
    if (arcs.isEmpty()) {
        "$label $value, no segments"
    } else {
        "$label $value. " + arcs.joinToString(", ") { it.label }
    }

@SakuPreview
@Composable
private fun DonutPreview() {
    SakuPreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(SakuTheme.spacing.lg)) {
            Donut(
                segments = listOf(
                    DonutSegment(ColorSlot(0), 8_400_000, "Pemberian"),
                    DonutSegment(ColorSlot(1), 3_100_000, "Makan & Minum"),
                    DonutSegment(ColorSlot(2), 1_800_000, "Kebutuhan Kos"),
                    DonutSegment(ColorSlot(3), 900_000, "Kuliah"),
                    DonutSegment(ColorSlot(4), 0, "Gym"),
                ),
                centerLabel = "Expenses",
                centerValue = Money(14_200_000),
            )
            Donut(
                segments = emptyList(),
                centerLabel = "Expenses",
                centerValue = Money.ZERO,
            )
            // Wider than the hole at amountLarge, so it has to shrink to fit.
            Donut(
                segments = listOf(DonutSegment(ColorSlot(0), 18_000_000, "RDPU SucorInvest")),
                centerLabel = "RDPU SucorInvest",
                centerValue = Money(18_000_000),
            )
        }
    }
}
