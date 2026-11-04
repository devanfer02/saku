package com.dvnnfrr.saku.receipt

import com.dvnnfrr.saku.domain.Money

/**
 * An app's order screen as read: the amount paid and its row, the items, and the shop or
 * restaurant when the screen names it above the items.
 */
data class AppOrderPick(val total: Money?, val totalRow: Int?, val items: ItemsPick, val shop: StorePick?)

private fun pattern(source: String) = Regex(source, RegexOption.IGNORE_CASE)

/**
 * An amount at the end of a row: `Rp110.000`, `-14.700`, `-Rp67.000`, `(5.000)`, and with
 * `Rp` or a minus, a small one: `-Rp500` of Koin Shopee.
 */
private val TRAILING_AMOUNT = pattern(
    """\s*(-?\(?(?:rp\.?\s*)?-?\s*(?:\d{1,3}(?:[.,]\d{3})+|\d{4,7})\)?|-?\s*rp\.?\s*\d{3}|-\s*\d{3})\s*$""",
)

/** `2 x Paket Kenyang`, `1x Nasi Goreng`: the quantity printed first. */
private val QUANTITY_FIRST = pattern("""^(\d{1,2})\s*[x×]\s+(.*\S.*)$""")

/** `2 Mie Ayam  30.000`: a quantity first with no `x`, only on a priced row; a name row
 * such as `2 Pcs Kaos` waits for its price row. */
private val COUNT_FIRST = pattern("""^(\d{1,2})\s+(\p{L}.*)$""")

/** GoFood's `Ayam Goreng Jumbo  1`: the name, then the quantity, priced in the summary. */
private val QUANTITY_LAST = pattern("""^(.*\p{L}.*?)\s+(\d{1,2})$""")

/** Tokopedia's `1 x Rp45.000` and Shopee's `x2 Rp89.000`, under the name: a unit price. */
private val UNIT_ROW = pattern("""^(?:(\d{1,3})\s*[x×]|[x×]\s*(\d{1,3}))$""")

private val LETTERS = Regex("""\p{L}{2,}""")

/** An item's option or note: `Flavour : Barbeque`, `Variasi: Hitam`, cutlery. Never an item or part of a name. */
private val OPTION = pattern("""^[^:]{1,30}:|cutlery|straw|alat\s*makan|sedotan|\bcatatan\b|\bnotes?\b""")

/** The app's own buttons in the item list. */
private val CHROME = pattern(
    """\blihat\b|\bshow\s*(less|more|all)\b|\bsee\s*(less|more|all)\b|\btampilkan\b|\bubah\b|\bedit\b|""" +
        """\bbeli\s*lagi\b|\bnilai\b|\bbantuan\b|\bhelp\b""",
)

/** A shop row with its button beside it: `Toko Sinar Jaya  Kunjungi Toko`. */
private val SHOP_BUTTON = pattern("""\s*\b(kunjungi\s*toko|lihat\s*toko|chat\s*penjual|chat)\s*$""")

/** Where the item list starts on screen. */
private val ITEMS_HEADER = pattern(
    """purchase\s*item|rincian\s*pesanan|detail\s*pesanan|detail\s*produk|order\s*(details|summary)|""" +
        """ringkasan\s*pesanan|pesanan\s*kamu""",
)

/** How it was paid: never an item, however it is laid out. */
private val PAYMENT = pattern(
    """\bgo\s?pay\b|\bovo\b|\bdana\b|shopee\s?pay|\blink\s?aja\b|\bpaid\s*(with|by|using)\b|dibayar\s*(dengan|pakai)|""" +
        """metode\s*pembayaran|payment\s*method|\btunai\b|\bcash\b""",
)

private val PAYMENT_SUMMARY = pattern(
    """payment\s*(summary|details)|ringkasan\s*pembayaran|rincian\s*pembayaran|detail\s*pembayaran""",
)

/* The summary's words, tried in this order. */
private val DISCOUNT = pattern(
    """\bdiscounts?\b|(?<!setelah\s)\bdiskon\b|\bpromo\b|\bvoucher\b|\bpotongan\b|\bkoin\b|\bcoins?\b|\bcashback\b|""" +
        """bebas\s*ongkir""",
)
private val SHIPPING = pattern("""pengiriman|ongkos\s*kirim|\bongkir\b|\bdelivery\b|\bshipping\b""")
private val SUBTOTAL = pattern("""\bsub\s*-?\s*total\b|^\W*(price|harga)\b|total\s*harga""")
private val FEE = pattern(
    """\bfees?\b|\bfee\(s\)|\bcharges?\b|\bbiaya\b|\bkemasan\b|\bbungkus\b|\bpackaging\b|\bpajak\b|\btax\b|\bppn\b|""" +
        """\bservice\b|\blayanan\b|\basuransi\b|\bproteksi\b""",
)

