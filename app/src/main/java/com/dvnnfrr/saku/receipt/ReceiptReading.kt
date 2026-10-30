package com.dvnnfrr.saku.receipt

import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.sum
import kotlinx.datetime.LocalDate

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
 * What Saku read from one receipt, in one photo or several.
 *
 * [items] are the bought items, each with its own discount; [receiptDiscounts] are cuts
 * on the whole receipt (`DISKON KARTU`) and [additions] tax or service added on top, both
 * to be spread over the items. [gapsAfterPhoto] holds `n` when photos `n` and `n + 1`
 * share no row, so a part may be missing between them.
 */
data class ReceiptReading(
    val total: Money?,
    val otherAmounts: List<Money>,
    val date: LocalDate?,
    val store: String?,
    val unplaced: List<String>,
    val items: List<ReceiptItem> = emptyList(),
    val receiptDiscounts: List<Money> = emptyList(),
    val additions: List<Money> = emptyList(),
    val gapsAfterPhoto: List<Int> = emptyList(),
) {
    val isEmpty: Boolean get() = total == null && otherAmounts.isEmpty() && unplaced.isEmpty() && items.isEmpty()

    /** The items after their own discounts, less the receipt discounts, plus the additions. */
    val itemsNet: Money get() = items.map { it.net }.sum() - receiptDiscounts.sum() + additions.sum()

    companion object {
        val EMPTY = ReceiptReading(null, emptyList(), null, null, emptyList())
    }
}
