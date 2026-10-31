package com.dvnnfrr.saku.receipt

import com.dvnnfrr.saku.domain.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptItemsTest {

    private fun items(vararg rows: String) = pickItems(rowsOf(*rows), null)

    private fun read(vararg rows: String) = ReceiptParser.read(linesOf(*rows), TODAY)

    @Test
    fun `an indomaret item with a discount under it`() {
        val item = items("SUSU UHT 1L 2 18.200 36.400", "DISKON : (4.000)").items.single()
        assertEquals(ReceiptItem("SUSU UHT 1L", 2, Money(36_400), Money(4_000)), item)
        assertEquals(Money(32_400), item.net)
    }

    @Test
    fun `the same item on two lines becomes one with quantity two`() {
        val item = items("SUSU UHT 1L 1 18.200 18.200", "SUSU UHT 1L 1 18.200 18.200").items.single()
        assertEquals(ReceiptItem("SUSU UHT 1L", 2, Money(36_400)), item)
    }

    @Test
    fun `the same name at another price or with a discount stays apart`() {
        val pick = items(
            "SUSU UHT 1L 1 18.200 18.200",
            "SUSU UHT 1L 1 19.900 19.900",
            "SUSU UHT 1L 1 18.200 18.200",
            "DISKON (1.000)",
        )
        assertEquals(3, pick.items.size)
    }

    @Test
    fun `a two-row item`() {
        val item = items("MINYAK GORENG 2L", "2 x 38.900 77.800").items.single()
        assertEquals(ReceiptItem("MINYAK GORENG 2L", 2, Money(77_800)), item)
    }

    @Test
    fun `a two-row item whose price was grouped with its name`() {
        val item = items("TEMPE PAPAN 18.000", "3 x 6.000").items.single()
        assertEquals(ReceiptItem("TEMPE PAPAN", 3, Money(18_000)), item)
    }

    @Test
    fun `a two-row item with a saving under it`() {
        val item = items("TAHU PUTIH 10S", "2 x 5.500 11.000", "HEMAT -1.000").items.single()
        assertEquals(ReceiptItem("TAHU PUTIH 10S", 2, Money(11_000), Money(1_000)), item)
    }

    @Test
    fun `a name and a price only is quantity one`() {
        assertEquals(ReceiptItem("ROTI TAWAR", 1, Money(18_900)), items("ROTI TAWAR 18.900").items.single())
    }

    @Test
    fun `a quantity that does not check out is dropped`() {
        assertEquals(ReceiptItem("GULA AREN", 1, Money(25_000)), items("GULA AREN 3 7.100 25.000").items.single())
    }

    @Test
    fun `a misread zero is repaired from quantity times unit price`() {
        assertEquals(ReceiptItem("AQUA BTL 1500ML", 2, Money(12_400)), items("AQUA BTL 1500ML 2 6.200 12.408").items.single())
        assertEquals(ReceiptItem("TEH BOTOL 450ML", 1, Money(4_500)), items("TEH BOTOL 450ML 4.500 4.509").items.single())
    }

    @Test
    fun `a letter O inside a size is read as zero`() {
        assertEquals("SABUN CUCI 800ML", items("SABUN CUCI 80OML 1 21.900 21.900").items.single().name)
    }

    @Test
    fun `a leading quantity is not part of the name`() {
        val item = items("2 Iced Tea 40,000").items.single()
        assertEquals("Iced Tea", item.name)
        assertEquals(Money(40_000), item.price)
    }

    @Test
    fun `a menu item called promo is an item, not a discount`() {
        val pick = items("J.STB PROMO 17500", "Y.B.BAT 46000")
        assertEquals(listOf("J.STB PROMO", "Y.B.BAT"), pick.items.map { it.name })
        assertTrue(pick.items.all { it.discount.isZero })
    }

    @Test
    fun `a card discount between the items and the total is a receipt discount`() {
        val reading = read(
            "INDOMARET",
            "INDOMIE GRG SPCL 5 3.100 15.500",
            "MINYAK GORENG 2L 1 38.900 38.900",
            "BERAS PREMIUM 5KG 1 72.500 72.500",
            "SUSU UHT 1L 2 18.200 36.400",
            "DISKON : (4.000)",
            "SABUN CUCI 800ML 1 21.900 21.900",
            "DETERJEN 1.8KG 1 44.900 44.900",
            "HARGA JUAL : 226.100",
            "DISKON KARTU : (20.000)",
            "TOTAL : 206.100",
            "KARTU DEBIT : 206.100",
            "PPN : 20.424",
        )
        assertEquals(Money(206_100), reading.total)
        assertEquals(listOf(Money(20_000)), reading.receiptDiscounts)
        assertEquals(emptyList<Money>(), reading.additions)
        assertEquals(6, reading.items.size)
        assertEquals(Money(206_100), reading.itemsNet)
    }

    @Test
    fun `tax and service above the total of a restaurant bill are additions`() {
        val dump = requireNotNull(javaClass.getResource("/receipt/cord/cord_014.txt")).readText()
        val reading = ReceiptParser.read(parseDump(dump), TODAY)
        assertEquals(listOf(38_000L, 55_000L, 65_000L, 35_000L, 25_000L, 20_000L), reading.items.map { it.price.rupiah })
        assertEquals(listOf(Money(17_850), Money(25_585)), reading.additions)
        assertEquals(Money(281_435), reading.itemsNet)
    }

    @Test
    fun `tax printed above the total of a tax-inclusive receipt is not added`() {
        val reading = read("ROTI TAWAR 1 18.900 18.900", "SUBTOTAL 18.900", "PPN 1.873", "TOTAL 18.900", "TUNAI 20.000")
        assertEquals(emptyList<Money>(), reading.additions)
        assertEquals(Money(18_900), reading.itemsNet)
    }

    @Test
    fun `a payment screenshot has no items`() {
        val reading = read("Transaksi berhasil!", "15 Okt 2026 • 13:33", "Pembayaran ke", "Kopi Tuku Cipete", "Total Bayar Rp46.882")
        assertEquals(emptyList<ReceiptItem>(), reading.items)
    }

    @Test
    fun `a quantity column receipt reads a glued quantity and a misread unit price`() {
        val pick = items(
            "4 TEPUNG BERAS 1 KG 1,300 5.200",
            "2 KECAP ASIN 995 1,990",
            "1 SABUN MANDI 2,090 2.090",
            "1GARAM HALUS 500 935 935",
            "2 2208 TISU WAJAH 15520 3.040",
        )
        assertEquals(ReceiptItem("GARAM HALUS 500", 1, Money(935)), pick.items[3])
        assertEquals(ReceiptItem("2208 TISU WAJAH", 1, Money(3_040)), pick.items[4])
    }

    @Test
    fun `a discount row read inside its item row is taken out`() {
        val item = items("SIRUP MELON 460ML 2 HEMAT -3.100 12.290 24.580").items.single()
        assertEquals(ReceiptItem("SIRUP MELON 460ML", 2, Money(24_580), Money(3_100)), item)
    }

    @Test
    fun `a quantity glued to the unit price is split off`() {
        val item = items("KERIPIK SINGKONG 17.490 7.490").items.single()
        assertEquals(ReceiptItem("KERIPIK SINGKONG", 1, Money(7_490)), item)
    }

    @Test
    fun `a line price cut by the photo edge is put back from quantity and unit`() {
        val item = items("DETERJEN CAIR 1L 1 46.590 46.59").items.single()
        assertEquals(ReceiptItem("DETERJEN CAIR 1L", 1, Money(46_590)), item)
    }

    @Test
    fun `a curled name row with a misread HEMAT gives the discount to the item above`() {
        val pick = items(
            "SUSU KENTAL 370G 2 12.500 25.000",
            "SAUS TIRAM 135ML HEMAI 2.100",
            "1 9.990 9.990",
            "MINYAK WIJEN HEMAT-1.000",
            "1 8.890 8.890",
        )
        assertEquals(listOf("SUSU KENTAL 370G", "SAUS TIRAM 135ML", "MINYAK WIJEN"), pick.items.map { it.name })
        assertEquals(listOf(Money(2_100), Money(1_000), Money.ZERO), pick.items.map { it.discount })
    }
}
