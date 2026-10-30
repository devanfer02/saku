package com.dvnnfrr.saku.ui.importexport

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dvnnfrr.saku.data.local.ImportBatchEntity
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.data.repository.RoomImportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The reverse-import confirmation lives in the view model, which outlives the dialog. Each
 * way out must clear it, or the dialog would come back on its own the next time the
 * screen draws, as the entry sheet's stale `saved` flag once closed a sheet on open.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportHistoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-10-30T03:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }

    private lateinit var db: SakuDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SakuDatabase::class.java)
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun batch(name: String): Long = db.importBatchDao().insert(
        ImportBatchEntity(fileName = name, importedAtEpochSec = 9_000, rowCount = 0, insertedCount = 0, skippedCount = 0),
    )

    private fun viewModel() = ImportHistoryViewModel(RoomImportRepository(db), clock)

    @Test
    fun `confirming a reversal closes the dialog, and the next ask opens it for that batch`() = runTest(dispatcher) {
        val first = batch("mm-august.xlsx")
        val second = batch("mm-september.xlsx")
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        val batches = vm.state.value.batches

        vm.askToReverse(batches.first { it.id == first })
        vm.confirmReversal()
        advanceUntilIdle()
        assertNull(vm.state.value.confirming)

        vm.askToReverse(batches.first { it.id == second })
        advanceUntilIdle()
        assertEquals(second, vm.state.value.confirming?.id)
    }

    @Test
    fun `dismissing closes the dialog without reversing`() = runTest(dispatcher) {
        val id = batch("mm-august.xlsx")
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.askToReverse(vm.state.value.batches.single())
        vm.dismissConfirmation()
        advanceUntilIdle()

        assertNull(vm.state.value.confirming)
        assertNull(vm.state.value.batches.single { it.id == id }.reversedAt)
    }
}
