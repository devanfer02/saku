package com.dvnnfrr.saku.receipt

import com.dvnnfrr.saku.domain.Money
import kotlinx.datetime.LocalDate

/**
 * One line of text as the recognizer read it, with its box in image pixels.
 *
 * The parser's only input. `data/receipt/ReceiptRecognizer` adapts ML Kit's `Text` to
 * this, so nothing in this package knows which engine did the reading, and the tests
 * feed it saved recognizer output with no device in the room.
 */
data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val centreY: Int get() = (top + bottom) / 2
    val height: Int get() = bottom - top
}

/**
 * What a receipt or a payment screenshot yielded.
 *
 * Every field may be missing. The sheet fills what is here and the user checks it before
 * saving, so a wrong guess costs a tap rather than a wrong transaction.
 */
data class ReceiptReading(
    /** The picked total, or null when no amount was read at all. */
    val total: Money?,
    /** Up to three other amounts, best first, offered as chips in case [total] is wrong. */
    val otherAmounts: List<Money>,
    val date: LocalDate?,
    /** The store or merchant, as it should read in a note. */
    val store: String?,
    /** Rows that were read but not used, in printed order, for the user to note by hand. */
    val unplaced: List<String>,
) {
    val isEmpty: Boolean get() = total == null && otherAmounts.isEmpty() && unplaced.isEmpty()

    companion object {
        val EMPTY = ReceiptReading(null, emptyList(), null, null, emptyList())
    }
}
