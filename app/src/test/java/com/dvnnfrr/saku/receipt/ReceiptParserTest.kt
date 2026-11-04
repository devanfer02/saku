package com.dvnnfrr.saku.receipt

import com.dvnnfrr.saku.domain.Money
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptParserTest {

    private val indomaret = linesOf(
        "INDOMARET",
        "JL. KALIURANG KM 5",
        "31/10/2026 08:12",
        "INDOMIE GRG SPCL 2 3.100 6.200",
        "AQUA 600ML 1 3.500 3.500",
        "ROTI TAWAR 1 42.600 42.600",
        "TOTAL 52.300",
        "TUNAI 100.000",
        "KEMBALI 47.700",
        "TERIMA KASIH",
    )

    @Test
    fun `a clear indomaret receipt fills total, date and store`() {
        val reading = ReceiptParser.read(indomaret, TODAY)
        assertEquals(Money(52_300), reading.total)
        assertEquals(LocalDate(2026, 10, 31), reading.date)
        assertEquals("Indomaret", reading.store)
    }

    @Test
    fun `unplaced holds the rows not used as total, date, store or item, in printed order`() {
        val reading = ReceiptParser.read(indomaret, TODAY)
        assertEquals(listOf("JL. KALIURANG KM 5", "TERIMA KASIH"), reading.unplaced)
        assertEquals(listOf("INDOMIE GRG SPCL", "AQUA 600ML", "ROTI TAWAR"), reading.items.map { it.name })
    }

    @Test
    fun `a wallet screenshot reads through the same parser`() {
        val reading = ReceiptParser.read(
            linesOf(
                "13:54",
                "Transaksi berhasil!",
                "15 Okt 2026 • 13:33",
                "Pembayaran ke",
                "Kopi Tuku Cipete",
                "Total Bayar Rp46.882",
                "Metode Pembayaran Saldo DANA",
            ),
            TODAY,
        )
        assertEquals(Money(46_882), reading.total)
        assertEquals("Kopi Tuku Cipete", reading.store)
        assertEquals(LocalDate(2026, 10, 15), reading.date)
    }

    @Test
    fun `the unplaced list stops at forty rows`() {
        val long = linesOf(*Array(60) { "ITEM NUMBER $it" }, "TOTAL 99.000")
        assertEquals(40, ReceiptParser.read(long, TODAY).unplaced.size)
    }

    @Test
    fun `no lines gives an empty reading`() {
        assertTrue(ReceiptParser.read(emptyList(), TODAY).isEmpty)
    }

    @Test
    fun `a total misread by a look-alike digit is confirmed by the items`() {
        val reading = ReceiptParser.read(
            linesOf("INDOMARET", "BERAS PREMIUM 5KG 1 72.500 72.500", "SUSU UHT 1L 2 18.200 36.400", "TOTAL 108.988"),
            TODAY,
        )
        assertEquals(Money(108_900), reading.total)
        assertTrue(Money(108_988) in reading.otherAmounts)
    }
}
