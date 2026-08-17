package com.dvnnfrr.saku.excel

import com.dvnnfrr.saku.domain.Transaction
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import java.math.BigDecimal

/**
 * Builds the sheet Saku exports.
 *
 * The shape is chosen so that reading it back reconstructs the database rather than
 * approximating it: both sides of a transfer are named, the subcategory has its own
 * column, and the amount is a whole-rupiah integer with no currency decoration to
 * re-parse. [ImportParser] accepts exactly what this writes — the round-trip test is
 * what keeps the two honest.
 */
object ExportSheet {

    const val SHEET_NAME = "Saku"

    /**
     * Names, not ids. An export is read by a database that has never seen this one's
     * primary keys, so an id in the file would resolve to a different record or to none.
     */
    fun build(
        transactions: List<Transaction>,
        accountNames: Map<Long, String>,
        categoryNames: Map<Long, String>,
        subcategoryNames: Map<Long, String>,
        zone: TimeZone,
    ): SheetTable = SheetTable(
        sheetName = SHEET_NAME,
        headers = SheetLayout.SAKU.headers,
        rows = transactions.sortedBy { it.occurredAt }.mapIndexed { index, transaction ->
            SheetRow(
                // 0 is the header, so data starts at 1 — the same numbering the reader
                // reports back, which is what lets a rejected row be pointed at.
                number = index + 1,
                cells = listOf(
                    CellValue.Timestamp(
                        transaction.occurredAt.toLocalDateTime(zone).toJavaLocalDateTime(),
                    ),
                    CellValue.Text(transaction.type.token),
                    CellValue.Number(BigDecimal.valueOf(transaction.amount.rupiah)),
                    text(accountNames[transaction.accountId]),
                    text(transaction.toAccountId?.let(accountNames::get)),
                    text(transaction.categoryId?.let(categoryNames::get)),
                    text(transaction.subcategoryId?.let(subcategoryNames::get)),
                    text(transaction.note),
                ),
            )
        },
    )

    private fun text(value: String?): CellValue =
        value?.takeIf { it.isNotBlank() }?.let { CellValue.Text(it) } ?: CellValue.Empty
}

/**
 * The word written in the Type column. A file-format token, not UI copy — it has to
 * stay stable across releases and across languages, because an export written today is
 * read back by a build that ships next year.
 */
internal val TxnType.token: String
    get() = when (this) {
        TxnType.EXPENSE -> "Expense"
        TxnType.INCOME -> "Income"
        TxnType.TRANSFER -> "Transfer"
        TxnType.ADJUSTMENT -> "Adjustment"
    }
