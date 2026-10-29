package com.dvnnfrr.saku.ui.stats

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
import com.dvnnfrr.saku.domain.PeriodMode
import com.dvnnfrr.saku.domain.TxnType
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

    // The subcategory split, which is why the drill exists at all.

    @Test
    fun `a category's drill splits by subcategory and names the untagged remainder`() =
        runTest(dispatcher) {
            seed()
            val rutin = categories.createSubcategory(pemberian, "Rutin keluarga")
            record(LocalDateTime(2026, 8, 5, 12, 0), 800_000, categoryId = pemberian, subcategoryId = rutin)
            record(LocalDateTime(2026, 8, 6, 12, 0), 200_000, categoryId = pemberian)

            val vm = viewModel()
            vm.state.first { !it.loading }
            vm.openCategory(pemberian)

            val drill = vm.state.first { it.drill != null }.drill as DrillUiState.Category
            assertEquals(2, drill.subcategories.size)
            assertEquals("Rutin keluarga", drill.subcategories[0].name)
            assertEquals(Money(800_000), drill.subcategories[0].total)
            assertEquals(80, drill.subcategories[0].percent)
            assertTrue(drill.subcategories[1].isUnassigned)
            assertEquals(Money(200_000), drill.subcategories[1].total)
            assertEquals(100, drill.subcategories.sumOf { it.percent })
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
}
