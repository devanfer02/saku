package com.dvnnfrr.saku.receipt

import kotlinx.datetime.LocalDate

/**
 * Reads a receipt photo or a payment screenshot from its recognized lines.
 *
 * One reading for both kinds, with no switch between them: paper and screenshot labels
 * sit in the same lists, so the user never says which one the image is.
 */
object ReceiptParser {

    /** At most this many unplaced rows; a long receipt's list would bury the sheet. */
    private const val MAX_UNPLACED = 40

    fun read(lines: List<OcrLine>, today: LocalDate): ReceiptReading {
        val rows = receiptRows(lines)
        if (rows.isEmpty()) return ReceiptReading.EMPTY

        val total = pickTotal(rows)
        val date = pickDate(rows, today)
        val store = pickStore(rows)

        val used = setOfNotNull(total.rowIndex, date?.rowIndex, store?.rowIndex)
        val unplaced = rows.withIndex()
            .filterNot { (index, row) -> index in used || isPaymentRow(row.text) }
            .map { it.value.text.trim() }
            .filter { it.length >= 2 }
            .take(MAX_UNPLACED)

        return ReceiptReading(
            total = total.total,
            otherAmounts = total.others,
            date = date?.date,
            store = store?.name,
            unplaced = unplaced,
        )
    }
}
