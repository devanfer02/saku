package com.dvnnfrr.saku.receipt

import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptRowsTest {

    @Test
    fun `a label and its amount from different blocks join one row`() {
        // The recognizer's order: amounts block first, labels block second.
        val lines = listOf(
            OcrLine("28,000", 300, 100, 360, 116),
            OcrLine("100,000", 300, 130, 360, 146),
            OcrLine("TOTAL", 40, 102, 100, 118),
            OcrLine("CASH", 40, 131, 90, 147),
        )
        assertEquals(listOf("TOTAL 28,000", "CASH 100,000"), receiptRows(lines).map { it.text })
    }

    @Test
    fun `rows come out top to bottom`() {
        val lines = listOf(
            OcrLine("KEMBALI 72.000", 10, 200, 200, 220),
            OcrLine("INDOMARET", 10, 0, 200, 20),
        )
        assertEquals(listOf("INDOMARET", "KEMBALI 72.000"), receiptRows(lines).map { it.text })
    }

    @Test
    fun `lines a full row apart stay separate`() {
        assertEquals(2, rowsOf("SUBTOTAL 50.000", "TOTAL 55.000").size)
    }

    @Test
    fun `blank lines are dropped`() {
        assertEquals(listOf("TOTAL 5.000"), rowsOf("  ", "TOTAL 5.000").map { it.text })
    }
}