/** Totals that are not the amount paid: `Total Harga`, `Total Ongkos Kirim`, `Total Diskon`. */
private val NOT_TOTAL = pattern(
    """\bsub\s*-?\s*total|total\s*(item|menu|qty|harga|ongkos|ongkir|pengiriman|diskon|potongan|cashback|barang|produk)""",
)
private val STRONG_TOTAL = pattern("""total\s*(pembayaran|payment|tagihan|belanja|bayar|dibayar)""")
private val TOTAL = pattern("""\btotal\b""")

/** ShopeeFood's `Paid Rp132.000`; `Paid with ShopeePay` is a payment row. */
private val PAID = pattern("""^\W*(paid|dibayar|bayar)\b(?!\s*(with|by|using|dengan|pakai|via))""")

/** ShopeeFood prints this under the amount paid. */
private val TAX_INCLUDED = pattern("""termasuk\s*pajak|incl(uding|\.)?\s*tax|tax\s*included""")

private const val NAME_ROWS = 2

private data class Amount(val rupiah: Long, val negative: Boolean)

/** The row's text before its trailing amounts, and those amounts left to right. */
private fun split(text: String): Pair<String, List<Amount>> {
    var label = text.trim()
    val amounts = mutableListOf<Amount>()
    while (true) {
        val match = TRAILING_AMOUNT.find(label) ?: break
        val token = match.groupValues[1]
        amounts.add(0, Amount(token.filter(Char::isDigit).toLong(), token.contains('-') || token.contains('(')))
        label = label.substring(0, match.range.first).trim()
    }
    return label to amounts
}

private fun isTotal(text: String) = TOTAL.containsMatchIn(text) && !NOT_TOTAL.containsMatchIn(text)

private fun cleanName(name: String): String = name.replace(Regex("""\s+"""), " ").trim { it.isWhitespace() || it in ":.-=*#" }

private class Pending(val rows: MutableList<Int>, var name: String, val quantity: Int, val price: Long?)

/**
 * Reads an app's order screen: GoFood, ShopeeFood, Shopee and Tokopedia. Such a screen
 * lists the items, then a summary of price, fees, discounts and the amount paid. See
 * design.md (receipt-items), "App order screens".
 */
