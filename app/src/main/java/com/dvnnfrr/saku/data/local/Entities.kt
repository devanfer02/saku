package com.dvnnfrr.saku.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.TxnType

@Entity(
    tableName = "account",
    indices = [Index(value = ["archived"])],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    /** Balance as of [openingAsOfEpochSec]; transactions before that date never move it. */
    val openingBalance: Long,
    val openingAsOfEpochSec: Long,
    val colorSlot: Int,
    val sortOrder: Int,
    val archived: Boolean = false,
    /** Null until the account has been reconciled once. Drives the staleness flag. */
    val lastReconciledEpochSec: Long? = null,
)

@Entity(
    tableName = "category",
    indices = [Index(value = ["kind", "archived"])],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: CategoryKind,
    val colorSlot: Int,
    val sortOrder: Int,
    val archived: Boolean = false,
)

@Entity(
    tableName = "subcategory",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            // Categories holding transactions are archived rather than deleted, so a
            // cascade here only ever fires for an unused category.
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["categoryId", "archived"])],
)
data class SubcategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val name: String,
    val sortOrder: Int,
    val archived: Boolean = false,
)

@Entity(
    tableName = "txn",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["toAccountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = SubcategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["subcategoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["occurredAtEpochSec"]),
        Index(value = ["accountId"]),
        Index(value = ["toAccountId"]),
        Index(value = ["categoryId"]),
        Index(value = ["subcategoryId"]),
        Index(value = ["importBatchId"]),
        // NOT unique. Two legitimate transactions can share a dedup hash — the same
        // fare, the same minute, the same account — and a unique index turns that
        // into a crash on save and a silently dropped row on import. Deduplication
        // belongs to the importer, which filters inside one transaction.
        Index(value = ["dedupHash"]),
    ],
)
data class TxnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val occurredAtEpochSec: Long,
    val type: TxnType,
    @ColumnInfo(name = "amountIdr") val amountIdr: Long,
    val accountId: Long,
    /** Set only for [TxnType.TRANSFER]; a transfer is one row with two accounts. */
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val subcategoryId: Long? = null,
    val note: String? = null,
    val createdAtEpochSec: Long,
    val importBatchId: Long? = null,
    val dedupHash: String,
)

@Entity(tableName = "import_batch")
data class ImportBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val importedAtEpochSec: Long,
    /** Rows in the file, including any the user excluded before confirming. */
    val rowCount: Int,
    val insertedCount: Int,
    val skippedCount: Int,
    /** Set when the batch is reversed; the record itself is never deleted. */
    val reversedAtEpochSec: Long? = null,
)
