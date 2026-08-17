package com.dvnnfrr.saku.ui.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AffectedCountLineTest {

    @Test
    fun `no count means no line`() {
        assertNull(affectedCountLine(null))
    }

    @Test
    fun `one is singular`() {
        assertEquals("1 transaction affected", affectedCountLine(1))
    }

    @Test
    fun `zero and many are plural`() {
        assertEquals("0 transactions affected", affectedCountLine(0))
        assertEquals("2 transactions affected", affectedCountLine(2))
        assertEquals("214 transactions affected", affectedCountLine(214))
        assertEquals("1200 transactions affected", affectedCountLine(1200))
    }

    @Test
    fun `the noun is a parameter, so merge and reversal can say what they mean`() {
        assertEquals("3 categories affected", affectedCountLine(3, "category", "categories"))
        assertEquals("1 category affected", affectedCountLine(1, "category", "categories"))
        assertEquals("2 rows affected", affectedCountLine(2, "row"))
    }
}
