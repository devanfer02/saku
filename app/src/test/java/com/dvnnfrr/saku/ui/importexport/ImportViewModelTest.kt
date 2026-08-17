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
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.excel.Fixture
import com.dvnnfrr.saku.excel.JAKARTA
import com.dvnnfrr.saku.excel.SheetLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.io.File
import java.io.InputStream

/**
 * The import flow end to end, against the real 1,200-row export and a real database.
 *
 * Covers the scenarios in specs/excel-interchange/spec.md that need both halves: the
 * preview writing nothing, the mapping steps blocking the action, the six investment
 * transfers acquiring a destination, and re-importing the same file inserting zero.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: SakuDatabase

    /** Well before the export's earliest row, so historical transfers are in scope. */
    private val opening = Instant.parse("2025-01-01T00:00:00Z")
    private val now = Instant.parse("2026-08-17T03:00:00Z")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // Room runs queries on its own executor and its Flows emit from there, so
        // advanceUntilIdle() would return while the import was still reading. Pinning
        // both executors to the test dispatcher makes the whole flow deterministic.
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SakuDatabase::class.java,
        )
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    // -- Preview ----------------------------------------------------------------

    @Test
    fun `the preview reports the file, its rows and its date span`() = runFlow {
        val model = loadFixture()
        val preview = model.preview()

        assertEquals(Fixture.NAME, preview.fileName)
        assertEquals(SheetLayout.MONEY_MANAGER, preview.layout)
        assertEquals(1_200, preview.tally.fileRowCount)
        assertEquals(1_200, preview.tally.parsedRowCount)
        assertEquals("2025-07-28", preview.dateSpan!!.first.toString())
        assertEquals("2026-08-16", preview.dateSpan.last.toString())
    }

    /** Scenario: The deleted count is structurally zero. */
    @Test
    fun `the deleted count is zero`() = runFlow {
        assertEquals(0, loadFixture().preview().tally.deleted)
    }

    /** Scenario: Abandoning an import — no record of any kind created or modified. */
    @Test
    fun `nothing is written while the preview is open, or when it is abandoned`() = runFlow {
        val accountsBefore = db.accountDao().all().size
        val categoriesBefore = db.categoryDao().all().size

        val model = loadFixture()
        mapEverything(model)

        assertEquals(0, transactionCount())
        assertEquals(accountsBefore, db.accountDao().all().size)
        assertEquals(categoriesBefore, db.categoryDao().all().size)
        assertEquals(emptyList<Any>(), imports().observeBatches().first())

        model.discard()
        advanceUntilIdle()

        assertEquals(0, transactionCount())
        assertEquals(accountsBefore, db.accountDao().all().size)
    }

    // -- Account mapping --------------------------------------------------------

    /** Scenario: Mapping the reference export. */
    @Test
    fun `Card is prefilled to BCA, Cash matches by name, Gopay is left to the user`() = runFlow {
        seedAccounts()
        val preview = loadFixture().preview()

        val card = preview.accounts.single { it.sourceName == "Card" }
        assertEquals("BCA", card.choice?.displayName)
        assertTrue("Card should read as a guess", card.prefilled)
        assertEquals(1_150, card.rowCount)

        val cash = preview.accounts.single { it.sourceName == "Cash" }
        assertEquals("Cash", cash.choice?.displayName)

        val gopay = preview.accounts.single { it.sourceName == "Gopay" }
        assertEquals(null, gopay.choice)
    }

    /** Scenario: Import blocked on an unmapped account. */
    @Test
    fun `an unmapped account blocks the import and is named`() = runFlow {
        seedAccounts()
        val preview = loadFixture().preview()

        assertFalse(preview.canImport)
        assertTrue(
            preview.problems.toString(),
            preview.problems.contains(ImportProblem.UnmappedAccount("Gopay")),
        )
    }

    // -- Investment destinations ------------------------------------------------

    /** Scenario: Mapping the six transfer rows — one ask per category. */
    @Test
    fun `the six transfers are grouped into two categories, each asked once`() = runFlow {
        seedAccounts()
        val preview = loadFixture().preview()

        assertEquals(
            listOf("RDPU SucorInvest", "Saham"),
            preview.transferDestinations.map { it.categoryName },
        )
        assertEquals(6, preview.transferDestinations.sumOf { it.rowCount })
        assertEquals(
            Money(5_050_800),
            Money(preview.transferDestinations.sumOf { it.total.rupiah }),
        )
    }

    /** Scenario: Import blocked on an unmapped transfer group. */
    @Test
    fun `an unmapped transfer group blocks the import`() = runFlow {
        seedAccounts()
        val model = loadFixture()
        model.chooseAccount("Gopay", AccountChoice.Create("Gopay", AccountType.CASH))
        advanceUntilIdle()

        val preview = model.preview()
        assertFalse(preview.canImport)
        assertTrue(
            preview.problems.toString(),
            preview.problems.contains(ImportProblem.UnmappedTransferDestination("Saham")),
        )
    }

    /** The transfers must not evaporate: both legs land, so net worth is unchanged. */
    @Test
    fun `each imported transfer carries the destination its group was given`() = runFlow {
        seedAccounts()
        importEverything()

        val transfers = db.txnDao().let { dao ->
            transactionRepo().all().filter { it.type == TxnType.TRANSFER }
        }
        assertEquals(6, transfers.size)
        assertTrue("every transfer needs a destination", transfers.all { it.toAccountId != null })

        val investment = accountId("RDPU SucorInvest")
        val shares = accountId("Saham")
        assertEquals(
            setOf(investment, shares),
            transfers.mapNotNull { it.toAccountId }.toSet(),
        )
        assertEquals(
            Money(5_050_800),
            Money(transfers.sumOf { it.amount.rupiah }),
        )
    }

    // -- Deduplication ----------------------------------------------------------

    /** Task 12.11 and the "Re-importing an overlapping export" scenario. */
    @Test
    fun `importing the same file twice inserts nothing the second time`() = runFlow {
        seedAccounts()
        val first = importEverything()
        assertEquals(1_200, first)
        assertEquals(first, transactionCount())

        val model = loadFixture()
        mapEverything(model)
        val preview = model.preview()

        assertEquals(0, preview.tally.insertable)
        assertEquals(1_200, preview.tally.alreadyPresent)
        assertEquals(0, preview.tally.deleted)
        // Nothing left to add, so the action is off rather than being a no-op the user
        // has to tap to discover.
        assertFalse(preview.canImport)
        assertTrue(preview.problems.contains(ImportProblem.NothingToImport))

        model.confirm()
        advanceUntilIdle()
        assertEquals(first, transactionCount())
        assertEquals(1, imports().observeBatches().first().size)
    }

    /** Scenario: Reviewing an import — the tally splits new from already here. */
    @Test
    fun `a partially present file reports the split exactly`() = runFlow {
        seedAccounts()
        val total = importEverything()

        // Reverse everything, then put back a handful by hand so the second preview has
        // something to skip and something to add.
        val batch = imports().observeBatches().first().single()
        imports().reverse(batch.id, now)
        assertEquals(0, transactionCount())

        val keep = 54
        importEverything(limitTo = keep)
        assertEquals(keep, transactionCount())

        val preview = loadFixture().let { model ->
            mapEverything(model)
            model.preview()
        }
        assertEquals(keep, preview.tally.alreadyPresent)
        assertEquals(total - keep, preview.tally.insertable)
        assertEquals(0, preview.tally.deleted)
    }

    // -- Batch history ----------------------------------------------------------

    /** Scenario: Reviewing past imports, and Reversing a batch. */
    @Test
    fun `a reversal removes only that batch's rows`() = runFlow {
        seedAccounts()
        val handEntered = transactionRepo().record(
            occurredAt = now,
            type = TxnType.EXPENSE,
            amount = Money(25_000),
            accountId = accountId("Cash"),
            categoryId = categoryId("Makan & Minum"),
            note = "by hand",
        )

        val inserted = importEverything()
        assertEquals(inserted + 1, transactionCount())

        val batch = imports().observeBatches().first().single()
        assertEquals(Fixture.NAME, batch.fileName)
        assertEquals(1_200, batch.rowCount)
        assertEquals(inserted, batch.insertedCount)

        val removed = imports().reverse(batch.id, now)
        assertEquals(inserted, removed)
        assertEquals(1, transactionCount())
        assertNotNull(transactionRepo().byId(handEntered))

        val after = imports().observeBatches().first().single()
        assertTrue("the batch record survives its reversal", after.isReversed)
    }

    // -- Rows the user has to decide about ---------------------------------------

    /**
     * Scenario: A mismatch between IDR and Amount — listed with both values, and the
     * user decides whether it goes in.
     */
    @Test
    fun `a row whose two amount columns disagree is listed and can be dropped`() = runFlow {
        seedAccounts()
        val model = viewModel()
        model.load(FixtureWithOneMismatch())
        advanceUntilIdle()
        mapEverything(model)

        val discrepancy = model.preview().discrepancies.single()
        assertEquals(MISMATCHED_ROW, discrepancy.sourceRow)
        assertEquals("999999", discrepancy.declared)
        assertTrue("a flagged row still goes in unless dropped", discrepancy.included)

        val withRow = model.preview().tally.insertable
        model.setDiscrepancyIncluded(MISMATCHED_ROW, false)
        advanceUntilIdle()

        val preview = model.preview()
        assertEquals(withRow - 1, preview.tally.insertable)
        assertEquals(1, preview.tally.excluded)
        assertFalse(preview.discrepancies.single().included)

        model.confirm()
        advanceUntilIdle()
        assertEquals(withRow - 1, transactionCount())
    }

    /** Scenario: Importing a Money Manager export through the generic entry point. */
    @Test
    fun `the Money Manager layout is handled whichever menu row opened the screen`() = runFlow {
        seedAccounts()
        val model = ImportViewModel(
            imports = imports(),
            accounts = accountRepo(),
            categories = categoryRepo(),
            entryPoint = ImportEntryPoint.EXCEL,
            zone = JAKARTA,
            clock = object : Clock {
                override fun now(): Instant = now
            },
            io = dispatcher,
        )
        model.load(FileSource(Fixture.file))
        advanceUntilIdle()

        val preview = model.preview()
        assertEquals(SheetLayout.MONEY_MANAGER, preview.layout)
        assertEquals(ImportEntryPoint.EXCEL, preview.entryPoint)
        assertEquals(1_200, preview.tally.parsedRowCount)
    }

    // -- Refusals ---------------------------------------------------------------

    @Test
    fun `a file that is not a workbook is refused before anything is read`() = runFlow {
        val model = viewModel()
        val junk = File.createTempFile("not-a-workbook", ".xlsx").apply { writeText("nope") }
        model.load(FileSource(junk))
        advanceUntilIdle()

        val refused = model.state.value as ImportUiState.Refused
        assertTrue(refused.reason is ReadFailure.Unreadable)
        assertEquals(0, transactionCount())
    }

    /**
     * The failure mode the preview exists to catch. A first run opens every account
     * today, so a thirteen-month import would ask the repository to debit a transfer
     * from an account that did not exist yet — it throws and abandons the batch.
     */
    @Test
    fun `transfers dated before an account opened block the import rather than throwing`() = runFlow {
        seedAccounts(openingAsOf = now)
        val model = loadFixture()
        mapEverything(model)

        val preview = model.preview()
        assertFalse(preview.canImport)
        val problem = preview.problems.filterIsInstance<ImportProblem.TransferPredatesOpening>()
        assertTrue("expected an opening-date problem, got ${preview.problems}", problem.isNotEmpty())
        assertEquals(0, transactionCount())
    }

    // -- Helpers ----------------------------------------------------------------

    private fun runFlow(body: suspend TestScope.() -> Unit) = runTest(dispatcher) { body() }

    private fun viewModel() = ImportViewModel(
        imports = imports(),
        accounts = accountRepo(),
        categories = categoryRepo(),
        entryPoint = ImportEntryPoint.MONEY_MANAGER,
        zone = JAKARTA,
        clock = object : Clock {
            override fun now(): Instant = now
        },
        io = dispatcher,
    )

    private suspend fun TestScope.loadFixture(): ImportViewModel {
        val model = viewModel()
        model.load(FileSource(Fixture.file))
        advanceUntilIdle()
        return model
    }

    private fun ImportViewModel.preview(): ImportPreview =
        (state.value as ImportUiState.Ready).preview

    /** Every choice the reference export needs, made the way the owner would make them. */
    private suspend fun TestScope.mapEverything(model: ImportViewModel) {
        val preview = model.preview()
        preview.accounts.filter { it.choice == null }.forEach {
            model.chooseAccount(
                it.sourceName,
                AccountChoice.Create(it.sourceName, guessAccountType(it.sourceName)),
            )
        }
        preview.transferDestinations.forEach { group ->
            val name = group.categoryName ?: "Investments"
            model.chooseTransferDestination(
                group.categoryName,
                AccountChoice.Create(name, AccountType.INVESTMENT),
            )
        }
        advanceUntilIdle()
    }

    /**
     * Runs a whole import and returns how many rows landed. [limitTo] trims the file's
     * rows first, which is how a partially overlapping second import is staged.
     */
    private suspend fun TestScope.importEverything(limitTo: Int? = null): Int {
        val model = viewModel()
        model.load(if (limitTo == null) FileSource(Fixture.file) else TruncatedFixture(limitTo))
        advanceUntilIdle()
        mapEverything(model)

        model.confirm()
        advanceUntilIdle()

        return when (val state = model.state.value) {
            is ImportUiState.Imported -> state.inserted
            is ImportUiState.Ready -> error(
                "import did not run: ${state.preview.problems} ${state.preview.failure}",
            )

            else -> error("unexpected state $state")
        }
    }

    private suspend fun seedAccounts(openingAsOf: Instant = opening) {
        listOf(
            "BCA" to AccountType.BANK,
            "Cash" to AccountType.CASH,
        ).forEach { (name, type) ->
            accountRepo().create(name, type, Money.ZERO, openingAsOf)
        }
        categoryRepo().findOrCreate("Makan & Minum", CategoryKind.EXPENSE)
    }

    private suspend fun accountId(name: String): Long =
        accountRepo().all().single { it.name == name }.id

    private suspend fun categoryId(name: String): Long =
        categoryRepo().findOrCreate(name, CategoryKind.EXPENSE)

    private suspend fun transactionCount(): Int = transactionRepo().all().size

    private fun imports() = RoomImportRepository(db)

    private fun accountRepo() = RoomAccountRepository(db)

    private fun categoryRepo() = RoomCategoryRepository(db)

    private fun transactionRepo() = RoomTransactionRepository(db)

    private companion object {
        /** An ordinary expense row, chosen only because it is not one of the transfers. */
        const val MISMATCHED_ROW = 4
    }

    private class FileSource(private val file: File) : DocumentSource {
        override val name: String = file.name

        override fun openInput(): InputStream = file.inputStream()
    }

    /**
     * The reference export with its data rows cut short, written back out as a real
     * workbook. Staging an overlap this way keeps the rows byte-identical to the ones a
     * full import would produce, which is the only way the dedup count means anything.
     */

    /**
     * The real export with the `Amount` cell of one row rewritten so it no longer
     * agrees with `IDR`. Every other row and every other column is the file as shipped —
     * the condition has to be manufactured because all 1,200 real rows agree.
     */
    private inner class FixtureWithOneMismatch : DocumentSource {
        override val name: String = Fixture.NAME

        override fun openInput(): InputStream {
            val table = com.dvnnfrr.saku.excel.XlsxReader.read(Fixture.file)
            val rows = table.rows.map { row ->
                if (row.number != MISMATCHED_ROW) {
                    row
                } else {
                    row.copy(
                        cells = row.cells.toMutableList().also {
                            it[SheetLayout.MoneyManager.AMOUNT] =
                                com.dvnnfrr.saku.excel.CellValue.Text("999999")
                        },
                    )
                }
            }
            val bytes = java.io.ByteArrayOutputStream()
                .also { com.dvnnfrr.saku.excel.XlsxWriter.write(table.copy(rows = rows), it) }
                .toByteArray()
            return java.io.ByteArrayInputStream(bytes)
        }
    }

    private inner class TruncatedFixture(private val rows: Int) : DocumentSource {
        override val name: String = Fixture.NAME

        override fun openInput(): InputStream {
            val table = com.dvnnfrr.saku.excel.XlsxReader.read(Fixture.file)
            val trimmed = table.copy(rows = table.rows.take(rows))
            val bytes = java.io.ByteArrayOutputStream()
                .also { com.dvnnfrr.saku.excel.XlsxWriter.write(trimmed, it) }
                .toByteArray()
            return java.io.ByteArrayInputStream(bytes)
        }
    }
}
