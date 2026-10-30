package com.dvnnfrr.saku.excel

import org.dhatim.fastexcel.reader.Cell
import org.dhatim.fastexcel.reader.CellType
import org.dhatim.fastexcel.reader.ReadableWorkbook
import org.dhatim.fastexcel.reader.ReadingOptions
import org.dhatim.fastexcel.reader.Row
import java.io.File
import java.io.InputStream
import kotlin.streams.toList

/**
 * Reads an `.xlsx` into a [SheetTable] using `fastexcel-reader`.
 *
 * ## Why the build carries a StAX dependency
 *
 * `fastexcel-reader` parses through `javax.xml.stream` (StAX), implemented by `aalto-xml` and
 * `stax2-api`. The StAX **API** packages — `javax.xml.stream`, `javax.xml.stream.events` and
 * `javax.xml.stream.util` — have never been part of Android. `android.jar` for API 35 declares
 * zero classes in all three, while supplying every other `javax.xml.*` package the chain
 * touches. Neither fastexcel nor aalto nor stax2-api bundles the API, so on a stock Android
 * runtime the classes are simply missing.
 *
 * The failure is immediate rather than an edge case: `DefaultXMLInputFactory` holds its
 * `XMLInputFactory` in a `static final` field, so the first touch of [ReadableWorkbook]
 * triggers class initialisation and throws
 * `NoClassDefFoundError: javax/xml/stream/XMLStreamException`.
 *
 * The fix is `org.apache.geronimo.specs:geronimo-stax-api_1.2_spec`, a 37 KB API-only jar
 * declared in `app/build.gradle.kts`. It supplies the missing types and nothing else; aalto
 * remains the implementation. fastexcel constructs `aalto.stax.InputFactoryImpl` directly
 * rather than going through `XMLInputFactory.newInstance()`, so no `ServiceLoader` or
 * factory-finder lookup has to work on Android for this to hold together.
 *
 * **Removing that dependency silently breaks import on device.** Nothing in the build catches
 * it: `assembleDebug` still succeeds, D8 dexes the dangling references without complaint while
 * minification is off, and JVM unit tests still pass because the desktop JDK has its own StAX.
 * `XlsxAndroidRuntimeTest` is the guard — it reproduces Android's classpath and fails if the
 * shim goes missing.
 *
 * [XlsxWriter] needs none of this; the writer half of fastexcel touches no StAX at all.
 */
object XlsxReader {
    /** Ask fastexcel for number formats so date-formatted numerics can be told from plain ones. */
    private val OPTIONS = ReadingOptions(true, false)

    fun read(file: File): SheetTable = ReadableWorkbook(file, OPTIONS).use { toTable(it) }

    fun read(input: InputStream): SheetTable = ReadableWorkbook(input, OPTIONS).use { toTable(it) }

    /**
     * Every sheet, in workbook order, each carrying its own name. A backup is found by its
     * sheet names; [read] stays first-sheet-only because that is what makes a backup open
     * through the plain importer exactly like an export.
     *
     * A sheet with no rows at all comes back with no headers rather than failing the whole
     * workbook: whoever asked for it by name decides whether that is an error.
     */
    fun readAll(input: InputStream): List<SheetTable> = ReadableWorkbook(input, OPTIONS).use { workbook ->
        // Not Stream.toList(): that is API 34, and Saku runs from API 26.
        workbook.sheets.iterator().asSequence().toList().map { sheet ->
            val rows = sheet.read()
            if (rows.isEmpty()) SheetTable(sheet.name, emptyList(), emptyList()) else toTable(sheet.name, rows)
        }
    }

    private fun toTable(workbook: ReadableWorkbook): SheetTable {
        val sheet = workbook.firstSheet
        val rows = sheet.read()
        require(rows.isNotEmpty()) { "Sheet '${sheet.name}' has no rows, so it has no header." }
        return toTable(sheet.name, rows)
    }

    private fun toTable(name: String, rows: List<Row>): SheetTable =
        SheetTable(
            sheetName = name,
            headers = rows.first().map { cell -> cell?.asString().orEmpty() },
            rows = rows.drop(1).map { it.toSheetRow() },
        )

    private fun Row.toSheetRow(): SheetRow =
        SheetRow(
            number = rowNum,
            cells = (0 until cellCount).map { index -> getCell(index).toCellValue() },
        )

    private fun Cell?.toCellValue(): CellValue =
        when (this?.type) {
            null, CellType.EMPTY -> CellValue.Empty
            CellType.BOOLEAN -> CellValue.Flag(asBoolean())
            CellType.NUMBER ->
                if (looksLikeDate(dataFormatString)) CellValue.Timestamp(asDate())
                else CellValue.Number(asNumber())

            CellType.STRING, CellType.FORMULA, CellType.ERROR ->
                asString().let { if (it.isNullOrEmpty()) CellValue.Empty else CellValue.Text(it) }
        }

    /**
     * Excel does not mark a cell as a date; a date is a number wearing a date number-format.
     * The fixture uses a custom format (`dd/MM/yyyy HH:mm:ss`, numFmtId 165) rather than one of
     * the built-in date ids, so the format *string* is what has to be inspected. Literal text
     * inside a format code is quoted, so quoted runs are stripped before looking for the
     * y/m/d/h/s date tokens.
     */
    private fun looksLikeDate(format: String?): Boolean {
        if (format.isNullOrBlank() || format == "General") return false
        val unquoted = format
            .replace(Regex("\"[^\"]*\""), "")
            .replace(Regex("\\\\."), "")
            .replace(Regex("\\[[^]]*]"), "")
        return unquoted.any { it in "yYdDhHsS" } || unquoted.contains("mm")
    }
}
