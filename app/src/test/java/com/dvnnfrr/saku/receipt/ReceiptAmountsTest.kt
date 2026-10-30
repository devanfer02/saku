package com.dvnnfrr.saku.receipt

import com.dvnnfrr.saku.domain.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptAmountsTest {

    @Test
    fun `every printed form of twenty five thousand reads the same`() {
        listOf("25,000", "25.000", "Rp25.000", "Rp 25.000", "Rp. 25.000", "25000", "25.000,00", "25,000.00")
            .forEach { assertEquals(it, listOf(Money(25_000)), amountsIn(it)) }
    }

    @Test
    fun `sen after an unseparated amount is dropped`() {
        assertEquals(listOf(Money(365_000)), amountsIn("365000.00"))
    }

    @Test
    fun `millions keep every group`() {
        assertEquals(listOf(Money(1_565_938)), amountsIn("TOTAL 1.565.938"))
    }

    @Test
    fun `quantities and counts under one hundred rupiah are ignored`() {
        assertEquals(listOf(Money(3_100)), amountsIn("INDOMIE GRG SPCL 2 3.100"))
    }

    @Test
    fun `long digit runs are ids and dates, not amounts`() {
        assertEquals(emptyList<Money>(), amountsIn("ID Transaksi 20220715111212800"))
        assertEquals(emptyList<Money>(), amountsIn("11 Items 15072022"))
    }

    @Test
    fun `the year of a printed date is not an amount`() {
        assertEquals(emptyList<Money>(), amountsIn("30 Okt 2026 • 13:33"))
        assertEquals(emptyList<Money>(), amountsIn("2026-09-01 12:00"))
        assertEquals(listOf(Money(15_000)), amountsIn("30/10/2026 PARKIR 15.000"))
    }

    @Test
    fun `an item named like a month keeps its price`() {
        assertEquals(listOf(Money(25_000)), amountsIn("1 DESSERT 25.000"))
        assertEquals(listOf(Money(12_000)), amountsIn("2 MARGARIN 12.000"))
        assertEquals(listOf(Money(9_500)), amountsIn("3 JULY JUICE 9.500"))
    }

    @Test
    fun `a cut off amount is not guessed`() {
        assertEquals(emptyList<Money>(), amountsIn("GRANDTOTAL 24.0"))
    }

    @Test
    fun `letter O between digits reads as zero once rows are rebuilt`() {
        assertEquals(listOf(Money(60_000)), amountsIn(rowsOf("TOTAL 6O.0O0").single().text))
    }

    @Test
    fun `amounts come back left to right`() {
        assertEquals(listOf(Money(24_000), Money(24_000)), amountsIn("1 THAI TEA 24.000 24.000"))
    }
}
