package com.dvnnfrr.saku.excel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Spike check 2 — open the real 1,200-row export and get typed rows back.
 *
 * Runs on the JVM. It proves fastexcel *parses the file correctly*; it does not prove the
 * reader runs on Android, and it cannot — the desktop JDK supplies the StAX API that Android
 * lacks, so this test would pass either way. `XlsxAndroidRuntimeTest` is the one that settles
 * the Android question.
 */
class XlsxReaderFixtureTest {

    private val table: SheetTable by lazy { XlsxReader.read(Fixture.file) }

    @Test
    fun `reads every data row from the real export`() {
        assertEquals(1_200, table.rows.size)
    }

    @Test
    fun `reads the sheet name`() {
        assertEquals("Money Manager", table.sheetName)
    }

    @Test
    fun `reads all eleven headers, with Account appearing twice`() {
        assertEquals(
            listOf(
                "Date",
                "Account",
                "Category",
                "Subcategory",
                "Note",
                "IDR",
                "Income/Expense",
                "Description",
                "Amount",
                "Currency",
                "Account",
            ),
            table.headers,
        )
        assertEquals(11, table.columnCount)
        assertEquals(2, table.headers.count { it == "Account" })
    }

    /**
     * The duplicate is not a parsing artefact — the two `Account` columns hold different
     * things. Column 2 is the account name; column 11 repeats the IDR amount and is the one
     * the importer discards.
     */
    @Test
    fun `the two Account columns hold different kinds of value`() {
        val row = table.rows.first()
        assertEquals("Card", row.textAt(1))
        assertEquals(0, BigDecimal("187000").compareTo(row.numberAt(10)))
        assertEquals(0, row.numberAt(5)!!.compareTo(row.numberAt(10)))
    }

    /** Column 11 duplicates column 6 on every single row, which is why it can be dropped. */
    @Test
    fun `column eleven duplicates the IDR column on every row`() {
        val differing = table.rows.count { row ->
            val idr = row.numberAt(5)
            val trailing = row.numberAt(10)
            idr == null || trailing == null || idr.compareTo(trailing) != 0
        }
        assertEquals(0, differing)
    }

    @Test
    fun `transaction types match the known distribution`() {
        val byType = table.rows.groupingBy { it.textAt(6) }.eachCount()
        assertEquals(mapOf("Expense" to 1_160, "Income" to 35, "Transfer-Out" to 6), byType)
    }

    @Test
    fun `every row is IDR`() {
        assertEquals(setOf("IDR"), table.rows.mapNotNull { it.textAt(9) }.toSet())
    }

    @Test
    fun `accounts collapse to three names, dominated by Card`() {
        val byAccount = table.rows.groupingBy { it.textAt(1) }.eachCount()
        assertEquals(mapOf("Card" to 1_150, "Cash" to 43, "Gopay" to 3), byAccount)
    }

    /** Gap 1 from the analysis: the column exists and was never once filled in. */
    @Test
    fun `Subcategory and Description are empty on every row`() {
        assertEquals(0, table.rows.count { it[3] != CellValue.Empty })
        assertEquals(0, table.rows.count { it[7] != CellValue.Empty })
    }

    /**
     * The date column carries a custom number format rather than a built-in date id, so this
     * also covers the reader's format-string date detection.
     */
    @Test
    fun `dates come back as timestamps with sub-second precision`() {
        val first = table.rows.first().timestampAt(0)!!
        assertEquals(2026, first.year)
        assertEquals(8, first.monthValue)
        assertEquals(16, first.dayOfMonth)
        assertEquals(18, first.hour)
        assertEquals(47, first.minute)
        assertEquals(34, first.second)
        assertTrue("expected sub-second precision, got $first", first.nano > 0)

        assertEquals(1_200, table.rows.count { it.timestampAt(0) != null })
    }

    /**
     * Column 9 (`Amount`) is *text* holding the same figure column 6 stores as a number.
     * The importer's cross-check has to parse it rather than read it as a number.
     */
    @Test
    fun `Amount is text while IDR is numeric`() {
        val row = table.rows.first()
        assertTrue("Amount should be text", row[8] is CellValue.Text)
        assertTrue("IDR should be numeric", row[5] is CellValue.Number)
        assertEquals("187000.0", row.textAt(8))
    }

    /** Nothing is fractional, which is what lets the app store rupiah as Long. */
    @Test
    fun `every IDR amount is a whole number of rupiah`() {
        val fractional = table.rows.filter { row ->
            val idr = row.numberAt(5) ?: return@filter true
            idr.stripTrailingZeros().scale() > 0
        }
        assertEquals(emptyList<SheetRow>(), fractional)
    }
}
