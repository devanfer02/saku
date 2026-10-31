package com.dvnnfrr.saku.receipt

import com.dvnnfrr.saku.domain.Money

/** One bought item: [price] is the line price for [quantity], [discount] the cut printed under it. */
data class ReceiptItem(
    val name: String,
    val quantity: Int,
    val price: Money,
    val discount: Money = Money.ZERO,
) {
    val net: Money get() = price - discount
}

/**
 * The item rows of a receipt, the discounts on the whole receipt, and tax or service rows
 * added on top. [rowIndices] are the rows used, so they are not offered as unplaced.
 */
data class ItemsPick(
    val items: List<ReceiptItem>,
    val receiptDiscounts: List<Money>,
    val additions: List<Money>,
    val rowIndices: Set<Int>,
) {
    companion object {
        val NONE = ItemsPick(emptyList(), emptyList(), emptyList(), emptySet())
    }
}

private fun pattern(source: String) = Regex(source, RegexOption.IGNORE_CASE)

private const val TOTAL_WORD = """t\s?[o0]\s?t\s?[a4]\s?[l1i|]"""

private val ZONE_END = pattern(
    """sub\s*-?\s*$TOTAL_WORD|$TOTAL_WORD|harga\s*jual|tagihan|jumlah|grand|\btunai\b|\bcash\b|""" +
        """kembali|\bchange\b|\bdebit\b|\bkartu\b|\bcard\b|\bbayar\b|\bedc\b|\bqris\b""",
)
private val DISCOUNT_WORD = pattern("""diskon|disc(?:ount)?\b|potongan|hemat|promo|voucher""")
private val DISCOUNT_LABEL = pattern("""^[\W_]*(?:(?:diskon|disc(?:ount)?|potongan|hemat|promo|voucher|item|member)[\W_]*)+$""")
private val ADDITION_WORD = pattern("""\btax\b|pajak|\bpb\s*1\b|\bppn\b|servi[cs]e?|\bsvc\b""")

// A group separator may be read as `-` or `:` on a dot-matrix print (`5-280`, `1:300`).
private val AMOUNT_TOKEN = pattern("""^\(?-?(?:rp\.?)?-?(\d{1,3}(?:[.,:\-]\d{3})+|\d{3,7})(?:[.,]\d{2})?\)?-?$""")
private val QTY_TOKEN = pattern("""^(\d{1,3})(?:[x×]|pcs?)?$""")
private val TIMES_TOKEN = pattern("""^[x×@*]$""")
private val GLUED_TIMES = pattern("""(?<=\s|^)([x×@])(?=\d)""")
private val RP_TOKEN = pattern("""^rp\.?$""")
private val LEAD_QTY = pattern("""^(\d{1,2})\s*[x×]?\s+(?=\p{L})""")
private val O_IN_NUMBER = Regex("""(?<=\d)[oO]|[oO](?=\d)""")
private val LETTERS = Regex("""\p{L}.*\p{L}""")
private val PRODUCT_CODE = Regex("""^\d{5,}\s+(?=\S*\p{L})""")
private val WEIGHT = Regex("""^\d{2,4}$""")
private val PER_WEIGHT = Regex("""^\d{1,3}[.,]\d{1,2}$""")
private val PLAIN_QTY = Regex("""^\d{1,3}$""")
private val QTY_FIRST = Regex("""^\d{1,3}\s+\S*\p{L}""")
private val GLUED_MINUS = Regex("""(?<=\p{L})-(?=\d)""")
private val CUT_PRICE = Regex("""^\d{1,3}[.,]\d{1,2}$""")
private val SEPARATED = Regex("""^\d{1,3}(?:[.,]\d{3})+$""")
private val INNER_DISCOUNT = pattern("""(?<=\s|^)(?:hema[t1il|]|diskon|potongan)[\s:]*-?\(?(\d{1,3}(?:[.,]\d{3})+)\)?(?=\s|$)""")
private val GLUED_QTY = Regex("""^(\d{1,2})\p{L}""")

/** A name row that also carries the discount of the item above it: `BANGO KECAP HEMAT -12.270`. */
private val NAME_THEN_DISCOUNT = pattern("""^(.*\p{L}.*?)\s+(?:hema[t1il|]|diskon|disc|potongan)[\W_]*$""")

private const val MIN_ITEM = 100L
private const val MIN_COLUMN_ROWS = 4
private const val MIN_CUT_DIGITS = 3

/**
 * A row read from its right end: a line price, then maybe a unit price, an `x` and a
 * quantity, and what is left as the name (empty for the second row of a two-row item).
 */
