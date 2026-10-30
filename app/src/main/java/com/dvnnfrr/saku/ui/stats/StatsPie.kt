package com.dvnnfrr.saku.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.forSlotOrNeutral
import com.dvnnfrr.saku.ui.theme.formatIdr
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * One slice: its colour, already resolved, its sweep from `pieSweeps`, and what the hole
 * says about it once it is tapped.
 */
@Immutable
data class PieSlice(
    val color: Color,
    val sweepTenths: Int,
    val name: String = "",
    val percent: Int = 0,
    val amount: Money = Money.ZERO,
)

/**
 * A ring of slices with a total in its hole, drawn above the ranked rows that are its
 * legend. Never shown without those rows: the pie answers "what share" at a glance and
 * the rows carry the names, the percents and the amounts.
 *
 * A thin gap separates neighbouring slices, the second cue for a reader who cannot tell
 * two adjacent hues apart.
 *
 * The centre is measured, not trusted. An eight-digit total at the amount style is wider
 * than the hole, and the old account donut showed only "Rp" when it was left to wrap, so
 * each line is shrunk until it fits the chord of the hole at that height. It never wraps
 * and never clips.
 *
 * Tapping a slice highlights it: it draws thicker, the rest fade, and the hole names it
 * with its whole percent large and its amount small. Tapping it again, or the hole,
 * returns to the total. The highlight is a passing view state, like a pressed row, so it
 * lives here rather than in the ViewModel; it survives a theme change and resets when the
 * slices change.
 */
@Composable
fun StatsPie(
    slices: List<PieSlice>,
    centerLabel: String,
    centerValue: String,
    modifier: Modifier = Modifier,
) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing
    val typography = SakuTheme.typography
    val density = LocalDensity.current

    val diameterPx = with(density) { PIE_SIZE.toPx() }
    val strokePx = with(density) { PIE_STROKE.toPx() }
    val gapPx = with(density) { PIE_GAP.toPx() }
    val drawn = slices.filter { it.sweepTenths > 0 }
    val centerline = diameterPx - strokePx
    val gapDegrees = if (drawn.size < 2) 0f else gapPx / (Math.PI.toFloat() * centerline) * FULL_TURN

    var highlighted by rememberSaveable(drawn) { mutableStateOf<Int?>(null) }
    val focus = highlighted?.let(drawn::getOrNull)

    val textMeasurer = rememberTextMeasurer()
    val lineWidthPx = with(density) {
        val blockHeight = typography.label.lineHeight.toPx() + spacing.xs.toPx() +
            typography.amountLarge.lineHeight.toPx()
        holeTextWidth(diameterPx - 2 * strokePx, blockHeight) - 2 * spacing.xs.toPx()
    }
    val labelStyle = remember(centerLabel, typography.label, lineWidthPx, textMeasurer) {
        textMeasurer.fitOnOneLine(centerLabel, typography.label, lineWidthPx)
    }
    val valueStyle = remember(centerValue, typography.amountLarge, lineWidthPx, textMeasurer) {
        textMeasurer.fitOnOneLine(centerValue, typography.amountLarge, lineWidthPx)
    }
    // Three lines when a slice is focused, so the hole is narrower where they sit.
    val focusWidthPx = with(density) {
        val blockHeight = typography.label.lineHeight.toPx() + typography.amountLarge.lineHeight.toPx() +
            typography.caption.lineHeight.toPx() + 2 * spacing.xs.toPx()
        holeTextWidth(diameterPx - 2 * strokePx, blockHeight) - 2 * spacing.xs.toPx()
    }
    val focusName = focus?.name.orEmpty()
    val focusNameStyle = remember(focusName, typography.label, focusWidthPx, textMeasurer) {
        textMeasurer.fitOnOneLine(focusName, typography.label, focusWidthPx)
    }
    val focusAmount = focus?.let { formatIdr(it.amount) }.orEmpty()
    val focusAmountStyle = remember(focusAmount, typography.caption, focusWidthPx, textMeasurer) {
        textMeasurer.fitOnOneLine(focusAmount, typography.caption, focusWidthPx)
    }
    val description = focus?.let { "${it.name}, ${it.percent}%, $focusAmount" } ?: "$centerLabel $centerValue"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = spacing.md),
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(PIE_SIZE)
                // The rows below speak every slice; the pie speaks the total, or the slice
                // a sighted user has tapped.
                .clearAndSetSemantics { contentDescription = description }
                .pointerInput(drawn) {
                    detectTapGestures { tap ->
                        val index = sliceAt(tap, diameterPx, strokePx, drawn.map { it.sweepTenths })
                        highlighted = if (index == null || index == highlighted) null else index
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(PIE_SIZE)) {
                val inset = strokePx / 2f
                val arcSize = Size(centerline, centerline)
                val topLeft = Offset(inset, inset)
                val stroke = Stroke(width = strokePx)
                drawArc(colors.surfaceAlt, 0f, FULL_TURN, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)

                val focusStroke = Stroke(width = strokePx * FOCUS_STROKE_SCALE)
                var start = START_DEGREES
                drawn.forEachIndexed { index, slice ->
                    val sweep = slice.sweepTenths / TENTHS
                    val visible = (sweep - gapDegrees).coerceAtLeast(MIN_VISIBLE_DEGREES)
                    val isFocus = index == highlighted
                    val color = if (highlighted == null || isFocus) slice.color else slice.color.copy(alpha = FADED_ALPHA)
                    drawArc(
                        color, start, visible, useCenter = false, topLeft = topLeft, size = arcSize,
                        style = if (isFocus) focusStroke else stroke,
                    )
                    start += sweep
                }
            }
            if (focus != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    Text(focusName, style = focusNameStyle, color = colors.ink2, softWrap = false, maxLines = 1)
                    Text(
                        text = "${focus.percent}%",
                        style = typography.amountLarge,
                        color = colors.ink,
                        softWrap = false,
                        maxLines = 1,
                    )
                    Text(focusAmount, style = focusAmountStyle, color = colors.ink2, softWrap = false, maxLines = 1)
                }
                return@Box
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
}

