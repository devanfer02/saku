package com.dvnnfrr.saku.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dvnnfrr.saku.data.local.ALL_MIGRATIONS
import com.dvnnfrr.saku.data.local.MIGRATION_1_2
import com.dvnnfrr.saku.data.local.SakuDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Schema 1 to 2 on a device: a version 1 file with rows in every table, migrated by
 * [MIGRATION_1_2] and validated by Room against 2.json, then opened by the app's own
 * builder setup. Every row must still be there, unchanged, with no tags and no learned words.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val name = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SakuDatabase::class.java,
    )

    @Test
    fun migrate1To2KeepsEveryRow() {
        helper.createDatabase(name, 1).apply {
            execSQL(
                "INSERT INTO account (id, name, type, openingBalance, openingAsOfEpochSec, colorSlot, sortOrder, archived) " +
                    "VALUES (1, 'Cash', 'CASH', 150000, 0, 0, 0, 0), (2, 'BCA', 'BANK', 2500000, 0, 1, 1, 0)",
            )
            execSQL(
                "INSERT INTO category (id, name, kind, colorSlot, sortOrder, archived) " +
                    "VALUES (1, 'Makan & Minum', 'EXPENSE', 2, 0, 0), (2, 'Gaji', 'INCOME', 4, 1, 0)",
            )
            execSQL("INSERT INTO subcategory (id, categoryId, name, sortOrder, archived) VALUES (1, 1, 'Sarapan', 0, 1)")
            for (i in 1..2000) {
                execSQL(
                    "INSERT INTO txn (id, occurredAtEpochSec, type, amountIdr, accountId, categoryId, subcategoryId, note, " +
                        "createdAtEpochSec, dedupHash) VALUES ($i, ${1_767_582_000L + i * 3600}, 'EXPENSE', ${i * 1000}, " +
                        "${1 + i % 2}, 1, ${if (i == 1) "1" else "NULL"}, 'row $i', 0, 'h$i')",
                )
            }
            close()
        }

        helper.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2).close()

        val db = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            SakuDatabase::class.java,
            name,
        ).addMigrations(*ALL_MIGRATIONS).build()
        try {
            runBlocking {
                val rows = db.txnDao().all()
                assertEquals(2000, rows.size)
                assertEquals((1..2000).sumOf { it * 1000L }, rows.sumOf { it.amountIdr })
                assertEquals("row 1", rows.first().note)
                assertEquals(1L, rows.first().subcategoryId)
                assertEquals(2, db.accountDao().all().size)
                assertEquals(2, db.categoryDao().all().size)
                assertEquals(1, db.subcategoryDao().all().size)
                assertEquals(0, db.tagDao().all().size)
                assertEquals(0, db.tagDao().allLinks().size)
                assertEquals(0, db.itemWordDao().all().size)
            }
        } finally {
            db.close()
            InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(name)
        }
    }
}
