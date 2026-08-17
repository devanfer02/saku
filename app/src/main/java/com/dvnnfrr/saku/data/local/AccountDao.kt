package com.dvnnfrr.saku.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM account WHERE id = :id")
    suspend fun byId(id: Long): AccountEntity?

    @Query("SELECT * FROM account ORDER BY sortOrder, id")
    suspend fun all(): List<AccountEntity>

    /** Archived accounts hold their slot; freeing it would recolour live charts. */
    @Query("SELECT colorSlot FROM account")
    suspend fun allColorSlots(): List<Int>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM account")
    suspend fun nextSortOrder(): Int

    @Query("SELECT COUNT(*) FROM txn WHERE accountId = :id OR toAccountId = :id")
    suspend fun transactionCount(id: Long): Int

    /**
     * Balance per account: opening balance, plus everything that lands in the account
     * and minus everything that leaves it, counting only transactions dated on or
     * after the account's own as-of date.
     *
     * The date filter is what lets a year of imported history feed the stats
     * screen without moving a single balance. `amountIdr` is a positive magnitude for
     * EXPENSE, INCOME and TRANSFER; ADJUSTMENT stores a signed value so a downward
     * correction is a negative addend. A LIABILITY account's balance therefore goes
     * negative as debt accrues, which is the convention AccountSummary depends on.
     *
     * Archived accounts are included. Filtering them here previously made
     * `reconcile()` and `byId()` silently fall back to the opening balance for an
     * archived account, reporting a wrong figure and writing a phantom adjustment.
     * Callers that want only live accounts filter on [AccountBalanceRow.archived].
     */
    @Query(
        """
        SELECT a.id                        AS id,
               a.name                      AS name,
               a.type                      AS type,
               a.colorSlot                 AS colorSlot,
               a.sortOrder                 AS sortOrder,
               a.archived                  AS archived,
               a.openingBalance            AS openingBalance,
               a.openingAsOfEpochSec       AS openingAsOfEpochSec,
               a.lastReconciledEpochSec    AS lastReconciledEpochSec,
               a.openingBalance
                 + COALESCE((
                     SELECT SUM(
                       CASE t.type
                         WHEN 'INCOME'     THEN  t.amountIdr
                         WHEN 'ADJUSTMENT' THEN  t.amountIdr
                         WHEN 'EXPENSE'    THEN -t.amountIdr
                         WHEN 'TRANSFER'   THEN -t.amountIdr
                         ELSE 0
                       END)
                     FROM txn t
                     WHERE t.accountId = a.id
                       AND t.occurredAtEpochSec >= a.openingAsOfEpochSec
                   ), 0)
                 + COALESCE((
                     SELECT SUM(t.amountIdr)
                     FROM txn t
                     WHERE t.toAccountId = a.id
                       AND t.type = 'TRANSFER'
                       AND t.occurredAtEpochSec >= a.openingAsOfEpochSec
                   ), 0)                   AS balance
        FROM account a
        ORDER BY a.sortOrder, a.id
        """,
    )
    fun observeBalances(): Flow<List<AccountBalanceRow>>

    /**
     * The same aggregate for one account, as a one-shot read.
     *
     * Needed because collecting `observeBalances().first()` inside a suspend function
     * recomputes every account and registers an invalidation observer, which deadlocks
     * against the write lock once the caller is wrapped in `withTransaction`.
     */
    @Query(
        """
        SELECT a.openingBalance
                 + COALESCE((
                     SELECT SUM(
                       CASE t.type
                         WHEN 'INCOME'     THEN  t.amountIdr
                         WHEN 'ADJUSTMENT' THEN  t.amountIdr
                         WHEN 'EXPENSE'    THEN -t.amountIdr
                         WHEN 'TRANSFER'   THEN -t.amountIdr
                         ELSE 0
                       END)
                     FROM txn t
                     WHERE t.accountId = a.id
                       AND t.occurredAtEpochSec >= a.openingAsOfEpochSec
                   ), 0)
                 + COALESCE((
                     SELECT SUM(t.amountIdr)
                     FROM txn t
                     WHERE t.toAccountId = a.id
                       AND t.type = 'TRANSFER'
                       AND t.occurredAtEpochSec >= a.openingAsOfEpochSec
                   ), 0)
        FROM account a
        WHERE a.id = :id
        """,
    )
    suspend fun balanceOf(id: Long): Long?

    /**
     * Transfers that moving [id]'s cutover to [newAsOf] would split.
     *
     * A transfer is one row counted against two accounts, each filtered by its own
     * as-of date. Move one account's cutover past a transfer and that leg stops
     * counting while the other still does — the Total gains or loses the whole
     * amount, silently and permanently. The transaction write path already refuses
     * to create such a row; this is the same invariant on the account write path.
     */
    @Query(
        """
        SELECT COUNT(*) FROM txn t
        JOIN account o
          ON o.id = CASE WHEN t.accountId = :id THEN t.toAccountId ELSE t.accountId END
        WHERE t.type = 'TRANSFER'
          AND (t.accountId = :id OR t.toAccountId = :id)
          AND t.occurredAtEpochSec < :newAsOf
          AND t.occurredAtEpochSec >= o.openingAsOfEpochSec
        """,
    )
    suspend fun transfersSplitBy(id: Long, newAsOf: Long): Int

    /** Import maps a file's account names onto these. Archived included, live first. */
    @Query(
        """
        SELECT * FROM account
        WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name))
        ORDER BY archived ASC
        LIMIT 1
        """,
    )
    suspend fun findAnyByName(name: String): AccountEntity?

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    /*
     * Targeted column updates. `@Update` writes every column from a row read earlier,
     * so a rename that overlapped a setOpening wrote the pre-read opening balance back
     * and the account's balance jumped. Each mutator now touches only its own column.
     */

    @Query("UPDATE account SET name = :name WHERE id = :id")
    suspend fun setName(id: Long, name: String)

    @Query("UPDATE account SET type = :type WHERE id = :id")
    suspend fun setType(id: Long, type: String)

    @Query("UPDATE account SET colorSlot = :colorSlot WHERE id = :id")
    suspend fun setColorSlot(id: Long, colorSlot: Int)

    @Query(
        "UPDATE account SET openingBalance = :openingBalance, " +
            "openingAsOfEpochSec = :openingAsOfEpochSec WHERE id = :id",
    )
    suspend fun setOpening(id: Long, openingBalance: Long, openingAsOfEpochSec: Long)

    @Query("UPDATE account SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("UPDATE account SET lastReconciledEpochSec = :at WHERE id = :id")
    suspend fun stampReconciled(id: Long, at: Long)

    @Query("DELETE FROM account WHERE id = :id")
    suspend fun deleteUnused(id: Long)
}
