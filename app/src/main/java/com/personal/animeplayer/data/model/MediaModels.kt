package com.personal.animeplayer.data.model

data class AnimeSeries(
    val folderUri: String,
    val name: String,
    val episodeCount: Int = 0,
    val firstEpisodeUri: String? = null,
    val lastModified: Long = 0L
)

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

data class SubtitleTrackInfo(
    val id: String,
    val label: String,
    val language: String? = null,
    val isExternal: Boolean = false,
    val uri: String? = null,
    val trackGroupIndex: Int = -1,
    val trackIndex: Int = -1,
    val isSelected: Boolean = false
)

data class AudioTrackInfo(
    val id: String,
    val label: String,
    val language: String? = null,
    val channels: Int = 2,
    val sampleRate: Int = 48000,
    val trackGroupIndex: Int = -1,
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
