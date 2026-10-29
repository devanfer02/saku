package com.dvnnfrr.saku.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.data.repository.RoomAccountRepository
import com.dvnnfrr.saku.data.repository.RoomCategoryRepository
import com.dvnnfrr.saku.data.repository.RoomTransactionRepository
import com.dvnnfrr.saku.domain.AccountFlows
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TransactionFilter
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `TXN_FILTER_WHERE` against a real database, through every query that concatenates it.
 *
 * The fragment is the one implementation of the filter rule, so these are the scenarios of
 * `specs/transaction-filter/spec.md` that the SQL decides: which rows pass, how transfers and
 * adjustments behave, what "none ticked" (`IN ()`) does, and the per-account figures.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FilterQueryTest {

    private val zone = TimeZone.of("Asia/Jakarta")
    private val october = DateRange.ofMonth(LocalDate(2026, 10, 1))

    private lateinit var db: SakuDatabase
    private lateinit var transactions: RoomTransactionRepository

    private var bca = 0L
    private var jago = 0L
    private var cash = 0L
    private var rdpu = 0L
    private var makan = 0L
    private var jajan = 0L
    private var pemberian = 0L
    private var gaji = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SakuDatabase::class.java,
        ).allowMainThreadQueries().build()
        transactions = RoomTransactionRepository(db)
        val accounts = RoomAccountRepository(db)
        val categories = RoomCategoryRepository(db)

        val asOf = Instant.fromEpochSeconds(0)
        bca = accounts.create("BCA", AccountType.BANK, Money.ZERO, asOf)
        jago = accounts.create("Jago", AccountType.BANK, Money.ZERO, asOf)
        cash = accounts.create("Cash", AccountType.CASH, Money.ZERO, asOf)
        rdpu = accounts.create("RDPU SucorInvest", AccountType.INVESTMENT, Money.ZERO, asOf)
        makan = categories.createCategory("Makan & Minum", CategoryKind.EXPENSE)
        jajan = categories.createCategory("Jajan & Hiburan", CategoryKind.EXPENSE)
        pemberian = categories.createCategory("Pemberian", CategoryKind.EXPENSE)
        gaji = categories.createCategory("Gaji", CategoryKind.INCOME)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun record(
        day: Int,
        type: TxnType,
        amount: Long,
        account: Long,
        category: Long? = null,
        to: Long? = null,
        month: Int = 10,
    ) = transactions.record(
        occurredAt = LocalDateTime(2026, month, day, 12, 0).toInstant(zone),
        type = type,
        amount = Money(amount),
        accountId = account,
        toAccountId = to,
        categoryId = category,
    )

    private suspend fun rows(filter: TransactionFilter) =
        transactions.observeInRange(october, zone, filter).first()

    private suspend fun totals(filter: TransactionFilter) =
        transactions.observePeriodTotals(october, zone, filter).first()

    @Test
    fun `category and account selections apply together`() = runTest {
        val cashMeal = record(3, TxnType.EXPENSE, 45_000, cash, makan)
        record(4, TxnType.EXPENSE, 60_000, bca, makan)
        record(5, TxnType.EXPENSE, 70_000, cash, jajan)

        val filter = TransactionFilter(expenseCategoryIds = setOf(makan), accountIds = setOf(cash))

        assertEquals(listOf(cashMeal), rows(filter).map { it.id })
        assertEquals(Money(45_000), totals(filter).expense)
    }

    @Test
    fun `a transfer passes on its destination leg and counts toward nothing`() = runTest {
        record(2, TxnType.INCOME, 8_000_000, bca, gaji)
        val transfer = record(14, TxnType.TRANSFER, 500_000, bca, to = rdpu)

        val filter = TransactionFilter(accountIds = setOf(rdpu))

        assertEquals(listOf(transfer), rows(filter).map { it.id })
        assertEquals(Money.ZERO, totals(filter).income)
        assertEquals(Money.ZERO, totals(filter).expense)
    }

    @Test
    fun `a transfer passes on its source leg too`() = runTest {
        val transfer = record(14, TxnType.TRANSFER, 500_000, bca, to = rdpu)

        assertEquals(listOf(transfer), rows(TransactionFilter(accountIds = setOf(bca))).map { it.id })
        assertTrue(rows(TransactionFilter(accountIds = setOf(jago))).isEmpty())
    }

    @Test
    fun `transfers and adjustments are hidden under a category filter`() = runTest {
        val gift = record(1, TxnType.EXPENSE, 1_050_000, bca, pemberian)
        record(14, TxnType.TRANSFER, 500_000, bca, to = rdpu)
        record(15, TxnType.ADJUSTMENT, -53_500, cash)

        assertEquals(3, rows(TransactionFilter.ALL).size)
        assertEquals(
            listOf(gift),
            rows(TransactionFilter(expenseCategoryIds = setOf(pemberian))).map { it.id },
        )
        // Narrowing the income side alone hides them as well: they have no category.
        assertEquals(
            listOf(gift),
            rows(TransactionFilter(incomeCategoryIds = setOf(gaji))).map { it.id },
        )
    }

    @Test
    fun `an adjustment passes on its account`() = runTest {
        val adjustment = record(15, TxnType.ADJUSTMENT, -53_500, cash)

        assertEquals(listOf(adjustment), rows(TransactionFilter(accountIds = setOf(cash))).map { it.id })
        assertTrue(rows(TransactionFilter(accountIds = setOf(bca))).isEmpty())
    }

    @Test
    fun `an empty income selection hides the income side only`() = runTest {
        record(2, TxnType.INCOME, 8_000_000, bca, gaji)
        record(3, TxnType.EXPENSE, 45_000, cash, makan)
        record(4, TxnType.EXPENSE, 70_000, bca, jajan)

        val filter = TransactionFilter(incomeCategoryIds = emptySet())

        assertEquals(setOf(TxnType.EXPENSE), rows(filter).map { it.type }.toSet())
        assertEquals(Money.ZERO, totals(filter).income)
        assertEquals(Money(115_000), totals(filter).expense)
    }

    @Test
    fun `empty expense and account selections match nothing`() = runTest {
        record(2, TxnType.INCOME, 8_000_000, bca, gaji)
        record(3, TxnType.EXPENSE, 45_000, cash, makan)

        assertEquals(
            setOf(TxnType.INCOME),
            rows(TransactionFilter(expenseCategoryIds = emptySet())).map { it.type }.toSet(),
        )
        assertTrue(rows(TransactionFilter(accountIds = emptySet())).isEmpty())
        assertEquals(Money.ZERO, totals(TransactionFilter(accountIds = emptySet())).expense)
    }

    @Test
    fun `the joined category totals query takes the filter`() = runTest {
        record(3, TxnType.EXPENSE, 1_257_700, bca, makan)
        record(4, TxnType.EXPENSE, 843_800, bca, jajan)
        record(5, TxnType.EXPENSE, 1_050_000, bca, pemberian)
        record(6, TxnType.EXPENSE, 99_000, cash, makan)

        val filter = TransactionFilter(
            expenseCategoryIds = setOf(makan, jajan),
            accountIds = setOf(bca),
        )
        val slices = transactions.observeCategoryTotals(october, zone, TxnType.EXPENSE, filter).first()

        assertEquals(listOf(makan, jajan), slices.map { it.categoryId })
        assertEquals(listOf(Money(1_257_700), Money(843_800)), slices.map { it.total })
        assertEquals(Money(2_101_500), totals(filter).expense)
    }

    @Test
    fun `daily totals and the drill queries take the filter`() = runTest {
        record(3, TxnType.EXPENSE, 45_000, cash, makan)
        record(3, TxnType.EXPENSE, 60_000, bca, makan)
        record(4, TxnType.EXPENSE, 70_000, bca, jajan)
        val cashOnly = TransactionFilter(accountIds = setOf(cash))

        val days = transactions.observeDailyTotals(october, zone, cashOnly).first()
        assertEquals(listOf(Money(45_000)), days.map { it.expense })

        val drill = transactions.observeForCategory(october, zone, makan, TxnType.EXPENSE, cashOnly).first()
        assertEquals(listOf(Money(45_000)), drill.map { it.amount })

        val other = transactions
            .observeForCategories(october, zone, listOf(makan, jajan), TxnType.EXPENSE, cashOnly)
            .first()
        assertEquals(listOf(Money(45_000)), other.map { it.amount })
    }

    @Test
    fun `observeAll spans every period, newest first, under the filter`() = runTest {
        val march = record(10, TxnType.EXPENSE, 25_000, cash, makan, month = 3)
        val lately = record(10, TxnType.EXPENSE, 32_000, cash, makan)
        record(11, TxnType.EXPENSE, 47_500, bca, makan)

        val found = transactions.observeAll(TransactionFilter(accountIds = setOf(cash))).first()

        assertEquals(listOf(lately, march), found.map { it.id })
        assertEquals(3, transactions.observeAll(TransactionFilter.ALL).first().size)
    }

    @Test
    fun `account flows split both transfer legs and leave out adjustments`() = runTest {
        record(1, TxnType.INCOME, 8_000_000, bca, gaji)
        record(2, TxnType.EXPENSE, 2_400_000, bca, pemberian)
        record(14, TxnType.TRANSFER, 500_000, bca, to = rdpu)
        record(20, TxnType.TRANSFER, 300_000, jago, to = bca)
        record(25, TxnType.ADJUSTMENT, -53_500, cash)
        record(26, TxnType.ADJUSTMENT, 10_000, bca)

        val flows = transactions.observeAccountFlows(october, zone).first().associateBy { it.accountId }

        assertEquals(
            AccountFlows(bca, Money(8_000_000), Money(2_400_000), Money(300_000), Money(500_000)),
            flows[bca],
        )
        assertEquals(AccountFlows(rdpu, Money.ZERO, Money.ZERO, Money(500_000), Money.ZERO), flows[rdpu])
        assertEquals(AccountFlows(jago, Money.ZERO, Money.ZERO, Money.ZERO, Money(300_000)), flows[jago])
        // Cash only had an adjustment, so nothing moved through it.
        assertEquals(null, flows[cash])
    }
}
