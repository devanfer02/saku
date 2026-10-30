package com.dvnnfrr.saku.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** What search compares a note against. See `specs/transaction-search/spec.md`. */
class TextFoldTest {

    @Test
    fun `case is folded`() {
        assertEquals("kopi kenangan", fold("Kopi Kenangan"))
    }

    @Test
    fun `accents are stripped both ways`() {
        assertEquals("cafe batavia", fold("Café Batavia"))
        assertEquals(fold("cafe"), fold("café"))
        assertEquals("nasi uduk", fold("Nási Údúk"))
    }

    @Test
    fun `repeated and edge spaces collapse`() {
        assertEquals("kopi susu", fold("  kopi   susu\t"))
    }

    @Test
    fun `lowercasing ignores the device locale`() {
        val saved = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr"))
            assertEquals("indomaret", fold("INDOMARET"))
        } finally {
            java.util.Locale.setDefault(saved)
        }
    }
}
