package com.dvnnfrr.saku.ui.stats

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
}
