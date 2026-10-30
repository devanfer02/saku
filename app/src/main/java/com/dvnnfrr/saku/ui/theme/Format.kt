package com.dvnnfrr.saku.ui.theme

import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import kotlin.math.abs

private const val RUPIAH_PREFIX = "Rp"
private const val GROUP_SEPARATOR = '.'
private const val GROUP_SIZE = 3

/**
 * The typographic minus, not the hyphen. It is the width of the plus sign, so a column
 * of signed amounts keeps its digits aligned under tabular figures.
 */
const val MINUS_SIGN = '−'

private const val PLUS_SIGN = '+'

/** Which sign an amount carries. One rule per kind of figure, picked by the caller. */
enum class MoneySign {
    /** A balance or a labelled total: a sign only when negative. */
    BALANCE,

    /** A net that can go either way: `+` above zero, minus below, nothing at zero. */
    NET,

    /**
     * A single flow: income reads `+`, expense reads minus, a transfer reads neither. An
     * adjustment keeps its stored sign, which is the direction of the correction.
     */
    FLOW,
}

/**
 * Formats whole rupiah the Indonesian way: `Rp 5.061.400`, full stop as the thousands
 * separator, no decimal component.
 *
 * The grouping is done by hand on the digit string rather than through `NumberFormat`,
 * because a device set to en-US would otherwise render `Rp 5,061,400`. The separator is
 * a property of the currency, not of the user's phone.
 *
 * A negative amount carries the minus ahead of the prefix, `−Rp 5.061.400`, and the
 * digits come from [Long.toString], so [Long.MIN_VALUE] formats rather than overflowing
 * the way negation would.
 *
 * @param withPrefix false drops the `Rp`. Only for an amount embedded in a sentence or
 *   an expression that already names the currency; every figure on screen keeps it.
 */
fun formatIdr(money: Money, withPrefix: Boolean = true): String =
    formatMoney(money, MoneySign.BALANCE, type = null, withPrefix = withPrefix)

/** `+Rp 250.000` / `−Rp 250.000`, for a net whose sign is the point being made. */
fun formatIdrSigned(money: Money, withPrefix: Boolean = true): String =
    formatMoney(money, MoneySign.NET, type = null, withPrefix = withPrefix)

/**
 * The one formatter behind every amount. [type] matters only for [MoneySign.FLOW]; a
 * flow amount is stored as a magnitude and its direction comes from the type.
 */
fun formatMoney(
    money: Money,
    sign: MoneySign,
    type: TxnType? = null,
    withPrefix: Boolean = true,
): String {
    val digits = money.rupiah.toString()
    val negative = digits.startsWith('-')
    val magnitude = if (negative) digits.substring(1) else digits
    val signChar = signFor(sign, type, negative, money.isZero)

    return buildString {
        signChar?.let(::append)
        if (withPrefix) {
            append(RUPIAH_PREFIX)
            append(' ')
        }
        appendGrouped(magnitude)
    }
}

private fun signFor(sign: MoneySign, type: TxnType?, negative: Boolean, zero: Boolean): Char? {
    if (zero) return null
    return when (sign) {
        MoneySign.BALANCE -> MINUS_SIGN.takeIf { negative }
        MoneySign.NET -> if (negative) MINUS_SIGN else PLUS_SIGN
        MoneySign.FLOW -> when (type) {
            TxnType.INCOME -> PLUS_SIGN
            TxnType.EXPENSE -> MINUS_SIGN
            TxnType.TRANSFER -> null
            TxnType.ADJUSTMENT, null -> MINUS_SIGN.takeIf { negative }
        }
    }
}

private const val RUPIAH_PER_TENTH_JUTA = 100_000L
private const val COMPACT_SUFFIX = " jt"
private const val DECIMAL_COMMA = ','

/**
 * A net in millions for chart labels: `+2,5 jt`, `−1,4 jt`. One decimal with a decimal
 * comma, as Indonesian amounts are written, rounded half away from zero on the magnitude.
 *
 * Integer-only: the rupiah are divided into tenths of a juta with the remainder carried
 * by hand. A figure that rounds to nothing reads `0 jt` with no sign, because `+0,0 jt`
 * claims a direction the label cannot show.
 *
 * [sign] is [MoneySign.NET] for a net, and [MoneySign.BALANCE] for a one-sided total
 * such as a category's month (`4,8 jt`), which carries a minus only when negative.
 * [MoneySign.FLOW] has no type to read here and behaves as [MoneySign.NET].
 */
fun formatIdrCompact(money: Money, sign: MoneySign = MoneySign.NET): String {
    val negative = money.isNegative
    // Long.MIN_VALUE has no positive twin; the clamp costs one rupiah on an amount no
    // ledger will hold.
    val magnitude = if (negative) -(money.rupiah.coerceAtLeast(-Long.MAX_VALUE)) else money.rupiah
    val tenths = magnitude / RUPIAH_PER_TENTH_JUTA +
        if (magnitude % RUPIAH_PER_TENTH_JUTA >= RUPIAH_PER_TENTH_JUTA / 2) 1 else 0
    if (tenths == 0L) return "0$COMPACT_SUFFIX"

    return buildString {
        if (negative) append(MINUS_SIGN) else if (sign != MoneySign.BALANCE) append(PLUS_SIGN)
        appendGrouped((tenths / 10).toString())
        append(DECIMAL_COMMA)
        append(tenths % 10)
        append(COMPACT_SUFFIX)
    }
}

/**
 * A chart axis tick: `0`, `250 rb`, `2,5 jt`, `10 jt`. Ticks are round steps from
 * `stripAxis`, so a juta shows a decimal only when it has one, and anything under a juta
 * reads in thousands (`rb`, ribu) rather than as `0,3 jt`. Unsigned: an axis starts at 0.
 */
fun formatIdrAxis(tick: Money): String {
    val rupiah = abs(tick.rupiah)
    if (rupiah == 0L) return "0"
    if (rupiah < RUPIAH_PER_JUTA) return "${roundedDiv(rupiah, 1_000)}$AXIS_THOUSANDS_SUFFIX"
    val tenths = roundedDiv(rupiah, RUPIAH_PER_TENTH_JUTA)
    return buildString {
        appendGrouped((tenths / 10).toString())
        if (tenths % 10 != 0L) append(DECIMAL_COMMA).append(tenths % 10)
        append(COMPACT_SUFFIX)
    }
}

private const val RUPIAH_PER_JUTA = 1_000_000L
private const val AXIS_THOUSANDS_SUFFIX = " rb"

/**
 * A day's net in a calendar cell, about a seventh of the screen wide, where
 * `Rp 5.061.400` cannot fit: `45k`, `−5m`. Rounded to the nearest thousand or million,
 * which is enough for a glance; the exact figure is one tap away in Daily.
 */
fun formatIdrCell(net: Money): String {
    val rupiah = net.rupiah
    if (rupiah == 0L) return "0"
    val sign = if (rupiah < 0) MINUS_SIGN.toString() else ""
    // Rounded, not truncated. 1.999.000 truncates to "1m", which reads as half the
    // value on a cell whose whole job is a glance.
    val thousands = roundedDiv(rupiah, 1_000)
    if (thousands == 0L) return "$sign<1k"
    val millions = roundedDiv(thousands, 1_000)
    val magnitude = if (millions != 0L) "${abs(millions)}m" else "${abs(thousands)}k"
    return sign + magnitude
}

private fun roundedDiv(value: Long, by: Long): Long {
    val half = by / 2
    return if (value < 0) (value - half) / by else (value + half) / by
}

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
