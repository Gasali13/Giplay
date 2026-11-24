package com.personal.animeplayer.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class AnimeSeries(
    val folderUri: String,
    val name: String,
    val episodeCount: Int = 0,
    val lastModified: Long = 0L
)

@Immutable
data class Episode(
    val uri: String,
    val title: String,
    val fileName: String,
    val seriesFolderUri: String,
    val durationMs: Long = 0L,
    val lastPositionMs: Long = 0L,
    val progressPercentage: Int = 0,
    val fileSize: Long = 0L,
    val dateAdded: Long = 0L
)

@Immutable
data class SubtitleTrackInfo(
    val label: String,
    val language: String? = null,
    val trackIndex: Int = -1,
    val isSelected: Boolean = false
)

@Immutable
data class AudioTrackInfo(
    val label: String,
    val language: String? = null,
    val channels: Int = 2,
    val trackIndex: Int = -1,
    val isSelected: Boolean = false
)

data class MediaScanResult(
    val allVideos: List<Episode> = emptyList(),
    val folders: List<AnimeSeries> = emptyList()
)

enum class SortOrder {
    NAME,
    NEWEST,
    OLDEST
}

@Immutable
data class VideoDetailInfo(
    val title: String,
    val fileName: String,
    val path: String,
    val fileSizeFormatted: String,
    val durationFormatted: String,
    val resolution: String,
    val mimeType: String,
    val bitrateFormatted: String,
    val dateAddedFormatted: String,
    val hdrType: String = "SDR",
    val colorDetails: String = ""
)

sealed class FileOpResult {
    object Success : FileOpResult()
    data class RequiresConsent(val intentSender: android.content.IntentSender) : FileOpResult()
    data class Error(val message: String) : FileOpResult()
}
