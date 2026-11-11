package com.dvnnfrr.saku.ui.receipt

import com.dvnnfrr.saku.domain.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Add next part scrolls the Receipt list to the first item the new photo added. */
class ReceiptNewItemsTest {

    private fun item(key: String) = ReceiptItemUi(
        key = key, name = key, quantity = 1, price = Money(1_000), discount = Money.ZERO,
        amount = Money(1_000), categoryId = null, categoryName = null, fromHistory = false,
    )

    @Test
    fun `the first reading scrolls nowhere`() {
        assertNull(firstNewItemKey(null, listOf("A", "B")))
    }

    @Test
    fun `a new photo's items are found after the ones already listed`() {
        assertEquals("C", firstNewItemKey(listOf("A", "B"), listOf("A", "B", "C", "D")))
    }

    @Test
    fun `a photo that adds nothing, or only drops items, scrolls nowhere`() {
        assertNull(firstNewItemKey(listOf("A", "B"), listOf("A", "B")))
        assertNull(firstNewItemKey(listOf("A", "B"), listOf("A")))
    }

    @Test
    fun `an item's row sits below the summary, notices and the items title`() {
        val state = ReceiptUiState(
            photos = 2,
            items = listOf(item("A"), item("B"), item("C")),
            recordAs = RecordAs.PER_ITEM,
            gapsAfterPhoto = listOf(1),
        )

        assertEquals(5, receiptListIndexOf(state, "C"))
        assertEquals(6, receiptListIndexOf(state.copy(unreadable = true), "C"))
        assertNull(receiptListIndexOf(state, "Z"))
        assertNull(receiptListIndexOf(state.copy(recordAs = RecordAs.ONE_ROW), "C"))
    }
}
