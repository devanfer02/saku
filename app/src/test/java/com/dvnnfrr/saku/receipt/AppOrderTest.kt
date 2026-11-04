package com.dvnnfrr.saku.receipt

import com.dvnnfrr.saku.domain.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOrderTest {

    private fun read(vararg rows: Pair<String, String?>) = ReceiptParser.read(screenOf(*rows), TODAY)

    private fun money(vararg rupiah: Long) = rupiah.map(::Money)

    @Test
    fun `a gofood order reads its one item, fees, discount and total payment`() {
        val reading = read(*GOFOOD_ONE_ITEM)
        assertEquals(ReceiptSource.GOFOOD, reading.source)
        assertEquals(Money(63_100), reading.total)
        assertEquals(listOf(ReceiptItem("Big Crunch Chicken", 1, Money(49_000))), reading.items)
        assertEquals(money(22_800, 6_000), reading.additions)
        assertEquals(money(14_700), reading.receiptDiscounts)
        assertEquals(reading.total, reading.itemsNet)
    }

    @Test
    fun `a food app's own headings are not the store`() {
        assertNull(read(*GOFOOD_ONE_ITEM).store)
        assertNull(read(*SHOPEEFOOD_ORDER).store)
    }

    @Test
    fun `a known chain on a food order is still the store`() {
        val reading = read("KFC" to null, *GOFOOD_ONE_ITEM)
        assertEquals("KFC", reading.store)
        assertEquals(ReceiptSource.GOFOOD, reading.source)
    }

    @Test
    fun `a shopeefood order reads its items, voucher, fees and the amount paid`() {
        val reading = read(*SHOPEEFOOD_ORDER)
        assertEquals(ReceiptSource.SHOPEEFOOD, reading.source)
        assertEquals(Money(132_000), reading.total)
        assertEquals(
            listOf(
                ReceiptItem("Paket Kenyang 2 Bakar", 2, Money(110_000)),
                ReceiptItem("1/2 Ekor Bebek Bakar + 1 Sambal Matah 30 Ml", 1, Money(39_500)),
                ReceiptItem("Saus Keju", 1, Money(9_000)),
                ReceiptItem("Sambal Ijo", 1, Money(9_000)),
            ),
            reading.items,
        )
        assertEquals(money(67_000), reading.receiptDiscounts)
        assertEquals(money(24_500, 4_000, 3_000), reading.additions)
        assertEquals(reading.total, reading.itemsNet)
    }

    @Test
    fun `a quantity printed first is kept with the line price`() {
        val item = read("Rincian Pesanan" to null, "3 x Roti Bakar Cokelat" to "Rp45.000", "Total" to "Rp45.000").items.single()
        assertEquals(ReceiptItem("Roti Bakar Cokelat", 3, Money(45_000)), item)
    }

    @Test
    fun `a wrapped name joins the item above, but an option or a button does not`() {
        val reading = read(
            "Rincian Pesanan" to null,
            "1 x Mie Goreng Spesial Telur" to "Rp28.000",
            "Setengah Matang" to null,
            "Level : Pedas" to null,
            "Tanpa sendok" to null,
            "Lihat Semua" to null,
            "Total" to "Rp28.000",
        )
        assertEquals("Mie Goreng Spesial Telur Setengah Matang", reading.items.single().name)
        assertTrue("Level : Pedas" in reading.unplaced)
        assertTrue("Tanpa sendok" in reading.unplaced)
    }

    @Test
    fun `gofood options under an item stay out of its name`() {
        val reading = read(*GOFOOD_ONE_ITEM)
        assertEquals("Big Crunch Chicken", reading.items.single().name)
        assertTrue("Flavour : Smoky Barbeque" in reading.unplaced)
    }

    @Test
    fun `of a struck-through old amount and the new one, the last counts`() {
        val reading = read(*SHOPEEFOOD_ORDER)
        assertTrue(Money(24_500) in reading.additions)
        assertFalse(Money(40_500) in reading.additions)
    }

    @Test
    fun `a payment row is never an item`() {
        val reading = read(*GOFOOD_ONE_ITEM)
        assertTrue(reading.items.none { "GoPay" in it.name || "Price" in it.name || "fee" in it.name })
    }

    @Test
    fun `several items priced only in the summary stay out of the items`() {
        val reading = read(
            "Purchase item(s)" to null,
            "Nasi Uduk Komplit" to "1",
            "Teh Tarik" to "2",
            "Payment summary" to null,
            "Price" to "52.000",
            "Delivery fee" to "9.000",
            "Total payment" to "61.000",
        )
        assertEquals(Money(61_000), reading.total)
        assertTrue(reading.items.isEmpty())
        assertTrue("Nasi Uduk Komplit 1" in reading.unplaced)
    }

    @Test
    fun `the amount above sudah termasuk pajak is the total when no row is labelled`() {
        val reading = read(
            "Rincian Pesanan" to null,
            "1 x Soto Ayam Kampung" to "Rp30.000",
            "Biaya Layanan" to "Rp2.000",
            "" to "Rp32.000",
            "Sudah termasuk pajak" to null,
        )
        assertEquals(Money(32_000), reading.total)
        assertEquals(money(2_000), reading.additions)
    }

    @Test
    fun `a shopee order takes names from the rows above each unit price`() {
        val reading = read(*SHOPEE_ORDER)
        assertEquals(ReceiptSource.SHOPEE, reading.source)
        assertEquals("Toko Sinar Rapi", reading.store)
        assertEquals(Money(106_000), reading.total)
        assertEquals(
            listOf(
                ReceiptItem("Kaos Polos Katun Combed 30s Lengan Pendek Unisex", 2, Money(90_000)),
                ReceiptItem("Gantungan Baju Plastik isi 12", 1, Money(18_500)),
            ),
            reading.items,
        )
        assertEquals(money(10_000, 5_000, 500), reading.receiptDiscounts)
        assertEquals(money(12_000, 1_000), reading.additions)
        assertEquals(reading.total, reading.itemsNet)
    }

    @Test
    fun `a tokopedia order names the shop and skips each product's total harga`() {
        val reading = read(*TOKOPEDIA_ORDER)
        assertEquals(ReceiptSource.TOKOPEDIA, reading.source)
        assertEquals("Rumah Rajut Mandiri", reading.store)
        assertEquals(Money(62_500), reading.total)
        assertEquals(
            listOf(
                ReceiptItem("Benang Rajut Katun Susu 50 Gram", 3, Money(36_000)),
                ReceiptItem("Hakpen Aluminium Set 8 Ukuran", 1, Money(25_000)),
            ),
            reading.items,
        )
        assertEquals(money(9_000), reading.receiptDiscounts)
        assertEquals(money(9_000, 500, 1_000), reading.additions)
        assertEquals(reading.total, reading.itemsNet)
    }

    @Test
    fun `a gofood checkout pays the last amount on the total row`() {
        val reading = read(*GOFOOD_CHECKOUT_ID)
        assertEquals(ReceiptSource.GOFOOD, reading.source)
        assertEquals(Money(69_000), reading.total)
        assertTrue(reading.items.isEmpty())
    }

    @Test
    fun `a delivered gofood order names the restaurant and prices its one item`() {
        val reading = read(*GOFOOD_DELIVERED)
        assertEquals("Dapur Sumber Rasa", reading.store)
        assertEquals(Money(60_000), reading.total)
        assertEquals(listOf(ReceiptItem("Nasi Bakar Tongkol", 1, Money(42_000))), reading.items)
        assertEquals(money(3_000, 15_000), reading.additions)
        assertEquals(reading.total, reading.itemsNet)
    }

    @Test
    fun `a fee group's own row is counted once, through its parts`() {
        val reading = read(
            "Detail Pesanan" to null,
            "Sate Ayam Madura" to "1",
            "Ringkasan pembayaran" to null,
            "Harga" to "40.000",
            "Ongkos kirim" to "13.000",
            "Biaya layanan dan lainnya" to "7.000",
            "Biaya jasa aplikasi" to "5.000",
            "Biaya parkir" to "2.000",
            "Diskon" to "-20.000",
            "Total pembayaran" to "40.000",
        )
        assertEquals(money(13_000, 5_000, 2_000), reading.additions)
        assertEquals(reading.total, reading.itemsNet)
    }

    @Test
    fun `fee and discount words in an item's name keep it an item`() {
        val reading = read("Purchase item(s)" to null, "Paket Promo Hemat" to "1", "Payment summary" to null, "Price" to "25.000", "Total payment" to "25.000")
        assertEquals(listOf(ReceiptItem("Paket Promo Hemat", 1, Money(25_000))), reading.items)
    }
}
