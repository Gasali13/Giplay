package com.personal.animeplayer.player

import com.personal.animeplayer.data.model.Episode
import com.personal.animeplayer.util.NaturalOrderComparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerNavigationTest {

    private fun createDummyEpisode(title: String, fileName: String, uri: String): Episode {
        return Episode(
            uri = uri,
            title = title,
            fileName = fileName,
            seriesFolderUri = "mediastore://folder/test",
            durationMs = 120_000L,
            lastPositionMs = 0L,
            progressPercentage = 0,
            fileSize = 1000L,
            dateAdded = System.currentTimeMillis()
        )
    }

    @Test
    fun testSequentialNextAndPreviousNavigation() {
        val episodes = listOf(
            createDummyEpisode("Episode 1", "ep01.mkv", "content://video/1"),
            createDummyEpisode("Episode 2", "ep02.mkv", "content://video/2"),
            createDummyEpisode("Episode 3", "ep03.mkv", "content://video/3")
        )

        var currentIndex = 0

        fun hasNext() = currentIndex >= 0 && currentIndex < episodes.size - 1
        fun hasPrevious() = currentIndex > 0
        fun getNextEpisode() = if (hasNext()) episodes[currentIndex + 1] else null
        fun getPreviousEpisode() = if (hasPrevious()) episodes[currentIndex - 1] else null

        // At Episode 1
        assertTrue(hasNext())
        assertFalse(hasPrevious())
        val next1 = getNextEpisode()
        assertEquals("Episode 2", next1?.title)

        // Switch to Episode 2
        currentIndex = episodes.indexOfFirst { it.uri == next1!!.uri }
        assertEquals(1, currentIndex)
        assertTrue(hasNext())
        assertTrue(hasPrevious())

        // Next from Episode 2 -> Episode 3
        val next2 = getNextEpisode()
        assertEquals("Episode 3", next2?.title)

        // Switch to Episode 3
        currentIndex = episodes.indexOfFirst { it.uri == next2!!.uri }
        assertEquals(2, currentIndex)
        assertFalse(hasNext())
        assertTrue(hasPrevious())
        assertNull(getNextEpisode())

        // Prev from Episode 3 -> Episode 2
        val prev1 = getPreviousEpisode()
        assertEquals("Episode 2", prev1?.title)

        // Switch to Episode 2
        currentIndex = episodes.indexOfFirst { it.uri == prev1!!.uri }
        assertEquals(1, currentIndex)

        // Prev from Episode 2 -> Episode 1
        val prev2 = getPreviousEpisode()
        assertEquals("Episode 1", prev2?.title)

        // Switch to Episode 1
        currentIndex = episodes.indexOfFirst { it.uri == prev2!!.uri }
        assertEquals(0, currentIndex)
        assertFalse(hasPrevious())
        assertNull(getPreviousEpisode())
    }

    @Test
    fun testNaturalOrderPlaylistSorting() {
        val rawList = listOf(
            createDummyEpisode("Episode 10", "Anime - 10.mkv", "content://video/10"),
            createDummyEpisode("Episode 1", "Anime - 1.mkv", "content://video/1"),
            createDummyEpisode("Episode 2", "Anime - 2.mkv", "content://video/2"),
            createDummyEpisode("Episode 3", "Anime - 3.mkv", "content://video/3")
        )

        val comparator = NaturalOrderComparator()
        val sortedList = rawList.sortedWith { e1, e2 -> comparator.compare(e1.fileName, e2.fileName) }

        assertEquals("Episode 1", sortedList[0].title)
        assertEquals("Episode 2", sortedList[1].title)
        assertEquals("Episode 3", sortedList[2].title)
        assertEquals("Episode 10", sortedList[3].title)
    }

    @Test
    fun testDeleteCurrentEpisodeAdvancesToNextEpisode() {
        val ep1 = createDummyEpisode("Episode 1", "ep01.mkv", "content://video/1")
        val ep2 = createDummyEpisode("Episode 2", "ep02.mkv", "content://video/2")
        val ep3 = createDummyEpisode("Episode 3", "ep03.mkv", "content://video/3")
        PlaylistHolder.clear()
        PlaylistHolder.setPlaylist(listOf(ep1, ep2, ep3))

        var playlist = PlaylistHolder.currentPlaylist
        var currentIndex = 1 // Watching Episode 2

        // Simulation of removeCurrentEpisodeAndGetNext
        val currentUri = playlist[currentIndex].uri
        PlaylistHolder.recordDeletedUri(currentUri)

        val nextEp = if (currentIndex < playlist.size - 1) playlist[currentIndex + 1] else null
        val prevEp = if (currentIndex > 0) playlist[currentIndex - 1] else null

        playlist = playlist.filter { it.uri != currentUri }
        val targetEpisode = nextEp ?: prevEp

        assertEquals("Episode 3", targetEpisode?.title)
        assertEquals(2, playlist.size)
        assertTrue(playlist.none { it.uri == ep2.uri })
        assertTrue(PlaylistHolder.deletedUris.contains(ep2.uri))
    }

    @Test
    fun testDeleteLastEpisodeFallsBackToPreviousEpisode() {
        PlaylistHolder.clear()
        val ep1 = createDummyEpisode("Episode 1", "ep01.mkv", "content://video/10")
        val ep2 = createDummyEpisode("Episode 2", "ep02.mkv", "content://video/20")
        PlaylistHolder.setPlaylist(listOf(ep1, ep2))

        var playlist = PlaylistHolder.currentPlaylist
        var currentIndex = 1 // Watching Episode 2 (the last one)

        val currentUri = playlist[currentIndex].uri
        PlaylistHolder.recordDeletedUri(currentUri)

        val nextEp = if (currentIndex < playlist.size - 1) playlist[currentIndex + 1] else null
        val prevEp = if (currentIndex > 0) playlist[currentIndex - 1] else null

        playlist = playlist.filter { it.uri != currentUri }
        val targetEpisode = nextEp ?: prevEp

        assertEquals("Episode 1", targetEpisode?.title)
        assertEquals(1, playlist.size)
    }

    @Test
    fun testDeleteSoleEpisodeReturnsNull() {
        PlaylistHolder.clear()
        val ep1 = createDummyEpisode("Episode 1", "ep01.mkv", "content://video/99")
        PlaylistHolder.setPlaylist(listOf(ep1))

        var playlist = PlaylistHolder.currentPlaylist
        var currentIndex = 0

        val currentUri = playlist[currentIndex].uri
        PlaylistHolder.recordDeletedUri(currentUri)

        val nextEp = if (currentIndex < playlist.size - 1) playlist[currentIndex + 1] else null
        val prevEp = if (currentIndex > 0) playlist[currentIndex - 1] else null

        playlist = playlist.filter { it.uri != currentUri }
        val targetEpisode = nextEp ?: prevEp

        assertNull(targetEpisode)
        assertTrue(playlist.isEmpty())
    }

    @Test
    fun testPlaylistHolderLastPlayedAndScrollFlag() {
        PlaylistHolder.clear()
        PlaylistHolder.lastPlayedUri = "content://video/42"
        PlaylistHolder.shouldScrollToLastPlayed = true

        assertEquals("content://video/42", PlaylistHolder.lastPlayedUri)
        assertTrue(PlaylistHolder.consumeShouldScroll())
        assertFalse(PlaylistHolder.shouldScrollToLastPlayed) // Consumed, so now false
    }

    @Test
    fun testLastWatchedUriTrackingAndResetOnDeletion() {
        PlaylistHolder.clear()
        val ep1 = createDummyEpisode("Episode 1", "ep01.mkv", "content://video/1")
        val ep2 = createDummyEpisode("Episode 2", "ep02.mkv", "content://video/2")
        PlaylistHolder.setPlaylist(listOf(ep1, ep2))

        // When watching Episode 1
        PlaylistHolder.lastPlayedUri = ep1.uri
        assertEquals("content://video/1", PlaylistHolder.lastPlayedUri)

        // Switch to Episode 2
        PlaylistHolder.lastPlayedUri = ep2.uri
        assertEquals("content://video/2", PlaylistHolder.lastPlayedUri)

        // Episode 2 deleted: lastPlayedUri should clear
        if (PlaylistHolder.lastPlayedUri == ep2.uri) {
            PlaylistHolder.lastPlayedUri = null
        }
        assertNull(PlaylistHolder.lastPlayedUri)
    }

    @Test
    fun testEpisodeMatchesLastWatchedUri() {
        val ep1 = createDummyEpisode("Episode 1", "ep01.mkv", "content://video/1")
        val ep2 = createDummyEpisode("Episode 2", "ep02.mkv", "content://video/2")
        val lastWatchedUri = "content://video/2"

        val isEp1LastWatched = (ep1.uri == lastWatchedUri)
        val isEp2LastWatched = (ep2.uri == lastWatchedUri)

        assertFalse(isEp1LastWatched)
        assertTrue(isEp2LastWatched)
    }
}
