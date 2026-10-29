package com.dvnnfrr.saku.excel

import com.dvnnfrr.saku.data.repository.BackupSnapshot
import com.dvnnfrr.saku.domain.Account
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.Category
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.Subcategory
import com.dvnnfrr.saku.domain.Transaction
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal

/**
 * The backup format against specs/full-backup/spec.md: what the five sheets hold, that the
 * plain importer still reads sheet one, and which files restore refuses before anything
 * is written. Everything goes through a real `.xlsx` byte stream where the cell types
 * matter, because a timestamp or a boolean that only survives in memory proves nothing.
 */
class BackupSheetsTest {

    private val writtenAt = Instant.parse("2026-10-30T02:15:00Z")

    // -- Contents of a backup -----------------------------------------------------

    @Test
    fun `a backup has the five sheets in order, transactions first`() {
        val names = build().map { it.sheetName }
        assertEquals(listOf("Saku", "Accounts", "Categories", "Subcategories", "Backup"), names)
    }

    /** Scenario: Contents of a backup. Every record once, archived ones flagged. */
    @Test
    fun `every record appears once and archived ones carry the flag`() {
        val contents = readBack(build())

        assertEquals(listOf("BCA", "Cash", "Old wallet"), contents.accounts.map { it.name })
        assertEquals(listOf(false, false, true), contents.accounts.map { it.archived })
        assertEquals(listOf("Makan & Minum", "Gaji", "Kos lama"), contents.categories.map { it.name })
        assertEquals(listOf(false, false, true), contents.categories.map { it.archived })
        assertEquals(listOf("Warung", "Restoran"), contents.subcategories.map { it.name })
        assertEquals(listOf(false, true), contents.subcategories.map { it.archived })
        assertEquals(4, contents.transactions.rows.size)
        assertEquals(0, contents.transactions.rejected.size)
    }

    /** Scenario: Accounts keep what the transaction sheet cannot. */
    @Test
    fun `an account row holds opening balance and date, type, colour, sort order and reconcile date`() {
        val bca = readBack(build()).accounts.single { it.name == "BCA" }

        assertEquals(Money(2_500_000), bca.openingBalance)
        assertEquals(OPENING, bca.openingAsOf)
        assertEquals(AccountType.BANK, bca.type)
        assertEquals(ColorSlot(5), bca.colorSlot)
        assertEquals(3, bca.sortOrder)
        assertEquals(Instant.parse("2026-09-30T12:00:00Z"), bca.lastReconciled)
        assertNull(readBack(build()).accounts.single { it.name == "Cash" }.lastReconciled)
    }

    @Test
    fun `the backup sheet records format, app version, write time and zone`() {
        val info = readBack(build()).info
        assertEquals(1, info.formatVersion)
        assertEquals("1.0", info.appVersion)
        assertEquals(writtenAt, info.writtenAt)
        assertEquals(JAKARTA, info.zone)
    }

    /** Scenario: Ordinary import still reads a backup. */
    @Test
    fun `the plain importer reads a backup's first sheet exactly as an export`() {
        val bytes = toBytes(build())
        val outcome = ImportParser.parse(XlsxReader.read(ByteArrayInputStream(bytes)), JAKARTA)

        val sheet = (outcome as ParseOutcome.Parsed).sheet
        assertEquals(SheetLayout.SAKU, sheet.layout)
        assertEquals(4, sheet.rows.size)
        assertEquals(0, sheet.rejected.size)
    }

    @Test
    fun `a parent category's kind travels with its subcategory`() {
        val warung = readBack(build()).subcategories.single { it.name == "Warung" }
        assertEquals("Makan & Minum", warung.categoryName)
        assertEquals(CategoryKind.EXPENSE, warung.categoryKind)
    }

    // -- Versioning and refusals --------------------------------------------------

