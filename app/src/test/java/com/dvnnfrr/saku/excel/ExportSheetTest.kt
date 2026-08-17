package com.dvnnfrr.saku.excel

import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.Transaction
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Covers "Export to a losslessly re-importable sheet" at the sheet level: what
 * [ExportSheet] writes is exactly what [ImportParser] reads back.
 *
 * The file really is written and re-read through fastexcel rather than compared in
 * memory, because the lossy step is the serialisation — Excel keeps a timestamp as a
 * fractional day in a double, and a whole second can come back a fraction short.
 */
class ExportSheetTest {

    private val accounts = mapOf(1L to "BCA", 2L to "Cash", 3L to "RDPU SucorInvest")
    private val categories = mapOf(10L to "Makan & Minum", 11L to "Gaji", 12L to "Saham")
    private val subcategories = mapOf(20L to "Warung")

    private val transactions = listOf(
        txn(1, "2026-08-16T11:47:34Z", TxnType.EXPENSE, 187_000, 1, categoryId = 10, subcategoryId = 20, note = "nasi padang"),
        txn(2, "2026-08-01T02:00:00Z", TxnType.INCOME, 7_500_000, 1, categoryId = 11),
        txn(3, "2026-07-20T04:30:00Z", TxnType.TRANSFER, 1_000_000, 1, toAccountId = 3, categoryId = 12),
        txn(4, "2026-08-10T09:15:00Z", TxnType.ADJUSTMENT, -42_000, 2),
    )

    @Test
    fun `the header row is the Saku layout`() {
        assertEquals(SheetLayout.SAKU.headers, exported().headers)
        assertEquals(ExportSheet.SHEET_NAME, exported().sheetName)
    }

    /** Scenario: Exporting a transfer — both accounts named. */
    @Test
    fun `a transfer carries its source in Account and its destination in To Account`() {
        val row = exported().rows.single { it.textAt(SheetLayout.Saku.TYPE) == "Transfer" }
        assertEquals("BCA", row.textAt(SheetLayout.Saku.ACCOUNT))
        assertEquals("RDPU SucorInvest", row.textAt(SheetLayout.Saku.TO_ACCOUNT))
    }

    /** Nothing else has a destination, so the column stays empty rather than repeating the source. */
    @Test
    fun `an expense leaves To Account empty`() {
        val row = exported().rows.single { it.textAt(SheetLayout.Saku.TYPE) == "Expense" }
        assertEquals(CellValue.Empty, row[SheetLayout.Saku.TO_ACCOUNT])
    }

    /** Scenario: Round-tripping — every field intact after a real write and read. */
    @Test
    fun `a written export parses back into the same rows`() {
        val reparsed = writeAndParse()

        assertEquals(emptyList<RejectedRow>(), reparsed.rejected)
        assertEquals(SheetLayout.SAKU, reparsed.layout)
        assertEquals(transactions.size, reparsed.rows.size)

        val byNote = reparsed.rows.associateBy { it.note }
        val expense = byNote.getValue("nasi padang")
        assertEquals(Instant.parse("2026-08-16T11:47:34Z"), expense.occurredAt)
        assertEquals(TxnType.EXPENSE, expense.type)
        assertEquals(Money(187_000), expense.amount)
        assertEquals("BCA", expense.accountName)
        assertNull(expense.toAccountName)
        assertEquals("Makan & Minum", expense.categoryName)
        assertEquals("Warung", expense.subcategoryName)
    }

    /**
     * The regression this class exists for. `Instant` truncated to the second must come
     * back as the same second, or every row's dedup hash changes and re-importing an
     * export reports 100% new instead of 100% already present.
     */
    @Test
    fun `every timestamp survives the serial-date round trip to the second`() {
        val original = transactions.map { it.occurredAt }.sorted()
        assertEquals(original, writeAndParse().rows.map { it.occurredAt })
    }

    @Test
    fun `an adjustment keeps its sign`() {
        val adjustment = writeAndParse().rows.single { it.type == TxnType.ADJUSTMENT }
        assertEquals(Money(-42_000), adjustment.amount)
    }

    /** A zero adjustment corrects nothing; the repository refuses one, so the parser does too. */
    @Test
    fun `a zero adjustment is held back`() {
        val sheet = ImportParser.parse(
            SheetLayout.SAKU,
            sheetOf(
                SheetLayout.SAKU,
                listOf(
                    CellValue.Timestamp(java.time.LocalDateTime.of(2026, 8, 1, 9, 0, 0)),
                    CellValue.Text("Adjustment"),
                    CellValue.Number(java.math.BigDecimal.ZERO),
                    CellValue.Text("Cash"),
                ),
            ),
            JAKARTA,
        )
        assertEquals(listOf(RejectedRow(1, RowRejection.ZERO_ADJUSTMENT)), sheet.rejected)
    }

    /** A transfer with no destination is the shape that destroys net worth. It never parses. */
    @Test
    fun `a Saku transfer without a destination is held back`() {
        val sheet = ImportParser.parse(
            SheetLayout.SAKU,
            sheetOf(
                SheetLayout.SAKU,
                listOf(
                    CellValue.Timestamp(java.time.LocalDateTime.of(2026, 8, 1, 9, 0, 0)),
                    CellValue.Text("Transfer"),
                    CellValue.Number(java.math.BigDecimal("1000000")),
                    CellValue.Text("BCA"),
                ),
            ),
            JAKARTA,
        )
        assertEquals(listOf(RejectedRow(1, RowRejection.MISSING_DESTINATION)), sheet.rejected)
    }

    private fun exported() =
        ExportSheet.build(transactions, accounts, categories, subcategories, JAKARTA)

    private fun writeAndParse(): ParsedSheet {
        val bytes = ByteArrayOutputStream().also { XlsxWriter.write(exported(), it) }.toByteArray()
        val table = XlsxReader.read(ByteArrayInputStream(bytes))
        return (ImportParser.parse(table, JAKARTA) as ParseOutcome.Parsed).sheet
    }

    private fun txn(
        id: Long,
        at: String,
        type: TxnType,
        amount: Long,
        accountId: Long,
        toAccountId: Long? = null,
        categoryId: Long? = null,
        subcategoryId: Long? = null,
        note: String? = null,
    ) = Transaction(
        id = id,
        occurredAt = Instant.parse(at),
        type = type,
        amount = Money(amount),
        accountId = accountId,
        toAccountId = toAccountId,
        categoryId = categoryId,
        subcategoryId = subcategoryId,
        note = note,
        createdAt = Instant.parse(at),
    )
}
