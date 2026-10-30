package com.dvnnfrr.saku.ui.search

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.data.repository.RoomAccountRepository
import com.dvnnfrr.saku.data.repository.RoomCategoryRepository
import com.dvnnfrr.saku.data.repository.RoomTransactionRepository
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TransactionFilter
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.ui.filter.SharedFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchViewModelTest {

    private val zone = TimeZone.of("Asia/Jakarta")
    private val dispatcher = StandardTestDispatcher()

    private lateinit var db: SakuDatabase
    private lateinit var transactions: RoomTransactionRepository
    private lateinit var accounts: RoomAccountRepository
    private lateinit var categories: RoomCategoryRepository
    private val shared = SharedFilter()

    private var bca = 0L
    private var cash = 0L
    private var makan = 0L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SakuDatabase::class.java,
        ).allowMainThreadQueries().build()
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
        bca = accounts.create("BCA", AccountType.BANK, Money.ZERO, Instant.fromEpochSeconds(0))
        cash = accounts.create("Cash", AccountType.CASH, Money.ZERO, Instant.fromEpochSeconds(0))
        makan = categories.createCategory("Makan & Minum", CategoryKind.EXPENSE)
    }

    private suspend fun spend(note: String, account: Long, amount: Long = 25_000, day: Int = 1) =
        transactions.record(
            occurredAt = LocalDateTime(2026, 10, day, 12, 0).toInstant(zone),
            type = TxnType.EXPENSE,
            amount = Money(amount),
            accountId = account,
            categoryId = makan,
            note = note,
        )

    private fun viewModel() = SearchViewModel(transactions, accounts, categories, shared, zone)

    @Test
    fun `search opens empty and matches after the query settles`() = runTest(dispatcher) {
        seed()
        spend("Kopi Kenangan", bca)
        val vm = viewModel()
        assertFalse(vm.state.first().searched)

        vm.setQuery("kopi")

        assertEquals("kopi", vm.query.value)
        assertEquals(1, vm.state.first { it.searched }.count)
    }

    @Test
    fun `matches outside the filter are counted, not listed`() = runTest(dispatcher) {
        seed()
        repeat(3) { spend("kopi", cash, day = it + 1) }
        repeat(2) { spend("kopi", bca, day = it + 10) }
        shared.set(TransactionFilter(accountIds = setOf(cash)))
        val vm = viewModel()

        vm.setQuery("kopi")
        val state = vm.state.first { it.searched }

        assertEquals(3, state.count)
        assertEquals(2, state.hiddenCount)
        assertEquals(shared.state.value, state.filter)
    }

    @Test
    fun `an edit updates the results under the same query`() = runTest(dispatcher) {
        seed()
        val id = spend("grab", bca, amount = 25_000)
        val vm = viewModel()
        vm.setQuery("grab 25000")
        assertEquals(1, vm.state.first { it.searched }.count)

        val stored = requireNotNull(transactions.byId(id))
        transactions.update(stored.copy(amount = Money(32_000)))

        // The amount no longer matches, so the row drops out without retyping.
        assertEquals(0, vm.state.first { it.searched && it.count == 0 }.count)
        assertEquals("grab 25000", vm.query.value)
    }
}
