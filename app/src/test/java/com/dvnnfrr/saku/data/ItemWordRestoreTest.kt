package com.dvnnfrr.saku.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.data.repository.RoomAccountRepository
import com.dvnnfrr.saku.data.repository.RoomCategoryRepository
import com.dvnnfrr.saku.data.repository.RoomImportRepository
import com.dvnnfrr.saku.data.repository.RoomTransactionRepository
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.excel.BackupReadOutcome
import com.dvnnfrr.saku.excel.BackupSheets
import com.dvnnfrr.saku.data.repository.NewTransaction
import com.dvnnfrr.saku.receipt.LearnedWords
import kotlinx.coroutines.flow.first
import com.dvnnfrr.saku.excel.XlsxReader
import com.dvnnfrr.saku.excel.DatabaseReadOutcome
import com.dvnnfrr.saku.data.local.DatabaseFileReader
import java.io.File
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

/** Learned item words: saved with receipt rows, overwritten by token, and carried by backup and restore. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ItemWordRestoreTest {

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

    private suspend fun backup(db: SakuDatabase): ByteArray {
        val sheets = BackupSheets.build(RoomImportRepository(db).backupSnapshot(), "1.0", now, zone)
        return ByteArrayOutputStream().also { XlsxWriter.write(sheets, it) }.toByteArray()
    }

    private suspend fun restore(db: SakuDatabase, file: ByteArray) {
        val contents = (BackupSheets.parse(XlsxReader.readAll(ByteArrayInputStream(file))) as BackupReadOutcome.Read).contents
        RoomImportRepository(db).restore(contents, "backup.xlsx", now)
    }

    private suspend fun learn(db: SakuDatabase, words: LearnedWords) {
        val card = RoomAccountRepository(db).create("Card", AccountType.BANK, Money.ZERO, Instant.fromEpochSeconds(0))
        val makan = RoomCategoryRepository(db).createCategory("Makan & Minum", CategoryKind.EXPENSE)
        RoomTransactionRepository(db).saveAll(
            listOf(NewTransaction(now, TxnType.EXPENSE, Money(72_500), card, makan, "Buris Premium 5KG")),
            words = words,
        )
    }

    private val taught = LearnedWords(
        words = mapOf("BRS" to "Buris"),
        names = mapOf("IDM F/T 2PLY 3X180'S" to "Tisu Indomaret"),
    )

    @Test
    fun `learned words are saved with the rows and a later edit overwrites by token`() = runTest {
        learn(source, taught)
        val repo = RoomTransactionRepository(source)
        assertEquals(taught, repo.observeLearnedWords().first())

        repo.saveAll(emptyList(), words = LearnedWords(words = mapOf("BRS" to "Beras")))
        assertEquals(mapOf("BRS" to "Beras"), repo.observeLearnedWords().first().words)
    }

    @Test
    fun `a backup carries the Words sheet and restore brings the words back`() = runTest {
        learn(source, taught)
        restore(destination, backup(source))

        assertEquals(taught, RoomTransactionRepository(destination).observeLearnedWords().first())
    }

    @Test
    fun `restore never overwrites a word the database already has`() = runTest {
        learn(source, taught)
        RoomTransactionRepository(destination).saveAll(emptyList(), words = LearnedWords(words = mapOf("BRS" to "Beras")))
        restore(destination, backup(source))

        val words = RoomTransactionRepository(destination).observeLearnedWords().first()
        assertEquals("Beras", words.words["BRS"])
        assertEquals("Tisu Indomaret", words.names["IDM F/T 2PLY 3X180'S"])
    }

    @Test
    fun `a backup written before learned words still restores`() = runTest {
        learn(source, taught)
        val sheets = BackupSheets.build(RoomImportRepository(source).backupSnapshot(), "1.0", now, zone)
            .filterNot { it.sheetName == BackupSheets.WORDS }
        val contents = (BackupSheets.parse(sheets) as BackupReadOutcome.Read).contents

        assertTrue(contents.words.isEmpty)
    }

    @Test
    fun `a database file brings its learned words`() = runTest {
        val file = SchemaDatabaseFile(File(folder.root, "v2.db")).createSchema()
            .account(1, "BCA")
            .itemWord("BRS", "Buris")
            .itemWord("IDM F/T 2PLY 3X180'S", "Tisu Indomaret", wholeName = true)
            .close()
        val contents = (DatabaseFileReader.read(file) as DatabaseReadOutcome.Read).contents

        RoomImportRepository(destination).restore(contents, "v2.db", now)

        assertEquals(taught, RoomTransactionRepository(destination).observeLearnedWords().first())
    }
}
