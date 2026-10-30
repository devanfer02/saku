package com.dvnnfrr.saku.data.local

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.dvnnfrr.saku.excel.DatabaseAccountRow
import com.dvnnfrr.saku.excel.DatabaseCategoryRow
import com.dvnnfrr.saku.excel.DatabaseHeader
import com.dvnnfrr.saku.excel.DatabaseImport
import com.dvnnfrr.saku.excel.DatabaseReadOutcome
import com.dvnnfrr.saku.excel.DatabaseRows
import com.dvnnfrr.saku.excel.DatabaseSubcategoryRow
import com.dvnnfrr.saku.excel.DatabaseTxnRow
import java.io.File

/**
 * Reads a SQLite file in Saku's schema, read-only, for the database import.
 *
 * Only ever pointed at a copy in the cache directory, never at the app's own `saku.db`.
 * The handle is opened read-only, so nothing here can write to the file it reads; the
 * import writes through `ImportRepository.restore` like a backup restore does.
 *
 * `NO_LOCALIZED_COLLATORS` because a file built outside Android has no `android_metadata`
 * table, and without the flag Android tries to create one, which a read-only handle
 * refuses. A file that is not SQLite at all throws `SQLiteException`; the caller reports
 * it as unreadable.
 */
object DatabaseFileReader {

    fun read(file: File): DatabaseReadOutcome {
        val flags = SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        return SQLiteDatabase.openDatabase(file.path, null, flags).use { db ->
            val header = DatabaseHeader(userVersion = db.version, identityHash = identityHash(db))
            DatabaseImport.refusalFor(header, SakuDatabase.VERSION, SakuDatabase.IDENTITY_HASH)
                ?.let { return@use DatabaseReadOutcome.Refused(it) }
            DatabaseImport.toBackup(rows(db), header.userVersion)
        }
    }

    private fun identityHash(db: SQLiteDatabase): String? {
        val hasMasterTable = db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'room_master_table'",
            null,
        ).use { it.moveToFirst() }
        if (!hasMasterTable) return null
        return db.rawQuery("SELECT identity_hash FROM room_master_table WHERE id = 42", null)
            .use { if (it.moveToFirst()) it.getStringOrNull(0) else null }
    }

    // Named columns, never `*`: a file that claims the right hash but lacks a column fails
    // here as unreadable instead of being read by position.
    private fun rows(db: SQLiteDatabase) = DatabaseRows(
        accounts = db.all(
            "SELECT id, name, type, openingBalance, openingAsOfEpochSec, colorSlot, sortOrder, " +
                "archived, lastReconciledEpochSec FROM account ORDER BY id",
        ) {
            DatabaseAccountRow(
                id = getLong(0),
                name = getString(1),
                type = getString(2),
                openingBalance = getLong(3),
                openingAsOfEpochSec = getLong(4),
                colorSlot = getInt(5),
                sortOrder = getInt(6),
                archived = getInt(7) != 0,
                lastReconciledEpochSec = getLongOrNull(8),
            )
        },
        categories = db.all("SELECT id, name, kind, colorSlot, sortOrder, archived FROM category ORDER BY id") {
            DatabaseCategoryRow(
                id = getLong(0),
                name = getString(1),
                kind = getString(2),
                colorSlot = getInt(3),
                sortOrder = getInt(4),
                archived = getInt(5) != 0,
            )
        },
        subcategories = db.all("SELECT id, categoryId, name, sortOrder, archived FROM subcategory ORDER BY id") {
            DatabaseSubcategoryRow(
                id = getLong(0),
                categoryId = getLong(1),
                name = getString(2),
                sortOrder = getInt(3),
                archived = getInt(4) != 0,
            )
        },
        transactions = db.all(
            "SELECT id, occurredAtEpochSec, type, amountIdr, accountId, toAccountId, categoryId, " +
                "subcategoryId, note FROM txn ORDER BY occurredAtEpochSec, id",
        ) {
            DatabaseTxnRow(
                id = getLong(0),
                occurredAtEpochSec = getLong(1),
                type = getString(2),
                amountIdr = getLong(3),
                accountId = getLong(4),
                toAccountId = getLongOrNull(5),
                categoryId = getLongOrNull(6),
                subcategoryId = getLongOrNull(7),
                note = getStringOrNull(8),
            )
        },
    )

    private fun <T> SQLiteDatabase.all(sql: String, row: Cursor.() -> T): List<T> =
        rawQuery(sql, null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.row()) }
        }

    private fun Cursor.getLongOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)

    private fun Cursor.getStringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)
}
