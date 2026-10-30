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
        TagEntity::class,
        TxnTagEntity::class,
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
    abstract fun tagDao(): TagDao

    companion object {
        private const val NAME = "saku.db"

        /** The Room schema version, which Room also writes as `PRAGMA user_version`. */
        const val VERSION = 2

        /**
         * Room's identity hash for [VERSION], as it writes it into `room_master_table`.
         * Room does not expose it at runtime, so it is copied from the exported schema
         * and `SchemaIdentityTest` fails when the two drift. The database import refuses
         * a file whose hash differs.
         */
        const val IDENTITY_HASH = "f7bbcdec21beeb7050323443cbe7e4bc"

        /** Version 1's hash. The database import still reads version 1 files. */
        const val IDENTITY_HASH_V1 = "bfb8f34edf6ae031f2e4cb76c266b385"

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
                // Never a destructive fallback: a missing migration must crash, not wipe
                // the ledger. Opening a newer file with an older Saku is refused by Room.
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
