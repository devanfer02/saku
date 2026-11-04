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

/** ShopeeFood order detail with its list folded (`Lihat Lebih Banyak`), a modifier line, a
 * delivery fee struck to Rp0, and the amount paid beside the Paid stamp (research SF-1). */
val SHOPEEFOOD_FOLDED = arrayOf<Pair<String, String?>>(
    "Rincian Pesananmu" to null,
    "1 x Es Kopi Gula Aren" to "Rp30.000",
    "Normal, Normal Ice , Normal Espresso" to null,
    "Lihat Lebih Banyak" to null,
    "Subtotal Pesanan (8 menu)" to "Rp215.000",
    "Voucher Diskon" to "-Rp118.250",
    "Biaya Pengiriman" to "Rp7.500 Rp0",
    "Biaya Layanan" to "Rp500",
    "Paid" to "Rp97.250",
    "" to "Sudah termasuk pajak",
    "Informasi Pesanan" to null,
    "No. Pesanan" to "2512150000ABCD SALIN",
    "Pembayaran" to "ShopeePay",
)

/** ShopeeFood in English: a summary line with the total, `Paid` and the tax caption, and an
 * 18-digit order id (research SF-5). */
val SHOPEEFOOD_ENGLISH = arrayOf<Pair<String, String?>>(
    "Order Processing" to null,
    "Warung Kebun Hijau" to null,
    "Rp19.000 - 1 item - ShopeePay" to null,
    "Deliver To" to null,
    "1 x Salad Sayur Segar" to "Rp11.000",
    "Subtotal (1 item)" to "Rp11.000",
    "Shipping Fee" to "↑ Rp5.000",
    "Service Fee" to "Rp3.000",
    "Paid" to "Rp19.000",
    "" to "Tax included, where applicable.",
    "Order ID" to "600000000000000001",
    "Payment" to "ShopeePay",
)

/** ShopeeFood checkout in English: the price on its own row under the item, and Total
 * then Total Payment (research SF-4). */
val SHOPEEFOOD_CHECKOUT = arrayOf<Pair<String, String?>>(
    "Confirm Order" to null,
    "Kedai Pizza Rumahan" to null,
    "1x Pizza Keju Lumer" to null,
    "Jagung" to null,
    "" to "Rp24.000",
    "Note:" to "Please leave a note...",
    "Order Total (1 item):" to "Rp24.000",
    "Voucher" to "Select voucher >",
    "Redeem 0 Shopee Coin" to null,
    "Payment Option" to "Credit / Debit Card >",
    "Order Subtotal (1 item)" to "Rp24.000",
    "Shipping Fee" to "↑ Rp5.000",
    "Service Fee" to "Rp3.000",
    "Total" to "Rp32.000",
    "Tax included, where applicable." to null,
    "Total Payment" to "Rp32.000",
)

/** Shopee checkout: `Rp89.900  x1`, an unticked add-on, instalment tiles and a coin toggle
 * above Rincian Pembayaran (research SM-2). */
val SHOPEE_CHECKOUT = arrayOf<Pair<String, String?>>(
    "Checkout" to null,
    "Kipas Meja Mini Lipat" to null,
    "Bebas Pengembalian" to null,
    "Variasi: biru muda" to null,
    "Rp89.900" to "x1",
    "Proteksi Kerusakan +" to "Rp625 x1",
    "Voucher Toko" to "Gunakan/ masukkan kode >",
    "Opsi Pengiriman" to null,
    "Reguler" to "Rp9.000 Rp0",
    "Voucher s/d Rp10.000 jika pesanan belum tiba 2 Jun 2024." to null,
    "Total Pesanan (1 Produk):" to "Rp89.900",
    "Tukarkan 10181 Koin Shopee" to null,
    "Metode Pembayaran" to "ShopeePay >",
    "SPayLater (Rp16.191.841 Tersisa)" to null,
    "Rekomendasi Rp91.799 x 1 bln" to "Rp33.308 x 3 bln",
    "Rincian Pembayaran" to null,
    "Subtotal untuk Produk" to "Rp89.900",
    "Subtotal Pengiriman" to "Rp9.000",
    "Total Diskon Pengiriman" to "- Rp9.000",
    "Biaya Layanan" to "Rp1.000",
    "Total Pembayaran" to "Rp90.900",
    "Total Pembayaran" to "Rp90.900 Buat Pesanan",
)

/** Tokopedia's invoice: a four-column item table, Total Belanja and Total Tagihan (research TP-2). */
val TOKOPEDIA_INVOICE = arrayOf<Pair<String, String?>>(
    "INVOICE" to null,
    "INV/20261101/MPL/1234567890" to null,
    "Penjual : Toko Jam Sentosa" to null,
    "Pembeli : Budi Contoh" to null,
    "INFO PRODUK" to "JUMLAH HARGA SATUAN TOTAL HARGA",
    "Jam Tangan Digital Sport" to "1 Rp35.000 Rp35.000",
    "Berat: 200 gr" to null,
    "TOTAL HARGA (1 BARANG)" to "Rp35.000",
    "Total Ongkos Kirim" to "Rp12.000",
    "Diskon Ongkos Kirim" to "-Rp1.000",
    "Asuransi Pengiriman" to "Rp500",
    "Biaya Jasa Aplikasi" to "Rp1.000",
    "TOTAL BELANJA" to "Rp47.500",
    "TOTAL TAGIHAN" to "Rp47.500",
    "Metode Pembayaran : GoPay" to null,
    "Invoice ini sah dan diproses oleh komputer" to null,
)

/** A GoFood order in GoPay's history: Pembelian with counts only, Bayar Pakai (research GP-1). */
val GOFOOD_IN_GOPAY = arrayOf<Pair<String, String?>>(
    "Rangkuman transaksi" to null,
    "gofood" to "23 Agu, 10:53 AM",
    "Makanan udah diantar" to "Transaksi F-2570000000",
    "Alamat restoran" to null,
    "Dapur Lele Sederhana" to null,
    "Pembelian" to null,
    "Ayam Penyet Sambal Ijo" to "8",
    "Sate Usus" to "5",
    "Kol Goreng" to "3",
    "Tanpa alat makan/sedotan" to null,
    "Detail pembayaran" to null,
    "Harga" to "280.000",
    "Biaya Penanganan dan Pengiriman" to "17.500",
    "Total" to "297.500",
    "Bayar Pakai GoPay Tabungan By Jago" to "297.500",
    "Download bukti" to null,
)

private const val SCREEN_PITCH = 40
private const val SCREEN_LINE = 24
