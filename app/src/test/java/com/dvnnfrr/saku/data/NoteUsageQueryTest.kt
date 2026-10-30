package com.dvnnfrr.saku.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.data.local.TxnEntity
import com.dvnnfrr.saku.data.repository.RoomAccountRepository
import com.dvnnfrr.saku.data.repository.RoomCategoryRepository
import com.dvnnfrr.saku.data.repository.RoomTransactionRepository
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `TxnDao.observeNoteUses` through `observeNoteUsage`: which rows reach the fold. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteUsageQueryTest {

    private lateinit var db: SakuDatabase
    private lateinit var transactions: RoomTransactionRepository
    private var bca = 0L
    private var rdpu = 0L
    private var makan = 0L
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
        rdpu = accounts.create("RDPU", AccountType.INVESTMENT, Money.ZERO, asOf)
        makan = categories.createCategory("Makan & Minum", CategoryKind.EXPENSE)
        gaji = categories.createCategory("Gaji", CategoryKind.INCOME)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun record(type: TxnType, note: String?, at: Long, amount: Long = 10_000) =
        transactions.record(
            occurredAt = Instant.fromEpochSeconds(at),
            type = type,
            amount = Money(amount),
            accountId = bca,
            toAccountId = if (type == TxnType.TRANSFER) rdpu else null,
            categoryId = when (type) {
                TxnType.EXPENSE -> makan
                TxnType.INCOME -> gaji
                else -> null
            },
            note = note,
        )

    @Test
    fun `notes are scoped to the requested type`() = runTest {
        record(TxnType.EXPENSE, "uang arisan", at = 100)
        record(TxnType.INCOME, "uang saku", at = 200)
        record(TxnType.TRANSFER, "uang tabungan", at = 300)

        assertEquals(listOf("uang arisan"), transactions.observeNoteUsage(TxnType.EXPENSE).first().map { it.note })
        assertEquals(listOf("uang saku"), transactions.observeNoteUsage(TxnType.INCOME).first().map { it.note })
    }

    @Test
    fun `missing and blank notes are left out`() = runTest {
        record(TxnType.EXPENSE, null, at = 100)
        // `record` stores a blank note as null, so a blank one can only arrive from an
        // older write path; insert it directly to cover the query's TRIM guard.
        db.txnDao().insert(
            TxnEntity(
                occurredAtEpochSec = 200,
                type = TxnType.EXPENSE,
                amountIdr = 5_000,
                accountId = bca,
                categoryId = makan,
                note = "   ",
                createdAtEpochSec = 200,
                dedupHash = "blank-note",
            ),
        )

        assertTrue(transactions.observeNoteUsage(TxnType.EXPENSE).first().isEmpty())
    }

    @Test
    fun `repeated notes arrive folded with category and last amount`() = runTest {
        record(TxnType.EXPENSE, "Bensin Motor", at = 100, amount = 25_000)
        record(TxnType.EXPENSE, "bensin motor", at = 200, amount = 30_000)

        val usage = transactions.observeNoteUsage(TxnType.EXPENSE).first().single()

        assertEquals("bensin motor", usage.note)
        assertEquals(2, usage.useCount)
        assertEquals(Money(30_000), usage.lastAmount)
        assertEquals(makan, usage.categoryId)
    }
}
