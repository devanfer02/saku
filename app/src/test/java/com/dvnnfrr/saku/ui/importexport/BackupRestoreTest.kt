package com.dvnnfrr.saku.ui.importexport

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.data.repository.RoomAccountRepository
import com.dvnnfrr.saku.data.repository.RoomCategoryRepository
import com.dvnnfrr.saku.data.repository.RoomImportRepository
import com.dvnnfrr.saku.data.repository.RoomTransactionRepository
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.excel.AccountConflict
import com.dvnnfrr.saku.excel.AccountSetting
import com.dvnnfrr.saku.excel.BackupRefusal
import com.dvnnfrr.saku.excel.BackupSheets
import com.dvnnfrr.saku.excel.CellValue
import com.dvnnfrr.saku.excel.JAKARTA
import com.dvnnfrr.saku.excel.XlsxReader
import com.dvnnfrr.saku.excel.XlsxWriter
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.math.BigDecimal

/**
 * Backup and restore end to end, through both view models and two in-memory databases.
 *
 * Covers the scenarios in specs/full-backup/spec.md that need a database: the exact round
 * trip, restoring twice, a same-named account keeping its settings, undoing a restore,
 * rollback on a failed commit, picker cancel and the newer-format refusal.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRestoreTest {

    private val dispatcher = StandardTestDispatcher()
    private val opening = Instant.parse("2025-01-01T00:00:00Z")
    private val now = Instant.parse("2026-10-30T03:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }

    private lateinit var source: SakuDatabase
    private lateinit var destination: SakuDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        source = newDatabase()
        destination = newDatabase()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        source.close()
        destination.close()
    }

    // -- Round trip ---------------------------------------------------------------

    /**
     * Scenario: Round trip, and Restore onto an empty install. Every stored field compared,
     * except ids, creation timestamps and import batches. Ids differ between the two
     * databases, so references are compared by the name they resolve to.
     */
    @Test
    fun `backing up and restoring onto an empty database reproduces every field`() = runTest(dispatcher) {
        populate(source)
        val file = backUp(source)

        val model = restoreModel(destination)
        model.load(BytesSource(BACKUP_NAME, file))
        advanceUntilIdle()

        val preview = (model.state.value as RestoreUiState.Ready).preview
        assertEquals(4, preview.newAccounts)
        assertEquals(4, preview.newCategories)
        assertEquals(3, preview.newSubcategories)
        assertEquals(6, preview.newTransactions)
        assertEquals(0, preview.alreadyPresent)
        assertEquals(emptyList<AccountConflict>(), preview.conflicts)

        model.confirm()
        advanceUntilIdle()
        assertTrue(model.state.value is RestoreUiState.Restored)

        assertEquals(accountsOf(source), accountsOf(destination))
        assertEquals(categoriesOf(source), categoriesOf(destination))
        assertEquals(subcategoriesOf(source), subcategoriesOf(destination))
        assertEquals(transactionsOf(source), transactionsOf(destination))
    }

    @Test
    fun `the backup screen reports the record counts written`() = runTest(dispatcher) {
        populate(source)
        val model = backupModel(source)
        model.backUp(RecordingSink())
        advanceUntilIdle()

        assertEquals(
            BackupCounts(accounts = 4, categories = 4, subcategories = 3, transactions = 6),
            (model.state.value as BackupUiState.Written).counts,
        )
    }

    @Test
    fun `the suggested name is Saku backup and today's date`() {
        assertEquals("Saku backup 2026-10-30.xlsx", backupModel(source).suggestedFileName())
    }

    // -- Merging into data --------------------------------------------------------

    /** Scenario: Restoring the same backup twice. */
    @Test
    fun `a second restore of the same file previews nothing new`() = runTest(dispatcher) {
        populate(source)
        val file = backUp(source)
        restore(destination, file)

        val model = restoreModel(destination)
        model.load(BytesSource(BACKUP_NAME, file))
        advanceUntilIdle()

        val preview = (model.state.value as RestoreUiState.Ready).preview
        assertEquals(0, preview.newAccounts + preview.newCategories + preview.newSubcategories)
        assertEquals(0, preview.newTransactions)
        assertEquals(6, preview.alreadyPresent)
        assertFalse(preview.hasAnythingNew)

        model.confirm()
        advanceUntilIdle()
        assertTrue("confirm must do nothing when nothing is new", model.state.value is RestoreUiState.Ready)
        assertEquals(6, RoomTransactionRepository(destination).all().size)
    }

    /** Scenario: Restore onto a database with data. */
    @Test
    fun `a same-named account keeps its own settings and only missing records are added`() =
        runTest(dispatcher) {
            populate(source)
            val file = backUp(source)
            val accounts = RoomAccountRepository(destination)
            val bca = accounts.create("bca", AccountType.BANK, Money(42), opening, ColorSlot(0))

            val model = restoreModel(destination)
            model.load(BytesSource(BACKUP_NAME, file))
            advanceUntilIdle()

            val preview = (model.state.value as RestoreUiState.Ready).preview
            assertEquals(3, preview.newAccounts)
            val conflict = preview.conflicts.single()
            assertEquals("bca", conflict.name)
            assertTrue(AccountSetting.OPENING_BALANCE in conflict.settings)

            model.confirm()
            advanceUntilIdle()

            val after = accounts.all()
            assertEquals(4, after.size)
            val kept = after.single { it.id == bca }
            assertEquals(Money(42), kept.openingBalance)
            assertEquals(ColorSlot(0), kept.colorSlot)
            assertEquals(6, RoomTransactionRepository(destination).all().size)
        }

    /** Scenario: Undoing a restore. */
    @Test
    fun `reversing the restore's batch removes its transactions and keeps the records it created`() =
        runTest(dispatcher) {
            populate(source)
            val handEntered = RoomAccountRepository(destination)
                .create("Dompet", AccountType.CASH, Money.ZERO, opening)
            val categories = RoomCategoryRepository(destination)
            val jajan = categories.createCategory("Jajan", CategoryKind.EXPENSE)
            RoomTransactionRepository(destination).record(
                occurredAt = Instant.parse("2026-10-01T05:00:00Z"),
                type = TxnType.EXPENSE,
                amount = Money(5_000),
                accountId = handEntered,
                categoryId = jajan,
            )

            restore(destination, backUp(source))
            val imports = RoomImportRepository(destination)
            val batch = imports.observeBatches().first().single()
            assertEquals(6, batch.insertedCount)

            assertEquals(6, imports.reverse(batch.id, now))

            val left = RoomTransactionRepository(destination).all()
            assertEquals(listOf(Money(5_000)), left.map { it.amount })
            assertEquals(5, RoomAccountRepository(destination).all().size)
        }

    /** Scenario: Failure part-way. Nothing from the restore remains. */
    @Test
    fun `a commit that fails part-way leaves no record of the restore`() = runTest(dispatcher) {
        populate(source)
        val file = backUp(source)
        // Same name, later opening date: restore keeps it, and the backup's transfers out
        // of BCA now predate its opening, which the commit refuses after creating records.
        RoomAccountRepository(destination)
            .create("BCA", AccountType.BANK, Money.ZERO, Instant.parse("2026-10-01T00:00:00Z"))
        val accountsBefore = destination.accountDao().all()
        val categoriesBefore = destination.categoryDao().all()
        val subcategoriesBefore = destination.subcategoryDao().all()

        val model = restoreModel(destination)
        model.load(BytesSource(BACKUP_NAME, file))
        advanceUntilIdle()
        model.confirm()
        advanceUntilIdle()

        val state = model.state.value as RestoreUiState.Ready
        assertNotNull(state.failure)
        assertFalse(state.committing)
        assertEquals(accountsBefore, destination.accountDao().all())
        assertEquals(categoriesBefore, destination.categoryDao().all())
        assertEquals(subcategoriesBefore, destination.subcategoryDao().all())
        assertEquals(0, destination.txnDao().all().size)
        assertEquals(emptyList<Any>(), RoomImportRepository(destination).observeBatches().first())
    }

    // -- View model states --------------------------------------------------------

    /** Scenario: Picker cancelled. Nothing written, no error shown. */
    @Test
    fun `a cancelled picker leaves both screens idle`() = runTest(dispatcher) {
        val backup = backupModel(source)
        backup.backUp(null)
        advanceUntilIdle()
        assertEquals(BackupUiState.Idle, backup.state.value)

        val restore = restoreModel(destination)
        restore.load(null)
        advanceUntilIdle()
        assertEquals(RestoreUiState.Idle, restore.state.value)
    }

    @Test
    fun `restore reads, then previews, and writes nothing until confirmed`() = runTest(dispatcher) {
        populate(source)
        val file = backUp(source)
        val model = restoreModel(destination)

        model.load(BytesSource(BACKUP_NAME, file))
        assertEquals(RestoreUiState.Reading(BACKUP_NAME), model.state.value)

        advanceUntilIdle()
        val ready = model.state.value as RestoreUiState.Ready
        assertEquals(BACKUP_NAME, ready.preview.fileName)
        assertEquals(1, ready.preview.formatVersion)
        assertEquals("2026-10-30", ready.preview.writtenOn.toString())
        assertEquals(0, destination.accountDao().all().size)
        assertEquals(0, destination.txnDao().all().size)

        model.discard()
        assertEquals(RestoreUiState.Idle, model.state.value)
        assertEquals(0, destination.accountDao().all().size)
    }

    /** Scenario: Backup from a newer app. Nothing parsed or written; the screen says why. */
    @Test
    fun `a newer backup is refused with nothing written`() = runTest(dispatcher) {
        populate(source)
        val sheets = XlsxReader.readAll(ByteArrayInputStream(backUp(source))).map { table ->
            if (table.sheetName != BackupSheets.BACKUP) return@map table
            table.copy(
                rows = table.rows.map { row ->
                    if (row.textAt(0) != "Format version") row
                    else row.copy(cells = listOf(row[0], CellValue.Number(BigDecimal(2))))
                },
            )
        }
        val newer = ByteArrayOutputStream().also { XlsxWriter.write(sheets, it) }.toByteArray()

        val model = restoreModel(destination)
        model.load(BytesSource(BACKUP_NAME, newer))
        advanceUntilIdle()

        assertEquals(
            RestoreUiState.Refused(
                BACKUP_NAME,
                RestoreRefusal.Backup(BackupRefusal.NewerFormat(formatVersion = 2, appVersion = "1.0")),
            ),
            model.state.value,
        )
        assertEquals(0, destination.accountDao().all().size)
    }

    @Test
    fun `a file that is not a spreadsheet is refused as unreadable`() = runTest(dispatcher) {
        val model = restoreModel(destination)
        model.load(BytesSource(BACKUP_NAME, "not a workbook".toByteArray()))
        advanceUntilIdle()

        val refused = model.state.value as RestoreUiState.Refused
        assertTrue(refused.refusal is RestoreRefusal.Unreadable)
    }

    // -- Helpers ------------------------------------------------------------------

    private fun newDatabase(): SakuDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        SakuDatabase::class.java,
    )
        .setQueryExecutor(dispatcher.asExecutor())
        .setTransactionExecutor(dispatcher.asExecutor())
        .allowMainThreadQueries()
        .build()

    private fun backupModel(db: SakuDatabase) = BackupViewModel(
        imports = RoomImportRepository(db),
        appVersion = "1.0",
        zone = JAKARTA,
        clock = clock,
        io = dispatcher,
    )

    private fun restoreModel(db: SakuDatabase) = RestoreViewModel(
        imports = RoomImportRepository(db),
        accounts = RoomAccountRepository(db),
        categories = RoomCategoryRepository(db),
        zone = JAKARTA,
        clock = clock,
        io = dispatcher,
    )

    private fun TestScope.backUp(db: SakuDatabase): ByteArray {
        val sink = RecordingSink()
        backupModel(db).backUp(sink)
        advanceUntilIdle()
        return sink.bytes()
    }

    private fun TestScope.restore(db: SakuDatabase, file: ByteArray) {
        val model = restoreModel(db)
        model.load(BytesSource(BACKUP_NAME, file))
        advanceUntilIdle()
        model.confirm()
        advanceUntilIdle()
        check(model.state.value is RestoreUiState.Restored) { "restore failed: ${model.state.value}" }
    }

    /**
     * Something of every kind a backup has to carry: an opening balance, a reconcile and its
     * adjustment, an archived account, category and subcategory, an income category, a
     * transfer, and a note.
     */
    private suspend fun populate(db: SakuDatabase) {
        val accounts = RoomAccountRepository(db)
        val categories = RoomCategoryRepository(db)
        val transactions = RoomTransactionRepository(db)

        val bca = accounts.create("BCA", AccountType.BANK, Money(2_500_000), opening, ColorSlot(5))
        val cash = accounts.create("Cash", AccountType.CASH, Money(150_000), opening)
        val rdpu = accounts.create("RDPU SucorInvest", AccountType.INVESTMENT, Money.ZERO, opening)
        val wallet = accounts.create("Old wallet", AccountType.CASH, Money(10_000), opening)

        val makan = categories.createCategory("Makan & Minum", CategoryKind.EXPENSE)
        val warung = categories.findOrCreateSubcategory(makan, "Warung")
        val restoran = categories.findOrCreateSubcategory(makan, "Restoran")
        val pemberian = categories.createCategory("Pemberian", CategoryKind.EXPENSE)
        categories.findOrCreateSubcategory(pemberian, "Arisan")
        val gaji = categories.createCategory("Gaji", CategoryKind.INCOME)
        val kos = categories.createCategory("Kos lama", CategoryKind.EXPENSE)

        transactions.record(
            occurredAt = Instant.parse("2026-08-16T11:47:34Z"),
            type = TxnType.EXPENSE,
            amount = Money(187_000),
            accountId = bca,
            categoryId = makan,
            subcategoryId = warung,
            note = "nasi padang",
        )
        transactions.record(
            occurredAt = Instant.parse("2026-08-01T02:00:00Z"),
            type = TxnType.INCOME,
            amount = Money(7_500_000),
            accountId = bca,
            categoryId = gaji,
        )
        transactions.record(
            occurredAt = Instant.parse("2026-07-20T04:30:00Z"),
            type = TxnType.TRANSFER,
            amount = Money(1_000_000),
            accountId = bca,
            toAccountId = rdpu,
        )
        transactions.record(
            occurredAt = Instant.parse("2026-06-05T06:00:00Z"),
            type = TxnType.EXPENSE,
            amount = Money(12_000),
            accountId = wallet,
            categoryId = kos,
            subcategoryId = null,
        )
        transactions.record(
            occurredAt = Instant.parse("2026-06-06T06:00:00Z"),
            type = TxnType.EXPENSE,
            amount = Money(80_000),
            accountId = cash,
            categoryId = makan,
            subcategoryId = restoran,
        )
        // Writes an ADJUSTMENT and stamps the reconcile date.
        checkNotNull(accounts.reconcile(cash, Money(50_000), Instant.parse("2026-09-30T12:00:00Z")))

        db.subcategoryDao().setArchived(restoran, true)
        categories.setCategoryArchived(kos, true)
        accounts.setArchived(wallet, true)
    }

    /** Every stored account field but the id; balance is derived and compared as a check. */
    private suspend fun accountsOf(db: SakuDatabase) =
        RoomAccountRepository(db).all().map { it.copy(id = 0) }.sortedBy { it.name }

    private suspend fun categoriesOf(db: SakuDatabase) =
        RoomCategoryRepository(db).allCategories().map { it.copy(id = 0) }.sortedBy { it.name }

    private suspend fun subcategoriesOf(db: SakuDatabase): List<Any> {
        val categories = RoomCategoryRepository(db)
        val parents = categories.allCategories().associate { it.id to it.name }
        return categories.allSubcategories()
            .map { parents.getValue(it.categoryId) to it.copy(id = 0, categoryId = 0) }
            .sortedBy { it.toString() }
    }

    /** By name rather than id, with createdAt and importBatchId cleared. */
    private suspend fun transactionsOf(db: SakuDatabase): List<Any> {
        val accounts = RoomAccountRepository(db).all().associate { it.id to it.name }
        val categories = RoomCategoryRepository(db)
        val categoryNames = categories.allCategories().associate { it.id to it.name }
        val subcategoryNames = categories.allSubcategories().associate { it.id to it.name }
        return RoomTransactionRepository(db).all().map { txn ->
            listOf(
                txn.occurredAt,
                txn.type,
                txn.amount,
                accounts[txn.accountId],
                txn.toAccountId?.let(accounts::get),
                txn.categoryId?.let(categoryNames::get),
                txn.subcategoryId?.let(subcategoryNames::get),
                txn.note,
            )
        }.sortedBy { it.toString() }
    }

    private class RecordingSink : DocumentSink {
        override val name = BACKUP_NAME
        private val buffer = ByteArrayOutputStream()

        override fun openOutput(): OutputStream = buffer

        fun bytes(): ByteArray = buffer.toByteArray()
    }

    private class BytesSource(override val name: String, private val bytes: ByteArray) : DocumentSource {
        override fun openInput(): InputStream = ByteArrayInputStream(bytes)
    }

    private companion object {
        const val BACKUP_NAME = "Saku backup 2026-10-30.xlsx"
    }
}
