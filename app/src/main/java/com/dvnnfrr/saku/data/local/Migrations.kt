package com.dvnnfrr.saku.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema 1 to 2: the tag tables. Create statements only, so no existing row can change;
 * `MigrationSqlTest` fails if anything else appears here. The text is Room's own
 * `createSql` from `schemas/.../2.json`, so the migrated file validates against the schema.
 */
internal val MIGRATION_1_2_SQL: List<String> = listOf(
    "CREATE TABLE IF NOT EXISTS `tag` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`name` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `archived` INTEGER NOT NULL)",
    "CREATE TABLE IF NOT EXISTS `txn_tag` (`txnId` INTEGER NOT NULL, `tagId` INTEGER NOT NULL, " +
        "PRIMARY KEY(`txnId`, `tagId`), " +
        "FOREIGN KEY(`txnId`) REFERENCES `txn`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
        "FOREIGN KEY(`tagId`) REFERENCES `tag`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
    "CREATE INDEX IF NOT EXISTS `index_txn_tag_tagId` ON `txn_tag` (`tagId`)",
)

val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MIGRATION_1_2_SQL.forEach(db::execSQL)
    }
}

/** Every migration Saku ships, in order. The builder and the migration test both use this. */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
