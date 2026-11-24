package com.personal.animeplayer.ui.player

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.animeplayer.data.datastore.AppPreferences
import com.personal.animeplayer.data.model.Episode
import com.personal.animeplayer.data.model.FileOpResult
import com.personal.animeplayer.data.model.SortOrder
import com.personal.animeplayer.data.repository.MediaRepository
import com.personal.animeplayer.data.repository.PlaybackRepository
import com.personal.animeplayer.player.PlaylistHolder
import com.personal.animeplayer.player.PlaylistNavigator
import com.personal.animeplayer.ui.compose.components.VideoThumbnailLoader
import com.personal.animeplayer.util.sortedByOrder
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class PlayerUiData(
    val isLoaded: Boolean = false,
    val initialPositionMs: Long = 0L,
    val externalSubtitles: List<Uri> = emptyList(),
    val subtitleScale: Int = 100,
    val subtitleDelayMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val savedBrightness: Float = -1f,
    val isLoopEnabled: Boolean = false,
    val isSkipOpEdEnabled: Boolean = true
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val playbackRepo = PlaybackRepository(application)
    private val mediaRepo = MediaRepository(application)
    private val prefs = AppPreferences(application)

    private val _playerData = MutableStateFlow(PlayerUiData())
    val playerData: StateFlow<PlayerUiData> = _playerData.asStateFlow()

    private val navigator = PlaylistNavigator()
    private var currentSeriesKey: String = ""

    fun initVideo(videoUri: Uri, title: String, seriesFolderUri: String?) {
        val videoUriStr = videoUri.toString()
        navigator.setCurrentMedia(videoUriStr, title)
        currentSeriesKey = seriesFolderUri ?: videoUriStr
        PlaylistHolder.lastPlayedUri = videoUriStr
        PlaylistHolder.shouldScrollToLastPlayed = true

        // Use pre-sorted active playlist from PlaylistHolder if available
        val activePlaylist = PlaylistHolder.currentPlaylist
        if (activePlaylist.isNotEmpty() && activePlaylist.any { it.uri == videoUriStr }) {
            navigator.setPlaylist(activePlaylist)
        }

        viewModelScope.launch {
            val savedProgress = playbackRepo.getProgress(videoUriStr)
            val startPos = if (savedProgress != null && !savedProgress.isCompleted && (savedProgress.durationMs >= 60_000L || savedProgress.durationMs == 0L)) {
                savedProgress.lastPositionMs
            } else 0L

            val matchingSubs = try {
                val fileName = videoUri.lastPathSegment ?: title
                mediaRepo.findMatchingSubtitles(videoUri, fileName).map { it.uri }
            } catch (e: Exception) {
                emptyList()
            }

            val scale = prefs.subtitleScale.firstOrNull() ?: 100
            val delayMs = prefs.subtitleDelayMs.firstOrNull() ?: 0L
            val speed = prefs.playbackSpeed.firstOrNull() ?: 1.0f
            val brightness = prefs.savedBrightness.firstOrNull() ?: -1f
            val loopEnabled = prefs.isLoopEnabled.firstOrNull() ?: false
            val skipOpEdEnabled = prefs.isSkipOpEdEnabled.firstOrNull() ?: true

            _playerData.value = PlayerUiData(
                isLoaded = true,
                initialPositionMs = startPos,
                externalSubtitles = matchingSubs,
                subtitleScale = scale,
                subtitleDelayMs = delayMs,
                playbackSpeed = speed,
                savedBrightness = brightness,
                isLoopEnabled = loopEnabled,
                isSkipOpEdEnabled = skipOpEdEnabled
            )

            prefs.setLastWatchedUri(videoUriStr)

            if (navigator.playlist.isEmpty()) {
                val folderUri = if (!seriesFolderUri.isNullOrEmpty()) seriesFolderUri else "mediastore://folder/all"
                currentSeriesKey = folderUri
                val rawPlaylist = try {
                    mediaRepo.getEpisodesForSeries(Uri.parse(folderUri))
                } catch (e: Exception) {
                    emptyList()
                }

                val activeSort = prefs.sortOrder.firstOrNull() ?: SortOrder.NAME
                navigator.setPlaylist(rawPlaylist.sortedByOrder(activeSort))
            }
        }
    }

    fun hasNext(): Boolean = navigator.hasNext()

    fun hasPrevious(): Boolean = navigator.hasPrevious()

    fun getNextEpisode(): Episode? = navigator.getNextEpisode()

    fun getPreviousEpisode(): Episode? = navigator.getPreviousEpisode()

    fun getCurrentEpisode(): Episode? = navigator.getCurrentEpisode()

    suspend fun deleteCurrentVideo(): FileOpResult {
        val ep = getCurrentEpisode() ?: return FileOpResult.Error("Video tidak ditemukan")
        val res = mediaRepo.deleteVideo(ep)
        if (res is FileOpResult.Success) {
            PlaylistHolder.recordDeletedUri(ep.uri)
            VideoThumbnailLoader.evict(getApplication(), listOf(ep.uri))
        }
        return res
    }

    fun removeCurrentEpisodeAndGetNext(): Episode? {
        val currentUri = navigator.currentVideoUri
        PlaylistHolder.recordDeletedUri(currentUri)
        viewModelScope.launch {
            mediaRepo.deleteProgress(currentUri)
        }

        val targetEpisode = navigator.removeCurrentAndGetNext()
        if (targetEpisode != null) {
            PlaylistHolder.lastPlayedUri = targetEpisode.uri
            viewModelScope.launch {
                prefs.setLastWatchedUri(targetEpisode.uri)
            }
        } else {
            PlaylistHolder.lastPlayedUri = null
            viewModelScope.launch {
                prefs.setLastWatchedUri(null)
            }
        }
        return targetEpisode
    }

    fun switchToEpisode(
        episode: Episode,
        onReady: ((startPos: Long, externalSubtitles: List<Uri>) -> Unit)? = null
    ) {
        navigator.switchToEpisode(episode)
        val uriStr = episode.uri
        PlaylistHolder.lastPlayedUri = uriStr
        PlaylistHolder.shouldScrollToLastPlayed = true

        viewModelScope.launch {
            prefs.setLastWatchedUri(uriStr)

            val savedProgress = playbackRepo.getProgress(uriStr)
            val effectiveDur = savedProgress?.durationMs ?: episode.durationMs
            val startPos = if (savedProgress != null && !savedProgress.isCompleted && effectiveDur >= 60_000L) {
                savedProgress.lastPositionMs
            } else 0L

            val epUri = Uri.parse(uriStr)
            val matchingSubs = try {
                val fileName = episode.fileName.ifEmpty { epUri.lastPathSegment ?: episode.title }
                mediaRepo.findMatchingSubtitles(epUri, fileName).map { it.uri }
            } catch (e: Exception) {
                emptyList()
            }

            _playerData.value = _playerData.value.copy(
                isLoaded = true,
                initialPositionMs = startPos,
                externalSubtitles = matchingSubs
            )

            onReady?.invoke(startPos, matchingSubs)
        }
    }

    fun saveProgress(positionMs: Long, durationMs: Long) {
        val uriSnapshot = navigator.currentVideoUri
        if (uriSnapshot.isBlank() || (durationMs in 1 until 60_000L)) return
        val titleSnapshot = navigator.currentTitle
        val seriesSnapshot = currentSeriesKey
        viewModelScope.launch {
            withContext(NonCancellable) {
                playbackRepo.saveProgress(
                    videoUri = uriSnapshot,
                    title = titleSnapshot,
                    seriesFolderUri = seriesSnapshot,
                    lastPositionMs = positionMs,
                    durationMs = durationMs
                )
            }
        }
    }

    fun updateSubtitlePreferences(
        scalePercent: Int = _playerData.value.subtitleScale,
        delayMs: Long = _playerData.value.subtitleDelayMs
    ) {
        viewModelScope.launch {
            prefs.setSubtitleScale(scalePercent)
            prefs.setSubtitleDelayMs(delayMs)

            _playerData.value = _playerData.value.copy(
                subtitleScale = scalePercent,
                subtitleDelayMs = delayMs
            )
        }
    }

    fun savePlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            prefs.setPlaybackSpeed(speed)
            _playerData.value = _playerData.value.copy(playbackSpeed = speed)
        }
    }

    fun saveBrightness(brightness: Float) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                prefs.setSavedBrightness(brightness)
                _playerData.value = _playerData.value.copy(savedBrightness = brightness)
            }
        }
    }

    fun saveLoopEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setLoopEnabled(enabled)
            _playerData.value = _playerData.value.copy(isLoopEnabled = enabled)
        }
    }

    fun saveSkipOpEdEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setSkipOpEdEnabled(enabled)
            _playerData.value = _playerData.value.copy(isSkipOpEdEnabled = enabled)
        }
    }
}
