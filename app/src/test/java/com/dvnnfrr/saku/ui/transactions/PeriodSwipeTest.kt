package com.dvnnfrr.saku.ui.transactions

import org.junit.Assert.assertEquals
import org.junit.Test

/** The swipe on the period header steps the period as the arrows do. */
class PeriodSwipeTest {

    @Test
    fun `a swipe to the left steps to the next period`() {
        assertEquals(1, swipeStep(draggedPx = -200f, thresholdPx = 150f))
    }

    @Test
    fun `a swipe to the right steps to the previous period`() {
        assertEquals(-1, swipeStep(draggedPx = 200f, thresholdPx = 150f))
    }

    @Test
    fun `a drag shorter than the threshold does nothing`() {
        assertEquals(0, swipeStep(draggedPx = 149f, thresholdPx = 150f))
        assertEquals(0, swipeStep(draggedPx = -149f, thresholdPx = 150f))
        assertEquals(0, swipeStep(draggedPx = 0f, thresholdPx = 150f))
    }

    @Test
    fun `a drag exactly at the threshold steps`() {
        assertEquals(1, swipeStep(draggedPx = -150f, thresholdPx = 150f))
        assertEquals(-1, swipeStep(draggedPx = 150f, thresholdPx = 150f))
    }
}