fun pickAppOrder(rows: List<ReceiptRow>): AppOrderPick {
    val texts = rows.map { it.text.trim() }
    val pending = mutableListOf<Pending>()
    val nameRows = mutableListOf<Int>()
    var headerRow = -1
    var shop: StorePick? = null
    var listed = false
    var joinable = false
    var summaryStart = rows.size

    for ((index, text) in texts.withIndex()) {
        val (label, amounts) = split(text)
        val price = amounts.lastOrNull()?.takeUnless { it.negative }?.rupiah
        val unit = UNIT_ROW.matchEntire(label)

        // Shopee and Tokopedia: the name rows above, then `x2 Rp89.000`. A first name row
        // right under the heading, with another under it, is the shop.
        if (unit != null && price != null && nameRows.isNotEmpty()) {
            val quantity = (unit.groupValues[1].ifEmpty { unit.groupValues[2] }).toInt()
            if (nameRows.size >= NAME_ROWS && nameRows.first() == headerRow + 1) {
                shop = shop ?: StorePick(texts[nameRows.first()], nameRows.first())
                nameRows.removeAt(0)
            }
            val name = nameRows.joinToString(" ") { texts[it] }
            pending += Pending((nameRows + index).toMutableList(), name, quantity, quantity * price)
            nameRows.clear()
            joinable = false
            continue
        }

        // Tokopedia repeats each product's line price under it as `Total Harga`.
        val last = pending.lastOrNull()
        if (SUBTOTAL.containsMatchIn(text) && last != null && price == last.price && last.rows.last() >= index - 2) {
            last.rows += index
            continue
        }

        val leading = QUANTITY_FIRST.matchEntire(label) ?: COUNT_FIRST.matchEntire(label)?.takeIf { price != null }
        val summaryWord = DISCOUNT.containsMatchIn(text) || SUBTOTAL.containsMatchIn(text) ||
            FEE.containsMatchIn(text) || SHIPPING.containsMatchIn(text)
        val totalWord = isTotal(text) || PAID.containsMatchIn(text)
        // A summary word opens the summary only with an amount, or as a total: `Paket Promo 1` is an item.
        val opensSummary = leading == null &&
            (PAYMENT_SUMMARY.containsMatchIn(text) || totalWord || summaryWord && amounts.isNotEmpty())
        if (opensSummary && !PAYMENT.containsMatchIn(text)) {
            summaryStart = index
            break
        }
        if (ITEMS_HEADER.containsMatchIn(text)) {
            listed = true
            joinable = false
            headerRow = index
            nameRows.clear()
            continue
        }
        val button = SHOP_BUTTON.find(text)
        if (button != null) {
            val name = text.substring(0, button.range.first).trim()
            if (shop == null && LETTERS.containsMatchIn(name)) shop = StorePick(name, index)
            nameRows.clear()
            joinable = false
            continue
        }
        if (OPTION.containsMatchIn(text)) {
            joinable = false
            continue
        }
        if (PAYMENT.containsMatchIn(text) || CHROME.containsMatchIn(text) || !LETTERS.containsMatchIn(label)) {
            joinable = false
            nameRows.clear()
            continue
        }
        val trailing = QUANTITY_LAST.matchEntire(label)
        when {
            leading != null -> {
                pending += Pending(mutableListOf(index), leading.groupValues[2], leading.groupValues[1].toInt(), price)
                listed = true
                joinable = true
                nameRows.clear()
            }
            listed && trailing != null && trailing.groupValues[2].toInt() > 0 -> {
                pending += Pending(mutableListOf(index), trailing.groupValues[1], trailing.groupValues[2].toInt(), price)
                joinable = true
                nameRows.clear()
            }
            listed && price != null -> {
                pending += Pending(mutableListOf(index), label, 1, price)
                joinable = true
                nameRows.clear()
            }
            joinable && amounts.isEmpty() -> {
                val item = pending.last()
                item.name = item.name + " " + label
                item.rows += index
            }
            amounts.isEmpty() -> {
                nameRows += index
                if (nameRows.size > NAME_ROWS) nameRows.removeAt(0)
            }
            else -> joinable = false
        }
    }

    val totalRow = totalRowOf(texts, summaryStart)
    val total = totalRow?.let { split(texts[it]).second.lastOrNull() }?.let { Money(it.rupiah) }

    val used = mutableSetOf<Int>()
    val discounts = mutableListOf<Money>()
    val fees = mutableListOf<Pair<Int, Long>>()
    var subtotal: Long? = null
    for (index in summaryStart until (totalRow ?: rows.size)) {
        val text = texts[index]
        val amount = split(text).second.lastOrNull() ?: continue
        if (PAYMENT.containsMatchIn(text) || isTotal(text) || PAID.containsMatchIn(text)) continue
        when {
            DISCOUNT.containsMatchIn(text) || amount.negative -> discounts += Money(amount.rupiah)
            SHIPPING.containsMatchIn(text) -> fees += index to amount.rupiah
            SUBTOTAL.containsMatchIn(text) -> subtotal = subtotal ?: amount.rupiah
            FEE.containsMatchIn(text) -> fees += index to amount.rupiah
            else -> continue
        }
        used += index
    }
    val additions = withoutGroupTotals(fees).map(::Money)

    val pricedSum = pending.sumOf { it.price ?: 0L }
    val unpriced = pending.count { it.price == null }
    val left = subtotal?.minus(pricedSum)?.takeIf { unpriced == 1 && it > 0 }
    val items = pending.mapNotNull { item ->
        val price = item.price ?: left ?: return@mapNotNull null
        used += item.rows
        ReceiptItem(cleanName(item.name), item.quantity, Money(price))
    }
    val pick = if (items.isEmpty()) ItemsPick.NONE else ItemsPick(items, discounts, additions, used)
    return AppOrderPick(total, totalRow, pick, shop)
}

/**
 * The row of the amount paid: a `Total pembayaran`-style row first, then a plain `Total`,
 * then `Paid`, then the amount beside or above `Sudah termasuk pajak`. A row with two
 * amounts (the struck-through one first) counts its last.
 */
private fun totalRowOf(texts: List<String>, from: Int): Int? {
    val rows = (from until texts.size).filter { split(texts[it]).second.lastOrNull()?.negative == false }
    rows.firstOrNull { STRONG_TOTAL.containsMatchIn(texts[it]) && isTotal(texts[it]) }?.let { return it }
    rows.firstOrNull { isTotal(texts[it]) }?.let { return it }
    rows.firstOrNull { PAID.containsMatchIn(texts[it]) }?.let { return it }
    val note = (from until texts.size).firstOrNull { TAX_INCLUDED.containsMatchIn(texts[it]) } ?: return null
    return listOf(note, note - 1).firstOrNull { it in rows }
}

/**
 * GoFood folds its smaller fees under one row (`Biaya lainnya 7.000`, then `Biaya jasa
 * aplikasi 5.000` and `Biaya parkir 2.000`); a fee equal to the sum of two or more fees
 * right after it is that group's total, and is counted once, through its parts.
 */
private fun withoutGroupTotals(fees: List<Pair<Int, Long>>): List<Long> = fees.filterIndexed { i, (_, amount) ->
    var sum = 0L
    var parts = 0
    for (j in i + 1 until fees.size) {
        sum += fees[j].second
        parts++
        if (sum >= amount) break
    }
    !(parts >= 2 && sum == amount)
}.map { it.second }
