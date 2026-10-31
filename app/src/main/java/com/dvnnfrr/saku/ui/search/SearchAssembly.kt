package com.dvnnfrr.saku.ui.search

import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.PeriodTotals
import com.dvnnfrr.saku.domain.Transaction
import com.dvnnfrr.saku.domain.TransactionFilter
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.domain.barPermille
import com.dvnnfrr.saku.domain.fold
import com.dvnnfrr.saku.receipt.LearnedWords
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

/** The three ways to read the matches, the same tabs as Trans: day rows, months, years. */
enum class SearchTab { DAILY, MONTHLY, YEARLY }

data class SearchUiState(
    val filter: TransactionFilter = TransactionFilter.ALL,
    /** False while the query is blank, which shows no results rather than all of them. */
    val searched: Boolean = false,
    val days: List<DaySection> = emptyList(),
    val count: Int = 0,
    val totals: PeriodTotals = PeriodTotals.EMPTY,
    /** Matches the active filter hides; zero while no filter is active. */
    val hiddenCount: Int = 0,
    /** The matches month by month, newest first, gaps included. Empty when nothing matched. */
    val months: List<SearchPeriod> = emptyList(),
    /** The matches year by year, newest first, gaps included. Empty when nothing matched. */
    val years: List<SearchPeriod> = emptyList(),
    /** Items the matches hold at least twice, most rows first; chips when there are two or more. */
    val priceItems: List<PriceItem> = emptyList(),
    /** The price card of the picked item, or of the first of [priceItems]; see `priceHistory`. */
    val price: PriceHistory? = null,
) {
    /**
     * The tab shown until the user picks one: Monthly when the matches span two months
     * or more, where a trend is the useful first view, else Daily, where the rows are.
     */
    val defaultTab: SearchTab
        get() = if (months.count { it.count > 0 } >= 2) SearchTab.MONTHLY else SearchTab.DAILY
}

/**
 * One month or year of the matches. [count] includes transfers and adjustments; [income]
 * and [expense] do not, the same rule as [SearchUiState.totals].
 */
data class SearchPeriod(
    val range: DateRange,
    val income: Money,
    val expense: Money,
    val count: Int,
    /** Which side the row's value and bar show, the same for every row of one search. */
    val side: TxnType,
    /** Thousandths of the largest row on [side], from `barPermille`. */
    val barPermille: Int,
) {
    val value: Money get() = if (side == TxnType.INCOME) income else expense

    /** The side the row does not show, for its supporting line; zero when there is none. */
    val otherSide: Money get() = if (side == TxnType.INCOME) expense else income
}

/**
 * Every month from the newest match back to the oldest, newest first, months with no
 * match included so a gap reads as a gap.
 *
 * The side is money out unless the matches brought in more than they spent, so a search
 * for a salary does not draw a column of empty bars.
 */
fun List<Transaction>.toSearchMonths(zone: TimeZone): List<SearchPeriod> =
    toSearchPeriods(zone, step = DateTimeUnit.MONTH, startOf = ::firstOfMonth) { DateRange.ofMonth(it) }

/** Every year from the newest match back to the oldest, newest first, gaps included. */
fun List<Transaction>.toSearchYears(zone: TimeZone): List<SearchPeriod> =
    toSearchPeriods(zone, step = DateTimeUnit.YEAR, startOf = { LocalDate(it.year, 1, 1) }) { DateRange.ofYear(it.year) }

private fun List<Transaction>.toSearchPeriods(
    zone: TimeZone,
    step: DateTimeUnit.DateBased,
    startOf: (LocalDate) -> LocalDate,
    rangeOf: (LocalDate) -> DateRange,
): List<SearchPeriod> {
    if (isEmpty()) return emptyList()
    val byStart = groupBy { startOf(it.occurredAt.toLocalDateTime(zone).date) }

    val totals = periodTotals()
    val side = if (totals.income > totals.expense) TxnType.INCOME else TxnType.EXPENSE
    val newest = byStart.keys.max()
    val oldest = byStart.keys.min()

    val periods = generateSequence(newest) { it.minus(1, step) }
        .takeWhile { it >= oldest }
        .map { start ->
            val txns = byStart[start].orEmpty()
            val periodTotals = txns.periodTotals()
            SearchPeriod(
                range = rangeOf(start),
                income = periodTotals.income,
                expense = periodTotals.expense,
                count = txns.size,
                side = side,
                barPermille = 0,
            )
        }
        .toList()

    val largest = periods.maxOf { it.value }
    return periods.map { it.copy(barPermille = barPermille(it.value, largest)) }
}

private fun firstOfMonth(date: LocalDate): LocalDate = LocalDate(date.year, date.month, 1)

/**
 * The Daily list position of [period]'s newest day header. Null when the period holds
 * no match, which the screen does not make tappable.
 */
fun dailyScrollIndex(days: List<DaySection>, period: SearchPeriod): Int? {
    if (period.count == 0) return null
    return scrollIndexFor(days, period.range.endInclusive)
}

/** The Monthly list position of [year]'s newest month with a match, or null for an empty year. */
fun monthlyScrollIndex(months: List<SearchPeriod>, year: SearchPeriod): Int? {
    if (year.count == 0) return null
    return months.indexOfFirst { it.count > 0 && it.range.start.year == year.range.start.year }
        .takeIf { it >= 0 }
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
    learned: LearnedWords = LearnedWords.NONE,
    /** The item chip the user picked, by key; null for the most bought item. */
    pickedItem: String? = null,
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
        years = matches.toSearchYears(zone),
        priceItems = matches.priceItems(learned),
        price = matches.priceHistory(zone, learned, pickedItem),
    )
}
