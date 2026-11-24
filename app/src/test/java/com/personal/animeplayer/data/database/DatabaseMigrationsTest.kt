package com.personal.animeplayer.data.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseMigrationsTest {

    @Test
    fun testAllMigrationsContainsExplicitOneToTwoTransitionWithoutDuplicates() {
        val migrations = DatabaseMigrations.ALL_MIGRATIONS
        assertTrue("Expected at least one registered migration", migrations.isNotEmpty())

        val transitions = migrations.map { it.startVersion to it.endVersion }
        assertTrue("Expected Migration(1, 2) to be registered", (1 to 2) in transitions)
        assertEquals("Migration transitions must be unique", transitions.distinct().size, transitions.size)
        for (migration in migrations) {
            assertTrue(
                "Migration endVersion (${migration.endVersion}) must exceed startVersion (${migration.startVersion})",
                migration.endVersion > migration.startVersion
            )
        }
    }

    @Test
    fun testBuildMissingColumnStatementsForUpgradeAndCompleteTable() {
        val allV2Columns = setOf(
            "videoUri",
            "title",
            "seriesFolderUri",
            "lastPositionMs",
            "durationMs",
            "lastWatchedTimestamp",
            "isCompleted"
        )
        assertTrue(
            "Complete v2 schema should require zero ALTER TABLE statements",
            DatabaseMigrations.buildMissingColumnStatements(allV2Columns).isEmpty()
        )

        val minimalV1Columns = setOf("videoUri", "title", "lastPositionMs", "durationMs")
        val statements = DatabaseMigrations.buildMissingColumnStatements(minimalV1Columns)
        assertEquals(
            listOf(
                "ALTER TABLE `playback_progress` ADD COLUMN `seriesFolderUri` TEXT",
                "ALTER TABLE `playback_progress` ADD COLUMN `lastWatchedTimestamp` INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE `playback_progress` ADD COLUMN `isCompleted` INTEGER NOT NULL DEFAULT 0"
            ),
            statements
        )
    }
}
