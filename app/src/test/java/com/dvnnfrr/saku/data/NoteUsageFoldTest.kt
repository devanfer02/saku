package com.dvnnfrr.saku.data

import com.dvnnfrr.saku.data.local.NoteUseRow
import com.dvnnfrr.saku.data.repository.foldNoteUsage
import com.dvnnfrr.saku.domain.Money
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteUsageFoldTest {

    private var nextId = 1L

    private fun use(
        note: String,
        at: Long,
        amount: Long = 10_000,
        categoryId: Long? = 1,
        id: Long = nextId++,
    ) = NoteUseRow(id, note, categoryId, amount, at)

    @Test
    fun `spellings that fold alike are one note, shown as the most recent spelling`() {
        val result = foldNoteUsage(
            listOf(use("Es Teh", at = 100), use("es teh", at = 300), use("es  teh ", at = 200)),
        )

        assertEquals(1, result.size)
        val usage = result.single()
        assertEquals("es teh", usage.key)
        assertEquals("es teh", usage.note)
        assertEquals(3, usage.useCount)
        assertEquals(Instant.fromEpochSeconds(300), usage.lastUsedAt)
    }

    @Test
    fun `the display spelling is trimmed`() {
        val usage = foldNoteUsage(listOf(use("  Laundry  ", at = 1))).single()

        assertEquals("Laundry", usage.note)
    }

    @Test
    fun `last amount comes from the most recent use, whatever the row order`() {
        val usage = foldNoteUsage(
            listOf(
                use("bensin motor", at = 300, amount = 30_000),
                use("bensin motor", at = 100, amount = 20_000),
                use("bensin motor", at = 200, amount = 25_000),
            ),
        ).single()

        assertEquals(Money(30_000), usage.lastAmount)
    }

    @Test
    fun `a same-second tie goes to the higher id`() {
        val usage = foldNoteUsage(
            listOf(
                use("susu uht", at = 100, amount = 1, id = 10),
                use("susu uht", at = 100, amount = 2, id = 11),
            ),
        ).single()

        assertEquals(Money(2), usage.lastAmount)
    }

    @Test
    fun `category is the most used one`() {
        val usage = foldNoteUsage(
            listOf(
                use("laundry", at = 400, categoryId = 9),
                use("laundry", at = 100, categoryId = 3),
                use("laundry", at = 200, categoryId = 3),
            ),
        ).single()

        assertEquals(3L, usage.categoryId)
    }

    @Test
    fun `a category tie goes to the most recently used`() {
        val usage = foldNoteUsage(
            listOf(
                use("parkir", at = 100, categoryId = 3),
                use("parkir", at = 400, categoryId = 9),
                use("parkir", at = 200, categoryId = 3),
                use("parkir", at = 300, categoryId = 9),
            ),
        ).single()

        assertEquals(9L, usage.categoryId)
    }

    @Test
    fun `uses without a category do not count toward it`() {
        val usage = foldNoteUsage(
            listOf(
                use("obat", at = 100, categoryId = null),
                use("obat", at = 200, categoryId = null),
                use("obat", at = 300, categoryId = 4),
            ),
        ).single()

        assertEquals(4L, usage.categoryId)
        assertEquals(3, usage.useCount)
    }

    @Test
    fun `a note never used with a category has none`() {
        assertNull(foldNoteUsage(listOf(use("obat", at = 1, categoryId = null))).single().categoryId)
    }

    @Test
    fun `whitespace-only notes are left out`() {
        assertTrue(foldNoteUsage(listOf(use("   ", at = 1))).isEmpty())
    }

    @Test
    fun `distinct notes stay apart`() {
        val keys = foldNoteUsage(listOf(use("uang arisan", at = 1), use("uang kos", at = 2)))
            .map { it.key }.toSet()

        assertEquals(setOf("uang arisan", "uang kos"), keys)
    }
}
