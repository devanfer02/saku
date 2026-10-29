package com.dvnnfrr.saku.ui.filter

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.data.repository.RoomAccountRepository
import com.dvnnfrr.saku.data.repository.RoomCategoryRepository
import com.dvnnfrr.saku.data.repository.RoomTransactionRepository
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TransactionFilter
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FilterSheetViewModelTest {

    private val zone = TimeZone.of("Asia/Jakarta")
    private val october = DateRange.ofMonth(LocalDate(2026, 10, 1))
    private val dispatcher = StandardTestDispatcher()

    private lateinit var db: SakuDatabase
    private lateinit var transactions: RoomTransactionRepository
    private lateinit var accounts: RoomAccountRepository
    private lateinit var categories: RoomCategoryRepository
    private val shared = SharedFilter()

    private var bca = 0L
    private var rdpu = 0L
    private var makan = 0L
    private var pemberian = 0L
    private var gaji = 0L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SakuDatabase::class.java,
        ).allowMainThreadQueries().build()
        transactions = RoomTransactionRepository(db)
        accounts = RoomAccountRepository(db)
        categories = RoomCategoryRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun seed() {
        val asOf = Instant.fromEpochSeconds(0)
        bca = accounts.create("BCA", AccountType.BANK, Money.ZERO, asOf)
        rdpu = accounts.create("RDPU SucorInvest", AccountType.INVESTMENT, Money.ZERO, asOf)
        makan = categories.createCategory("Makan & Minum", CategoryKind.EXPENSE)
        pemberian = categories.createCategory("Pemberian", CategoryKind.EXPENSE)
        gaji = categories.createCategory("Gaji", CategoryKind.INCOME)

        record(1, TxnType.INCOME, 10_000_000, gaji)
        record(2, TxnType.EXPENSE, 1_500_000, makan)
        record(3, TxnType.EXPENSE, 4_500_000, pemberian)
        record(14, TxnType.TRANSFER, 500_000, null, to = rdpu)
    }

    private suspend fun record(day: Int, type: TxnType, amount: Long, category: Long?, to: Long? = null) =
        transactions.record(
            occurredAt = LocalDateTime(2026, 10, day, 12, 0).toInstant(zone),
            type = type,
            amount = Money(amount),
            accountId = bca,
            toAccountId = to,
            categoryId = category,
        )

    private fun viewModel() = FilterSheetViewModel(october, transactions, accounts, categories, shared, zone)

    @Test
    fun `the sheet opens on INCOME with the opening screen's period`() = runTest(dispatcher) {
        seed()

        val state = viewModel().state.first { !it.loading }

        assertEquals(FilterTab.INCOME, state.tab)
        assertEquals(october, state.range)
        assertEquals(RingFigure(Money(10_000_000), 100), state.income)
    }

    @Test
    fun `a tick writes straight to the shared filter`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        vm.state.first { !it.loading }

        vm.selectTab(FilterTab.EXPENSES)
        vm.state.first { it.tab == FilterTab.EXPENSES }
        vm.toggleAll()
        vm.toggle(makan)

        assertEquals(TransactionFilter(expenseCategoryIds = setOf(makan)), shared.state.value)
        val state = vm.state.first { !it.allExpense && it.expenseRows.any { row -> row.ticked } }
        assertEquals(RingFigure(Money(1_500_000), 25), state.expense)
        assertEquals(Money(8_500_000), state.total)
    }

    @Test
    fun `a category filter leaves the account figures alone`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        val before = vm.state.first { !it.loading }.accountGroups

        shared.set(TransactionFilter(expenseCategoryIds = setOf(pemberian)))
        val after = vm.state.first { !it.allExpense }.accountGroups

        assertEquals(before, after)
        val bcaRow = after.flatMap { it.rows }.single { it.id == bca }
        assertEquals(Money(10_000_000), bcaRow.flows.income)
        assertEquals(Money(6_000_000), bcaRow.flows.expense)
        assertEquals(Money(500_000), bcaRow.flows.transferOut)
    }

    @Test
    fun `ticking All again clears that tab`() = runTest(dispatcher) {
        seed()
        shared.set(TransactionFilter(accountIds = setOf(rdpu)))
        val vm = viewModel()
        vm.state.first { !it.loading }

        vm.selectTab(FilterTab.ACCOUNT)
        vm.state.first { it.tab == FilterTab.ACCOUNT }
        vm.toggleAll()

        assertNull(shared.state.value.accountIds)
    }
}
