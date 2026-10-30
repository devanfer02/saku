package com.dvnnfrr.saku.excel

import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Covers "Map the Money Manager column layout" from specs/excel-interchange/spec.md,
 * against the real 1,200-row export rather than a workbook we wrote to our own
 * assumptions.
 */
class MoneyManagerParserTest {

    private val sheet: ParsedSheet by lazy {
        val outcome = ImportParser.parse(XlsxReader.read(Fixture.file), JAKARTA)
        (outcome as ParseOutcome.Parsed).sheet
    }

    @Test
    fun `every row of the mock export parses`() {
        assertEquals(emptyList<RejectedRow>(), sheet.rejected)
        assertEquals(390, sheet.rows.size)
        assertEquals(390, sheet.fileRowCount)
    }

    @Test
    fun `types map to the app's own three`() {
        assertEquals(
            mapOf(TxnType.EXPENSE to 368, TxnType.INCOME to 16, TxnType.TRANSFER to 6),
            sheet.rows.groupingBy { it.type }.eachCount(),
        )
    }

    /**
     * Scenario: The duplicate Account column. Column 11 holds the amount, not a name,
     * and the row's account comes from column 2 alone.
     */
    @Test
    fun `the account comes from column two and never from column eleven`() {
        assertEquals(
            mapOf("Card" to 337, "Cash" to 46, "OVO" to 7),
            sheet.rows.groupingBy { it.accountName }.eachCount(),
        )
        // Column 11 is the IDR figure. If it had leaked into the account mapping there
        // would be more than three distinct names and they would look like numbers.
        assertTrue(sheet.accountNames.none { it.toBigDecimalOrNull() != null })
        assertEquals(listOf("Card", "Cash", "OVO"), sheet.accountNames)
    }

    @Test
    fun `sub-second precision is truncated to the second`() {
        val raw = XlsxReader.read(Fixture.file).rows.first().timestampAt(0)!!
        assertTrue("fixture should carry sub-second precision", raw.nano > 0)

        val parsed = sheet.rows.first().occurredAt
        assertEquals(0, parsed.nanosecondsOfSecond)
        val local = parsed.toLocalDateTime(JAKARTA)
        assertEquals(LocalDateTime.of(2026, 10, 29, 18, 29, 48).toString(), local.toString())
    }

    @Test
    fun `amounts are whole rupiah`() {
        assertEquals(Money(39_000), sheet.rows.first().amount)
        assertTrue(sheet.rows.all { it.amount.isPositive })
    }

    /** Every one of the 390 rows agrees between IDR and Amount, so nothing is flagged. */
    @Test
    fun `no discrepancy in the reference export`() {
        assertEquals(emptyList<Int>(), sheet.rows.filter { it.discrepancy != null }.map { it.sourceRow })
    }

    /**
     * Scenario: A mismatch between IDR and Amount — listed as a discrepancy with both
     * values, and the row is still parsed so the user can decide.
     */
    @Test
    fun `a row whose Amount disagrees with its IDR is flagged with both values`() {
        val parsed = parseOne(moneyManagerRow(idr = BigDecimal("50000"), declared = "51000.0"))
        assertEquals(AmountDiscrepancy(BigDecimal("50000"), "51000.0"), parsed.discrepancy)
        assertEquals(Money(50_000), parsed.amount)
    }

    /** "187000.0" is text and 187000 is a number; they are the same figure. */
    @Test
    fun `a text Amount matching the numeric IDR is not a discrepancy`() {
        val parsed = parseOne(moneyManagerRow(idr = BigDecimal("187000"), declared = "187000.0"))
        assertNull(parsed.discrepancy)
    }

    /** Scenario: A non-IDR row — held back, reported, never converted. */
    @Test
    fun `a non-IDR row is held back`() {
        val sheet = ImportParser.parse(
            SheetLayout.MONEY_MANAGER,
            sheetOf(SheetLayout.MONEY_MANAGER, moneyManagerRow(currency = "USD")),
            JAKARTA,
        )
        assertEquals(emptyList<ParsedRow>(), sheet.rows)
        assertEquals(
            listOf(RejectedRow(1, RowRejection.NON_IDR_CURRENCY, "USD")),
            sheet.rejected,
        )
    }

