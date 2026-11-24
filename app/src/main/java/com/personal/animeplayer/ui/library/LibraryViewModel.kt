package com.personal.animeplayer.ui.library

import android.app.Application
import androidx.compose.runtime.Immutable
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
import com.personal.animeplayer.ui.compose.components.VideoThumbnailLoader
import com.personal.animeplayer.util.NaturalOrderComparator
import com.personal.animeplayer.util.sortedByOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class LibraryTab {
    ALL_VIDEOS,
    FOLDERS
}

@Immutable
data class LibraryState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val hasScanCompleted: Boolean = false,
    val startupProgress: Float = 0f,
    val startupStatus: String = "Menyiapkan pustaka…",
    val isStartupReady: Boolean = false,
    val currentTab: LibraryTab = LibraryTab.ALL_VIDEOS,
    val sortOrder: SortOrder = SortOrder.NAME,
    val filteredVideos: List<Episode> = emptyList(),
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
    private var isScanInProgress = false
    private var filterJob: Job? = null
    private val naturalOrderComparator = NaturalOrderComparator()

    init {
        viewModelScope.launch {
            appPreferences.sortOrder.collect { order ->
                _state.update { it.copy(sortOrder = order) }
                if (_state.value.hasScanCompleted) {
                    applyFilterAndPublish(hasCompleted = true)
                }
            }
        }
    }

    fun ensureInitialLoad() {
        if (!_state.value.hasScanCompleted && !isScanInProgress) {
            loadAllDeviceVideos()
        }
    }

    fun refreshLibrary() {
        loadAllDeviceVideos(isRefresh = true)
    }

    fun setTab(tab: LibraryTab) {
        if (_state.value.currentTab == tab) return
        _state.value = _state.value.copy(currentTab = tab)
    }

    fun markPermissionDenied() {
        _state.update {
            it.copy(
                isLoading = false,
                isRefreshing = false,
                hasScanCompleted = true,
                startupProgress = 1f,
                startupStatus = "",
                isStartupReady = true
            )
        }
    }

    fun loadAllDeviceVideos(isRefresh: Boolean = false) {
        if (isScanInProgress) return
        isScanInProgress = true
        viewModelScope.launch {
            runScanWithStartupStages(isRefresh = isRefresh)
        }
    }

    private suspend fun runScanWithStartupStages(isRefresh: Boolean) {
        val isInitialStartup = !isRefresh && !_state.value.isStartupReady
        _state.update {
            it.copy(
                isLoading = !(isRefresh && it.hasScanCompleted),
                isRefreshing = isRefresh,
                startupProgress = if (isInitialStartup) 0.14f else it.startupProgress,
                startupStatus = if (isInitialStartup) "Memindai video perangkat…" else it.startupStatus,
                errorMessage = null
            )
        }
        try {
            val scanResult = repository.scanAllDeviceVideos()
            currentScanResult = scanResult

            if (isInitialStartup) {
                _state.update {
                    it.copy(
                        startupProgress = 0.36f,
                        startupStatus = "Mengurutkan daftar & folder…"
                    )
                }
            }

            val snapshot = _state.value
            val (sortedVideos, sortedFolders) = withContext(Dispatchers.Default) {
                computeFilteredAndSorted(
                    scanResult = scanResult,
                    query = snapshot.searchQuery.trim(),
                    order = snapshot.sortOrder
                )
            }

            filterJob?.cancel()
            _state.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    hasScanCompleted = true,
                    filteredVideos = sortedVideos,
                    filteredFolders = sortedFolders,
                    startupProgress = if (isInitialStartup) 0.45f else it.startupProgress,
                    startupStatus = if (isInitialStartup) "Memuat pratinjau video…" else it.startupStatus,
                    errorMessage = null
                )
            }

            if (isInitialStartup) {
                if (sortedVideos.isNotEmpty()) {
                    withTimeoutOrNull(1800L) {
                        VideoThumbnailLoader.preload(
                            context = getApplication(),
                            episodes = sortedVideos,
                            maxCount = 16
                        ) { loaded, total ->
                            val frac = 0.45f + 0.47f * (loaded.toFloat() / total.coerceAtLeast(1).toFloat())
                            val clamped = frac.coerceIn(0.45f, 0.92f)
                            _state.update { curr ->
                                if (clamped > curr.startupProgress) {
                                    curr.copy(startupProgress = clamped)
                                } else {
                                    curr
                                }
                            }
                        }
                    }
                }
                _state.update {
                    it.copy(
                        startupProgress = 0.95f,
                        startupStatus = "Menyiapkan tampilan…"
                    )
                }
                delay(90L)
                _state.update {
                    it.copy(
                        startupProgress = 1f,
                        startupStatus = "Siap",
                        isStartupReady = true
                    )
                }
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    hasScanCompleted = true,
                    startupProgress = 1f,
                    isStartupReady = true,
                    errorMessage = e.localizedMessage ?: "Gagal memindai video perangkat."
                )
            }
        } finally {
            isScanInProgress = false
            _state.update { curr ->
                if (curr.isRefreshing) curr.copy(isRefreshing = false) else curr
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
        _state.update { it.copy(searchQuery = query) }
        applyFilterAndPublish()
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch {
            appPreferences.setSortOrder(order)
        }
    }

    fun clearErrorMessage() {
        if (_state.value.errorMessage != null) {
            _state.value = _state.value.copy(errorMessage = null)
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
        val appContext = getApplication<Application>()
        viewModelScope.launch {
            repository.deleteProgressList(uris)
            VideoThumbnailLoader.evict(appContext, uris)
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
            else folder.copy(episodeCount = remainingInFolder)
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
            loadAllDeviceVideos(isRefresh = true)
        }
        return result
    }

    private fun computeFilteredAndSorted(
        scanResult: MediaScanResult,
        query: String,
        order: SortOrder
    ): Pair<List<Episode>, List<AnimeSeries>> {
        val filteredVideos = if (query.isBlank()) {
            scanResult.allVideos
        } else {
            scanResult.allVideos.filter {
                it.title.contains(query, ignoreCase = true) || it.fileName.contains(query, ignoreCase = true)
            }
        }

        val filteredFolders = if (query.isBlank()) {
            scanResult.folders
        } else {
            scanResult.folders.filter {
                it.name.contains(query, ignoreCase = true)
            }
        }

        val sortedVideos = filteredVideos.sortedByOrder(order)

        val sortedFolders = when (order) {
            SortOrder.NAME -> filteredFolders.sortedWith { f1, f2 -> naturalOrderComparator.compare(f1.name, f2.name) }
            SortOrder.NEWEST -> filteredFolders.sortedByDescending { it.lastModified }
            SortOrder.OLDEST -> filteredFolders.sortedBy { it.lastModified }
        }

        return sortedVideos to sortedFolders
    }

    private fun applyFilterAndPublish(hasCompleted: Boolean = _state.value.hasScanCompleted) {
        val scanSnapshot = currentScanResult
        val query = _state.value.searchQuery.trim()
        val order = _state.value.sortOrder
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            val (sortedVideos, sortedFolders) = withContext(Dispatchers.Default) {
                computeFilteredAndSorted(
                    scanResult = scanSnapshot,
                    query = query,
                    order = order
                )
            }
            _state.update {
                it.copy(
                    isLoading = !hasCompleted,
                    isRefreshing = false,
                    hasScanCompleted = hasCompleted,
                    filteredVideos = sortedVideos,
                    filteredFolders = sortedFolders,
                    errorMessage = null
                )
            }
        }
    }
}
