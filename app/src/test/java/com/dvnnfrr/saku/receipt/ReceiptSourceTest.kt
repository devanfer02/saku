package com.dvnnfrr.saku.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptSourceTest {

    private fun source(vararg rows: String) = pickSource(rowsOf(*rows))

    @Test
    fun `gofood by its labels, before the gopay it was paid with`() {
        assertEquals(ReceiptSource.GOFOOD, source("GoPay 63.100", "Purchase item(s)", "Ayam 1", "Payment summary", "Total payment 63.100"))
        assertEquals(ReceiptSource.GOFOOD, source("Pesanan GoFood", "Total 20.000"))
    }

    @Test
    fun `shopeefood by its brand or its order heading`() {
        assertEquals(ReceiptSource.SHOPEEFOOD, source("Rincian Pesananmu", "Paid Rp20.000"))
        assertEquals(ReceiptSource.SHOPEEFOOD, source("Tambah ShopeeFood ke Layar Utama"))
    }

    @Test
    fun `shopee's marketplace by its own labels, though it also heads the order rincian pesanan`() {
        assertEquals(ReceiptSource.SHOPEE, source("Rincian Pesanan", "Subtotal untuk Produk Rp20.000", "Metode Pembayaran ShopeePay"))
        assertEquals(ReceiptSource.SHOPEE, source("Koin Shopee yang Digunakan -Rp500"))
    }

    @Test
    fun `tokopedia by its brand or its invoice number`() {
        assertEquals(ReceiptSource.TOKOPEDIA, source("No. Invoice INV/20261101/MPL/0000001", "Metode Pembayaran GoPay"))
        assertEquals(ReceiptSource.TOKOPEDIA, source("Belanja di Tokopedia"))
    }

    @Test
    fun `gofood by its indonesian summary`() {
        assertEquals(ReceiptSource.GOFOOD, source("Ringkasan pembayaran", "Harga 18.000", "Total pembayaran 22.000"))
        assertEquals(ReceiptSource.GOFOOD, source("Biaya penanganan dan pengiriman 4.000"))
    }

    @Test
    fun `a banking app by its own name on any row`() {
        assertEquals(ReceiptSource.BCA, source("Transfer Berhasil", "Rp 150.000", "x", "x", "x", "x", "m-BCA"))
        assertEquals(ReceiptSource.BRIMO, source("Transaksi Berhasil", "BRImo"))
        assertEquals(ReceiptSource.LIVIN, source("Livin' by Mandiri", "Transfer"))
    }

    @Test
    fun `a wallet header at the top`() {
        assertEquals(ReceiptSource.GOPAY, source("gopay", "Pembayaran berhasil", "x", "x", "x", "x", "Total 20.000"))
        assertEquals(ReceiptSource.OVO, source("OVO", "Pembayaran berhasil", "x", "x"))
    }

    @Test
    fun `a wallet named with its balance anywhere`() {
        assertEquals(
            ReceiptSource.DANA,
            source("Transaksi berhasil!", "Pembayaran ke", "Kopi Tuku", "x", "x", "Total Bayar Rp46.882", "Metode Pembayaran Saldo DANA"),
        )
    }

    @Test
    fun `a shop's printed name at the top`() {
        assertEquals(ReceiptSource.INDOMARET, source("lNDOMARET", "JL MAWAR 1", "x", "x", "TOTAL 20.000"))
        assertEquals(ReceiptSource.ALFAMIDI, source("ALFAMIDI", "x", "x", "x"))
    }

    @Test
    fun `a paper receipt paid by a wallet or card is not that wallet`() {
        assertNull(source("WARUNG BU SRI", "NASI 20.000", "x", "x", "TOTAL 20.000", "OVO 20.000", "DEBIT BCA 20.000"))
    }

    @Test
    fun `sumber dana is not the wallet`() {
        assertNull(source("Sumber Dana", "x", "x", "x"))
    }

    @Test
    fun `nothing known gives no source`() {
        assertNull(source("TOKO MAJU", "TOTAL 10.000"))
        assertNull(pickSource(emptyList()))
    }
}
