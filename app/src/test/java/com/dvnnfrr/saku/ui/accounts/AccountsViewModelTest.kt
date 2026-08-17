package com.dvnnfrr.saku.ui.accounts

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dvnnfrr.saku.data.FirstRunSeed
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.data.repository.RoomAccountRepository
import com.dvnnfrr.saku.data.repository.RoomCategoryRepository
import com.dvnnfrr.saku.data.repository.RoomTransactionRepository
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The accounts screen against real repositories on an in-memory database.
 *
 * Fakes would prove only that the fakes agree with each other, and the interesting
 * scenarios here are all about what the database actually does: the first-run seed, an
 * archived account keeping its history, a reconciliation writing a visible row, and the
 * opening-date rule refusing to split a transfer.
 *
 * The first test is the one that matters most. The balance formula exists twice — in
 * SQL, and again in `AccountsAssembly` for the detail's running column — and two
 * implementations of a money rule drift unless something holds them together.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountsViewModelTest {

    private val zone = TimeZone.of("Asia/Jakarta")
    private val dispatcher = StandardTestDispatcher()

    /** Parked on 20 Aug 2026, three days after the seeded cutover. */
    private val today = LocalDateTime(2026, 8, 20, 9, 0).toInstant(zone)
    private val cutover = LocalDateTime(2026, 8, 17, 0, 0).toInstant(zone)
    private val clock = object : Clock {
        override fun now(): Instant = today
    }

    private lateinit var db: SakuDatabase
    private lateinit var accounts: RoomAccountRepository
    private lateinit var transactions: RoomTransactionRepository
    private lateinit var categories: RoomCategoryRepository

    private var bca = 0L
    private var cash = 0L
    private var rdpu = 0L
    private var makan = 0L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SakuDatabase::class.java,
        ).allowMainThreadQueries().build()
        accounts = RoomAccountRepository(db)
        transactions = RoomTransactionRepository(db)
        categories = RoomCategoryRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    /**
     * The ViewModel plus a collector that lives as long as the test.
     *
     * `state` is a `WhileSubscribed` share: with nobody listening the pipeline does not
     * run and `state.value` never moves off its initial value, which reads in a test
     * exactly like the ViewModel ignoring the call it was just given.
     */
    private fun TestScope.viewModel(): AccountsViewModel {
        val vm = AccountsViewModel(
            accounts = accounts,
            transactions = transactions,
            categories = categories,
            zone = zone,
            clock = clock,
        )
        backgroundScope.launch(dispatcher) { vm.state.collect {} }
        return vm
    }

    /**
     * Waits for the outcome of a write.
     *
     * A repository write runs on Room's own transaction dispatcher, so draining the test
     * scheduler proves nothing about whether it finished. Every mutation this screen
     * makes ends in a message or in a new balance, so the tests wait on one of those.
     */
    private suspend fun AccountsViewModel.awaitMessage(): AccountsMessage =
        state.first { it.message != null }.message!!

    private suspend fun seed() {
        bca = accounts.create("BCA", AccountType.BANK, Money(8_420_000), cutover)
        cash = accounts.create("Cash", AccountType.CASH, Money(385_000), cutover)
        rdpu = accounts.create("RDPU", AccountType.INVESTMENT, Money(5_000_000), cutover)
        makan = categories.createCategory("Makan & Minum", CategoryKind.EXPENSE)
    }

    private suspend fun record(
        type: TxnType,
        amount: Long,
        accountId: Long = bca,
        day: Int = 18,
        toAccountId: Long? = null,
        categoryId: Long? = makan,
    ) = transactions.record(
        occurredAt = LocalDateTime(2026, 8, day, 12, 0).toInstant(zone),
        type = type,
        amount = Money(amount),
        accountId = accountId,
        toAccountId = toAccountId,
        categoryId = categoryId,
    )

    private suspend fun AccountsViewModel.settled(): AccountsUiState = state.first { !it.loading }

    @Test
    fun `the Kotlin fold and the SQL aggregate agree on every account`() = runTest(dispatcher) {
        seed()
        record(TxnType.EXPENSE, 187_000)
        record(TxnType.INCOME, 4_200_000)
        record(TxnType.TRANSFER, 500_000, toAccountId = rdpu, categoryId = null)
        record(TxnType.EXPENSE, 45_000, accountId = cash)
        // Before the cutover, so the SQL ignores it and so must the fold.
        transactions.record(
            occurredAt = LocalDateTime(2026, 7, 4, 12, 0).toInstant(zone),
            type = TxnType.EXPENSE,
            amount = Money(1_000_000),
            accountId = bca,
            categoryId = makan,
        )

        val everything = transactions.all()
        accounts.all().forEach { account ->
            assertEquals(
                "balance of ${account.name}",
                account.balance,
                deriveBalance(account, everything),
            )
        }
    }

    @Test
    fun `the header equals the sum of the rows beneath it`() = runTest(dispatcher) {
        seed()
        accounts.create("Kartu", AccountType.LIABILITY, Money(-2_000_000), cutover)
        record(TxnType.EXPENSE, 187_000)

        val state = viewModel().settled()

        // Against the repository's own aggregate, which the header deliberately does
        // not read from — two paths, held together by this.
        val fromRepository = accounts.observeSummary().first()
        assertEquals(fromRepository.spendable, state.summary.spendable)
        assertEquals(fromRepository.invested, state.summary.invested)
        assertEquals(fromRepository.owed, state.summary.owed)
        assertEquals(fromRepository.total, state.summary.total)

        val listed = state.rows.fold(Money.ZERO) { total, row -> total + row.balance }
        assertEquals(listed, state.summary.total)
    }

    // Requirement: accounts are typed and user-managed.

    @Test
    fun `first run leaves four typed accounts, each with a slot and no opening balance`() =
        runTest(dispatcher) {
            FirstRunSeed.applyIfEmpty(db, openingAsOf = today, zone = zone)

            val state = viewModel().settled()
            val rows = state.rows

            assertEquals(
                listOf("BCA", "Jago", "Cash", "RDPU SucorInvest"),
                rows.sortedBy { it.id }.map { it.name },
            )
            assertEquals(
                listOf(
                    AccountType.BANK,
                    AccountType.BANK,
                    AccountType.CASH,
                    AccountType.INVESTMENT,
                ),
                rows.sortedBy { it.id }.map { it.type },
            )
            assertTrue(rows.all { it.openingBalance.isZero })
            assertEquals(4, rows.map { it.colorSlot }.toSet().size)
            // Nothing to chart while every balance is zero, and no divide by zero either.
            assertTrue(state.mix.isEmpty())
            assertEquals(Money.ZERO, state.summary.total)
        }

    @Test
    fun `an added account joins the list, the pickers and the donut`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        vm.settled()

        vm.create(
            AccountFormState(
                name = "Gopay",
                type = AccountType.BANK,
                colorSlot = null,
                openingBalance = Money(250_000),
                openingAsOf = LocalDate(2026, 8, 17),
            ),
        )
        advanceUntilIdle()

        val state = vm.state.first { it.rows.any { row -> row.name == "Gopay" } }
        val gopay = state.rows.first { it.name == "Gopay" }
        assertEquals(Money(250_000), gopay.balance)
        assertTrue(state.mix.any { it.accountId == gopay.id })
        // observeActive is what every picker reads.
        assertTrue(accounts.observeActive().first().any { it.name == "Gopay" })
    }

    @Test
    fun `a rename, a retype and a recolour keep the account's history and its balance`() =
        runTest(dispatcher) {
            seed()
            record(TxnType.EXPENSE, 187_000)
            val vm = viewModel()
            val before = vm.settled().rows.first { it.id == bca }
            val newSlot = ColorSlot(before.colorSlot.index + 1)

            vm.save(
                AccountFormState(
                    id = bca,
                    name = "BCA Digital",
                    type = AccountType.CASH,
                    colorSlot = newSlot,
                    openingBalance = before.openingBalance,
                    openingAsOf = before.openingAsOf,
                ),
            )
            advanceUntilIdle()

            // Every field, not just the name. The repository exposes one write per
            // column — deliberately, so a rename racing a reconcile cannot put back a
            // stale opening balance — so a save lands as four writes and the list is
            // briefly renamed but not yet retyped or recoloured. Waiting on a subset
            // matches one of those intermediate emissions and then asserts against a
            // field that has not landed yet, which is a flake, not a failure.
            val renamed = vm.state.first { s ->
                val row = s.rows.firstOrNull { it.id == bca }
                row != null &&
                    row.name == "BCA Digital" &&
                    row.type == AccountType.CASH &&
                    row.colorSlot == newSlot
            }.rows.first { it.id == bca }
            assertEquals(AccountType.CASH, renamed.type)
            assertEquals(newSlot, renamed.colorSlot)
            assertEquals(Money(8_233_000), renamed.balance)
            assertEquals(1, transactions.all().count { it.accountId == bca })
        }

    @Test
    fun `a blank name is refused rather than written`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        val before = vm.settled().rows.first { it.id == bca }

        vm.save(
            AccountFormState(
                id = bca,
                name = "   ",
                type = before.type,
                colorSlot = before.colorSlot,
                openingBalance = before.openingBalance,
                openingAsOf = before.openingAsOf,
            ),
        )
        advanceUntilIdle()

        assertTrue(vm.state.value.message is AccountsMessage.Refused)
        assertEquals("BCA", accounts.byId(bca)?.name)
    }

    // Requirement: archiving.

    @Test
    fun `archiving keeps the transactions and drops the account from every figure`() =
        runTest(dispatcher) {
            seed()
            record(TxnType.EXPENSE, 85_000, accountId = cash)
            val vm = viewModel()
            val totalBefore = vm.settled().summary.total

            vm.setArchived(cash, archived = true)
            advanceUntilIdle()

            val state = vm.state.first { it.archived.isNotEmpty() }
            assertTrue(state.rows.none { it.id == cash })
            assertEquals(listOf("Cash"), state.archived.map { it.name })
            assertEquals(totalBefore - Money(300_000), state.summary.total)
            assertTrue(state.mix.none { it.accountId == cash })
            // Out of the pickers, but every row it is named on is still there.
            assertTrue(accounts.observeActive().first().none { it.id == cash })
            assertEquals(1, transactions.all().count { it.accountId == cash })
        }

    @Test
    fun `an archived account can be brought back`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        vm.settled()
        vm.setArchived(cash, archived = true)
        advanceUntilIdle()
        vm.state.first { it.archived.isNotEmpty() }

        vm.setArchived(cash, archived = false)
        advanceUntilIdle()

        assertTrue(vm.state.first { it.archived.isEmpty() }.rows.any { it.id == cash })
    }

    // Requirement: reconcile an account against its real balance.

    @Test
    fun `reconciling downward writes a dated adjustment for the difference`() =
        runTest(dispatcher) {
            seed()
            // 1.038.500 on Cash: 385.000 opening plus 653.500 of income.
            record(TxnType.INCOME, 653_500, accountId = cash)
            val vm = viewModel()
            assertEquals(Money(1_038_500), vm.settled().rows.first { it.id == cash }.balance)

            vm.reconcile(cash, Money(985_000))

            assertEquals(
                AccountsMessage.Reconciled("Cash", adjustment = Money(-53_500)),
                vm.awaitMessage(),
            )
            val adjustment = transactions.all().single { it.type == TxnType.ADJUSTMENT }
            assertEquals(Money(-53_500), adjustment.amount)
            assertEquals(cash, adjustment.accountId)
            assertEquals(today, adjustment.occurredAt)
            assertEquals(
                Money(985_000),
                vm.state.first { s -> s.rows.first { it.id == cash }.balance == Money(985_000) }
                    .rows.first { it.id == cash }.balance,
            )
        }

    @Test
    fun `reconciling with no difference writes no adjustment but still records the date`() =
        runTest(dispatcher) {
            seed()
            val vm = viewModel()
            vm.settled()

            vm.reconcile(cash, Money(385_000))

            assertEquals(
                AccountsMessage.Reconciled("Cash", adjustment = null),
                vm.awaitMessage(),
            )
            assertTrue(transactions.all().none { it.type == TxnType.ADJUSTMENT })
            // The date is still stamped: "checked, and it was right" is exactly the fact
            // the staleness flag is asking about.
            assertNotNull(accounts.byId(cash)?.lastReconciled)
        }

    @Test
    fun `an adjustment is an ordinary row - visible, editable and deletable`() =
        runTest(dispatcher) {
            seed()
            val vm = viewModel()
            vm.settled()
            vm.reconcile(cash, Money(300_000))
            advanceUntilIdle()

            vm.openDetail(cash)
            val detail = vm.state.first { it.detail?.entries?.isNotEmpty() == true }.detail!!
            val entry = detail.entries.single()
            assertEquals(TxnType.ADJUSTMENT, entry.type)
            assertEquals(Money(-85_000), entry.effect)
            assertEquals(Money(300_000), entry.runningBalance)

            // It is in the transaction list like anything else, and it can be removed.
            val august = DateRange.ofMonth(LocalDate(2026, 8, 1))
            assertTrue(
                transactions.observeInRange(august, zone).first()
                    .any { it.id == entry.transactionId },
            )
            transactions.delete(entry.transactionId)
            assertNull(transactions.byId(entry.transactionId))
            assertEquals(Money(385_000), accounts.byId(cash)?.balance)
        }

    @Test
    fun `an adjustment is excluded from the period's income and expense`() =
        runTest(dispatcher) {
            seed()
            record(TxnType.EXPENSE, 100_000, accountId = cash)
            val vm = viewModel()
            vm.settled()

            vm.reconcile(cash, Money(1_000_000))
            advanceUntilIdle()

            val august = DateRange.ofMonth(LocalDate(2026, 8, 1))
            val totals = transactions.observePeriodTotals(august, zone).first()
            // The 715.000 correction is neither income nor spending; it corrects a
            // balance rather than recording a flow.
            assertEquals(Money(100_000), totals.expense)
            assertEquals(Money.ZERO, totals.income)
        }

    // Requirement: set the opening balance after an account exists.

    @Test
    fun `first-run setup completes by giving a seeded account its real figures`() =
        runTest(dispatcher) {
            FirstRunSeed.applyIfEmpty(db, openingAsOf = today, zone = zone)
            val vm = viewModel()
            val seeded = vm.settled().rows.first { it.name == "BCA" }
            assertTrue(seeded.balance.isZero)

            vm.save(
                AccountFormState(
                    id = seeded.id,
                    name = seeded.name,
                    type = seeded.type,
                    colorSlot = seeded.colorSlot,
                    openingBalance = Money(8_420_000),
                    openingAsOf = LocalDate(2026, 8, 17),
                ),
            )
            advanceUntilIdle()

            val updated = vm.state.first { s -> s.rows.any { !it.balance.isZero } }
                .rows.first { it.name == "BCA" }
            assertEquals(Money(8_420_000), updated.balance)
            assertEquals(LocalDate(2026, 8, 17), updated.openingAsOf)
        }

    // Requirement: a transfer must postdate both accounts' as-of dates.

    @Test
    fun `moving a cutover past a transfer is refused with a message naming the account`() =
        runTest(dispatcher) {
            seed()
            record(TxnType.TRANSFER, 500_000, day = 18, toAccountId = rdpu, categoryId = null)
            val vm = viewModel()
            val before = vm.settled().rows.first { it.id == bca }

            vm.save(
                AccountFormState(
                    id = bca,
                    name = before.name,
                    type = before.type,
                    colorSlot = before.colorSlot,
                    openingBalance = before.openingBalance,
                    // Past the transfer on the 18th, which would leave it counted on
                    // RDPU's leg and not on BCA's.
                    openingAsOf = LocalDate(2026, 8, 19),
                ),
            )

            val message = vm.awaitMessage()
            assertTrue(message is AccountsMessage.Refused)
            assertTrue((message as AccountsMessage.Refused).reason.contains("BCA"))
            // Nothing moved: the cutover is where it was and so is the balance.
            assertEquals(cutover, accounts.byId(bca)?.openingAsOf)
            assertEquals(Money(7_920_000), accounts.byId(bca)?.balance)

            vm.dismissMessage()
            advanceUntilIdle()
            assertNull(vm.state.value.message)
        }

    // Selection, which is what a donut tap does.

    @Test
    fun `tapping a segment selects its account and tapping it again clears it`() =
        runTest(dispatcher) {
            seed()
            val vm = viewModel()
            val state = vm.settled()
            // Largest first: BCA 8.42m, RDPU 5m, Cash 385k.
            assertEquals(listOf(bca, rdpu, cash), state.mix.map { it.accountId })

            vm.selectSegment(1)
            advanceUntilIdle()
            assertEquals(rdpu, vm.state.value.selectedAccountId)
            assertEquals("RDPU", vm.state.value.selected?.name)

            vm.selectSegment(1)
            advanceUntilIdle()
            assertNull(vm.state.value.selectedAccountId)

            // A miss on the ring clears rather than selecting nothing-in-particular.
            vm.selectSegment(0)
            advanceUntilIdle()
            vm.selectSegment(null)
            advanceUntilIdle()
            assertNull(vm.state.value.selectedAccountId)
        }

    @Test
    fun `a selection dies with the segment it pointed at`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        vm.settled()
        vm.selectSegment(2)
        advanceUntilIdle()
        assertEquals(cash, vm.state.value.selectedAccountId)

        vm.setArchived(cash, archived = true)

        assertNull(vm.state.first { it.archived.isNotEmpty() }.selectedAccountId)
    }

    // The detail.

    @Test
    fun `the detail lists what moved the balance, newest first, and ends on the balance`() =
        runTest(dispatcher) {
            seed()
            record(TxnType.EXPENSE, 187_000, day = 18)
            record(TxnType.TRANSFER, 500_000, day = 19, toAccountId = rdpu, categoryId = null)
            // Before the cutover: real, but it moves nothing here.
            transactions.record(
                occurredAt = LocalDateTime(2026, 7, 4, 12, 0).toInstant(zone),
                type = TxnType.EXPENSE,
                amount = Money(1_000_000),
                accountId = bca,
                categoryId = makan,
            )
            val vm = viewModel()
            vm.settled()

            vm.openDetail(bca)
            val detail = vm.state.first { it.detail != null }.detail!!

            assertEquals(2, detail.entries.size)
            assertEquals(Money(-500_000), detail.entries.first().effect)
            assertEquals(detail.account.balance, detail.entries.first().runningBalance)
            assertEquals("Makan & Minum", detail.entries.last().label)
            assertEquals(Money(8_420_000), detail.account.openingBalance)

            vm.closeDetail()
            assertNull(vm.state.first { it.detail == null }.detail)
        }

    @Test
    fun `the destination of a transfer sees it as money arriving`() = runTest(dispatcher) {
        seed()
        record(TxnType.TRANSFER, 500_000, day = 19, toAccountId = rdpu, categoryId = null)
        val vm = viewModel()
        vm.settled()

        vm.openDetail(rdpu)
        val detail = vm.state.first { it.detail?.entries?.isNotEmpty() == true }.detail!!

        assertEquals(Money(500_000), detail.entries.single().effect)
        assertEquals(Money(5_500_000), detail.entries.single().runningBalance)
        assertFalse(detail.entries.single().effect.isNegative)
    }
}