private data class Tail(
    val name: String,
    val quantity: Int?,
    val unit: Long?,
    val price: Long,
    val negative: Boolean,
    val times: Boolean,
)

private fun readTail(text: String, quantityColumn: Boolean = false): Tail? {
    val tokens = text.replace(GLUED_TIMES, "$1 ").replace(GLUED_MINUS, " -").trim().split(Regex("""\s+"""))
        .filterNot { RP_TOKEN.matches(it) }
        .toMutableList()
    val last = tokens.removeLastOrNull()?.let { uncut(it, tokens) } ?: return null
    val price = amountOf(last) ?: return null
    if (price < MIN_ITEM) return null
    val negative = last.startsWith("-") || last.startsWith("(") || last.endsWith(")") || last.endsWith("-")

    // A weighed line prints grams and the price per 100 g before the price: `830 25.5`.
    if (tokens.size >= 3 && PER_WEIGHT.matches(tokens.last()) && WEIGHT.matches(tokens[tokens.lastIndex - 1])) {
        tokens.removeAt(tokens.lastIndex)
        tokens.removeAt(tokens.lastIndex)
    }

    var unit = tokens.lastOrNull()?.let(::amountOf)?.takeIf { it >= MIN_ITEM }
    if (unit != null) tokens.removeAt(tokens.lastIndex)
    // A quantity of one digit read glued to the unit price: `17.390 7.390` for `1 7.390 7.390`.
    var gluedQuantity: Int? = null
    if (unit != null && unit != price && !lookAlike(unit, price) && unit.toString().length >= 4) {
        val digits = unit.toString()
        val first = digits.first().digitToInt()
        val rest = digits.drop(1).toLong()
        if (first > 0 && rest >= MIN_ITEM && lookAlike(first * rest, price)) {
            gluedQuantity = first
            unit = rest
        }
    }
    val times = tokens.lastOrNull()?.let { TIMES_TOKEN.matches(it) } == true
    if (times) tokens.removeAt(tokens.lastIndex)
    var quantity = gluedQuantity ?: tokens.lastOrNull()?.let { QTY_TOKEN.matchEntire(it) }?.groupValues?.get(1)?.toIntOrNull()
    if (quantity != null && gluedQuantity == null) tokens.removeAt(tokens.lastIndex)
    // With a quantity column, a number before the unit price is part of the name
    // (`GULA HALUS 500 935 935`) unless it checks out as a quantity.
    if (quantityColumn && quantity != null && !times && unit != null && !lookAlike(quantity * unit, price)) {
        tokens += quantity.toString()
        quantity = null
    }

    // `QTY NAME UNIT TOTAL`, the quantity in a column of its own before the name.
    if (quantity == null && !times && unit != null && tokens.size >= 2 && PLAIN_QTY.matches(tokens.first()) &&
        (LETTERS.containsMatchIn(tokens[1]) || quantityColumn && tokens.drop(1).any { LETTERS.containsMatchIn(it) })
    ) {
        quantity = tokens.removeAt(0).toInt()
    }
    // On such a receipt the quantity may be read glued to the name (`1GULA`, `16ULA` for
    // `1 GULA`); it is split off only when quantity times unit price checks out.
    if (quantityColumn && quantity == null && !times && unit != null && tokens.isNotEmpty()) {
        val glued = GLUED_QTY.find(tokens.first())
        val split = glued?.let { (1..it.groupValues[1].length).firstOrNull { n -> lookAlike(it.groupValues[1].take(n).toLong() * unit, price) } }
        if (glued != null && split != null) {
            quantity = tokens.first().take(split).toInt()
            tokens[0] = tokens.first().drop(split)
        }
    }

    // A lone number before the price is part of the name (`MIE 200 11.200`) unless it is
    // the unit price repeated or a quantity of one.
    if (quantity == null && !times && unit != null && lookAlike(unit, price)) quantity = 1
    // A number before the price is the unit price on a receipt with a quantity column,
    // even when it was misread and does not check out; elsewhere it belongs to the name.
    if (quantity == null && !times && unit != null && unit != price && !quantityColumn) {
        tokens += unit.toString()
        unit = null
    }
    if (unit == null && !times && quantity != null && quantity != 1) {
        tokens += quantity.toString()
        quantity = null
    }
    return Tail(tokens.joinToString(" "), quantity, unit, price, negative, times)
}

/*
 * A line price cut by the photo's edge (`86.49` for 86.490) is put back from the quantity
 * and unit price before it, when their product starts with the digits that were read.
 */
