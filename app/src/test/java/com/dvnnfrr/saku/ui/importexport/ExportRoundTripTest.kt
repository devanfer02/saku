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
import com.dvnnfrr.saku.domain.Transaction
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.excel.JAKARTA
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
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

/**
 * Task 12.10 and the round-tripping scenario: export a populated database, import the
 * file into an empty one, and assert every field survived.
 *
 * Two databases rather than one, because reproducing a transaction is only meaningful
 * when the ids on the far side were assigned independently. Everything travels as a
 * name, so nothing may depend on the two databases having numbered their records alike.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExportRoundTripTest {

    private val dispatcher = StandardTestDispatcher()
    private val opening = Instant.parse("2025-01-01T00:00:00Z")
    private val now = Instant.parse("2026-08-17T03:00:00Z")

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

    @Test
    fun `every transaction survives an export and a re-import into an empty database`() =
        runTest(dispatcher) {
            val expected = populate(source)
            val file = export(source)

            assertEquals(0, RoomTransactionRepository(destination).all().size)
            importInto(destination, file)

            val landed = RoomTransactionRepository(destination).all().sortedBy { it.occurredAt }
            assertEquals(expected.size, landed.size)

            val sourceNames = names(source)
            val destinationNames = names(destination)

            expected.sortedBy { it.occurredAt }.zip(landed).forEach { (before, after) ->
                assertEquals(before.occurredAt, after.occurredAt)
                assertEquals(before.type, after.type)
                assertEquals(before.amount, after.amount)
                assertEquals(before.note, after.note)
                assertEquals(
                    sourceNames.account(before.accountId),
                    destinationNames.account(after.accountId),
                )
                assertEquals(
                    before.toAccountId?.let(sourceNames::account),
                    after.toAccountId?.let(destinationNames::account),
                )
                assertEquals(
                    before.categoryId?.let(sourceNames::category),
                    after.categoryId?.let(destinationNames::category),
                )
                assertEquals(
                    before.subcategoryId?.let(sourceNames::subcategory),
                    after.subcategoryId?.let(destinationNames::subcategory),
                )
            }
        }

    /** Scenario: Exporting a transfer — both accounts named, so net worth survives. */
    @Test
    fun `a transfer arrives with both of its accounts`() = runTest(dispatcher) {
        populate(source)
        importInto(destination, export(source))

        val transfer = RoomTransactionRepository(destination).all()
            .single { it.type == TxnType.TRANSFER }
        val names = names(destination)

        assertEquals("BCA", names.account(transfer.accountId))
        assertEquals("RDPU SucorInvest", names.account(transfer.toAccountId!!))
    }

    /** Nothing in the export path opens a stream the user did not choose. */
    @Test
    fun `export writes to the chosen document and nowhere else`() = runTest(dispatcher) {
        populate(source)
        val sink = RecordingSink()

        ExportViewModel(
            transactions = RoomTransactionRepository(source),
            accounts = RoomAccountRepository(source),
            categories = RoomCategoryRepository(source),
            zone = JAKARTA,
            io = dispatcher,
        ).also { it.export(sink) }
        advanceUntilIdle()

        assertEquals(1, sink.opened)
        assertTrue("the sheet should have been written", sink.bytes().isNotEmpty())
    }

    // -- Helpers ----------------------------------------------------------------

    private fun newDatabase(): SakuDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        SakuDatabase::class.java,
    )
        .setQueryExecutor(dispatcher.asExecutor())
        .setTransactionExecutor(dispatcher.asExecutor())
        .allowMainThreadQueries()
        .build()

    private suspend fun populate(db: SakuDatabase): List<Transaction> {
        val accounts = RoomAccountRepository(db)
        val categories = RoomCategoryRepository(db)
        val transactions = RoomTransactionRepository(db)

        val bca = accounts.create("BCA", AccountType.BANK, Money.ZERO, opening)
        val cash = accounts.create("Cash", AccountType.CASH, Money.ZERO, opening)
        val rdpu = accounts.create("RDPU SucorInvest", AccountType.INVESTMENT, Money.ZERO, opening)

        val makan = categories.createCategory("Makan & Minum", CategoryKind.EXPENSE)
        val warung = categories.findOrCreateSubcategory(makan, "Warung")
        val gaji = categories.createCategory("Gaji", CategoryKind.INCOME)
        val saham = categories.createCategory("Saham", CategoryKind.EXPENSE)

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
            categoryId = saham,
        )
        // No category and no note: the columns must round-trip as empty rather than as
        // the string "null", which is what a naive toString() would have written.
        transactions.record(
            occurredAt = Instant.parse("2026-06-05T06:00:00Z"),
            type = TxnType.EXPENSE,
            amount = Money(12_000),
            accountId = cash,
            categoryId = makan,
        )

        return transactions.all()
    }

    private suspend fun TestScope.export(db: SakuDatabase): ByteArray {
        val sink = RecordingSink()
        ExportViewModel(
            transactions = RoomTransactionRepository(db),
            accounts = RoomAccountRepository(db),
            categories = RoomCategoryRepository(db),
            zone = JAKARTA,
            io = dispatcher,
        ).export(sink)
        advanceUntilIdle()
        return sink.bytes()
    }

    private suspend fun TestScope.importInto(db: SakuDatabase, file: ByteArray) {
        val model = ImportViewModel(
            imports = RoomImportRepository(db),
            accounts = RoomAccountRepository(db),
            categories = RoomCategoryRepository(db),
            entryPoint = ImportEntryPoint.EXCEL,
            zone = JAKARTA,
            clock = object : Clock {
                override fun now(): Instant = now
            },
            io = dispatcher,
        )
        model.load(BytesSource("saku-export.xlsx", file))
        advanceUntilIdle()

        val preview = (model.state.value as ImportUiState.Ready).preview
        // The importing database is empty, so every account in the file is new. They
        // open on the earliest row they carry, which is what keeps the transfer inside
        // both of its accounts' as-of windows.
        preview.accounts.forEach {
            model.chooseAccount(
                it.sourceName,
                AccountChoice.Create(it.sourceName, guessAccountType(it.sourceName)),
            )
        }
        advanceUntilIdle()

        model.confirm()
        advanceUntilIdle()

        when (val state = model.state.value) {
            is ImportUiState.Imported -> Unit
            is ImportUiState.Ready ->
                error("import blocked: ${state.preview.problems} ${state.preview.failure}")

            else -> error("unexpected state $state")
        }
    }

    private suspend fun names(db: SakuDatabase): Names {
        val categories = RoomCategoryRepository(db)
        return Names(
            accounts = RoomAccountRepository(db).all().associate { it.id to it.name },
            categories = categories.allCategories().associate { it.id to it.name },
            subcategories = categories.allSubcategories().associate { it.id to it.name },
        )
    }

    private class Names(
        val accounts: Map<Long, String>,
        val categories: Map<Long, String>,
        val subcategories: Map<Long, String>,
    ) {
        fun account(id: Long) = accounts.getValue(id)

        fun category(id: Long) = categories.getValue(id)

        fun subcategory(id: Long) = subcategories.getValue(id)
    }

    private class RecordingSink : DocumentSink {
        override val name = "saku-export.xlsx"
        private val buffer = ByteArrayOutputStream()
        var opened = 0
            private set

        override fun openOutput(): OutputStream {
            opened++
            return buffer
        }

        fun bytes(): ByteArray = buffer.toByteArray()
    }

    private class BytesSource(override val name: String, private val bytes: ByteArray) :
        DocumentSource {
        override fun openInput(): InputStream = ByteArrayInputStream(bytes)
    }
}
