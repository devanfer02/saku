package com.dvnnfrr.saku.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dvnnfrr.saku.data.local.AccountEntity
import com.dvnnfrr.saku.data.local.CategoryEntity
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.data.local.TxnEntity
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the exclusion rule that recurs across three specs: transfers and adjustments
 * appear in lists but never in an income or expense figure. Moving money between the
 * owner's own accounts is not spending; an adjustment corrects a balance.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PeriodAggregateTest {

    private lateinit var db: SakuDatabase
    private val jakarta = TimeZone.of("Asia/Jakarta")
    private val august = DateRange.ofMonth(LocalDate(2026, 8, 1))

    private var bca = 0L
    private var rdpu = 0L
    private var food = 0L
    private var giving = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SakuDatabase::class.java,
        ).allowMainThreadQueries().build()

        bca = db.accountDao().insert(
            AccountEntity(
                name = "BCA", type = AccountType.BANK, openingBalance = 0,
                openingAsOfEpochSec = 0, colorSlot = 0, sortOrder = 0,
            ),
        )
        rdpu = db.accountDao().insert(
            AccountEntity(
                name = "RDPU", type = AccountType.INVESTMENT, openingBalance = 0,
                openingAsOfEpochSec = 0, colorSlot = 1, sortOrder = 1,
            ),
        )
        food = db.categoryDao().insert(
            CategoryEntity(name = "Makan & Minum", kind = CategoryKind.EXPENSE, colorSlot = 1, sortOrder = 0),
        )
        giving = db.categoryDao().insert(
            CategoryEntity(name = "Pemberian", kind = CategoryKind.EXPENSE, colorSlot = 2, sortOrder = 1),
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun txn(
        type: TxnType,
        amount: Long,
        at: String,
        categoryId: Long? = null,
        toAccountId: Long? = null,
        hash: String = "$type-$amount-$at",
    ) = db.txnDao().insert(
        TxnEntity(
            occurredAtEpochSec = Instant.parse(at).epochSeconds,
            type = type,
            amountIdr = amount,
            accountId = bca,
            toAccountId = toAccountId,
            categoryId = categoryId,
            createdAtEpochSec = Instant.parse(at).epochSeconds,
            dedupHash = hash,
        ),
    )

    @Test
    fun `period totals exclude transfers and adjustments`() = runTest {
        txn(TxnType.EXPENSE, 5_061_400, "2026-08-16T10:00:00Z", categoryId = food)
        txn(TxnType.TRANSFER, 500_000, "2026-08-14T10:00:00Z", toAccountId = rdpu)
        txn(TxnType.ADJUSTMENT, -53_500, "2026-08-15T10:00:00Z")

        val totals = db.txnDao()
            .observePeriodTotals(august.startSecFor(jakarta), august.endSecFor(jakarta))
            .first()

        assertEquals(0L, totals.income)
        assertEquals(5_061_400L, totals.expense)
        assertEquals(-5_061_400L, totals.net)
    }

    @Test
    fun `transactions outside the period are excluded`() = runTest {
        txn(TxnType.EXPENSE, 100_000, "2026-07-31T10:00:00Z", categoryId = food, hash = "july")
        txn(TxnType.EXPENSE, 200_000, "2026-08-05T10:00:00Z", categoryId = food, hash = "aug")
        txn(TxnType.EXPENSE, 300_000, "2026-09-01T10:00:00Z", categoryId = food, hash = "sept")

        val totals = db.txnDao()
            .observePeriodTotals(august.startSecFor(jakarta), august.endSecFor(jakarta))
            .first()

        assertEquals(200_000L, totals.expense)
    }

    @Test
    fun `category totals rank by amount descending`() = runTest {
        txn(TxnType.EXPENSE, 1_257_700, "2026-08-05T10:00:00Z", categoryId = food, hash = "a")
        txn(TxnType.EXPENSE, 1_050_000, "2026-08-06T10:00:00Z", categoryId = giving, hash = "b")
        txn(TxnType.EXPENSE, 300_000, "2026-08-07T10:00:00Z", categoryId = food, hash = "c")

        val rows = db.txnDao().observeCategoryTotals(
            august.startSecFor(jakarta),
            august.endSecFor(jakarta),
            TxnType.EXPENSE.name,
        ).first()

        assertEquals(2, rows.size)
        assertEquals("Makan & Minum", rows[0].categoryName)
        assertEquals(1_557_700L, rows[0].total)
        assertEquals("Pemberian", rows[1].categoryName)
        assertEquals(1_050_000L, rows[1].total)
    }

    @Test
    fun `category totals do not mix income into the expense side`() = runTest {
        txn(TxnType.EXPENSE, 200_000, "2026-08-05T10:00:00Z", categoryId = food, hash = "e")
        txn(TxnType.INCOME, 14_800_000, "2026-08-25T10:00:00Z", categoryId = giving, hash = "i")

        val expenses = db.txnDao().observeCategoryTotals(
            august.startSecFor(jakarta),
            august.endSecFor(jakarta),
            TxnType.EXPENSE.name,
        ).first()

        assertEquals(1, expenses.size)
        assertEquals(200_000L, expenses[0].total)
    }

    @Test
    fun `daily totals bucket by local day and exclude transfers`() = runTest {
        // 2026-08-16 17:00Z is 2026-08-17 00:00 in Jakarta (UTC+7) — the boundary case.
        txn(TxnType.EXPENSE, 187_000, "2026-08-16T09:00:00Z", categoryId = food, hash = "d1")
        txn(TxnType.EXPENSE, 30_000, "2026-08-16T11:00:00Z", categoryId = giving, hash = "d2")
        txn(TxnType.TRANSFER, 500_000, "2026-08-16T12:00:00Z", toAccountId = rdpu, hash = "d3")

        val days = db.txnDao().observeDailyTotals(
            august.startSecFor(jakarta),
            august.endSecFor(jakarta),
            25_200, // UTC+7
        ).first()

        assertEquals(1, days.size)
        assertEquals(217_000L, days[0].expense)
        assertEquals(0L, days[0].income)
    }
}

private fun DateRange.startSecFor(zone: TimeZone): Long = startInstant(zone).epochSeconds

private fun DateRange.endSecFor(zone: TimeZone): Long = endInstantExclusive(zone).epochSeconds
