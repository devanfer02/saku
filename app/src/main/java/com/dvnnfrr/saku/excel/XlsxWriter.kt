package com.dvnnfrr.saku.excel

import org.dhatim.fastexcel.Workbook
import java.io.OutputStream

/**
 * Writes a [SheetTable] out as `.xlsx` using the writer half of fastexcel.
 *
 * Unlike [XlsxReader] this **is** Android-safe. `org.dhatim:fastexcel` references no
 * `javax.xml.stream` type at all — it emits the sheet XML by hand and zips it with
 * `com.github.rzymek:opczip`. Its only transitive dependency is opczip, which is likewise
 * clean. Verified by scanning every class in both jars for StAX references, and re-verified
 * under an Android-like classloader in `XlsxAndroidRuntimeTest`.
 */
object XlsxWriter {
    private const val APPLICATION = "Saku"
    private const val VERSION = "1.0"

    /**
     * The writer is given an [OutputStream] rather than a path because on Android the
     * destination is a `content://` URI handed back by the system file picker, not a [java.io.File].
     */
    fun write(table: SheetTable, output: OutputStream) {
        write(listOf(table), output)
    }

    /**
     * Several sheets into one workbook, in the order given. A backup relies on that order:
     * the transaction sheet goes first so the plain importer, which reads only the first
     * sheet, still recognises the file.
     */
    fun write(tables: List<SheetTable>, output: OutputStream) {
        require(tables.isNotEmpty()) { "A workbook needs at least one sheet" }
        Workbook(output, APPLICATION, VERSION).use { workbook ->
            tables.forEach { table -> writeSheet(workbook, table) }
        }
    }

    private fun writeSheet(workbook: Workbook, table: SheetTable) {
        val sheet = workbook.newWorksheet(table.sheetName)

        table.headers.forEachIndexed { column, header -> sheet.value(0, column, header) }

        table.rows.forEachIndexed { index, row ->
            val rowNumber = index + 1
            row.cells.forEachIndexed { column, cell ->
                when (cell) {
                    is CellValue.Empty -> Unit
                    is CellValue.Text -> sheet.value(rowNumber, column, cell.value)
                    is CellValue.Number -> sheet.value(rowNumber, column, cell.value)
                    is CellValue.Flag -> sheet.value(rowNumber, column, cell.value)
                    is CellValue.Timestamp -> {
                        sheet.value(rowNumber, column, cell.value)
                        sheet.style(rowNumber, column)
                            .format("dd/MM/yyyy HH:mm:ss")
                            .set()
                    }
                }
            }
        }
        sheet.finish()
    }
}
