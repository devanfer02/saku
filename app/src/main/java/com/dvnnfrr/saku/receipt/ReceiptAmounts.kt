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
