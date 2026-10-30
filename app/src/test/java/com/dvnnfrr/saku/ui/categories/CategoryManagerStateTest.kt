package com.dvnnfrr.saku.ui.categories

import com.dvnnfrr.saku.domain.Category
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.ColorSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ordering and deletability rules, without a database or a composition behind them. */
class CategoryManagerStateTest {

    private fun category(id: Long, name: String, order: Int, archived: Boolean = false) =
        Category(
            id = id,
            name = name,
            kind = CategoryKind.EXPENSE,
            colorSlot = ColorSlot(id.toInt() % ColorSlot.COUNT),
            sortOrder = order,
            archived = archived,
        )

    @Test
    fun `moving an item down lands it on the target index`() {
        assertEquals(listOf(2L, 3L, 1L, 4L), listOf(1L, 2L, 3L, 4L).moveItem(from = 0, to = 2))
    }

    @Test
    fun `moving an item up lands it on the target index`() {
        assertEquals(listOf(4L, 1L, 2L, 3L), listOf(1L, 2L, 3L, 4L).moveItem(from = 3, to = 0))
    }

    @Test
    fun `a move past the end is clamped rather than thrown`() {
        // A fast drag reports a position beyond the last row before the list catches up.
        assertEquals(listOf(2L, 3L, 1L), listOf(1L, 2L, 3L).moveItem(from = 0, to = 9))
        assertEquals(listOf(1L, 2L, 3L), listOf(1L, 2L, 3L).moveItem(from = 7, to = 0))
    }

    @Test
    fun `reordering the visible rows leaves the hidden ones where they were`() {
        // B is archived and therefore not on screen. Renumbering from the visible list
        // alone would push it to the end, and unarchiving would not bring it back here.
        val all = listOf(1L, 2L, 3L, 4L)
        val visibleAfterDrag = listOf(4L, 1L, 3L)

        val result = reorderedIds(all = all, visibleOrder = visibleAfterDrag)

        assertEquals(listOf(4L, 2L, 1L, 3L), result)
        assertEquals(1, result.indexOf(2L))
    }

    @Test
    fun `an order naming ids the list does not hold is ignored`() {
        assertEquals(listOf(2L, 1L), reorderedIds(all = listOf(1L, 2L), visibleOrder = listOf(2L, 1L, 99L)))
    }

    @Test
    fun `a pending order repaints the list before the write lands`() {
        val rows = listOf(
            CategoryRow(category(1, "Pemberian", 0), 0),
            CategoryRow(category(2, "Makan", 1), 0),
            CategoryRow(category(3, "Kos", 2), 0),
        )

        val painted = rows.applyOrder(listOf(3L, 1L, 2L))

        assertEquals(listOf("Kos", "Pemberian", "Makan"), painted.map { it.name })
        assertEquals(listOf("Pemberian", "Makan", "Kos"), rows.applyOrder(null).map { it.name })
    }

    @Test
    fun `a category holding transactions is not deletable`() {
        val row = CategoryRow(category(1, "Pemberian", 0), transactionCount = 13)
        assertFalse(row.deletable)
    }

    @Test
    fun `a category nothing refers to is deletable`() {
        val row = CategoryRow(category = category(1, "Asuransi", 0), transactionCount = 0)
        assertTrue(row.deletable)
    }

    @Test
    fun `a category is never offered as a merge target for itself`() {
        val state = CategoryManagerUiState(
            active = listOf(
                CategoryRow(category(1, "Pemberian", 0), 0),
                CategoryRow(category(2, "Makan", 1), 0),
            ),
            loading = false,
        )

        assertEquals(listOf(2L), state.mergeTargetsFor(1).map { it.id })
    }

    @Test
    fun `rows carry the counts they were built with`() {
        val rows = buildCategoryRows(
            categories = listOf(category(1, "Pemberian", 0), category(2, "Makan", 1)),
            categoryCounts = mapOf(1L to 443),
        )

        assertEquals(443, rows.first().transactionCount)
        assertEquals(0, rows.last().transactionCount)
    }
}
