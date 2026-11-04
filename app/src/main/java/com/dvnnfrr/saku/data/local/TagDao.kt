package com.dvnnfrr.saku.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    /** Every tag, archived included, in the user's order. */
    @Query("SELECT * FROM tag ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<TagEntity>>

    /** One-shot, so it is safe inside `withTransaction`. */
    @Query("SELECT * FROM tag ORDER BY sortOrder, id")
    suspend fun all(): List<TagEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM tag")
    suspend fun nextSortOrder(): Int

    @Insert
    suspend fun insert(tag: TagEntity): Long

    @Query("UPDATE tag SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE tag SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    /** Rows per tag, for the Tags screen. A tag on no row has no entry. */
    @Query("SELECT tagId AS id, COUNT(*) AS count FROM txn_tag GROUP BY tagId")
    fun observeCounts(): Flow<List<IdCount>>

    /**
     * Every link. The lists attach tags to their rows from this; the table holds a few
     * links per tagged row, so reading it whole is cheaper than a join per list query.
     */
    @Query("SELECT * FROM txn_tag")
    fun observeLinks(): Flow<List<TxnTagEntity>>

    @Query("SELECT * FROM txn_tag")
    suspend fun allLinks(): List<TxnTagEntity>

    @Query("SELECT tagId FROM txn_tag WHERE txnId = :txnId")
    suspend fun tagIdsFor(txnId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(links: List<TxnTagEntity>)

    /** Takes [tagIds] off one transaction. Scoped to that row; the tags themselves stay. */
    @Query("DELETE FROM txn_tag WHERE txnId = :txnId AND tagId IN (:tagIds)")
    suspend fun unlink(txnId: Long, tagIds: List<Long>)
}
