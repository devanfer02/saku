package com.dvnnfrr.saku.ui.stats

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

/** The centre-fit arithmetic the pie measures its total against. */
class StatsPieGeometryTest {

    @Test
    fun `the hole holds the chord at the block's edge, not the diameter`() {
        assertEquals(80f, holeTextWidth(holeDiameterPx = 100f, blockHeightPx = 60f), 0.01f)
    }

    @Test
    fun `a block taller than the hole has no room at all`() {
        assertEquals(0f, holeTextWidth(holeDiameterPx = 100f, blockHeightPx = 100f), 0f)
    }

    @Test
    fun `text that fits keeps its size and text that does not shrinks to fit`() {
        assertEquals(1f, textFitScale(measuredWidthPx = 80f, availableWidthPx = 100f), 0f)
        assertEquals(0.5f, textFitScale(measuredWidthPx = 200f, availableWidthPx = 100f), 0.0001f)
    }

    @Test
    fun `a tap on the ring finds its slice clockwise from twelve and the hole finds none`() {
        // 200 px pie, 40 px ring: the ring's centreline is 80 px from the centre.
        val sweeps = listOf(900, 900, 1_800) // a quarter, a quarter, a half
        assertEquals(0, sliceAt(Offset(156.6f, 43.4f), 200f, 40f, sweeps)) // half past one
        assertEquals(1, sliceAt(Offset(156.6f, 156.6f), 200f, 40f, sweeps)) // half past four
        assertEquals(2, sliceAt(Offset(20f, 100f), 200f, 40f, sweeps)) // nine o'clock
        assertEquals(null, sliceAt(Offset(100f, 100f), 200f, 40f, sweeps)) // the hole
        assertEquals(null, sliceAt(Offset(0f, 0f), 200f, 40f, sweeps)) // the corner, outside the ring
    }
}
