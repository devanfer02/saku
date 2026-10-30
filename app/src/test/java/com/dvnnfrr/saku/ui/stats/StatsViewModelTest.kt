package com.dvnnfrr.saku.ui.stats

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dvnnfrr.saku.data.ChartStyleStore
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.data.repository.RoomAccountRepository
import com.dvnnfrr.saku.data.repository.RoomCategoryRepository
import com.dvnnfrr.saku.data.repository.RoomTransactionRepository
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.ChartStyle
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.PeriodMode
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every scenario in `specs/period-stats/spec.md` that needs a query behind it, run
 * against real repositories on an in-memory database rather than against fakes.
 *
 * Stats aggregates in SQL because it reports on periods it does not list, so faking the
 * repository would test the fake's arithmetic instead of the query's. The pure folds are
 * covered separately in `StatsAssemblyTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatsViewModelTest {

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
    private var pemberian = 0L
    private var gaji = 0L

    /** Parked inside August 2026, so the default period is deterministic. */
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
        val asOf = LocalDateTime(2025, 1, 1, 0, 0).toInstant(zone)
        bca = accounts.create("BCA", AccountType.BANK, Money.ZERO, asOf)
        rdpu = accounts.create("RDPU", AccountType.INVESTMENT, Money.ZERO, asOf)
        makan = categories.createCategory("Makan & Minum", CategoryKind.EXPENSE)
        pemberian = categories.createCategory("Pemberian", CategoryKind.EXPENSE)
        gaji = categories.createCategory("Gaji", CategoryKind.INCOME)
    }

    private suspend fun record(
        at: LocalDateTime,
        amount: Long,
        type: TxnType = TxnType.EXPENSE,
        categoryId: Long? = makan,
        subcategoryId: Long? = null,
        toAccountId: Long? = null,
    ) = transactions.record(
        occurredAt = at.toInstant(zone),
        type = type,
        amount = Money(amount),
        accountId = bca,
        toAccountId = toAccountId,
        categoryId = categoryId,
        subcategoryId = subcategoryId,
    )

    private suspend fun spend(day: Int, amount: Long, categoryId: Long = makan, hour: Int = 12) =
        record(LocalDateTime(2026, 8, day, hour, 0), amount, categoryId = categoryId)

    private fun viewModel() = StatsViewModel(
        transactionRepository = transactions,
        accountRepository = accounts,
        categoryRepository = categories,
        sharedFilter = shared,
        chartStyleStore = ChartStyleStore(ApplicationProvider.getApplicationContext()),
        zone = zone,
        clock = clock,
    )

    // Requirement: default to the current month, offer four period modes.

    @Test
    fun `the screen opens on the current month with Expenses selected`() = runTest(dispatcher) {
        seed()
        spend(day = 5, amount = 200_000)

        val state = viewModel().state.first { !it.loading }

        assertEquals(PeriodMode.MONTHLY, state.range.mode)
        assertEquals(LocalDate(2026, 8, 1), state.range.start)
        assertEquals(LocalDate(2026, 8, 31), state.range.endInclusive)
        assertEquals(StatsSide.EXPENSES, state.side)
        assertEquals(Money(200_000), state.totals.expense)
    }

    @Test
    fun `the state carries today from the clock for the period picker`() = runTest(dispatcher) {
        seed()

        val state = viewModel().state.first { !it.loading }

        assertEquals(LocalDate(2026, 8, 17), state.today)
    }

    @Test
    fun `a custom period recomputes every figure for that range`() = runTest(dispatcher) {
        seed()
        record(LocalDateTime(2026, 1, 20, 12, 0), 111_000)
        record(LocalDateTime(2026, 3, 10, 12, 0), 222_000)
        record(LocalDateTime(2026, 6, 15, 12, 0), 333_000)
        record(LocalDateTime(2026, 8, 2, 12, 0), 444_000)

        val vm = viewModel()
        vm.state.first { !it.loading }
        vm.selectRange(LocalDate(2026, 2, 1), LocalDate(2026, 7, 31))

        val state = vm.state.first { it.range.mode == PeriodMode.CUSTOM }
        assertEquals(LocalDate(2026, 2, 1), state.range.start)
        assertEquals(LocalDate(2026, 7, 31), state.range.endInclusive)
        // January and August fall outside; March and June are inside.
        assertEquals(Money(555_000), state.totals.expense)
        assertEquals(Money(555_000), state.composition.total)
    }

    @Test
    fun `stepping back moves the range one calendar month and recomputes`() =
        runTest(dispatcher) {
            seed()
            record(LocalDateTime(2026, 7, 14, 12, 0), 750_000)
            spend(day = 14, amount = 250_000)

            val vm = viewModel()
            assertEquals(Money(250_000), vm.state.first { !it.loading }.totals.expense)

            vm.previousPeriod()

            val july = vm.state.first { it.range.start == LocalDate(2026, 7, 1) }
            assertEquals(LocalDate(2026, 7, 31), july.range.endInclusive)
            assertEquals(Money(750_000), july.totals.expense)
        }

    @Test
    fun `switching mode re-anchors on today and steps in whole periods`() =
        runTest(dispatcher) {
            seed()

            val vm = viewModel()
            vm.state.first { !it.loading }
            vm.selectMode(PeriodMode.ANNUALLY)

            val year = vm.state.first { it.range.mode == PeriodMode.ANNUALLY }
            assertEquals(LocalDate(2026, 1, 1), year.range.start)
            assertEquals(LocalDate(2026, 12, 31), year.range.endInclusive)

            vm.previousPeriod()
            val before = vm.state.first { it.range.start == LocalDate(2025, 1, 1) }
            assertEquals(LocalDate(2025, 12, 31), before.range.endInclusive)
        }

    // Requirement: separate income and expense views.

    @Test
    fun `a transfer appears in neither tab`() = runTest(dispatcher) {
        seed()
        spend(day = 16, amount = 5_061_400)
        record(
            at = LocalDateTime(2026, 8, 14, 12, 0),
            amount = 500_000,
            type = TxnType.TRANSFER,
            categoryId = null,
            toAccountId = rdpu,
        )

        val vm = viewModel()
        val expenses = vm.state.first { !it.loading }

        assertEquals(Money(5_061_400), expenses.totals.expense)
        assertEquals(Money(5_061_400), expenses.sideTotal)
        assertEquals(Money(5_061_400), expenses.composition.total)

        vm.selectSide(StatsSide.INCOME)
        val income = vm.state.first { it.side == StatsSide.INCOME }
        assertEquals(Money.ZERO, income.sideTotal)
        assertTrue(income.composition.isEmpty)
    }

    @Test
    fun `the income tab reports only income categories`() = runTest(dispatcher) {
        seed()
        spend(day = 5, amount = 200_000)
        record(LocalDateTime(2026, 8, 25, 12, 0), 14_800_000, TxnType.INCOME, categoryId = gaji)

        val vm = viewModel()
        vm.state.first { !it.loading }
        vm.selectSide(StatsSide.INCOME)

        val state = vm.state.first { it.side == StatsSide.INCOME && !it.loading }
        assertEquals(listOf("Gaji"), state.composition.rows.map { it.name })
        assertEquals(Money(14_800_000), state.sideTotal)
    }

    // Requirement: rank categories by amount with share and value.

    @Test
    fun `the SQL aggregate ranks categories and the shares sum to one hundred`() =
        runTest(dispatcher) {
            seed()
            spend(day = 5, amount = 1_257_700, categoryId = makan)
            spend(day = 6, amount = 1_050_000, categoryId = pemberian)
            spend(day = 7, amount = 300_000, categoryId = makan)

            val state = viewModel().state.first { !it.loading }

            assertEquals(
                listOf("Makan & Minum", "Pemberian"),
                state.composition.rows.map { it.name },
            )
            assertEquals(
                listOf(1_557_700L, 1_050_000L),
                state.composition.rows.map { it.total.rupiah },
            )
            assertEquals(100, state.composition.rows.sumOf { it.percent })
        }

    // Requirement: a period with no data.

    @Test
    fun `a period with no data composes to nothing and totals zero`() = runTest(dispatcher) {
        seed()
        spend(day = 5, amount = 200_000)

        val vm = viewModel()
        vm.state.first { !it.loading }
        vm.previousPeriod()

        val july = vm.state.first { it.range.start == LocalDate(2026, 7, 1) }
        assertTrue(july.composition.isEmpty)
        assertEquals(Money.ZERO, july.sideTotal)
        assertEquals(Money.ZERO, july.totals.net)
    }

    // The comparison against the previous equivalent period.

    @Test
    fun `the period is compared against the one before it`() = runTest(dispatcher) {
        seed()
        record(LocalDateTime(2026, 7, 10, 12, 0), 5_270_000)
        spend(day = 10, amount = 5_061_400)

        val state = viewModel().state.first { !it.loading }

        assertEquals(DateRange.ofMonth(LocalDate(2026, 7, 1)), state.comparison.previousRange)
        assertEquals(Money(5_270_000), state.comparison.previous.expense)
        assertEquals(Money(-208_600), state.comparison.expenseDelta)
        assertEquals(-4, state.comparison.expenseChangePercent)
    }

    @Test
    fun `a first-ever period offers no percentage to divide by`() = runTest(dispatcher) {
        seed()
        spend(day = 10, amount = 5_061_400)

        val state = viewModel().state.first { !it.loading }

        assertTrue(!state.comparison.hasBaseline)
        assertNull(state.comparison.expenseChangePercent)
    }

    // Requirement: drill through to the underlying transactions.

    @Test
    fun `opening a category lists its transactions with the same total as the row`() =
        runTest(dispatcher) {
            seed()
            spend(day = 3, amount = 170_000, categoryId = makan)
            spend(day = 9, amount = 45_000, categoryId = makan)
            spend(day = 11, amount = 1_050_000, categoryId = pemberian)

            val vm = viewModel()
            val overview = vm.state.first { !it.loading }
            val row = overview.composition.rows.first { it.categoryId == makan }
            assertEquals(Money(215_000), row.total)

            vm.openCategory(makan)

            val drilled = vm.state.first { it.drill != null }
            val drill = drilled.drill as DrillUiState.Category
            assertEquals("Makan & Minum", drill.title)
            assertEquals(row.total, drill.total)
            assertEquals(2, drill.rows.size)
            assertTrue(drill.rows.all { it.categoryName == "Makan & Minum" })
        }

    @Test
    fun `the drill excludes a transfer tagged with the same category`() = runTest(dispatcher) {
        seed()
        spend(day = 3, amount = 170_000, categoryId = makan)
        // observeCategoryTotals filters by type, so the drill must too — otherwise the
        // list opens with a total that does not match the row that opened it.
        record(
            at = LocalDateTime(2026, 8, 4, 12, 0),
            amount = 500_000,
            type = TxnType.TRANSFER,
            categoryId = makan,
            toAccountId = rdpu,
        )

        val vm = viewModel()
        val row = vm.state.first { !it.loading }.composition.rows.single()
        vm.openCategory(makan)

        val drill = vm.state.first { it.drill != null }.drill as DrillUiState.Category
        assertEquals(row.total, drill.total)
        assertEquals(1, drill.rows.size)
    }

    @Test
    fun `drilling into Other lists the categories it combines before any transactions`() =
        runTest(dispatcher) {
            seed()
            // Nine categories on one side, so the ninth onward is aggregated.
            val ids = (1..9).map { categories.createCategory("Cat $it", CategoryKind.EXPENSE) }
            ids.forEachIndexed { index, id ->
                spend(day = index + 1, amount = 1_000_000L - index * 10_000L, categoryId = id)
            }

            val vm = viewModel()
            val overview = vm.state.first { !it.loading }
            assertEquals(9, overview.composition.rows.size)
            assertNotNull(overview.composition.other)

            vm.openOther()

            val drill = vm.state.first { it.drill != null }.drill
            assertTrue(drill is DrillUiState.Other)
            drill as DrillUiState.Other
            assertEquals(listOf("Cat 9"), drill.members.map { it.name })
            assertEquals(overview.composition.other!!.total, drill.total)
        }

    @Test
    fun `switching side closes an open drill`() = runTest(dispatcher) {
        seed()
        spend(day = 3, amount = 170_000)

        val vm = viewModel()
        vm.state.first { !it.loading }
        vm.openCategory(makan)
        vm.state.first { it.drill != null }

        vm.selectSide(StatsSide.INCOME)

        assertNull(vm.state.first { it.side == StatsSide.INCOME }.drill)
    }

    // Stored subcategories are no longer split out; the drill lists every row as one.

    @Test
    fun `a category's drill lists rows with and without a stored subcategory as one`() =
        runTest(dispatcher) {
            seed()
            val rutin = categories.findOrCreateSubcategory(pemberian, "Rutin keluarga")
            record(LocalDateTime(2026, 8, 5, 12, 0), 800_000, categoryId = pemberian, subcategoryId = rutin)
            record(LocalDateTime(2026, 8, 6, 12, 0), 200_000, categoryId = pemberian)

            val vm = viewModel()
            vm.state.first { !it.loading }
            vm.openCategory(pemberian)

            val drill = vm.state.first { it.drill != null }.drill as DrillUiState.Category
            assertEquals(Money(1_000_000), drill.total)
            assertEquals(2, drill.count)
            assertEquals(2, drill.rows.size)
        }

    // The zone boundary.

    @Test
    fun `a transaction at one second to midnight belongs to that month in the device zone`() =
        runTest(dispatcher) {
            seed()
            // 31 Aug 23:59:59 Jakarta. Included.
            record(LocalDateTime(2026, 8, 31, 23, 59, 59), 100_000)
            // 31 Jul 23:59:59 Jakarta. Excluded — one second before the month opens.
            record(LocalDateTime(2026, 7, 31, 23, 59, 59), 200_000)
            // 1 Sep 00:00:00 Jakarta is 31 Aug 17:00 UTC. Excluded, and the row that
            // proves the bounds are the device's rather than UTC's: a UTC-bounded query
            // would count it as August.
            record(LocalDateTime(2026, 9, 1, 0, 0, 0), 400_000)

            val state = viewModel().state.first { !it.loading }

            assertEquals(Money(100_000), state.totals.expense)
            assertEquals(Money(100_000), state.composition.total)

            // And the excluded rows land where they belong rather than nowhere.
            assertEquals(
                Money(200_000),
                transactions.observePeriodTotals(august.previous(), zone, TransactionFilter.ALL).first().expense,
            )
            assertEquals(
                Money(400_000),
                transactions.observePeriodTotals(august.next(), zone, TransactionFilter.ALL).first().expense,
            )
        }

    // transaction-filter: every figure respects the filter.

    @Test
    fun `a category filter narrows the totals and the ranked shares sum to 100`() = runTest(dispatcher) {
        seed()
        val jajan = categories.createCategory("Jajan & Hiburan", CategoryKind.EXPENSE)
        spend(day = 5, amount = 1_257_700)
        spend(day = 6, amount = 843_800, categoryId = jajan)
        spend(day = 7, amount = 1_050_000, categoryId = pemberian)

        shared.set(TransactionFilter(expenseCategoryIds = setOf(makan, jajan)))
        val state = viewModel().state.first { !it.loading && it.filter.isActive }

        assertEquals(Money(2_101_500), state.totals.expense)
        assertEquals(listOf(makan, jajan), state.composition.rows.map { it.categoryId })
        assertEquals(listOf(60, 40), state.composition.rows.map { it.percent })
    }

    @Test
    fun `the comparison runs under the same filter`() = runTest(dispatcher) {
        seed()
        spend(day = 5, amount = 300_000)
        spend(day = 6, amount = 900_000, categoryId = pemberian)
        record(LocalDateTime(2026, 7, 5, 12, 0), 200_000)
        record(LocalDateTime(2026, 7, 6, 12, 0), 800_000, categoryId = pemberian)

        shared.set(TransactionFilter(expenseCategoryIds = setOf(makan)))
        val state = viewModel().state.first { !it.loading && it.filter.isActive }

        assertEquals(Money(200_000), state.comparison.previous.expense)
        assertEquals(Money(300_000), state.comparison.current.expense)
    }

    @Test
    fun `a drilled category that leaves the selection closes the drill`() = runTest(dispatcher) {
        seed()
        spend(day = 5, amount = 300_000)
        spend(day = 6, amount = 900_000, categoryId = pemberian)
        val vm = viewModel()
        vm.state.first { !it.loading }
        vm.openCategory(pemberian)
        assertNotNull(vm.state.first { it.drill != null }.drill)

        shared.set(TransactionFilter(expenseCategoryIds = setOf(makan)))

        val state = vm.state.first { it.filter.isActive && it.drill == null }
        assertNull(state.drill)
        assertEquals(Money(300_000), state.totals.expense)
    }

    @Test
    fun `a drilled category still selected stays open and is filtered`() = runTest(dispatcher) {
        seed()
        spend(day = 5, amount = 300_000)
        record(LocalDateTime(2026, 8, 6, 12, 0), 150_000, toAccountId = null)
        val vm = viewModel()
        vm.state.first { !it.loading }
        vm.openCategory(makan)
        vm.state.first { it.drill != null }

        shared.set(TransactionFilter(accountIds = setOf(rdpu)))

        val drill = vm.state.first { it.filter.isActive && it.drill != null }.drill
        assertEquals(Money.ZERO, drill?.total)
    }

    // buku-kas: the strip over all history.

    @Test
    fun `the strip runs three months past the selected month and agrees with the header`() = runTest(dispatcher) {
        seed()
        spend(day = 5, amount = 300_000)
        record(LocalDateTime(2026, 6, 10, 12, 0), 500_000)
        record(LocalDateTime(2026, 8, 1, 9, 0), 2_000_000, type = TxnType.INCOME, categoryId = gaji)

        val state = viewModel().state.first { !it.loading && it.strip.isNotEmpty() }

        // June holds the first transaction, so the strip starts there; August is both
        // today's month and the selected one, so it runs on to November.
        assertEquals((6..11).map { DateRange.ofMonth(LocalDate(2026, it, 1)) }, state.strip.map { it.range })
        val august = state.strip.single { it.selected }
        assertEquals(DateRange.ofMonth(LocalDate(2026, 8, 1)), august.range)
        assertEquals(state.totals.expense, august.expense)
        assertEquals(state.totals.income, august.income)
        // July is a quiet month after history began: a zero, not an empty slot.
        assertTrue(state.strip.none { it.empty })
        assertEquals(Money.ZERO, state.strip[1].expense)
    }

    @Test
    fun `the strip respects the filter and leaves transfers out`() = runTest(dispatcher) {
        seed()
        spend(day = 5, amount = 300_000)
        spend(day = 6, amount = 900_000, categoryId = pemberian)
        record(LocalDateTime(2026, 8, 7, 12, 0), 1_000_000, type = TxnType.TRANSFER, categoryId = null, toAccountId = rdpu)

        shared.set(TransactionFilter(expenseCategoryIds = setOf(makan)))
        val state = viewModel().state.first { it.filter.isActive && it.strip.isNotEmpty() }

        val august = state.strip.single { it.selected }
        assertEquals(Money(300_000), august.expense)
        assertEquals(Money.ZERO, august.income)
    }

    @Test
    fun `a future-dated transaction appears in its own strip column`() = runTest(dispatcher) {
        seed()
        spend(day = 5, amount = 300_000)
        record(LocalDateTime(2026, 10, 5, 12, 0), 700_000)

        val state = viewModel().state.first { !it.loading && it.strip.isNotEmpty() }

        // October is after today and the selection, and is still queried and folded.
        val october = state.strip.single { it.range == DateRange.ofMonth(LocalDate(2026, 10, 1)) }
        assertEquals(Money(700_000), october.expense)
        assertEquals(DateRange.ofMonth(LocalDate(2027, 1, 1)), state.strip.last().range)
        // The header still reports August alone.
        assertEquals(Money(300_000), state.totals.expense)
    }

    @Test
    fun `tapping a month in the strip selects it`() = runTest(dispatcher) {
        seed()
        record(LocalDateTime(2026, 5, 10, 12, 0), 500_000)
        spend(day = 5, amount = 300_000)
        val vm = viewModel()
        vm.state.first { it.strip.isNotEmpty() }

        vm.selectPeriod(DateRange.ofMonth(LocalDate(2026, 6, 1)))

        val state = vm.state.first { it.range.start == LocalDate(2026, 6, 1) && it.strip.selectedRange() == it.range }
        assertEquals(state.range, state.strip.single { it.selected }.range)
        // The months after June stay in the strip.
        assertEquals(DateRange.ofMonth(LocalDate(2026, 11, 1)), state.strip.last().range)
        assertEquals(PeriodMode.MONTHLY, state.range.mode)
    }

    @Test
    fun `the strip follows Weekly and Annually and leaves Period`() = runTest(dispatcher) {
        seed()
        record(LocalDateTime(2025, 7, 20, 12, 0), 500_000)
        spend(day = 5, amount = 300_000)
        val vm = viewModel()
        vm.state.first { it.strip.isNotEmpty() }

        vm.selectMode(PeriodMode.WEEKLY)
        val weekly = vm.state.first { it.range.mode == PeriodMode.WEEKLY && it.strip.isNotEmpty() }
        assertTrue(weekly.strip.all { it.range.mode == PeriodMode.WEEKLY })
        assertEquals(DateRange.ofWeek(LocalDate(2025, 7, 20)), weekly.strip.first().range)
        assertEquals(weekly.range, weekly.strip.selectedRange())
        assertEquals(DateRange.ofWeek(LocalDate(2026, 9, 7)), weekly.strip.last().range)

        vm.selectMode(PeriodMode.ANNUALLY)
        val annual = vm.state.first { it.range.mode == PeriodMode.ANNUALLY && it.strip.isNotEmpty() }
        assertEquals((2025..2029).map { DateRange.ofYear(it) }, annual.strip.map { it.range })

        vm.selectRange(LocalDate(2026, 8, 1), LocalDate(2026, 8, 10))
        val custom = vm.state.first { it.range.mode == PeriodMode.CUSTOM }
        assertTrue(custom.strip.isEmpty())
    }

    @Test
    fun `tapping a week in the strip selects that week`() = runTest(dispatcher) {
        seed()
        spend(day = 5, amount = 300_000)
        val vm = viewModel()
        vm.selectMode(PeriodMode.WEEKLY)
        vm.state.first { it.range.mode == PeriodMode.WEEKLY && it.strip.isNotEmpty() }

        val week = DateRange.ofWeek(LocalDate(2026, 8, 3))
        vm.selectPeriod(week)

        val state = vm.state.first { it.range == week && it.strip.selectedRange() == week }
        assertEquals(Money(300_000), state.strip.single { it.selected }.expense)
    }

    // buku-kas: chart style and the category detail.

    @Test
    fun `the chart style defaults to bars and survives a new ViewModel`() = runTest(dispatcher) {
        seed()
        spend(day = 5, amount = 300_000)
        val vm = viewModel()
        assertEquals(ChartStyle.BARS, vm.state.first { !it.loading }.chartStyle)

        vm.selectChartStyle(ChartStyle.PIE)
        vm.state.first { it.chartStyle == ChartStyle.PIE }

        // A restart is a new ViewModel over the same preferences.
        assertEquals(ChartStyle.PIE, viewModel().state.first { !it.loading }.chartStyle)
    }

    @Test
    fun `the category detail carries share, count, average and its own strip`() = runTest(dispatcher) {
        seed()
        record(LocalDateTime(2026, 6, 10, 12, 0), 400_000, categoryId = pemberian)
        spend(day = 3, amount = 300_000, categoryId = pemberian)
        spend(day = 9, amount = 200_001, categoryId = pemberian)
        spend(day = 5, amount = 500_000)
        record(LocalDateTime(2026, 8, 1, 9, 0), 2_000_000, type = TxnType.INCOME, categoryId = gaji)
        val vm = viewModel()
        vm.state.first { !it.loading }

        vm.openCategory(pemberian)
        val drill = vm.state.first { (it.drill as? DrillUiState.Category)?.strip?.isNotEmpty() == true }
            .drill as DrillUiState.Category

        assertEquals(Money(500_001), drill.total)
        assertEquals(50, drill.percent)
        assertEquals(2, drill.count)
        assertEquals(Money(250_001), drill.average)
        // June to August is Pemberian's history, its expenses only, nothing of Makan or
        // Gaji; September to November are the three months past it.
        assertEquals((6..11).map { DateRange.ofMonth(LocalDate(2026, it, 1)) }, drill.strip.map { it.range })
        assertEquals(listOf(400_000L, 0L, 500_001L, 0L, 0L, 0L), drill.strip.map { it.expense.rupiah })
        assertTrue(drill.strip.all { it.income.isZero })
    }

    @Test
    fun `the category strip respects the account filter`() = runTest(dispatcher) {
        seed()
        spend(day = 3, amount = 300_000, categoryId = pemberian)
        val vm = viewModel()
        vm.state.first { !it.loading }
        vm.openCategory(pemberian)
        vm.state.first { (it.drill as? DrillUiState.Category)?.strip?.isNotEmpty() == true }

        shared.set(TransactionFilter(accountIds = setOf(rdpu)))

        val drill = vm.state.first {
            it.filter.isActive && (it.drill as? DrillUiState.Category)?.strip?.isNotEmpty() == true
        }.drill as DrillUiState.Category
        assertTrue(drill.strip.single().empty)
    }

    private fun List<StripColumn>.selectedRange(): DateRange? = firstOrNull { it.selected }?.range
}
