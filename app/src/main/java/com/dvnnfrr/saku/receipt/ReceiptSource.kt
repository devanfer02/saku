package com.dvnnfrr.saku.receipt

/**
 * Which app or shop a reading came from, by the screen's own labels. Kept apart from the
 * store: a GoFood order's store is the restaurant, its source is GoFood. [label] is the
 * brand name as the brand writes it. An [orderApp] screen lists an order's items, fees and
 * the amount paid, and is read by `pickAppOrder`.
 */
enum class ReceiptSource(val label: String, val orderApp: Boolean = false) {
    GOFOOD("GoFood", orderApp = true),
    SHOPEEFOOD("ShopeeFood", orderApp = true),
    SHOPEE("Shopee", orderApp = true),
    TOKOPEDIA("Tokopedia", orderApp = true),
    GOPAY("GoPay"),
    OVO("OVO"),
    DANA("DANA"),
    SHOPEEPAY("ShopeePay"),
    LINKAJA("LinkAja"),
    BCA("BCA"),
    BRIMO("BRImo"),
    LIVIN("Livin' by Mandiri"),
    BNI("BNI"),
    INDOMARET("Indomaret"),
    ALFAMART("Alfamart"),
    ALFAMIDI("Alfamidi"),
    SUPERINDO("Superindo"),
    LAWSON("Lawson"),
    FAMILYMART("FamilyMart"),
}

private fun pattern(source: String) = Regex(source, RegexOption.IGNORE_CASE)

/* An order app, from any row. Brands first; then labels only that app prints. Shopee's
 * marketplace and ShopeeFood both head the order `Rincian Pesanan`, so the marketplace's
 * own labels are tried before that heading. */
private val ORDER_APPS = listOf(
    pattern("""go\s?food|biaya\s*penanganan|handling\s*and\s*delivery|biaya\s*layanan\s*dan\s*lainnya|service\s*and\s*other\s*fees""") to ReceiptSource.GOFOOD,
    pattern("""shopee\s?food""") to ReceiptSource.SHOPEEFOOD,
    pattern("""tokopedia|\binv/\d|biaya\s*jasa\s*aplikasi|invoice\s*ini\s*sah""") to ReceiptSource.TOKOPEDIA,
    pattern("""koin\s*shopee|shopee\s*koin|subtotal\s*(untuk\s*)?produk|subtotal\s*barang|subtotal\s*pengiriman|shopee\s*mall""") to
        ReceiptSource.SHOPEE,
)
private val GOFOOD_ITEMS = pattern("""purchase\s*item""")
private val GOFOOD_SUMMARY = pattern("""payment\s*summary""")
private val GOFOOD_SUMMARY_ID = pattern("""ringkasan\s*pembayaran""")
private val GOFOOD_PRICE_ID = pattern("""^\W*harga\b""")
/** ShopeeFood's heading, or the caption under its amount paid, in either language. */
private val SHOPEEFOOD_ORDER = pattern("""rincian\s*pesanan|sudah\s*termasuk\s*pajak|tax\s*included,\s*where\s*applicable""")

/* A banking app, from any row: names a paper slip never prints (`DEBIT BCA` is not m-BCA). */
private val BANK_APPS = listOf(
    pattern("""\bm-?bca\b|\bbca\s*mobile\b|\bmybca\b|\bklik\s*bca\b""") to ReceiptSource.BCA,
    pattern("""\bbrimo\b""") to ReceiptSource.BRIMO,
    pattern("""\blivin'?\b""") to ReceiptSource.LIVIN,
    pattern("""\bbni\s*mobile\b|\bwondr\b""") to ReceiptSource.BNI,
)

/* At the top of the image: a wallet's or bank's own header, or a shop's printed name. A
 * paper receipt names the wallet it was paid with at the bottom, so lower rows don't count. */
private val HEADERS = listOf(
    pattern("""shopee\s?pay""") to ReceiptSource.SHOPEEPAY,
    pattern("""\bgo\s?pay\b""") to ReceiptSource.GOPAY,
    pattern("""\bovo\b""") to ReceiptSource.OVO,
    // `Sumber Dana` (source of funds) is a bank screen's label, not the wallet.
    pattern("""(?<!sumber\s)\bdana\b""") to ReceiptSource.DANA,
    pattern("""\blink\s?aja\b""") to ReceiptSource.LINKAJA,
    pattern("""\bbca\b""") to ReceiptSource.BCA,
    pattern("""\bbni\b""") to ReceiptSource.BNI,
)

/** Keys as `chainKey` writes them; Alfamidi before Alfamart, which it does not contain. */
private val SHOPS = listOf(
    "indomaret" to ReceiptSource.INDOMARET,
    "alfamidi" to ReceiptSource.ALFAMIDI,
    "alfamart" to ReceiptSource.ALFAMART,
    "superindo" to ReceiptSource.SUPERINDO,
    "lawson" to ReceiptSource.LAWSON,
    "familymart" to ReceiptSource.FAMILYMART,
)

/* A wallet named with its balance anywhere: `Metode Pembayaran Saldo DANA`. */
private val WALLET_BALANCE = listOf(
    pattern("""saldo\s*shopee\s?pay|shopee\s?pay\s*balance""") to ReceiptSource.SHOPEEPAY,
    pattern("""saldo\s*go\s?pay|go\s?pay\s*balance""") to ReceiptSource.GOPAY,
    pattern("""saldo\s*ovo|ovo\s*(balance|cash)""") to ReceiptSource.OVO,
    pattern("""saldo\s*dana|dana\s*balance""") to ReceiptSource.DANA,
)

/**
 * The source, by the first rule that answers; null when none does.
 * 1. An order app's brand or its own labels, on any row (GoFood: `Purchase item(s)` with
 *    `Payment summary`, or `Biaya penanganan`; Shopee: `Koin Shopee`, `Subtotal untuk
 *    Produk`; Tokopedia: `INV/…`; ShopeeFood: `Rincian Pesanan`). It beats the wallet it
 *    was paid with.
 * 2. A banking app's own name on any row (`m-BCA`, `BRImo`, `Livin'`).
 * 3. In the top quarter, top row first: a wallet or bank header, or a shop's name.
 * 4. A wallet named with its balance anywhere.
 */
fun pickSource(rows: List<ReceiptRow>): ReceiptSource? {
    if (rows.isEmpty()) return null
    val texts = rows.map { it.text }
    fun anyRow(regex: Regex) = texts.any { regex.containsMatchIn(it) }

    if (anyRow(GOFOOD_ITEMS) && anyRow(GOFOOD_SUMMARY)) return ReceiptSource.GOFOOD
    if (anyRow(GOFOOD_SUMMARY_ID) && anyRow(GOFOOD_PRICE_ID)) return ReceiptSource.GOFOOD
    ORDER_APPS.firstOrNull { (regex, _) -> anyRow(regex) }?.let { return it.second }
    if (anyRow(SHOPEEFOOD_ORDER)) return ReceiptSource.SHOPEEFOOD
    BANK_APPS.firstOrNull { (regex, _) -> anyRow(regex) }?.let { return it.second }

    val top = rows.minOf { it.top }
    val quarter = top + (rows.maxOf { it.bottom } - top) / 4
    for (row in rows.filter { it.top <= quarter }) {
        HEADERS.firstOrNull { (regex, _) -> regex.containsMatchIn(row.text) }?.let { return it.second }
        val key = chainKey(row.text)
        SHOPS.firstOrNull { (k, _) -> k in key }?.let { return it.second }
    }

    return WALLET_BALANCE.firstOrNull { (regex, _) -> anyRow(regex) }?.second
}
