package com.dvnnfrr.saku.receipt

private val SPACES = Regex("""\s+""")
private val TRAILING_QUANTITY = Regex("""\s+[×xX]\s*(\d+)$""")

/**
 * The name an item is known by across receipts: trimmed, single-spaced, upper case, and
 * without a trailing quantity (` ×2`, ` x 2`). `SUSU UHT 1L ×2` and `susu uht  1l` share
 * one key, so the category dictionary and price history treat them as one item.
 */
fun itemKey(name: String): String = name.trim()
    .replace(SPACES, " ")
    .uppercase()
    .replace(TRAILING_QUANTITY, "")

private val QUANTITIES = 1..999

/**
 * How many units a note says were bought: n from a trailing ` ×n` or ` x n` (1 to 999),
 * else 1. The same suffix [itemKey] drops, so a row's key and its quantity always agree.
 */
fun itemQuantity(name: String): Int {
    val n = TRAILING_QUANTITY.find(name.trim())?.groupValues?.get(1)?.toIntOrNull() ?: return 1
    return if (n in QUANTITIES) n else 1
}
