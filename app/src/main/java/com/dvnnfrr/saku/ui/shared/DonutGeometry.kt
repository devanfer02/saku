package com.dvnnfrr.saku.ui.shared

import androidx.compose.runtime.Immutable
import com.dvnnfrr.saku.domain.ColorSlot

/** One slice of a donut: a colour identity, a magnitude in whole rupiah, and its name. */
@Immutable
data class DonutSegment(
    /**
     * Null for a segment that owns no slot — the stats screen's aggregated "Other".
     * Colour is identity and a slot is pinned to an entity for life, so "Other" cannot
     * borrow one; it takes the neutral chart colour instead.
     */
    val slot: ColorSlot?,
    val value: Long,
    val label: String,
)

/** A [DonutSegment] resolved to a position on the ring. Degrees are clockwise from 12. */
@Immutable
data class DonutArc(
    /**
     * Null for a segment that owns no slot — the stats screen's aggregated "Other".
     * Colour is identity and a slot is pinned to an entity for life, so "Other" cannot
     * borrow one; it takes the neutral chart colour instead.
     */
    val slot: ColorSlot?,
    val label: String,
    val value: Long,
    val startDegrees: Float,
    val sweepDegrees: Float,
)

/** Twelve o'clock, in the coordinate system `drawArc` uses. */
const val DONUT_START_DEGREES = -90f

private const val FULL_CIRCLE = 360f

/**
 * Lays segments out around the ring.
 *
 * Three rules, all of them requirements rather than styling:
 *
 * - Zero and negative values are omitted. A slice of nothing is not a slice, and a
 *   negative one would eat its neighbours' share.
 * - Every arc is followed by a gap of [gapDegrees], including the last, so the spacing
 *   is even the whole way round. With a single segment there is no neighbour to separate
 *   from, so the ring closes.
 * - Each arc's bounds come from an exact `Long` ratio against the running total rather
 *   than from accumulating per-segment angles, so rounding cannot drift the last slice.
 *
 * Split out from the composable because these rules are the part worth testing, and a
 * JVM test can reach a pure function.
 */
fun donutArcs(segments: List<DonutSegment>, gapDegrees: Float = 0f): List<DonutArc> {
    val drawn = segments.filter { it.value > 0L }
    if (drawn.isEmpty()) return emptyList()

    val total = drawn.sumOf { it.value }
    if (total <= 0L) return emptyList()

    val gap = when {
        drawn.size < 2 -> 0f
        gapDegrees <= 0f -> 0f
        // A pathological segment count would leave no ring to draw; drop the gaps rather
        // than emit negative sweeps.
        gapDegrees * drawn.size >= FULL_CIRCLE -> 0f
        else -> gapDegrees
    }
    val available = FULL_CIRCLE - gap * drawn.size

    var consumed = 0L
    return drawn.mapIndexed { index, segment ->
        val startFraction = consumed.toDouble() / total
        consumed += segment.value
        val endFraction = consumed.toDouble() / total
        DonutArc(
            slot = segment.slot,
            label = segment.label,
            value = segment.value,
            startDegrees = DONUT_START_DEGREES + (startFraction * available).toFloat() + gap * index,
            sweepDegrees = ((endFraction - startFraction) * available).toFloat(),
        )
    }
}

/**
 * The gap in degrees that renders as [gapPx] of arc on a ring of the given centreline
 * diameter. Keeps the 2dp requirement constant on screen whatever size the donut is.
 */
fun donutGapDegrees(gapPx: Float, centerlineDiameterPx: Float): Float {
    if (centerlineDiameterPx <= 0f || gapPx <= 0f) return 0f
    val circumference = (Math.PI * centerlineDiameterPx).toFloat()
    return (gapPx / circumference) * FULL_CIRCLE
}
