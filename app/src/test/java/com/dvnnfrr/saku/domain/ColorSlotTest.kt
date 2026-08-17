package com.dvnnfrr.saku.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorSlotTest {

    @Test
    fun `first eight entities get distinct slots`() {
        val assigned = mutableListOf<ColorSlot>()
        repeat(ColorSlot.COUNT) { assigned += ColorSlot.next(assigned) }
        assertEquals(ColorSlot.COUNT, assigned.distinct().size)
        assertEquals((0 until ColorSlot.COUNT).toList(), assigned.map { it.index })
    }

    @Test
    fun `the ninth entity wraps to the first slot`() {
        val assigned = (0 until ColorSlot.COUNT).map(::ColorSlot)
        assertEquals(0, ColorSlot.next(assigned).index)
    }

    @Test
    fun `assignment stays even past the wrap`() {
        val assigned = mutableListOf<ColorSlot>()
        repeat(ColorSlot.COUNT * 3) { assigned += ColorSlot.next(assigned) }
        val perSlot = assigned.groupingBy { it.index }.eachCount()
        assertTrue("Expected even distribution, got $perSlot", perSlot.values.all { it == 3 })
    }

    @Test
    fun `freeing a slot makes it the next assignment`() {
        // An archived or deleted entity releases its slot for reuse.
        val assigned = (0 until ColorSlot.COUNT).map(::ColorSlot).toMutableList()
        assigned.remove(ColorSlot(3))
        assertEquals(3, ColorSlot.next(assigned).index)
    }

    @Test
    fun `an empty palette starts at slot zero`() {
        assertEquals(0, ColorSlot.next(emptyList()).index)
    }

    @Test
    fun `slots outside the palette are rejected`() {
        listOf(-1, ColorSlot.COUNT).forEach { bad ->
            val error = runCatching { ColorSlot(bad) }.exceptionOrNull()
            assertTrue("Expected $bad to be rejected", error is IllegalArgumentException)
        }
    }
}
