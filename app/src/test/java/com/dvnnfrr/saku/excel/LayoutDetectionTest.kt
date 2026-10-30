package com.dvnnfrr.saku.excel

import com.dvnnfrr.saku.domain.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Covers "Two import entry points, one parser" from specs/excel-interchange/spec.md.
 *
 * The header row is the only thing consulted, which is what lets both menu items sit in
 * front of the same parser: "Import from Money Manager" opened on a Saku export still
 * reads it correctly.
 */
class LayoutDetectionTest {

    @Test
    fun `the real export is recognised as the Money Manager layout`() {
        val table = XlsxReader.read(Fixture.file)
        assertEquals(
            LayoutMatch.Recognised(SheetLayout.MONEY_MANAGER),
            LayoutDetector.detect(table.headers),
        )
    }

    /** Whichever entry point was tapped, the file's own header row decides. */
    @Test
    fun `the Money Manager shape is detected regardless of entry point`() {
        val table = XlsxReader.read(Fixture.file)
        val outcome = ImportParser.parse(table, JAKARTA)
        assertTrue(outcome is ParseOutcome.Parsed)
        assertEquals(SheetLayout.MONEY_MANAGER, (outcome as ParseOutcome.Parsed).sheet.layout)
    }

    @Test
    fun `the Saku shape is recognised`() {
        assertEquals(
            LayoutMatch.Recognised(SheetLayout.SAKU),
            LayoutDetector.detect(SheetLayout.SAKU.headers),
        )
    }

    @Test
    fun `case and stray whitespace do not change the layout`() {
        val sloppy = SheetLayout.SAKU.headers.map { "  ${it.uppercase()} " }
        assertEquals(LayoutMatch.Recognised(SheetLayout.SAKU), LayoutDetector.detect(sloppy))
    }

    /** Excel leaves blank cells past the last real column; that is not a new layout. */
    @Test
    fun `trailing blank headers are ignored`() {
        val padded = SheetLayout.MONEY_MANAGER.headers + listOf("", "  ", "")
        assertEquals(
            LayoutMatch.Recognised(SheetLayout.MONEY_MANAGER),
            LayoutDetector.detect(padded),
        )
    }

    /**
     * Scenario: An unrecognised layout — refused before any parsing, naming what was
     * expected and what was found.
     */
    @Test
    fun `an unrecognised header row is refused with both column lists`() {
        val found = listOf("When", "How much", "What for")
        val outcome = ImportParser.parse(
            SheetTable("Sheet1", found, listOf(SheetRow(1, listOf(CellValue.Text("x"))))),
            JAKARTA,
        )

        val refusal = outcome as ParseOutcome.UnrecognisedLayout
        assertEquals(found, refusal.mismatch.found)
        assertEquals(
            SheetLayout.MONEY_MANAGER.headers,
            refusal.mismatch.expected[SheetLayout.MONEY_MANAGER],
        )
        assertEquals(SheetLayout.SAKU.headers, refusal.mismatch.expected[SheetLayout.SAKU])
    }

    /** Exports from October 2026 on carry an empty `Tag` column after `Income/Expense`. */
    @Test
    fun `the Money Manager export with a Tag column parses as the reference layout`() {
        val outcome = ImportParser.parse(taggedTable(tag = ""), JAKARTA)

        val sheet = (outcome as ParseOutcome.Parsed).sheet
        assertEquals(SheetLayout.MONEY_MANAGER, sheet.layout)
        val row = sheet.rows.single()
        assertEquals(Money(25_500), row.amount)
        assertEquals("Nasi Rawon", row.note)
        assertNull(row.discrepancy)
    }

    /** Saku has no tags; a filled one is dropped rather than shifting the amount columns. */
    @Test
    fun `a filled Tag is dropped without moving the columns after it`() {
        val outcome = ImportParser.parse(taggedTable(tag = "kos"), JAKARTA)

        val row = (outcome as ParseOutcome.Parsed).sheet.rows.single()
        assertEquals(Money(25_500), row.amount)
        assertNull(row.discrepancy)
    }

    @Test
    fun `a Tag column anywhere else is not recognised`() {
        val misplaced = SheetLayout.MONEY_MANAGER.headers + "Tag"
        val outcome = ImportParser.parse(SheetTable("Sheet1", misplaced, emptyList()), JAKARTA)
        assertTrue(outcome is ParseOutcome.UnrecognisedLayout)
    }

    private fun taggedTable(tag: String): SheetTable {
        val headers = SheetLayout.MONEY_MANAGER.headers.toMutableList().apply { add(7, "Tag") }
        val cells = listOf(
            CellValue.Timestamp(LocalDateTime.of(2026, 10, 29, 14, 58, 48)),
            CellValue.Text("Card"),
            CellValue.Text("Makan & Minum"),
            CellValue.Empty,
            CellValue.Text("Nasi Rawon"),
            CellValue.Number(BigDecimal("25500")),
            CellValue.Text("Expense"),
            if (tag.isEmpty()) CellValue.Empty else CellValue.Text(tag),
            CellValue.Empty,
            CellValue.Text("25500"),
            CellValue.Text("IDR"),
            CellValue.Number(BigDecimal("25500")),
        )
        return SheetTable("Sheet1", headers, listOf(SheetRow(2, cells)))
    }

    /** A near miss is still a miss: one renamed column changes what every index means. */
    @Test
    fun `a layout missing one column is not recognised`() {
        val almost = SheetLayout.MONEY_MANAGER.headers.dropLast(1)
        assertTrue(LayoutDetector.detect(almost) is LayoutMatch.Unrecognised)
    }
}
