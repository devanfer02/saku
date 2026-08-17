package com.dvnnfrr.saku.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM category ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category ORDER BY sortOrder, id")
    suspend fun all(): List<CategoryEntity>

    @Query("SELECT * FROM category WHERE kind = :kind AND archived = 0 ORDER BY sortOrder, id")
    fun observeActiveByKind(kind: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun byId(id: Long): CategoryEntity?

    /** Duplicate detection is case- and whitespace-insensitive within a kind. */
    @Query(
        """
        SELECT * FROM category
        WHERE kind = :kind AND archived = 0
          AND LOWER(TRIM(name)) = LOWER(TRIM(:name))
        LIMIT 1
        """,
    )
    suspend fun findActiveByName(kind: String, name: String): CategoryEntity?

    /**
     * Includes archived rows, preferring a live one.
     *
     * Checking only active names let an import recreate an archived category, leaving
     * two rows with the same name — one holding the old history, one the imported
     * transactions — which the stats screen then renders as two identical entries.
     */
    @Query(
        """
        SELECT * FROM category
        WHERE kind = :kind AND LOWER(TRIM(name)) = LOWER(TRIM(:name))
        ORDER BY archived ASC
        LIMIT 1
        """,
    )
    suspend fun findAnyByName(kind: String, name: String): CategoryEntity?

    @Query("SELECT colorSlot FROM category")
    suspend fun allColorSlots(): List<Int>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM category WHERE kind = :kind")
    suspend fun nextSortOrder(kind: String): Int

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Update
    suspend fun updateAll(categories: List<CategoryEntity>)

    @Query("UPDATE category SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("UPDATE category SET colorSlot = :colorSlot WHERE id = :id")
    suspend fun setColorSlot(id: Long, colorSlot: Int)

    @Query("DELETE FROM category WHERE id = :id")
    suspend fun deleteUnused(id: Long)
}

@Dao
interface SubcategoryDao {

    @Query("SELECT * FROM subcategory WHERE archived = 0 ORDER BY sortOrder, id")
    fun observeActive(): Flow<List<SubcategoryEntity>>

    /**
     * Every subcategory, archived included.
     *
     * The transaction list renders a subcategory name per row and cannot ask
     * per category without an N+1. Archived rows are in scope because archiving a
     * subcategory does not rewrite the transactions that already reference it —
     * filtering them here blanks the label on real history.
     */
    @Query("SELECT * FROM subcategory ORDER BY categoryId, sortOrder, id")
    fun observeAll(): Flow<List<SubcategoryEntity>>

    @Query(
        "SELECT * FROM subcategory WHERE categoryId = :categoryId AND archived = 0 " +
            "ORDER BY sortOrder, id",
    )
    fun observeActiveForCategory(categoryId: Long): Flow<List<SubcategoryEntity>>

    /** Archived rows included, so the manage screen can offer unarchive. */
    @Query("SELECT * FROM subcategory WHERE categoryId = :categoryId ORDER BY sortOrder, id")
    fun observeAllForCategory(categoryId: Long): Flow<List<SubcategoryEntity>>

    @Update
    suspend fun updateAll(subcategories: List<SubcategoryEntity>)

    @Query("SELECT * FROM subcategory WHERE categoryId = :categoryId AND archived = 0")
    suspend fun activeForCategory(categoryId: Long): List<SubcategoryEntity>

    @Query("SELECT * FROM subcategory WHERE id = :id")
    suspend fun byId(id: Long): SubcategoryEntity?

    @Query(
        """
        SELECT * FROM subcategory
        WHERE categoryId = :categoryId AND archived = 0
          AND LOWER(TRIM(name)) = LOWER(TRIM(:name))
        LIMIT 1
        """,
    )
    suspend fun findActiveByName(categoryId: Long, name: String): SubcategoryEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM subcategory WHERE categoryId = :categoryId")
    suspend fun nextSortOrder(categoryId: Long): Int

    @Query("SELECT * FROM subcategory WHERE categoryId = :categoryId")
    suspend fun forCategory(categoryId: Long): List<SubcategoryEntity>

    @Insert
    suspend fun insert(subcategory: SubcategoryEntity): Long

    @Update
    suspend fun update(subcategory: SubcategoryEntity)

    @Query("UPDATE subcategory SET categoryId = :toCategoryId WHERE id = :id")
    suspend fun moveToCategory(id: Long, toCategoryId: Long)

    @Query("UPDATE subcategory SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("DELETE FROM subcategory WHERE id = :id")
    suspend fun deleteUnused(id: Long)
}

@Dao
interface ImportBatchDao {

    @Query("SELECT * FROM import_batch ORDER BY importedAtEpochSec DESC")
    fun observeAll(): Flow<List<ImportBatchEntity>>

    @Query("SELECT * FROM import_batch WHERE id = :id")
    suspend fun byId(id: Long): ImportBatchEntity?

    @Insert
    suspend fun insert(batch: ImportBatchEntity): Long

    /**
     * Reversal marks the batch rather than deleting it. The spec requires import
     * history to list every past import; deleting the record hid the fact that an
     * import happened at all.
     */
    @Query("UPDATE import_batch SET reversedAtEpochSec = :at WHERE id = :id")
    suspend fun markReversed(id: Long, at: Long)

    /**
     * Claims the batch for reversal, returning the number of rows it changed — zero
     * if it was already reversed. Doing this before the delete makes a double tap a
     * no-op rather than a second reversal reporting nothing removed.
     */
    @Query(
        "UPDATE import_batch SET reversedAtEpochSec = :at " +
            "WHERE id = :id AND reversedAtEpochSec IS NULL",
    )
    suspend fun markReversedIfNot(id: Long, at: Long): Int
}
