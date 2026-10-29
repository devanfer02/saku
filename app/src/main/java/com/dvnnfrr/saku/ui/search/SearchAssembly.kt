package com.dvnnfrr.saku.ui.search

import com.dvnnfrr.saku.domain.PeriodTotals
import com.dvnnfrr.saku.domain.Transaction
import com.dvnnfrr.saku.domain.TransactionFilter
import com.dvnnfrr.saku.domain.fold
import com.dvnnfrr.saku.ui.transactions.DaySection
import com.dvnnfrr.saku.ui.transactions.DisplayIndex
import com.dvnnfrr.saku.ui.transactions.periodTotals
import com.dvnnfrr.saku.ui.transactions.toDaySections
import kotlinx.datetime.TimeZone

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
)

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
    )
}