    @Test
    fun `an unknown type word is held back with the word that was found`() {
        val sheet = ImportParser.parse(
            SheetLayout.MONEY_MANAGER,
            sheetOf(SheetLayout.MONEY_MANAGER, moneyManagerRow(type = "Refund")),
            JAKARTA,
        )
        assertEquals(listOf(RejectedRow(1, RowRejection.UNKNOWN_TYPE, "Refund")), sheet.rejected)
    }

    /**
     * The in leg would move the same money a second time. Money Manager's own export
     * omits it, but a hand-edited file can carry one.
     */
    @Test
    fun `an inbound transfer leg is dropped rather than doubled`() {
        val sheet = ImportParser.parse(
            SheetLayout.MONEY_MANAGER,
            sheetOf(SheetLayout.MONEY_MANAGER, moneyManagerRow(type = "Transfer-In")),
            JAKARTA,
        )
        assertEquals(
            listOf(RejectedRow(1, RowRejection.INBOUND_TRANSFER_LEG, "Transfer-In")),
            sheet.rejected,
        )
    }

    @Test
    fun `a fractional amount is held back rather than rounded`() {
        val sheet = ImportParser.parse(
            SheetLayout.MONEY_MANAGER,
            sheetOf(SheetLayout.MONEY_MANAGER, moneyManagerRow(idr = BigDecimal("1500.25"))),
            JAKARTA,
        )
        assertEquals(
            listOf(RejectedRow(1, RowRejection.FRACTIONAL_AMOUNT, "1500.25")),
            sheet.rejected,
        )
    }

    /** Subcategory and Description are empty on all 1,200 rows; Description never lands. */
    @Test
    fun `subcategory stays empty and description is ignored`() {
        assertEquals(emptyList<String>(), sheet.rows.mapNotNull { it.subcategoryName }.distinct())
        val parsed = parseOne(moneyManagerRow(description = "should be ignored", note = "kopi"))
        assertEquals("kopi", parsed.note)
    }

    /** Scenario: mapping the six transfer rows — grouped by category, one ask per group. */
    @Test
    fun `the six transfers group into the two investment categories`() {
        val groups = sheet.transferGroupsNeedingDestination
        assertEquals(listOf("Emas", "Reksa Dana"), groups.map { it.categoryName })
        assertEquals(6, groups.sumOf { it.rowCount })
        assertNotNull(groups.first { it.categoryName == "Emas" })
    }

    /** The whole reason destinations are asked for: this much money must not evaporate. */
    @Test
    fun `the transfer rows carry the two million one hundred thousand in the mock`() {
        assertEquals(
            Money(2_100_000),
            Money(sheet.transferGroupsNeedingDestination.sumOf { it.total.rupiah }),
        )
    }

    @Test
    fun `the date span covers four months`() {
        val first = sheet.earliest!!.toLocalDateTime(JAKARTA).date
        val last = sheet.latest!!.toLocalDateTime(JAKARTA).date
        assertEquals(2026, first.year)
        assertEquals(7, first.monthNumber)
        assertEquals(2026, last.year)
        assertEquals(10, last.monthNumber)
    }

    private fun parseOne(cells: List<CellValue>): ParsedRow {
        val sheet = ImportParser.parse(
            SheetLayout.MONEY_MANAGER,
            sheetOf(SheetLayout.MONEY_MANAGER, cells),
            JAKARTA,
        )
        assertEquals(emptyList<RejectedRow>(), sheet.rejected)
        return sheet.rows.single()
    }

    private fun moneyManagerRow(
        at: LocalDateTime = LocalDateTime.of(2026, 10, 29, 18, 29, 48),
        account: String = "Card",
        category: String = "Makan & Minum",
        note: String? = null,
        idr: BigDecimal = BigDecimal("187000"),
        type: String = "Expense",
        description: String? = null,
        declared: String = idr.toPlainString(),
        currency: String = "IDR",
    ): List<CellValue> = listOf(
        CellValue.Timestamp(at),
        CellValue.Text(account),
        CellValue.Text(category),
        CellValue.Empty,
        note?.let { CellValue.Text(it) } ?: CellValue.Empty,
        CellValue.Number(idr),
        CellValue.Text(type),
        description?.let { CellValue.Text(it) } ?: CellValue.Empty,
        CellValue.Text(declared),
        CellValue.Text(currency),
        CellValue.Number(idr),
    )
}
