package com.dvnnfrr.saku.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.data.local.TxnEntity
import com.dvnnfrr.saku.data.repository.RoomAccountRepository
import com.dvnnfrr.saku.data.repository.RoomTransactionRepository
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `TransactionRepository.hasAny`: the ledger half of the first-run gate. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HasAnyQueryTest {

    private lateinit var db: SakuDatabase
    private lateinit var transactions: RoomTransactionRepository
    private var account = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SakuDatabase::class.java,
        ).allowMainThreadQueries().build()
        transactions = RoomTransactionRepository(db)
        account = RoomAccountRepository(db).create("Card", AccountType.BANK, Money.ZERO, Instant.fromEpochSeconds(0))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insert(type: TxnType, amount: Long) {
        db.txnDao().insert(
            TxnEntity(
                occurredAtEpochSec = 1_000,
                type = type,
                amountIdr = amount,
                accountId = account,
                createdAtEpochSec = 1_000,
                dedupHash = "$type-$amount",
            ),
        )
    }

    @Test
    fun `an empty ledger has nothing`() = runTest {
        assertFalse(transactions.hasAny())
    }

    @Test
    fun `accounts alone are not history`() = runTest {
        RoomAccountRepository(db).create("Gopay", AccountType.BANK, Money(50_000), Instant.fromEpochSeconds(0))

        assertFalse(transactions.hasAny())
    }

    @Test
    fun `an adjustment-only ledger counts`() = runTest {
        // lastEnteredAt ignores adjustments; the gate must not.
        insert(TxnType.ADJUSTMENT, -5_000)

        assertTrue(transactions.hasAny())
    }

    @Test
    fun `an ordinary row counts`() = runTest {
        insert(TxnType.EXPENSE, 10_000)

        assertTrue(transactions.hasAny())
    }
}
