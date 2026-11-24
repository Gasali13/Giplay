package com.personal.animeplayer.player

import com.personal.animeplayer.data.model.Episode

/**
 * Pure-Kotlin state tracker for continuous playback inactivity detection.
 */
class InactivityTracker(
    val thresholdMs: Long = DEFAULT_THRESHOLD_MS
) {
    var continuousPlaybackMs: Long = 0L
        private set

    var isPromptShowing: Boolean = false
        private set

    var pendingNextEpisode: Episode? = null
        private set

    private var lastCheckTimestampMs: Long = 0L

    fun onUserInteraction() {
        if (!isPromptShowing) {
            continuousPlaybackMs = 0L
        }
    }

    fun resetInteractionTimer() {
        continuousPlaybackMs = 0L
    }

    fun resetClock() {
        lastCheckTimestampMs = 0L
    }

    fun hasReachedThreshold(): Boolean = continuousPlaybackMs >= thresholdMs

    /**
     * Advances continuous playback duration from [nowElapsedMs] while [isPlaying] is true.
     * Returns true when the threshold is reached and the inactivity prompt is not already shown.
     */
    fun onPlaybackTick(nowElapsedMs: Long, isPlaying: Boolean): Boolean {
        if (lastCheckTimestampMs == 0L) {
            lastCheckTimestampMs = nowElapsedMs
        }
        val elapsed = (nowElapsedMs - lastCheckTimestampMs).coerceAtLeast(0L)
        lastCheckTimestampMs = nowElapsedMs

        if (isPlaying && !isPromptShowing) {
            continuousPlaybackMs += elapsed
            if (hasReachedThreshold()) {
                return true
            }
        }
        return false
    }

    fun markPromptShown(nextEpisode: Episode?) {
        isPromptShowing = true
        pendingNextEpisode = nextEpisode
    }

    /**
     * Resets prompt state upon continuing playback and returns the pending next [Episode]
     * only when playback is already within the final second of the current video.
     */
    fun resolveContinueWatching(currentPositionMs: Long, durationMs: Long): Episode? {
        isPromptShowing = false
        continuousPlaybackMs = 0L
        val next = pendingNextEpisode
        pendingNextEpisode = null
        return if (next != null && durationMs > 0L && currentPositionMs >= (durationMs - 1000L)) {
            next
        } else {
            null
        }
    }

    fun markExited() {
        isPromptShowing = false
        pendingNextEpisode = null
    }

    companion object {
        const val DEFAULT_THRESHOLD_MS: Long = 60 * 60 * 1000L
    }
}
