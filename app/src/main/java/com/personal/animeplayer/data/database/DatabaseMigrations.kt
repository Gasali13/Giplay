package com.personal.animeplayer.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit Room migrations to preserve watch history across schema updates.
 * Future schema version increments must register a new `Migration(x, y)` in
 * [ALL_MIGRATIONS] instead of falling back to destructive migration.
 */
object DatabaseMigrations {

    internal val PLAYBACK_PROGRESS_V2_COLUMNS: LinkedHashMap<String, String> = linkedMapOf(
        "title" to "TEXT NOT NULL DEFAULT ''",
        "seriesFolderUri" to "TEXT",
        "lastPositionMs" to "INTEGER NOT NULL DEFAULT 0",
        "durationMs" to "INTEGER NOT NULL DEFAULT 0",
        "lastWatchedTimestamp" to "INTEGER NOT NULL DEFAULT 0",
        "isCompleted" to "INTEGER NOT NULL DEFAULT 0"
    )

    internal fun buildMissingColumnStatements(existingColumns: Set<String>): List<String> {
        return PLAYBACK_PROGRESS_V2_COLUMNS.entries
            .filter { (columnName, _) -> columnName !in existingColumns }
            .map { (columnName, columnDef) ->
                "ALTER TABLE `playback_progress` ADD COLUMN `$columnName` $columnDef"
            }
    }

    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `playback_progress` (" +
                    "`videoUri` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`seriesFolderUri` TEXT, " +
                    "`lastPositionMs` INTEGER NOT NULL, " +
                    "`durationMs` INTEGER NOT NULL, " +
                    "`lastWatchedTimestamp` INTEGER NOT NULL, " +
                    "`isCompleted` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`videoUri`))"
            )

            val existingColumns = mutableSetOf<String>()
            db.query("PRAGMA table_info(`playback_progress`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0) {
                        existingColumns.add(cursor.getString(nameIndex))
                    }
                }
            }

            buildMissingColumnStatements(existingColumns).forEach { statement ->
                db.execSQL(statement)
            }
        }
    }

    val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
}
