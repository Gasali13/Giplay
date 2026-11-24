package com.personal.animeplayer.player

import com.personal.animeplayer.data.model.Episode

/**
 * Pure playlist state & navigation helper for [com.personal.animeplayer.ui.player.PlayerViewModel],
 * allowing sequential navigation and deletion fallback to be unit-tested directly.
 */
class PlaylistNavigator {

    var playlist: List<Episode> = emptyList()
        private set

    var currentIndex: Int = -1
        private set

    var currentVideoUri: String = ""
        private set

    var currentTitle: String = ""
        private set

    fun setCurrentMedia(uri: String, title: String) {
        currentVideoUri = uri
        currentTitle = title
        resolveCurrentIndex()
    }

    fun setPlaylist(episodes: List<Episode>) {
        playlist = episodes
        resolveCurrentIndex()
    }

    fun switchToEpisode(episode: Episode) {
        currentVideoUri = episode.uri
        currentTitle = episode.title
        resolveCurrentIndex()
    }

    fun hasNext(): Boolean = currentIndex != -1 && currentIndex < playlist.size - 1

    fun hasPrevious(): Boolean = currentIndex > 0

    fun getNextEpisode(): Episode? = if (hasNext()) playlist[currentIndex + 1] else null

    fun getPreviousEpisode(): Episode? = if (hasPrevious()) playlist[currentIndex - 1] else null

    fun getCurrentEpisode(): Episode? {
        if (currentIndex in playlist.indices) {
            return playlist[currentIndex]
        }
        if (currentVideoUri.isNotEmpty()) {
            return playlist.firstOrNull { it.uri == currentVideoUri }
        }
        return null
    }

    fun removeCurrentAndGetNext(): Episode? {
        val removedUri = currentVideoUri
        val nextEp = getNextEpisode()
        val prevEp = getPreviousEpisode()

        playlist = playlist.filter { it.uri != removedUri }

        val targetEpisode = nextEp ?: prevEp
        if (targetEpisode != null) {
            currentVideoUri = targetEpisode.uri
            currentTitle = targetEpisode.title
            resolveCurrentIndex()
        } else {
            currentIndex = -1
            currentVideoUri = ""
            currentTitle = ""
        }
        return targetEpisode
    }

    private fun resolveCurrentIndex() {
        if (playlist.isEmpty() || currentVideoUri.isEmpty()) {
            currentIndex = -1
            return
        }
        currentIndex = playlist.indexOfFirst { it.uri == currentVideoUri }
        if (currentIndex == -1 && currentTitle.isNotEmpty()) {
            currentIndex = playlist.indexOfFirst { it.title.equals(currentTitle, ignoreCase = true) }
        }
    }
}
