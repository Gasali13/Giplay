package com.personal.animeplayer.data.repository

import android.content.Context
import com.personal.animeplayer.data.database.AnimeDatabase
import com.personal.animeplayer.data.database.PlaybackProgressEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlaybackRepository(context: Context) {

    private val db = AnimeDatabase.getInstance(context)
    private val progressDao = db.playbackProgressDao()

    suspend fun getProgress(videoUri: String): PlaybackProgressEntity? = withContext(Dispatchers.IO) {
        val progress = progressDao.getProgress(videoUri) ?: return@withContext null
        if (progress.durationMs in 1 until 60_000L) {
            return@withContext null
        }
        progress
    }

    suspend fun saveProgress(
        videoUri: String,
        title: String,
        seriesFolderUri: String?,
        lastPositionMs: Long,
        durationMs: Long
    ) = withContext(Dispatchers.IO) {
        // Video di bawah 60 detik tidak perlu fitur lanjut nonton
        if (durationMs < 60_000L) return@withContext
        val isCompleted = lastPositionMs >= (durationMs * 0.95) // Marked completed if watched > 95%
        val entity = PlaybackProgressEntity(
            videoUri = videoUri,
            title = title,
            seriesFolderUri = seriesFolderUri,
            lastPositionMs = if (isCompleted) 0L else lastPositionMs,
            durationMs = durationMs,
            lastWatchedTimestamp = System.currentTimeMillis(),
            isCompleted = isCompleted
        )
        progressDao.insertOrUpdate(entity)
    }
}
