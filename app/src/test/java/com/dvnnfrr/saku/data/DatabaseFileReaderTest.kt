package com.dvnnfrr.saku.data

import android.database.sqlite.SQLiteException
import com.dvnnfrr.saku.data.local.DatabaseFileReader
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.excel.BackupContents
import com.dvnnfrr.saku.excel.DatabaseReadOutcome
import com.dvnnfrr.saku.excel.DatabaseRefusal
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The read-only reader against files built from Room's exported schema, as the owner's
 * script builds them: the header check, and every row adapted to the restore's records.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseFileReaderTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun newFile(): File = File(folder.root, "import-${System.nanoTime()}.db")

    private fun read(file: File): BackupContents =
        (DatabaseFileReader.read(file) as DatabaseReadOutcome.Read).contents

    private fun refusalOf(file: File): DatabaseRefusal =
        (DatabaseFileReader.read(file) as DatabaseReadOutcome.Refused).refusal

    // -- Header ---------------------------------------------------------------------

    @Test
    fun `a file for another schema version is refused`() {
        val versioned = SchemaDatabaseFile(newFile()).createSchema(userVersion = SakuDatabase.VERSION + 1).close()

        assertEquals(
            DatabaseRefusal.DifferentVersion(found = SakuDatabase.VERSION + 1, expected = SakuDatabase.VERSION),
            refusalOf(versioned),
        )
    }

    @Test
    fun `the right version over a different identity hash is refused as another layout`() {
        val file = SchemaDatabaseFile(newFile()).createSchema(identityHash = "0".repeat(32)).close()

        assertEquals(DatabaseRefusal.DifferentLayout, refusalOf(file))
    }

    @Test
    fun `a file Room never wrote is not a Saku database`() {
        val file = SchemaDatabaseFile(newFile()).createSchema(withMasterTable = false).close()

        assertEquals(DatabaseRefusal.NotSaku, refusalOf(file))
    }

    @Test(expected = SQLiteException::class)
    fun `a file that is not SQLite throws for the caller to report as unreadable`() {
        val file = newFile().apply { writeText("not a database, just text that is long enough to matter") }
        DatabaseFileReader.read(file)
    }

    @Test
    fun `reading leaves the file byte for byte as it was`() {
        val file = sampleDatabaseFile(newFile())
        val before = file.readBytes()

        read(file)

        assertTrue(before.contentEquals(file.readBytes()))
    }

    // -- Rows -----------------------------------------------------------------------

    @Test
    fun `accounts carry their opening balances, types, colours and reconcile dates`() {
        val accounts = read(sampleDatabaseFile(newFile())).accounts.associateBy { it.name }

        val bca = accounts.getValue("BCA")
        assertEquals(AccountType.BANK, bca.type)
        assertEquals(Money(2_500_000), bca.openingBalance)
        assertEquals(Instant.fromEpochSeconds(0), bca.openingAsOf)
        assertEquals(ColorSlot(1), bca.colorSlot)
        assertNull(bca.lastReconciled)

        val rdpu = accounts.getValue("RDPU SucorInvest")
        assertEquals(AccountType.INVESTMENT, rdpu.type)
        assertEquals(Instant.fromEpochSeconds(1_780_000_000), rdpu.lastReconciled)
    }

    @Test
    fun `archived categories of both kinds stay archived`() {
        val categories = read(sampleDatabaseFile(newFile())).categories.associateBy { it.name }

        assertTrue(categories.getValue("Kos lama").archived)
        assertEquals(CategoryKind.EXPENSE, categories.getValue("Kos lama").kind)
        assertTrue(categories.getValue("Hadiah").archived)
        assertEquals(CategoryKind.INCOME, categories.getValue("Hadiah").kind)
        assertEquals(false, categories.getValue("Makan & Minum").archived)
    }

    @Test
    fun `a transfer keeps both accounts and an adjustment keeps its sign`() {
        val rows = read(sampleDatabaseFile(newFile())).transactions.rows

        val transfer = rows.single { it.type == TxnType.TRANSFER }
        assertEquals("BCA", transfer.accountName)
        assertEquals("RDPU SucorInvest", transfer.toAccountName)
        assertNull(transfer.categoryName)

        val adjustment = rows.single { it.type == TxnType.ADJUSTMENT }
        assertEquals(Money(-25_000), adjustment.amount)
        assertEquals("Cash", adjustment.accountName)
    }

    @Test
    fun `transactions name their category and keep their note and instant`() {
        val first = read(sampleDatabaseFile(newFile())).transactions.rows.first()

        assertEquals(Instant.fromEpochSeconds(SAMPLE_FIRST_EPOCH_SEC), first.occurredAt)
        assertEquals("Makan & Minum", first.categoryName)
        assertEquals("nasi padang", first.note)
        assertEquals(Money(50_000), first.amount)
    }

    @Test
    fun `a subcategory is named under its parent`() {
        val file = SchemaDatabaseFile(newFile()).createSchema()
            .account(1, "Cash")
            .category(1, "Pemberian")
            .subcategory(1, categoryId = 1, name = "Arisan")
            .txn(1, SAMPLE_FIRST_EPOCH_SEC, "EXPENSE", 500_000, accountId = 1, categoryId = 1, subcategoryId = 1)
            .close()

        val contents = read(file)
        assertEquals("Pemberian", contents.subcategories.single().categoryName)
        assertEquals("Arisan", contents.transactions.rows.single().subcategoryName)
    }

    @Test
    fun `a transaction pointing at a missing account is refused`() {
        val file = SchemaDatabaseFile(newFile()).createSchema()
            .account(1, "Cash")
            .category(1, "Makan")
            .txn(7, SAMPLE_FIRST_EPOCH_SEC, "EXPENSE", 1_000, accountId = 9, categoryId = 1)
            .close()

        assertEquals(DatabaseRefusal.MissingReference("txn", 7, "accountId"), refusalOf(file))
    }

    @Test
    fun `an unknown transaction type is refused`() {
        val file = SchemaDatabaseFile(newFile()).createSchema()
            .account(1, "Cash")
            .txn(3, SAMPLE_FIRST_EPOCH_SEC, "Transfer-Out", 1_000, accountId = 1)
            .close()

        assertEquals(DatabaseRefusal.InvalidValue("txn", 3, "type"), refusalOf(file))
    }
}
