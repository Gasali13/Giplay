package com.personal.animeplayer.player

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.View
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.personal.animeplayer.data.model.AudioTrackInfo
import com.personal.animeplayer.data.model.SubtitleTrackInfo

enum class AspectRatioMode(val title: String) {
    FIT("Fit Layar (Asli)"),
    STRETCH("Regangkan Penuh"),
    ZOOM("Potong / Zoom Layar"),
    SIXTEEN_NINE("16:9 Standar"),
    FOUR_THREE("4:3 Klasik");

    fun nextCycle(): AspectRatioMode {
        return when (this) {
            FIT -> STRETCH
            STRETCH -> ZOOM
            ZOOM -> FIT
            SIXTEEN_NINE -> FIT
            FOUR_THREE -> FIT
        }
    }
}

/**
 * Lightweight media reference preserving an Android [Uri] on device while remaining
 * constructible with plain strings in pure-JVM unit tests.
 */
data class MediaRef(
    val uriString: String,
    val uri: Uri? = null
) {
    fun toUri(): Uri = uri ?: Uri.parse(uriString)
}

internal data class EngineCallbacks(
    val onPlaybackStateChanged: (isPlaying: Boolean) -> Unit,
    val onPositionChanged: (currentPosMs: Long, durationMs: Long) -> Unit,
    val onVideoReady: (durationMs: Long) -> Unit,
    val onPlayerError: (String) -> Unit,
    val onPlaybackEnded: () -> Unit,
    val onHdrStateDetected: (isHdr: Boolean, hdrType: String) -> Unit
)

internal interface ManagedEngine {
    val isPlaying: Boolean
    val currentPosition: Long
    val duration: Long

    fun playMedia(
        media: MediaRef,
        title: String,
        startPositionMs: Long,
        externalSubtitles: List<MediaRef>
    )

    fun play()
    fun pause()
    fun stop()
    fun seekTo(positionMs: Long, fast: Boolean = false)
    fun setPlaybackSpeed(speed: Float)
    fun setLooping(enabled: Boolean)
    fun loadExternalSubtitle(sub: MediaRef, select: Boolean)
    fun setSubtitleScale(scalePercent: Int)
    fun setSubtitleDelay(delayMs: Long) {}
    fun setDecoderMode(mode: String) {}
    fun setSongDetectionMode(enabled: Boolean)
    fun setAspectRatio(mode: AspectRatioMode, displayWidthPx: Int, displayHeightPx: Int)
    fun getSubtitleTracks(): List<SubtitleTrackInfo>
    fun selectSubtitleTrack(trackId: Int)
    fun getAudioTracks(): List<AudioTrackInfo>
    fun selectAudioTrack(trackId: Int)
    fun getVideoSize(): Pair<Int, Int>? = null
    fun release()
}

/**
 * Coordinates lazy initialization, switching, preference synchronization, and lifecycle
 * management across the MPV ([PlayerEngine]) and ExoPlayer ([ExoPlayerEngine]) backends.
 */
