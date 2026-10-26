package com.personal.animeplayer.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
@Dao
interface PlaybackProgressDao {
    @Query("SELECT * FROM playback_progress WHERE videoUri = :videoUri LIMIT 1")
    suspend fun getProgress(videoUri: String): PlaybackProgressEntity?

    @Query("SELECT * FROM playback_progress")
    suspend fun getAllProgress(): List<PlaybackProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(progress: PlaybackProgressEntity)

    @Query("DELETE FROM playback_progress WHERE videoUri = :videoUri")
    suspend fun deleteProgress(videoUri: String)

    @Query("DELETE FROM playback_progress WHERE videoUri IN (:videoUris)")
    suspend fun deleteProgressList(videoUris: List<String>)

    @Query("DELETE FROM playback_progress WHERE durationMs > 0 AND durationMs < 60000")
    suspend fun deleteShortVideosProgress()
}
