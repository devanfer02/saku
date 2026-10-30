package com.dvnnfrr.saku.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.dvnnfrr.saku.data.local.SakuDatabase
import org.json.JSONObject
import java.io.File

/**
 * Builds a SQLite file the way the owner's script does: every table and index from Room's
 * exported schema JSON, the `room_master_table` setup queries, and `PRAGMA user_version`.
 * No Room and no `android_metadata`, so the file is what an outside tool would write.
 *
 * Needs Robolectric for SQLite and a real `org.json`.
 */
class SchemaDatabaseFile(private val file: File) {

    private val db: SQLiteDatabase = SQLiteDatabase.openDatabase(
        file.path,
        null,
        SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
    )

    /**
     * [userVersion] and [identityHash] default to the schema's own; [withMasterTable] false
     * leaves `room_master_table` out, as a file Room never wrote would.
     */
    fun createSchema(
        userVersion: Int = SakuDatabase.VERSION,
        identityHash: String? = null,
        withMasterTable: Boolean = true,
    ): SchemaDatabaseFile {
        val database = JSONObject(schemaJson().readText()).getJSONObject("database")
        val entities = database.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        if (withMasterTable) {
            val setup = database.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) {
                val query = setup.getString(i)
                db.execSQL(identityHash?.let { query.replace(database.getString("identityHash"), it) } ?: query)
            }
        }
        db.version = userVersion
        return this
    }

    fun account(
        id: Long,
        name: String,
        type: String = "BANK",
        openingBalance: Long = 0,
        openingAsOfEpochSec: Long = 0,
        colorSlot: Int = 0,
        archived: Boolean = false,
        lastReconciledEpochSec: Long? = null,
    ) = insert(
        "account",
        "id" to id, "name" to name, "type" to type, "openingBalance" to openingBalance,
        "openingAsOfEpochSec" to openingAsOfEpochSec, "colorSlot" to colorSlot, "sortOrder" to id,
        "archived" to archived, "lastReconciledEpochSec" to lastReconciledEpochSec,
    )

    fun category(id: Long, name: String, kind: String = "EXPENSE", colorSlot: Int = 0, archived: Boolean = false) =
        insert(
            "category",
            "id" to id, "name" to name, "kind" to kind, "colorSlot" to colorSlot,
            "sortOrder" to id, "archived" to archived,
        )

    fun subcategory(id: Long, categoryId: Long, name: String) = insert(
        "subcategory",
        "id" to id, "categoryId" to categoryId, "name" to name, "sortOrder" to id, "archived" to false,
    )

    fun txn(
        id: Long,
        occurredAtEpochSec: Long,
        type: String,
        amountIdr: Long,
        accountId: Long,
        toAccountId: Long? = null,
        categoryId: Long? = null,
        subcategoryId: Long? = null,
        note: String? = null,
    ) = insert(
        "txn",
        "id" to id, "occurredAtEpochSec" to occurredAtEpochSec, "type" to type, "amountIdr" to amountIdr,
        "accountId" to accountId, "toAccountId" to toAccountId, "categoryId" to categoryId,
        "subcategoryId" to subcategoryId, "note" to note, "createdAtEpochSec" to occurredAtEpochSec,
        "importBatchId" to null, "dedupHash" to "script-$id",
    )

    fun close(): File {
        db.close()
        return file
    }

    private fun insert(table: String, vararg values: Pair<String, Any?>): SchemaDatabaseFile {
        val row = ContentValues()
        values.forEach { (column, value) ->
            when (value) {
                null -> row.putNull(column)
                is Long -> row.put(column, value)
                is Int -> row.put(column, value)
                is Boolean -> row.put(column, if (value) 1 else 0)
                is String -> row.put(column, value)
                else -> error("Unsupported value for $column: $value")
            }
        }
        db.insertOrThrow(table, null, row)
        return this
    }
}

/** 2026-01-05T03:00:00Z, the first transaction in [sampleDatabaseFile]. */
const val SAMPLE_FIRST_EPOCH_SEC = 1_767_582_000L

/** A day, for spacing the sample's transactions. */
const val DAY_SEC = 86_400L

/**
 * A small file shaped like the owner's: opening balances, an archived expense and an
 * archived income category, a transfer, a negative adjustment and no subcategories.
 */
fun sampleDatabaseFile(file: File): File = SchemaDatabaseFile(file).createSchema()
    .account(1, "Cash", type = "CASH", openingBalance = 150_000, colorSlot = 0)
    .account(2, "BCA", type = "BANK", openingBalance = 2_500_000, colorSlot = 1)
    .account(3, "RDPU SucorInvest", type = "INVESTMENT", colorSlot = 3, lastReconciledEpochSec = 1_780_000_000)
    .category(1, "Makan & Minum", kind = "EXPENSE", colorSlot = 2)
    .category(2, "Gaji", kind = "INCOME", colorSlot = 4)
    .category(3, "Kos lama", kind = "EXPENSE", archived = true)
    .category(4, "Hadiah", kind = "INCOME", archived = true)
    .txn(1, SAMPLE_FIRST_EPOCH_SEC, "EXPENSE", 50_000, accountId = 1, categoryId = 1, note = "nasi padang")
    .txn(2, SAMPLE_FIRST_EPOCH_SEC + DAY_SEC, "INCOME", 7_500_000, accountId = 2, categoryId = 2)
    .txn(3, SAMPLE_FIRST_EPOCH_SEC + 2 * DAY_SEC, "TRANSFER", 1_000_000, accountId = 2, toAccountId = 3)
    .txn(4, SAMPLE_FIRST_EPOCH_SEC + 3 * DAY_SEC, "ADJUSTMENT", -25_000, accountId = 1)
    .txn(5, SAMPLE_FIRST_EPOCH_SEC + 40 * DAY_SEC, "EXPENSE", 12_000, accountId = 2, categoryId = 3, note = "kos")
    .close()
