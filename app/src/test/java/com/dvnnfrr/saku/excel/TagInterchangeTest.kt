package com.dvnnfrr.saku.excel

import com.dvnnfrr.saku.data.repository.BackupSnapshot
import com.dvnnfrr.saku.domain.Account
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.Category
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.Tag
import com.dvnnfrr.saku.domain.TagName
import com.dvnnfrr.saku.domain.Transaction
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal

/** Tags through the export sheet, the backup format and the database import, with no database. */
class TagInterchangeTest {

    private val opening = Instant.parse("2025-01-01T00:00:00Z")
    private val created = Instant.parse("2026-10-01T00:00:00Z")

    private val bca = Account(1, "BCA", AccountType.BANK, ColorSlot(0), 0, false, Money.ZERO, opening, null, Money.ZERO)
    private val makan = Category(10, "Makan & Minum", CategoryKind.EXPENSE, ColorSlot(1), 0, false)
    private val grocery = Tag(100, "Monthly grocery", 0, archived = false)
    private val bali = Tag(101, "Trip Bali", 1, archived = true)

    private fun expense(id: Long, amount: Long, tags: Set<Long>) = Transaction(
        id = id, occurredAt = Instant.parse("2026-10-0${id}T05:00:00Z"), type = TxnType.EXPENSE,
        amount = Money(amount), accountId = 1, categoryId = 10, createdAt = created, tagIds = tags,
    )

    private val snapshot = BackupSnapshot(
        accounts = listOf(bca),
        categories = listOf(makan),
        subcategories = emptyList(),
        transactions = listOf(
            expense(1, 120_000, setOf(grocery.id, bali.id)),
            expense(2, 30_000, setOf(grocery.id)),
            expense(3, 50_000, emptySet()),
        ),
        tags = listOf(grocery, bali),
    )

    private fun roundTrip(tables: List<SheetTable>): List<SheetTable> {
        val bytes = ByteArrayOutputStream().also { XlsxWriter.write(tables, it) }.toByteArray()
        return XlsxReader.readAll(ByteArrayInputStream(bytes))
    }

    private fun backupSheets() = BackupSheets.build(snapshot, "1.0", created, JAKARTA)

    @Test
    fun `tag names split and join exactly`() {
        assertEquals("Monthly grocery; Trip Bali", TagName.join(listOf("Monthly grocery", "Trip Bali")))
        assertEquals(listOf("Monthly grocery", "Trip Bali"), TagName.split(" Monthly  grocery ;Trip Bali; ;monthly grocery"))
        assertEquals(emptyList<String>(), TagName.split(null))
    }

    @Test
    fun `the export writes tag names in the last column and the parser reads them back`() {
        val table = ExportSheet.build(
            snapshot.transactions, mapOf(1L to "BCA"), mapOf(10L to "Makan & Minum"), emptyMap(), JAKARTA,
            tagNames = mapOf(grocery.id to grocery.name, bali.id to bali.name),
        )
        assertEquals("Tags", table.headers.last())

        val read = roundTrip(listOf(table)).single()
        val sheet = (ImportParser.parse(read, JAKARTA) as ParseOutcome.Parsed).sheet
        assertEquals(
            listOf(listOf("Monthly grocery", "Trip Bali"), listOf("Monthly grocery"), emptyList()),
            sheet.rows.map { it.tagNames },
        )
    }

    @Test
    fun `an export written before tags still imports as the Saku layout`() {
        val table = ExportSheet.build(snapshot.transactions, mapOf(1L to "BCA"), mapOf(10L to "Makan & Minum"), emptyMap(), JAKARTA)
        val old = table.copy(headers = table.headers.dropLast(1), rows = table.rows.map { it.copy(cells = it.cells.dropLast(1)) })

        val sheet = (ImportParser.parse(roundTrip(listOf(old)).single(), JAKARTA) as ParseOutcome.Parsed).sheet
        assertEquals(SheetLayout.SAKU, sheet.layout)
        assertEquals(3, sheet.rows.size)
        assertTrue(sheet.rows.all { it.tagNames.isEmpty() })
    }

    @Test
    fun `a format 2 backup carries the Tags sheet with the archived flag`() {
        val contents = (BackupSheets.parse(roundTrip(backupSheets())) as BackupReadOutcome.Read).contents
        assertEquals(2, contents.info.formatVersion)
        assertEquals(listOf(BackupTag("Monthly grocery", false, 0), BackupTag("Trip Bali", true, 1)), contents.tags)
        assertEquals(listOf("Monthly grocery", "Trip Bali"), contents.transactions.rows.first().tagNames)
    }