private fun uncut(last: String, before: List<String>): String {
    if (!CUT_PRICE.matches(last)) return last
    val unit = before.lastOrNull()?.takeIf { SEPARATED.matches(it) }?.let(::amountOf) ?: return last
    val quantity = before.getOrNull(before.lastIndex - 1)?.let { QTY_TOKEN.matchEntire(it) }?.groupValues?.get(1)?.toLongOrNull() ?: 1L
    val read = last.filter(Char::isDigit)
    val whole = (quantity * unit).toString()
    return if (read.length >= MIN_CUT_DIGITS && whole.length > read.length && whole.startsWith(read)) whole else last
}

private fun amountOf(token: String): Long? =
    AMOUNT_TOKEN.matchEntire(token)?.groupValues?.get(1)?.filter(Char::isDigit)?.toLongOrNull()

private fun cleanName(name: String): String = name
    .split(' ')
    .joinToString(" ") { token -> if (token.any(Char::isDigit)) token.replace(O_IN_NUMBER, "0") else token }
    .replace(PRODUCT_CODE, "")
    .trim { it.isWhitespace() || it in ":.-=*#" }
    .replace(Regex("""\s+"""), " ")

private fun isName(text: String): Boolean = LETTERS.containsMatchIn(text) && !DISCOUNT_LABEL.matches(text)

private fun isDiscount(tail: Tail): Boolean = tail.negative || DISCOUNT_LABEL.matches(tail.name)

/**
 * How well a row reads as an item, to choose between two photos' copies of one row:
 * 2 when it ends in a price, 3 when its quantity times unit price is also exact.
 */
internal fun rowQuality(text: String): Int {
    val tail = readTail(text) ?: return 0
    val quantity = tail.quantity ?: return 2
    val unit = tail.unit ?: return 2
    return if (quantity * unit == tail.price) 3 else 2
}

/*
 * Rows printed close together may be read as one: an item row with the discount row under
 * it inside (`LE MINERALE 330ML 24 HEMAT -11.060 2.290 54.960`). The discount is taken
 * out, and returned with the rest, when the rest still reads as a named item.
 */
private fun innerDiscount(text: String, quantityColumn: Boolean): Pair<String, Long>? {
    val match = INNER_DISCOUNT.find(text) ?: return null
    val rest = text.removeRange(match.range).replace(Regex("""\s+"""), " ").trim()
    val tail = readTail(rest, quantityColumn) ?: return null
    if (tail.negative || !isName(tail.name)) return null
    val amount = match.groupValues[1].filter(Char::isDigit).toLongOrNull()?.takeIf { it >= MIN_ITEM } ?: return null
    return rest to repairZeros(amount)
}

/** The line price a row ends in, read as an item row is (`1:300` included), or null. */
internal fun linePrice(text: String): Long? = readTail(text)?.price

/*
 * A receipt printed as `QTY NAME UNIT TOTAL` (a dot-matrix till, Gelael) has most of its
 * priced rows starting with a plain quantity and a unit price before the line price.
 */
private fun hasQuantityColumn(rows: List<ReceiptRow>): Boolean {
    val named = rows.map { it.text.trim() }.filter { text -> readTail(text)?.let { LETTERS.containsMatchIn(it.name) } == true }
    val columned = named.count { text -> QTY_FIRST.containsMatchIn(text) && readTail(text)?.unit != null }
    return named.size >= MIN_COLUMN_ROWS && columned * 2 >= named.size
}

/** Quantity and price of an item, kept only when quantity times unit price checks out. */
private fun priced(tail: Tail, leadQuantity: Int?): Pair<Int, Long> {
    val quantity = tail.quantity ?: leadQuantity
    val unit = tail.unit
    if (quantity != null && quantity > 0 && unit != null) {
        val computed = quantity * unit
        if (computed == tail.price) return quantity to tail.price
        if (lookAlike(computed, tail.price)) return quantity to rounder(computed, tail.price)
    }
    return 1 to repairZeros(tail.price)
}

/**
 * Reads the item rows, between the header and the first subtotal, total or payment row,
 * and the receipt discounts and additions between there and [totalRow]. Items with the
 * same name and unit price and no discount of their own are merged into one, quantities
 * added. See design.md (receipt-items) section 3.
 */
