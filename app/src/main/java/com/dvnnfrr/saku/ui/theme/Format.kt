package com.dvnnfrr.saku.ui.theme

import com.dvnnfrr.saku.domain.Money

private const val RUPIAH_PREFIX = "Rp"
private const val GROUP_SEPARATOR = '.'
private const val GROUP_SIZE = 3

/**
 * Formats whole rupiah the Indonesian way: `Rp 5.061.400`, full stop as the thousands
 * separator, no decimal component.
 *
 * The grouping is done by hand on the digit string rather than through `NumberFormat`,
 * because a device set to en-US would otherwise render `Rp 5,061,400`. The separator is
 * a property of the currency, not of the user's phone.
 *
 * A negative amount carries its sign ahead of the prefix — `-Rp 5.061.400` — and the
 * digits come from [Long.toString], so [Long.MIN_VALUE] formats rather than overflowing
 * the way negation would.
 *
 * @param withPrefix false drops the `Rp`, for dense rows where the currency is already
 *   unambiguous.
 */
fun formatIdr(money: Money, withPrefix: Boolean = true): String {
    val digits = money.rupiah.toString()
    val negative = digits.startsWith('-')
    val magnitude = if (negative) digits.substring(1) else digits

    return buildString {
        if (negative) append('-')
        if (withPrefix) {
            append(RUPIAH_PREFIX)
            append(' ')
        }
        appendGrouped(magnitude)
    }
}

/** `+Rp 250.000` / `-Rp 250.000`, for figures whose sign is the point being made. */
fun formatIdrSigned(money: Money, withPrefix: Boolean = true): String =
    if (money.isPositive) "+" + formatIdr(money, withPrefix) else formatIdr(money, withPrefix)

private fun StringBuilder.appendGrouped(magnitude: String) {
    val leading = magnitude.length % GROUP_SIZE
    if (leading != 0) append(magnitude, 0, leading)
    var index = leading
    while (index < magnitude.length) {
        if (index != 0) append(GROUP_SEPARATOR)
        append(magnitude, index, index + GROUP_SIZE)
        index += GROUP_SIZE
    }
}