    /** Scenario: Backup from a newer app. */
    @Test
    fun `a newer format is refused and names the app version that wrote it`() {
        val sheets = build(appVersion = "2.3").map { table ->
            if (table.sheetName != BackupSheets.BACKUP) return@map table
            table.copy(
                rows = table.rows.map { row ->
                    if (row.textAt(0) != "Format version") row
                    else row.copy(cells = listOf(row[0], CellValue.Number(BigDecimal(2))))
                },
            )
        }

        assertEquals(
            BackupReadOutcome.Refused(BackupRefusal.NewerFormat(formatVersion = 2, appVersion = "2.3")),
            BackupSheets.parse(sheets),
        )
    }

    /**
     * Refused before any record sheet is read: a newer file whose Accounts sheet would
     * also fail still reports the version, which is the one thing the user can act on.
     */
    @Test
    fun `the version is checked before the record sheets`() {
        val sheets = build().map { table ->
            when (table.sheetName) {
                BackupSheets.ACCOUNTS -> table.copy(headers = listOf("Something else"))
                BackupSheets.BACKUP -> table.copy(
                    rows = table.rows.map { row ->
                        if (row.textAt(0) != "Format version") row
                        else row.copy(cells = listOf(row[0], CellValue.Number(BigDecimal(7))))
                    },
                )

                else -> table
            }
        }

        val refusal = (BackupSheets.parse(sheets) as BackupReadOutcome.Refused).refusal
        assertTrue(refusal is BackupRefusal.NewerFormat)
    }

    @Test
    fun `an export is not a backup`() {
        val exportOnly = build().take(1)
        assertEquals(
            BackupReadOutcome.Refused(BackupRefusal.MissingSheet(BackupSheets.BACKUP)),
            BackupSheets.parse(exportOnly),
        )
    }

    @Test
    fun `two accounts with one name are refused and the name is given`() {
        val doubled = snapshot().let { it.copy(accounts = it.accounts + it.accounts[1].copy(id = 99, name = " cash ")) }
        assertEquals(
            BackupReadOutcome.Refused(BackupRefusal.DuplicateName(BackupSheets.ACCOUNTS, "cash")),
            BackupSheets.parse(build(doubled)),
        )
    }

    @Test
    fun `one category name may exist once per kind`() {
        val both = snapshot().let {
            it.copy(categories = it.categories + category(40, "Makan & Minum", CategoryKind.INCOME))
        }
        assertTrue(BackupSheets.parse(build(both)) is BackupReadOutcome.Read)
    }

    @Test
    fun `a transaction naming an account the backup does not list is refused`() {
        val sheets = build().map { table ->
            if (table.sheetName != BackupSheets.ACCOUNTS) table
            else table.copy(rows = table.rows.filterNot { it.textAt(0) == "Cash" })
        }
        assertEquals(
            BackupReadOutcome.Refused(BackupRefusal.UnknownName(ExportSheet.SHEET_NAME, "Cash")),
            BackupSheets.parse(sheets),
        )
    }

    @Test
    fun `an unreadable cell is refused with its sheet, row and column`() {
        val sheets = build().map { table ->
            if (table.sheetName != BackupSheets.ACCOUNTS) table
            else table.copy(
                rows = table.rows.mapIndexed { index, row ->
                    if (index != 0) row
                    else row.copy(cells = row.cells.toMutableList().also { it[4] = CellValue.Number(BigDecimal(9)) })
                },
            )
        }
        assertEquals(
            BackupReadOutcome.Refused(BackupRefusal.InvalidCell(BackupSheets.ACCOUNTS, 1, "Colour slot")),
            BackupSheets.parse(sheets),
        )
    }

    /** Version 1 readers ignore columns they do not know, so adding one is not a format change. */
    @Test
    fun `an unknown extra column is ignored`() {
        val sheets = build().map { table ->
            if (table.sheetName != BackupSheets.ACCOUNTS) table
            else table.copy(
                headers = listOf("Added later") + table.headers,
                rows = table.rows.map { it.copy(cells = listOf(CellValue.Text("x")) + it.cells) },
            )
        }
        val contents = (BackupSheets.parse(sheets) as BackupReadOutcome.Read).contents
        assertEquals(Money(2_500_000), contents.accounts.single { it.name == "BCA" }.openingBalance)
    }