fun pickItems(rows: List<ReceiptRow>, totalRow: Int?): ItemsPick {
    val items = mutableListOf<ReceiptItem>()
    val used = mutableSetOf<Int>()
    var pendingName: Int? = null
    // The name of a pending row when it is only part of the row's text.
    var pendingText: String? = null
    var nameRowItem: Int? = null
    var zoneEnd = rows.size
    val quantityColumn = hasQuantityColumn(rows)

    for ((index, row) in rows.withIndex()) {
        if (items.isNotEmpty() && ZONE_END.containsMatchIn(row.text)) {
            zoneEnd = index
            break
        }
        val inner = innerDiscount(row.text.trim(), quantityColumn)
        val text = inner?.first ?: row.text.trim()
        val itemsBefore = items.size
        val tail = readTail(text, quantityColumn)
        if (tail == null) {
            pendingName = index.takeIf { isName(text) && !ZONE_END.containsMatchIn(text) }
            pendingText = null
            continue
        }
        val pending = pendingName
        val pendingNameText = pendingText
        pendingText = null
        val priceAbove = nameRowItem.takeIf { it == index - 1 }
        pendingName = null
        nameRowItem = null
        when {
            priceAbove != null && tail.name.isEmpty() && !tail.negative && tail.quantity != null -> {
                // A two-row item whose price was grouped with its name: `TEMPE PAPAN 18.000`
                // above `3 x 6.000`. The quantity row confirms the quantity.
                val last = items.last()
                val computed = tail.quantity * (tail.unit ?: tail.price)
                if (lookAlike(computed, last.price.rupiah)) {
                    items[items.lastIndex] = last.copy(
                        quantity = tail.quantity,
                        price = Money(rounder(computed, last.price.rupiah)),
                    )
                    used += index
                }
            }
            items.isNotEmpty() && pending == null && NAME_THEN_DISCOUNT.matches(tail.name) -> {
                // A curled photo shifts the price column down a row: the next item's name
                // sits beside this item's discount, and its price on the row below.
                val last = items.removeAt(items.lastIndex)
                items += last.copy(discount = last.discount + Money(repairZeros(tail.price)))
                used += index
                pendingName = index
                pendingText = NAME_THEN_DISCOUNT.matchEntire(tail.name)?.groupValues?.get(1)
            }
            items.isNotEmpty() && isDiscount(tail) && pending == null -> {
                val last = items.removeAt(items.lastIndex)
                items += last.copy(discount = last.discount + Money(repairZeros(tail.price)))
                used += index
            }
            tail.name.isEmpty() && pending != null && !tail.negative -> {
                val (quantity, price) = priced(tail, null)
                items += ReceiptItem(cleanName(pendingNameText ?: rows[pending].text), quantity, Money(price))
                used += pending
                used += index
            }
            isName(tail.name) && !tail.negative && !ZONE_END.containsMatchIn(tail.name) -> {
                val lead = LEAD_QTY.find(tail.name)
                val name = lead?.let { tail.name.substring(it.range.last + 1) } ?: tail.name
                val (quantity, price) = priced(tail, lead?.groupValues?.get(1)?.toIntOrNull())
                items += ReceiptItem(cleanName(name), quantity, Money(price))
                used += index
                if (tail.unit == null && quantity == 1) nameRowItem = index
            }
        }
        if (inner != null && items.size > itemsBefore) {
            val last = items.removeAt(items.lastIndex)
            items += last.copy(discount = last.discount + Money(inner.second))
        }
    }
    if (items.isEmpty()) return ItemsPick.NONE

    val end = totalRow?.takeIf { it > zoneEnd } ?: rows.size
    val discounts = mutableListOf<Money>()
    val additions = mutableListOf<Money>()
    for (index in zoneEnd until end) {
        val text = rows[index].text
        if (totalRow == null && index > zoneEnd && isPaymentRow(text)) break
        val tail = readTail(text) ?: continue
        when {
            DISCOUNT_WORD.containsMatchIn(text) || tail.negative -> discounts += Money(repairZeros(tail.price))
            ADDITION_WORD.containsMatchIn(text) -> additions += Money(tail.price)
            else -> continue
        }
        used += index
    }

    // Prices on most Indonesian till receipts include tax; a PPN row above the total is
    // then information, and adding it would break a check that already holds.
    val net = items.sumOf { it.net.rupiah } - discounts.sumOf { it.rupiah }
    val total = totalRow?.let { amountsIn(rows[it].text).lastOrNull() }
    val keptAdditions = if (total != null && net == total.rupiah) emptyList() else additions

    return ItemsPick(merged(items), discounts, keptAdditions, used)
}

private fun merged(items: List<ReceiptItem>): List<ReceiptItem> {
    val out = mutableListOf<ReceiptItem>()
    for (item in items) {
        val same = out.indexOfFirst { other ->
            item.discount.isZero && other.discount.isZero &&
                itemKey(other.name) == itemKey(item.name) &&
                other.price.rupiah * item.quantity == item.price.rupiah * other.quantity
        }
        if (same < 0) {
            out += item
        } else {
            val other = out[same]
            out[same] = other.copy(quantity = other.quantity + item.quantity, price = other.price + item.price)
        }
    }
    return out
}
