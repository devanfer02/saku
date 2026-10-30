package com.dvnnfrr.saku.receipt

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptDateStoreTest {

    private fun date(vararg rows: String) = pickDate(rowsOf(*rows), TODAY)?.date

    @Test
    fun `day first numeric dates`() {
        assertEquals(LocalDate(2026, 10, 30), date("30/10/2026 19:05"))
        assertEquals(LocalDate(2026, 10, 30), date("30-10-26"))
        assertEquals(LocalDate(2026, 10, 30), date("30.10.2026"))
    }

    @Test
    fun `year first dates`() {
        assertEquals(LocalDate(2026, 9, 1), date("2026-09-01 12:00:01"))
    }

    @Test
    fun `indonesian and english month names`() {
        assertEquals(LocalDate(2026, 10, 15), date("15 Okt 2026 • 13:33"))
        assertEquals(LocalDate(2026, 5, 3), date("03 Mei 2026 08:02"))
        assertEquals(LocalDate(2026, 8, 17), date("17 Agustus 2026"))
        assertEquals(LocalDate(2026, 3, 9), date("09 Mar 2026"))
    }

    @Test
    fun `a future date or one over a year old is ignored`() {
        assertNull(date("01/11/2026"))
        assertNull(date("01 Dec 2026"))
        assertNull(date("15 Jul 2022 13:33"))
        assertEquals(LocalDate(2026, 10, 1), date("Berlaku s/d 01/01/2027", "01/10/2026"))
    }

    @Test
    fun `an impossible date is skipped`() {
        assertNull(date("31/02/2026"))
    }

    private fun store(vararg rows: String) = pickStore(rowsOf(*rows))?.name

    @Test
    fun `a known chain is named the way a note should read`() {
        assertEquals("Indomaret", store("lNDOMARET", "JL. SUDIRMAN 12", "TOTAL 52.300"))
        assertEquals("Waroeng Steak & Shake", store("WAROENG STEAK & SHAKE", "MALANG", "", "TOTAL 139.000"))
    }

    @Test
    fun `a wallet screen names the payee after its label`() {
        assertEquals(
            "Kopi Tuku Cipete",
            store("08:16", "Transaksi berhasil!", "Pembayaran ke", "Kopi Tuku Cipete", "Total Bayar Rp46.882"),
        )
        assertEquals("Warung Bu Sri", store("Merchant: Warung Bu Sri", "Total Rp 12.000"))
    }

    @Test
    fun `otherwise the first named row at the top, never the status bar clock`() {
        assertEquals(
            "RM PADANG SEDERHANA",
            store("13:54", "RM PADANG SEDERHANA", "JL MERDEKA", "NASI RENDANG 25.000", "TOTAL 25.000", "TERIMA KASIH", "", ""),
        )
    }
}
