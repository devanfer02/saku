package com.dvnnfrr.saku.ui.entry

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dvnnfrr.saku.data.FirstRunSeed
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.data.repository.RoomAccountRepository
import com.dvnnfrr.saku.data.repository.RoomCategoryRepository
import com.dvnnfrr.saku.data.repository.RoomTransactionRepository
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.domain.TransactionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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

/** Covers the write-path scenarios in specs/transaction-entry/spec.md end to end. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransactionEntryViewModelTest {

    private val zone = TimeZone.of("Asia/Jakarta")
    private val dispatcher = StandardTestDispatcher()
    private val august = DateRange.ofMonth(LocalDate(2026, 8, 1))

    private lateinit var db: SakuDatabase
    private lateinit var transactions: RoomTransactionRepository
    private lateinit var accounts: RoomAccountRepository
    private lateinit var categories: RoomCategoryRepository

    private var bca = 0L
    private var rdpu = 0L
    private var makan = 0L
    private var gaji = 0L
    private var restoran = 0L

    private val clock = object : Clock {
        override fun now(): Instant = LocalDateTime(2026, 8, 17, 9, 0).toInstant(zone)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // Room runs queries on its own executor and its Flows emit from there, so
        // advanceUntilIdle() would return while the work was still on a background
        // thread. Pinning both executors to the test dispatcher makes it deterministic.
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SakuDatabase::class.java,
        )
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .allowMainThreadQueries()
            .build()
        transactions = RoomTransactionRepository(db)
        accounts = RoomAccountRepository(db)
        categories = RoomCategoryRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun seed() {
        val asOf = LocalDateTime(2026, 8, 1, 0, 0).toInstant(zone)
        bca = accounts.create("BCA", AccountType.BANK, Money.ZERO, asOf)
        rdpu = accounts.create("RDPU", AccountType.INVESTMENT, Money.ZERO, asOf)
        makan = categories.createCategory("Makan & Minum", CategoryKind.EXPENSE)
        gaji = categories.createCategory("Gaji", CategoryKind.INCOME)
        restoran = categories.findOrCreateSubcategory(makan, "Restoran")
    }

    private fun viewModel() = TransactionEntryViewModel(
        transactions = transactions,
        categoryRepository = categories,
        accountRepository = accounts,
        zone = zone,
        clock = clock,
    )

    private fun type(vm: TransactionEntryViewModel, digits: String) {
        digits.forEach { vm.setAmount(vm.state.value.form.amount.digit(it - '0')) }
    }

    @Test
    fun `a new sheet opens on Expense with the first account already chosen`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()

        vm.open()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(TxnType.EXPENSE, state.form.type)
        assertEquals(bca, state.form.accountId)
        assertEquals(LocalDateTime(2026, 8, 17, 9, 0), state.form.occurredAt)
        // Only expense categories are offered.
        assertEquals(listOf("Makan & Minum"), state.categories.map { it.name })
    }

    /**
     * A brand-new install seeds Cash and Card and no categories. The picker gets an
     * empty list (it still offers "New category"), and the first category is made from
     * the sheet itself, after which the entry saves.
     */
    @Test
    fun `after first run the sheet opens with no categories and creates the first one`() =
        runTest(dispatcher) {
            FirstRunSeed.applyIfEmpty(db, openingAsOf = clock.now(), zone = zone)
            val vm = viewModel()
            vm.open()
            advanceUntilIdle()

            val opened = vm.state.value
            assertEquals(listOf("Cash", "Card"), opened.accounts.map { it.name })
            assertEquals(opened.accounts.first().id, opened.form.accountId)
            assertTrue(opened.categories.isEmpty())

            type(vm, "25000")
            vm.createCategory("Food")
            advanceUntilIdle()
            val created = vm.state.value
            assertEquals(listOf("Food"), created.categories.map { it.name })
            assertEquals(created.categories.single().id, created.form.categoryId)

            vm.save()
            advanceUntilIdle()
            val stored = transactions.observeInRange(august, zone, TransactionFilter.ALL).first().single()
            assertEquals(Money(25_000), stored.amount)
            assertEquals(created.form.categoryId, stored.categoryId)
            assertTrue(vm.state.value.saved)
        }

    @Test
    fun `after first run an income category is created with the income kind`() =
        runTest(dispatcher) {
            FirstRunSeed.applyIfEmpty(db, openingAsOf = clock.now(), zone = zone)
            val vm = viewModel()
            vm.open()
            advanceUntilIdle()

            vm.setType(TxnType.INCOME)
            advanceUntilIdle()
            assertTrue(vm.state.value.categories.isEmpty())

            vm.createCategory("Salary")
            advanceUntilIdle()
            assertEquals(CategoryKind.INCOME, categories.allCategories().single().kind)
            assertEquals(listOf("Salary"), vm.state.value.categories.map { it.name })
        }

    @Test
    fun `recording an expense stores it`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        vm.open()
        advanceUntilIdle()

        type(vm, "187")
        vm.setAmount(vm.state.value.form.amount.tripleZero())
        vm.selectCategory(makan)
        advanceUntilIdle()
        vm.setNote("Padang")
        vm.save()
        advanceUntilIdle()

        val stored = transactions.observeInRange(august, zone, TransactionFilter.ALL).first()
        assertEquals(1, stored.size)
        assertEquals(Money(187_000), stored.single().amount)
        assertEquals(makan, stored.single().categoryId)
        assertEquals("Padang", stored.single().note)
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun `inline arithmetic saves one transaction of the total`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        vm.open()
        advanceUntilIdle()

        type(vm, "16000")
        vm.setAmount(vm.state.value.form.amount.operator(AmountOperator.PLUS))
        type(vm, "14000")
        vm.selectCategory(makan)
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()

        val stored = transactions.observeInRange(august, zone, TransactionFilter.ALL).first()
        assertEquals(1, stored.size)
        assertEquals(Money(30_000), stored.single().amount)
    }

    @Test
    fun `saving without a category is blocked and reports the field`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        vm.open()
        advanceUntilIdle()

        type(vm, "45000")
        vm.save()
        advanceUntilIdle()

        assertTrue(transactions.observeInRange(august, zone, TransactionFilter.ALL).first().isEmpty())
        assertEquals(EntryProblem.CATEGORY_MISSING, vm.state.value.problemFor(EntryField.CATEGORY))
        assertFalse(vm.state.value.saved)
    }

    @Test
    fun `problems stay hidden until the first save attempt`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        vm.open()
        advanceUntilIdle()

        // Nothing entered, but nothing is wrong yet either — the user has not finished.
        assertTrue(vm.state.value.problems.isEmpty())
    }

    @Test
    fun `switching type reloads the category list`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        vm.open()
        advanceUntilIdle()

        vm.setType(TxnType.INCOME)
        advanceUntilIdle()

        assertEquals(listOf("Gaji"), vm.state.value.categories.map { it.name })
        assertNull(vm.state.value.form.categoryId)
    }

    /** A row from before subcategories were dropped, still carrying Restoran. */
    private suspend fun recordWithStoredSubcategory(): Long {
        val id = transactions.record(
            occurredAt = LocalDateTime(2026, 8, 10, 12, 0).toInstant(zone),
            type = TxnType.EXPENSE,
            amount = Money(50_000),
            accountId = bca,
            categoryId = makan,
            note = "Padang",
        )
        transactions.update(requireNotNull(transactions.byId(id)).copy(subcategoryId = restoran))
        return id
    }

    @Test
    fun `a new transaction saves no subcategory even where one was used before`() =
        runTest(dispatcher) {
            seed()
            recordWithStoredSubcategory()
            val vm = viewModel()
            vm.open()
            advanceUntilIdle()
            vm.selectCategory(makan)
            type(vm, "25000")
            vm.save()
            advanceUntilIdle()

            val saved = transactions.observeInRange(august, zone, TransactionFilter.ALL).first()
                .single { it.amount == Money(25_000) }
            assertNull(saved.subcategoryId)
        }

    @Test
    fun `editing an old row's note keeps its stored subcategory`() = runTest(dispatcher) {
        seed()
        val id = recordWithStoredSubcategory()
        val vm = viewModel()
        vm.open(editingId = id)
        advanceUntilIdle()

        vm.setNote("Padang Sederhana")
        vm.save()
        advanceUntilIdle()

        val stored = requireNotNull(transactions.byId(id))
        assertEquals("Padang Sederhana", stored.note)
        assertEquals(restoran, stored.subcategoryId)
    }

    @Test
    fun `moving an old row to another category clears its stored subcategory`() =
        runTest(dispatcher) {
            seed()
            val lain = categories.createCategory("Jajan", CategoryKind.EXPENSE)
            val id = recordWithStoredSubcategory()
            val vm = viewModel()
            vm.open(editingId = id)
            advanceUntilIdle()

            vm.selectCategory(lain)
            vm.save()
            advanceUntilIdle()

            val stored = requireNotNull(transactions.byId(id))
            assertEquals(lain, stored.categoryId)
            assertNull(stored.subcategoryId)
        }

    @Test
    fun `creating a category inline selects it and keeps the other fields`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        vm.open()
        advanceUntilIdle()
        type(vm, "45000")
        vm.setNote("Kopi")

        vm.createCategory("Kafe")
        advanceUntilIdle()

        val state = vm.state.value
        assertNotNull(state.form.categoryId)
        assertEquals(Money(45_000), state.form.amount.value)
        assertEquals("Kopi", state.form.note)
        assertTrue(state.categories.any { it.name == "Kafe" })
    }

    @Test
    fun `a duplicate name selects the existing category and says so`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        vm.open()
        advanceUntilIdle()

        vm.createCategory("  makan & minum  ")
        advanceUntilIdle()

        // Case- and whitespace-insensitive. The user's intent was to file under that
        // name, and it exists, so select it rather than refusing.
        assertEquals(makan, vm.state.value.form.categoryId)
        assertNotNull(vm.state.value.duplicateName)
    }

    @Test
    fun `a transfer to the same account is blocked`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        vm.open()
        advanceUntilIdle()

        vm.setType(TxnType.TRANSFER)
        advanceUntilIdle()
        type(vm, "500000")
        vm.setDestination(bca)
        vm.save()
        advanceUntilIdle()

        assertEquals(
            EntryProblem.DESTINATION_SAME_AS_SOURCE,
            vm.state.value.problemFor(EntryField.DESTINATION),
        )
        assertTrue(transactions.observeInRange(august, zone, TransactionFilter.ALL).first().isEmpty())
    }

    @Test
    fun `a transfer stores one record with both accounts`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        vm.open()
        advanceUntilIdle()

        vm.setType(TxnType.TRANSFER)
        advanceUntilIdle()
        type(vm, "500000")
        vm.setDestination(rdpu)
        vm.save()
        advanceUntilIdle()

        val stored = transactions.observeInRange(august, zone, TransactionFilter.ALL).first().single()
        assertEquals(TxnType.TRANSFER, stored.type)
        assertEquals(bca, stored.accountId)
        assertEquals(rdpu, stored.toAccountId)
        assertNull(stored.categoryId)
    }

    @Test
    fun `editing keeps the import batch the row belongs to`() = runTest(dispatcher) {
        seed()
        val id = transactions.record(
            occurredAt = LocalDateTime(2026, 8, 10, 12, 0).toInstant(zone),
            type = TxnType.EXPENSE,
            amount = Money(187_000),
            accountId = bca,
            categoryId = makan,
        )

        val vm = viewModel()
        vm.open(editingId = id)
        advanceUntilIdle()
        assertEquals(Money(187_000), vm.state.value.form.amount.value)

        vm.setAmount(AmountInput.of(Money(170_000)))
        vm.save()
        advanceUntilIdle()

        val stored = requireNotNull(transactions.byId(id))
        assertEquals(Money(170_000), stored.amount)
        // Rebuilding the row from the form alone would drop createdAt and the batch
        // link, and reversing that batch would then leave the row behind.
        assertEquals(makan, stored.categoryId)
    }

    @Test
    fun `deleting removes only that transaction`() = runTest(dispatcher) {
        seed()
        val keep = transactions.record(
            occurredAt = LocalDateTime(2026, 8, 9, 12, 0).toInstant(zone),
            type = TxnType.EXPENSE, amount = Money(10_000), accountId = bca, categoryId = makan,
        )
        val drop = transactions.record(
            occurredAt = LocalDateTime(2026, 8, 10, 12, 0).toInstant(zone),
            type = TxnType.EXPENSE, amount = Money(20_000), accountId = bca, categoryId = makan,
        )

        val vm = viewModel()
        vm.open(editingId = drop)
        advanceUntilIdle()
        vm.delete()
        advanceUntilIdle()

        assertNull(transactions.byId(drop))
        assertNotNull(transactions.byId(keep))
    }

    // --- Note autocomplete (specs/note-autocomplete/spec.md) -------------------

    private suspend fun recordNoted(
        note: String,
        amount: Long,
        type: TxnType = TxnType.EXPENSE,
        day: Int = 10,
    ) = transactions.record(
        occurredAt = LocalDateTime(2026, 8, day, 12, 0).toInstant(zone),
        type = type,
        amount = Money(amount),
        accountId = bca,
        categoryId = if (type == TxnType.INCOME) gaji else makan,
        note = note,
    )

    private fun kotlinx.coroutines.test.TestScope.collectSuggestions(vm: TransactionEntryViewModel) {
        backgroundScope.launch(dispatcher) { vm.suggestions.collect {} }
    }

    @Test
    fun `typing suggests expense notes and picking fills category and last amount`() =
        runTest(dispatcher) {
            seed()
            recordNoted("bensin motor", 25_000, day = 3)
            recordNoted("bensin motor", 30_000, day = 9)
            val vm = viewModel()
            collectSuggestions(vm)
            vm.open()
            advanceUntilIdle()
            vm.setAccount(rdpu)

            vm.setNote("be")
            advanceUntilIdle()
            val suggestion = vm.suggestions.value.single()
            assertEquals("bensin motor", suggestion.note)
            assertEquals(makan, suggestion.category?.id)
            assertEquals(Money(30_000), suggestion.lastAmount)

            vm.pickSuggestion(suggestion)
            advanceUntilIdle()
            val form = vm.state.value.form
            assertEquals("bensin motor", form.note)
            assertEquals(makan, form.categoryId)
            assertEquals(Money(30_000), form.amount.value)
            assertEquals(rdpu, form.accountId)
            assertTrue(vm.suggestions.value.isEmpty())
        }

    @Test
    fun `suggestions follow the type and transfers get none`() = runTest(dispatcher) {
        seed()
        recordNoted("uang arisan", 500_000)
        recordNoted("uang saku", 1_000_000, type = TxnType.INCOME)
        val vm = viewModel()
        collectSuggestions(vm)
        vm.open()
        advanceUntilIdle()

        vm.setNote("ua")
        advanceUntilIdle()
        assertEquals(listOf("uang arisan"), vm.suggestions.value.map { it.note })

        vm.setType(TxnType.INCOME)
        vm.setNote("uan")
        advanceUntilIdle()
        assertEquals(listOf("uang saku"), vm.suggestions.value.map { it.note })

        vm.setType(TxnType.TRANSFER)
        vm.setNote("uang")
        advanceUntilIdle()
        assertTrue(vm.suggestions.value.isEmpty())
    }

    @Test
    fun `opening an edit with a note shows nothing until the note changes`() = runTest(dispatcher) {
        seed()
        val id = recordNoted("bensin motor", 30_000)
        val vm = viewModel()
        collectSuggestions(vm)

        vm.open(id)
        advanceUntilIdle()
        assertEquals("bensin motor", vm.state.value.form.note)
        assertTrue(vm.suggestions.value.isEmpty())

        vm.setNote("bensin moto")
        advanceUntilIdle()
        assertEquals(listOf("bensin motor"), vm.suggestions.value.map { it.note })
    }

    @Test
    fun `dismissing closes the list and typing reopens it`() = runTest(dispatcher) {
        seed()
        recordNoted("laundry", 35_000)
        val vm = viewModel()
        collectSuggestions(vm)
        vm.open()
        advanceUntilIdle()

        vm.setNote("la")
        advanceUntilIdle()
        assertEquals(1, vm.suggestions.value.size)

        vm.dismissSuggestions()
        advanceUntilIdle()
        assertTrue(vm.suggestions.value.isEmpty())
        assertEquals("la", vm.state.value.form.note)

        vm.setNote("lau")
        advanceUntilIdle()
        assertEquals(1, vm.suggestions.value.size)
    }

    @Test
    fun `a typed amount survives a pick`() = runTest(dispatcher) {
        seed()
        recordNoted("bensin motor", 30_000)
        val vm = viewModel()
        collectSuggestions(vm)
        vm.open()
        advanceUntilIdle()

        type(vm, "50000")
        vm.setNote("bensin")
        advanceUntilIdle()
        vm.pickSuggestion(vm.suggestions.value.single())

        assertEquals(Money(50_000), vm.state.value.form.amount.value)
        assertEquals(makan, vm.state.value.form.categoryId)
    }

    @Test
    fun `a saved note is suggested on the next sheet`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        collectSuggestions(vm)
        vm.open()
        advanceUntilIdle()
        type(vm, "15000")
        vm.selectCategory(makan)
        vm.setNote("es teh")
        vm.save()
        advanceUntilIdle()

        vm.open()
        advanceUntilIdle()
        vm.setNote("es")
        advanceUntilIdle()

        assertEquals(listOf("es teh"), vm.suggestions.value.map { it.note })
    }
}
