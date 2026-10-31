package com.dvnnfrr.saku.receipt

private val SPACES = Regex("""\s+""")

/**
 * A quantity at the end of a name, in the ways receipts and people write it: ` ×2`,
 * ` x 2`, ` 2x`, ` 2X`, ` 3 biji`, ` 20pcs`, ` 2 porsi`, ` 2 bks`. Group 1 or 2 is n.
 */
private val TRAILING_QUANTITY = Regex(
    """\s+(?:[×x]\s*(\d+)|(\d+)\s*(?:[×x]|pcs|pc|biji|porsi|bks|bungkus))$""",
    RegexOption.IGNORE_CASE,
)

/**
 * The name an item is known by across receipts and hand-typed notes: trimmed,
 * single-spaced, upper case, and without a trailing quantity (` ×2`, ` 2X`, ` 3 biji`).
 * `SUSU UHT 1L ×2` and `susu uht  1l` share one key, so the category dictionary and price
 * history treat them as one item. Otherwise an exact match.
 */
fun itemKey(name: String): String = name.trim()
    .replace(SPACES, " ")
    .uppercase()
    .replace(TRAILING_QUANTITY, "")

/**
 * The name as a person would search for it: trimmed, single-spaced, case kept, without a
 * trailing quantity. `Indomie Goreng 2X` gives `Indomie Goreng`.
 */
fun itemSearchName(name: String): String = name.trim()
    .replace(SPACES, " ")
    .replace(TRAILING_QUANTITY, "")

private val QUANTITIES = 1..999

/**
 * How many units a note says were bought: n from a trailing ` ×n` or ` x n` (1 to 999),
 * ` 2x`, ` 3 biji` and the like, else 1. The same suffix [itemKey] drops, so a row's key
 * and its quantity always agree.
 */
fun itemQuantity(name: String): Int {
    val match = TRAILING_QUANTITY.find(name.trim()) ?: return 1
    val n = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.toIntOrNull() ?: return 1
    return if (n in QUANTITIES) n else 1
}
