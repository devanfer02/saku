package com.dvnnfrr.saku.ui.search

import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.Transaction
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.receipt.itemKey
import com.dvnnfrr.saku.receipt.itemQuantity
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/*
 * Pure assembly for the price card in search. No Compose, no Android.
 *
 * Rows of one item share an [itemKey]; a row's unit price is its amount over the
 * quantity in its note. One point per day, so two trips on one day do not draw a
 * vertical jump.
 */

/** One day's unit price of the item. */
data class PricePoint(val date: LocalDate, val unitPrice: Money)

/**
 * The price card: [points] oldest first, at least two of them. [change] is latest minus
 * first; [changePercent] is that over the first, rounded half away from zero, or null
 * when the first price is zero.
 */
data class PriceHistory(
    val itemKey: String,
    val points: List<PricePoint>,
    val lowest: PricePoint,
    val highest: PricePoint,
    val change: Money,
    val changePercent: Int?,
) {
    val first: PricePoint get() = points.first()
    val latest: PricePoint get() = points.last()
}

/** Amount over the note's quantity, rounded half up to whole rupiah. */
fun unitPrice(amount: Money, note: String?): Money {
    val n = itemQuantity(note.orEmpty()).toLong()
    val rupiah = amount.absolute.rupiah
    return Money((2 * rupiah + n) / (2 * n))
}

/**
 * The price card for [matches], or null unless every expense among them has the same
 * non-blank item key and there are at least two of them. Income, transfers and
 * adjustments are ignored: a refund is not a price.
 */
fun List<Transaction>.priceHistory(zone: TimeZone): PriceHistory? {
    val expenses = filter { it.type == TxnType.EXPENSE }
    if (expenses.size < MIN_ROWS) return null
    val keys = expenses.map { itemKey(it.note.orEmpty()) }.toSet()
    val key = keys.singleOrNull()
    if (key.isNullOrEmpty()) return null

    val points = expenses
        .groupBy { it.occurredAt.toLocalDateTime(zone).date }
        .map { (date, rows) -> PricePoint(date, averageOf(rows.map { unitPrice(it.amount, it.note) })) }
        .sortedBy { it.date }

    val first = points.first()
    val latest = points.last()
    val change = latest.unitPrice - first.unitPrice
    return PriceHistory(
        itemKey = key,
        points = points,
        // The most recent day at the extreme: "lowest in October" says when it was last that cheap.
        lowest = points.reversed().minBy { it.unitPrice },
        highest = points.reversed().maxBy { it.unitPrice },
        change = change,
        changePercent = percentOf(change, first.unitPrice),
    )
}

private const val MIN_ROWS = 2

/** Mean of [prices], rounded half up; every price here is zero or more. */
private fun averageOf(prices: List<Money>): Money {
    val n = prices.size.toLong()
    val sum = prices.sumOf { it.rupiah }
    return Money((2 * sum + n) / (2 * n))
}

/**
 * [change] as a whole percent of [base], rounded half away from zero. A plain division,
 * not `wholePercents`: that one splits a whole into shares summing to 100.
 */
internal fun percentOf(change: Money, base: Money): Int? {
    if (base.rupiah <= 0L) return null
    val magnitude = (200 * kotlin.math.abs(change.rupiah) + base.rupiah) / (2 * base.rupiah)
    return (if (change.isNegative) -magnitude else magnitude).toInt()
}
