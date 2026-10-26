package com.personal.animeplayer.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Manages audio focus for AniPlay video player.
 *
 * 1. Requests AUDIOFOCUS_GAIN_TRANSIENT so external music players (Spotify, YouTube Music, etc.)
 *    pause automatically when a video is played, avoiding audio collision ("suara tidak tabrakan").
 * 2. Abandons audio focus when video playback is paused or stopped, so external music players
 *    can resume automatically ("lagunya otomatis play kembali").
 * 3. Ignores AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK so phone song detection tools (MIUI Sound Recognition,
 *    Shazam, Google Sound Search) can listen to the video's audio output without pausing the video.
 * 4. Automatically pauses on transient interruptions like phone calls (AUDIOFOCUS_LOSS_TRANSIENT)
 *    and resumes when the interruption ends.
 */
class AudioFocusHelper(
    context: Context? = null,
    private val onPausePlayback: () -> Unit,
    private val onResumePlayback: () -> Unit
) {
    private val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val mainHandler: Handler? = try {
        Handler(Looper.getMainLooper())
    } catch (e: Exception) {
        null
    }

    var hasAudioFocus = false
        private set

    var wasPlayingBeforeLoss = false
        private set

    private var audioFocusRequest: AudioFocusRequest? = null

    private val abandonRunnable = Runnable {
        executeAbandonFocus()
    }

    val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        handleAudioFocusChange(focusChange)
    }

    init {
        if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build()

            val builder = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
            if (mainHandler != null) {
                builder.setOnAudioFocusChangeListener(audioFocusChangeListener, mainHandler)
            } else {
                builder.setOnAudioFocusChangeListener(audioFocusChangeListener)
            }
            audioFocusRequest = builder.build()
        }
    }

    var songDetectionMode: Boolean = true

    /**
     * Determines whether an actual telephone or VoIP call is in progress.
     */
    val isCallActive: Boolean
        get() {
            val am = audioManager ?: return false
            return when (am.mode) {
                AudioManager.MODE_IN_CALL,
                AudioManager.MODE_IN_COMMUNICATION,
                AudioManager.MODE_RINGTONE -> true
                else -> false
            }
        }

    fun handleAudioFocusChange(focusChange: Int) {
        Log.d(TAG, "onAudioFocusChange: $focusChange")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent loss of audio focus (e.g. user explicitly started another audio/video app)
                hasAudioFocus = false
                wasPlayingBeforeLoss = false
                mainHandler?.removeCallbacks(abandonRunnable)
                onPausePlayback()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (songDetectionMode && !isCallActive) {
                    // Non-call transient loss (e.g. Google Assistant, Circle to Search "Search a song",
                    // Shazam, or background voice detection).
                    // DO NOT pause playback! Keep playing so music recognition tools can hear and detect the song.
                    Log.d(TAG, "Ignoring AUDIOFOCUS_LOSS_TRANSIENT (non-call) for song detection")
                    return
                }
                // Temporary loss from phone call, alarm, or user disabled song detection mode
                hasAudioFocus = false
                wasPlayingBeforeLoss = true
                mainHandler?.removeCallbacks(abandonRunnable)
                onPausePlayback()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Transient loss where other app allows ducking / quiet background (e.g. notification sound,
                // or song detection listening to audio output)
                // DO NOT pause playback! Keep playing so music recognition or video isn't interrupted.
                Log.d(TAG, "Ignoring AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK to keep playback active for song detection")
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Focus regained
                hasAudioFocus = true
                mainHandler?.removeCallbacks(abandonRunnable)
                if (wasPlayingBeforeLoss) {
                    wasPlayingBeforeLoss = false
                    onResumePlayback()
                }
            }
        }
    }

    /**
     * Requests audio focus. Other music players (Spotify, etc.) will pause automatically.
     */
    fun requestFocus(): Boolean {
        mainHandler?.removeCallbacks(abandonRunnable)
        if (hasAudioFocus) return true
        val am = audioManager ?: return true

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.requestAudioFocus(it) } ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }

        hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED))
        Log.d(TAG, "requestFocus result: $result, hasAudioFocus: $hasAudioFocus")
        return hasAudioFocus
    }

    /**
     * Abandons audio focus. Other music players (Spotify, etc.) will receive AUDIOFOCUS_GAIN
     * and automatically resume playback.
     * @param delayedMs delay in milliseconds before abandoning focus (prevents rapid focus hopping during scrubbing/seeking)
     */
    fun abandonFocus(delayedMs: Long = 0L) {
        if (!hasAudioFocus) return
        mainHandler?.removeCallbacks(abandonRunnable)
        if (delayedMs <= 0L || mainHandler == null) {
            executeAbandonFocus()
        } else {
            mainHandler.postDelayed(abandonRunnable, delayedMs)
        }
    }

    fun cancelPendingAbandon() {
        mainHandler?.removeCallbacks(abandonRunnable)
    }

    private fun executeAbandonFocus() {
        if (!hasAudioFocus) return
        hasAudioFocus = false
        wasPlayingBeforeLoss = false
        val am = audioManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(audioFocusChangeListener)
        }
        Log.d(TAG, "abandonFocus executed")
    }

    companion object {
        private const val TAG = "AudioFocusHelper"
    }
}
