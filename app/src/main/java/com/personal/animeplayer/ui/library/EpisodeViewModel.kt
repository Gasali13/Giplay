package com.personal.animeplayer.ui.library

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.animeplayer.data.datastore.AppPreferences
import com.personal.animeplayer.data.model.Episode
import com.personal.animeplayer.data.model.SortOrder
import com.personal.animeplayer.data.repository.MediaRepository
import com.personal.animeplayer.player.PlaylistHolder
import com.personal.animeplayer.ui.compose.components.VideoThumbnailLoader
import com.personal.animeplayer.util.sortedByOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
sealed class EpisodeUiState {
    @Immutable
    object Loading : EpisodeUiState()

    @Immutable
    data class Success(val episodes: List<Episode>) : EpisodeUiState()

    @Immutable
    data class Empty(val message: String) : EpisodeUiState()

    @Immutable
    data class Error(val error: String) : EpisodeUiState()
}

class EpisodeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaRepository(application)
    private val prefs = AppPreferences(application)

    private val _uiState = MutableStateFlow<EpisodeUiState>(EpisodeUiState.Loading)
    val uiState: StateFlow<EpisodeUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val lastWatchedUri = prefs.lastWatchedUri
    val sortOrderFlow = prefs.sortOrder

    private var allEpisodes: List<Episode> = emptyList()
    private var currentSortOrder: SortOrder = SortOrder.NAME
    private var currentSearchQuery: String = ""
    private var filterJob: Job? = null

    val sortOrder: SortOrder
        get() = currentSortOrder

    init {
        viewModelScope.launch {
            prefs.sortOrder.collect { order ->
                currentSortOrder = order
                if (allEpisodes.isNotEmpty()) {
                    applyFilterAndPublish()
                }
            }
        }
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch {
            prefs.setSortOrder(order)
        }
    }

    fun loadEpisodes(seriesFolderUri: Uri, isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) {
                _isRefreshing.value = true
            } else if (allEpisodes.isEmpty()) {
                _uiState.value = EpisodeUiState.Loading
            }
            try {
                val list = repository.getEpisodesForSeries(seriesFolderUri)
                val deleted = PlaylistHolder.deletedUris
                allEpisodes = if (deleted.isNotEmpty()) list.filter { it.uri !in deleted } else list
                applyFilterAndPublish()
            } catch (e: Exception) {
                if (allEpisodes.isEmpty()) {
                    _uiState.value = EpisodeUiState.Error(e.localizedMessage ?: "Gagal memuat episode.")
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun searchEpisodes(query: String) {
        currentSearchQuery = query.trim()
        applyFilterAndPublish()
    }

    private fun applyFilterAndPublish() {
        val snapshot = allEpisodes
        val query = currentSearchQuery
        val order = currentSortOrder
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            val nextState = withContext(Dispatchers.Default) {
                val deleted = PlaylistHolder.deletedUris
                val validEpisodes = if (deleted.isNotEmpty()) snapshot.filter { it.uri !in deleted } else snapshot
                if (validEpisodes.isEmpty()) {
                    return@withContext EpisodeUiState.Empty("Tidak ada episode di folder ini.")
                }

                val filtered = if (query.isBlank()) {
                    validEpisodes
                } else {
                    validEpisodes.filter {
                        it.title.contains(query, ignoreCase = true) ||
                            it.fileName.contains(query, ignoreCase = true)
                    }
                }

                if (filtered.isEmpty()) {
                    EpisodeUiState.Empty("Tidak ada episode yang cocok.")
                } else {
                    EpisodeUiState.Success(filtered.sortedByOrder(order))
                }
            }
            _uiState.value = nextState
        }
    }

    suspend fun getVideoDetails(episode: Episode): com.personal.animeplayer.data.model.VideoDetailInfo {
        return repository.getVideoDetails(episode)
    }

    suspend fun deleteVideo(episode: Episode): com.personal.animeplayer.data.model.FileOpResult {
        val result = repository.deleteVideo(episode)
        if (result is com.personal.animeplayer.data.model.FileOpResult.Success) {
            onVideosDeleted(listOf(episode.uri))
        }
        return result
    }

    suspend fun deleteVideos(episodes: List<Episode>): com.personal.animeplayer.data.model.FileOpResult {
        val result = repository.deleteVideos(episodes)
        if (result is com.personal.animeplayer.data.model.FileOpResult.Success) {
            onVideosDeleted(episodes.map { it.uri })
        }
        return result
    }

    fun onVideosDeleted(uris: Collection<String>) {
        if (uris.isEmpty()) return
        val appContext = getApplication<Application>()
        viewModelScope.launch {
            repository.deleteProgressList(uris)
            VideoThumbnailLoader.evict(appContext, uris)
        }
        uris.forEach { uri ->
            PlaylistHolder.recordDeletedUri(uri)
            if (PlaylistHolder.lastPlayedUri == uri) {
                PlaylistHolder.lastPlayedUri = null
                viewModelScope.launch { prefs.setLastWatchedUri(null) }
            }
        }
        val uriSet = uris.toSet()
        allEpisodes = allEpisodes.filter { it.uri !in uriSet }
        val current = (_uiState.value as? EpisodeUiState.Success)?.episodes
        if (current != null) {
            val filtered = current.filter { it.uri !in uriSet }
            if (filtered.isEmpty()) {
                _uiState.value = EpisodeUiState.Empty("Tidak ada episode di folder ini.")
            } else {
                _uiState.value = EpisodeUiState.Success(filtered)
            }
        }
    }

    suspend fun renameVideo(episode: Episode, newName: String, seriesFolderUri: Uri): com.personal.animeplayer.data.model.FileOpResult {
        val result = repository.renameVideo(episode, newName)
        if (result is com.personal.animeplayer.data.model.FileOpResult.Success) {
            val ext = episode.fileName.substringAfterLast('.', "")
            val cleanName = if (ext.isNotBlank() && newName.endsWith(".$ext", ignoreCase = true)) {
                newName.dropLast(ext.length + 1).trim()
            } else {
                newName.trim()
            }
            val fullNewFileName = if (ext.isNotBlank()) "$cleanName.$ext" else cleanName
            allEpisodes = allEpisodes.map {
                if (it.uri == episode.uri) {
                    it.copy(title = cleanName, fileName = fullNewFileName)
                } else {
                    it
                }
            }
            val current = (_uiState.value as? EpisodeUiState.Success)?.episodes
            if (current != null) {
                _uiState.value = EpisodeUiState.Success(current.map {
                    if (it.uri == episode.uri) {
                        it.copy(title = cleanName, fileName = fullNewFileName)
                    } else {
                        it
                    }
                })
            }
            loadEpisodes(seriesFolderUri)
        }
        return result
    }
}

