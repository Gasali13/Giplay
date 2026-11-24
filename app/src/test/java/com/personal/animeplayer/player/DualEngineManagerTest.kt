package com.personal.animeplayer.player

import com.personal.animeplayer.data.model.AudioTrackInfo
import com.personal.animeplayer.data.model.SubtitleTrackInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DualEngineManagerTest {

    private class FakeManagedEngine(
        val callbacks: EngineCallbacks
    ) : ManagedEngine {
        override var isPlaying: Boolean = false
        override var currentPosition: Long = 0L
        override var duration: Long = 0L

        var playMediaCalls = 0
        var playCalls = 0
        var pauseCalls = 0
        var stopCalls = 0
        var releaseCalls = 0
        var lastMedia: MediaRef? = null
        var lastTitle: String = ""
        var lastStartPositionMs: Long = 0L
        var lastSpeed: Float = 1.0f
        var lastLooping: Boolean = false
        var lastSubtitleScale: Int = 100
        var lastSubtitleDelayMs: Long = 0L
        var lastDecoderMode: String = "mediacodec"
        var lastSongDetectionMode: Boolean = true
        val aspectRatioCalls = mutableListOf<Triple<AspectRatioMode, Int, Int>>()

        override fun playMedia(
            media: MediaRef,
            title: String,
            startPositionMs: Long,
            externalSubtitles: List<MediaRef>
        ) {
            playMediaCalls++
            lastMedia = media
            lastTitle = title
            lastStartPositionMs = startPositionMs
            currentPosition = startPositionMs
            isPlaying = true
        }

        override fun play() {
            playCalls++
            isPlaying = true
        }

        override fun pause() {
            pauseCalls++
            isPlaying = false
        }

        override fun stop() {
            stopCalls++
            isPlaying = false
        }

        override fun seekTo(positionMs: Long, fast: Boolean) {
            currentPosition = positionMs
        }

        override fun setPlaybackSpeed(speed: Float) {
            lastSpeed = speed
        }

        override fun setLooping(enabled: Boolean) {
            lastLooping = enabled
        }

        override fun loadExternalSubtitle(sub: MediaRef, select: Boolean) {}

        override fun setSubtitleScale(scalePercent: Int) {
            lastSubtitleScale = scalePercent
        }

        override fun setSubtitleDelay(delayMs: Long) {
            lastSubtitleDelayMs = delayMs
        }

        override fun setDecoderMode(mode: String) {
            lastDecoderMode = mode
        }

        override fun setSongDetectionMode(enabled: Boolean) {
            lastSongDetectionMode = enabled
        }

        override fun setAspectRatio(mode: AspectRatioMode, displayWidthPx: Int, displayHeightPx: Int) {
            aspectRatioCalls.add(Triple(mode, displayWidthPx, displayHeightPx))
        }

        override fun getSubtitleTracks(): List<SubtitleTrackInfo> = emptyList()
        override fun selectSubtitleTrack(trackId: Int) {}
        override fun getAudioTracks(): List<AudioTrackInfo> = emptyList()
        override fun selectAudioTrack(trackId: Int) {}

        override fun release() {
            releaseCalls++
        }
    }

    private class Harness : DualEngineManager.Listener {
        var mpvFactoryCount = 0
        var exoFactoryCount = 0
        var lastMpv: FakeManagedEngine? = null
        var lastExo: FakeManagedEngine? = null
        val surfaceSwitches = mutableListOf<String>()
        val hdrEvents = mutableListOf<Pair<Boolean, String>>()
        val videoReadyEvents = mutableListOf<Long>()
        val playbackEndedCount = mutableListOf<Unit>()

        val manager = DualEngineManager(
            listener = this,
            createMpvEngine = { callbacks ->
                mpvFactoryCount++
                FakeManagedEngine(callbacks).also { lastMpv = it }
            },
            createExoPlayerEngine = { callbacks ->
                exoFactoryCount++
                FakeManagedEngine(callbacks).also { lastExo = it }
            },
            onSurfaceVisibilityChanged = { active ->
                surfaceSwitches.add(active)
            },
            getDisplaySizePx = { 1080 to 2400 }
        )

        override fun onPlaybackStateChanged(isPlaying: Boolean) {}
        override fun onPositionChanged(currentPosMs: Long, durationMs: Long) {}
        override fun onVideoReady(durationMs: Long) {
            videoReadyEvents.add(durationMs)
        }
        override fun onPlayerError(message: String) {}
        override fun onPlaybackEnded() {
            playbackEndedCount.add(Unit)
        }
        override fun onHdrStateDetected(isHdr: Boolean, hdrType: String) {
            hdrEvents.add(isHdr to hdrType)
        }
    }

    @Test
    fun testAutoModeSdrInitializesOnlyMpv() {
        val harness = Harness()
        assertFalse(harness.manager.hasInitializedEngine)

        harness.manager.playMediaRef(
            media = MediaRef("content://video/ep1.mkv"),
            title = "Anime Episode 01",
            startPositionMs = 15_000L,
            prefEngine = DualEngineManager.ENGINE_AUTO,
            isLikelyHdrOrCamera = false
        )

        assertEquals(1, harness.mpvFactoryCount)
        assertEquals(0, harness.exoFactoryCount)
        assertTrue(harness.manager.isMpvInitialized)
        assertFalse(harness.manager.isExoInitialized)
        assertEquals(DualEngineManager.ENGINE_MPV, harness.manager.activeEngine)
        assertEquals(listOf(DualEngineManager.ENGINE_MPV), harness.surfaceSwitches)
        assertEquals(15_000L, harness.lastMpv?.lastStartPositionMs)
    }

    @Test
    fun testAutoModeHdrInitializesOnlyExoPlayer() {
        val harness = Harness()

        harness.manager.playMediaRef(
            media = MediaRef("content://video/movie_hdr10.mkv"),
            title = "Movie.2160p.HDR10.mkv",
            startPositionMs = 2_000L,
            prefEngine = DualEngineManager.ENGINE_AUTO,
            isLikelyHdrOrCamera = true
        )

        assertEquals(0, harness.mpvFactoryCount)
        assertEquals(1, harness.exoFactoryCount)
        assertFalse(harness.manager.isMpvInitialized)
        assertTrue(harness.manager.isExoInitialized)
        assertEquals(DualEngineManager.ENGINE_EXOPLAYER, harness.manager.activeEngine)
        assertEquals(listOf(DualEngineManager.ENGINE_EXOPLAYER), harness.surfaceSwitches)
        assertNull(harness.lastMpv)
    }

    @Test
    fun testExplicitEnginePreferenceInitializesOnlyRequestedEngine() {
        val mpvHarness = Harness()
        mpvHarness.manager.playMediaRef(
            media = MediaRef("content://video/hdr.mkv"),
            title = "Movie.HDR10.mkv",
            startPositionMs = 0L,
            prefEngine = DualEngineManager.ENGINE_MPV,
            isLikelyHdrOrCamera = true
        )
        assertEquals(1, mpvHarness.mpvFactoryCount)
        assertEquals(0, mpvHarness.exoFactoryCount)
        assertEquals(DualEngineManager.ENGINE_MPV, mpvHarness.manager.activeEngine)

        val exoHarness = Harness()
        exoHarness.manager.playMediaRef(
            media = MediaRef("content://video/sdr.mkv"),
            title = "Anime Episode 01",
            startPositionMs = 0L,
            prefEngine = DualEngineManager.ENGINE_EXOPLAYER,
            isLikelyHdrOrCamera = false
        )
        assertEquals(0, exoHarness.mpvFactoryCount)
        assertEquals(1, exoHarness.exoFactoryCount)
        assertEquals(DualEngineManager.ENGINE_EXOPLAYER, exoHarness.manager.activeEngine)
    }

    @Test
    fun testPreferencesCachedBeforeInitAndSyncedOnLazyCreationAndSwitch() {
        val harness = Harness()

        harness.manager.applyPreferences(
            subtitleDelayMs = 1500L,
            subtitleScale = 135,
            playbackSpeed = 1.25f,
            isLoopEnabled = true,
            decoderMode = "mediacodec-copy",
            isSongDetectionEnabled = false,
            playerEngineMode = DualEngineManager.ENGINE_AUTO,
            isLoaded = true
        )
        harness.manager.setAspectRatio(AspectRatioMode.ZOOM)

        // Applying preferences before playback must NOT eagerly create either engine
        assertEquals(0, harness.mpvFactoryCount)
        assertEquals(0, harness.exoFactoryCount)
        assertFalse(harness.manager.hasInitializedEngine)

        // Launch SDR video -> lazily initializes MPV with cached settings
        harness.manager.playMediaRef(
            media = MediaRef("content://video/ep2.mkv"),
            title = "Anime Episode 02",
            startPositionMs = 0L,
            prefEngine = DualEngineManager.ENGINE_AUTO,
            isLikelyHdrOrCamera = false
        )
        val mpv = harness.lastMpv!!
        assertEquals(1500L, mpv.lastSubtitleDelayMs)
        assertEquals(135, mpv.lastSubtitleScale)
        assertEquals(1.25f, mpv.lastSpeed)
        assertTrue(mpv.lastLooping)
        assertEquals("mediacodec-copy", mpv.lastDecoderMode)
        assertFalse(mpv.lastSongDetectionMode)
        assertEquals(AspectRatioMode.ZOOM, mpv.aspectRatioCalls.last().first)

        // Switch to ExoPlayer -> lazily initializes ExoPlayer and syncs cached preferences
        harness.manager.switchActiveEngine(DualEngineManager.ENGINE_EXOPLAYER)
        val exo = harness.lastExo!!
        assertEquals(1, harness.exoFactoryCount)
        assertEquals(135, exo.lastSubtitleScale)
        assertEquals(1.25f, exo.lastSpeed)
        assertTrue(exo.lastLooping)
        assertFalse(exo.lastSongDetectionMode)
        assertEquals(AspectRatioMode.ZOOM, exo.aspectRatioCalls.last().first)
    }

    @Test
    fun testAutoSwitchFromMpvToExoOnHdrPreservesPositionAndReusesInstances() {
        val harness = Harness()

        harness.manager.playMediaRef(
            media = MediaRef("content://video/ep3.mkv"),
            title = "Mystery Video",
            startPositionMs = 0L,
            prefEngine = DualEngineManager.ENGINE_AUTO,
            isLikelyHdrOrCamera = false
        )
        val mpv = harness.lastMpv!!
        mpv.currentPosition = 42_500L

        // Runtime HDR detection in MPV while prefEngine == auto switches cleanly to ExoPlayer
        mpv.callbacks.onHdrStateDetected(true, "HDR10")

        assertEquals(1, harness.mpvFactoryCount)
        assertEquals(1, harness.exoFactoryCount)
        assertEquals(1, mpv.pauseCalls)
        assertEquals(DualEngineManager.ENGINE_EXOPLAYER, harness.manager.activeEngine)
        assertEquals(42_500L, harness.lastExo?.lastStartPositionMs)
        assertTrue(harness.manager.isPlaying)

        // Switching back to MPV reuses the existing MPV instance without re-creating it
        harness.lastExo!!.currentPosition = 65_000L
        harness.manager.switchActiveEngine(DualEngineManager.ENGINE_MPV)
        assertEquals(1, harness.mpvFactoryCount)
        assertEquals(1, harness.exoFactoryCount)
        assertEquals(DualEngineManager.ENGINE_MPV, harness.manager.activeEngine)
        assertEquals(65_000L, mpv.lastStartPositionMs)
    }

    @Test
    fun testSwitchWhilePausedKeepsTargetEnginePaused() {
        val harness = Harness()
        harness.manager.playMediaRef(
            media = MediaRef("content://video/ep4.mkv"),
            title = "Paused Test",
            startPositionMs = 5_000L,
            prefEngine = DualEngineManager.ENGINE_AUTO,
            isLikelyHdrOrCamera = false
        )
        harness.manager.pause()
        assertFalse(harness.manager.isPlaying)

        harness.manager.switchActiveEngine(DualEngineManager.ENGINE_EXOPLAYER)

        val exo = harness.lastExo!!
        assertEquals(DualEngineManager.ENGINE_EXOPLAYER, harness.manager.activeEngine)
        assertEquals(1, exo.pauseCalls)
        assertFalse(exo.isPlaying)
    }

    @Test
    fun testReleaseOnlyReleasesInitializedEngines() {
        val harness = Harness()
        harness.manager.playMediaRef(
            media = MediaRef("content://video/ep5.mkv"),
            title = "MPV Only",
            startPositionMs = 0L,
            prefEngine = DualEngineManager.ENGINE_MPV,
            isLikelyHdrOrCamera = false
        )
        val mpv = harness.lastMpv!!

        harness.manager.release()

        assertEquals(1, mpv.stopCalls)
        assertEquals(1, mpv.releaseCalls)
        assertEquals(0, harness.exoFactoryCount)
        assertNull(harness.lastExo)
        assertFalse(harness.manager.hasInitializedEngine)
    }

    @Test
    fun testAspectRatioModeCycle() {
        val harness = Harness()
        assertEquals(AspectRatioMode.FIT, harness.manager.currentAspectRatioMode)
        assertEquals(AspectRatioMode.STRETCH, harness.manager.cycleAspectRatio())
        assertEquals(AspectRatioMode.ZOOM, harness.manager.cycleAspectRatio())
        assertEquals(AspectRatioMode.FIT, harness.manager.cycleAspectRatio())

        harness.manager.setAspectRatio(AspectRatioMode.SIXTEEN_NINE)
        assertEquals(AspectRatioMode.FIT, harness.manager.cycleAspectRatio())

        harness.manager.setAspectRatio(AspectRatioMode.FOUR_THREE)
        assertEquals(AspectRatioMode.FIT, harness.manager.cycleAspectRatio())
    }
}
