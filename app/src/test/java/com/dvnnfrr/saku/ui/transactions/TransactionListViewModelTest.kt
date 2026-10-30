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
import com.dvnnfrr.saku.ui.filter.SharedFilter
import com.dvnnfrr.saku.domain.TransactionFilter
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
import kotlinx.datetime.Month
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
    private val shared = SharedFilter()

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
        sharedFilter = shared,
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

        val sqlPeriod = transactions.observePeriodTotals(august, zone, TransactionFilter.ALL).first()
        assertEquals(sqlPeriod.income, state.totals.income)
        assertEquals(sqlPeriod.expense, state.totals.expense)
        assertEquals(sqlPeriod.net, state.totals.net)

        val sqlDaily = transactions.observeDailyTotals(august, zone, TransactionFilter.ALL).first()
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

    // Calendar date selection.

    @Test
    fun `the calendar tap sequence picks a day, closes a range, starts over and clears`() =
        runTest(dispatcher) {
            seed()
            record(day = 3, amount = 170_000)
            record(day = 12, amount = 60_000)
            record(day = 14, type = TxnType.INCOME, amount = 8_000_000, categoryId = gaji)
            val vm = viewModel()
            vm.state.first { !it.loading }
            vm.selectTab(TransactionTab.CALENDAR)

            vm.tapCalendarDay(LocalDate(2026, 8, 12))
            val day = requireNotNull(vm.state.first { it.calendarRange != null }.calendarRange)
            assertEquals(DateRange.custom(LocalDate(2026, 8, 12), LocalDate(2026, 8, 12)), day.range)
            assertEquals(Money(60_000), day.totals.expense)

            // Backwards: the second tap is earlier than the first.
            vm.tapCalendarDay(LocalDate(2026, 8, 3))
            val range = requireNotNull(
                vm.state.first { it.calendarRange?.range?.start == LocalDate(2026, 8, 3) }.calendarRange,
            )
            assertEquals(LocalDate(2026, 8, 12), range.range.endInclusive)
            assertEquals(Money(230_000), range.totals.expense)
            assertEquals(listOf(LocalDate(2026, 8, 12), LocalDate(2026, 8, 3)), range.days.map { it.date })

            vm.tapCalendarDay(LocalDate(2026, 8, 14))
            val fresh = requireNotNull(
                vm.state.first { it.calendarRange?.range?.start == LocalDate(2026, 8, 14) }.calendarRange,
            )
            assertEquals(fresh.range.start, fresh.range.endInclusive)
            assertEquals(Money(8_000_000), fresh.totals.income)

            vm.tapCalendarDay(LocalDate(2026, 8, 14))
            assertNull(vm.state.first { it.calendarRange == null }.calendarRange)
        }

    @Test
    fun `a sweep selects the dragged range`() = runTest(dispatcher) {
        seed()
        record(day = 5, amount = 10_000)
        record(day = 9, amount = 20_000)
        val vm = viewModel()
        vm.state.first { !it.loading }

        vm.sweepCalendarDays(LocalDate(2026, 8, 9), LocalDate(2026, 8, 4))

        val swept = requireNotNull(vm.state.first { it.calendarRange != null }.calendarRange)
        assertEquals(DateRange.custom(LocalDate(2026, 8, 4), LocalDate(2026, 8, 9)), swept.range)
        assertEquals(Money(30_000), swept.totals.expense)

        vm.clearCalendarSelection()
        assertNull(vm.state.first { it.calendarRange == null }.calendarRange)
    }

    @Test
    fun `the calendar selection respects the filter`() = runTest(dispatcher) {
        seed()
        record(day = 12, amount = 60_000)
        record(day = 12, type = TxnType.INCOME, amount = 8_000_000, categoryId = gaji)
        val vm = viewModel()
        vm.state.first { !it.loading }
        vm.tapCalendarDay(LocalDate(2026, 8, 12))
        vm.state.first { it.calendarRange != null }

        shared.set(TransactionFilter(incomeCategoryIds = emptySet()))

        val filtered = requireNotNull(vm.state.first { it.filter.isActive && it.calendarRange != null }.calendarRange)
        assertEquals(Money.ZERO, filtered.totals.income)
        assertEquals(Money(60_000), filtered.totals.expense)
        assertEquals(listOf(TxnType.EXPENSE), filtered.days.flatMap { it.rows }.map { it.type })
    }

    @Test
    fun `changing month clears the calendar selection`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        vm.state.first { !it.loading }
        vm.selectTab(TransactionTab.CALENDAR)
        vm.tapCalendarDay(LocalDate(2026, 8, 12))
        vm.state.first { it.calendarRange != null }

        vm.previousMonth()
        val july = vm.state.first { it.range.start == LocalDate(2026, 7, 1) }
        assertNull(july.calendarRange)

        vm.nextMonth()
        assertNull(vm.state.first { it.range == august }.calendarRange)
    }

    // transaction-filter: every figure respects the filter, set from either screen.

    @Test
    fun `the filter narrows the header, the days and the calendar together`() = runTest(dispatcher) {
        seed()
        record(day = 3, amount = 170_000)
        record(day = 14, type = TxnType.INCOME, amount = 8_000_000, categoryId = gaji)
        record(day = 14, type = TxnType.TRANSFER, amount = 500_000, categoryId = null, toAccountId = rdpu)
        val vm = viewModel()
        vm.state.first { !it.loading }

        shared.set(TransactionFilter(accountIds = setOf(rdpu)))
        val state = vm.state.first { it.filter.isActive }

        // Only the transfer touches RDPU, and it counts toward nothing.
        assertEquals(listOf(TxnType.TRANSFER), state.days.flatMap { it.rows }.map { it.type })
        assertEquals(Money.ZERO, state.totals.income)
        assertEquals(Money.ZERO, state.totals.expense)
        assertEquals(1, state.calendar?.cells?.count { it.hasTransactions })
    }

    @Test
    fun `clearing the filter restores the whole month`() = runTest(dispatcher) {
        seed()
        record(day = 3, amount = 170_000)
        shared.set(TransactionFilter(expenseCategoryIds = emptySet()))
        val vm = viewModel()
        assertTrue(vm.state.first { !it.loading }.days.isEmpty())

        vm.clearFilter()

        val state = vm.state.first { !it.filter.isActive }
        assertEquals(Money(170_000), state.totals.expense)
        assertEquals(TransactionFilter.ALL, shared.state.value)
    }

    // transaction-monthly-view.

    @Test
    fun `Monthly shows the month's year with the current month expanded`() = runTest(dispatcher) {
        seed()
        record(day = 3, amount = 170_000)
        val vm = viewModel()
        vm.state.first { !it.loading }

        vm.selectTab(TransactionTab.MONTHLY)
        val monthly = requireNotNull(vm.state.first { it.monthly != null }.monthly)

        assertEquals(2026, monthly.year)
        // The clock reads 17 Aug 2026: August first, January last.
        assertEquals((8 downTo 1).toList(), monthly.months.map { it.range.start.monthNumber })
        assertEquals(listOf(Month.AUGUST), monthly.months.filter { it.expanded }.map { it.month })
        assertEquals(Money(170_000), monthly.totals.expense)

        vm.selectTab(TransactionTab.DAILY)
        val daily = vm.state.first { it.tab == TransactionTab.DAILY && it.monthly == null }
        assertEquals(august, daily.range)
    }

    @Test
    fun `the year arrows step twelve months and expand nothing in another year`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        vm.state.first { !it.loading }
        vm.selectTab(TransactionTab.MONTHLY)
        vm.state.first { it.monthly != null }

        vm.previousMonth()
        val monthly = requireNotNull(vm.state.first { it.monthly?.year == 2025 }.monthly)
        assertEquals(12, monthly.months.size)
        assertEquals(Month.DECEMBER, monthly.months.first().month)
        assertTrue(monthly.months.none { it.expanded })

        vm.selectTab(TransactionTab.DAILY)
        assertEquals(
            DateRange.ofMonth(LocalDate(2025, 8, 1)),
            vm.state.first { it.monthly == null && it.range.start.year == 2025 }.range,
        )
    }

    @Test
    fun `tapping a month expands it and collapses the other`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        vm.state.first { !it.loading }
        vm.selectTab(TransactionTab.MONTHLY)
        vm.state.first { it.monthly != null }

        vm.toggleMonth(Month.JUNE)
        val months = vm.state.first { state -> state.monthly?.months?.any { it.expanded && it.month == Month.JUNE } == true }
            .monthly!!.months
        assertEquals(listOf(Month.JUNE), months.filter { it.expanded }.map { it.month })

        vm.toggleMonth(Month.JUNE)
        assertTrue(vm.state.first { s -> s.monthly?.months?.none { it.expanded } == true }.monthly!!.months.none { it.expanded })
    }

    @Test
    fun `opening a week opens Daily on the month it was listed under`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        vm.state.first { !it.loading }
        vm.selectTab(TransactionTab.MONTHLY)

        vm.openDay(LocalDate(2026, 6, 30))

        val state = vm.state.first { it.pendingScrollTo != null && it.range.start.monthNumber == 6 }
        assertEquals(TransactionTab.DAILY, state.tab)
        assertEquals(DateRange.ofMonth(LocalDate(2026, 6, 1)), state.range)
    }

    @Test
    fun `Monthly figures respect the filter`() = runTest(dispatcher) {
        seed()
        record(day = 3, amount = 170_000)
        record(day = 14, type = TxnType.INCOME, amount = 8_000_000, categoryId = gaji)
        val vm = viewModel()
        vm.state.first { !it.loading }
        vm.selectTab(TransactionTab.MONTHLY)
        vm.state.first { it.monthly != null }

        shared.set(TransactionFilter(incomeCategoryIds = emptySet()))
        val monthly = requireNotNull(vm.state.first { it.monthly != null && it.filter.isActive }.monthly)

        assertEquals(Money.ZERO, monthly.totals.income)
        assertEquals(Money(170_000), monthly.totals.expense)
        assertEquals(Money(170_000), monthly.months.first().totals?.expense)
    }
}
