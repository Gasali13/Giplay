package com.personal.animeplayer.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_progress")
data class PlaybackProgressEntity(
    @PrimaryKey val videoUri: String,
    val title: String,
    val seriesFolderUri: String?,
    val lastPositionMs: Long,
    val durationMs: Long,
    val lastWatchedTimestamp: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = false
) {
    val progressPercentage: Int
        get() = if (durationMs > 0) ((lastPositionMs * 100) / durationMs).toInt().coerceIn(0, 100) else 0
}
