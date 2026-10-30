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
    fun `spaces inside a number are closed up`() {
        assertEquals(Money(123_585), total("SUbtotal 107.000", "4 Total 123. 585").total)
        assertEquals(Money(54_000), total("TOTAL 54 ,000", "CASH 104,000").total)
        assertEquals(Money(38_000), total("***TOTAL RP38. 000", "CASH Rr50. 000").total)
    }

    @Test
    fun `letters read for digits are repaired inside numbers`() {
        assertEquals(Money(68_000), total("TOTAL 6B,000", "CASH 100,000").total)
        assertEquals(Money(111_000), total("Subtotal 100,909", "Total Ii1,000").total)
        assertEquals(Money(118_000), total("Total Item:", "Total. Rp.118.D00").total)
        assertEquals(Money(165_000), total("Total Tax Included: Rp15000.00", "Total: Rpl65000.00").total)
    }

    @Test
    fun `words are not mistaken for numbers`() {
        assertEquals("ADD CHICKEN BOX 909", rowsOf("ADD CHICKEN BOX 909").single().text)
        assertEquals("ID Ii 12", rowsOf("ID Ii 12").single().text)
    }

    @Test
    fun `a total cut off mid number is padded back`() {
        assertEquals(Money(63_000), total("1 Lemon Tea 25. 000", "1 Caramel 38.000", "TOTAL 63.00", "CASH 70.000").total)
        assertEquals(Money(17_500), total("SubTotal: 15,909", "Total: 17,50", "Change 2,500").total)
        assertEquals(Money(21_000), total("6 GORENGAN 6,000", "TL -21, O0", "CASH -30. 000").total)
        assertEquals(Money(51_300), total("TOTA", "GRAD TOTA. 51,30>", "TLAI 52.(00").total)
    }

    @Test
    fun `misread subtotal labels stay subtotals`() {
        assertEquals(Money(281_435), total("SUETOTAL 238,000", "Service 17,850", "TOTAi 281, 435", "CASH 200,000").total)
        assertEquals(Money(145_900), total("SUB, TOTAL 132,636", "GRAND TOTAL", "~BCA EDC 145,900").total)
    }

    @Test
    fun `due and short TL labels are totals`() {
        assertEquals(Money(1_565_938), total("SUBTOTAL 1,343,000", "Tax 10% 142,358", "DU 1,565,938").total)
        assertEquals(Money(8_999), total("SUBTOTAL 8, 181", "TL - 8, 999", "CASH • 10, 000").total)
    }

    @Test
    fun `a status bar clock is not a TL total`() {
        assertEquals(Money(1_500_351), total("18.19 tl LTE", "Transfer", "Rp1.500.351", "Detail transaksi").total)
    }

    @Test
    fun `more misread labels and a space for thousands`() {
        assertEquals(Money(33_500), total("HARGA JUAL 33,50)", "10TAL : 33,50)", "TUNAI : 50,003").total)
        assertEquals(Money(63_000), total("Subt.o al 57.275", "4 Tolal 63 000", "Ba'yar 100.000").total)
    }

    @Test
    fun `a bare grand total does not take the cash paid`() {
        assertEquals(Money(24_000), total("SUB-TOTAL 24.000", "GRAND TOTAL", "CASH 50.000", "CHANGE 26.000").total)
    }

    @Test
    fun `nothing readable gives no total`() {
        val pick = total("TERIMA KASIH", "SELAMAT BELANJA")
        assertNull(pick.total)
        assertEquals(emptyList<Money>(), pick.others)
    }
}
