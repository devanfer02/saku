package com.dvnnfrr.saku.ui.search

import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.Transaction
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.receipt.LearnedWords
import com.dvnnfrr.saku.receipt.itemKey
import com.dvnnfrr.saku.receipt.itemQuantity
import com.dvnnfrr.saku.receipt.itemSearchName
import com.dvnnfrr.saku.receipt.readableItemName
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/*
 * Pure assembly for the price card in search. No Compose, no Android.
 *
 * Rows of one item share a [priceKey]: the note in its readable form (short forms
 * expanded, the user's learned words applied), then compared ignoring case, spaces and
 * a trailing quantity (` ×2`, ` 2X`, ` 3 biji`). An exact match on that, nothing fuzzier: `INDOMIE GRG SPCL` and
 * "Indomie goreng special" are one item, "Indomie Goreng" and "Indomie Goreng Special"
 * are two. A row's unit price is its amount over the quantity in its note. One point per
 * day, so two trips on one day do not draw a vertical jump.
 */

/**
 * The key price history groups rows by: the readable name's [itemKey] with its spaces
 * dropped too, since hand-typed notes split words either way (`Es Teh`, `Esteh`). Blank
 * for a blank note, which is never an item.
 */
fun priceKey(note: String?, learned: LearnedWords = LearnedWords.NONE): String {
    val text = note.orEmpty()
    if (text.isBlank()) return ""
    return itemKey(readableItemName(text, learned)).replace(" ", "")
}

/**
 * An item the matches hold at least twice: its [key], a [name] to show (the readable
 * form of its newest note, without the quantity) and how many rows it has.
 */
data class PriceItem(val key: String, val name: String, val count: Int)

/**
 * The items among the expense [this] bought at least twice, most rows first, then the
 * most recently bought. The counter behind the item chips: a search for "indomie" that
 * finds two flavours offers both, the one bought more often first.
 */
fun List<Transaction>.priceItems(learned: LearnedWords = LearnedWords.NONE): List<PriceItem> =
    filter { it.type == TxnType.EXPENSE }
        .groupBy { priceKey(it.note, learned) }
        .filterKeys { it.isNotEmpty() }
        .filterValues { it.size >= MIN_ROWS }
        .map { (key, rows) ->
            val newest = rows.maxBy { it.occurredAt }
            val name = itemSearchName(readableItemName(newest.note.orEmpty(), learned))
            PriceItem(key, name, rows.size) to newest.occurredAt
        }
        .sortedWith(compareByDescending<Pair<PriceItem, Instant>> { it.first.count }.thenByDescending { it.second })
        .map { it.first }

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
 * The price card for the item [key] among [this], or for the first of [priceItems] when
 * [key] is null or no longer among them. Null when no item was bought at least twice.
 * Income, transfers and adjustments are ignored: a refund is not a price.
 */
fun List<Transaction>.priceHistory(
    zone: TimeZone,
    learned: LearnedWords = LearnedWords.NONE,
    key: String? = null,
): PriceHistory? {
    val items = priceItems(learned)
    val chosen = items.firstOrNull { it.key == key } ?: items.firstOrNull() ?: return null
    val expenses = filter { it.type == TxnType.EXPENSE && priceKey(it.note, learned) == chosen.key }

    val points = expenses
        .groupBy { it.occurredAt.toLocalDateTime(zone).date }
        .map { (date, rows) -> PricePoint(date, averageOf(rows.map { unitPrice(it.amount, it.note) })) }
        .sortedBy { it.date }

    val first = points.first()
    val latest = points.last()
    val change = latest.unitPrice - first.unitPrice
    return PriceHistory(
        itemKey = chosen.key,
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
