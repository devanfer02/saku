package com.dvnnfrr.saku.receipt

import kotlinx.datetime.LocalDate

object ReceiptParser {

    private const val MAX_UNPLACED = 40

    fun read(lines: List<OcrLine>, today: LocalDate): ReceiptReading {
        val rows = receiptRows(lines)
        if (rows.isEmpty()) return ReceiptReading.EMPTY

        val total = pickTotal(rows)
        val date = pickDate(rows, today)
        val store = pickStore(rows)
        val items = pickItems(rows, total.rowIndex)

        // Item rows stay in unplaced while the entry sheet offers them as lines; task 5.1
        // removes that sheet action and adds items.rowIndices here.
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
            items = items.items,
            receiptDiscounts = items.receiptDiscounts,
            additions = items.additions,
        )
    }
}
