package com.dvnnfrr.saku.receipt

import com.dvnnfrr.saku.domain.Money

/** The picked total, the row it came from, and the runners-up for the chips. */
data class TotalPick(val total: Money?, val rowIndex: Int?, val others: List<Money>)

/** `TOTAL` as the recognizer misreads it: `T0TAL`, `TUTAI`, `TOTA1`, `TOTAl`. */
private const val T = """t[o0u][t7][a4][l1i|]"""

private fun pattern(source: String) = Regex(source, RegexOption.IGNORE_CASE)

/*
 * Labels, in three strengths. A grand-total style label beats a plain TOTAL, which beats
 * NOMINAL: a banking screen shows the transfer as Nominal and what left the account,
 * admin fee included, as Total.
 */
private val GRAND = pattern("""grand\s*$T|$T\s*(bayar|belanja|tagihan|pembayaran|transaksi)|jumlah\s*pembayaran""")
private val PLAIN = pattern("""$T|tagihan|amount\s*due""")
private val WEAK = pattern("""nominal|jumlah""")

/** Rows that carry a total word but are not the total. */
private val NOT_TOTAL = pattern(
    """sub\s*-?\s*$T|$T\s*(disc|item|i\|l|hemat|diskon|potongan|savings?|qty)|biaya\s*admin|saldo""",
)

/** How it was paid, and the change: never the total, though often the largest figure. */
private val PAYMENT = pattern("""tunai|cash|kembali|change|bayar\b|debit|kartu|card|edc|qris|ovo|gopay|dana\b|shopee""")
private val CHANGE = pattern("""kembali|change""")
private val BARE_AMOUNT = pattern("""\s*(rp\.?\s*)?[\d.,]+\s*""")

private const val CHIPS = 3

internal fun isPaymentRow(text: String): Boolean = PAYMENT.containsMatchIn(text) && !GRAND.containsMatchIn(text)

/**
 * Picks the total from [rows].
 *
 * 1. A row with a total label, strongest label first, then the lowest such row. Its
 *    rightmost amount; if it has none, the next row's (KFC prints `Total`, `Cash`,
 *    `322,000` on three rows), skipping a payment row unless it is a bare amount.
 * 2. Otherwise the largest amount printed at least twice: the total repeats as the card,
 *    QRIS or e-wallet payment.
 * 3. Otherwise the largest amount outside the payment rows.
 */
fun pickTotal(rows: List<ReceiptRow>): TotalPick {
    data class Candidate(val strength: Int, val index: Int, val amount: Money)

    val labelled = rows.mapIndexedNotNull { index, row ->
        val text = row.text
        val strength = when {
            NOT_TOTAL.containsMatchIn(text) -> return@mapIndexedNotNull null
            GRAND.containsMatchIn(text) -> 3
            isPaymentRow(text) -> return@mapIndexedNotNull null
            PLAIN.containsMatchIn(text) -> 2
            WEAK.containsMatchIn(text) -> 1
            else -> return@mapIndexedNotNull null
        }
        amountForLabel(rows, index)?.let { Candidate(strength, index, it) }
    }

    val all = rows.flatMap { amountsIn(it.text) }
    val counts = all.groupingBy { it }.eachCount()
    val repeated = counts.filterValues { it >= 2 }.keys.sortedDescending()
    val outsidePayments = rows.filterNot { isPaymentRow(it.text) }
        .flatMap { amountsIn(it.text) }
        .distinct()
        .sortedDescending()

    val byStrength = compareBy<Candidate>({ it.strength }, { it.index })
    val best = labelled.maxWithOrNull(byStrength)
    val pick = best?.amount ?: repeated.firstOrNull() ?: outsidePayments.firstOrNull()
    val pickRow = best?.index ?: pick?.let { value -> rows.indexOfFirst { value in amountsIn(it.text) } }

    // The runners-up: other labelled amounts, then repeats, then the rest by size. Change
    // is never offered; it is never what was spent.
    val change = rows.filter { CHANGE.containsMatchIn(it.text) }.flatMap { amountsIn(it.text) }.toSet()
    val others = (labelled.sortedWith(byStrength.reversed()).map { it.amount } +
        repeated + outsidePayments)
        .distinct()
        .filterNot { it == pick || it in change }
        .take(CHIPS)

    return TotalPick(pick, pickRow?.takeIf { it >= 0 }, others)
}

private fun amountForLabel(rows: List<ReceiptRow>, index: Int): Money? {
    amountsIn(rows[index].text).lastOrNull()?.let { return it }
    for (next in rows.drop(index + 1).take(2)) {
        val text = next.text
        if (BARE_AMOUNT.matches(text)) amountsIn(text).lastOrNull()?.let { return it }
        else if (!isPaymentRow(text)) return amountsIn(text).lastOrNull()
    }
    return null
}