    @Test
    fun `a format 1 backup still reads, with no tags`() {
        val v1 = backupSheets()
            .filterNot { it.sheetName == BackupSheets.TAGS }
            .map { table ->
                when {
                    table.sheetName == ExportSheet.SHEET_NAME -> table.copy(
                        headers = table.headers.dropLast(1),
                        rows = table.rows.map { it.copy(cells = it.cells.dropLast(1)) },
                    )
                    table.sheetName == BackupSheets.BACKUP -> table.copy(
                        rows = table.rows.map { row ->
                            if (row.textAt(0) != "Format version") row
                            else row.copy(cells = listOf(row[0], CellValue.Number(BigDecimal.ONE)))
                        },
                    )
                    else -> table
                }
            }

        val contents = (BackupSheets.parse(roundTrip(v1)) as BackupReadOutcome.Read).contents
        assertEquals(1, contents.info.formatVersion)
        assertEquals(emptyList<BackupTag>(), contents.tags)
        assertEquals(3, contents.transactions.rows.size)
        assertTrue(contents.transactions.rows.all { it.tagNames.isEmpty() })
    }

    @Test
    fun `a format 2 backup without its Tags sheet is refused`() {
        val sheets = backupSheets().filterNot { it.sheetName == BackupSheets.TAGS }
        assertEquals(
            BackupReadOutcome.Refused(BackupRefusal.MissingSheet(BackupSheets.TAGS)),
            BackupSheets.parse(sheets),
        )
    }

    @Test
    fun `a row naming a tag the Tags sheet lacks is refused`() {
        val sheets = backupSheets().map { table ->
            if (table.sheetName != BackupSheets.TAGS) table else table.copy(rows = table.rows.take(1))
        }
        assertEquals(
            BackupReadOutcome.Refused(BackupRefusal.UnknownName(ExportSheet.SHEET_NAME, "Trip Bali")),
            BackupSheets.parse(sheets),
        )
    }

    @Test
    fun `restore plans to create only the tags the database lacks`() {
        val contents = (BackupSheets.parse(backupSheets()) as BackupReadOutcome.Read).contents
        val existing = ExistingRecords(
            accounts = listOf(bca), categories = listOf(makan), subcategories = emptyList(),
            tags = listOf(Tag(7, "monthly GROCERY", 0, archived = false)),
        )
        val plan = RestorePlan.of(contents, existing)
        assertEquals(listOf("Trip Bali"), plan.tagsToCreate.map { it.name })
        assertEquals(listOf("Monthly grocery", "Trip Bali"), plan.pendingRows(plan.matched).first().tagNames)
    }

    @Test
    fun `a schema 2 database file carries tags and a schema 1 file has none`() {
        val rows = DatabaseRows(
            accounts = listOf(DatabaseAccountRow(1, "BCA", "BANK", 0, 0, 0, 0, false, null)),
            categories = listOf(DatabaseCategoryRow(10, "Makan & Minum", "EXPENSE", 1, 0, false)),
            subcategories = emptyList(),
            transactions = listOf(DatabaseTxnRow(1, 1_790_000_000, "EXPENSE", 120_000, 1, null, 10, null, null)),
        )
        val v1 = (DatabaseImport.toBackup(rows, 1) as DatabaseReadOutcome.Read).contents
        assertEquals(emptyList<BackupTag>(), v1.tags)
        assertTrue(v1.transactions.rows.single().tagNames.isEmpty())

        val v2 = (
            DatabaseImport.toBackup(
                rows.copy(
                    tags = listOf(DatabaseTagRow(5, "Trip Bali", 0, archived = true)),
                    txnTags = listOf(DatabaseTxnTagRow(1, 5)),
                ),
                2,
            ) as DatabaseReadOutcome.Read
            ).contents
        assertEquals(listOf(BackupTag("Trip Bali", true, 0)), v2.tags)
        assertEquals(listOf("Trip Bali"), v2.transactions.rows.single().tagNames)
    }

    @Test
    fun `a link to a missing tag is refused`() {
        val rows = DatabaseRows(
            accounts = listOf(DatabaseAccountRow(1, "BCA", "BANK", 0, 0, 0, 0, false, null)),
            categories = emptyList(),
            subcategories = emptyList(),
            transactions = listOf(DatabaseTxnRow(1, 1_790_000_000, "TRANSFER", 1, 1, 1, null, null, null)),
            txnTags = listOf(DatabaseTxnTagRow(1, 99)),
        )
        assertEquals(
            DatabaseReadOutcome.Refused(DatabaseRefusal.MissingReference(DatabaseImport.TXN_TAG, 1, "tagId")),
            DatabaseImport.toBackup(rows, 2),
        )
    }
}
