package com.dvnnfrr.saku.ui.importexport

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dvnnfrr.saku.data.SchemaDatabaseFile
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.data.repository.RoomAccountRepository
import com.dvnnfrr.saku.data.repository.RoomCategoryRepository
import com.dvnnfrr.saku.data.repository.RoomImportRepository
import com.dvnnfrr.saku.data.repository.RoomTransactionRepository
import com.dvnnfrr.saku.data.sampleDatabaseFile
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.excel.AccountSetting
import com.dvnnfrr.saku.excel.DatabaseRefusal
import com.dvnnfrr.saku.excel.JAKARTA
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

/**
 * The database import end to end: a file built from the exported schema, the view model,
 * and an in-memory database seeded as a fresh install is (Cash and Card at the epoch).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseImportViewModelTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val epoch = Instant.fromEpochSeconds(0)
    private val now = Instant.parse("2026-10-30T03:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }

    private lateinit var db: SakuDatabase
    private lateinit var scratch: File

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SakuDatabase::class.java)
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .allowMainThreadQueries()
            .build()
        scratch = folder.newFolder("cache")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun `cancelling the picker leaves the screen as it was`() {
        val model = model()
        model.load(null)

        assertEquals(DatabaseImportUiState.Idle, model.state.value)
    }

    @Test
    fun `a file for another schema version is refused and nothing is written`() = runTest(dispatcher) {
        seedFreshInstall()
        val file = SchemaDatabaseFile(newDbFile())
            .createSchema(userVersion = SakuDatabase.VERSION + 1)
            .close()

        val model = model()
        model.load(FileSource(file))
        advanceUntilIdle()

        val refused = model.state.value as DatabaseImportUiState.Refused
        assertEquals(
            DatabaseImportRefusal.Database(DatabaseRefusal.DifferentVersion(SakuDatabase.VERSION + 1, SakuDatabase.VERSION)),
            refused.refusal,
        )
        assertEquals(2, RoomAccountRepository(db).all().size)
    }

    @Test
    fun `a file that is not a database is unreadable and its copy is removed`() = runTest(dispatcher) {
        val model = model()
        model.load(BytesSource("notes.db", "this is not SQLite".toByteArray()))
        advanceUntilIdle()

        val refused = model.state.value as DatabaseImportUiState.Refused
        assertTrue(refused.refusal is DatabaseImportRefusal.Unreadable)
        assertEquals(emptyList<String>(), scratch.list()!!.toList())
    }

    @Test
    fun `the preview counts the file and what it would add on a fresh install`() = runTest(dispatcher) {
        seedFreshInstall()
        val model = model()
        model.load(FileSource(sampleDatabaseFile(newDbFile())))
        advanceUntilIdle()

        val preview = (model.state.value as DatabaseImportUiState.Ready).preview
        assertEquals(3, preview.accountsInFile)
        assertEquals(4, preview.categoriesInFile)
        assertEquals(5, preview.transactionsInFile)
        assertEquals(LocalDate(2026, 1, 5), preview.firstDate)
        assertEquals(LocalDate(2026, 2, 14), preview.lastDate)
        // Cash merges into the seeded Cash; BCA and RDPU are new.
        assertEquals(2, preview.newAccounts)
        assertEquals(4, preview.newCategories)
        assertEquals(5, preview.newTransactions)
        assertEquals(0, preview.alreadyPresent)
        val cash = preview.conflicts.single()
        assertEquals("Cash", cash.name)
        assertTrue(AccountSetting.OPENING_BALANCE in cash.settings)
        assertTrue(preview.hasAnythingNew)
        assertEquals("the copy is removed once read", emptyList<String>(), scratch.list()!!.toList())
    }

    @Test
    fun `confirming imports one batch that carries archived flags, transfers and adjustments`() =
        runTest(dispatcher) {
            seedFreshInstall()
            import(sampleDatabaseFile(newDbFile()))

            val accounts = RoomAccountRepository(db).all().associateBy { it.name }
            assertEquals(setOf("Cash", "Card", "BCA", "RDPU SucorInvest"), accounts.keys)
            assertEquals("the seeded Cash keeps its own opening balance", Money.ZERO, accounts.getValue("Cash").openingBalance)
            assertEquals(Money(2_500_000), accounts.getValue("BCA").openingBalance)
            assertEquals(AccountType.INVESTMENT, accounts.getValue("RDPU SucorInvest").type)

            val categories = RoomCategoryRepository(db).allCategories().associateBy { it.name }
            assertTrue(categories.getValue("Kos lama").archived)
            assertTrue(categories.getValue("Hadiah").archived)
            assertFalse(categories.getValue("Gaji").archived)

            val txns = RoomTransactionRepository(db).all()
            assertEquals(5, txns.size)
            val transfer = txns.single { it.type == TxnType.TRANSFER }
            assertEquals(accounts.getValue("BCA").id, transfer.accountId)
            assertEquals(accounts.getValue("RDPU SucorInvest").id, transfer.toAccountId)
            val adjustment = txns.single { it.type == TxnType.ADJUSTMENT }
            assertEquals(Money(-25_000), adjustment.amount)
            assertEquals(accounts.getValue("Cash").id, adjustment.accountId)

            val batch = RoomImportRepository(db).observeBatches().first().single()
            assertEquals(SAMPLE_NAME, batch.fileName)
            assertEquals(5, batch.insertedCount)
        }

    @Test
    fun `a second import of the same file previews nothing new and confirm does nothing`() =
        runTest(dispatcher) {
            seedFreshInstall()
            val file = sampleDatabaseFile(newDbFile())
            import(file)

            val model = model()
            model.load(FileSource(file))
            advanceUntilIdle()

            val preview = (model.state.value as DatabaseImportUiState.Ready).preview
            assertEquals(0, preview.newAccounts + preview.newCategories + preview.newTransactions)
            assertEquals(5, preview.alreadyPresent)
            assertFalse(preview.hasAnythingNew)

            model.confirm()
            advanceUntilIdle()
            assertTrue(model.state.value is DatabaseImportUiState.Ready)
            assertEquals(5, RoomTransactionRepository(db).all().size)
            assertEquals(1, RoomImportRepository(db).observeBatches().first().size)
        }

    @Test
    fun `reversing the batch in Import history removes only its transactions`() = runTest(dispatcher) {
        seedFreshInstall()
        import(sampleDatabaseFile(newDbFile()))
        val imports = RoomImportRepository(db)
        val batch = imports.observeBatches().first().single()

        assertEquals(5, imports.reverse(batch.id, now))
        assertEquals(0, RoomTransactionRepository(db).all().size)
        assertEquals(4, RoomAccountRepository(db).all().size)
    }

    @Test
    fun `discarding the preview writes nothing`() = runTest(dispatcher) {
        seedFreshInstall()
        val model = model()
        model.load(FileSource(sampleDatabaseFile(newDbFile())))
        advanceUntilIdle()

        model.discard()
        model.confirm()
        advanceUntilIdle()

        assertEquals(DatabaseImportUiState.Idle, model.state.value)
        assertEquals(0, RoomTransactionRepository(db).all().size)
        assertEquals(2, RoomAccountRepository(db).all().size)
    }

    // -- Helpers ------------------------------------------------------------------

    private fun model() = DatabaseImportViewModel(
        imports = RoomImportRepository(db),
        accounts = RoomAccountRepository(db),
        categories = RoomCategoryRepository(db),
        scratchDir = scratch,
        zone = JAKARTA,
        clock = clock,
        io = dispatcher,
    )

    /** What first run leaves: Cash and Card, opening nothing at the epoch, no categories. */
    private suspend fun seedFreshInstall() {
        val accounts = RoomAccountRepository(db)
        accounts.create("Cash", AccountType.CASH, Money.ZERO, epoch)
        accounts.create("Card", AccountType.BANK, Money.ZERO, epoch)
    }

    private fun TestScope.import(file: File) {
        val model = model()
        model.load(FileSource(file))
        advanceUntilIdle()
        model.confirm()
        advanceUntilIdle()
        check(model.state.value is DatabaseImportUiState.Imported) { "import failed: ${model.state.value}" }
    }

    private fun newDbFile(): File = File(folder.root, "sample-${System.nanoTime()}.db")

    private class FileSource(private val file: File) : DocumentSource {
        override val name = SAMPLE_NAME

        override fun openInput(): InputStream = file.inputStream()
    }

    private class BytesSource(override val name: String, private val bytes: ByteArray) : DocumentSource {
        override fun openInput(): InputStream = ByteArrayInputStream(bytes)
    }

    private companion object {
        const val SAMPLE_NAME = "saku-sample.db"
    }
}
