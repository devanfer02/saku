package com.dvnnfrr.saku.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Every aggregate here excludes TRANSFER and ADJUSTMENT. Moving money between the
 * owner's own accounts is not spending, and an adjustment corrects a balance rather
 * than recording a flow, so neither belongs in any income or expense figure.
 */
@Dao
interface TxnDao {

    @Query(
        """
        SELECT * FROM txn
        WHERE occurredAtEpochSec >= :startSec AND occurredAtEpochSec < :endSec
        ORDER BY occurredAtEpochSec DESC, id DESC
        """,
    )
    fun observeInRange(startSec: Long, endSec: Long): Flow<List<TxnEntity>>

    @Query(
        """
        SELECT * FROM txn
        WHERE occurredAtEpochSec >= :startSec AND occurredAtEpochSec < :endSec
          AND categoryId = :categoryId
          AND type = :type
        ORDER BY occurredAtEpochSec DESC, id DESC
        """,
    )
    fun observeInRangeForCategory(
        startSec: Long,
        endSec: Long,
        categoryId: Long,
        type: String,
    ): Flow<List<TxnEntity>>

    @Query(
        """
        SELECT
          COALESCE(SUM(CASE WHEN type = 'INCOME'  THEN amountIdr ELSE 0 END), 0) AS income,
          COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amountIdr ELSE 0 END), 0) AS expense
        FROM txn
        WHERE occurredAtEpochSec >= :startSec AND occurredAtEpochSec < :endSec
        """,
    )
    fun observePeriodTotals(startSec: Long, endSec: Long): Flow<PeriodTotalsRow>

    /**
     * Per-day subtotals. Days are bucketed in the caller's zone by passing the zone
     * offset in seconds, so a transaction just before midnight lands on the right day.
     *
     * The `type IN` filter is in the WHERE, not only inside the SUMs. Without it a
     * transfer-only or adjustment-only day still opened a GROUP BY bucket and emitted
     * an all-zero row, putting an empty header in the Daily list and a phantom cell in
     * the Calendar.
     *
     * The modulo term floors toward negative infinity. SQLite integer division
     * truncates toward zero, which buckets pre-1970 timestamps a day late.
     */
    @Query(
        """
        SELECT
          (occurredAtEpochSec + :zoneOffsetSec)
            - ((occurredAtEpochSec + :zoneOffsetSec) % 86400)
            - (CASE WHEN (occurredAtEpochSec + :zoneOffsetSec) % 86400 < 0 THEN 86400 ELSE 0 END)
            - :zoneOffsetSec                                                       AS dayEpochSec,
          COALESCE(SUM(CASE WHEN type = 'INCOME'  THEN amountIdr ELSE 0 END), 0)   AS income,
          COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amountIdr ELSE 0 END), 0)   AS expense
        FROM txn
        WHERE occurredAtEpochSec >= :startSec AND occurredAtEpochSec < :endSec
          AND type IN ('INCOME', 'EXPENSE')
        GROUP BY dayEpochSec
        ORDER BY dayEpochSec DESC
        """,
    )
    fun observeDailyTotals(
        startSec: Long,
        endSec: Long,
        zoneOffsetSec: Int,
    ): Flow<List<DailyTotalRow>>

    @Query(
        """
        SELECT t.categoryId  AS categoryId,
               c.name        AS categoryName,
               c.colorSlot   AS colorSlot,
               SUM(t.amountIdr) AS total
        FROM txn t
        JOIN category c ON c.id = t.categoryId
        WHERE t.type = :type
          AND t.occurredAtEpochSec >= :startSec
          AND t.occurredAtEpochSec < :endSec
        GROUP BY t.categoryId, c.name, c.colorSlot
        ORDER BY total DESC
        """,
    )
    fun observeCategoryTotals(
        startSec: Long,
        endSec: Long,
        type: String,
    ): Flow<List<CategoryTotalRow>>

    /** Drill-through for the stats screen's aggregated "Other" slice. */
    @Query(
        """
        SELECT * FROM txn
        WHERE occurredAtEpochSec >= :startSec AND occurredAtEpochSec < :endSec
          AND categoryId IN (:categoryIds)
          AND type = :type
        ORDER BY occurredAtEpochSec DESC, id DESC
        """,
    )
    fun observeInRangeForCategories(
        startSec: Long,
        endSec: Long,
        categoryIds: List<Long>,
        type: String,
    ): Flow<List<TxnEntity>>

    /** Unbounded read for export. Range-scoped queries cannot serve a full dump. */
    @Query("SELECT * FROM txn ORDER BY occurredAtEpochSec ASC, id ASC")
    suspend fun all(): List<TxnEntity>

    @Query("SELECT * FROM txn WHERE id = :id")
    suspend fun byId(id: Long): TxnEntity?

    @Query("SELECT dedupHash FROM txn WHERE dedupHash IN (:hashes)")
    suspend fun existingHashes(hashes: List<String>): List<String>

    @Query("SELECT COUNT(*) FROM txn WHERE categoryId = :categoryId")
    suspend fun countForCategory(categoryId: Long): Int

    @Query("SELECT COUNT(*) FROM txn WHERE subcategoryId = :subcategoryId")
    suspend fun countForSubcategory(subcategoryId: Long): Int

    /*
     * Grouped counts. The category screen puts a count on every row and disables
     * delete wherever there is one; asking per row is an N+1 over ~19 categories.
     */

    @Query(
        "SELECT categoryId AS id, COUNT(*) AS count FROM txn " +
            "WHERE categoryId IS NOT NULL GROUP BY categoryId",
    )
    suspend fun countsByCategory(): List<IdCount>

    @Query(
        "SELECT subcategoryId AS id, COUNT(*) AS count FROM txn " +
            "WHERE subcategoryId IS NOT NULL GROUP BY subcategoryId",
    )
    suspend fun countsBySubcategory(): List<IdCount>

    /**
     * The subcategory last used for a category, so the entry sheet can pre-fill it.
     * Recall is the reason subcategory can stay optional without staying empty.
     */
    @Query(
        """
        SELECT subcategoryId FROM txn
        WHERE categoryId = :categoryId AND subcategoryId IS NOT NULL
        ORDER BY occurredAtEpochSec DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun lastSubcategoryFor(categoryId: Long): Long?

    @Insert
    suspend fun insert(txn: TxnEntity): Long

    /**
     * Batch insert for the importer. Deduplication happens before this call, inside
     * the same transaction, so there is nothing here to ignore or replace.
     */
    @Insert
    suspend fun insertAll(txns: List<TxnEntity>): List<Long>

    @Update
    suspend fun update(txn: TxnEntity)

    @Delete
    suspend fun delete(txn: TxnEntity)

    @Query("UPDATE txn SET categoryId = :toCategoryId WHERE categoryId = :fromCategoryId")
    suspend fun reassignCategory(fromCategoryId: Long, toCategoryId: Long): Int

    @Query("UPDATE txn SET subcategoryId = :toId WHERE subcategoryId = :fromId")
    suspend fun reassignSubcategory(fromId: Long, toId: Long?): Int

    @Query("DELETE FROM txn WHERE importBatchId = :batchId")
    suspend fun deleteByImportBatch(batchId: Long): Int
}