/**
 * The index of the slice under [tap], or null for the hole or outside the ring. Angles
 * run clockwise from twelve o'clock, as the slices are drawn. Half a ring's width of
 * slack either side, so a thin slice is still a target.
 */
internal fun sliceAt(tap: Offset, diameterPx: Float, strokePx: Float, sweepTenths: List<Int>): Int? {
    val radius = diameterPx / 2f
    val dx = tap.x - radius
    val dy = tap.y - radius
    val distance = sqrt(dx * dx + dy * dy)
    val outsideRing = distance < radius - strokePx * (1f + TAP_SLACK) || distance > radius + strokePx * TAP_SLACK
    if (outsideRing || sweepTenths.isEmpty()) return null
    val degrees = (Math.toDegrees(atan2(dy, dx).toDouble()) - START_DEGREES + FULL_TURN) % FULL_TURN
    val tenths = (degrees * TENTHS).toInt()
    var end = 0
    sweepTenths.forEachIndexed { index, sweep ->
        end += sweep
        if (tenths < end) return index
    }
    return sweepTenths.lastIndex
}

/**
 * How wide a line the hole holds for a block [blockHeightPx] tall centred in it: the
 * chord at the block's top and bottom edge, narrower than the diameter. Zero when the
 * block is taller than the hole.
 */
internal fun holeTextWidth(holeDiameterPx: Float, blockHeightPx: Float): Float {
    val radius = holeDiameterPx / 2f
    val halfHeight = blockHeightPx / 2f
    if (radius <= 0f || halfHeight >= radius) return 0f
    return 2f * sqrt(radius * radius - halfHeight * halfHeight)
}

/**
 * The factor a font size is multiplied by so a line [measuredWidthPx] wide fits into
 * [availableWidthPx]. Never above 1: text that fits keeps its size, and text that does
 * not shrinks rather than wrapping.
 */
internal fun textFitScale(measuredWidthPx: Float, availableWidthPx: Float): Float {
    if (measuredWidthPx <= availableWidthPx || measuredWidthPx <= 0f) return 1f
    return (availableWidthPx / measuredWidthPx).coerceAtLeast(0f)
}

/**
 * [style] shrunk until [text] fits on one line [maxWidthPx] wide. Measured again after
 * each step because glyph widths round to whole pixels and do not scale exactly.
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
private const val FULL_TURN = 360f
private const val TENTHS = 10f

/** Twelve o'clock in `drawArc`'s coordinates. */
private const val START_DEGREES = -90f

/** A sliver below the gap still shows as a tick rather than vanishing. */
private const val MIN_VISIBLE_DEGREES = 0.5f

private const val FOCUS_STROKE_SCALE = 1.3f
private const val FADED_ALPHA = 0.3f
private const val TAP_SLACK = 0.5f

private val PIE_SIZE = 184.dp
private val PIE_STROKE = 28.dp
private val PIE_GAP = 2.dp

@SakuPreview
@Composable
private fun StatsPiePreview() {
    val totals = listOf(4_770_000L, 2_805_000L, 2_244_000L, 2_244_000L, 1_263_400L, 700_000L)
    val sweeps = pieSweeps(totals.map(::Money))
    SakuPreviewSurface(padded = false) {
        Column {
            StatsPie(
                slices = sweeps.mapIndexed { index, sweep ->
                    PieSlice(SakuTheme.colors.forSlotOrNeutral(ColorSlot(index)), sweep)
                },
                centerLabel = "Spent",
                centerValue = formatIdr(Money(14_026_400)),
            )
            // Wider than the hole at the amount style, so it has to shrink to fit.
            StatsPie(
                slices = listOf(PieSlice(SakuTheme.colors.forSlotOrNeutral(null), 3_600)),
                centerLabel = "Kendaraan Pribadi",
                centerValue = formatIdr(Money(1_234_567_890)),
            )
        }
    }
}
