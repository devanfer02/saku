package com.dvnnfrr.saku.receipt

import com.dvnnfrr.saku.domain.Money

/*
 * Rupiah as receipts print it: `25.000`, `25,000`, `Rp25.000`, `25000`, `25.000,00`.
 *
 * A separated number needs whole groups of three. An unseparated one is 4 to 7 digits:
 * longer runs are transaction ids, phone numbers and dates (`15072022`), never a price.
 * A trailing `,00` or `.00` is sen, and dropped; a group of two can never be thousands.
 */
private val AMOUNT = Regex(
    """(?<![\d.,])(?:rp\.?\s*)?(\d{1,3}(?:[.,]\d{3})+|\d{4,7})(?:[.,]\d{2})?(?!\d)""",
    RegexOption.IGNORE_CASE,
)

/** Below this a number is a quantity, an item count or a percentage, not a price. */
private const val MIN_RUPIAH = 100L

/** Every amount on [text], left to right. */
fun amountsIn(text: String): List<Money> = AMOUNT.findAll(withoutDates(text))
    .mapNotNull { match -> match.groupValues[1].filter(Char::isDigit).toLongOrNull() }
    .filter { it >= MIN_RUPIAH }
    .map(::Money)
    .toList()

private val CONFUSED = setOf("06", "08", "09", "68", "69", "89", "38", "56", "17")

/**
 * Whether two amounts read from the same printed number could be the same, given how
 * recognition mixes up digits (`17.500` and `17.508`): equal, or the same length with at
 * most two digits differing, each a known mix-up.
 */
internal fun lookAlike(a: Long, b: Long): Boolean {
    if (a == b) return true
    val x = a.toString()
    val y = b.toString()
    if (x.length != y.length) return false
    val differ = x.indices.filter { x[it] != y[it] }
    return differ.size <= 2 && differ.all { i -> listOf(x[i], y[i]).sorted().joinToString("") in CONFUSED }
}

/** Of two look-alike readings, the one a till would print: ending in 00, then 50. */
internal fun rounder(computed: Long, read: Long): Long {
    fun score(v: Long) = when {
        v % 100 == 0L -> 2
        v % 50 == 0L -> 1
        else -> 0
    }
    return if (score(computed) > score(read)) computed else read
}

private val MISREAD_ENDINGS = setOf(6L, 8L, 68L, 80L, 86L, 88L)

/**
 * An item price with no second reading to check it against, with a final `0` or `00`
 * that recognition turned into 6 or 8 put back (`12.408` to `12.400`). Only endings no
 * till prints are repaired: see design.md (receipt-items), "Zeros read as 8".
 */
internal fun repairZeros(amount: Long): Long {
    if (amount < 1_000) return amount
    val lastTwo = amount % 100
    return if (lastTwo in MISREAD_ENDINGS) amount - lastTwo else amount
}
