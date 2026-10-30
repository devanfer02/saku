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
    version = SakuDatabase.VERSION,
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

        /** The Room schema version, which Room also writes as `PRAGMA user_version`. */
        const val VERSION = 1

        /**
         * Room's identity hash for [VERSION], as it writes it into `room_master_table`.
         * Room does not expose it at runtime, so it is copied from the exported schema
         * and `SchemaIdentityTest` fails when the two drift. The database import refuses
         * a file whose hash differs.
         */
        const val IDENTITY_HASH = "bfb8f34edf6ae031f2e4cb76c266b385"

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
