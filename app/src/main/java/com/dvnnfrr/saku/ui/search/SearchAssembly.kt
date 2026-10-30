package com.dvnnfrr.saku.ui.search

import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.PeriodTotals
import com.dvnnfrr.saku.domain.Transaction
import com.dvnnfrr.saku.domain.TransactionFilter
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.domain.barPermille
import com.dvnnfrr.saku.domain.fold
import com.dvnnfrr.saku.ui.transactions.DaySection
import com.dvnnfrr.saku.ui.transactions.DisplayIndex
import com.dvnnfrr.saku.ui.transactions.periodTotals
import com.dvnnfrr.saku.ui.transactions.scrollIndexFor
import com.dvnnfrr.saku.ui.transactions.toDaySections
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/*
 * Pure assembly for search. No Compose, no Android, no coroutines.
 *
 * The filter is applied in SQL before rows reach here (`observeAll(filter)`); this file
 * matches text, which SQLite cannot do well: `LIKE` folds ASCII case only and no accents.
 * Results reuse the Daily grouping and `periodTotals`, so search totals follow the same
 * rules as every other list: transfers and adjustments listed, never counted.
 */

/** One word of the query. Every word must match for a transaction to match. */
sealed interface QueryWord {
    /** Matches when the folded note contains [folded]. */
    data class Text(val folded: String) : QueryWord

    /**
     * An amount-like word, "47.500" or "Rp25.000": matches when the amount's plain digits
     * contain [digits], or when the note contains the word itself or its digits.
     */
    data class Digits(val digits: String, val folded: String) : QueryWord
}

private val SEPARATORS = Regex("[.,]")
private const val RUPIAH = "rp"

/**
 * Splits [query] on whitespace into words. A word that is only digits once "." and ","
 * separators and a leading "Rp" are stripped is an amount word; anything else is text.
 * A bare "Rp" on its own is dropped, so "Rp 25.000", typed the way the app prints it,
 * means the same as "25.000".
 */
fun parseQuery(query: String): List<QueryWord> =
    fold(query).split(' ').filter { it.isNotEmpty() && it != RUPIAH }.map { word ->
        val digits = word.removePrefix(RUPIAH).replace(SEPARATORS, "")
        if (digits.isNotEmpty() && digits.all { it.isDigit() }) {
            QueryWord.Digits(digits, word)
        } else {
            QueryWord.Text(word)
        }
    }

/** A transaction with its note folded once, so typing does not refold every note. */
data class Searchable(
    val transaction: Transaction,
    val note: String,
    val amountDigits: String,
)

fun List<Transaction>.searchable(): List<Searchable> = map {
    // Magnitude: a downward adjustment is stored signed, but nobody types the minus.
    Searchable(it, fold(it.note.orEmpty()), it.amount.absolute.rupiah.toString())
}

fun Searchable.matches(words: List<QueryWord>): Boolean = words.all { word ->
    when (word) {
        is QueryWord.Text -> word.folded in note
        is QueryWord.Digits ->
            word.digits in amountDigits || word.folded in note || word.digits in note
    }
}

data class SearchUiState(
    val filter: TransactionFilter = TransactionFilter.ALL,
    /** False while the query is blank, which shows no results rather than all of them. */
    val searched: Boolean = false,
    val days: List<DaySection> = emptyList(),
    val count: Int = 0,
    val totals: PeriodTotals = PeriodTotals.EMPTY,
    /** Matches the active filter hides; zero while no filter is active. */
    val hiddenCount: Int = 0,
    /** The matches month by month, newest first. Empty unless they span two months or more. */
    val months: List<SearchMonth> = emptyList(),
)

/**
 * One month of the matches. [count] includes transfers and adjustments; [income] and
 * [expense] do not, the same rule as [SearchUiState.totals].
 */
data class SearchMonth(
    val range: DateRange,
    val income: Money,
    val expense: Money,
    val count: Int,
    /** Which side the row's value and bar show, the same for every month of one search. */
    val side: TxnType,
    /** Thousandths of the largest month on [side], from `barPermille`. */
    val barPermille: Int,
) {
    val value: Money get() = if (side == TxnType.INCOME) income else expense

    /** The side the row does not show, for its supporting line; zero when there is none. */
    val otherSide: Money get() = if (side == TxnType.INCOME) expense else income
}

/**
 * Every month from the newest match back to the oldest, newest first, months with no
 * match included so a gap reads as a gap. Empty when the matches fall in fewer than two
 * months, where the breakdown would only repeat the summary.
 *
 * The side is money out unless the matches brought in more than they spent, so a search
 * for a salary does not draw a column of empty bars.
 */
fun List<Transaction>.toSearchMonths(zone: TimeZone): List<SearchMonth> {
    val byMonth = groupBy { firstOfMonth(it.occurredAt.toLocalDateTime(zone).date) }
    if (byMonth.size < 2) return emptyList()

    val totals = periodTotals()
    val side = if (totals.income > totals.expense) TxnType.INCOME else TxnType.EXPENSE
    val newest = byMonth.keys.max()
    val oldest = byMonth.keys.min()

    val months = generateSequence(newest) { it.minus(1, DateTimeUnit.MONTH) }
        .takeWhile { it >= oldest }
        .map { first ->
            val txns = byMonth[first].orEmpty()
            val monthTotals = txns.periodTotals()
            SearchMonth(
                range = DateRange.ofMonth(first),
                income = monthTotals.income,
                expense = monthTotals.expense,
                count = txns.size,
                side = side,
                barPermille = 0,
            )
        }
        .toList()

    val largest = months.maxOf { it.value }
    return months.map { it.copy(barPermille = barPermille(it.value, largest)) }
}

private fun firstOfMonth(date: LocalDate): LocalDate = LocalDate(date.year, date.month, 1)

/**
 * The list position of [month]'s newest day when the screen lays out [leadingItems]
 * items, then one item per month in [months], then the day sections. Null when the
 * month holds no match, which the screen does not make tappable.
 */
fun searchScrollIndex(
    leadingItems: Int,
    months: List<SearchMonth>,
    days: List<DaySection>,
    month: SearchMonth,
): Int? {
    if (month.count == 0) return null
    val inDays = scrollIndexFor(days, month.range.endInclusive) ?: return null
    return leadingItems + months.size + inDays
}

/**
 * The results for [query]. [filtered] passed the active filter in SQL; [unfiltered] is
 * every transaction and is non-null only while a filter is active, to count what it hides.
 */
fun assembleSearch(
    query: String,
    filter: TransactionFilter,
    filtered: List<Searchable>,
    unfiltered: List<Searchable>?,
    zone: TimeZone,
    index: DisplayIndex,
): SearchUiState {
    val words = parseQuery(query)
    if (words.isEmpty()) return SearchUiState(filter = filter)

    val matches = filtered.filter { it.matches(words) }.map { it.transaction }
    val allMatches = unfiltered?.count { it.matches(words) } ?: matches.size
    return SearchUiState(
        filter = filter,
        searched = true,
        days = matches.toDaySections(zone, index),
        count = matches.size,
        totals = matches.periodTotals(),
        hiddenCount = (allMatches - matches.size).coerceAtLeast(0),
        months = matches.toSearchMonths(zone),
    )
}
