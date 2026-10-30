package com.dvnnfrr.saku.ui.entry

import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.receipt.ReceiptReading
import kotlinx.datetime.LocalDateTime

/**
 * What a scan left on the sheet besides the filled fields: the amounts to offer as
 * chips and the lines Saku could not place.
 *
 * [amounts] holds every candidate, the picked total first. The chips shown are the ones
 * that differ from the current amount, so tapping a chip swaps it with the one in use.
 */
data class ReceiptPanel(
    val amounts: List<Money>,
    val lines: List<String>,
    /** False when no amount was read at all, which the sheet says in words. */
    val totalFound: Boolean,
) {
    fun chipsFor(current: Money): List<Money> = amounts.filterNot { it == current }.take(MAX_CHIPS)

    fun withoutLine(line: String): ReceiptPanel = copy(lines = lines - line)

    private companion object {
        const val MAX_CHIPS = 3
    }
}

data class ReceiptFill(val form: EntryFormState, val panel: ReceiptPanel)

/**
 * Fills [form] from [reading].
 *
 * - The amount is set to the total only if the user has not typed one; a typed amount is
 *   theirs, and the total is then offered as the first chip instead.
 * - A date replaces the date and keeps the time of day, so the entry lands on the day of
 *   the purchase without inventing an hour. The parser already dropped implausible dates.
 * - The store becomes the note only if the note is empty.
 * - Type, account and category never change.
 */
fun applyReceipt(form: EntryFormState, reading: ReceiptReading): ReceiptFill {
    val total = reading.total
    val filled = form.copy(
        amount = if (form.amount.isEmpty && total != null) AmountInput.of(total) else form.amount,
        occurredAt = reading.date?.let { LocalDateTime(it, form.occurredAt.time) } ?: form.occurredAt,
        note = if (form.note.isBlank()) reading.store.orEmpty() else form.note,
    )
    val panel = ReceiptPanel(
        amounts = listOfNotNull(total).plus(reading.otherAmounts).distinct(),
        lines = reading.unplaced.distinct(),
        totalFound = total != null,
    )
    return ReceiptFill(filled, panel)
}

/** Appends a receipt line to the note, after a comma when the note already says something. */
fun appendToNote(note: String, line: String): String =
    if (note.isBlank()) line else "${note.trimEnd()}, $line"
