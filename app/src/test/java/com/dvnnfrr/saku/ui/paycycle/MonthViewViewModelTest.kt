package com.dvnnfrr.saku.ui.paycycle

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dvnnfrr.saku.data.PeriodStore
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.data.repository.RoomAccountRepository
import com.dvnnfrr.saku.data.repository.RoomCategoryRepository
import com.dvnnfrr.saku.data.repository.RoomTransactionRepository
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.MonthView
import com.dvnnfrr.saku.domain.PeriodMode
import com.dvnnfrr.saku.domain.TransactionFilter
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.ui.filter.SharedFilter
import com.dvnnfrr.saku.ui.stats.StatsViewModel
import com.dvnnfrr.saku.ui.transactions.TransactionListViewModel
import com.dvnnfrr.saku.ui.transactions.TransactionTab
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Trans and Stats against one [PeriodStore], the way `AppContainer` wires them, so the
 * switch being shared, persisted and hidden is tested on the real wiring rather than on
 * each ViewModel alone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MonthViewViewModelTest {

    private val zone = TimeZone.of("Asia/Jakarta")
    private val dispatcher = StandardTestDispatcher()

    private lateinit var db: SakuDatabase
    private lateinit var transactions: RoomTransactionRepository
    private lateinit var accounts: RoomAccountRepository
    private lateinit var categories: RoomCategoryRepository
    private val shared = SharedFilter()
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var store: PeriodStore

    /** Payday, 30 Oct 2026. */
    private val clock = object : Clock {
        override fun now(): Instant = LocalDateTime(2026, 10, 30, 9, 0).toInstant(zone)
    }

    private val payOctober = DateRange.ofPayMonth(LocalDate(2026, 10, 1), 30)
    private val payNovember = DateRange.ofPayMonth(LocalDate(2026, 10, 30), 30)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(context, SakuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        transactions = RoomTransactionRepository(db)
        accounts = RoomAccountRepository(db)
        categories = RoomCategoryRepository(db)
        store = PeriodStore(context)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun trans(periodStore: PeriodStore = store) = TransactionListViewModel(
        transactionRepository = transactions,
        accountRepository = accounts,
        categoryRepository = categories,
        sharedFilter = shared,
        periodStore = periodStore,
        zone = zone,
        clock = clock,
    )

    private fun stats(periodStore: PeriodStore = store) = StatsViewModel(
        transactionRepository = transactions,
        accountRepository = accounts,
        categoryRepository = categories,
        sharedFilter = shared,
        periodStore = periodStore,
        zone = zone,
        clock = clock,
    )

    @Test
    fun `the switch is hidden and months are calendar months while no pay cycle is set`() = runTest(dispatcher) {
        store.setView(MonthView.PAY)

        val transState = trans().state.first { !it.loading }
        val statsState = stats().state.first { !it.loading }

        assertNull(transState.monthView)
        assertNull(statsState.monthView)
        assertEquals(DateRange.ofMonth(LocalDate(2026, 10, 30)), transState.range)
        assertEquals(DateRange.ofMonth(LocalDate(2026, 10, 30)), statsState.range)
    }

    @Test
    fun `switching Stats to Pay month moves Trans too`() = runTest(dispatcher) {
        store.setStartDay(30)
        val statsVm = stats()
        val transVm = trans()
        assertEquals(MonthView.CALENDAR, transVm.state.first { !it.loading }.monthView)

        statsVm.selectMonthView(MonthView.PAY)

        assertEquals(payNovember, statsVm.state.first { it.range == payNovember }.range)
        val transState = transVm.state.first { it.range == payNovember }
        assertEquals(MonthView.PAY, transState.monthView)
    }

    @Test
    fun `a restart opens in Pay month`() = runTest(dispatcher) {
        store.setStartDay(30)
        store.setView(MonthView.PAY)

        val reopened = PeriodStore(context)

        assertEquals(payNovember, trans(reopened).state.first { !it.loading }.range)
        assertEquals(payNovember, stats(reopened).state.first { !it.loading }.range)
    }

    @Test
    fun `clearing the pay cycle hides the switch and returns to calendar months`() = runTest(dispatcher) {
        store.setStartDay(30)
        store.setView(MonthView.PAY)
        val transVm = trans()
        transVm.state.first { it.range == payNovember }

        store.setStartDay(null)

        val october = DateRange.ofMonth(LocalDate(2026, 10, 30))
        val state = transVm.state.first { it.range == october }
        assertNull(state.monthView)
    }

    @Test
    fun `switching the month view leaves the filter as it was`() = runTest(dispatcher) {
        val filter = TransactionFilter(accountIds = setOf(7L))
        shared.set(filter)
        store.setStartDay(30)
        val transVm = trans()

        transVm.selectMonthView(MonthView.PAY)

        assertEquals(filter, transVm.state.first { it.range == payNovember }.filter)
        assertEquals(filter, shared.state.value)
    }

    @Test
    fun `a payday income counts in the pay month it opens`() = runTest(dispatcher) {
        val bca = accounts.create("BCA", AccountType.BANK, Money.ZERO, LocalDateTime(2026, 1, 1, 0, 0).toInstant(zone))
        val gaji = categories.createCategory("Gaji", CategoryKind.INCOME)
        transactions.record(
            occurredAt = LocalDateTime(2026, 10, 30, 8, 0).toInstant(zone),
            type = TxnType.INCOME,
            amount = Money(8_000_000),
            accountId = bca,
            toAccountId = null,
            categoryId = gaji,
        )
        store.setStartDay(30)
        store.setView(MonthView.PAY)

        val transVm = trans()
        assertEquals(Money(8_000_000), transVm.state.first { !it.loading }.totals.income)

        // One step back is the pay month called October, which ends the day before payday.
        transVm.previousMonth()
        val october = transVm.state.first { it.range == payOctober }
        assertEquals(Money.ZERO, october.totals.income)
    }

    @Test
    fun `Stats compares a pay month with the pay month before it`() = runTest(dispatcher) {
        store.setStartDay(30)
        store.setView(MonthView.PAY)
        val vm = stats()
        vm.state.first { it.range == payNovember }

        vm.previousPeriod()

        val state = vm.state.first { it.range == payOctober }
        assertEquals(
            DateRange(LocalDate(2026, 8, 30), LocalDate(2026, 9, 29), PeriodMode.MONTHLY, 30),
            state.comparison.previousRange,
        )
    }

    @Test
    fun `the Stats month window is six pay months ending with the selected one`() = runTest(dispatcher) {
        store.setStartDay(30)
        store.setView(MonthView.PAY)

        val window = stats().state.first { !it.loading }.monthWindow

        assertEquals(6, window.size)
        assertEquals(payNovember, window.last())
        assertEquals(LocalDate(2026, 5, 30), window.first().start)
        window.zipWithNext().forEach { (earlier, later) -> assertEquals(earlier.next(), later) }
    }

    @Test
    fun `the Stats switch shows only in Monthly mode`() = runTest(dispatcher) {
        store.setStartDay(30)
        store.setView(MonthView.PAY)
        val vm = stats()
        assertEquals(MonthView.PAY, vm.state.first { !it.loading }.monthView)

        vm.selectMode(PeriodMode.WEEKLY)

        val weekly = vm.state.first { it.range.mode == PeriodMode.WEEKLY }
        assertNull(weekly.monthView)
        assertEquals(emptyList<DateRange>(), weekly.monthWindow)
        assertEquals(DateRange.ofWeek(LocalDate(2026, 10, 30)), weekly.range)

        vm.selectMode(PeriodMode.MONTHLY)
        assertEquals(payNovember, vm.state.first { it.range.mode == PeriodMode.MONTHLY }.range)
    }

    @Test
    fun `the Trans Monthly view lists pay months named in the year`() = runTest(dispatcher) {
        store.setStartDay(30)
        store.setView(MonthView.PAY)
        val vm = trans()
        vm.state.first { !it.loading }

        vm.selectTab(TransactionTab.MONTHLY)

        val year = vm.state.first { it.monthly != null }.monthly!!
        // 30 Dec 2025 opens the pay month called January 2026.
        assertEquals(LocalDate(2025, 12, 30), year.range.start)
        assertEquals(LocalDate(2026, 11, 29), year.months.first().range.endInclusive)
        assertEquals(Month.NOVEMBER, year.months.first().labelMonth)
        assertEquals(true, year.months.first().expanded)
        assertEquals(11, year.months.size)
    }
}
