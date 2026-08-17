package com.dvnnfrr.saku.ui.shared

import com.dvnnfrr.saku.domain.ColorSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DonutGeometryTest {

    private fun segment(index: Int, value: Long) =
        DonutSegment(ColorSlot(index), value, "slot $index")

    @Test
    fun `an empty list draws no ring`() {
        assertEquals(emptyList<DonutArc>(), donutArcs(emptyList(), gapDegrees = 4f))
    }

    @Test
    fun `zero values are omitted from the ring`() {
        val arcs = donutArcs(
            listOf(segment(0, 100), segment(1, 0), segment(2, 100)),
            gapDegrees = 0f,
        )
        assertEquals(listOf(0, 2), arcs.map { it.slot?.index })
    }

    @Test
    fun `negative values are omitted from the ring`() {
        val arcs = donutArcs(
            listOf(segment(0, 100), segment(1, -500), segment(2, 100)),
            gapDegrees = 0f,
        )
        assertEquals(listOf(0, 2), arcs.map { it.slot?.index })
    }

    @Test
    fun `a set of nothing but zero and negative values draws no ring`() {
        val arcs = donutArcs(listOf(segment(0, 0), segment(1, -1)), gapDegrees = 4f)
        assertEquals(emptyList<DonutArc>(), arcs)
    }

    @Test
    fun `an omitted segment does not consume any of the ring`() {
        val withZero = donutArcs(
            listOf(segment(0, 300), segment(1, 0), segment(2, 100)),
            gapDegrees = 0f,
        )
        val without = donutArcs(listOf(segment(0, 300), segment(2, 100)), gapDegrees = 0f)
        assertEquals(without, withZero)
    }

    @Test
    fun `the first arc starts at twelve o'clock`() {
        val arcs = donutArcs(listOf(segment(0, 1), segment(1, 1)), gapDegrees = 6f)
        assertEquals(DONUT_START_DEGREES, arcs.first().startDegrees, 0.001f)
    }

    @Test
    fun `a single segment closes the ring, because it has no neighbour to separate from`() {
        val arcs = donutArcs(listOf(segment(3, 42)), gapDegrees = 6f)
        assertEquals(1, arcs.size)
        assertEquals(DONUT_START_DEGREES, arcs[0].startDegrees, 0.001f)
        assertEquals(360f, arcs[0].sweepDegrees, 0.001f)
    }

    @Test
    fun `every arc is followed by a gap, including the last`() {
        val gap = 6f
        val arcs = donutArcs(
            listOf(segment(0, 500), segment(1, 300), segment(2, 200)),
            gapDegrees = gap,
        )
        for (index in 0 until arcs.size - 1) {
            val endOfThis = arcs[index].startDegrees + arcs[index].sweepDegrees
            assertEquals(
                "gap after arc $index",
                gap,
                arcs[index + 1].startDegrees - endOfThis,
                0.001f,
            )
        }
        val endOfLast = arcs.last().startDegrees + arcs.last().sweepDegrees
        val wrapToFirst = arcs.first().startDegrees + 360f - endOfLast
        assertEquals("gap after the last arc", gap, wrapToFirst, 0.001f)
    }

    @Test
    fun `sweeps plus gaps account for the whole circle`() {
        val gap = 4f
        val segments = (0 until 5).map { segment(it, (it + 1) * 1000L) }
        val arcs = donutArcs(segments, gap)
        val swept = arcs.sumOf { it.sweepDegrees.toDouble() }
        assertEquals(360.0, swept + gap * arcs.size, 0.001)
    }

    @Test
    fun `sweeps stay proportional to their values`() {
        val arcs = donutArcs(listOf(segment(0, 300), segment(1, 100)), gapDegrees = 0f)
        assertEquals(270f, arcs[0].sweepDegrees, 0.001f)
        assertEquals(90f, arcs[1].sweepDegrees, 0.001f)
    }

    @Test
    fun `an amount that does not divide evenly loses nothing to rounding`() {
        val segments = (0 until 3).map { segment(it, 1L) }
        val arcs = donutArcs(segments, gapDegrees = 0f)
        val swept = arcs.sumOf { it.sweepDegrees.toDouble() }
        assertEquals(360.0, swept, 0.0001)
    }

    @Test
    fun `a large value against tiny ones keeps the tiny ones on the ring`() {
        val arcs = donutArcs(
            listOf(segment(0, 999_999_999L), segment(1, 1L)),
            gapDegrees = 0f,
        )
        assertEquals(2, arcs.size)
        assertTrue("the tiny arc vanished", arcs[1].sweepDegrees >= 0f)
    }

    @Test
    fun `gaps are dropped rather than made negative when they cannot fit`() {
        val segments = (0 until 8).map { segment(it, 1L) }
        val arcs = donutArcs(segments, gapDegrees = 45f)
        val swept = arcs.sumOf { it.sweepDegrees.toDouble() }
        assertEquals(360.0, swept, 0.001)
        assertTrue(arcs.all { it.sweepDegrees > 0f })
    }

    @Test
    fun `an arc keeps its slot, label and value`() {
        val arcs = donutArcs(listOf(DonutSegment(ColorSlot(5), 250, "Pemberian")), 0f)
        assertEquals(ColorSlot(5), arcs[0].slot)
        assertEquals("Pemberian", arcs[0].label)
        assertEquals(250L, arcs[0].value)
    }

    @Test
    fun `the gap in degrees shrinks as the ring grows`() {
        val small = donutGapDegrees(gapPx = 6f, centerlineDiameterPx = 200f)
        val large = donutGapDegrees(gapPx = 6f, centerlineDiameterPx = 400f)
        assertTrue(small > large)
        assertEquals(small / 2f, large, 0.0001f)
    }

    @Test
    fun `the gap in degrees is the arc length as a share of the circumference`() {
        val diameter = 100f
        val expected = (6f / (Math.PI.toFloat() * diameter)) * 360f
        assertEquals(expected, donutGapDegrees(6f, diameter), 0.0001f)
    }

    @Test
    fun `a degenerate ring asks for no gap`() {
        assertEquals(0f, donutGapDegrees(gapPx = 6f, centerlineDiameterPx = 0f), 0f)
        assertEquals(0f, donutGapDegrees(gapPx = 0f, centerlineDiameterPx = 200f), 0f)
    }
}
