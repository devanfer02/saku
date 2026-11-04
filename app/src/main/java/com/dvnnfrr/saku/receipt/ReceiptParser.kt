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

        val source = pickSource(rows)
        val order = if (source?.orderApp == true) pickAppOrder(rows) else null
        val total = withOrderTotal(pickTotal(rows), order)
        val date = pickDate(rows, today)
        val store = if (order == null) pickStore(rows) else pickStore(rows, appScreen = true) ?: order.shop
        // An order app's screen is read by its own layout; the paper-receipt reader would
        // take its payment and fee rows for items.
        val items = order?.items ?: pickItems(rows, total.rowIndex)

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
            source = source,
        )
    }

    /** The order's amount paid, when it found one; the generic pick becomes a chip. */
    private fun withOrderTotal(generic: TotalPick, order: AppOrderPick?): TotalPick {
        val paid = order?.total ?: return generic
        val others = (listOfNotNull(generic.total) + generic.others).distinct().filterNot { it == paid }
        return TotalPick(paid, order.totalRow, others.take(CHIPS))
    }
}