class DualEngineManager internal constructor(
    private val listener: Listener,
    private val createMpvEngine: (EngineCallbacks) -> ManagedEngine,
    private val createExoPlayerEngine: (EngineCallbacks) -> ManagedEngine,
    private val onSurfaceVisibilityChanged: (activeEngine: String) -> Unit,
    private val getDisplaySizePx: () -> Pair<Int, Int>
) {

    interface Listener {
        fun onPlaybackStateChanged(isPlaying: Boolean)
        fun onPositionChanged(currentPosMs: Long, durationMs: Long)
        fun onVideoReady(durationMs: Long)
        fun onPlayerError(message: String)
        fun onPlaybackEnded()
        fun onHdrStateDetected(isHdr: Boolean, hdrType: String)
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    constructor(
        context: Context,
        mpvView: MPVView,
        exoPlayerView: PlayerView,
        listener: Listener
    ) : this(
        listener = listener,
        createMpvEngine = { callbacks ->
            val engine = PlayerEngine(
                context = context,
                mpvView = mpvView,
                onPlaybackStateChanged = callbacks.onPlaybackStateChanged,
                onPositionChanged = callbacks.onPositionChanged,
                onVideoReady = callbacks.onVideoReady,
                onPlayerError = callbacks.onPlayerError,
                onPlaybackEnded = callbacks.onPlaybackEnded,
                onHdrStateDetected = callbacks.onHdrStateDetected
            )
            MpvManagedEngineAdapter(engine)
        },
        createExoPlayerEngine = { callbacks ->
            val engine = ExoPlayerEngine(
                context = context,
                playerView = exoPlayerView,
                onPlaybackStateChanged = callbacks.onPlaybackStateChanged,
                onPositionChanged = callbacks.onPositionChanged,
                onVideoReady = callbacks.onVideoReady,
                onPlayerError = callbacks.onPlayerError,
                onPlaybackEnded = callbacks.onPlaybackEnded,
                onHdrStateDetected = callbacks.onHdrStateDetected
            )
            ExoManagedEngineAdapter(engine)
        },
        onSurfaceVisibilityChanged = { active ->
            if (active == ENGINE_EXOPLAYER) {
                mpvView.visibility = View.GONE
                exoPlayerView.visibility = View.VISIBLE
            } else {
                exoPlayerView.visibility = View.GONE
                mpvView.visibility = View.VISIBLE
            }
        },
        getDisplaySizePx = {
            context.resources.displayMetrics.let { it.widthPixels to it.heightPixels }
        }
    )

    private var mpvEngine: ManagedEngine? = null
    private var exoPlayerEngine: ManagedEngine? = null

    var activeEngine: String = ENGINE_MPV
        private set

    var currentAspectRatioMode: AspectRatioMode = AspectRatioMode.FIT
        private set

    val isMpvInitialized: Boolean
        get() = mpvEngine != null

    val isExoInitialized: Boolean
        get() = exoPlayerEngine != null

    val hasInitializedEngine: Boolean
        get() = mpvEngine != null || exoPlayerEngine != null

    private val activeEngineInstance: ManagedEngine?
        get() = if (activeEngine == ENGINE_EXOPLAYER) exoPlayerEngine else mpvEngine

    // Cached preferences applied on lazy creation & engine switch
    private var cachedSubtitleDelayMs: Long = 0L
    private var cachedSubtitleScale: Int = 100
    private var cachedPlaybackSpeed: Float = 1.0f
    private var cachedLooping: Boolean = false
    private var cachedDecoderMode: String = "mediacodec"
    private var cachedSongDetectionMode: Boolean = true
    private var currentPrefEngine: String = ENGINE_AUTO

    // Current media metadata for seamless engine switching
    private var currentMedia: MediaRef? = null
    private var currentMediaTitle: String = ""
    private var currentExternalSubtitles: List<MediaRef> = emptyList()
    private var currentIsLikelyHdr: Boolean = false

    val isPlaying: Boolean
        get() = activeEngineInstance?.isPlaying == true

    val currentPosition: Long
        get() = activeEngineInstance?.currentPosition ?: 0L

    val duration: Long
        get() = activeEngineInstance?.duration ?: 0L

    private fun ensureMpvEngine(): ManagedEngine {
        mpvEngine?.let { return it }
        val callbacks = EngineCallbacks(
            onPlaybackStateChanged = { playing ->
                if (activeEngine == ENGINE_MPV) listener.onPlaybackStateChanged(playing)
            },
            onPositionChanged = { pos, dur ->
                if (activeEngine == ENGINE_MPV) listener.onPositionChanged(pos, dur)
            },
            onVideoReady = { dur ->
                if (activeEngine == ENGINE_MPV) {
                    reapplyAspectRatioIfNeeded()
                    listener.onVideoReady(dur)
                }
            },
            onPlayerError = { msg ->
                if (activeEngine == ENGINE_MPV) listener.onPlayerError(msg)
            },
            onPlaybackEnded = {
                if (activeEngine == ENGINE_MPV) listener.onPlaybackEnded()
            },
            onHdrStateDetected = { isHdr, hdrType ->
                if (activeEngine == ENGINE_MPV) {
                    if (isHdr && currentPrefEngine == ENGINE_AUTO) {
                        switchActiveEngine(ENGINE_EXOPLAYER)
                    } else {
                        listener.onHdrStateDetected(isHdr, hdrType)
                    }
                }
            }
        )
        val created = createMpvEngine(callbacks)
        mpvEngine = created
        applyCachedConfigTo(created, isMpv = true)
        return created
    }

    private fun ensureExoPlayerEngine(): ManagedEngine {
        exoPlayerEngine?.let { return it }
        val callbacks = EngineCallbacks(
            onPlaybackStateChanged = { playing ->
                if (activeEngine == ENGINE_EXOPLAYER) listener.onPlaybackStateChanged(playing)
            },
            onPositionChanged = { pos, dur ->
                if (activeEngine == ENGINE_EXOPLAYER) listener.onPositionChanged(pos, dur)
            },
            onVideoReady = { dur ->
                if (activeEngine == ENGINE_EXOPLAYER) {
                    reapplyAspectRatioIfNeeded()
                    listener.onVideoReady(dur)
                }
            },
            onPlayerError = { msg ->
                if (activeEngine == ENGINE_EXOPLAYER) listener.onPlayerError(msg)
            },
            onPlaybackEnded = {
                if (activeEngine == ENGINE_EXOPLAYER) listener.onPlaybackEnded()
            },
            onHdrStateDetected = { isHdr, hdrType ->
                if (activeEngine == ENGINE_EXOPLAYER) {
                    listener.onHdrStateDetected(isHdr, hdrType)
                }
            }
        )
        val created = createExoPlayerEngine(callbacks)
        exoPlayerEngine = created
        applyCachedConfigTo(created, isMpv = false)
        return created
    }

    private fun applyCachedConfigTo(engine: ManagedEngine, isMpv: Boolean) {
        engine.setPlaybackSpeed(cachedPlaybackSpeed)
        engine.setLooping(cachedLooping)
        engine.setSubtitleScale(cachedSubtitleScale)
        engine.setSongDetectionMode(cachedSongDetectionMode)
        if (isMpv) {
            engine.setSubtitleDelay(cachedSubtitleDelayMs)
            engine.setDecoderMode(cachedDecoderMode)
        }
        if (currentAspectRatioMode != AspectRatioMode.FIT) {
            val (w, h) = getDisplaySizePx()
            engine.setAspectRatio(currentAspectRatioMode, w, h)
        }
    }

    fun playMedia(
        uri: Uri,
        title: String,
        startPositionMs: Long,
        externalSubtitles: List<Uri> = emptyList(),
        prefEngine: String = currentPrefEngine,
        isLikelyHdrOrCamera: Boolean = false
    ) {
        playMediaRef(
            media = MediaRef(uri.toString(), uri),
            title = title,
            startPositionMs = startPositionMs,
            externalSubtitles = externalSubtitles.map { MediaRef(it.toString(), it) },
            prefEngine = prefEngine,
            isLikelyHdrOrCamera = isLikelyHdrOrCamera
        )
    }

    internal fun playMediaRef(
        media: MediaRef,
        title: String,
        startPositionMs: Long,
        externalSubtitles: List<MediaRef> = emptyList(),
        prefEngine: String = currentPrefEngine,
        isLikelyHdrOrCamera: Boolean = false
    ) {
        currentMedia = media
        currentMediaTitle = title
        currentExternalSubtitles = externalSubtitles
        currentPrefEngine = prefEngine
        currentIsLikelyHdr = isLikelyHdrOrCamera

        val targetEngine = determineTargetEngine(prefEngine, isLikelyHdrOrCamera)
        activeEngine = targetEngine

        if (targetEngine == ENGINE_EXOPLAYER) {
            mpvEngine?.pause()
            val exo = ensureExoPlayerEngine()
            onSurfaceVisibilityChanged(ENGINE_EXOPLAYER)
            exo.playMedia(media, title, startPositionMs, externalSubtitles)
        } else {
            exoPlayerEngine?.pause()
            // Ensure MPV engine & holder callback are registered before surfacing the view.
            val mpv = ensureMpvEngine()
            onSurfaceVisibilityChanged(ENGINE_MPV)
            mpv.playMedia(media, title, startPositionMs, externalSubtitles)
        }
    }

    fun switchActiveEngine(newEngine: String) {
        val target = if (newEngine == ENGINE_AUTO) {
            determineTargetEngine(ENGINE_AUTO, currentIsLikelyHdr)
        } else {
            newEngine
        }
        if (target == activeEngine) return

        val media = currentMedia ?: return
        val currentPos = currentPosition
        val currentPlaying = isPlaying

        activeEngine = target

        if (target == ENGINE_EXOPLAYER) {
            mpvEngine?.pause()
            val exo = ensureExoPlayerEngine()
            onSurfaceVisibilityChanged(ENGINE_EXOPLAYER)
            exo.playMedia(media, currentMediaTitle, currentPos, currentExternalSubtitles)
            if (!currentPlaying) exo.pause()
        } else {
            exoPlayerEngine?.pause()
            val mpv = ensureMpvEngine()
            onSurfaceVisibilityChanged(ENGINE_MPV)
            mpv.playMedia(media, currentMediaTitle, currentPos, currentExternalSubtitles)
            if (!currentPlaying) mpv.pause()
        }
    }

    fun play() {
        activeEngineInstance?.play()
    }

    fun pause() {
        activeEngineInstance?.pause()
    }

    fun seekTo(positionMs: Long, fast: Boolean = false) {
        activeEngineInstance?.seekTo(positionMs, fast)
    }

    fun seekBy(deltaMs: Long): Long {
        val cur = currentPosition
        val dur = duration
        val target = (cur + deltaMs).coerceIn(0L, if (dur > 0) dur else Long.MAX_VALUE)
        seekTo(target)
        return target
    }

    fun applyPreferences(
        subtitleDelayMs: Long,
        subtitleScale: Int,
        playbackSpeed: Float,
        isLoopEnabled: Boolean,
        decoderMode: String,
        isSongDetectionEnabled: Boolean,
        playerEngineMode: String,
        isLoaded: Boolean
    ) {
        setSubtitleDelay(subtitleDelayMs)
        setSubtitleScale(subtitleScale)
        setPlaybackSpeed(playbackSpeed)

        if (isLoaded) {
            currentPrefEngine = playerEngineMode
            setLooping(isLoopEnabled)
            setDecoderMode(decoderMode)
            setSongDetectionMode(isSongDetectionEnabled)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        cachedPlaybackSpeed = speed
        exoPlayerEngine?.setPlaybackSpeed(speed)
        mpvEngine?.setPlaybackSpeed(speed)
    }

    fun setLooping(enabled: Boolean) {
        cachedLooping = enabled
        exoPlayerEngine?.setLooping(enabled)
        mpvEngine?.setLooping(enabled)
    }

    fun setSubtitleScale(scalePercent: Int) {
        cachedSubtitleScale = scalePercent
        exoPlayerEngine?.setSubtitleScale(scalePercent)
        mpvEngine?.setSubtitleScale(scalePercent)
    }

    fun setSubtitleDelay(delayMs: Long) {
        cachedSubtitleDelayMs = delayMs
        mpvEngine?.setSubtitleDelay(delayMs)
    }

    fun setDecoderMode(mode: String) {
        cachedDecoderMode = mode
        mpvEngine?.setDecoderMode(mode)
    }

    fun setSongDetectionMode(enabled: Boolean) {
        cachedSongDetectionMode = enabled
        exoPlayerEngine?.setSongDetectionMode(enabled)
        mpvEngine?.setSongDetectionMode(enabled)
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        currentAspectRatioMode = mode
        val (w, h) = getDisplaySizePx()
        exoPlayerEngine?.setAspectRatio(mode, w, h)
        mpvEngine?.setAspectRatio(mode, w, h)
    }

    fun cycleAspectRatio(): AspectRatioMode {
        val next = currentAspectRatioMode.nextCycle()
        setAspectRatio(next)
        return next
    }

    fun reapplyAspectRatioIfNeeded() {
        if (currentAspectRatioMode != AspectRatioMode.FIT || exoPlayerEngine != null) {
            val (w, h) = getDisplaySizePx()
            activeEngineInstance?.setAspectRatio(currentAspectRatioMode, w, h)
        }
    }

    fun loadExternalSubtitle(uri: Uri, select: Boolean = true) {
        loadExternalSubtitleRef(MediaRef(uri.toString(), uri), select)
    }

    internal fun loadExternalSubtitleRef(sub: MediaRef, select: Boolean = true) {
        if (currentExternalSubtitles.none { it.uriString == sub.uriString }) {
            currentExternalSubtitles = currentExternalSubtitles + sub
        }
        activeEngineInstance?.loadExternalSubtitle(sub, select)
    }

    fun getSubtitleTracks(): List<SubtitleTrackInfo> {
        return activeEngineInstance?.getSubtitleTracks().orEmpty()
    }

    fun selectSubtitleTrack(trackId: Int) {
        activeEngineInstance?.selectSubtitleTrack(trackId)
    }

    fun getAudioTracks(): List<AudioTrackInfo> {
        return activeEngineInstance?.getAudioTracks().orEmpty()
    }

    fun selectAudioTrack(trackId: Int) {
        activeEngineInstance?.selectAudioTrack(trackId)
    }

    fun getVideoSize(): Pair<Int, Int>? {
        return activeEngineInstance?.getVideoSize()
    }

    fun pauseAll(stopPlayback: Boolean = false) {
        exoPlayerEngine?.let { engine ->
            try {
                if (stopPlayback) engine.stop() else engine.pause()
            } catch (e: Exception) {
                Log.e("DualEngineManager", "Error stopping/pausing ExoPlayer: ${e.message}")
            }
        }
        mpvEngine?.let { engine ->
            try {
                if (stopPlayback) engine.stop() else engine.pause()
            } catch (e: Exception) {
                Log.e("DualEngineManager", "Error stopping/pausing MPV: ${e.message}")
            }
        }
    }

    fun release() {
        pauseAll(stopPlayback = true)
        exoPlayerEngine?.let { engine ->
            try {
                engine.release()
            } catch (e: Exception) {
                Log.e("DualEngineManager", "Error releasing ExoPlayer: ${e.message}")
            }
        }
        mpvEngine?.let { engine ->
            try {
                engine.release()
            } catch (e: Exception) {
                Log.e("DualEngineManager", "Error releasing MPV: ${e.message}")
            }
        }
        exoPlayerEngine = null
        mpvEngine = null
    }

    private class MpvManagedEngineAdapter(
        private val engine: PlayerEngine
    ) : ManagedEngine {
        override val isPlaying: Boolean get() = engine.isPlaying
        override val currentPosition: Long get() = engine.currentPosition
        override val duration: Long get() = engine.duration

        override fun playMedia(
            media: MediaRef,
            title: String,
            startPositionMs: Long,
            externalSubtitles: List<MediaRef>
        ) {
            engine.playMedia(
                videoUri = media.toUri(),
                title = title,
                startPositionMs = startPositionMs,
                externalSubtitles = externalSubtitles.map { it.toUri() }
            )
        }

        override fun play() = engine.play()
        override fun pause() = engine.pause()
        override fun stop() = engine.stop()
        override fun seekTo(positionMs: Long, fast: Boolean) = engine.seekTo(positionMs, fast)
        override fun setPlaybackSpeed(speed: Float) = engine.setPlaybackSpeed(speed)
        override fun setLooping(enabled: Boolean) = engine.setLooping(enabled)
        override fun loadExternalSubtitle(sub: MediaRef, select: Boolean) =
            engine.loadExternalSubtitle(sub.toUri(), select)
        override fun setSubtitleScale(scalePercent: Int) = engine.setSubtitleScale(scalePercent)
        override fun setSubtitleDelay(delayMs: Long) = engine.setSubtitleDelay(delayMs)
        override fun setDecoderMode(mode: String) = engine.setDecoderMode(mode)
        override fun setSongDetectionMode(enabled: Boolean) = engine.setSongDetectionMode(enabled)
        override fun setAspectRatio(mode: AspectRatioMode, displayWidthPx: Int, displayHeightPx: Int) =
            engine.setAspectRatio(mode, displayWidthPx, displayHeightPx)
        override fun getSubtitleTracks(): List<SubtitleTrackInfo> = engine.getSubtitleTracks()
        override fun selectSubtitleTrack(trackId: Int) = engine.selectSubtitleTrack(trackId)
        override fun getAudioTracks(): List<AudioTrackInfo> = engine.getAudioTracks()
        override fun selectAudioTrack(trackId: Int) = engine.selectAudioTrack(trackId)
        override fun getVideoSize(): Pair<Int, Int>? = engine.getVideoSize()
        override fun release() = engine.release()
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private class ExoManagedEngineAdapter(
        private val engine: ExoPlayerEngine
    ) : ManagedEngine {
        override val isPlaying: Boolean get() = engine.isPlaying
        override val currentPosition: Long get() = engine.currentPosition
        override val duration: Long get() = engine.duration

        override fun playMedia(
            media: MediaRef,
            title: String,
            startPositionMs: Long,
            externalSubtitles: List<MediaRef>
        ) {
            engine.playMedia(
                videoUri = media.toUri(),
                startPositionMs = startPositionMs,
                title = title,
                externalSubtitles = externalSubtitles.map { it.toUri() }
            )
        }

        override fun play() = engine.play()
        override fun pause() = engine.pause()
        override fun stop() = engine.stop()
        override fun seekTo(positionMs: Long, fast: Boolean) = engine.seekTo(positionMs)
        override fun setPlaybackSpeed(speed: Float) = engine.setPlaybackSpeed(speed)
        override fun setLooping(enabled: Boolean) = engine.setLooping(enabled)
        override fun loadExternalSubtitle(sub: MediaRef, select: Boolean) =
            engine.loadExternalSubtitle(sub.toUri(), select)
        override fun setSubtitleScale(scalePercent: Int) = engine.setSubtitleScale(scalePercent)
        override fun setSongDetectionMode(enabled: Boolean) = engine.setSongDetectionMode(enabled)
        override fun setAspectRatio(mode: AspectRatioMode, displayWidthPx: Int, displayHeightPx: Int) =
            engine.setAspectRatio(mode)
        override fun getSubtitleTracks(): List<SubtitleTrackInfo> = engine.getSubtitleTracks()
        override fun selectSubtitleTrack(trackId: Int) = engine.selectSubtitleTrack(trackId)
        override fun getAudioTracks(): List<AudioTrackInfo> = engine.getAudioTracks()
        override fun selectAudioTrack(trackId: Int) = engine.selectAudioTrack(trackId)
        override fun getVideoSize(): Pair<Int, Int>? = engine.getVideoSize()
        override fun release() = engine.release()
    }

    companion object {
        const val ENGINE_AUTO = "auto"
        const val ENGINE_MPV = "mpv"
        const val ENGINE_EXOPLAYER = "exoplayer"

        fun determineTargetEngine(prefEngine: String, isLikelyHdrOrCamera: Boolean): String {
            return when (prefEngine) {
                ENGINE_EXOPLAYER -> ENGINE_EXOPLAYER
                ENGINE_MPV -> ENGINE_MPV
                else -> if (isLikelyHdrOrCamera) ENGINE_EXOPLAYER else ENGINE_MPV
            }
        }
    }
}
