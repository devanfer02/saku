package com.dvnnfrr.saku.excel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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

    /** A near miss is still a miss: one renamed column changes what every index means. */
    @Test
    fun `a layout missing one column is not recognised`() {
        val almost = SheetLayout.MONEY_MANAGER.headers.dropLast(1)
        assertTrue(LayoutDetector.detect(almost) is LayoutMatch.Unrecognised)
    }
}
