package com.personal.animeplayer.ui.player

import android.os.CountDownTimer
import com.personal.animeplayer.data.model.Episode
import com.personal.animeplayer.player.InactivityTracker

/**
 * Coordinates the 60-minute continuous playback sleep prompt and 30-second auto-exit
 * countdown overlay.
 */
class InactivityMonitor(
    private val onPromptStateChanged: (isShowing: Boolean, secondsLeft: Int) -> Unit,
    private val onPausePlayback: () -> Unit,
    private val onHideControls: () -> Unit,
    private val onResumePlayback: () -> Unit,
    private val onSwitchToEpisode: (Episode) -> Unit,
    private val onKeepScreenOn: () -> Unit,
    private val onExitApp: () -> Unit,
    private val getCurrentPosition: () -> Long,
    private val getDuration: () -> Long,
    private val getNextEpisode: () -> Episode?,
    thresholdMs: Long = InactivityTracker.DEFAULT_THRESHOLD_MS
) {
    private val tracker = InactivityTracker(thresholdMs)
    private var countdownTimer: CountDownTimer? = null

    val isPromptShowing: Boolean
        get() = tracker.isPromptShowing

    fun onUserInteraction() {
        tracker.onUserInteraction()
    }

    fun resetInteractionTimer() {
        tracker.resetInteractionTimer()
    }

    fun resetClock() {
        tracker.resetClock()
    }

    fun hasReachedThreshold(): Boolean = tracker.hasReachedThreshold()

    fun onPlaybackTick(nowElapsedMs: Long, isPlaying: Boolean) {
        if (tracker.onPlaybackTick(nowElapsedMs, isPlaying)) {
            showPrompt(getNextEpisode())
        }
    }

    fun showPrompt(nextEpisode: Episode? = null) {
        tracker.markPromptShown(nextEpisode)
        onPausePlayback()
        onHideControls()
        onPromptStateChanged(true, 30)

        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(30_000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = ((millisUntilFinished + 500) / 1000).toInt().coerceAtLeast(0)
                onPromptStateChanged(true, secondsLeft)
            }

            override fun onFinish() {
                cancelCountdown()
                tracker.markExited()
                onPromptStateChanged(false, 0)
                onExitApp()
            }
        }.start()
    }

    fun continueWatching() {
        cancelCountdown()
        onPromptStateChanged(false, 0)
        onKeepScreenOn()

        val epToSwitch = tracker.resolveContinueWatching(getCurrentPosition(), getDuration())
        if (epToSwitch != null) {
            onSwitchToEpisode(epToSwitch)
        } else {
            onResumePlayback()
        }
    }

    fun cancelCountdown() {
        countdownTimer?.cancel()
        countdownTimer = null
    }

    fun release() {
        cancelCountdown()
    }
}
