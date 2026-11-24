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
        val ep1 = createDummyEpisode("Episode 1", "ep01.mkv", "content://video/1")
        val ep2 = createDummyEpisode("Episode 2", "ep02.mkv", "content://video/2")
        val ep3 = createDummyEpisode("Episode 3", "ep03.mkv", "content://video/3")

        val navigator = PlaylistNavigator()
        navigator.setCurrentMedia(ep1.uri, ep1.title)
        navigator.setPlaylist(listOf(ep1, ep2, ep3))

        // At Episode 1
        assertEquals(0, navigator.currentIndex)
        assertEquals(ep1, navigator.getCurrentEpisode())
        assertTrue(navigator.hasNext())
        assertFalse(navigator.hasPrevious())
        val next1 = navigator.getNextEpisode()
        assertEquals("Episode 2", next1?.title)

        // Switch to Episode 2
        navigator.switchToEpisode(next1!!)
        assertEquals(1, navigator.currentIndex)
        assertTrue(navigator.hasNext())
        assertTrue(navigator.hasPrevious())

        // Next from Episode 2 -> Episode 3
        val next2 = navigator.getNextEpisode()
        assertEquals("Episode 3", next2?.title)

        // Switch to Episode 3
        navigator.switchToEpisode(next2!!)
        assertEquals(2, navigator.currentIndex)
        assertFalse(navigator.hasNext())
        assertTrue(navigator.hasPrevious())
        assertNull(navigator.getNextEpisode())

        // Prev from Episode 3 -> Episode 2
        val prev1 = navigator.getPreviousEpisode()
        assertEquals("Episode 2", prev1?.title)

        // Switch to Episode 2
        navigator.switchToEpisode(prev1!!)
        assertEquals(1, navigator.currentIndex)

        // Prev from Episode 2 -> Episode 1
        val prev2 = navigator.getPreviousEpisode()
        assertEquals("Episode 1", prev2?.title)

        // Switch to Episode 1
        navigator.switchToEpisode(prev2!!)
        assertEquals(0, navigator.currentIndex)
        assertFalse(navigator.hasPrevious())
        assertNull(navigator.getPreviousEpisode())
    }

    @Test
    fun testNaturalOrderPlaylistSortingAndNavigatorResolution() {
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

        val navigator = PlaylistNavigator()
        navigator.setCurrentMedia("content://video/2", "Episode 2")
        navigator.setPlaylist(sortedList)
        assertEquals(1, navigator.currentIndex)
        assertEquals("Episode 3", navigator.getNextEpisode()?.title)
    }

    @Test
    fun testTitleFallbackIndexResolutionWhenUriDiffers() {
        val ep1 = createDummyEpisode("Episode 1", "ep01.mkv", "content://video/1")
        val ep2 = createDummyEpisode("Episode 2", "ep02.mkv", "content://video/2")

        val navigator = PlaylistNavigator()
        navigator.setCurrentMedia("file:///external/ep02.mkv", "episode 2")
        navigator.setPlaylist(listOf(ep1, ep2))

        assertEquals(1, navigator.currentIndex)
        assertEquals(ep2, navigator.getCurrentEpisode())
    }

    @Test
    fun testDeleteCurrentEpisodeAdvancesToNextEpisode() {
        val ep1 = createDummyEpisode("Episode 1", "ep01.mkv", "content://video/1")
        val ep2 = createDummyEpisode("Episode 2", "ep02.mkv", "content://video/2")
        val ep3 = createDummyEpisode("Episode 3", "ep03.mkv", "content://video/3")
        PlaylistHolder.clear()
        PlaylistHolder.setPlaylist(listOf(ep1, ep2, ep3))

        val navigator = PlaylistNavigator()
        navigator.setCurrentMedia(ep2.uri, ep2.title)
        navigator.setPlaylist(PlaylistHolder.currentPlaylist)

        val removedUri = navigator.currentVideoUri
        PlaylistHolder.recordDeletedUri(removedUri)
        val targetEpisode = navigator.removeCurrentAndGetNext()

        assertEquals("Episode 3", targetEpisode?.title)
        assertEquals(2, navigator.playlist.size)
        assertEquals(1, navigator.currentIndex)
        assertEquals(ep3.uri, navigator.currentVideoUri)
        assertTrue(navigator.playlist.none { it.uri == ep2.uri })
        assertTrue(PlaylistHolder.deletedUris.contains(ep2.uri))
        assertTrue(PlaylistHolder.currentPlaylist.none { it.uri == ep2.uri })
    }

    @Test
    fun testDeleteLastEpisodeFallsBackToPreviousEpisode() {
        PlaylistHolder.clear()
        val ep1 = createDummyEpisode("Episode 1", "ep01.mkv", "content://video/10")
        val ep2 = createDummyEpisode("Episode 2", "ep02.mkv", "content://video/20")
        PlaylistHolder.setPlaylist(listOf(ep1, ep2))

        val navigator = PlaylistNavigator()
        navigator.setCurrentMedia(ep2.uri, ep2.title)
        navigator.setPlaylist(PlaylistHolder.currentPlaylist)

        val targetEpisode = navigator.removeCurrentAndGetNext()

        assertEquals("Episode 1", targetEpisode?.title)
        assertEquals(1, navigator.playlist.size)
        assertEquals(0, navigator.currentIndex)
        assertEquals(ep1.uri, navigator.currentVideoUri)
    }

    @Test
    fun testDeleteSoleEpisodeReturnsNullAndResetsState() {
        PlaylistHolder.clear()
        val ep1 = createDummyEpisode("Episode 1", "ep01.mkv", "content://video/99")
        PlaylistHolder.setPlaylist(listOf(ep1))

        val navigator = PlaylistNavigator()
        navigator.setCurrentMedia(ep1.uri, ep1.title)
        navigator.setPlaylist(PlaylistHolder.currentPlaylist)

        val targetEpisode = navigator.removeCurrentAndGetNext()

        assertNull(targetEpisode)
        assertTrue(navigator.playlist.isEmpty())
        assertEquals(-1, navigator.currentIndex)
        assertEquals("", navigator.currentVideoUri)
        assertEquals("", navigator.currentTitle)
    }

    @Test
    fun testPlaylistHolderLastPlayedAndScrollFlag() {
        PlaylistHolder.clear()
        PlaylistHolder.lastPlayedUri = "content://video/42"
        PlaylistHolder.shouldScrollToLastPlayed = true

        assertEquals("content://video/42", PlaylistHolder.lastPlayedUri)
        assertTrue(PlaylistHolder.consumeShouldScroll())
        assertFalse(PlaylistHolder.shouldScrollToLastPlayed)
    }

    @Test
    fun testLastWatchedUriTrackingAndResetOnDeletion() {
        PlaylistHolder.clear()
        val ep1 = createDummyEpisode("Episode 1", "ep01.mkv", "content://video/1")
        val ep2 = createDummyEpisode("Episode 2", "ep02.mkv", "content://video/2")
        PlaylistHolder.setPlaylist(listOf(ep1, ep2))

        PlaylistHolder.lastPlayedUri = ep1.uri
        assertEquals("content://video/1", PlaylistHolder.lastPlayedUri)

        PlaylistHolder.lastPlayedUri = ep2.uri
        assertEquals("content://video/2", PlaylistHolder.lastPlayedUri)

        PlaylistHolder.recordDeletedUri(ep2.uri)
        if (PlaylistHolder.lastPlayedUri == ep2.uri) {
            PlaylistHolder.lastPlayedUri = null
        }
        assertNull(PlaylistHolder.lastPlayedUri)
        assertEquals(1, PlaylistHolder.currentPlaylist.size)
    }

    @Test
    fun testEpisodeMatchesLastWatchedUri() {
        val ep1 = createDummyEpisode("Episode 1", "ep01.mkv", "content://video/1")
        val ep2 = createDummyEpisode("Episode 2", "ep02.mkv", "content://video/2")
        val lastWatchedUri = "content://video/2"

        assertFalse(ep1.uri == lastWatchedUri)
        assertTrue(ep2.uri == lastWatchedUri)
    }
}
