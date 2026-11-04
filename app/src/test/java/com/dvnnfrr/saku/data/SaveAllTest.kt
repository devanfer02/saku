package com.dvnnfrr.saku.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.data.repository.NewTransaction
import com.dvnnfrr.saku.data.repository.RoomAccountRepository
import com.dvnnfrr.saku.data.repository.RoomCategoryRepository
import com.dvnnfrr.saku.data.repository.RoomTagRepository
import com.dvnnfrr.saku.data.repository.RoomTransactionRepository
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
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

/** `saveAll`, the receipt's write: every row with its tags, or nothing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SaveAllTest {

    private val zone = TimeZone.of("Asia/Jakarta")
    private val at = LocalDateTime(2026, 10, 30, 19, 45).toInstant(zone)

    private lateinit var db: SakuDatabase
    private lateinit var transactions: RoomTransactionRepository
    private var card = 0L
    private var makan = 0L
    private var grocery = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SakuDatabase::class.java)
            .allowMainThreadQueries().build()
        transactions = RoomTransactionRepository(db)
        card = RoomAccountRepository(db).create("Card", AccountType.BANK, Money.ZERO, Instant.fromEpochSeconds(0))
        makan = RoomCategoryRepository(db).createCategory("Makan & Minum", CategoryKind.EXPENSE)
        grocery = RoomTagRepository(db).findOrCreate("Monthly grocery")
    }

    @After
    fun tearDown() = db.close()

    private fun row(amount: Long, note: String, category: Long? = makan) =
        NewTransaction(at, TxnType.EXPENSE, Money(amount), card, category, note)

    @Test
    fun `every row is saved with the tags`() = runTest {
        val ids = transactions.saveAll(
            listOf(row(14_129, "INDOMIE GRG SPCL ×5"), row(29_534, "SUSU UHT 1L ×2")),
            tagIds = setOf(grocery),
        )

        val saved = transactions.all()
        assertEquals(ids, saved.map { it.id })
        assertEquals(listOf(14_129L, 29_534L), saved.map { it.amount.rupiah })
        assertTrue(saved.all { it.tagIds == setOf(grocery) })
    }

    @Test
    fun `a row that fails leaves nothing from the receipt`() = runTest {
        // The third row has no category, which an expense needs, so the write fails there.
        val result = runCatching {
            transactions.saveAll(
                listOf(row(14_129, "INDOMIE"), row(35_459, "MINYAK GORENG 2L"), row(66_087, "BERAS", category = null)),
                tagIds = setOf(grocery),
            )
        }

        assertTrue(result.isFailure)
        assertEquals(emptyList<Any>(), transactions.all())
        assertEquals(emptyList<Any>(), db.tagDao().observeLinks().first())
    }
}
