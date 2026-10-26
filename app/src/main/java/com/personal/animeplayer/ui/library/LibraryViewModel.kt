package com.personal.animeplayer.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.animeplayer.data.datastore.AppPreferences
import com.personal.animeplayer.data.model.AnimeSeries
import com.personal.animeplayer.data.model.Episode
import com.personal.animeplayer.data.model.FileOpResult
import com.personal.animeplayer.data.model.MediaScanResult
import com.personal.animeplayer.data.model.SortOrder
import com.personal.animeplayer.data.model.VideoDetailInfo
import com.personal.animeplayer.data.repository.MediaRepository
import com.personal.animeplayer.player.PlaylistHolder
import com.personal.animeplayer.util.sortedByOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

enum class LibraryTab {
    ALL_VIDEOS,
    FOLDERS
}

data class LibraryState(
    val isLoading: Boolean = true,
    val hasScanCompleted: Boolean = false,
    val currentTab: LibraryTab = LibraryTab.ALL_VIDEOS,
    val sortOrder: SortOrder = SortOrder.NAME,
    val allVideos: List<Episode> = emptyList(),
    val filteredVideos: List<Episode> = emptyList(),
    val folders: List<AnimeSeries> = emptyList(),
    val filteredFolders: List<AnimeSeries> = emptyList(),
    val searchQuery: String = "",
    val errorMessage: String? = null
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaRepository(application)
    private val appPreferences = AppPreferences(application)

    private val _state = MutableStateFlow(LibraryState(isLoading = true))
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    val lastWatchedUri = appPreferences.lastWatchedUri

    private var currentScanResult = MediaScanResult()

    init {
        viewModelScope.launch {
            appPreferences.sortOrder.collect { order ->
                _state.value = _state.value.copy(sortOrder = order)
                if (_state.value.hasScanCompleted) {
                    applyFilterAndPublish(hasCompleted = true)
                }
            }
        }
        checkSavedFolder()
    }

    fun checkSavedFolder() {
        viewModelScope.launch {
            val savedUri = appPreferences.rootFolderUri.firstOrNull()
            if (!savedUri.isNullOrEmpty()) {
                loadLibrary(Uri.parse(savedUri))
            } else {
                loadAllDeviceVideos()
            }
        }
    }

    fun refreshLibrary() {
        checkSavedFolder()
    }

    fun setTab(tab: LibraryTab) {
        _state.value = _state.value.copy(currentTab = tab)
    }

    fun loadAllDeviceVideos() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                val scanResult = repository.scanAllDeviceVideos()
                currentScanResult = scanResult
                applyFilterAndPublish(hasCompleted = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    hasScanCompleted = true,
                    errorMessage = e.localizedMessage ?: "Gagal memindai video perangkat."
                )
            }
        }
    }

    fun onFolderSelected(uri: Uri) {
        viewModelScope.launch {
            appPreferences.setRootFolderUri(uri.toString())
            loadLibrary(uri)
        }
    }

    fun loadLibrary(folderUri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                val scanResult = repository.scanAnimeLibrary(folderUri)
                currentScanResult = scanResult
                applyFilterAndPublish(hasCompleted = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    hasScanCompleted = true,
                    errorMessage = e.localizedMessage ?: "Gagal memindai folder."
                )
            }
        }
    }

    fun refreshWatchProgress() {
        viewModelScope.launch {
            if (currentScanResult.allVideos.isNotEmpty()) {
                val deleted = PlaylistHolder.deletedUris
                val existing = if (deleted.isNotEmpty()) {
                    currentScanResult.allVideos.filter { it.uri !in deleted }
                } else {
                    currentScanResult.allVideos
                }
                val refreshed = repository.refreshProgress(existing)
                currentScanResult = currentScanResult.copy(allVideos = refreshed)
                applyFilterAndPublish()
            }
        }
    }

    fun search(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        applyFilterAndPublish()
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch {
            appPreferences.setSortOrder(order)
        }
    }

    suspend fun getVideoDetails(episode: Episode): VideoDetailInfo {
        return repository.getVideoDetails(episode)
    }

    suspend fun deleteVideo(episode: Episode): FileOpResult {
        val result = repository.deleteVideo(episode)
        if (result is FileOpResult.Success) {
            onVideosDeleted(listOf(episode.uri))
        }
        return result
    }

    suspend fun deleteVideos(episodes: List<Episode>): FileOpResult {
        val result = repository.deleteVideos(episodes)
        if (result is FileOpResult.Success) {
            onVideosDeleted(episodes.map { it.uri })
        }
        return result
    }

    fun onVideosDeleted(uris: Collection<String>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            repository.deleteProgressList(uris)
        }
        uris.forEach { uri ->
            PlaylistHolder.recordDeletedUri(uri)
            if (PlaylistHolder.lastPlayedUri == uri) {
                PlaylistHolder.lastPlayedUri = null
                viewModelScope.launch { appPreferences.setLastWatchedUri(null) }
            }
        }
        val uriSet = uris.toSet()
        val updatedVideos = currentScanResult.allVideos.filter { it.uri !in uriSet }
        val updatedFolders = currentScanResult.folders.mapNotNull { folder ->
            val remainingInFolder = updatedVideos.count { it.seriesFolderUri == folder.folderUri }
            if (remainingInFolder == 0) null
            else folder.copy(
                episodeCount = remainingInFolder,
                firstEpisodeUri = updatedVideos.firstOrNull { it.seriesFolderUri == folder.folderUri }?.uri
            )
        }
        currentScanResult = currentScanResult.copy(allVideos = updatedVideos, folders = updatedFolders)
        applyFilterAndPublish()
    }

    suspend fun renameVideo(episode: Episode, newName: String): FileOpResult {
        val result = repository.renameVideo(episode, newName)
        if (result is FileOpResult.Success) {
            val ext = episode.fileName.substringAfterLast('.', "")
            val cleanName = if (ext.isNotBlank() && newName.endsWith(".$ext", ignoreCase = true)) {
                newName.dropLast(ext.length + 1).trim()
            } else {
                newName.trim()
            }
            val fullNewFileName = if (ext.isNotBlank()) "$cleanName.$ext" else cleanName
            val updatedVideos = currentScanResult.allVideos.map {
                if (it.uri == episode.uri) {
                    it.copy(title = cleanName, fileName = fullNewFileName)
                } else {
                    it
                }
            }
            currentScanResult = currentScanResult.copy(allVideos = updatedVideos)
            applyFilterAndPublish()
            checkSavedFolder()
        }
        return result
    }

    private fun applyFilterAndPublish(hasCompleted: Boolean = _state.value.hasScanCompleted) {
        val query = _state.value.searchQuery.trim()
        val order = _state.value.sortOrder

        val filteredVideos = if (query.isBlank()) {
            currentScanResult.allVideos
        } else {
            currentScanResult.allVideos.filter {
                it.title.contains(query, ignoreCase = true) || it.fileName.contains(query, ignoreCase = true)
            }
        }

        val filteredFolders = if (query.isBlank()) {
            currentScanResult.folders
        } else {
            currentScanResult.folders.filter {
                it.name.contains(query, ignoreCase = true)
            }
        }

        // Apply sort order
        val sortedVideos = filteredVideos.sortedByOrder(order)

        val naturalComp = com.personal.animeplayer.util.NaturalOrderComparator()
        val sortedFolders = when (order) {
            SortOrder.NAME -> filteredFolders.sortedWith { f1, f2 -> naturalComp.compare(f1.name, f2.name) }
            SortOrder.NEWEST -> filteredFolders.sortedByDescending { it.lastModified }
            SortOrder.OLDEST -> filteredFolders.sortedBy { it.lastModified }
        }

        _state.value = _state.value.copy(
            isLoading = !hasCompleted,
            hasScanCompleted = hasCompleted,
            allVideos = currentScanResult.allVideos,
            filteredVideos = sortedVideos,
            folders = currentScanResult.folders,
            filteredFolders = sortedFolders,
            errorMessage = null
        )
    }
}
