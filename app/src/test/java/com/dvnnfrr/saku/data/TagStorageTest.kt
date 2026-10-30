package com.dvnnfrr.saku.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.data.repository.DuplicateNameException
import com.dvnnfrr.saku.data.repository.RoomAccountRepository
import com.dvnnfrr.saku.data.repository.RoomCategoryRepository
import com.dvnnfrr.saku.data.repository.RoomTagRepository
import com.dvnnfrr.saku.data.repository.RoomTransactionRepository
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.InvalidTagNameException
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tags in storage: the name rules, writing links with a row, and the tag filter in SQL. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TagStorageTest {

    private val zone = TimeZone.of("Asia/Jakarta")
    private val october = DateRange.ofMonth(LocalDate(2026, 10, 1))

    private lateinit var db: SakuDatabase
    private lateinit var transactions: RoomTransactionRepository
    private lateinit var tags: RoomTagRepository
    private var bca = 0L
    private var cash = 0L
    private var makan = 0L
    private var kos = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SakuDatabase::class.java)
            .allowMainThreadQueries().build()
        transactions = RoomTransactionRepository(db)
        tags = RoomTagRepository(db)
        val accounts = RoomAccountRepository(db)
        val categories = RoomCategoryRepository(db)
        bca = accounts.create("BCA", AccountType.BANK, Money.ZERO, Instant.fromEpochSeconds(0))
        cash = accounts.create("Cash", AccountType.CASH, Money.ZERO, Instant.fromEpochSeconds(0))
        makan = categories.createCategory("Makan & Minum", CategoryKind.EXPENSE)
        kos = categories.createCategory("Kebutuhan Kos", CategoryKind.EXPENSE)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun record(
        day: Int,
        amount: Long,
        category: Long?,
        tagIds: Set<Long> = emptySet(),
        type: TxnType = TxnType.EXPENSE,
        to: Long? = null,
    ) = transactions.record(
        occurredAt = LocalDateTime(2026, 10, day, 12, 0).toInstant(zone),
        type = type,
        amount = Money(amount),
        accountId = bca,
        toAccountId = to,
        categoryId = category,
        tagIds = tagIds,
    )

    @Test
    fun `a name in other case and spacing finds the existing tag`() = runTest {
        val grocery = tags.findOrCreate("Monthly grocery")
        assertEquals(grocery, tags.findOrCreate("  monthly   GROCERY "))
        assertEquals(listOf("Monthly grocery"), tags.all().map { it.name })
    }

    @Test
    fun `an invalid name is refused`() = runTest {
        assertThrows(InvalidTagNameException::class.java) { kotlinx.coroutines.runBlocking { tags.findOrCreate("   ") } }
        assertThrows(InvalidTagNameException::class.java) { kotlinx.coroutines.runBlocking { tags.findOrCreate("a;b") } }
        assertThrows(InvalidTagNameException::class.java) {
            kotlinx.coroutines.runBlocking { tags.findOrCreate("x".repeat(31)) }
        }
        assertTrue(tags.all().isEmpty())
    }

    @Test
    fun `rename refuses a name another tag uses and allows a case change of its own`() = runTest {
        val grocery = tags.findOrCreate("Monthly grocery")
        tags.findOrCreate("Trip Bali")
        assertThrows(DuplicateNameException::class.java) {
            kotlinx.coroutines.runBlocking { tags.rename(grocery, "trip bali") }
        }
        tags.rename(grocery, "MONTHLY grocery")
        assertEquals("MONTHLY grocery", tags.all().first { it.id == grocery }.name)
    }

    @Test
    fun `several tags save with the row and read back`() = runTest {
        val grocery = tags.findOrCreate("Monthly grocery")
        val ramadan = tags.findOrCreate("Ramadan")
        val id = record(3, 120_000, makan, setOf(grocery, ramadan))

        val saved = transactions.byId(id)!!
        assertEquals(setOf(grocery, ramadan), saved.tagIds)
        assertEquals(makan, saved.categoryId)
        assertEquals(setOf(grocery, ramadan), transactions.observeInRange(october, zone, TransactionFilter.ALL).first().single().tagIds)
        assertEquals(mapOf(grocery to 1, ramadan to 1), tags.observeCounts().first())
    }

    @Test
    fun `a row saved without tags has none`() = runTest {
        val id = record(3, 50_000, makan)
        assertTrue(transactions.byId(id)!!.tagIds.isEmpty())
    }

    @Test
    fun `removing a tag in an edit keeps the tag and lowers its count`() = runTest {
        val grocery = tags.findOrCreate("Monthly grocery")
        val first = record(3, 120_000, makan, setOf(grocery))
        record(4, 15_000, kos, setOf(grocery))

        transactions.update(transactions.byId(first)!!.copy(tagIds = emptySet()))

        assertTrue(transactions.byId(first)!!.tagIds.isEmpty())
        assertEquals(listOf("Monthly grocery"), tags.all().map { it.name })
        assertEquals(mapOf(grocery to 1), tags.observeCounts().first())
    }

    @Test
    fun `a failed save writes no tag link`() = runTest {
        val grocery = tags.findOrCreate("Monthly grocery")
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { record(3, 0, makan, setOf(grocery)) }
        }
        assertTrue(db.tagDao().allLinks().isEmpty())
    }

    @Test
    fun `deleting a row drops its links and keeps the tag`() = runTest {
        val grocery = tags.findOrCreate("Monthly grocery")
        val id = record(3, 120_000, makan, setOf(grocery))
        transactions.delete(id)
        assertTrue(db.tagDao().allLinks().isEmpty())
        assertEquals(1, tags.all().size)
    }

    @Test
    fun `the tag filter passes rows with any chosen tag and All passes every row`() = runTest {
        val grocery = tags.findOrCreate("Monthly grocery")
        val bali = tags.findOrCreate("Trip Bali")
        val rice = record(3, 120_000, makan, setOf(grocery))
        val soap = record(3, 30_000, kos, setOf(grocery, bali))
        val plain = record(4, 50_000, makan)
        val transfer = record(5, 500_000, null, setOf(bali), TxnType.TRANSFER, to = cash)

        suspend fun ids(filter: TransactionFilter) =
            transactions.observeInRange(october, zone, filter).first().map { it.id }.toSet()

        assertEquals(setOf(rice, soap, plain, transfer), ids(TransactionFilter.ALL))
        assertEquals(setOf(rice, soap), ids(TransactionFilter(tagIds = setOf(grocery))))
        assertEquals(setOf(rice, soap, transfer), ids(TransactionFilter(tagIds = setOf(grocery, bali))))

        val groceryOnly = TransactionFilter(tagIds = setOf(grocery))
        assertEquals(Money(150_000), transactions.observePeriodTotals(october, zone, groceryOnly).first().expense)
        val split = transactions.observeCategoryTotals(october, zone, TxnType.EXPENSE, groceryOnly).first()
        assertEquals(listOf(makan to 120_000L, kos to 30_000L), split.map { it.categoryId to it.total.rupiah })
        assertEquals(10_000, split.sumOf { it.shareBasisPoints })
        assertEquals(
            Money(150_000),
            Money(transactions.observeDailyTotals(october, zone, groceryOnly).first().sumOf { it.expense.rupiah }),
        )
    }

    @Test
    fun `archiving keeps the tag on its rows`() = runTest {
        val bali = tags.findOrCreate("Trip Bali")
        val id = record(3, 120_000, makan, setOf(bali))
        tags.setArchived(bali, true)
        assertTrue(tags.all().single().archived)
        assertEquals(setOf(bali), transactions.byId(id)!!.tagIds)
        tags.setArchived(bali, false)
        assertFalse(tags.all().single().archived)
    }
}
