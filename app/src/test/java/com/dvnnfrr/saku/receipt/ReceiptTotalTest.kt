package com.dvnnfrr.saku.receipt

import com.dvnnfrr.saku.domain.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptTotalTest {

    private fun total(vararg rows: String) = pickTotal(rowsOf(*rows))

    @Test
    fun `subtotal, tax, cash and change are passed over for the total`() {
        val pick = total("SUBTOTAL 50.000", "PPN 5.000", "TOTAL 55.000", "TUNAI 100.000", "KEMBALI 45.000")
        assertEquals(Money(55_000), pick.total)
    }

    @Test
    fun `an OCR slip in the label still counts`() {
        assertEquals(Money(28_000), total("SUB TUTAI 28,000", "T0TAL SA 3 28,000", "CASH 100,000").total)
    }

    @Test
    fun `grand total beats a plain total line`() {
        assertEquals(Money(61_000), total("TOTAL 55.000", "SERVICE 6.000", "GRAND TOTAL 61.000").total)
    }

    @Test
    fun `item and discount totals are not the total`() {
        assertEquals(Money(30_000), total("TOTAL ITEM 3", "TOTAL DISC 5.000", "TOTAL 30.000").total)
    }

    @Test
    fun `a bare total label takes the amount below it, past the payment row`() {
        // KFC prints the label, the payment method and the figure on three rows.
        assertEquals(Money(322_000), total("Sub Total 292,723", "Total", "Cash", "322,000").total)
    }

    @Test
    fun `a cut off total does not borrow the cash paid`() {
        val pick = total("SUB-TOTAL 24.000", "GRANDTOTAL 24.0", "CASH 50.000", "CHANGED 26.000")
        assertEquals(Money(24_000), pick.total)
    }

    @Test
    fun `total bayar on a wallet screen is a total, not a payment row`() {
        assertEquals(Money(631_000), total("Pembayaran ke", "Total Bayar Rp631.000", "Metode Pembayaran Saldo DANA").total)
    }

    @Test
    fun `a banking total with an admin fee beats the nominal, which becomes a chip`() {
        val pick = total("Nominal Rp 50.000,00", "Biaya Admin Rp 2.500,00", "Total Rp 52.500,00")
        assertEquals(Money(52_500), pick.total)
        assertTrue(Money(50_000) in pick.others)
    }

    @Test
    fun `with no label the repeated amount wins`() {
        // The figure repeats as the card payment.
        assertEquals(Money(156_000), total("Propolis Cair 1 156000", "Debit BCA 156000", "Tunai 200000").total)
    }

    @Test
    fun `with no label and no repeat the largest non payment amount wins`() {
        assertEquals(Money(23_900), total("ROTI 12.000", "SUSU 11.900", "23.900", "TUNAI 50.000").total)
    }

    @Test
    fun `chips offer the other amounts but never the cash paid or the change`() {
        val pick = total("SUBTOTAL 50.000", "PPN 5.000", "TOTAL 55.000", "TUNAI 100.000", "KEMBALI 45.000")
        assertEquals(listOf(Money(50_000), Money(5_000)), pick.others)
    }

    @Test
    fun `chips stop at three`() {
        val pick = total("A 10.000", "B 20.000", "C 30.000", "D 40.000", "TOTAL 100.000")
        assertEquals(listOf(Money(40_000), Money(30_000), Money(20_000)), pick.others)
    }

    @Test
    fun `nothing readable gives no total`() {
        val pick = total("TERIMA KASIH", "SELAMAT BELANJA")
        assertNull(pick.total)
        assertEquals(emptyList<Money>(), pick.others)
    }
}
