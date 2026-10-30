package com.dvnnfrr.saku.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dvnnfrr.saku.data.local.DatabaseFileReader
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.data.repository.PendingRow
import com.dvnnfrr.saku.data.repository.RoomAccountRepository
import com.dvnnfrr.saku.data.repository.RoomCategoryRepository
import com.dvnnfrr.saku.data.repository.RoomImportRepository
import com.dvnnfrr.saku.data.repository.RoomTagRepository
import com.dvnnfrr.saku.data.repository.RoomTransactionRepository
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.excel.BackupReadOutcome
import com.dvnnfrr.saku.excel.BackupSheets
import com.dvnnfrr.saku.excel.DatabaseReadOutcome
import com.dvnnfrr.saku.excel.XlsxReader
import com.dvnnfrr.saku.excel.XlsxWriter
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/** Tags through a real backup file, restore, Excel import and a schema 1 database file. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TagRestoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val zone = TimeZone.of("Asia/Jakarta")
    private val now = Instant.parse("2026-10-31T03:00:00Z")
    private lateinit var source: SakuDatabase
    private lateinit var destination: SakuDatabase

    private fun newDatabase() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SakuDatabase::class.java)
        .allowMainThreadQueries().build()

    @Before
    fun setUp() {
        source = newDatabase()
        destination = newDatabase()
    }

    @After
    fun tearDown() {
        source.close()
        destination.close()
    }

    /** Two tagged rows, one untagged, and an archived tag. Returns the row ids. */
    private suspend fun populate(db: SakuDatabase): List<Long> {
        val bca = RoomAccountRepository(db).create("BCA", AccountType.BANK, Money.ZERO, Instant.fromEpochSeconds(0))
        val makan = RoomCategoryRepository(db).createCategory("Makan & Minum", CategoryKind.EXPENSE)
        val tags = RoomTagRepository(db)
        val grocery = tags.findOrCreate("Monthly grocery")
        val bali = tags.findOrCreate("Trip Bali")
        tags.setArchived(bali, true)
        val txns = RoomTransactionRepository(db)
        fun at(day: Int) = Instant.parse("2026-10-${day.toString().padStart(2, '0')}T05:00:00Z")
        return listOf(
            txns.record(at(1), TxnType.EXPENSE, Money(120_000), bca, categoryId = makan, tagIds = setOf(grocery, bali)),
            txns.record(at(2), TxnType.EXPENSE, Money(30_000), bca, categoryId = makan, tagIds = setOf(grocery)),
            txns.record(at(3), TxnType.EXPENSE, Money(50_000), bca, categoryId = makan),
        )
    }

    private suspend fun backup(db: SakuDatabase): ByteArray {
        val sheets = BackupSheets.build(RoomImportRepository(db).backupSnapshot(), "1.0", now, zone)
        return ByteArrayOutputStream().also { XlsxWriter.write(sheets, it) }.toByteArray()
    }

    private suspend fun restore(db: SakuDatabase, file: ByteArray) {
        val contents = (BackupSheets.parse(XlsxReader.readAll(ByteArrayInputStream(file))) as BackupReadOutcome.Read).contents
        RoomImportRepository(db).restore(contents, "backup.xlsx", now)
    }

    /** Tag names per row amount, which is unique in [populate]. */
    private suspend fun tagsByAmount(db: SakuDatabase): Map<Long, Set<String>> {
        val names = RoomTagRepository(db).all().associate { it.id to it.name }
        return RoomTransactionRepository(db).all().associate { row -> row.amount.rupiah to row.tagIds.map { names.getValue(it) }.toSet() }
    }

    @Test
    fun `backup and restore onto an empty database keeps every tag and the archived flag`() = runTest {
        populate(source)
        restore(destination, backup(source))

        assertEquals(tagsByAmount(source), tagsByAmount(destination))
        assertEquals(
            listOf("Monthly grocery" to false, "Trip Bali" to true),
            RoomTagRepository(destination).all().map { it.name to it.archived },
        )
    }

    @Test
    fun `restore never adds tags to a row that already exists`() = runTest {
        val ids = populate(source)
        restore(destination, backup(source))

        // Tag the untagged row in the source and back up again.
        val ramadan = RoomTagRepository(source).findOrCreate("Ramadan")
        val txns = RoomTransactionRepository(source)
        txns.update(txns.byId(ids[2])!!.copy(tagIds = setOf(ramadan)))
        restore(destination, backup(source))

        assertEquals(emptySet<String>(), tagsByAmount(destination).getValue(50_000))
        assertTrue("the missing tag itself is created", RoomTagRepository(destination).all().any { it.name == "Ramadan" })
    }

    @Test
    fun `an import commit creates missing tags by name and reuses existing ones in any case`() = runTest {
        val bca = RoomAccountRepository(destination).create("BCA", AccountType.BANK, Money.ZERO, Instant.fromEpochSeconds(0))
        val makan = RoomCategoryRepository(destination).createCategory("Makan & Minum", CategoryKind.EXPENSE)
        val grocery = RoomTagRepository(destination).findOrCreate("Monthly grocery")

        fun row(amount: Long, tags: List<String>) = PendingRow(
            Instant.parse("2026-10-05T05:00:00Z"), TxnType.EXPENSE, Money(amount), bca, null, makan, null, null,
            dedupHash = "row-$amount", tagNames = tags,
        )
        RoomImportRepository(destination).commit("x.xlsx", listOf(row(1, listOf("monthly grocery", "Trip Bali")), row(2, emptyList())), 2, now)

        val tags = RoomTagRepository(destination).all()
        assertEquals(listOf("Monthly grocery", "Trip Bali"), tags.map { it.name })
        assertEquals(mapOf(1L to setOf("Monthly grocery", "Trip Bali"), 2L to emptySet<String>()), tagsByAmount(destination))
        assertEquals(grocery, tags.first().id)
    }

    @Test
    fun `a schema 1 database file imports with no tags`() = runTest {
        val file = sampleDatabaseFile(File(folder.root, "v1.db"), schemaVersion = 1)
        val contents = (DatabaseFileReader.read(file) as DatabaseReadOutcome.Read).contents
        assertEquals(5, contents.transactions.rows.size)

        RoomImportRepository(destination).restore(contents, "v1.db", now)

        assertEquals(5, RoomTransactionRepository(destination).all().size)
        assertTrue(RoomTagRepository(destination).all().isEmpty())
    }

    @Test
    fun `a schema 2 database file brings its tags`() = runTest {
        val file = SchemaDatabaseFile(File(folder.root, "v2.db")).createSchema()
            .account(1, "BCA")
            .category(1, "Makan & Minum")
            .tag(1, "Trip Bali", archived = true)
            .txn(1, SAMPLE_FIRST_EPOCH_SEC, "EXPENSE", 50_000, accountId = 1, categoryId = 1)
            .txnTag(1, 1)
            .close()
        val contents = (DatabaseFileReader.read(file) as DatabaseReadOutcome.Read).contents

        RoomImportRepository(destination).restore(contents, "v2.db", now)

        assertEquals(mapOf(50_000L to setOf("Trip Bali")), tagsByAmount(destination))
        assertTrue(RoomTagRepository(destination).all().single().archived)
    }
}
