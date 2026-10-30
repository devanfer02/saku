package com.dvnnfrr.saku.ui.entry

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.data.repository.RoomAccountRepository
import com.dvnnfrr.saku.data.repository.RoomCategoryRepository
import com.dvnnfrr.saku.data.repository.RoomTransactionRepository
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.receipt.OcrLine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The scan scenarios in specs/receipt-scan/spec.md, through the ViewModel with a fake recognizer. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReceiptScanViewModelTest {

    private val zone = TimeZone.of("Asia/Jakarta")
    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: SakuDatabase
    private lateinit var transactions: RoomTransactionRepository
    private lateinit var accounts: RoomAccountRepository
    private lateinit var categories: RoomCategoryRepository

    private val clock = object : Clock {
        override fun now(): Instant = LocalDateTime(2026, 10, 31, 19, 45).toInstant(zone)
    }

    private val photo = Uri.parse("content://media/picker/0/receipt/1")

    private val indomaret = listOf(
        "INDOMARET", "30/10/2026 08:12", "INDOMIE GRG SPCL 2 3.100 6.200", "AQUA 600ML 1 3.500",
        "TOTAL 52.300", "TUNAI 100.000", "KEMBALI 47.700",
    ).mapIndexed { i, text -> OcrLine(text, 10, i * 30, 300, i * 30 + 20) }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SakuDatabase::class.java)
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .allowMainThreadQueries()
            .build()
        transactions = RoomTransactionRepository(db)
        accounts = RoomAccountRepository(db)
        categories = RoomCategoryRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun viewModel(read: suspend (Uri) -> List<OcrLine>): TransactionEntryViewModel {
        accounts.create("Cash", AccountType.CASH, Money.ZERO, LocalDateTime(2026, 1, 1, 0, 0).toInstant(zone))
        return TransactionEntryViewModel(
            transactions = transactions,
            categoryRepository = categories,
            accountRepository = accounts,
            zone = zone,
            clock = clock,
            readReceipt = read,
        )
    }

    @Test
    fun `a scan fills amount, date and note and offers the rest`() = runTest(dispatcher) {
        val vm = viewModel { indomaret }
        vm.open()
        advanceUntilIdle()

        vm.scanReceipt(photo)
        assertTrue(vm.state.value.readingReceipt)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.readingReceipt)
        assertEquals(Money(52_300), state.form.amount.value)
        assertEquals(LocalDateTime(2026, 10, 30, 19, 45), state.form.occurredAt)
        assertEquals("Indomaret", state.form.note)
        assertTrue("INDOMIE GRG SPCL 2 3.100 6.200" in state.receipt!!.lines)
    }

    @Test
    fun `an amount typed while reading is kept`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val vm = viewModel { gate.await(); indomaret }
        vm.open()
        advanceUntilIdle()

        vm.scanReceipt(photo)
        advanceUntilIdle()
        "40000".forEach { vm.onDigit(it - '0') }
        gate.complete(Unit)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(Money(40_000), state.form.amount.value)
        assertEquals(Money(52_300), state.receipt!!.chipsFor(state.form.amount.value).first())
    }

    @Test
    fun `a chip replaces the amount and a line joins the note`() = runTest(dispatcher) {
        val vm = viewModel { indomaret }
        vm.open()
        advanceUntilIdle()
        vm.scanReceipt(photo)
        advanceUntilIdle()

        vm.pickReceiptAmount(Money(6_200))
        vm.addReceiptLine("AQUA 600ML 1 3.500")

        val state = vm.state.value
        assertEquals(Money(6_200), state.form.amount.value)
        assertEquals("Indomaret, AQUA 600ML 1 3.500", state.form.note)
        assertFalse("AQUA 600ML 1 3.500" in state.receipt!!.lines)
    }

    @Test
    fun `a failed read says no total and changes nothing`() = runTest(dispatcher) {
        val vm = viewModel { error("decoder gave up") }
        vm.open()
        advanceUntilIdle()
        vm.scanReceipt(photo)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.receipt!!.totalFound)
        assertEquals(Money.ZERO, state.form.amount.value)
        assertEquals("", state.form.note)
    }

    @Test
    fun `scanning is offered only for a new expense`() = runTest(dispatcher) {
        val vm = viewModel { indomaret }
        vm.open()
        advanceUntilIdle()
        assertTrue(vm.state.value.canScanReceipt)

        vm.setType(TxnType.INCOME)
        assertFalse(vm.state.value.canScanReceipt)
        vm.setType(TxnType.TRANSFER)
        assertFalse(vm.state.value.canScanReceipt)
    }

    @Test
    fun `reopening the sheet drops the last scan`() = runTest(dispatcher) {
        val vm = viewModel { indomaret }
        vm.open()
        advanceUntilIdle()
        vm.scanReceipt(photo)
        advanceUntilIdle()

        vm.selectCategory(categories.createCategory("Belanja", com.dvnnfrr.saku.domain.CategoryKind.EXPENSE))
        vm.save()
        advanceUntilIdle()
        vm.open()
        advanceUntilIdle()

        assertNull(vm.state.value.receipt)
    }
}
