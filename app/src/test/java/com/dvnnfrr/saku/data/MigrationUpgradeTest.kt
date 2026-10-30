package com.dvnnfrr.saku.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dvnnfrr.saku.data.local.ALL_MIGRATIONS
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.data.repository.RoomTransactionRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * A real version 1 file, built from the exported 1.json, opened by the version 2 app.
 * Room validates the migrated schema against 2.json on open, so a migration that left
 * anything out throws here. The androidTest `MigrationTest` runs the same on a device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationUpgradeTest {

    private lateinit var file: File
    private var db: SakuDatabase? = null

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        file = context.getDatabasePath("upgrade-test.db").apply { parentFile?.mkdirs() }
        sampleDatabaseFile(file, schemaVersion = 1)
    }

    @After
    fun tearDown() {
        db?.close()
    }

    @Test
    fun `upgrading keeps every row and tags none`() = runTest {
        val opened = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SakuDatabase::class.java,
            file.path,
        ).addMigrations(*ALL_MIGRATIONS).allowMainThreadQueries().build()
        db = opened

        val rows = RoomTransactionRepository(opened).all()

        assertEquals(5, rows.size)
        assertEquals(listOf(50_000L, 7_500_000L, 1_000_000L, -25_000L, 12_000L), rows.map { it.amount.rupiah })
        assertEquals(listOf("nasi padang", null, null, null, "kos"), rows.map { it.note })
        assertTrue(rows.all { it.tagIds.isEmpty() })
        assertEquals(3, opened.accountDao().all().size)
        assertEquals(4, opened.categoryDao().all().size)
        assertEquals(emptyList<Any>(), opened.tagDao().all())
        assertEquals(SakuDatabase.VERSION, opened.openHelper.readableDatabase.version)
    }
}
