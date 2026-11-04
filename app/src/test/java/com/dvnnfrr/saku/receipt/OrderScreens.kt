package com.dvnnfrr.saku.receipt

/*
 * App order screens as the recognizer returns them: a label at the left and its amount
 * right-aligned, one row per 40 px. Laid out after the owner's GoFood and ShopeeFood
 * screenshots (Nov 2026) and the layouts on GoFood's help pages; Shopee and Tokopedia
 * after their order detail screens. Every shop, restaurant and item name is invented, and
 * no address or order number is copied.
 */

/** One row per pair: the left text, then the amounts on the right, space separated. */
fun screenOf(vararg rows: Pair<String, String?>): List<OcrLine> = rows.flatMapIndexed { i, (label, amounts) ->
    val top = i * SCREEN_PITCH
    val left = OcrLine(label, left = 20, top = top, right = 20 + label.length * 9, bottom = top + SCREEN_LINE)
    val right = amounts?.split(' ').orEmpty().mapIndexed { n, amount ->
        val x = 300 + n * 110
        OcrLine(amount, left = x, top = top, right = x + 100, bottom = top + SCREEN_LINE)
    }
    listOf(left) + right
}

/** GoFood, paid with GoPay: one item, priced on the summary's Price row. */
val GOFOOD_ONE_ITEM = arrayOf<Pair<String, String?>>(
    "12.26" to null,
    "GoPay" to "63.100",
    "Purchase item(s)" to null,
    "Big Crunch Chicken" to "1",
    "Flavour : Smoky Barbeque" to null,
    "Spiciness : Level 2" to null,
    "Without cutlery/straws" to null,
    "Payment summary" to null,
    "Price" to "49.000",
    "Handling and delivery fee" to "22.800",
    "Other fee(s)" to "6.000",
    "Other discounts" to "-14.700",
    "Total payment" to "63.100",
    "Feel like trying other restos?" to null,
    "Low delivery fee offer ends in 30 min." to null,
)

/** ShopeeFood: quantities first, a wrapped name, a struck-through delivery fee, and Paid. */
val SHOPEEFOOD_ORDER = arrayOf<Pair<String, String?>>(
    "08.33" to null,
    "Rincian Pesananmu" to null,
    "Tambah ShopeeFood ke Layar Utama" to null,
    "Rincian Pesanan" to "Ubah",
    "2 x Paket Kenyang 2 Bakar" to "Rp110.000",
    "1 x 1/2 Ekor Bebek Bakar + 1" to "Rp39.500",
    "Sambal Matah 30 Ml" to null,
    "1 x Saus Keju" to "Rp9.000",
    "1 x Sambal Ijo" to "Rp9.000",
    "Lihat Lebih Sedikit" to null,
    "Subtotal Pesanan (5 menu)" to "Rp167.500",
    "Voucher Diskon" to "-Rp67.000",
    "Biaya Pengiriman" to "Rp40.500 Rp24.500",
    "Biaya Layanan" to "Rp4.000",
    "Biaya Lain-lain" to "Rp3.000",
    "Paid" to "Rp132.000",
    "Sudah termasuk pajak" to null,
)

/** GoFood checkout in Indonesian: `Biaya lainnya` open with its parts, and the total
 * struck through before the discount, then paid. No items on this card. */
val GOFOOD_CHECKOUT_ID = arrayOf<Pair<String, String?>>(
    "Ringkasan pembayaran" to null,
    "Harga" to "60.000",
    "Biaya penanganan dan pengiriman" to "23.000",
    "Biaya lainnya" to null,
    "Biaya bungkus" to "1.000",
    "Ditagih dan jumlahnya ditentukan resto" to null,
    "Diskon" to "-15.000",
    "Total pembayaran" to "84.000 69.000",
)

/** GoFood's order detail after delivery: the restaurant under its label, quantities and no prices. */
val GOFOOD_DELIVERED = arrayOf<Pair<String, String?>>(
    "Pesanan" to null,
    "gofood" to "Minggu, 03 Jan, 14:00",
    "Makanan sudah diantar" to "Pesanan F-123",
    "Detail Pengiriman" to null,
    "Alamat Restoran" to null,
    "Dapur Sumber Rasa" to null,
    "Detail Pesanan" to null,
    "Nasi Bakar Tongkol" to "1",
    "Detail Pembayaran" to null,
    "Price" to "42.000",
    "Restaurant takeaway charges" to "3.000",
    "Delivery fee" to "15.000",
    "Total" to "60.000",
    "Paid with GoPay" to "60.000",
    "Bantuan" to null,
)

/** Shopee marketplace order detail: shop row with its button, names over two rows, a
 * variant, `x2` with the unit price, then shipping, vouchers and Koin Shopee. */
val SHOPEE_ORDER = arrayOf<Pair<String, String?>>(
    "Pesanan Selesai" to null,
    "Toko Sinar Rapi" to "Kunjungi Toko",
    "Kaos Polos Katun Combed 30s" to null,
    "Lengan Pendek Unisex" to null,
    "Variasi: Hitam, XL" to null,
    "x2" to "Rp120.000 Rp45.000",
    "Gantungan Baju Plastik isi 12" to null,
    "x1" to "Rp18.500",
    "Rincian Pembayaran" to null,
    "Subtotal untuk Produk" to "Rp108.500",
    "Subtotal Pengiriman" to "Rp12.000",
    "Biaya Layanan" to "Rp1.000",
    "Voucher Diskon Pengiriman" to "-Rp10.000",
    "Voucher Shopee" to "-Rp5.000",
    "Koin Shopee yang Digunakan" to "-Rp500",
    "Total Pembayaran" to "Rp106.000",
    "Metode Pembayaran" to "ShopeePay",
)

/** Tokopedia order detail: the shop under Detail Produk, `1 x` with the unit price and
 * each product's Total Harga, then shipping, fees, Bebas Ongkir and Total Belanja. */
val TOKOPEDIA_ORDER = arrayOf<Pair<String, String?>>(
    "Detail Transaksi" to null,
    "Selesai" to null,
    "No. Invoice" to "INV/20261101/MPL/0000001",
    "Detail Produk" to null,
    "Rumah Rajut Mandiri" to null,
    "Benang Rajut Katun Susu 50 Gram" to null,
    "3 x" to "Rp12.000",
    "Total Harga" to "Rp36.000",
    "Hakpen Aluminium Set 8 Ukuran" to null,
    "1 x" to "Rp25.000",
    "Total Harga" to "Rp25.000",
    "Rincian Pembayaran" to null,
    "Metode Pembayaran" to "GoPay",
    "Total Harga (4 Barang)" to "Rp61.000",
    "Total Ongkos Kirim (1 kg)" to "Rp9.000",
    "Asuransi Pengiriman" to "Rp500",
    "Biaya Jasa Aplikasi" to "Rp1.000",
    "Bebas Ongkir" to "-Rp9.000",
    "Total Belanja" to "Rp62.500",
)

private const val SCREEN_PITCH = 40
private const val SCREEN_LINE = 24
