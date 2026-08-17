package com.dvnnfrr.saku.ui.categories

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
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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

/** Covers every scenario in specs/category-management/spec.md. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CategoryManagerViewModelTest {

    private val zone = TimeZone.of("Asia/Jakarta")
    private val dispatcher = StandardTestDispatcher()

    private lateinit var db: SakuDatabase
    private lateinit var categories: RoomCategoryRepository
    private lateinit var transactions: RoomTransactionRepository
    private lateinit var accounts: RoomAccountRepository

    private var bca = 0L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SakuDatabase::class.java,
        )
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .allowMainThreadQueries()
            .build()
        categories = RoomCategoryRepository(db)
        transactions = RoomTransactionRepository(db)
        accounts = RoomAccountRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun account(): Long {
        if (bca == 0L) {
            bca = accounts.create(
                name = "BCA",
                type = AccountType.BANK,
                openingBalance = Money.ZERO,
                openingAsOf = LocalDateTime(2026, 8, 1, 0, 0).toInstant(zone),
            )
        }
        return bca
    }

    private suspend fun spend(
        categoryId: Long,
        subcategoryId: Long? = null,
        times: Int = 1,
    ) {
        val accountId = account()
        repeat(times) { index ->
            transactions.record(
                occurredAt = LocalDateTime(2026, 8, 10, 9, index % 60).toInstant(zone),
                type = TxnType.EXPENSE,
                amount = Money(10_000L + index),
                accountId = accountId,
                categoryId = categoryId,
                subcategoryId = subcategoryId,
            )
        }
    }

    /**
     * The state is a WhileSubscribed flow, so it produces nothing until something reads
     * it. A background collector stands in for the screen.
     */
    private fun TestScope.manager(): CategoryManagerViewModel {
        val viewModel = CategoryManagerViewModel(categories)
        backgroundScope.launch { viewModel.state.collect {} }
        return viewModel
    }

    // Requirement: Manage categories from a dedicated screen

    @Test
    fun `expense categories are listed in sort order with a colour and a subcategory count`() =
        runTest(dispatcher) {
            val pemberian = categories.createCategory("Pemberian", CategoryKind.EXPENSE)
            categories.createCategory("Makan & Minum", CategoryKind.EXPENSE)
            categories.createCategory("Gaji", CategoryKind.INCOME)
            categories.createSubcategory(pemberian, "Arisan")
            categories.createSubcategory(pemberian, "Rumah")

            val viewModel = manager()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(listOf("Pemberian", "Makan & Minum"), state.active.map { it.name })
            assertEquals(2, state.active.first().subcategoryCount)
            assertEquals(ColorSlot(0), state.active.first().colorSlot)
            assertEquals(ColorSlot(1), state.active[1].colorSlot)
        }

    @Test
    fun `expanding a category reveals its subcategories with their transaction counts`() =
        runTest(dispatcher) {
            val pemberian = categories.createCategory("Pemberian", CategoryKind.EXPENSE)
            val arisan = categories.createSubcategory(pemberian, "Arisan")
            categories.createSubcategory(pemberian, "Rumah")
            spend(pemberian, arisan, times = 3)

            val viewModel = manager()
            advanceUntilIdle()
            viewModel.toggleExpanded(pemberian)
            advanceUntilIdle()

            assertEquals(pemberian, viewModel.state.value.expandedId)
            val subcategories = viewModel.state.value.active.single().subcategories
            assertEquals(listOf("Arisan", "Rumah"), subcategories.map { it.name })
            assertEquals(3, subcategories.first().transactionCount)
            assertEquals(0, subcategories.last().transactionCount)
        }

    @Test
    fun `the income tab lists only income categories`() = runTest(dispatcher) {
        categories.createCategory("Pemberian", CategoryKind.EXPENSE)
        categories.createCategory("Gaji", CategoryKind.INCOME)

        val viewModel = manager()
        advanceUntilIdle()
        viewModel.selectKind(CategoryKind.INCOME)
        advanceUntilIdle()

        assertEquals(listOf("Gaji"), viewModel.state.value.active.map { it.name })
    }

    // Requirement: Create categories and subcategories

    @Test
    fun `creating a category stores it with the next colour slot and appends it to the order`() =
        runTest(dispatcher) {
            categories.createCategory("Pemberian", CategoryKind.EXPENSE)
            categories.createCategory("Makan & Minum", CategoryKind.EXPENSE)

            val viewModel = manager()
            advanceUntilIdle()
            viewModel.promptCreateCategory()
            viewModel.submitName("Asuransi")
            advanceUntilIdle()

            val created = categories.allCategories().single { it.name == "Asuransi" }
            assertEquals(CategoryKind.EXPENSE, created.kind)
            assertEquals(ColorSlot(2), created.colorSlot)
            assertEquals(2, created.sortOrder)
            assertNull(viewModel.state.value.dialog)
            // Available in every expense picker, which reads the same active-by-kind query.
            val offered = categories.observeActiveByKind(CategoryKind.EXPENSE).first()
            assertTrue(offered.any { it.name == "Asuransi" })
            assertEquals("Asuransi", viewModel.state.value.active.last().name)
        }

    @Test
    fun `a category created on the income tab is an income category`() = runTest(dispatcher) {
        val viewModel = manager()
        advanceUntilIdle()
        viewModel.selectKind(CategoryKind.INCOME)
        advanceUntilIdle()
        viewModel.promptCreateCategory()
        viewModel.submitName("Bonus")
        advanceUntilIdle()

        assertEquals(CategoryKind.INCOME, categories.allCategories().single().kind)
    }

    @Test
    fun `a blank category name is refused with an inline message`() = runTest(dispatcher) {
        val viewModel = manager()
        advanceUntilIdle()
        viewModel.promptCreateCategory()
        viewModel.submitName("   ")
        advanceUntilIdle()

        val dialog = viewModel.state.value.dialog
        assertTrue(dialog is CreateCategoryPrompt)
        assertEquals(NameError.Blank, (dialog as NamePrompt).error)
        assertTrue(categories.allCategories().isEmpty())
    }

    @Test
    fun `a blank subcategory name is refused with an inline message`() = runTest(dispatcher) {
        val pemberian = categories.createCategory("Pemberian", CategoryKind.EXPENSE)

        val viewModel = manager()
        advanceUntilIdle()
        viewModel.promptCreateSubcategory(pemberian)
        viewModel.submitName("")
        advanceUntilIdle()

        assertEquals(NameError.Blank, (viewModel.state.value.dialog as NamePrompt).error)
        assertTrue(categories.allSubcategories().isEmpty())
    }

    @Test
    fun `a duplicate subcategory in one category is refused, the same name elsewhere is not`() =
        runTest(dispatcher) {
            val pemberian = categories.createCategory("Pemberian", CategoryKind.EXPENSE)
            val makan = categories.createCategory("Makan & Minum", CategoryKind.EXPENSE)
            categories.createSubcategory(pemberian, "Arisan")

            val viewModel = manager()
            advanceUntilIdle()
            viewModel.promptCreateSubcategory(pemberian)
            viewModel.submitName("  arisan  ")
            advanceUntilIdle()

            // Case- and whitespace-insensitive, and refused rather than folded into the
            // existing row: on this screen the user is editing the list, not filing a
            // transaction, so a silent merge would lose the name they just typed.
            val error = (viewModel.state.value.dialog as NamePrompt).error
            assertEquals(NameError.Duplicate("Arisan"), error)
            assertEquals(1, categories.allSubcategories().size)

            viewModel.dismissDialog()
            viewModel.promptCreateSubcategory(makan)
            viewModel.submitName("Arisan")
            advanceUntilIdle()

            assertNull(viewModel.state.value.dialog)
            assertEquals(2, categories.allSubcategories().size)
        }

    @Test
    fun `a duplicate category name is refused`() = runTest(dispatcher) {
        categories.createCategory("Pemberian", CategoryKind.EXPENSE)

        val viewModel = manager()
        advanceUntilIdle()
        viewModel.promptCreateCategory()
        viewModel.submitName("pemberian")
        advanceUntilIdle()

        assertEquals(
            NameError.Duplicate("Pemberian"),
            (viewModel.state.value.dialog as NamePrompt).error,
        )
        assertEquals(1, categories.allCategories().size)
    }

    // Requirement: Rename, recolour and reorder

    @Test
    fun `renaming a category in use keeps all 443 transactions pointing at it`() =
        runTest(dispatcher) {
            val pemberian = categories.createCategory("Pemberian", CategoryKind.EXPENSE)
            spend(pemberian, times = 443)

            val viewModel = manager()
            advanceUntilIdle()
            viewModel.promptRenameCategory(pemberian)
            viewModel.submitName("Keluarga")
            advanceUntilIdle()

            assertEquals("Keluarga", categories.byId(pemberian)?.name)
            assertEquals(443, categories.transactionCount(pemberian))
            assertTrue(transactions.all().all { it.categoryId == pemberian })
            assertEquals(443, viewModel.state.value.active.single().transactionCount)
        }

    @Test
    fun `renaming a subcategory keeps its transactions`() = runTest(dispatcher) {
        val pemberian = categories.createCategory("Pemberian", CategoryKind.EXPENSE)
        val arisan = categories.createSubcategory(pemberian, "Arisan")
        spend(pemberian, arisan, times = 5)

        val viewModel = manager()
        advanceUntilIdle()
        viewModel.toggleExpanded(pemberian)
        advanceUntilIdle()
        viewModel.promptRenameSubcategory(arisan)
        viewModel.submitName("Ibu")
        advanceUntilIdle()

        assertEquals("Ibu", categories.subcategoryById(arisan)?.name)
        assertEquals(5, transactions.all().count { it.subcategoryId == arisan })
    }

    @Test
    fun `changing a colour slot moves that category only`() = runTest(dispatcher) {
        val pemberian = categories.createCategory("Pemberian", CategoryKind.EXPENSE)
        val makan = categories.createCategory("Makan & Minum", CategoryKind.EXPENSE)
        val before = categories.byId(makan)?.colorSlot

        val viewModel = manager()
        advanceUntilIdle()
        viewModel.promptRecolor(pemberian)
        viewModel.recolor(ColorSlot(5))
        advanceUntilIdle()

        assertEquals(ColorSlot(5), categories.byId(pemberian)?.colorSlot)
        assertEquals(before, categories.byId(makan)?.colorSlot)
        assertNull(viewModel.state.value.dialog)
    }

    @Test
    fun `subcategories follow the parent's colour because they carry none`() =
        runTest(dispatcher) {
            val pemberian = categories.createCategory("Pemberian", CategoryKind.EXPENSE)
            categories.createSubcategory(pemberian, "Arisan")

            val viewModel = manager()
            advanceUntilIdle()
            viewModel.promptRecolor(pemberian)
            viewModel.recolor(ColorSlot(6))
            advanceUntilIdle()

            val row = viewModel.state.value.active.single()
            // One slot for the whole group: the screen paints each child's swatch from
            // the row above it, so there is no second value that could go stale.
            assertEquals(ColorSlot(6), row.colorSlot)
            assertEquals(1, row.subcategories.size)
        }

    @Test
    fun `moving a category down persists the new sort order`() = runTest(dispatcher) {
        val a = categories.createCategory("A", CategoryKind.EXPENSE)
        val b = categories.createCategory("B", CategoryKind.EXPENSE)
        val c = categories.createCategory("C", CategoryKind.EXPENSE)

        val viewModel = manager()
        advanceUntilIdle()
        viewModel.moveCategory(a, 1)
        advanceUntilIdle()

        assertEquals(listOf("B", "A", "C"), viewModel.state.value.active.map { it.name })
        val stored = categories.allCategories().sortedBy { it.sortOrder }
        assertEquals(listOf(b, a, c), stored.map { it.id })
    }

    @Test
    fun `a drag reorders on the drop, not on every row it crosses`() = runTest(dispatcher) {
        val a = categories.createCategory("A", CategoryKind.EXPENSE)
        categories.createCategory("B", CategoryKind.EXPENSE)
        val c = categories.createCategory("C", CategoryKind.EXPENSE)

        val viewModel = manager()
        advanceUntilIdle()
        viewModel.startReorder()
        viewModel.moveCategoryOver(a, c)
        advanceUntilIdle()

        assertEquals(listOf("B", "C", "A"), viewModel.state.value.active.map { it.name })
        // Still untouched on disk: the drag has not been dropped yet.
        assertEquals(
            listOf("A", "B", "C"),
            categories.allCategories().sortedBy { it.sortOrder }.map { it.name },
        )

        viewModel.commitReorder()
        advanceUntilIdle()

        assertEquals(
            listOf("B", "C", "A"),
            categories.allCategories().sortedBy { it.sortOrder }.map { it.name },
        )
    }

    // Requirement: Archive rather than delete where history exists

    @Test
    fun `archiving a category in use hides it from pickers and keeps its 13 transactions`() =
        runTest(dispatcher) {
            val pemberian = categories.createCategory("Pemberian", CategoryKind.EXPENSE)
            categories.createCategory("Makan & Minum", CategoryKind.EXPENSE)
            spend(pemberian, times = 13)

            val viewModel = manager()
            advanceUntilIdle()
            viewModel.setCategoryArchived(pemberian, archived = true)
            advanceUntilIdle()

            val offered = categories.observeActiveByKind(CategoryKind.EXPENSE).first()
            assertTrue(offered.none { it.id == pemberian })
            assertEquals(listOf("Makan & Minum"), viewModel.state.value.active.map { it.name })
            assertEquals(listOf("Pemberian"), viewModel.state.value.archived.map { it.name })
            assertEquals(13, categories.transactionCount(pemberian))
            assertEquals(13, transactions.all().count { it.categoryId == pemberian })
        }

    @Test
    fun `delete is not offered for a category in use`() = runTest(dispatcher) {
        val pemberian = categories.createCategory("Pemberian", CategoryKind.EXPENSE)
        spend(pemberian, times = 1)

        val viewModel = manager()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.active.single().deletable)

        viewModel.promptDeleteCategory(pemberian)
        advanceUntilIdle()

        // Nothing to confirm: the screen refuses to open the dialog at all.
        assertNull(viewModel.state.value.dialog)
        assertNotNull(categories.byId(pemberian))
    }

    @Test
    fun `deleting an unused category removes it and its subcategories`() = runTest(dispatcher) {
        val asuransi = categories.createCategory("Asuransi", CategoryKind.EXPENSE)
        categories.createSubcategory(asuransi, "Jiwa")
        categories.createSubcategory(asuransi, "Kesehatan")

        val viewModel = manager()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.active.single().deletable)

        viewModel.promptDeleteCategory(asuransi)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.dialog is ConfirmDeletePrompt)

        viewModel.confirmDelete()
        advanceUntilIdle()

        assertNull(categories.byId(asuransi))
        assertTrue(categories.allSubcategories().isEmpty())
        assertTrue(viewModel.state.value.isEmpty)
    }

    @Test
    fun `unarchiving returns a category to the pickers with its colour and position`() =
        runTest(dispatcher) {
            val a = categories.createCategory("A", CategoryKind.EXPENSE)
            categories.createCategory("B", CategoryKind.EXPENSE)
            val c = categories.createCategory("C", CategoryKind.EXPENSE)
            val d = categories.createCategory("D", CategoryKind.EXPENSE)
            val slotBefore = categories.byId(c)?.colorSlot

            val viewModel = manager()
            advanceUntilIdle()
            viewModel.setCategoryArchived(c, archived = true)
            advanceUntilIdle()

            // Reorder what is left. An archived row holds its place through this: it is
            // off screen, so the visible order must not renumber over the top of it.
            viewModel.moveCategory(d, -2)
            advanceUntilIdle()
            assertEquals(listOf("D", "A", "B"), viewModel.state.value.active.map { it.name })

            viewModel.setCategoryArchived(c, archived = false)
            advanceUntilIdle()

            assertEquals(listOf("D", "A", "C", "B"), viewModel.state.value.active.map { it.name })
            assertEquals(2, viewModel.state.value.active.indexOfFirst { it.id == c })
            assertEquals(slotBefore, categories.byId(c)?.colorSlot)
            val offered = categories.observeActiveByKind(CategoryKind.EXPENSE).first()
            assertTrue(offered.any { it.id == c })
            assertEquals(a, viewModel.state.value.active[1].id)
        }

    @Test
    fun `archiving a subcategory hides it from pickers and keeps its transactions`() =
        runTest(dispatcher) {
            val pemberian = categories.createCategory("Pemberian", CategoryKind.EXPENSE)
            val arisan = categories.createSubcategory(pemberian, "Arisan")
            spend(pemberian, arisan, times = 4)

            val viewModel = manager()
            advanceUntilIdle()
            viewModel.setSubcategoryArchived(arisan, archived = true)
            advanceUntilIdle()

            assertTrue(categories.observeSubcategories(pemberian).first().isEmpty())
            assertEquals(4, transactions.all().count { it.subcategoryId == arisan })
            assertEquals(0, viewModel.state.value.active.single().subcategoryCount)
        }

    @Test
    fun `delete is not offered for a subcategory in use`() = runTest(dispatcher) {
        val pemberian = categories.createCategory("Pemberian", CategoryKind.EXPENSE)
        val arisan = categories.createSubcategory(pemberian, "Arisan")
        spend(pemberian, arisan, times = 2)

        val viewModel = manager()
        advanceUntilIdle()
        viewModel.toggleExpanded(pemberian)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.active.single().subcategories.single().deletable)

        viewModel.promptDeleteSubcategory(arisan)
        advanceUntilIdle()

        assertNull(viewModel.state.value.dialog)
        assertNotNull(categories.subcategoryById(arisan))
    }

    // Requirement: Merge one category into another

    @Test
    fun `a merge states the count, moves every transaction and archives the source`() =
        runTest(dispatcher) {
            val jajan = categories.createCategory("Jajan & Hiburan", CategoryKind.EXPENSE)
            val makan = categories.createCategory("Makan & Minum", CategoryKind.EXPENSE)
            spend(jajan, times = 27)
            spend(makan, times = 5)
            val totalBefore = transactions.all().size

            val viewModel = manager()
            advanceUntilIdle()
            viewModel.promptMerge(jajan)
            advanceUntilIdle()
            viewModel.chooseMergeTarget(makan)
            advanceUntilIdle()

            val confirmation = viewModel.state.value.dialog as ConfirmMergePrompt
            assertEquals(27, confirmation.affectedCount)
            assertEquals("Makan & Minum", confirmation.toName)

            viewModel.confirmMerge()
            advanceUntilIdle()

            // The three assertions that prove no money vanished: nothing was deleted,
            // every row that pointed at the source now points at the target, and the
            // source is archived rather than gone.
            val after = transactions.all()
            assertEquals(totalBefore, after.size)
            assertEquals(32, after.count { it.categoryId == makan })
            assertEquals(0, after.count { it.categoryId == jajan })
            assertTrue(after.all { it.categoryId != null })
            assertEquals(true, categories.byId(jajan)?.archived)
            assertEquals(false, categories.byId(makan)?.archived)
            assertEquals(32, viewModel.state.value.active.single().transactionCount)
        }

    @Test
    fun `a merge carries subcategories across and folds a name clash into the existing one`() =
        runTest(dispatcher) {
            val jajan = categories.createCategory("Jajan & Hiburan", CategoryKind.EXPENSE)
            val makan = categories.createCategory("Makan & Minum", CategoryKind.EXPENSE)
            val jajanWarung = categories.createSubcategory(jajan, "Warung")
            val jajanBioskop = categories.createSubcategory(jajan, "Bioskop")
            val makanWarung = categories.createSubcategory(makan, "Warung")
            spend(jajan, jajanWarung, times = 3)
            spend(jajan, jajanBioskop, times = 2)
            spend(makan, makanWarung, times = 1)
            val totalBefore = transactions.all().size

            val viewModel = manager()
            advanceUntilIdle()
            viewModel.promptMerge(jajan)
            advanceUntilIdle()
            viewModel.chooseMergeTarget(makan)
            advanceUntilIdle()
            viewModel.confirmMerge()
            advanceUntilIdle()

            val after = transactions.all()
            assertEquals(totalBefore, after.size)
            assertEquals(6, after.count { it.categoryId == makan })

            val survivors = categories.observeSubcategories(makan).first()
            assertEquals(listOf("Warung", "Bioskop"), survivors.map { it.name })
            // The clashing pair became one row, and the rows that pointed at the source's
            // "Warung" were moved onto the target's rather than left dangling.
            assertEquals(4, after.count { it.subcategoryId == makanWarung })
            assertEquals(0, after.count { it.subcategoryId == jajanWarung })
            assertEquals(2, after.count { it.subcategoryId == jajanBioskop })
            assertEquals(makan, categories.subcategoryById(jajanBioskop)?.categoryId)
        }

    @Test
    fun `only categories of the same kind are offered as merge targets`() = runTest(dispatcher) {
        val jajan = categories.createCategory("Jajan & Hiburan", CategoryKind.EXPENSE)
        val makan = categories.createCategory("Makan & Minum", CategoryKind.EXPENSE)
        categories.createCategory("Gaji", CategoryKind.INCOME)
        val archived = categories.createCategory("Lama", CategoryKind.EXPENSE)
        categories.setCategoryArchived(archived, true)

        val viewModel = manager()
        advanceUntilIdle()
        viewModel.promptMerge(jajan)
        advanceUntilIdle()

        val prompt = viewModel.state.value.dialog as ChooseMergeTargetPrompt
        // Same kind, still active, and never the source itself. Folding an expense
        // category into an income one would flip the sign of everything it moved.
        assertEquals(listOf(makan), prompt.targets.map { it.id })
    }

    @Test
    fun `merge is not offered when there is nothing to merge into`() = runTest(dispatcher) {
        val only = categories.createCategory("Pemberian", CategoryKind.EXPENSE)
        categories.createCategory("Gaji", CategoryKind.INCOME)

        val viewModel = manager()
        advanceUntilIdle()
        viewModel.promptMerge(only)
        advanceUntilIdle()

        assertNull(viewModel.state.value.dialog)
    }

    // Requirement: Seed the default Indonesian categories

    @Test
    fun `the sixteen seeded categories are ordinary editable records`() = runTest(dispatcher) {
        FirstRunSeed.applyIfEmpty(
            db = db,
            openingAsOf = LocalDateTime(2026, 8, 17, 9, 0).toInstant(zone),
            zone = zone,
        )

        val viewModel = manager()
        advanceUntilIdle()

        val names = viewModel.state.value.active.map { it.name }
        assertEquals(FirstRunSeed.DEFAULT_EXPENSE_CATEGORIES, names)
        assertEquals(16, names.size)
        assertTrue(viewModel.state.value.active.all { it.colorSlot.index in 0 until ColorSlot.COUNT })

        // Nothing about a seeded row is special-cased: it renames, recolours, archives.
        val pemberian = viewModel.state.value.active.first()
        assertEquals("Pemberian", pemberian.name)
        assertEquals(4, pemberian.subcategoryCount)

        viewModel.promptRenameCategory(pemberian.id)
        viewModel.submitName("Pemberian Keluarga")
        advanceUntilIdle()
        assertEquals("Pemberian Keluarga", categories.byId(pemberian.id)?.name)

        viewModel.setCategoryArchived(pemberian.id, archived = true)
        advanceUntilIdle()
        assertEquals(15, viewModel.state.value.active.size)
        assertEquals(1, viewModel.state.value.archived.size)
    }

    @Test
    fun `creating a subcategory under Pemberian opens the parent so the result is visible`() =
        runTest(dispatcher) {
            FirstRunSeed.applyIfEmpty(
                db = db,
                openingAsOf = LocalDateTime(2026, 8, 17, 9, 0).toInstant(zone),
                zone = zone,
            )

            val viewModel = manager()
            advanceUntilIdle()
            val pemberian = viewModel.state.value.active.first { it.name == "Pemberian" }

            viewModel.promptCreateSubcategory(pemberian.id)
            viewModel.submitName("Adik")
            advanceUntilIdle()

            assertEquals(pemberian.id, viewModel.state.value.expandedId)
            val row = viewModel.state.value.active.first { it.id == pemberian.id }
            assertTrue(row.subcategories.any { it.name == "Adik" })
            // The entry sheet reads the same query, so it can file against it immediately.
            val offered = categories.observeSubcategories(pemberian.id).first()
            assertTrue(offered.any { it.name == "Adik" })
        }
}
