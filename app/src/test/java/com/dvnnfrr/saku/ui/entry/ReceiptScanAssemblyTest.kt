package com.dvnnfrr.saku.ui.entry

import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.receipt.ReceiptReading
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReceiptScanAssemblyTest {

    private val now = LocalDateTime(2026, 10, 31, 19, 45)
    private val blank = EntryFormState.recording(now, accountId = 1L)

    private val indomaret = ReceiptReading(
        total = Money(52_300),
        otherAmounts = listOf(Money(42_600), Money(6_200)),
        date = LocalDate(2026, 10, 30),
        store = "Indomaret",
        unplaced = listOf("INDOMIE GRG SPCL 2 3.100 6.200", "AQUA 600ML 1 3.500"),
    )

    @Test
    fun `a blank sheet takes the total, the date at the same time of day, and the store`() {
        val form = applyReceipt(blank, indomaret).form
        assertEquals(Money(52_300), form.amount.value)
        assertEquals(LocalDateTime(2026, 10, 30, 19, 45), form.occurredAt)
        assertEquals("Indomaret", form.note)
    }

    @Test
    fun `type, account and category are left alone`() {
        val before = blank.copy(categoryId = 7L)
        val form = applyReceipt(before, indomaret).form
        assertEquals(TxnType.EXPENSE, form.type)
        assertEquals(1L, form.accountId)
        assertEquals(7L, form.categoryId)
    }

    @Test
    fun `a typed amount is kept and the total becomes the first chip`() {
        val typed = blank.copy(amount = AmountInput.of(Money(40_000)))
        val fill = applyReceipt(typed, indomaret)
        assertEquals(Money(40_000), fill.form.amount.value)
        assertEquals(Money(52_300), fill.panel.chipsFor(fill.form.amount.value).first())
    }

    @Test
    fun `a typed note is kept`() {
        val noted = blank.copy(note = "belanja bulanan")
        assertEquals("belanja bulanan", applyReceipt(noted, indomaret).form.note)
    }

    @Test
    fun `chips leave out the amount in use and stop at three`() {
        val panel = ReceiptPanel(
            amounts = listOf(Money(52_300), Money(42_600), Money(6_200), Money(3_500), Money(3_100)),
            lines = emptyList(),
            totalFound = true,
        )
        assertEquals(listOf(Money(42_600), Money(6_200), Money(3_500)), panel.chipsFor(Money(52_300)))
        assertEquals(listOf(Money(52_300), Money(6_200), Money(3_500)), panel.chipsFor(Money(42_600)))
    }

    @Test
    fun `no total keeps the amount empty and still lists the lines`() {
        val fill = applyReceipt(blank, indomaret.copy(total = null, otherAmounts = emptyList()))
        assertEquals(Money.ZERO, fill.form.amount.value)
        assertFalse(fill.panel.totalFound)
        assertEquals(2, fill.panel.lines.size)
    }

    @Test
    fun `no date keeps the sheet's date`() {
        assertEquals(now, applyReceipt(blank, indomaret.copy(date = null)).form.occurredAt)
    }

    @Test
    fun `a tapped line joins the note after a comma`() {
        assertEquals("Indomaret, INDOMIE GRG SPCL 2 3.100 6.200", appendToNote("Indomaret", "INDOMIE GRG SPCL 2 3.100 6.200"))
        assertEquals("AQUA 600ML", appendToNote("  ", "AQUA 600ML"))
    }

    @Test
    fun `a tapped line leaves the list`() {
        val panel = applyReceipt(blank, indomaret).panel.withoutLine("AQUA 600ML 1 3.500")
        assertEquals(listOf("INDOMIE GRG SPCL 2 3.100 6.200"), panel.lines)
    }
}
