package com.dvnnfrr.saku.excel

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Spike check 3 — write an `.xlsx`, read it back, assert the values survive.
 *
 * The payload is taken from the mock export rather than written inline, so the round trip is
 * exercised against the value shapes the app will actually handle: Indonesian category names
 * with an ampersand, microsecond timestamps, and seven-digit rupiah amounts.
 */
class XlsxRoundTripTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun roundTrip(table: SheetTable): SheetTable {
        val file = temporaryFolder.newFile("round-trip.xlsx")
        file.outputStream().use { XlsxWriter.write(table, it) }
        return XlsxReader.read(file)
    }

    @Test
    fun `a table written by Saku reads back identically`() {
        val original = SheetTable(
            sheetName = "Saku",
            headers = listOf("Date", "Account", "Category", "Amount", "Note", "Cleared"),
            rows = listOf(
                SheetRow(
                    number = 1,
                    cells = listOf(
                        CellValue.Timestamp(LocalDateTime.of(2026, 10, 29, 17, 3, 13)),
                        CellValue.Text("BCA"),
                        CellValue.Text("Makan & Minum"),
                        CellValue.Number(BigDecimal("187000")),
                        CellValue.Text("Guri Ramen"),
                        CellValue.Flag(true),
                    ),
                ),
                SheetRow(
                    number = 2,
                    cells = listOf(
                        CellValue.Timestamp(LocalDateTime.of(2025, 7, 28, 16, 26, 6)),
                        CellValue.Text("Cash"),
                        CellValue.Text("Pemberian"),
                        CellValue.Number(BigDecimal("5050800")),
                        CellValue.Empty,
                        CellValue.Flag(false),
                    ),
                ),
            ),
        )

        val reloaded = roundTrip(original)

        assertEquals(original.sheetName, reloaded.sheetName)
        assertEquals(original.headers, reloaded.headers)
        assertEquals(original.rows.size, reloaded.rows.size)

        original.rows.forEachIndexed { index, expectedRow ->
            val actualRow = reloaded.rows[index]
            expectedRow.cells.forEachIndexed { column, expected ->
                assertSameCell("row $index column $column", expected, actualRow[column])
            }
        }
    }

    /**
     * The heavier version: take 250 rows straight off the mock Money Manager export, write
     * them, read them back and compare. This is the path export-then-reimport will take.
     */
    @Test
    fun `mock export rows survive a write and re-read`() {
        val source = XlsxReader.read(Fixture.file)
        val sample = SheetTable(
            sheetName = "Saku",
            headers = source.headers,
            rows = source.rows.take(250),
        )

        val reloaded = roundTrip(sample)

        assertEquals(sample.headers, reloaded.headers)
        assertEquals(250, reloaded.rows.size)

        sample.rows.forEachIndexed { index, expectedRow ->
            val actualRow = reloaded.rows[index]
            assertEquals("cell count on row $index", expectedRow.cells.size, actualRow.cells.size)
            expectedRow.cells.forEachIndexed { column, expected ->
                assertSameCell("row $index column $column", expected, actualRow[column])
            }
        }
    }

    /**
     * [BigDecimal] equality is scale-sensitive — `187000` and `187000.0` are unequal but
     * represent the same amount, and a round trip through Excel's floating-point cell storage
     * legitimately changes the scale. Compare numbers by value.
     */
    private fun assertSameCell(where: String, expected: CellValue, actual: CellValue) {
        if (expected is CellValue.Number && actual is CellValue.Number) {
            assertEquals(where, 0, expected.value.compareTo(actual.value))
        } else {
            assertEquals(where, expected, actual)
        }
    }
}
