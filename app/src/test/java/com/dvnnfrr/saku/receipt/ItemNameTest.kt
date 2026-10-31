package com.dvnnfrr.saku.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Readable item names and the words an edit teaches; specs/receipt-items "Readable item names". */
class ItemNameTest {

    @Test
    fun `the owner's example reads as words`() {
        assertEquals("Indomie Goreng Special ×2", readableItemName("INDOMIE GRG SPCL x2"))
        assertEquals("Indomie Goreng Special", readableItemName("INDOMIE GRG SPCL"))
    }

    @Test
    fun `sizes and codes keep their form`() {
        assertEquals("Minyak Goreng 2L", readableItemName("MINYAK GORENG 2L"))
        assertEquals("Sabun Cuci 800ML", readableItemName("SABUN CUCI 800ML"))
        assertEquals("Deterjen 1.8KG", readableItemName("DETERJEN 1.8KG"))
        assertEquals("Royco Ayam 6X8GR", readableItemName("ROYCO AYAM 6X8GR"))
    }

    @Test
    fun `a trailing quantity in any spelling becomes ×n`() {
        assertEquals("Susu Uht ×3", readableItemName("SSU UHT X3"))
        assertEquals("Teh Botol 450ML ×2", readableItemName("TEH BTL 450ML ×2"))
        assertEquals("Teh Botol 450ML ×2", readableItemName("teh btl 450ml x 2"))
    }

    @Test
    fun `unknown words are only title-cased, separators restart a word`() {
        assertEquals("Prendjak Teh Celu", readableItemName("PRENDJAK TEH CELU"))
        assertEquals("G/F Uht F/CRM200", readableItemName("G/F UHT F/CRM200"))
        assertEquals("Ayam Ext.Minyak", readableItemName("AYAM EXT.MINYAK"))
    }

    @Test
    fun `a readable name reads the same again`() {
        for (printed in listOf("INDOMIE GRG SPCL x2", "BC MKRONI LV.15 135G", "KUSUKA RMPT LAUT1", "SUSU KENTAL MNS")) {
            val once = readableItemName(printed)
            assertEquals(once, readableItemName(once))
        }
    }

    @Test
    fun `an edited word is learned under its printed token and used next time`() {
        val printed = "BRS PREMIUM 5KG"
        val draft = readableItemName(printed)
        assertEquals("Beras Premium 5KG", draft)

        val learned = learnedWords(printed, draft, "Buris Premium 5KG")
        assertEquals(mapOf("BRS" to "Buris"), learned.words)
        assertEquals("Buris Premium 5KG", readableItemName("BRS PREMIUM 5KG", learned))
        assertEquals("Buris Pulen 5KG ×2", readableItemName("BRS PULEN 5KG x2", learned))
    }

    @Test
    fun `a learned word overrides the built-in one, and a later edit overwrites it`() {
        val first = learnedWords("SBN CUCI", "Sabun Cuci", "Sbn Cuci")
        assertEquals("Sbn Cuci", readableItemName("SBN CUCI", first))

        val again = first + learnedWords("SBN CUCI", readableItemName("SBN CUCI", first), "Sabun Cuci")
        assertEquals(mapOf("SBN" to "Sabun"), again.words)
        assertEquals("Sabun Cuci", readableItemName("SBN CUCI", again))
    }

    @Test
    fun `a size token is never learned`() {
        val changedSize = learnedWords("BRS PREMIUM 5KG", "Beras Premium 5KG", "Beras Premium 10KG")
        assertTrue(changedSize.isEmpty)
    }

    @Test
    fun `a different word count learns the whole name`() {
        val learned = learnedWords("IDM F/T 2PLY 3X180'S", "Idm F/T 2PLY 3X180'S", "Tisu Toilet Indomaret 2 ply")
        assertEquals(mapOf("IDM F/T 2PLY 3X180'S" to "Tisu Toilet Indomaret 2 ply"), learned.names)
        assertEquals("Tisu Toilet Indomaret 2 ply ×3", readableItemName("IDM F/T 2PLY 3X180'S x3", learned))
    }

    @Test
    fun `an unchanged name teaches nothing`() {
        assertTrue(learnedWords("INDOMIE GRG SPCL", "Indomie Goreng Special", "Indomie Goreng Special ×5").isEmpty)
    }
}
