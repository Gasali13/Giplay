package com.personal.animeplayer.ui.player

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.animeplayer.data.datastore.AppPreferences
import com.personal.animeplayer.data.repository.MediaRepository
import com.personal.animeplayer.data.repository.PlaybackRepository
import com.personal.animeplayer.player.PlaylistHolder
import com.personal.animeplayer.util.sortedByOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.personal.animeplayer.data.model.Episode
import com.personal.animeplayer.data.model.FileOpResult
import com.personal.animeplayer.data.model.SortOrder
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

data class PlayerUiData(
    val isLoaded: Boolean = false,
    val initialPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val externalSubtitles: List<Uri> = emptyList(),
    val subtitleScale: Int = 100,
    val subtitleFontSize: Float = 18f,
    val subtitleDelayMs: Long = 0L,
    val isDualSubEnabled: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val savedBrightness: Float = -1f,
    val isLoopEnabled: Boolean = false,
    val isSkipOpEdEnabled: Boolean = true,
    val playerEngineMode: String = "auto",
    val decoderMode: String = "mediacodec",
    val isSongDetectionEnabled: Boolean = true
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val playbackRepo = PlaybackRepository(application)
    private val mediaRepo = MediaRepository(application)
    private val prefs = AppPreferences(application)

    private val _playerData = MutableStateFlow(PlayerUiData())
    val playerData: StateFlow<PlayerUiData> = _playerData.asStateFlow()

    private var currentVideoUri: String = ""
    private var currentSeriesKey: String = ""
    private var currentTitle: String = ""
    private var playlist: List<Episode> = emptyList()
    private var currentIndex: Int = -1

    fun initVideo(videoUri: Uri, title: String, seriesFolderUri: String?) {
        currentVideoUri = videoUri.toString()
        currentSeriesKey = seriesFolderUri ?: videoUri.toString()
        currentTitle = title
        PlaylistHolder.lastPlayedUri = currentVideoUri
        PlaylistHolder.shouldScrollToLastPlayed = true

        // Use pre-sorted active playlist from PlaylistHolder if available
        val activePlaylist = PlaylistHolder.currentPlaylist
        if (activePlaylist.isNotEmpty() && activePlaylist.any { it.uri == currentVideoUri }) {
            playlist = activePlaylist
            currentIndex = playlist.indexOfFirst { it.uri == currentVideoUri }
            if (currentIndex == -1) {
                currentIndex = playlist.indexOfFirst { it.title.equals(currentTitle, ignoreCase = true) }
            }
        }

        viewModelScope.launch {
            // Check resume position first
            val savedProgress = playbackRepo.getProgress(currentVideoUri)
            val startPos = if (savedProgress != null && !savedProgress.isCompleted && (savedProgress.durationMs >= 60_000L || savedProgress.durationMs == 0L)) {
                savedProgress.lastPositionMs
            } else 0L

            // Auto-detect matching external subtitles
            val matchingSubs = try {
                val fileName = videoUri.lastPathSegment ?: title
                mediaRepo.findMatchingSubtitles(videoUri, fileName).map { it.uri }
            } catch (e: Exception) {
                emptyList()
            }

            // Load preferences
            val scale = prefs.subtitleScale.firstOrNull() ?: 100
            val fontSize = prefs.subtitleFontSize.firstOrNull() ?: 18f
            val delayMs = prefs.subtitleDelayMs.firstOrNull() ?: 0L
            val dualSub = prefs.isDualSubEnabled.firstOrNull() ?: false
            val speed = prefs.playbackSpeed.firstOrNull() ?: 1.0f
            val brightness = prefs.savedBrightness.firstOrNull() ?: -1f
            val loopEnabled = prefs.isLoopEnabled.firstOrNull() ?: false
            val skipOpEdEnabled = prefs.isSkipOpEdEnabled.firstOrNull() ?: true
            val engineMode = prefs.playerEngine.firstOrNull() ?: "auto"
            val decoderMode = prefs.decoderMode.firstOrNull() ?: "mediacodec"
            val songDetectionEnabled = prefs.isSongDetectionEnabled.firstOrNull() ?: true

            // Immediately emit loaded playback data to start player without delay
            _playerData.value = PlayerUiData(
                isLoaded = true,
                initialPositionMs = startPos,
                durationMs = savedProgress?.durationMs ?: 0L,
                externalSubtitles = matchingSubs,
                subtitleScale = scale,
                subtitleFontSize = fontSize,
                subtitleDelayMs = delayMs,
                isDualSubEnabled = dualSub,
                playbackSpeed = speed,
                savedBrightness = brightness,
                isLoopEnabled = loopEnabled,
                isSkipOpEdEnabled = skipOpEdEnabled,
                playerEngineMode = engineMode,
                decoderMode = decoderMode,
                isSongDetectionEnabled = songDetectionEnabled
            )

            // Persist last watched URI
            prefs.setLastWatchedUri(currentVideoUri)

            // Fallback: Load playlist from repository if not provided by PlaylistHolder
            if (playlist.isEmpty()) {
                val folderUri = if (!seriesFolderUri.isNullOrEmpty()) seriesFolderUri else "mediastore://folder/all"
                currentSeriesKey = folderUri
                val rawPlaylist = try {
                    mediaRepo.getEpisodesForSeries(Uri.parse(folderUri))
                } catch (e: Exception) {
                    emptyList()
                }

                val activeSort = prefs.sortOrder.firstOrNull() ?: SortOrder.NAME
                playlist = rawPlaylist.sortedByOrder(activeSort)
                if (playlist.isNotEmpty()) {
                    currentIndex = playlist.indexOfFirst { it.uri == currentVideoUri }
                    if (currentIndex == -1) {
                        currentIndex = playlist.indexOfFirst { it.title.equals(currentTitle, ignoreCase = true) }
                    }
                }
            }
        }
    }

    fun hasNext(): Boolean {
        return currentIndex != -1 && currentIndex < playlist.size - 1
    }

    fun hasPrevious(): Boolean {
        return currentIndex > 0
    }

    fun getNextEpisode(): Episode? {
        if (hasNext()) {
            return playlist[currentIndex + 1]
        }
        return null
    }

    fun getPreviousEpisode(): Episode? {
        if (hasPrevious()) {
            return playlist[currentIndex - 1]
        }
        return null
    }

    fun getCurrentEpisode(): Episode? {
        if (currentIndex in playlist.indices) {
            return playlist[currentIndex]
        }
        if (currentVideoUri.isNotEmpty()) {
            return playlist.firstOrNull { it.uri == currentVideoUri }
        }
        return null
    }

    suspend fun deleteCurrentVideo(): FileOpResult {
        val ep = getCurrentEpisode() ?: return FileOpResult.Error("Video tidak ditemukan")
        val res = mediaRepo.deleteVideo(ep)
        if (res is FileOpResult.Success) {
            PlaylistHolder.recordDeletedUri(ep.uri)
        }
        return res
    }

    fun removeCurrentEpisodeAndGetNext(): Episode? {
        val currentUri = currentVideoUri
        PlaylistHolder.recordDeletedUri(currentUri)
        viewModelScope.launch {
            mediaRepo.deleteProgress(currentUri)
        }

        val nextEp = getNextEpisode()
        val prevEp = getPreviousEpisode()

        playlist = playlist.filter { it.uri != currentUri }

        val targetEpisode = nextEp ?: prevEp
        if (targetEpisode != null) {
            currentIndex = playlist.indexOfFirst { it.uri == targetEpisode.uri }
            PlaylistHolder.lastPlayedUri = targetEpisode.uri
            viewModelScope.launch {
                prefs.setLastWatchedUri(targetEpisode.uri)
            }
        } else {
            currentIndex = -1
            PlaylistHolder.lastPlayedUri = null
            viewModelScope.launch {
                prefs.setLastWatchedUri(null)
            }
        }
        return targetEpisode
    }

    fun switchToEpisode(
        episode: Episode,
        onReady: ((startPos: Long, durationMs: Long, externalSubtitles: List<Uri>) -> Unit)? = null
    ) {
        currentVideoUri = episode.uri
        currentTitle = episode.title
        PlaylistHolder.lastPlayedUri = currentVideoUri
        PlaylistHolder.shouldScrollToLastPlayed = true
        currentIndex = playlist.indexOfFirst { it.uri == currentVideoUri }
        if (currentIndex == -1) {
            currentIndex = playlist.indexOfFirst { it.title.equals(currentTitle, ignoreCase = true) }
        }

        viewModelScope.launch {
            // Persist last watched URI
            prefs.setLastWatchedUri(currentVideoUri)

            // Check resume position for this episode
            val savedProgress = playbackRepo.getProgress(currentVideoUri)
            val effectiveDur = savedProgress?.durationMs ?: episode.durationMs
            val startPos = if (savedProgress != null && !savedProgress.isCompleted && effectiveDur >= 60_000L) {
                savedProgress.lastPositionMs
            } else 0L

            // Auto-detect matching external subtitles for this episode
            val epUri = Uri.parse(episode.uri)
            val matchingSubs = try {
                val fileName = episode.fileName.ifEmpty { epUri.lastPathSegment ?: episode.title }
                mediaRepo.findMatchingSubtitles(epUri, fileName).map { it.uri }
            } catch (e: Exception) {
                emptyList()
            }

            _playerData.value = _playerData.value.copy(
                isLoaded = true,
                initialPositionMs = startPos,
                durationMs = effectiveDur,
                externalSubtitles = matchingSubs
            )

            onReady?.invoke(startPos, effectiveDur, matchingSubs)
        }
    }

    fun saveProgress(positionMs: Long, durationMs: Long) {
        if (currentVideoUri.isBlank() || (durationMs in 1 until 60_000L)) return
        viewModelScope.launch {
            playbackRepo.saveProgress(
                videoUri = currentVideoUri,
                title = currentTitle,
                seriesFolderUri = currentSeriesKey,
                lastPositionMs = positionMs,
                durationMs = durationMs
            )
        }
    }

    fun updateSubtitlePreferences(
        scalePercent: Int = _playerData.value.subtitleScale,
        fontSize: Float = (scalePercent / 100f) * 18f,
        delayMs: Long = _playerData.value.subtitleDelayMs,
        dualSub: Boolean = _playerData.value.isDualSubEnabled
    ) {
        viewModelScope.launch {
            prefs.setSubtitleScale(scalePercent)
            prefs.setSubtitleFontSize(fontSize)
            prefs.setSubtitleDelayMs(delayMs)
            prefs.setDualSubEnabled(dualSub)

            _playerData.value = _playerData.value.copy(
                subtitleScale = scalePercent,
                subtitleFontSize = fontSize,
                subtitleDelayMs = delayMs,
                isDualSubEnabled = dualSub
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
            prefs.setSavedBrightness(brightness)
            _playerData.value = _playerData.value.copy(savedBrightness = brightness)
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

    fun savePlayerEngine(engine: String) {
        viewModelScope.launch {
            prefs.setPlayerEngine(engine)
            _playerData.value = _playerData.value.copy(playerEngineMode = engine)
        }
    }

    fun saveDecoderMode(mode: String) {
        viewModelScope.launch {
            prefs.setDecoderMode(mode)
            _playerData.value = _playerData.value.copy(decoderMode = mode)
        }
    }

    fun saveSongDetectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setSongDetectionEnabled(enabled)
            _playerData.value = _playerData.value.copy(isSongDetectionEnabled = enabled)
        }
    }
}
