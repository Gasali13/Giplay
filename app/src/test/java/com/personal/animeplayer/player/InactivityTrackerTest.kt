package com.personal.animeplayer.player

import com.personal.animeplayer.data.model.Episode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InactivityTrackerTest {

    private val nextEpisode = Episode(
        uri = "content://anime/ep2",
        title = "Episode 02",
        fileName = "ep02.mkv",
        seriesFolderUri = "content://anime",
        durationMs = 1_440_000L
    )

    @Test
    fun testPauseDoesNotAccumulateAndPlayingAccumulatesUntilThreshold() {
        val tracker = InactivityTracker(thresholdMs = 60_000L)

        assertFalse(tracker.onPlaybackTick(nowElapsedMs = 1_000L, isPlaying = true))
        assertEquals(0L, tracker.continuousPlaybackMs)

        // Elapsed time while paused must not accumulate
        assertFalse(tracker.onPlaybackTick(nowElapsedMs = 31_000L, isPlaying = false))
        assertEquals(0L, tracker.continuousPlaybackMs)

        // Elapsed time while playing accumulates until reaching threshold
        assertFalse(tracker.onPlaybackTick(nowElapsedMs = 61_000L, isPlaying = true))
        assertEquals(30_000L, tracker.continuousPlaybackMs)
        assertFalse(tracker.hasReachedThreshold())

        assertTrue(tracker.onPlaybackTick(nowElapsedMs = 91_000L, isPlaying = true))
        assertEquals(60_000L, tracker.continuousPlaybackMs)
        assertTrue(tracker.hasReachedThreshold())
    }

    @Test
    fun testUserInteractionResetsWhenPromptHiddenAndIgnoredWhenShown() {
        val tracker = InactivityTracker(thresholdMs = 60_000L)

        assertFalse(tracker.onPlaybackTick(nowElapsedMs = 1_000L, isPlaying = true))
        assertFalse(tracker.onPlaybackTick(nowElapsedMs = 41_000L, isPlaying = true))
        assertEquals(40_000L, tracker.continuousPlaybackMs)

        tracker.onUserInteraction()
        assertEquals(0L, tracker.continuousPlaybackMs)

        assertTrue(tracker.onPlaybackTick(nowElapsedMs = 101_000L, isPlaying = true))
        tracker.markPromptShown(nextEpisode)

        // While prompt is showing, user interaction and ticks do not dismiss or re-trigger
        tracker.onUserInteraction()
        assertTrue(tracker.isPromptShowing)
        assertEquals(60_000L, tracker.continuousPlaybackMs)
        assertFalse(tracker.onPlaybackTick(nowElapsedMs = 110_000L, isPlaying = true))
        assertEquals(60_000L, tracker.continuousPlaybackMs)
    }

    @Test
    fun testResolveContinueWatchingNearEndVsMidVideo() {
        val tracker = InactivityTracker(thresholdMs = 10_000L)
        tracker.onPlaybackTick(nowElapsedMs = 1_000L, isPlaying = true)
        tracker.onPlaybackTick(nowElapsedMs = 11_000L, isPlaying = true)
        tracker.markPromptShown(nextEpisode)

        // Mid-video -> resumes current episode (returns null) and resets state
        val midVideoResult = tracker.resolveContinueWatching(currentPositionMs = 500_000L, durationMs = 1_440_000L)
        assertNull(midVideoResult)
        assertFalse(tracker.isPromptShowing)
        assertNull(tracker.pendingNextEpisode)
        assertEquals(0L, tracker.continuousPlaybackMs)

        // Near end (within final 1000ms) -> switches to pending next episode
        tracker.markPromptShown(nextEpisode)
        val nearEndResult = tracker.resolveContinueWatching(currentPositionMs = 1_439_500L, durationMs = 1_440_000L)
        assertEquals(nextEpisode, nearEndResult)
        assertFalse(tracker.isPromptShowing)
        assertNull(tracker.pendingNextEpisode)
    }

    @Test
    fun testMarkExitedAndResetClock() {
        val tracker = InactivityTracker(thresholdMs = 60_000L)
        tracker.onPlaybackTick(nowElapsedMs = 1_000L, isPlaying = true)
        tracker.onPlaybackTick(nowElapsedMs = 11_000L, isPlaying = true)
        assertEquals(10_000L, tracker.continuousPlaybackMs)

        // Reset clock simulates episode transition so huge wall jump doesn't count
        tracker.resetClock()
        assertFalse(tracker.onPlaybackTick(nowElapsedMs = 500_000L, isPlaying = true))
        assertEquals(10_000L, tracker.continuousPlaybackMs)

        tracker.markPromptShown(nextEpisode)
        tracker.markExited()
        assertFalse(tracker.isPromptShowing)
        assertNull(tracker.pendingNextEpisode)
    }
}
