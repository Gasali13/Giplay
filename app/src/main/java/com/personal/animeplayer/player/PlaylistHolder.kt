package com.personal.animeplayer.player

import com.personal.animeplayer.data.model.Episode

/**
 * In-memory holder for the currently active playlist being browsed by the user.
 * This guarantees that PlayerActivity plays videos in the exact same order
 * that was displayed on screen in EpisodeListActivity or LibraryActivity,
 * including any active search filters or custom sorting.
 */
object PlaylistHolder {
    @Volatile
    private var _currentPlaylist: List<Episode> = emptyList()

    @Volatile
    private var _currentSeriesKey: String? = null

    @Volatile
    var lastPlayedUri: String? = null

    @Volatile
    var shouldScrollToLastPlayed: Boolean = false

    private val _deletedUris = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    val deletedUris: Set<String>
        get() = _deletedUris

    val currentPlaylist: List<Episode>
        get() = _currentPlaylist

    val currentSeriesKey: String?
        get() = _currentSeriesKey

    fun setPlaylist(episodes: List<Episode>, seriesKey: String? = null) {
        _currentPlaylist = episodes.filter { it.uri !in _deletedUris }
        _currentSeriesKey = seriesKey
    }

    fun recordDeletedUri(uri: String) {
        _deletedUris.add(uri)
        _currentPlaylist = _currentPlaylist.filter { it.uri != uri }
    }

    fun consumeShouldScroll(): Boolean {
        val should = shouldScrollToLastPlayed
        shouldScrollToLastPlayed = false
        return should
    }

    fun clear() {
        _currentPlaylist = emptyList()
        _currentSeriesKey = null
        _deletedUris.clear()
        lastPlayedUri = null
        shouldScrollToLastPlayed = false
    }
}