    // -- Helpers ------------------------------------------------------------------

    private fun build(snapshot: BackupSnapshot = snapshot(), appVersion: String = "1.0"): List<SheetTable> =
        BackupSheets.build(snapshot, appVersion, writtenAt, JAKARTA)

    private fun toBytes(sheets: List<SheetTable>): ByteArray =
        ByteArrayOutputStream().also { XlsxWriter.write(sheets, it) }.toByteArray()

    /** Through a real file, so cell types are what fastexcel writes and reads back. */
    private fun readBack(sheets: List<SheetTable>): BackupContents {
        val read = XlsxReader.readAll(ByteArrayInputStream(toBytes(sheets)))
        return (BackupSheets.parse(read) as BackupReadOutcome.Read).contents
    }

    companion object {
        val OPENING: Instant = Instant.parse("2025-01-01T00:00:00Z")

        fun account(id: Long, name: String, type: AccountType, opening: Long = 0, sortOrder: Int = id.toInt()) = Account(
            id = id,
            name = name,
            type = type,
            colorSlot = ColorSlot((id % ColorSlot.COUNT).toInt()),
            sortOrder = sortOrder,
            archived = false,
            openingBalance = Money(opening),
            openingAsOf = OPENING,
            lastReconciled = null,
            balance = Money(opening),
        )

        fun category(id: Long, name: String, kind: CategoryKind, archived: Boolean = false) = Category(
            id = id,
            name = name,
            kind = kind,
            colorSlot = ColorSlot((id % ColorSlot.COUNT).toInt()),
            sortOrder = id.toInt(),
            archived = archived,
        )

        fun snapshot(): BackupSnapshot {
            val bca = account(1, "BCA", AccountType.BANK, opening = 2_500_000, sortOrder = 3).copy(
                colorSlot = ColorSlot(5),
                lastReconciled = Instant.parse("2026-09-30T12:00:00Z"),
            )
            val cash = account(2, "Cash", AccountType.CASH)
            val wallet = account(3, "Old wallet", AccountType.CASH).copy(archived = true)
            val makan = category(10, "Makan & Minum", CategoryKind.EXPENSE)
            val gaji = category(11, "Gaji", CategoryKind.INCOME)
            val kos = category(12, "Kos lama", CategoryKind.EXPENSE, archived = true)
            val warung = Subcategory(id = 20, categoryId = 10, name = "Warung", sortOrder = 0, archived = false)
            val restoran = Subcategory(id = 21, categoryId = 10, name = "Restoran", sortOrder = 1, archived = true)
            val created = Instant.parse("2026-10-01T00:00:00Z")

            return BackupSnapshot(
                accounts = listOf(bca, cash, wallet),
                categories = listOf(makan, gaji, kos),
                subcategories = listOf(warung, restoran),
                transactions = listOf(
                    Transaction(
                        id = 1, occurredAt = Instant.parse("2026-08-16T11:47:34Z"), type = TxnType.EXPENSE,
                        amount = Money(187_000), accountId = 1, categoryId = 10, subcategoryId = 20,
                        note = "nasi padang", createdAt = created,
                    ),
                    Transaction(
                        id = 2, occurredAt = Instant.parse("2026-08-01T02:00:00Z"), type = TxnType.INCOME,
                        amount = Money(7_500_000), accountId = 1, categoryId = 11, createdAt = created,
                    ),
                    Transaction(
                        id = 3, occurredAt = Instant.parse("2026-07-20T04:30:00Z"), type = TxnType.TRANSFER,
                        amount = Money(1_000_000), accountId = 1, toAccountId = 2, createdAt = created,
                    ),
                    Transaction(
                        id = 4, occurredAt = Instant.parse("2026-09-30T12:00:00Z"), type = TxnType.ADJUSTMENT,
                        amount = Money(-12_000), accountId = 1, createdAt = created,
                    ),
                ),
            )
        }
    }
}
