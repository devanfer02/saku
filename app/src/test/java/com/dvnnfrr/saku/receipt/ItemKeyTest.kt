package com.dvnnfrr.saku.receipt

import org.junit.Assert.assertEquals
import org.junit.Test

class ItemKeyTest {

    @Test
    fun `case, spaces and a trailing quantity do not change the key`() {
        assertEquals("SUSU UHT 1L", itemKey("SUSU UHT 1L"))
        assertEquals("SUSU UHT 1L", itemKey("  susu uht   1l "))
        assertEquals("SUSU UHT 1L", itemKey("SUSU UHT 1L ×2"))
        assertEquals("SUSU UHT 1L", itemKey("SUSU UHT 1L x 2"))
        assertEquals("SUSU UHT 1L", itemKey("Susu UHT 1L X12"))
    }

    @Test
    fun `sizes and counts inside the name stay`() {
        assertEquals("TELUR AYAM 10", itemKey("TELUR AYAM 10"))
        assertEquals("SPONS CUCI 3S", itemKey("spons cuci 3s"))
        assertEquals("MIE 2X", itemKey("MIE 2X"))
    }
}
