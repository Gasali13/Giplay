package com.personal.animeplayer.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.personal.animeplayer.data.model.AudioTrackInfo
import com.personal.animeplayer.data.model.SubtitleTrackInfo
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.MPVNode
import java.io.File
import java.io.FileOutputStream

class PlayerEngine(
    private val context: Context,
    private val mpvView: MPVView,
    private val onPlaybackStateChanged: (isPlaying: Boolean) -> Unit = {},
    private val onPositionChanged: (currentPosMs: Long, durationMs: Long) -> Unit = { _, _ -> },
    private val onVideoReady: (durationMs: Long) -> Unit = {},
    private val onPlayerError: (String) -> Unit = {},
    private val onPlaybackEnded: () -> Unit = {},
    private val onHdrStateDetected: (isHdr: Boolean, hdrType: String) -> Unit = { _, _ -> }
) : MPV.EventObserver {

    val mpv: MPV
        get() = mpvView.mpv

    private val mainHandler = Handler(Looper.getMainLooper())

    val audioFocusHelper = AudioFocusHelper(
        context = context,
        onPausePlayback = {
            mainHandler.post {
                mpv.setPropertyBoolean("pause", true)
            }
        },
        onResumePlayback = {
            mainHandler.post {
                mpv.setPropertyBoolean("pause", false)
            }
        }
    )

    private var currentDurationMs: Long = 0L
    private var isMpvInitialized = false
    private var pendingStartPositionMs: Long = 0L
    private var currentMediaTitle: String = ""

    private var currentHdrType: String = "SDR"
    private var isCurrentVideoHdr: Boolean = false

    init {
        initMpv()
    }

    private fun initMpv() {
        try {
            // Unpack bundled MPV assets (subfont.ttf and cacert.pem)
            `is`.xyz.mpv.Utils.copyAssets(context)

            val configDir = context.filesDir
            val cacheDir = File(context.cacheDir, "mpv").apply { mkdirs() }
            val fontsDir = File(configDir, "fonts").apply { mkdirs() }

            setupFonts(configDir, fontsDir)

            mpvView.onInitOptions = {
                // Configure optimal settings for Video, Anime & HDR10+
                mpv.setOptionString("vo", "gpu")
                mpv.setOptionString("hwdec", "mediacodec")
                mpv.setOptionString("hwdec-codecs", "all")
                mpv.setOptionString("keep-open", "yes")

                // HDR10+ and HDR color pipeline with high-fidelity tone-mapping
                mpv.setOptionString("target-colorspace-hint", "yes")
                mpv.setOptionString("tone-mapping", "bt.2390")
                mpv.setOptionString("tone-mapping-param", "1.0")
                mpv.setOptionString("tone-mapping-max-boost", "2.5")
                mpv.setOptionString("target-peak", "auto")

                // Matikan audio-stream-silence agar audio benar-benar terputus saat jeda/berhenti
                mpv.setOptionString("audio-stream-silence", "no")
                mpv.setOptionString("audio-wait-open", "0")
                mpv.setOptionString("hr-seek-framedrop", "no")

                // Subtitle rendering configuration
                mpv.setOptionString("sub-ass-override", "scale")
                mpv.setOptionString("sub-auto", "fuzzy")
                mpv.setOptionString("sub-fix-timing", "yes")
                mpv.setOptionString("embeddedfonts", "yes")
                mpv.setOptionString("sub-fonts-dir", fontsDir.absolutePath)
                mpv.setOptionString("sub-font", "subfont")
            }

            mpvView.onSurfaceDestroyedListener = {
                try {
                    // Ketika surface dihancurkan (misal saat jendela PiP ditutup tanda X),
                    // audio MPV harus segera dihentikan / dijeda agar tidak bocor ke latar belakang
                    mpv.setPropertyBoolean("pause", true)
                } catch (e: Exception) {}
                audioFocusHelper.abandonFocus(delayedMs = 0L)
            }

            mpvView.onObserveProperties = {
                mpv.addObserver(this)

                // Observe playback properties
                mpv.observeProperty("pause", MPV.mpvFormat.MPV_FORMAT_FLAG)
                mpv.observeProperty("time-pos", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
                mpv.observeProperty("duration", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
                mpv.observeProperty("track-list", MPV.mpvFormat.MPV_FORMAT_NONE)
                mpv.observeProperty("eof-reached", MPV.mpvFormat.MPV_FORMAT_FLAG)

                // Observe video parameters for dynamic HDR detection
                mpv.observeProperty("video-params/gamma", MPV.mpvFormat.MPV_FORMAT_STRING)
                mpv.observeProperty("video-params/primaries", MPV.mpvFormat.MPV_FORMAT_STRING)
                mpv.observeProperty("video-params/colormatrix", MPV.mpvFormat.MPV_FORMAT_STRING)
                mpv.observeProperty("video-params/pixelformat", MPV.mpvFormat.MPV_FORMAT_STRING)
            }

            mpvView.initialize(configDir.absolutePath, cacheDir.absolutePath)
            isMpvInitialized = true
        } catch (e: Exception) {
            Log.e("PlayerEngine", "Failed to initialize MPV: ${e.message}", e)
            onPlayerError("Gagal menginisialisasi engine MPV: ${e.localizedMessage}")
        }
    }

    private fun setupFonts(configDir: File, fontsDir: File) {
        try {
            // Copy anime_font.ttf from assets if available
            val animeFont = File(fontsDir, "anime_font.ttf")
            if (!animeFont.exists()) {
                try {
                    context.assets.open("fonts/anime_font.ttf").use { input ->
                        FileOutputStream(animeFont).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("PlayerEngine", "Could not copy anime_font.ttf: ${e.message}")
                }
            }

            val subfontFile = File(context.filesDir, "subfont.ttf")
            val sourceFont = if (subfontFile.exists() && subfontFile.length() > 0) subfontFile else animeFont

            if (sourceFont.exists()) {
                // Ensure subfont.ttf exists in configDir and fontsDir
                val configSubfont = File(configDir, "subfont.ttf")
                if (!configSubfont.exists()) {
                    sourceFont.copyTo(configSubfont, overwrite = true)
                }
                val fontsSubfont = File(fontsDir, "subfont.ttf")
                if (!fontsSubfont.exists()) {
                    sourceFont.copyTo(fontsSubfont, overwrite = true)
                }

                // Map fansub dialogue fonts (like Cronos Pro Light) directly to font file
                val cronos1 = File(fontsDir, "Cronos Pro Light.ttf")
                val cronos2 = File(fontsDir, "CronosPro-Light.ttf")
                val defaultFont = File(fontsDir, "default.ttf")
                if (!cronos1.exists()) sourceFont.copyTo(cronos1, overwrite = true)
                if (!cronos2.exists()) sourceFont.copyTo(cronos2, overwrite = true)
                if (!defaultFont.exists()) sourceFont.copyTo(defaultFont, overwrite = true)
            }
        } catch (e: Exception) {
            Log.w("PlayerEngine", "Error setting up fonts: ${e.message}")
        }
    }

    override fun eventProperty(property: String) {
        if (property == "track-list") {
            // Tracks changed or loaded
        }
    }

    override fun eventProperty(property: String, value: Boolean) {
        if (property == "pause") {
            isPlaying = !value
            mainHandler.post {
                onPlaybackStateChanged(!value)
            }
        } else if (property == "eof-reached" && value) {
            if (isLoopingEnabled && !isStopped) {
                mainHandler.post {
                    if (!isStopped) {
                        seekTo(0L)
                        play()
                    }
                }
            } else {
                mainHandler.post {
                    audioFocusHelper.abandonFocus(delayedMs = 0L)
                    onPlaybackEnded()
                }
            }
        }
    }

    override fun eventProperty(property: String, value: Long) {}

    override fun eventProperty(property: String, value: Double) {
        when (property) {
            "time-pos" -> {
                val posMs = (value * 1000).toLong()
                mainHandler.post {
                    onPositionChanged(posMs, currentDurationMs)
                }
            }
            "duration" -> {
                val durMs = (value * 1000).toLong()
                if (durMs > 0) {
                    currentDurationMs = durMs
                    mainHandler.post {
                        onVideoReady(durMs)
                    }
                }
            }
        }
    }

    override fun eventProperty(property: String, value: String) {
        when (property) {
            "video-params/gamma", "video-params/primaries", "video-params/pixelformat" -> {
                checkHdrState()
            }
        }
    }

    override fun eventProperty(property: String, value: MPVNode) {}

    override fun event(eventId: Int, node: MPVNode) {
        if (eventId == MPV.mpvEvent.MPV_EVENT_FILE_LOADED) {
            mainHandler.post {
                checkHdrState()
                mpv.setPropertyString("loop-file", if (isLoopingEnabled) "inf" else "no")
                val durSec = mpv.getPropertyDouble("duration") ?: 0.0
                if (durSec > 0) {
                    currentDurationMs = (durSec * 1000).toLong()
                    onVideoReady(currentDurationMs)
                }
                if (pendingStartPositionMs > 0) {
                    seekTo(pendingStartPositionMs)
                    pendingStartPositionMs = 0L
                }
            }
        }
    }

    fun checkHdrState() {
        val gamma = mpv.getPropertyString("video-params/gamma") ?: ""
        val primaries = mpv.getPropertyString("video-params/primaries") ?: ""
        val pixelFormat = mpv.getPropertyString("video-params/pixelformat") ?: ""

        val isPq = gamma.equals("pq", ignoreCase = true)
        val isHlg = gamma.equals("hlg", ignoreCase = true)
        val is10Bit = pixelFormat.contains("10", ignoreCase = true) || pixelFormat.contains("p010", ignoreCase = true)
        val isBt2020 = primaries.contains("2020", ignoreCase = true)

        val titleLower = currentMediaTitle.lowercase()
        val isExplicitHdr10Plus = titleLower.contains("hdr10plus") || titleLower.contains("hdr10+")

        val (isHdr, type) = when {
            isExplicitHdr10Plus -> true to "HDR10+"
            isPq && isBt2020 -> {
                if (titleLower.contains("hdr10plus") || titleLower.contains("hdr10+")) true to "HDR10+"
                else true to "HDR10"
            }
            isPq -> true to "HDR10"
            isHlg -> true to "HLG"
            is10Bit && isBt2020 -> true to "HDR"
            titleLower.contains("hdr10") -> true to "HDR10"
            titleLower.contains("hdr") -> true to "HDR"
            else -> false to "SDR"
        }

        isCurrentVideoHdr = isHdr
        currentHdrType = type
        mainHandler.post {
            onHdrStateDetected(isHdr, type)
        }
    }

    fun setDecoderMode(mode: String) {
        try {
            mpv.setPropertyString("hwdec", mode)
        } catch (e: Exception) {
            Log.w("PlayerEngine", "Failed to set decoder mode: ${e.message}")
        }
    }

    fun getDecoderMode(): String {
        return mpv.getPropertyString("hwdec") ?: "mediacodec"
    }

    var isPlaying: Boolean = false
        private set

    var isStopped: Boolean = false
        private set

    val currentPosition: Long
        get() = ((mpv.getPropertyDouble("time-pos") ?: 0.0) * 1000).toLong()

    val duration: Long
        get() = if (currentDurationMs > 0) currentDurationMs else ((mpv.getPropertyDouble("duration") ?: 0.0) * 1000).toLong()

    fun play() {
        isStopped = false
        isPlaying = true
        audioFocusHelper.cancelPendingAbandon()
        audioFocusHelper.requestFocus()
        mpv.setPropertyBoolean("pause", false)
    }

    fun pause() {
        isPlaying = false
        try {
            mpv.setPropertyBoolean("pause", true)
        } catch (e: Exception) {
            Log.w("PlayerEngine", "Failed to pause MPV: ${e.message}")
        }
        audioFocusHelper.abandonFocus(delayedMs = 0L)
    }

    fun stop() {
        isStopped = true
        isPlaying = false
        try {
            mpv.setPropertyBoolean("pause", true)
            mpv.command("stop")
        } catch (e: Exception) {
            Log.w("PlayerEngine", "Failed to stop MPV: ${e.message}")
        }
        audioFocusHelper.abandonFocus(delayedMs = 0L)
    }

    fun abandonAudioFocus(immediate: Boolean = true) {
        audioFocusHelper.abandonFocus(delayedMs = if (immediate) 0L else 500L)
    }

    fun seekTo(positionMs: Long, fast: Boolean = false) {
        val seconds = (positionMs.coerceAtLeast(0L) / 1000.0)
        val flag = if (fast) "absolute+keyframes" else "absolute"
        mpv.command("seek", seconds.toString(), flag)
    }

    fun seekBy(deltaMs: Long) {
        val deltaSeconds = deltaMs / 1000.0
        mpv.command("seek", deltaSeconds.toString(), "relative")
    }

    val playbackSpeed: Float
        get() = (mpv.getPropertyDouble("speed") ?: 1.0).toFloat()

    fun setPlaybackSpeed(speed: Float) {
        val safeSpeed = speed.coerceIn(0.25f, 4.0f).toDouble()
        mpv.setPropertyDouble("speed", safeSpeed)
    }

    var isLoopingEnabled: Boolean = false
        private set

    fun setLooping(enabled: Boolean) {
        isLoopingEnabled = enabled
        try {
            mpv.setPropertyString("loop-file", if (enabled) "inf" else "no")
            if (enabled && duration > 0 && currentPosition >= duration - 500L) {
                seekTo(0L)
                play()
            }
        } catch (e: Exception) {
            Log.w("PlayerEngine", "Failed to set loop-file: ${e.message}")
        }
    }

    fun playMedia(
        videoUri: Uri,
        title: String,
        startPositionMs: Long = 0L,
        externalSubtitles: List<Uri> = emptyList()
    ) {
        try {
            currentMediaTitle = title
            isCurrentVideoHdr = false
            currentHdrType = "SDR"
            mainHandler.post {
                onHdrStateDetected(false, "SDR")
            }

            audioFocusHelper.cancelPendingAbandon()
            audioFocusHelper.requestFocus()

            val path = if (videoUri.scheme == "content") {
                context.contentResolver.openFileDescriptor(videoUri, "r")?.use { pfd ->
                    "fd://${pfd.detachFd()}"
                } ?: videoUri.toString()
            } else {
                videoUri.path ?: videoUri.toString()
            }

            pendingStartPositionMs = startPositionMs

            if (mpvView.holder.surface.isValid) {
                mpv.command("loadfile", path)
            } else {
                mpvView.playFile(path)
            }
            mpv.setPropertyString("loop-file", if (isLoopingEnabled) "inf" else "no")
            mpv.setPropertyBoolean("pause", false)
            isPlaying = true

            externalSubtitles.forEach { subUri ->
                loadExternalSubtitle(subUri, select = false)
            }
        } catch (e: Exception) {
            Log.e("PlayerEngine", "Error playing media: ${e.message}", e)
            onPlayerError("Tidak dapat memutar video: ${e.localizedMessage}")
        }
    }

    fun loadExternalSubtitle(subUri: Uri, select: Boolean = false) {
        try {
            val path = if (subUri.scheme == "content") {
                context.contentResolver.openFileDescriptor(subUri, "r")?.use { pfd ->
                    "fd://${pfd.detachFd()}"
                } ?: subUri.toString()
            } else {
                subUri.path ?: subUri.toString()
            }
            val flag = if (select) "select" else "auto"
            mpv.command("sub-add", path, flag)
        } catch (e: Exception) {
            Log.e("PlayerEngine", "Failed to add external sub: ${e.message}")
        }
    }

    fun getSubtitleTracks(): List<SubtitleTrackInfo> {
        val count = mpv.getPropertyInt("track-list/count") ?: 0
        val list = mutableListOf<SubtitleTrackInfo>()
        for (i in 0 until count) {
            val type = mpv.getPropertyString("track-list/$i/type")
            if (type == "sub") {
                val id = mpv.getPropertyInt("track-list/$i/id") ?: (i + 1)
                val title = mpv.getPropertyString("track-list/$i/title")
                val lang = mpv.getPropertyString("track-list/$i/lang")
                val selected = mpv.getPropertyBoolean("track-list/$i/selected") ?: false
                val label = when {
                    !title.isNullOrBlank() -> title
                    !lang.isNullOrBlank() -> lang
                    else -> "Subtitle $id"
                }
                list.add(
                    SubtitleTrackInfo(
                        id = id.toString(),
                        label = label,
                        language = lang,
                        trackGroupIndex = id,
                        trackIndex = id,
                        isSelected = selected
                    )
                )
            }
        }
        return list
    }

    fun selectSubtitleTrack(trackId: Int) {
        if (trackId <= 0) {
            mpv.setPropertyString("sid", "no")
        } else {
            mpv.setPropertyInt("sid", trackId)
        }
    }

    fun setSecondarySubtitleTrack(trackId: Int) {
        if (trackId <= 0) {
            mpv.setPropertyString("secondary-sid", "no")
        } else {
            mpv.setPropertyInt("secondary-sid", trackId)
        }
    }

    fun setSubtitleDelay(delayMs: Long) {
        mpv.setPropertyDouble("sub-delay", delayMs / 1000.0)
    }

    fun setSubtitleScale(scalePercent: Int) {
        if (!isMpvInitialized) return
        val scale = (scalePercent / 100.0).coerceIn(0.5, 3.0)
        try {
            mpv.setPropertyString("sub-ass-override", "scale")
            mpv.setPropertyDouble("sub-scale", scale)
            val baseMpvSize = 55.0
            mpv.setPropertyDouble("sub-font-size", baseMpvSize * scale)
        } catch (e: Exception) {
            Log.w("PlayerEngine", "Failed to set sub scale: ${e.message}")
        }
    }

    fun setSubtitleFontSize(sizeSp: Float) {
        // Kept for backward compatibility, routes to scale
        setSubtitleScale((sizeSp / 18.0f * 100f).toInt())
    }

    fun getAudioTracks(): List<AudioTrackInfo> {
        val count = mpv.getPropertyInt("track-list/count") ?: 0
        val list = mutableListOf<AudioTrackInfo>()
        for (i in 0 until count) {
            val type = mpv.getPropertyString("track-list/$i/type")
            if (type == "audio") {
                val id = mpv.getPropertyInt("track-list/$i/id") ?: (i + 1)
                val title = mpv.getPropertyString("track-list/$i/title")
                val lang = mpv.getPropertyString("track-list/$i/lang")
                val channels = mpv.getPropertyInt("track-list/$i/audio-channels") ?: 2
                val selected = mpv.getPropertyBoolean("track-list/$i/selected") ?: false
                val label = when {
                    !title.isNullOrBlank() -> title
                    !lang.isNullOrBlank() -> lang
                    else -> "Audio Track $id"
                }
                list.add(
                    AudioTrackInfo(
                        id = id.toString(),
                        label = label,
                        language = lang,
                        channels = channels,
                        sampleRate = 48000,
                        trackGroupIndex = id,
                        trackIndex = id,
                        isSelected = selected
                    )
                )
            }
        }
        return list
    }

    fun selectAudioTrack(trackId: Int) {
        mpv.setPropertyInt("aid", trackId)
    }

    fun release() {
        try {
            stop()
            mpv.removeObserver(this)
            mpvView.destroy()
        } catch (e: Exception) {
            Log.e("PlayerEngine", "Error releasing MPV: ${e.message}")
        }
    }
}
