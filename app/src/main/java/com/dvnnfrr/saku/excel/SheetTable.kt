package com.dvnnfrr.saku.excel

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * A single spreadsheet cell, carrying the type the file actually declared rather than a
 * stringified rendering of it. The Money Manager export mixes types within a row — column 6
 * (`IDR`) is a number, column 9 (`Amount`) is text holding the same figure — so the importer
 * has to be able to tell them apart.
 */
sealed interface CellValue {
    /** No cell was written, or the cell holds the empty string. */
    data object Empty : CellValue

    data class Text(val value: String) : CellValue

    data class Number(val value: BigDecimal) : CellValue

    /** A serial date/time. Excel has no timezone concept, hence [LocalDateTime]. */
    data class Timestamp(val value: LocalDateTime) : CellValue

    data class Flag(val value: Boolean) : CellValue
}

/** One row of a sheet. [number] is the workbook's own 1-based row number. */
data class SheetRow(val number: Int, val cells: List<CellValue>) {
    operator fun get(index: Int): CellValue = cells.getOrElse(index) { CellValue.Empty }

    fun textAt(index: Int): String? = (this[index] as? CellValue.Text)?.value

    fun numberAt(index: Int): BigDecimal? = (this[index] as? CellValue.Number)?.value

    fun timestampAt(index: Int): LocalDateTime? = (this[index] as? CellValue.Timestamp)?.value
}

/**
 * A whole sheet, split into its header row and its data rows.
 *
 * [headers] deliberately keeps duplicates. The Money Manager export names both column 2 and
 * column 11 `Account`, and layout detection has to see that rather than have it silently
 * deduplicated away.
 */
data class SheetTable(
    val sheetName: String,
    val headers: List<String>,
    val rows: List<SheetRow>,
) {
    val columnCount: Int get() = headers.size
}
