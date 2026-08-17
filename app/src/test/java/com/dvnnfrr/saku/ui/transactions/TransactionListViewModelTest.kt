package com.dvnnfrr.saku.ui.transactions

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
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The ViewModel against real repositories on an in-memory database, rather than
 * against fakes. Three interfaces totalling fifty-odd methods would need fifty-odd
 * stubs to fake, and the stubs would only ever prove that the stubs agree with each
 * other.
 *
 * The first test is the one that matters. The screen folds its header and day
 * subtotals from the loaded rows in Kotlin, while the stats screen aggregates the
 * same rules in SQL. Two implementations of one rule is a standing invitation to
 * drift, so this asserts they agree on the same data.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransactionListViewModelTest {

    private val zone = TimeZone.of("Asia/Jakarta")
    private val august = DateRange.ofMonth(LocalDate(2026, 8, 1))
    private val dispatcher = StandardTestDispatcher()

    private lateinit var db: SakuDatabase
    private lateinit var transactions: RoomTransactionRepository
    private lateinit var accounts: RoomAccountRepository
    private lateinit var categories: RoomCategoryRepository

    private var bca = 0L
    private var rdpu = 0L
    private var makan = 0L
    private var gaji = 0L

    /** A clock parked inside August 2026, so the default range is deterministic. */
    private val clock = object : Clock {
        override fun now(): Instant = LocalDateTime(2026, 8, 17, 9, 0).toInstant(zone)
    }

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
        val asOf = LocalDateTime(2026, 8, 1, 0, 0).toInstant(zone)
        bca = accounts.create("BCA", AccountType.BANK, Money.ZERO, asOf)
        rdpu = accounts.create("RDPU", AccountType.INVESTMENT, Money.ZERO, asOf)
        makan = categories.createCategory("Makan & Minum", CategoryKind.EXPENSE)
        gaji = categories.createCategory("Gaji", CategoryKind.INCOME)
    }

    private suspend fun record(
        day: Int,
        hour: Int = 12,
        type: TxnType = TxnType.EXPENSE,
        amount: Long,
        categoryId: Long? = makan,
        toAccountId: Long? = null,
    ) = transactions.record(
        occurredAt = LocalDateTime(2026, 8, day, hour, 0).toInstant(zone),
        type = type,
        amount = Money(amount),
        accountId = bca,
        toAccountId = toAccountId,
        categoryId = categoryId,
    )

    private fun viewModel() = TransactionListViewModel(
        transactionRepository = transactions,
        accountRepository = accounts,
        categoryRepository = categories,
        zone = zone,
        clock = clock,
    )

    @Test
    fun `the Kotlin fold and the SQL aggregate agree on the same month`() = runTest(dispatcher) {
        seed()
        record(day = 3, amount = 170_000)
        record(day = 3, amount = 45_000)
        record(day = 14, type = TxnType.INCOME, amount = 8_000_000, categoryId = gaji)
        record(day = 14, type = TxnType.TRANSFER, amount = 500_000, categoryId = null, toAccountId = rdpu)
        record(day = 21, amount = 1_250_000)

        val vm = viewModel()
        val state = vm.state.first { !it.loading }

        val sqlPeriod = transactions.observePeriodTotals(august, zone).first()
        assertEquals(sqlPeriod.income, state.totals.income)
        assertEquals(sqlPeriod.expense, state.totals.expense)
        assertEquals(sqlPeriod.net, state.totals.net)

        val sqlDaily = transactions.observeDailyTotals(august, zone).first()
            .filterNot { it.isEmpty }
            .associateBy { it.date }
        val folded = state.days.associateBy { it.date }

        // Every day the SQL reports must be folded identically. Days holding only a
        // transfer are absent from the SQL side, which is why isEmpty is filtered out
        // rather than compared key-for-key.
        assertTrue(sqlDaily.isNotEmpty())
        sqlDaily.forEach { (date, sql) ->
            val section = requireNotNull(folded[date]) { "no folded section for $date" }
            assertEquals("income on $date", sql.income, section.income)
            assertEquals("expense on $date", sql.expense, section.expense)
        }
    }

    @Test
    fun `the screen opens on the current month`() = runTest(dispatcher) {
        seed()

        val state = viewModel().state.first { !it.loading }

        assertEquals(august, state.range)
        assertEquals(TransactionTab.DAILY, state.tab)
    }

    @Test
    fun `navigating months keeps the selected tab`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        vm.state.first { !it.loading }

        vm.selectTab(TransactionTab.CALENDAR)
        vm.previousMonth()

        val state = vm.state.first { it.range == august.previous() }
        assertEquals(TransactionTab.CALENDAR, state.tab)
        assertEquals(DateRange.ofMonth(LocalDate(2026, 7, 1)), state.range)
    }

    @Test
    fun `the month reload carries its own rows`() = runTest(dispatcher) {
        seed()
        record(day = 5, amount = 99_000)

        val vm = viewModel()
        assertEquals(1, vm.state.first { !it.loading }.days.size)

        vm.previousMonth()

        val july = vm.state.first { it.range.start == LocalDate(2026, 7, 1) }
        assertTrue(july.days.isEmpty())
        assertEquals(Money.ZERO, july.totals.expense)
        assertEquals(31, july.calendar?.cells?.size)
    }

    @Test
    fun `opening a day from the calendar switches to Daily and asks for a scroll`() =
        runTest(dispatcher) {
            seed()
            record(day = 12, amount = 60_000)
            val vm = viewModel()
            vm.state.first { !it.loading }
            vm.selectTab(TransactionTab.CALENDAR)

            vm.openDay(LocalDate(2026, 8, 12))

            // Waits on pendingScrollTo rather than on the tab: DAILY is also the value
            // the stream started at, so a tab predicate matches the stale emission.
            val state = vm.state.first { it.pendingScrollTo != null }
            assertEquals(LocalDate(2026, 8, 12), state.pendingScrollTo)
            assertEquals(TransactionTab.DAILY, state.tab)

            vm.consumeScrollTarget()
            assertNull(vm.state.first { it.pendingScrollTo == null }.pendingScrollTo)
        }
}
