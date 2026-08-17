package com.dvnnfrr.saku.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dvnnfrr.saku.data.local.AccountEntity
import com.dvnnfrr.saku.data.local.CategoryEntity
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.data.local.TxnEntity
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the scenarios in specs/account-management/spec.md for the balance formula.
 * The as-of date clause is the load-bearing part: it is what lets a year of
 * imported history feed the stats screen without moving a single balance.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BalanceQueryTest {

    private lateinit var db: SakuDatabase

    private val cutover = Instant.parse("2026-08-17T00:00:00Z")
    private val beforeCutover = Instant.parse("2026-07-10T12:00:00Z")
    private val afterCutover = Instant.parse("2026-08-18T12:00:00Z")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SakuDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun account(
        name: String,
        type: AccountType = AccountType.BANK,
        opening: Long = 0L,
        asOf: Instant = cutover,
    ): Long = db.accountDao().insert(
        AccountEntity(
            name = name,
            type = type,
            openingBalance = opening,
            openingAsOfEpochSec = asOf.epochSeconds,
            colorSlot = 0,
            sortOrder = 0,
        ),
    )

    private suspend fun category(name: String = "Makan & Minum"): Long =
        db.categoryDao().insert(
            CategoryEntity(name = name, kind = CategoryKind.EXPENSE, colorSlot = 1, sortOrder = 0),
        )

    private suspend fun txn(
        type: TxnType,
        amount: Long,
        accountId: Long,
        at: Instant,
        toAccountId: Long? = null,
        categoryId: Long? = null,
        hash: String = "h-${type}-$amount-$accountId-${at.epochSeconds}-${toAccountId ?: 0}",
    ) = db.txnDao().insert(
        TxnEntity(
            occurredAtEpochSec = at.epochSeconds,
            type = type,
            amountIdr = amount,
            accountId = accountId,
            toAccountId = toAccountId,
            categoryId = categoryId,
            createdAtEpochSec = at.epochSeconds,
            dedupHash = hash,
        ),
    )

    private suspend fun balanceOf(id: Long): Long =
        db.accountDao().observeBalances().first().first { it.id == id }.balance

    @Test
    fun `historical rows do not move balances`() = runTest {
        val bca = account("BCA", opening = 8_420_000)
        val food = category()
        // Stand-in for the 1,150 imported rows, all dated before the cutover.
        repeat(20) { i ->
            txn(
                TxnType.EXPENSE, 100_000, bca,
                beforeCutover.plus(kotlin.time.Duration.parse("${i}h")),
                categoryId = food,
                hash = "history-$i",
            )
        }
        assertEquals(8_420_000L, balanceOf(bca))
    }

    @Test
    fun `a transaction on the as-of date counts`() = runTest {
        val bca = account("BCA", opening = 1_000_000)
        txn(TxnType.EXPENSE, 187_000, bca, cutover, categoryId = category())
        assertEquals(813_000L, balanceOf(bca))
    }

    @Test
    fun `income raises and expense lowers the balance`() = runTest {
        val bca = account("BCA", opening = 0)
        val food = category()
        txn(TxnType.INCOME, 14_800_000, bca, afterCutover, categoryId = food, hash = "in")
        txn(TxnType.EXPENSE, 800_000, bca, afterCutover, categoryId = food, hash = "out")
        assertEquals(14_000_000L, balanceOf(bca))
    }

    @Test
    fun `a transfer moves both balances and leaves the total unchanged`() = runTest {
        val bca = account("BCA", opening = 8_420_000)
        val rdpu = account("RDPU SucorInvest", AccountType.INVESTMENT, opening = 5_000_000)

        txn(TxnType.TRANSFER, 500_000, bca, afterCutover, toAccountId = rdpu)

        assertEquals(7_920_000L, balanceOf(bca))
        assertEquals(5_500_000L, balanceOf(rdpu))
        // This is the fix for the investment black hole: net worth is unchanged.
        assertEquals(13_420_000L, balanceOf(bca) + balanceOf(rdpu))
    }

    @Test
    fun `a transfer dated before the destination as-of date does not credit it`() = runTest {
        val bca = account("BCA", opening = 1_000_000, asOf = beforeCutover)
        val rdpu = account("RDPU", AccountType.INVESTMENT, opening = 5_000_000, asOf = cutover)

        txn(TxnType.TRANSFER, 500_000, bca, beforeCutover, toAccountId = rdpu)

        assertEquals(500_000L, balanceOf(bca))
        assertEquals(5_000_000L, balanceOf(rdpu))
    }

    @Test
    fun `a negative adjustment lowers the balance`() = runTest {
        val cash = account("Cash", AccountType.CASH, opening = 1_038_500)
        txn(TxnType.ADJUSTMENT, -53_500, cash, afterCutover)
        assertEquals(985_000L, balanceOf(cash))
    }

    @Test
    fun `a positive adjustment raises the balance`() = runTest {
        val cash = account("Cash", AccountType.CASH, opening = 1_000_000)
        txn(TxnType.ADJUSTMENT, 25_000, cash, afterCutover)
        assertEquals(1_025_000L, balanceOf(cash))
    }

    /**
     * The DAO deliberately includes archived accounts. Filtering them here made
     * `reconcile()` and `byId()` fall back to the opening balance for an archived
     * account, reporting a wrong figure and writing a phantom adjustment. The
     * repository filters instead — see ReviewRegressionTest.
     */
    @Test
    fun `archived accounts are still balanced, and flagged rather than dropped`() = runTest {
        val bca = account("BCA", opening = 1_000_000)
        val gopay = account("Gopay", opening = 50_000)
        txn(TxnType.EXPENSE, 20_000, gopay, afterCutover, categoryId = category(), hash = "g1")
        db.accountDao().setArchived(gopay, true)

        val rows = db.accountDao().observeBalances().first()
        assertEquals(listOf(bca, gopay), rows.map { it.id })
        assertEquals(30_000L, rows.first { it.id == gopay }.balance)
        assertEquals(true, rows.first { it.id == gopay }.archived)
    }

    @Test
    fun `balanceOf matches the list projection, including for archived accounts`() = runTest {
        val cash = account("Cash", AccountType.CASH, opening = 1_000_000)
        txn(TxnType.EXPENSE, 300_000, cash, afterCutover, categoryId = category())
        db.accountDao().setArchived(cash, true)

        assertEquals(700_000L, db.accountDao().balanceOf(cash))
    }

    @Test
    fun `the projection carries the real opening balance and as-of date`() = runTest {
        val bca = account("BCA", opening = 8_420_000, asOf = cutover)

        val row = db.accountDao().observeBalances().first().single()
        assertEquals(8_420_000L, row.openingBalance)
        assertEquals(cutover.epochSeconds, row.openingAsOfEpochSec)
        assertEquals(bca, row.id)
    }
}
