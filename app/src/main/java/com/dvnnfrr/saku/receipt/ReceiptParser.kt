package com.dvnnfrr.saku.receipt

import kotlinx.datetime.LocalDate

object ReceiptParser {

    private const val MAX_UNPLACED = 40

    fun read(lines: List<OcrLine>, today: LocalDate): ReceiptReading = readParts(listOf(lines), today)

    /** Reads one receipt from several photos, in the order taken, top to bottom. */
    fun readParts(parts: List<List<OcrLine>>, today: LocalDate): ReceiptReading {
        val joined = joinPhotos(parts.map(::receiptRows))
        val rows = joined.rows
        if (rows.isEmpty()) return ReceiptReading.EMPTY.copy(gapsAfterPhoto = joined.gapsAfterPhoto)

        val total = pickTotal(rows)
        val date = pickDate(rows, today)
        val store = pickStore(rows)
        val items = pickItems(rows, total.rowIndex)

        // Item rows are shown as items, so they are not offered again as unplaced lines.
        val used = setOfNotNull(total.rowIndex, date?.rowIndex, store?.rowIndex) + items.rowIndices
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
            items = items.items,
            receiptDiscounts = items.receiptDiscounts,
            additions = items.additions,
            gapsAfterPhoto = joined.gapsAfterPhoto,
        )
    }
}
