package com.dvnnfrr.saku.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dvnnfrr.saku.data.local.ImportBatchEntity
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `TxnDao.lastEnteredAtEpochSec`: what the weekly reminder counts days from. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LastEnteredQueryTest {

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

    private suspend fun insert(
        occurredAt: Long,
        createdAt: Long,
        type: TxnType = TxnType.EXPENSE,
        batch: Long? = null,
    ) {
        db.txnDao().insert(
            TxnEntity(
                occurredAtEpochSec = occurredAt,
                type = type,
                amountIdr = 10_000,
                accountId = account,
                createdAtEpochSec = createdAt,
                importBatchId = batch,
                dedupHash = "$occurredAt-$createdAt-$type",
            ),
        )
    }

    private suspend fun batch(): Long = db.importBatchDao().insert(
        ImportBatchEntity(fileName = "mm.xlsx", importedAtEpochSec = 9_000, rowCount = 1, insertedCount = 1, skippedCount = 0),
    )

    @Test
    fun `an empty ledger has no last entry`() = runTest {
        assertNull(transactions.lastEnteredAt())
    }

    @Test
    fun `a typed row counts from when it was saved, not when it happened`() = runTest {
        // Back-dated: spent at 1_000, typed at 5_000.
        insert(occurredAt = 1_000, createdAt = 5_000)

        assertEquals(Instant.fromEpochSeconds(5_000), transactions.lastEnteredAt())
    }

    @Test
    fun `an imported row counts from its own timestamp, not the import`() = runTest {
        insert(occurredAt = 2_000, createdAt = 9_000, batch = batch())

        assertEquals(Instant.fromEpochSeconds(2_000), transactions.lastEnteredAt())
    }

    @Test
    fun `adjustments do not count`() = runTest {
        insert(occurredAt = 1_000, createdAt = 1_000)
        insert(occurredAt = 8_000, createdAt = 8_000, type = TxnType.ADJUSTMENT)

        assertEquals(Instant.fromEpochSeconds(1_000), transactions.lastEnteredAt())
    }

    @Test
    fun `the latest of typed and imported wins`() = runTest {
        insert(occurredAt = 3_000, createdAt = 4_000)
        insert(occurredAt = 6_000, createdAt = 9_000, batch = batch())

        assertEquals(Instant.fromEpochSeconds(6_000), transactions.lastEnteredAt())
    }
}
