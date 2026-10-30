package com.dvnnfrr.saku.ui.firstrun

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dvnnfrr.saku.data.FirstRunSeed
import com.dvnnfrr.saku.data.FirstRunStore
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.data.repository.PendingRow
import com.dvnnfrr.saku.data.repository.RoomAccountRepository
import com.dvnnfrr.saku.data.repository.RoomCategoryRepository
import com.dvnnfrr.saku.data.repository.RoomImportRepository
import com.dvnnfrr.saku.data.repository.RoomTransactionRepository
import com.dvnnfrr.saku.domain.Account
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * First-run setup against real repositories on an in-memory database, seeded the way a
 * fresh install is: Cash and Card at zero, as of the epoch, no categories.
 *
 * Writes run on Room's own dispatcher, so the tests wait on the state they expect
 * (`finished`, a gate value) rather than on the test scheduler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirstRunViewModelTest {

    private val zone = TimeZone.of("Asia/Jakarta")
    private val dispatcher = StandardTestDispatcher()

    /** Parked on 30 Oct 2026, mid-afternoon. */
    private val now = LocalDateTime(2026, 10, 30, 14, 0).toInstant(zone)
    private val startOfToday = LocalDateTime(2026, 10, 30, 0, 0).toInstant(zone)
    private val clock = object : Clock {
        override fun now(): Instant = now
    }

    private lateinit var db: SakuDatabase
    private lateinit var accounts: RoomAccountRepository
    private lateinit var categories: RoomCategoryRepository
    private lateinit var transactions: RoomTransactionRepository
    private lateinit var imports: RoomImportRepository

    private fun store() = FirstRunStore(ApplicationProvider.getApplicationContext())

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SakuDatabase::class.java,
        ).allowMainThreadQueries().build()
        accounts = RoomAccountRepository(db)
        categories = RoomCategoryRepository(db)
        transactions = RoomTransactionRepository(db)
        imports = RoomImportRepository(db)
        FirstRunSeed.applyIfEmpty(db, Instant.fromEpochSeconds(0), zone)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun setup() = FirstRunSetupViewModel(accounts, categories, store(), zone, clock)

    private fun gate() = FirstRunGateViewModel(store(), transactions)

    private suspend fun FirstRunSetupViewModel.loaded(): FirstRunSetupUiState =
        state.first { it.accounts.size == 2 }

    private suspend fun FirstRunSetupViewModel.finish() {
        next()
        state.first { it.step == SetupStep.CATEGORIES }
        done()
        state.first { it.finished }
    }

    private suspend fun FirstRunGateViewModel.decided(): FirstRunGate =
        gate.first { it != FirstRunGate.CHECKING }

    private suspend fun named(name: String): Account = accounts.all().first { it.name == name }

    private suspend fun cashId() = named("Cash").id

    private suspend fun cardId() = named("Card").id

    // ---- Done ---------------------------------------------------------------------------

    @Test
    fun `the seeded accounts are listed`() = runTest(dispatcher) {
        val state = setup().loaded()

        assertEquals(listOf("Cash", "Card"), state.accounts.map { it.draft.name })
        assertEquals(SetupStep.ACCOUNTS, state.step)
    }

    @Test
    fun `Done writes opening balances as of the start of today`() = runTest(dispatcher) {
        val vm = setup()
        vm.loaded()
        vm.setAmount(existingKey(cashId()), "250.000")
        vm.setAmount(existingKey(cardId()), "4100000")

        vm.finish()

        val cash = named("Cash")
        val card = named("Card")
        assertEquals(Money(250_000), cash.openingBalance)
        assertEquals(Money(4_100_000), card.openingBalance)
        assertEquals(startOfToday, cash.openingAsOf)
        assertEquals(startOfToday, card.openingAsOf)
        assertEquals(Money(250_000), cash.balance)
        assertTrue(store().isComplete())
    }

    @Test
    fun `an empty amount writes zero as of today`() = runTest(dispatcher) {
        val vm = setup()
        vm.loaded()

        vm.finish()

        val card = named("Card")
        assertEquals(Money.ZERO, card.openingBalance)
        assertEquals(startOfToday, card.openingAsOf)
    }

    @Test
    fun `rename, retype and add are written, the new account sharing the as-of date`() = runTest(dispatcher) {
        val vm = setup()
        vm.loaded()
        vm.rename(existingKey(cardId()), "BCA")
        vm.retype(existingKey(cashId()), AccountType.LIABILITY)
        vm.setAmount(existingKey(cashId()), "300000")
        vm.addAccount()
        vm.rename(addedKey(0), "Gopay")
        vm.setAmount(addedKey(0), "150000")

        vm.finish()

        assertEquals(listOf("Cash", "BCA", "Gopay"), accounts.all().map { it.name })
        val gopay = named("Gopay")
        assertEquals(Money(150_000), gopay.openingBalance)
        assertEquals(startOfToday, gopay.openingAsOf)
        val owed = named("Cash")
        assertEquals(AccountType.LIABILITY, owed.type)
        assertEquals(Money(-300_000), owed.openingBalance)
    }

    @Test
    fun `chosen categories are created and nothing else`() = runTest(dispatcher) {
        val vm = setup()
        vm.loaded()
        vm.toggleCategory(CategoryChoice("Food", CategoryKind.EXPENSE))
        vm.toggleCategory(CategoryChoice("Transport", CategoryKind.EXPENSE))
        vm.addCategory("Freelance", CategoryKind.INCOME)

        vm.finish()

        val created = categories.allCategories().map { it.name to it.kind }.toSet()
        assertEquals(
            setOf(
                "Food" to CategoryKind.EXPENSE,
                "Transport" to CategoryKind.EXPENSE,
                "Freelance" to CategoryKind.INCOME,
            ),
            created,
        )
    }

    @Test
    fun `a duplicate name keeps the user on the accounts step and writes nothing`() = runTest(dispatcher) {
        val vm = setup()
        vm.loaded()
        vm.rename(existingKey(cardId()), " cash ")

        vm.next()

        val state = vm.state.first { it.hasProblems }
        assertEquals(SetupStep.ACCOUNTS, state.step)
        assertEquals(DraftProblem.NAME_TAKEN, state.accounts.last().problem)
        assertEquals("Card", accounts.byId(cardId())!!.name)
        assertFalse(store().isComplete())
    }

    @Test
    fun `Done after a part-way failure creates no second account`() = runTest(dispatcher) {
        val vm = setup()
        vm.loaded()
        vm.addAccount()
        vm.rename(addedKey(0), "Gopay")
        vm.setAmount(addedKey(0), "150000")
        // What the failed first attempt left behind.
        accounts.create("Gopay", AccountType.BANK, Money.ZERO, startOfToday)

        vm.finish()

        assertEquals(1, accounts.all().count { it.name == "Gopay" })
        assertEquals(Money(150_000), named("Gopay").openingBalance)
    }

    // ---- Gate ---------------------------------------------------------------------------

    @Test
    fun `a fresh install is offered setup`() = runTest(dispatcher) {
        assertEquals(FirstRunGate.OFFER, gate().decided())
    }

    @Test
    fun `Skip writes nothing and marks setup done`() = runTest(dispatcher) {
        val before = accounts.all()
        val vm = gate()
        vm.decided()

        vm.skip()

        assertEquals(FirstRunGate.CLOSED, vm.gate.value)
        assertTrue(store().isComplete())
        assertEquals(before, accounts.all())
        assertTrue(categories.allCategories().isEmpty())
        assertFalse(transactions.hasAny())
    }

    @Test
    fun `setup is not offered when transactions exist`() = runTest(dispatcher) {
        val food = categories.createCategory("Food", CategoryKind.EXPENSE)
        transactions.record(now, TxnType.EXPENSE, Money(25_000), cashId(), categoryId = food)

        assertEquals(FirstRunGate.CLOSED, gate().decided())
        assertFalse(store().isComplete())
    }

    @Test
    fun `the flag persists across launches with an empty ledger`() = runTest(dispatcher) {
        gate().apply { decided() }.skip()

        // A new process: new store, new gate, same preferences file.
        assertEquals(FirstRunGate.CLOSED, gate().decided())
    }

    @Test
    fun `leaving an import that wrote rows completes first run`() = runTest(dispatcher) {
        val vm = gate()
        vm.decided()
        importExpense(cashId(), LocalDateTime(2026, 9, 15, 12, 0).toInstant(zone))

        vm.onImportLeft()

        assertEquals(FirstRunGate.CLOSED, vm.gate.first { it == FirstRunGate.CLOSED })
        assertTrue(store().isComplete())
    }

    @Test
    fun `backing out of an import with nothing imported keeps the welcome screen`() = runTest(dispatcher) {
        val vm = gate()
        vm.decided()

        vm.onImportLeft()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(FirstRunGate.OFFER, vm.gate.value)
        assertFalse(store().isComplete())

        // The same call closes the gate once rows exist, so the check above was not
        // passing only because the read had not landed yet.
        importExpense(cashId(), LocalDateTime(2026, 9, 15, 12, 0).toInstant(zone))
        vm.onImportLeft()
        vm.gate.first { it == FirstRunGate.CLOSED }
    }

    // ---- Import after a manual setup ----------------------------------------------------

    private var serial = 0

    private suspend fun importRows(rows: List<PendingRow>) =
        imports.commit("history.xlsx", rows, rows.size, now)

    private suspend fun expenseRow(accountId: Long, at: Instant): PendingRow {
        val food = categories.findOrCreate("Food", CategoryKind.EXPENSE)
        return PendingRow(at, TxnType.EXPENSE, Money(40_000), accountId, null, food, null, "lunch", "row-${serial++}")
    }

    private suspend fun importExpense(accountId: Long, at: Instant) = importRows(listOf(expenseRow(accountId, at)))

    private suspend fun setUpCashAt250k() {
        val vm = setup()
        vm.loaded()
        vm.setAmount(existingKey(cashId()), "250000")
        vm.setAmount(existingKey(cardId()), "4100000")
        vm.finish()
    }

    @Test
    fun `older expenses imported after setup do not move the stated balance`() = runTest(dispatcher) {
        setUpCashAt250k()

        importExpense(cashId(), LocalDateTime(2026, 9, 15, 12, 0).toInstant(zone))

        assertTrue(transactions.hasAny())
        assertEquals(Money(250_000), named("Cash").balance)
    }

    @Test
    fun `an expense after setup still moves the balance`() = runTest(dispatcher) {
        setUpCashAt250k()

        importExpense(cashId(), LocalDateTime(2026, 10, 30, 9, 0).toInstant(zone))

        assertEquals(Money(210_000), named("Cash").balance)
    }

    @Test
    fun `an older transfer imported after setup is refused and nothing is written`() = runTest(dispatcher) {
        setUpCashAt250k()
        val older = LocalDateTime(2026, 10, 1, 12, 0).toInstant(zone)
        val rows = listOf(
            expenseRow(cashId(), LocalDateTime(2026, 9, 15, 12, 0).toInstant(zone)),
            PendingRow(older, TxnType.TRANSFER, Money(500_000), cardId(), cashId(), null, null, null, "row-${serial++}"),
        )

        try {
            importRows(rows)
            fail("A transfer before Card's as-of date must be refused")
        } catch (refused: IllegalArgumentException) {
            assertTrue(refused.message.orEmpty().contains("Card"))
        }

        assertFalse(transactions.hasAny())
        assertEquals(Money(250_000), named("Cash").balance)
        assertEquals(Money(4_100_000), named("Card").balance)
    }
}
