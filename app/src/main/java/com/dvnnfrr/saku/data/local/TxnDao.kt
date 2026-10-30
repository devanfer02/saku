package com.dvnnfrr.saku.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * The filter, as one `WHERE` fragment every filtered query concatenates.
 *
 * Transfers pass the account selection on either leg; transfers and adjustments carry no
 * category, so they pass only while both category selections are "All". The six
 * arguments come from `TransactionFilter.toArgs()` in `Mappers.kt`. A `const val`, so
 * Room still verifies each full query at compile time.
 *
 * Column names are unqualified so the fragment also works in the joined category-totals
 * query. A filtered query must therefore never join a table that also has `accountId`,
 * `toAccountId`, `categoryId` or `type`.
 */
const val TXN_FILTER_WHERE = """
          AND (:allAccounts OR accountId IN (:accountIds) OR toAccountId IN (:accountIds))
          AND (    (type = 'INCOME'  AND (:allIncome  OR categoryId IN (:incomeIds)))
                OR (type = 'EXPENSE' AND (:allExpense OR categoryId IN (:expenseIds)))
                OR (type IN ('TRANSFER', 'ADJUSTMENT') AND :allIncome AND :allExpense) )
"""

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
        """ + TXN_FILTER_WHERE + """
        ORDER BY occurredAtEpochSec DESC, id DESC
        """,
    )
    fun observeInRange(
        startSec: Long,
        endSec: Long,
        allAccounts: Boolean,
        accountIds: List<Long>,
        allIncome: Boolean,
        incomeIds: List<Long>,
        allExpense: Boolean,
        expenseIds: List<Long>,
    ): Flow<List<TxnEntity>>

    @Query(
        """
        SELECT * FROM txn
        WHERE occurredAtEpochSec >= :startSec AND occurredAtEpochSec < :endSec
          AND categoryId = :categoryId
          AND type = :type
        """ + TXN_FILTER_WHERE + """
        ORDER BY occurredAtEpochSec DESC, id DESC
        """,
    )
    fun observeInRangeForCategory(
        startSec: Long,
        endSec: Long,
        categoryId: Long,
        type: String,
        allAccounts: Boolean,
        accountIds: List<Long>,
        allIncome: Boolean,
        incomeIds: List<Long>,
        allExpense: Boolean,
        expenseIds: List<Long>,
    ): Flow<List<TxnEntity>>

    @Query(
        """
        SELECT
          COALESCE(SUM(CASE WHEN type = 'INCOME'  THEN amountIdr ELSE 0 END), 0) AS income,
          COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amountIdr ELSE 0 END), 0) AS expense
        FROM txn
        WHERE occurredAtEpochSec >= :startSec AND occurredAtEpochSec < :endSec
        """ + TXN_FILTER_WHERE,
    )
    fun observePeriodTotals(
        startSec: Long,
        endSec: Long,
        allAccounts: Boolean,
        accountIds: List<Long>,
        allIncome: Boolean,
        incomeIds: List<Long>,
        allExpense: Boolean,
        expenseIds: List<Long>,
    ): Flow<PeriodTotalsRow>

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
        """ + TXN_FILTER_WHERE + """
        GROUP BY dayEpochSec
        ORDER BY dayEpochSec DESC
        """,
    )
    fun observeDailyTotals(
        startSec: Long,
        endSec: Long,
        zoneOffsetSec: Int,
        allAccounts: Boolean,
        accountIds: List<Long>,
        allIncome: Boolean,
        incomeIds: List<Long>,
        allExpense: Boolean,
        expenseIds: List<Long>,
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
        """ + TXN_FILTER_WHERE + """
        GROUP BY t.categoryId, c.name, c.colorSlot
        ORDER BY total DESC
        """,
    )
    fun observeCategoryTotals(
        startSec: Long,
        endSec: Long,
        type: String,
        allAccounts: Boolean,
        accountIds: List<Long>,
        allIncome: Boolean,
        incomeIds: List<Long>,
        allExpense: Boolean,
        expenseIds: List<Long>,
    ): Flow<List<CategoryTotalRow>>

    /** Drill-through for the stats screen's aggregated "Other" slice. */
    @Query(
        """
        SELECT * FROM txn
        WHERE occurredAtEpochSec >= :startSec AND occurredAtEpochSec < :endSec
          AND categoryId IN (:categoryIds)
          AND type = :type
        """ + TXN_FILTER_WHERE + """
        ORDER BY occurredAtEpochSec DESC, id DESC
        """,
    )
    fun observeInRangeForCategories(
        startSec: Long,
        endSec: Long,
        categoryIds: List<Long>,
        type: String,
        allAccounts: Boolean,
        accountIds: List<Long>,
        allIncome: Boolean,
        incomeIds: List<Long>,
        allExpense: Boolean,
        expenseIds: List<Long>,
    ): Flow<List<TxnEntity>>

    /**
     * Per-account income, expense and transfer legs for the filter sheet's ACCOUNT tab.
     *
     * Two legs by `UNION ALL`: the source leg books income, expense and transfer-out
     * against `accountId`, the destination leg books transfer-in against `toAccountId`.
     * Adjustments are left out, because they correct a balance rather than move money.
     * Deliberately unfiltered: the tab lists every account so the user can choose among
     * them, and its figures must not change when a category tab does.
     */
    @Query(
        """
        SELECT accountId,
               SUM(income)      AS income,
               SUM(expense)     AS expense,
               SUM(transferIn)  AS transferIn,
               SUM(transferOut) AS transferOut
        FROM (
          SELECT accountId                                              AS accountId,
                 CASE WHEN type = 'INCOME'   THEN amountIdr ELSE 0 END AS income,
                 CASE WHEN type = 'EXPENSE'  THEN amountIdr ELSE 0 END AS expense,
                 0                                                      AS transferIn,
                 CASE WHEN type = 'TRANSFER' THEN amountIdr ELSE 0 END AS transferOut
          FROM txn
          WHERE occurredAtEpochSec >= :startSec AND occurredAtEpochSec < :endSec
            AND type IN ('INCOME', 'EXPENSE', 'TRANSFER')
          UNION ALL
          SELECT toAccountId AS accountId, 0, 0, amountIdr, 0
          FROM txn
          WHERE occurredAtEpochSec >= :startSec AND occurredAtEpochSec < :endSec
            AND type = 'TRANSFER' AND toAccountId IS NOT NULL
        )
        GROUP BY accountId
        """,
    )
    fun observeAccountFlows(startSec: Long, endSec: Long): Flow<List<AccountFlowsRow>>

    /** Every transaction passing the filter, newest first. For search, over all history. */
    @Query(
        """
        SELECT * FROM txn
        WHERE 1 = 1
        """ + TXN_FILTER_WHERE + """
        ORDER BY occurredAtEpochSec DESC, id DESC
        """,
    )
    fun observeAll(
        allAccounts: Boolean,
        accountIds: List<Long>,
        allIncome: Boolean,
        incomeIds: List<Long>,
        allExpense: Boolean,
        expenseIds: List<Long>,
    ): Flow<List<TxnEntity>>

    /**
     * Every noted transaction of one type, newest first, as five columns. Note
     * autocomplete folds this in Kotlin: "most-used category, ties to the most recent"
     * needs a window function, and SQLite on API 26 to 29 has none.
     */
    @Query(
        """
        SELECT id, note, categoryId, amountIdr, occurredAtEpochSec FROM txn
        WHERE type = :type AND note IS NOT NULL AND TRIM(note) <> ''
        ORDER BY occurredAtEpochSec DESC, id DESC
        """,
    )
    fun observeNoteUses(type: String): Flow<List<NoteUseRow>>

    /** Unbounded read for export. Range-scoped queries cannot serve a full dump. */
    @Query("SELECT * FROM txn ORDER BY occurredAtEpochSec ASC, id ASC")
    suspend fun all(): List<TxnEntity>

    @Query("SELECT * FROM txn WHERE id = :id")
    suspend fun byId(id: Long): TxnEntity?

    @Query("SELECT dedupHash FROM txn WHERE dedupHash IN (:hashes)")
    suspend fun existingHashes(hashes: List<String>): List<String>

    @Query("SELECT COUNT(*) FROM txn WHERE categoryId = :categoryId")
    suspend fun countForCategory(categoryId: Long): Int

    /*
     * Grouped counts. The category screen puts a count on every row and disables
     * delete wherever there is one; asking per row is an N+1 over ~19 categories.
     */

    @Query(
        "SELECT categoryId AS id, COUNT(*) AS count FROM txn " +
            "WHERE categoryId IS NOT NULL GROUP BY categoryId",
    )
    suspend fun countsByCategory(): List<IdCount>

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

    /**
     * When the user last logged something, for the weekly reminder. A typed row counts
     * from when it was saved; an imported row from its own timestamp, because the moment
     * of import says nothing about logging. Adjustments are reconciling, not logging.
     */
    @Query(
        """
        SELECT MAX(CASE WHEN importBatchId IS NULL THEN createdAtEpochSec ELSE occurredAtEpochSec END)
        FROM txn
        WHERE type != 'ADJUSTMENT'
        """,
    )
    suspend fun lastEnteredAtEpochSec(): Long?
}
