package com.dvnnfrr.saku.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dvnnfrr.saku.data.local.CategoryEntity
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.data.repository.RoomAccountRepository
import com.dvnnfrr.saku.data.repository.RoomCategoryRepository
import com.dvnnfrr.saku.data.repository.RoomTransactionRepository
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
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
 * One test per bug the PR review proved. Every case here failed before the fix.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReviewRegressionTest {

    private lateinit var db: SakuDatabase
    private lateinit var accounts: RoomAccountRepository
    private lateinit var transactions: RoomTransactionRepository
    private lateinit var categories: RoomCategoryRepository

    private val jakarta = TimeZone.of("Asia/Jakarta")
    private val cutover = Instant.parse("2026-08-01T00:00:00Z")
    private val later = Instant.parse("2026-08-18T12:00:00Z")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SakuDatabase::class.java,
        ).allowMainThreadQueries().build()
        accounts = RoomAccountRepository(db)
        transactions = RoomTransactionRepository(db)
        categories = RoomCategoryRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun food(): Long = db.categoryDao().insert(
        CategoryEntity(name = "Makan & Minum", kind = CategoryKind.EXPENSE, colorSlot = 1, sortOrder = 0),
    )

    // ---- Bug 1: unique dedupHash index crashed on ordinary entry ----

    @Test
    fun `two identical expenses in the same second on the same account both save`() = runTest {
        val bca = accounts.create("BCA", AccountType.BANK, Money.ZERO, cutover)
        val cat = food()
        // A date picker yields whole minutes, so this is an ordinary pair of bus fares.
        transactions.record(later, TxnType.EXPENSE, Money(5_000), bca, categoryId = cat)
        transactions.record(later, TxnType.EXPENSE, Money(5_000), bca, categoryId = cat)

        val all = transactions.all()
        assertEquals("Both fares must be stored", 2, all.size)
        assertEquals(Money(10_000), all.fold(Money.ZERO) { a, t -> a + t.amount })
    }

    @Test
    fun `the same expense on two accounts both save`() = runTest {
        val bca = accounts.create("BCA", AccountType.BANK, Money.ZERO, cutover)
        val cash = accounts.create("Cash", AccountType.CASH, Money.ZERO, cutover)
        val cat = food()
        transactions.record(later, TxnType.EXPENSE, Money(20_000), bca, categoryId = cat)
        transactions.record(later, TxnType.EXPENSE, Money(20_000), cash, categoryId = cat)

        assertEquals(2, transactions.all().size)
    }

    @Test
    fun `clearing a note on an edit does not collide with a sibling row`() = runTest {
        val bca = accounts.create("BCA", AccountType.BANK, Money.ZERO, cutover)
        val cash = accounts.create("Cash", AccountType.CASH, Money.ZERO, cutover)
        val cat = food()
        transactions.record(later, TxnType.EXPENSE, Money(20_000), bca, categoryId = cat)
        val second = transactions.record(
            later, TxnType.EXPENSE, Money(20_000), cash, categoryId = cat, note = "warung",
        )

        transactions.update(transactions.byId(second)!!.copy(note = null))

        assertEquals(2, transactions.all().size)
    }

    // ---- Bug 2: a liability raised net worth ----

    @Test
    fun `debt lowers net worth instead of raising it`() = runTest {
        val bca = accounts.create("BCA", AccountType.BANK, Money(1_000_000), cutover)
        val card = accounts.create("Kartu Kredit", AccountType.LIABILITY, Money.ZERO, cutover)
        transactions.record(later, TxnType.EXPENSE, Money(500_000), card, categoryId = food())

        val summary = accounts.observeSummary().first()
        assertEquals(Money(1_000_000), summary.spendable)
        // Owed is presented positive; the stored balance is negative.
        assertEquals(Money(500_000), summary.owed)
        assertEquals(Money(500_000), summary.total)
        assertEquals(Money(-500_000), accounts.byId(card)!!.balance)
        assertEquals(bca, accounts.byId(bca)!!.id)
    }

    @Test
    fun `the liabilities figure is absent while no liability account exists`() = runTest {
        accounts.create("BCA", AccountType.BANK, Money(1_000_000), cutover)
        assertNull(accounts.observeSummary().first().owed)
    }

    // ---- Bug 3: a transfer across mismatched as-of dates invented money ----

    @Test
    fun `a transfer predating an account's opening date is refused`() = runTest {
        val bca = accounts.create("BCA", AccountType.BANK, Money(1_000_000), Instant.parse("2026-08-17T00:00:00Z"))
        val rdpu = accounts.create("RDPU", AccountType.INVESTMENT, Money(5_000_000), Instant.parse("2026-07-10T00:00:00Z"))

        val error = runCatching {
            transactions.record(
                Instant.parse("2026-07-15T00:00:00Z"), TxnType.TRANSFER, Money(500_000), bca,
                toAccountId = rdpu,
            )
        }.exceptionOrNull()

        assertTrue("Expected refusal, got $error", error is IllegalArgumentException)
    }

    @Test
    fun `a transfer after both opening dates preserves net worth`() = runTest {
        val bca = accounts.create("BCA", AccountType.BANK, Money(1_000_000), cutover)
        val rdpu = accounts.create("RDPU", AccountType.INVESTMENT, Money(5_000_000), cutover)
        transactions.record(later, TxnType.TRANSFER, Money(500_000), bca, toAccountId = rdpu)

        val summary = accounts.observeSummary().first()
        assertEquals(Money(500_000), summary.spendable)
        assertEquals(Money(5_500_000), summary.invested)
        assertEquals(Money(6_000_000), summary.total)
    }

    // ---- Bug 4 & 6: reconcile on archived accounts, and atomicity ----

    @Test
    fun `reconciling an archived account uses its real balance`() = runTest {
        val cash = accounts.create("Cash", AccountType.CASH, Money(1_000_000), cutover)
        transactions.record(later, TxnType.EXPENSE, Money(300_000), cash, categoryId = food())
        accounts.setArchived(cash, true)

        // True balance already matches, so there is nothing to correct.
        assertNull(accounts.reconcile(cash, Money(700_000), later))
        assertEquals(Money(700_000), accounts.byId(cash)!!.balance)
    }

    @Test
    fun `reconciling writes a signed adjustment and stamps the date`() = runTest {
        val cash = accounts.create("Cash", AccountType.CASH, Money(1_038_500), cutover)

        val id = accounts.reconcile(cash, Money(985_000), later)

        assertNotNull(id)
        assertEquals(Money(-53_500), transactions.byId(id!!)!!.amount)
        assertEquals(TxnType.ADJUSTMENT, transactions.byId(id)!!.type)
        assertEquals(Money(985_000), accounts.byId(cash)!!.balance)
        assertEquals(later, accounts.byId(cash)!!.lastReconciled)
    }

    @Test
    fun `an adjustment is excluded from spending totals`() = runTest {
        val cash = accounts.create("Cash", AccountType.CASH, Money(1_000_000), cutover)
        transactions.record(later, TxnType.EXPENSE, Money(200_000), cash, categoryId = food())
        accounts.reconcile(cash, Money(500_000), later)

        val august = DateRange.ofMonth(LocalDate(2026, 8, 1))
        assertEquals(Money(200_000), transactions.observePeriodTotals(august, jakarta).first().expense)
    }

    // ---- Bug 5: update() skipped every validation ----

    @Test
    fun `an edit cannot strip the category off an expense`() = runTest {
        val bca = accounts.create("BCA", AccountType.BANK, Money.ZERO, cutover)
        val id = transactions.record(later, TxnType.EXPENSE, Money(200_000), bca, categoryId = food())

        val error = runCatching {
            transactions.update(transactions.byId(id)!!.copy(categoryId = null))
        }.exceptionOrNull()
        assertTrue("Expected refusal, got $error", error is IllegalArgumentException)
    }

    @Test
    fun `an edit cannot make a transfer point at its own account`() = runTest {
        val bca = accounts.create("BCA", AccountType.BANK, Money.ZERO, cutover)
        val cash = accounts.create("Cash", AccountType.CASH, Money.ZERO, cutover)
        val id = transactions.record(later, TxnType.TRANSFER, Money(100_000), bca, toAccountId = cash)

        val error = runCatching {
            transactions.update(transactions.byId(id)!!.copy(toAccountId = bca))
        }.exceptionOrNull()
        assertTrue("Expected refusal, got $error", error is IllegalArgumentException)
    }

    @Test
    fun `an edit cannot zero the amount`() = runTest {
        val bca = accounts.create("BCA", AccountType.BANK, Money.ZERO, cutover)
        val id = transactions.record(later, TxnType.EXPENSE, Money(200_000), bca, categoryId = food())

        val error = runCatching {
            transactions.update(transactions.byId(id)!!.copy(amount = Money.ZERO))
        }.exceptionOrNull()
        assertTrue("Expected refusal, got $error", error is IllegalArgumentException)
    }

    @Test
    fun `category totals sum to the period expense header`() = runTest {
        val bca = accounts.create("BCA", AccountType.BANK, Money.ZERO, cutover)
        val cat = food()
        transactions.record(later, TxnType.EXPENSE, Money(200_000), bca, categoryId = cat)

        val august = DateRange.ofMonth(LocalDate(2026, 8, 1))
        val header = transactions.observePeriodTotals(august, jakarta).first().expense
        val slices = transactions.observeCategoryTotals(august, jakarta, TxnType.EXPENSE).first()
            .fold(Money.ZERO) { a, c -> a + c.total }

        assertEquals("The donut must sum to the header above it", header, slices)
    }

    // ---- Bug 7: transfer-only days emitted empty rows ----

    @Test
    fun `a day holding only a transfer produces no daily row`() = runTest {
        val bca = accounts.create("BCA", AccountType.BANK, Money.ZERO, cutover)
        val cash = accounts.create("Cash", AccountType.CASH, Money.ZERO, cutover)
        transactions.record(
            Instant.parse("2026-08-05T03:00:00Z"), TxnType.EXPENSE, Money(100_000), bca,
            categoryId = food(),
        )
        transactions.record(
            Instant.parse("2026-08-10T03:00:00Z"), TxnType.TRANSFER, Money(500_000), bca,
            toAccountId = cash,
        )
        accounts.reconcile(cash, Money(9_000), Instant.parse("2026-08-12T03:00:00Z"))

        val days = transactions
            .observeDailyTotals(DateRange.ofMonth(LocalDate(2026, 8, 1)), jakarta).first()

        assertEquals("Only the expense day has a header", 1, days.size)
        assertEquals(LocalDate(2026, 8, 5), days.single().date)
    }

    // ---- Bug 9: a subcategory from another category ----

    @Test
    fun `a subcategory from a different category is refused`() = runTest {
        val bca = accounts.create("BCA", AccountType.BANK, Money.ZERO, cutover)
        val giving = categories.createCategory("Pemberian", CategoryKind.EXPENSE)
        val arisan = categories.createSubcategory(giving, "Arisan")
        val kuliah = categories.createCategory("Kuliah", CategoryKind.EXPENSE)

        val error = runCatching {
            transactions.record(
                later, TxnType.EXPENSE, Money(50_000), bca,
                categoryId = kuliah, subcategoryId = arisan,
            )
        }.exceptionOrNull()
        assertTrue("Expected refusal, got $error", error is IllegalArgumentException)
    }

    // ---- Contract gaps ----

    @Test
    fun `opening balance can be set after the account exists`() = runTest {
        val bca = accounts.create("BCA", AccountType.BANK, Money.ZERO, cutover)
        val asOf = Instant.parse("2026-08-17T00:00:00Z")

        accounts.setOpening(bca, Money(8_420_000), asOf)

        val account = accounts.byId(bca)!!
        assertEquals(Money(8_420_000), account.openingBalance)
        assertEquals(asOf, account.openingAsOf)
        assertEquals(Money(8_420_000), account.balance)
    }

    @Test
    fun `an archived account is listed by observeAll but not observeActive`() = runTest {
        val bca = accounts.create("BCA", AccountType.BANK, Money.ZERO, cutover)
        val gopay = accounts.create("Gopay", AccountType.BANK, Money.ZERO, cutover)
        accounts.setArchived(gopay, true)

        assertEquals(listOf(bca, gopay), accounts.observeAll().first().map { it.id })
        assertEquals(listOf(bca), accounts.observeActive().first().map { it.id })
    }

    @Test
    fun `the account list carries real opening fields, not placeholders`() = runTest {
        val asOf = Instant.parse("2026-08-17T00:00:00Z")
        val bca = accounts.create("BCA", AccountType.BANK, Money(8_420_000), asOf)

        val fromList = accounts.observeAll().first().single()
        val fromById = accounts.byId(bca)!!

        assertEquals(fromById.openingBalance, fromList.openingBalance)
        assertEquals(fromById.openingAsOf, fromList.openingAsOf)
    }

    @Test
    fun `deleting an account does not let the next one reuse a sort order`() = runTest {
        val ids = (1..3).map { accounts.create("A$it", AccountType.BANK, Money.ZERO, cutover) }
        accounts.delete(ids[1])
        val fresh = accounts.create("A4", AccountType.BANK, Money.ZERO, cutover)

        val orders = accounts.observeAll().first().map { it.sortOrder }
        assertEquals("Sort orders must stay unique", orders.size, orders.distinct().size)
        assertEquals(3, accounts.byId(fresh)!!.sortOrder)
    }

    @Test
    fun `findOrCreate revives an archived category instead of duplicating it`() = runTest {
        val kuliah = categories.createCategory("Kuliah", CategoryKind.EXPENSE)
        categories.setCategoryArchived(kuliah, true)

        assertEquals(kuliah, categories.findOrCreate("kuliah", CategoryKind.EXPENSE))
        assertEquals(1, categories.observeAll().first().count { it.name == "Kuliah" })
    }

    @Test
    fun `category shares are whole basis points summing to exactly 100 percent`() = runTest {
        val bca = accounts.create("BCA", AccountType.BANK, Money.ZERO, cutover)
        // Three equal thirds, the classic case where rounding loses a point.
        repeat(3) { i ->
            val cat = categories.createCategory("C$i", CategoryKind.EXPENSE)
            transactions.record(later, TxnType.EXPENSE, Money(100_000), bca, categoryId = cat)
        }

        val totals = transactions
            .observeCategoryTotals(DateRange.ofMonth(LocalDate(2026, 8, 1)), jakarta, TxnType.EXPENSE)
            .first()

        assertEquals(10_000, totals.sumOf { it.shareBasisPoints })
    }
}
