package com.dvnnfrr.saku.receipt

import com.dvnnfrr.saku.domain.Money
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

        // The items confirm a total misread by a look-alike digit (`206.108` for 206.100):
        // the till's rounder figure wins and the reading as printed stays a chip.
        val net = ReceiptReading.EMPTY.copy(items = items.items, receiptDiscounts = items.receiptDiscounts, additions = items.additions).itemsNet
        val read = total.total
        val confirmed = read?.takeIf { items.items.isNotEmpty() && it != net && lookAlike(it.rupiah, net.rupiah) }
            ?.let { Money(rounder(net.rupiah, it.rupiah)) }
        val others = if (confirmed != null && confirmed != read) listOfNotNull(read) + total.others else total.others

        return ReceiptReading(
            total = confirmed ?: read,
            otherAmounts = others,
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
