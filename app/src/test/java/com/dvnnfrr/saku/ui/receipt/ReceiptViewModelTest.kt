package com.dvnnfrr.saku.ui.receipt

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.data.repository.RoomAccountRepository
import com.dvnnfrr.saku.data.repository.RoomCategoryRepository
import com.dvnnfrr.saku.data.repository.RoomTagRepository
import com.dvnnfrr.saku.data.repository.RoomTransactionRepository
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.receipt.OcrLine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Receipt screen's ViewModel with a fake recognizer and a real in-memory database. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReceiptViewModelTest {

    private val zone = TimeZone.of("Asia/Jakarta")
    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: SakuDatabase
    private lateinit var transactions: RoomTransactionRepository
    private lateinit var accounts: RoomAccountRepository
    private lateinit var categories: RoomCategoryRepository
    private lateinit var tags: RoomTagRepository
    private var defaults = ReceiptDefaults()
    private var cameraDiscarded = 0

    private val clock = object : Clock {
        override fun now(): Instant = LocalDateTime(2026, 10, 31, 19, 45).toInstant(zone)
    }

    private fun lines(vararg rows: String): List<OcrLine> =
        rows.mapIndexed { i, text -> OcrLine(text, 10, i * 30, 300, i * 30 + 20) }

    private val top = lines(
        "INDOMARET", "30/10/2026 08:12",
        "INDOMIE GRG SPCL 5 3.100 15.500",
        "MINYAK GORENG 2L 1 38.900 38.900",
        "BERAS PREMIUM 5KG 1 72.500 72.500",
    )
    private val bottom = lines(
        "MINYAK GORENG 2L 1 38.900 38.900",
        "BERAS PREMIUM 5KG 1 72.500 72.500",
        "SABUN CUCI 800ML 1 21.900 21.900",
        "TOTAL 148.800",
        "TUNAI 150.000",
    )
    private val photos = mapOf(
        Uri.parse("content://photo/top") to top,
        Uri.parse("content://photo/bottom") to bottom,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SakuDatabase::class.java)
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .allowMainThreadQueries()
            .build()
        transactions = RoomTransactionRepository(db)
        accounts = RoomAccountRepository(db)
        categories = RoomCategoryRepository(db)
        tags = RoomTagRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(
        first: String? = "content://photo/top",
        read: suspend (Uri) -> List<OcrLine> = { photos[it].orEmpty() },
    ): ReceiptViewModel {
        val vm = ReceiptViewModel(
            transactions = transactions,
            categoryRepository = categories,
            accountRepository = accounts,
            tagRepository = tags,
            readPhoto = read,
            newCameraUri = { Uri.EMPTY },
            discardCameraPhoto = { cameraDiscarded++ },
            loadDefaults = { defaults },
            rememberDefaults = { defaults = it },
            firstPhoto = first?.let(Uri::parse),
            zone = zone,
            clock = clock,
        )
        // The screen collects state; so does the test, or WhileSubscribed never starts.
        backgroundScope.launch { vm.state.collect {} }
        return vm
    }

    private suspend fun seed(): Pair<Long, Long> {
        val card = accounts.create("Card", AccountType.BANK, Money.ZERO, Instant.fromEpochSeconds(0))
        val makan = categories.createCategory("Makan & Minum", CategoryKind.EXPENSE)
        return card to makan
    }

    @Test
    fun `the first photo is read into items, with a reading state first`() = runTest(dispatcher) {
        seed()
        val gate = CompletableDeferred<Unit>()
        val vm = viewModel(read = { gate.await(); photos[it].orEmpty() })
        advanceUntilIdle()
        assertTrue(vm.state.value.reading)

        gate.complete(Unit)
        advanceUntilIdle()
        val state = vm.state.value
        assertFalse(state.reading)
        assertEquals(1, state.photos)
        assertEquals("Indomaret", state.store)
        assertEquals(listOf("INDOMIE GRG SPCL", "MINYAK GORENG 2L", "BERAS PREMIUM 5KG"), state.items.map { it.name })
    }

    @Test
    fun `add next part joins the overlap once and the check matches`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        advanceUntilIdle()
        vm.addPhoto(Uri.parse("content://photo/bottom"))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(2, state.photos)
        assertEquals(4, state.items.size)
        assertEquals(1, state.items.count { it.name == "BERAS PREMIUM 5KG" })
        assertTrue(state.check!!.matches)
        assertEquals(emptyList<Int>(), state.gapsAfterPhoto)
    }

    @Test
    fun `a photo with no text says so and adds no part`() = runTest(dispatcher) {
        seed()
        val vm = viewModel()
        advanceUntilIdle()
        vm.addPhoto(Uri.parse("content://photo/blank"))
        advanceUntilIdle()

        assertTrue(vm.state.value.unreadable)
        assertEquals(1, vm.state.value.photos)
    }

    @Test
    fun `save with unset categories asks, and use for all unset fills them and saves`() = runTest(dispatcher) {
        val (card, makan) = seed()
        val vm = viewModel()
        advanceUntilIdle()
        vm.addPhoto(Uri.parse("content://photo/bottom"))
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()
        assertTrue(vm.askingCategory.value)
        assertTrue(transactions.all().isEmpty())

        vm.useForAllUnset(makan)
        advanceUntilIdle()
        assertFalse(vm.askingCategory.value)
        val saved = transactions.all()
        assertEquals(4, saved.size)
        assertEquals(148_800L, saved.sumOf { it.amount.rupiah })
        assertTrue(saved.all { it.categoryId == makan && it.accountId == card })
        assertEquals(LocalDate(2026, 10, 30), vm.savedDay.value)
    }

    @Test
    fun `the next scan finds saved items in history and the tag is remembered`() = runTest(dispatcher) {
        val (card, makan) = seed()
        val grocery = tags.findOrCreate("Monthly grocery")
        val first = viewModel()
        advanceUntilIdle()
        first.addTypedTag("Monthly grocery")
        first.save()
        advanceUntilIdle()
        first.useForAllUnset(makan)
        advanceUntilIdle()
        assertEquals(ReceiptDefaults(card, setOf(grocery)), defaults)
        assertTrue(transactions.all().all { it.tagIds == setOf(grocery) })

        val second = viewModel(first = "content://photo/bottom")
        advanceUntilIdle()
        val state = second.state.value
        assertEquals(listOf(true, true, false), state.items.map { it.fromHistory })
        assertEquals(listOf("Monthly grocery"), state.tags.map { it.name })
    }

    @Test
    fun `clearing the tag saves untagged rows and is remembered`() = runTest(dispatcher) {
        val (_, makan) = seed()
        defaults = ReceiptDefaults(tagIds = setOf(tags.findOrCreate("Monthly grocery")))
        val vm = viewModel()
        advanceUntilIdle()
        vm.removeTag(vm.state.value.tags.single())
        vm.setRecordAs(RecordAs.ONE_ROW)
        vm.setOneRowCategory(makan)
        // The state is derived from the choices; the screen's Save comes frames later.
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()

        val saved = transactions.all().single()
        assertEquals(emptySet<Long>(), saved.tagIds)
        assertEquals("Indomaret", saved.note)
        assertEquals(emptySet<Long>(), defaults.tagIds)
    }

    @Test
    fun `a camera closed without a photo drops its file`() = runTest(dispatcher) {
        val vm = viewModel(first = null)
        vm.cameraCancelled()

        assertEquals(1, cameraDiscarded)
        assertNull(vm.savedDay.value)
    }
}
