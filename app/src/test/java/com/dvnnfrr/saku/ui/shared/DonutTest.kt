package com.dvnnfrr.saku.ui.shared

import androidx.compose.ui.geometry.Offset
import com.dvnnfrr.saku.domain.ColorSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where a tap on the ring lands.
 *
 * The angle arithmetic wraps at twelve o'clock, which is where `DONUT_START_DEGREES`
 * puts the first arc's leading edge, and an off-by-one-revolution there is invisible
 * until someone taps the first slice and selects the last. That is the whole reason the
 * hit test is a pure function instead of a lambda inside the pointer handler.
 */
class DonutTest {

    private val center = Offset(100f, 100f)
    private val radius = 80f
    private val band = 48f

    /** Four equal segments, no gaps: one per quadrant, starting at twelve o'clock. */
    private val quarters = donutArcs(
        listOf(
            DonutSegment(ColorSlot(0), 100, "North"),
            DonutSegment(ColorSlot(1), 100, "East"),
            DonutSegment(ColorSlot(2), 100, "South"),
            DonutSegment(ColorSlot(3), 100, "West"),
        ),
    )

    /** A point on the ring's centreline at [degrees] clockwise from three o'clock. */
    private fun at(degrees: Double): Offset = Offset(
        x = center.x + (radius * Math.cos(Math.toRadians(degrees))).toFloat(),
        y = center.y + (radius * Math.sin(Math.toRadians(degrees))).toFloat(),
    )

    private fun hit(point: Offset, arcs: List<DonutArc> = quarters, tolerance: Float = 0f) =
        donutHitTest(
            tap = point,
            center = center,
            centerlineRadius = radius,
            bandWidth = band,
            arcs = arcs,
            toleranceDegrees = tolerance,
        )

    @Test
    fun `each quadrant answers for its own segment`() {
        // -45 is the middle of the arc that starts at twelve o'clock and runs clockwise.
        assertEquals(0, hit(at(-45.0)))
        assertEquals(1, hit(at(45.0)))
        assertEquals(2, hit(at(135.0)))
        assertEquals(3, hit(at(225.0)))
    }

    @Test
    fun `a tap just clockwise of twelve belongs to the first segment, not the last`() {
        assertEquals(0, hit(at(-89.0)))
        assertEquals(3, hit(at(-91.0)))
    }

    @Test
    fun `the hole and the outside are misses`() {
        assertNull(hit(center))
        assertNull(hit(Offset(center.x, center.y - (radius - band))))
        assertNull(hit(Offset(center.x, center.y - (radius + band))))
    }

    @Test
    fun `the band is as wide as a tap target, not as wide as the stroke`() {
        // 18dp of stroke would have missed both of these; 48dp of target catches them.
        assertEquals(0, hit(Offset(center.x + 20f, center.y - (radius - 20f))))
        assertEquals(0, hit(Offset(center.x + 20f, center.y - (radius + 20f))))
    }

    @Test
    fun `a tap in a gap goes to the nearer neighbour rather than nowhere`() {
        val gapped = donutArcs(
            listOf(
                DonutSegment(ColorSlot(0), 100, "First"),
                DonutSegment(ColorSlot(1), 100, "Second"),
            ),
            gapDegrees = 6f,
        )

        // The first arc ends at 84 degrees and the second begins at 90, so 85 and 89 are
        // both in the gap and each is nearer a different neighbour.
        assertNull(hit(at(85.0), arcs = gapped, tolerance = 0f))
        assertEquals(0, hit(at(85.0), arcs = gapped, tolerance = 6f))
        assertEquals(1, hit(at(89.0), arcs = gapped, tolerance = 6f))
    }

    @Test
    fun `a ring with one segment answers everywhere on the band`() {
        val whole = donutArcs(listOf(DonutSegment(ColorSlot(0), 100, "Only")))

        assertEquals(0, hit(at(0.0), arcs = whole))
        assertEquals(0, hit(at(180.0), arcs = whole))
    }

    @Test
    fun `an empty ring answers nothing`() {
        assertNull(hit(at(0.0), arcs = emptyList()))
        assertNull(hit(at(0.0), arcs = donutArcs(listOf(DonutSegment(null, 0, "Nothing")))))
    }

    @Test
    fun `segments dropped for being empty are not addressable`() {
        // The zero-valued middle segment is not drawn, so the ring holds two arcs and
        // the caller's third segment is the second arc. Donut maps that back through the
        // same predicate before it reports an index.
        val arcs = donutArcs(
            listOf(
                DonutSegment(ColorSlot(0), 100, "First"),
                DonutSegment(ColorSlot(1), 0, "Empty"),
                DonutSegment(ColorSlot(2), 100, "Third"),
            ),
        )

        assertEquals(2, arcs.size)
        assertEquals("Third", arcs[1].label)
    }
}
