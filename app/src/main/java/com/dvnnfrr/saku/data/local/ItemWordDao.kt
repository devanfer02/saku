package com.dvnnfrr.saku.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemWordDao {

    @Query("SELECT * FROM item_word ORDER BY printed")
    fun observeAll(): Flow<List<ItemWordEntity>>

    /** One-shot, so it is safe inside `withTransaction`. */
    @Query("SELECT * FROM item_word ORDER BY printed")
    suspend fun all(): List<ItemWordEntity>

    /** A later edit of the same printed token overwrites the word, by primary key. */
    @Upsert
    suspend fun upsert(words: List<ItemWordEntity>)

    /** For restore: a word the database already has is kept as it is. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(words: List<ItemWordEntity>): List<Long>
}
