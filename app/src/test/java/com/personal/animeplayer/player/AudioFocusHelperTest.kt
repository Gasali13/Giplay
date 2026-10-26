package com.personal.animeplayer.player

import android.media.AudioManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFocusHelperTest {

    @Test
    fun testTransientLossPausesAndGainResumesWhenModeDisabled() {
        var pauseCalled = false
        var resumeCalled = false

        val helper = AudioFocusHelper(
            context = null,
            onPausePlayback = { pauseCalled = true },
            onResumePlayback = { resumeCalled = true }
        ).apply {
            songDetectionMode = false
        }

        // Interruption when song detection mode is disabled (or phone call)
        helper.handleAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertTrue(pauseCalled)
        assertTrue(helper.wasPlayingBeforeLoss)
        assertFalse(helper.hasAudioFocus)

        // Focus returns
        helper.handleAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertTrue(resumeCalled)
        assertFalse(helper.wasPlayingBeforeLoss)
        assertTrue(helper.hasAudioFocus)
    }

    @Test
    fun testSongDetectionModeKeepsPlaybackAliveOnTransientLoss() {
        var pauseCalled = false
        var resumeCalled = false

        val helper = AudioFocusHelper(
            context = null,
            onPausePlayback = { pauseCalled = true },
            onResumePlayback = { resumeCalled = true }
        ).apply {
            songDetectionMode = true
        }

        // Google song detection triggers AUDIOFOCUS_LOSS_TRANSIENT without active phone call
        helper.handleAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertFalse("Playback MUST NOT pause on non-call transient loss when songDetectionMode is active", pauseCalled)
        assertFalse(resumeCalled)
    }

    @Test
    fun testPermanentLossDoesNotAutoResume() {
        var pauseCalled = false
        var resumeCalled = false

        val helper = AudioFocusHelper(
            context = null,
            onPausePlayback = { pauseCalled = true },
            onResumePlayback = { resumeCalled = true }
        )

        // Permanent loss (e.g. user manually opened and played Spotify)
        helper.handleAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        assertTrue(pauseCalled)
        assertFalse(helper.wasPlayingBeforeLoss)
        assertFalse(helper.hasAudioFocus)

        // Regaining focus later should NOT auto-resume
        helper.handleAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertFalse(resumeCalled)
        assertTrue(helper.hasAudioFocus)
    }

    @Test
    fun testCanDuckDoesNotPausePlaybackForSongRecognition() {
        var pauseCalled = false
        var resumeCalled = false

        val helper = AudioFocusHelper(
            context = null,
            onPausePlayback = { pauseCalled = true },
            onResumePlayback = { resumeCalled = true }
        )

        // Song recognition or notification transient can duck
        helper.handleAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        assertFalse("Playback MUST NOT pause on can-duck to allow song detection", pauseCalled)
        assertFalse(resumeCalled)
    }
}
