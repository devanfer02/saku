package com.dvnnfrr.saku.ui.entry

import com.dvnnfrr.saku.domain.TxnType
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** When the edit sheet offers Price history, and on which item. */
class PriceHistoryActionTest {

    private fun form(id: Long? = 7, type: TxnType = TxnType.EXPENSE, note: String = "SUSU UHT 1L ×2") =
        EntryFormState(id = id, type = type, occurredAt = LocalDateTime(2026, 10, 1, 12, 0), note = note)

    @Test
    fun `a saved expense opens on its name without the quantity`() {
        assertEquals("SUSU UHT 1L", priceHistoryItem(form()))
        assertEquals("minyak goreng 2l", priceHistoryItem(form(note = " minyak  goreng 2l ")))
        assertEquals("Indomie", priceHistoryItem(form(note = "Indomie 2X")))
    }

    @Test
    fun `a new entry, another type or a blank note offers nothing`() {
        assertNull(priceHistoryItem(form(id = null)))
        assertNull(priceHistoryItem(form(type = TxnType.INCOME)))
        assertNull(priceHistoryItem(form(type = TxnType.TRANSFER)))
        assertNull(priceHistoryItem(form(note = "   ")))
    }
}
