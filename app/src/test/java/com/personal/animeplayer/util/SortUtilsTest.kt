package com.personal.animeplayer.util

import com.personal.animeplayer.data.model.Episode
import com.personal.animeplayer.data.model.SortOrder
import com.personal.animeplayer.player.PlaylistHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SortUtilsTest {

    private fun createEpisode(name: String, dateAdded: Long): Episode {
        return Episode(
            uri = "content://media/external/video/media/$name",
            title = name.substringBeforeLast('.'),
            fileName = name,
            seriesFolderUri = "mediastore://folder/camera",
            durationMs = 60000L,
            lastPositionMs = 0L,
            progressPercentage = 0,
            fileSize = 1024L,
            dateAdded = dateAdded
        )
    }

    @Test
    fun testSortByNameWithCameraVideoFormats() {
        val ep1 = createEpisode("VID_20240101_001.mp4", 1000L)
        val ep2 = createEpisode("VID_20240101_002.mp4", 2000L)
        val ep10 = createEpisode("VID_20240101_010.mp4", 3000L)
        val ep20 = createEpisode("VID_20240101_020.mp4", 4000L)

        val list = listOf(ep20, ep1, ep10, ep2)
        val sorted = list.sortedByOrder(SortOrder.NAME)

        assertEquals(listOf(ep1, ep2, ep10, ep20), sorted)
    }

    @Test
    fun testSortByNewest() {
        val epOld = createEpisode("VID_old.mp4", 1000L)
        val epMid = createEpisode("VID_mid.mp4", 2000L)
        val epNew = createEpisode("VID_new.mp4", 3000L)

        val list = listOf(epMid, epOld, epNew)
        val sorted = list.sortedByOrder(SortOrder.NEWEST)

        assertEquals(listOf(epNew, epMid, epOld), sorted)
    }

    @Test
    fun testSortByOldest() {
        val epOld = createEpisode("VID_old.mp4", 1000L)
        val epMid = createEpisode("VID_mid.mp4", 2000L)
        val epNew = createEpisode("VID_new.mp4", 3000L)

        val list = listOf(epMid, epNew, epOld)
        val sorted = list.sortedByOrder(SortOrder.OLDEST)

        assertEquals(listOf(epOld, epMid, epNew), sorted)
    }

    @Test
    fun testPlaylistHolderSync() {
        val ep1 = createEpisode("VID_1.mp4", 1000L)
        val ep2 = createEpisode("VID_2.mp4", 2000L)
        val list = listOf(ep1, ep2)

        PlaylistHolder.setPlaylist(list, "mediastore://folder/camera")
        assertEquals(2, PlaylistHolder.currentPlaylist.size)
        assertEquals(ep1, PlaylistHolder.currentPlaylist[0])
        assertEquals(ep2, PlaylistHolder.currentPlaylist[1])
        assertEquals("mediastore://folder/camera", PlaylistHolder.currentSeriesKey)

        PlaylistHolder.clear()
        assertTrue(PlaylistHolder.currentPlaylist.isEmpty())
    }
}
