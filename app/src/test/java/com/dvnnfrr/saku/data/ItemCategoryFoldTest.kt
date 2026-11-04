package com.dvnnfrr.saku.data

import com.dvnnfrr.saku.data.local.NoteUseRow
import com.dvnnfrr.saku.data.repository.foldItemCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The receipt item dictionary: notes pooled by item key, then the usual category. */
class ItemCategoryFoldTest {

    private var nextId = 1L

    private fun use(note: String, categoryId: Long?, at: Long = nextId) =
        NoteUseRow(nextId++, note, categoryId, 10_000, at)

    @Test
    fun `a known item maps to the category it was saved under`() {
        val dictionary = foldItemCategories(listOf(use("MINYAK GORENG 2L", categoryId = 7)))

        assertEquals(7L, dictionary["MINYAK GORENG 2L"])
    }

    @Test
    fun `a note with a quantity counts for the item bought once`() {
        val dictionary = foldItemCategories(listOf(use("SUSU UHT 1L ×2", categoryId = 7)))

        assertEquals(7L, dictionary["SUSU UHT 1L"])
    }

    @Test
    fun `uses of every spelling of one item are pooled before picking`() {
        // Alone, "SUSU UHT 1L" says 3; pooled with its two ×n uses under 7, 7 wins.
        val dictionary = foldItemCategories(
            listOf(
                use("SUSU UHT 1L", categoryId = 3),
                use("susu uht  1l ×2", categoryId = 7),
                use("SUSU UHT 1L x 3", categoryId = 7),
            ),
        )

        assertEquals(7L, dictionary["SUSU UHT 1L"])
    }

    @Test
    fun `a tie goes to the category used most recently`() {
        val dictionary = foldItemCategories(
            listOf(use("TELUR 1KG", categoryId = 3, at = 100), use("TELUR 1KG", categoryId = 5, at = 200)),
        )

        assertEquals(5L, dictionary["TELUR 1KG"])
    }

    @Test
    fun `a note never filed under a category is not in the dictionary`() {
        val dictionary = foldItemCategories(listOf(use("DETERJEN 1.8KG", categoryId = null)))

        assertFalse("DETERJEN 1.8KG" in dictionary)
    }
}
