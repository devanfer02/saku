package com.dvnnfrr.saku.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        SubcategoryEntity::class,
        TxnEntity::class,
        ImportBatchEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SakuDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun subcategoryDao(): SubcategoryDao
    abstract fun txnDao(): TxnDao
    abstract fun importBatchDao(): ImportBatchDao

    companion object {
        private const val NAME = "saku.db"

        @Volatile
        private var instance: SakuDatabase? = null

        fun get(context: Context): SakuDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): SakuDatabase =
            Room.databaseBuilder(context, SakuDatabase::class.java, NAME)
                // Foreign keys are RESTRICT on the paths that matter, so an account or
                // category holding transactions cannot be deleted out from under them.
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
