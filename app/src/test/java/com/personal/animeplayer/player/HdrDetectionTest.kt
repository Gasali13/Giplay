package com.personal.animeplayer.player

import com.personal.animeplayer.ui.player.HdrDisplayController
import com.personal.animeplayer.ui.player.PlayerActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HdrDetectionTest {

    @Test
    fun testNoFalsePositivesForCommonWordsContainingDvOrPq() {
        val nonHdrTitles = listOf(
            "JoJo's Bizarre Adventure - 01.mkv",
            "Advance of Zeta - Episode 05.mp4",
            "Anime [DVD 480p].mkv",
            "Advice for Beginners.mp4",
            "/storage/emulated/0/Advan/Movies/Episode_01.mkv",
            "PQRS_Test_Clip.mp4",
            "MyHdrLikeWordWithoutBoundary.mp4",
            "Movie.1080p.Dolby.Atmos.SDR.mkv"
        )

        for (title in nonHdrTitles) {
            assertFalse(
                "PlayerActivity HDR regex must NOT match '$title'",
                PlayerActivity.REGEX_HDR_KEYWORDS.containsMatchIn(title)
            )
            assertFalse(
                "ExoPlayerEngine Dolby regex must NOT match '$title'",
                ExoPlayerEngine.REGEX_DOLBY_VISION.containsMatchIn(title)
            )
            assertFalse(
                "ExoPlayerEngine HDR10+ regex must NOT match '$title'",
                ExoPlayerEngine.REGEX_HDR10_PLUS.containsMatchIn(title)
            )
        }
    }

    @Test
    fun testRealHdrAndDolbyVisionTitlesAreDetected() {
        val hdr10PlusTitles = listOf(
            "Movie.2025.2160p.HDR10+.mkv",
            "Demo [HDR10Plus] 4K.mp4",
            "Sample_HDR10+_10bit.mkv"
        )
        for (title in hdr10PlusTitles) {
            assertTrue("Should detect HDR10+ in '$title'", ExoPlayerEngine.REGEX_HDR10_PLUS.containsMatchIn(title))
            assertTrue("PlayerActivity should detect HDR in '$title'", PlayerActivity.REGEX_HDR_KEYWORDS.containsMatchIn(title))
        }

        val dolbyTitles = listOf(
            "Film.2025.2160p.DV.HEVC.mkv",
            "Series [DoVi] S01E01.mkv",
            "Demo Dolby Vision 4K.mp4",
            "Movie.Dolby.Vision.2160p.mkv"
        )
        for (title in dolbyTitles) {
            assertTrue("Should detect Dolby Vision in '$title'", ExoPlayerEngine.REGEX_DOLBY_VISION.containsMatchIn(title))
            assertTrue("PlayerActivity should detect HDR in '$title'", PlayerActivity.REGEX_HDR_KEYWORDS.containsMatchIn(title))
        }

        val hdr10Titles = listOf(
            "Anime.2160p.HDR10.10bit.mkv",
            "Clip_HDR_4K.mp4",
            "Broadcast.HLG.2025.ts"
        )
        for (title in hdr10Titles) {
            assertTrue("PlayerActivity should detect HDR in '$title'", PlayerActivity.REGEX_HDR_KEYWORDS.containsMatchIn(title))
        }
    }

    @Test
    fun testHdrDisplayControllerHeuristicNamesAndCameraPaths() {
        assertTrue(
            "Camera /DCIM/ path should route as HDR/camera",
            HdrDisplayController.isLikelyHdrOrCameraNames(
                listOf("sample video", "/storage/emulated/0/dcim/camera/clip.mp4")
            )
        )
        assertTrue(
            "VID_ camera prefix should route as HDR/camera",
            HdrDisplayController.isLikelyHdrOrCameraNames(listOf("vid_20261122_0100.mp4"))
        )
        assertTrue(
            "Explicit HDR keyword in filename should be detected",
            HdrDisplayController.isLikelyHdrOrCameraNames(listOf("anime.ep01.hdr10+.mkv"))
        )
        assertFalse(
            "Standard anime SDR path must NOT match HDR or camera heuristic",
            HdrDisplayController.isLikelyHdrOrCameraNames(
                listOf("advance of zeta - 01", "/storage/emulated/0/anime/advance of zeta - 01.mkv")
            )
        )
    }
}
