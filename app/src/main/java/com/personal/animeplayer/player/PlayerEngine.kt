package com.personal.animeplayer.player

import android.content.Context
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import com.personal.animeplayer.data.model.AudioTrackInfo
import com.personal.animeplayer.data.model.SubtitleTrackInfo
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
    @Volatile
    private var isReleased = false
    private var pendingStartPositionMs: Long = 0L
    private var pendingExternalSubtitles: List<Uri> = emptyList()
    private var currentMediaTitle: String = ""

    private val pfdLock = Any()
    private val activePfds = mutableListOf<ParcelFileDescriptor>()
    private val retiringPfds = mutableListOf<ParcelFileDescriptor>()

    private fun rotateActivePfdsForNewMedia() {
        val staleToClose: List<ParcelFileDescriptor>
        synchronized(pfdLock) {
            staleToClose = retiringPfds.toList()
            retiringPfds.clear()
            retiringPfds.addAll(activePfds)
            activePfds.clear()
        }
        staleToClose.forEach { pfd ->
            try {
                pfd.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun closeRetiringPfds() {
        val toClose: List<ParcelFileDescriptor>
        synchronized(pfdLock) {
            toClose = retiringPfds.toList()
            retiringPfds.clear()
        }
        toClose.forEach { pfd ->
            try {
                pfd.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun closeAllPfds() {
        val toClose: List<ParcelFileDescriptor>
        synchronized(pfdLock) {
            toClose = retiringPfds + activePfds
            retiringPfds.clear()
            activePfds.clear()
        }
        toClose.forEach { pfd ->
            try {
                pfd.close()
            } catch (_: Exception) {
            }
        }
    }

    init {
        initMpv()
    }

    private fun initMpv() {
        try {
            val configDir = context.filesDir
            val cacheDir = File(context.cacheDir, "mpv").apply { mkdirs() }
            val fontsDir = File(configDir, "fonts").apply { mkdirs() }

            if (!File(configDir, "subfont.ttf").exists() || !File(fontsDir, "default.ttf").exists()) {
                // Unpack bundled MPV assets (subfont.ttf and cacert.pem) only when missing
                `is`.xyz.mpv.Utils.copyAssets(context)
                setupFonts(configDir, fontsDir)
            }

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

                // Matroska Ordered Chapters & Segment Linking
                mpv.setOptionString("ordered-chapters", "yes")
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
                mpv.observeProperty("video-params/pixelformat", MPV.mpvFormat.MPV_FORMAT_STRING)
            }

            mpvView.initialize(configDir.absolutePath, cacheDir.absolutePath)
            isMpvInitialized = true
            if (mpvView.holder.surface?.isValid == true) {
                mpvView.surfaceCreated(mpvView.holder)
                if (mpvView.width > 0 && mpvView.height > 0) {
                    mpvView.surfaceChanged(
                        mpvView.holder,
                        PixelFormat.RGBA_8888,
                        mpvView.width,
                        mpvView.height
                    )
                }
            }
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

    override fun eventProperty(property: String) {}

    override fun eventProperty(property: String, value: Boolean) {
        if (isReleased) return
        if (property == "pause") {
            isPlaying = !value
            mainHandler.post {
                if (!isReleased) onPlaybackStateChanged(!value)
            }
        } else if (property == "eof-reached" && value) {
            if (isLoopingEnabled && !isStopped) {
                mainHandler.post {
                    if (!isReleased && !isStopped) {
                        seekTo(0L)
                        play()
                    }
                }
            } else {
                mainHandler.post {
                    if (!isReleased) {
                        audioFocusHelper.abandonFocus(delayedMs = 0L)
                        onPlaybackEnded()
                    }
                }
            }
        }
    }

    override fun eventProperty(property: String, value: Long) {}

    override fun eventProperty(property: String, value: Double) {
        if (isReleased) return
        when (property) {
            "time-pos" -> {
                val posMs = (value * 1000).toLong()
                mainHandler.post {
                    if (!isReleased) onPositionChanged(posMs, currentDurationMs)
                }
            }
            "duration" -> {
                val durMs = (value * 1000).toLong()
                if (durMs > 0) {
                    currentDurationMs = durMs
                    mainHandler.post {
                        if (!isReleased) onVideoReady(durMs)
                    }
                }
            }
        }
    }

    override fun eventProperty(property: String, value: String) {
        if (isReleased) return
        when (property) {
            "video-params/gamma", "video-params/primaries", "video-params/pixelformat" -> {
                checkHdrState()
            }
        }
    }

    override fun eventProperty(property: String, value: MPVNode) {}

    override fun event(eventId: Int, node: MPVNode) {
        if (isReleased) return
        if (eventId == MPV.mpvEvent.MPV_EVENT_FILE_LOADED) {
            closeRetiringPfds()
            mainHandler.post {
                if (isReleased) return@post
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
                if (pendingExternalSubtitles.isNotEmpty()) {
                    val subsToLoad = pendingExternalSubtitles
                    pendingExternalSubtitles = emptyList()
                    subsToLoad.forEach { subUri ->
                        loadExternalSubtitle(subUri, select = false)
                    }
                }
            }
        }
    }

    fun checkHdrState() {
        if (isReleased) return
        val gamma = mpv.getPropertyString("video-params/gamma") ?: ""
        val primaries = mpv.getPropertyString("video-params/primaries") ?: ""
        val pixelFormat = mpv.getPropertyString("video-params/pixelformat") ?: ""

        val isPq = gamma.equals("pq", ignoreCase = true)
        val isHlg = gamma.equals("hlg", ignoreCase = true)
        val is10Bit = pixelFormat.contains("10", ignoreCase = true) || pixelFormat.contains("p010", ignoreCase = true)
        val isBt2020 = primaries.contains("2020", ignoreCase = true)

        val isExplicitHdr10Plus = REGEX_HDR10_PLUS.containsMatchIn(currentMediaTitle)
        val isExplicitHdr10 = REGEX_HDR10.containsMatchIn(currentMediaTitle)
        val isExplicitHdr = REGEX_HDR.containsMatchIn(currentMediaTitle)

        val (isHdr, type) = when {
            isExplicitHdr10Plus -> true to "HDR10+"
            isPq && isBt2020 -> true to "HDR10"
            isPq -> true to "HDR10"
            isHlg -> true to "HLG"
            is10Bit && isBt2020 -> true to "HDR"
            isExplicitHdr10 -> true to "HDR10"
            isExplicitHdr -> true to "HDR"
            else -> false to "SDR"
        }

        mainHandler.post {
            if (!isReleased) onHdrStateDetected(isHdr, type)
        }
    }

    fun setDecoderMode(mode: String) {
        if (!isMpvInitialized || isReleased) return
        try {
            mpv.setPropertyString("hwdec", mode)
        } catch (e: Exception) {
            Log.w("PlayerEngine", "Failed to set decoder mode: ${e.message}")
        }
    }

    fun setSongDetectionMode(enabled: Boolean) {
        audioFocusHelper.songDetectionMode = enabled
    }

    fun setAspectRatio(mode: AspectRatioMode, displayWidthPx: Int, displayHeightPx: Int) {
        if (!isMpvInitialized || isReleased) return
        try {
            when (mode) {
                AspectRatioMode.FIT -> {
                    mpv.setPropertyString("video-aspect-override", "-1")
                    mpv.setPropertyDouble("panscan", 0.0)
                }
                AspectRatioMode.STRETCH -> {
                    if (displayWidthPx > 0 && displayHeightPx > 0) {
                        val displayAspect = displayWidthPx.toDouble() / displayHeightPx.toDouble()
                        mpv.setPropertyString(
                            "video-aspect-override",
                            String.format(java.util.Locale.US, "%.4f", displayAspect)
                        )
                    }
                    mpv.setPropertyDouble("panscan", 0.0)
                }
                AspectRatioMode.ZOOM -> {
                    mpv.setPropertyString("video-aspect-override", "-1")
                    mpv.setPropertyDouble("panscan", 1.0)
                }
                AspectRatioMode.SIXTEEN_NINE -> {
                    mpv.setPropertyString("video-aspect-override", "16:9")
                    mpv.setPropertyDouble("panscan", 0.0)
                }
                AspectRatioMode.FOUR_THREE -> {
                    mpv.setPropertyString("video-aspect-override", "4:3")
                    mpv.setPropertyDouble("panscan", 0.0)
                }
            }
        } catch (e: Exception) {
            Log.w("PlayerEngine", "Failed to set MPV aspect ratio: ${e.message}")
        }
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
        } finally {
            closeAllPfds()
        }
        audioFocusHelper.abandonFocus(delayedMs = 0L)
    }

    fun seekTo(positionMs: Long, fast: Boolean = false) {
        val seconds = (positionMs.coerceAtLeast(0L) / 1000.0)
        val flag = if (fast) "absolute+keyframes" else "absolute"
        mpv.command("seek", seconds.toString(), flag)
    }

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

    /**
     * Resolves the real filesystem path for a media or subtitle URI so MPV can
     * directly access its directory (essential for Matroska Ordered Chapters / Segment Linking
     * and external subtitle auto-detection). Falls back to fd:// if direct path is unavailable.
     */
    private fun getRealPathFromUri(uri: Uri): String? {
        if (uri.scheme == "file") return uri.path
        if (uri.scheme != "content") return null

        // 1. Try querying MediaStore DATA column
        try {
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val colIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (colIdx != -1 && cursor.moveToFirst()) {
                    val path = cursor.getString(colIdx)
                    if (!path.isNullOrBlank()) return path
                }
            }
        } catch (e: Exception) {
            // Ignore and try fallback
        }

        // 2. Try parsing external storage documents uri (SAF)
        try {
            if ("com.android.externalstorage.documents" == uri.authority) {
                val docId = DocumentsContract.getDocumentId(uri)
                val parts = docId.split(":")
                if (parts.size >= 2) {
                    val type = parts[0]
                    val relativePath = parts[1]
                    return if ("primary".equals(type, ignoreCase = true)) {
                        "/storage/emulated/0/$relativePath"
                    } else {
                        "/storage/$type/$relativePath"
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        return null
    }

    private fun resolveMediaFilePath(uri: Uri): String {
        val realPath = getRealPathFromUri(uri)
        if (!realPath.isNullOrBlank()) {
            val file = File(realPath)
            if (file.exists() && file.canRead()) {
                Log.d("PlayerEngine", "Resolved direct file path for MPV: $realPath")
                return realPath
            }
        }

        // Fallback to managed Android ContentResolver FileDescriptor
        if (uri.scheme == "content") {
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    synchronized(pfdLock) {
                        activePfds.add(pfd)
                    }
                    val fdPath = "fd://${pfd.fd}"
                    Log.d("PlayerEngine", "Using managed FileDescriptor fallback for MPV: $fdPath")
                    return fdPath
                }
            } catch (e: Exception) {
                Log.w("PlayerEngine", "Failed to open file descriptor for $uri: ${e.message}")
            }
        }

        return uri.path ?: uri.toString()
    }

    fun playMedia(
        videoUri: Uri,
        title: String,
        startPositionMs: Long = 0L,
        externalSubtitles: List<Uri> = emptyList()
    ) {
        try {
            currentMediaTitle = title
            mainHandler.post {
                if (!isReleased) onHdrStateDetected(false, "SDR")
            }

            audioFocusHelper.cancelPendingAbandon()
            audioFocusHelper.requestFocus()

            rotateActivePfdsForNewMedia()
            val path = resolveMediaFilePath(videoUri)

            pendingStartPositionMs = startPositionMs
            pendingExternalSubtitles = externalSubtitles

            if (mpvView.holder.surface.isValid) {
                mpv.command("loadfile", path)
            } else {
                mpvView.playFile(path)
            }
            mpv.setPropertyString("loop-file", if (isLoopingEnabled) "inf" else "no")
            mpv.setPropertyBoolean("pause", false)
            isPlaying = true
        } catch (e: Exception) {
            Log.e("PlayerEngine", "Error playing media: ${e.message}", e)
            onPlayerError("Tidak dapat memutar video: ${e.localizedMessage}")
        }
    }

    fun loadExternalSubtitle(subUri: Uri, select: Boolean = false) {
        try {
            val path = resolveMediaFilePath(subUri)
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
                        label = label,
                        language = lang,
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

    fun setSubtitleDelay(delayMs: Long) {
        if (!isMpvInitialized || isReleased) return
        try {
            mpv.setPropertyDouble("sub-delay", delayMs / 1000.0)
        } catch (e: Exception) {
            Log.w("PlayerEngine", "Failed to set subtitle delay: ${e.message}")
        }
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
                        label = label,
                        language = lang,
                        channels = channels,
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

    fun getVideoSize(): Pair<Int, Int>? {
        if (!isMpvInitialized || isReleased) return null
        return try {
            val w = mpv.getPropertyInt("video-out-params/dw")
                ?: mpv.getPropertyInt("dwidth")
                ?: mpv.getPropertyInt("width")
            val h = mpv.getPropertyInt("video-out-params/dh")
                ?: mpv.getPropertyInt("dheight")
                ?: mpv.getPropertyInt("height")
            if (w != null && h != null && w > 0 && h > 0) w to h else null
        } catch (_: Exception) {
            null
        }
    }

    fun release() {
        isReleased = true
        isMpvInitialized = false
        pendingExternalSubtitles = emptyList()
        mainHandler.removeCallbacksAndMessages(null)
        try {
            stop()
            audioFocusHelper.release()
            mpv.removeObserver(this)
            mpvView.destroy()
        } catch (e: Exception) {
            Log.e("PlayerEngine", "Error releasing MPV: ${e.message}")
        } finally {
            closeAllPfds()
        }
    }

    companion object {
        private val REGEX_HDR10_PLUS = Regex("""(?i)(?:^|[^a-z0-9])(hdr10\+|hdr10plus)(?:$|[^a-z0-9])""")
        private val REGEX_HDR10 = Regex("""(?i)(?:^|[^a-z0-9])hdr10(?:$|[^a-z0-9])""")
        private val REGEX_HDR = Regex("""(?i)(?:^|[^a-z0-9])hdr(?:$|[^a-z0-9])""")
    }
}
