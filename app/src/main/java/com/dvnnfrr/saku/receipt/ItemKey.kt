package com.dvnnfrr.saku.receipt

private val SPACES = Regex("""\s+""")
private val TRAILING_QUANTITY = Regex("""\s+[×xX]\s*\d+$""")

/**
 * The name an item is known by across receipts: trimmed, single-spaced, upper case, and
 * without a trailing quantity (` ×2`, ` x 2`). `SUSU UHT 1L ×2` and `susu uht  1l` share
 * one key, so the category dictionary and price history treat them as one item.
 */
fun itemKey(name: String): String = name.trim()
    .replace(SPACES, " ")
    .uppercase()
    .replace(TRAILING_QUANTITY, "")
