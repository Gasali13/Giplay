package com.personal.animeplayer.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.personal.animeplayer.data.model.AudioTrackInfo
import com.personal.animeplayer.data.model.SubtitleTrackInfo

@androidx.annotation.OptIn(UnstableApi::class)
class ExoPlayerEngine(
    private val context: Context,
    private val playerView: PlayerView,
    private val onPlaybackStateChanged: (isPlaying: Boolean) -> Unit = {},
    private val onPositionChanged: (currentPosMs: Long, durationMs: Long) -> Unit = { _, _ -> },
    private val onVideoReady: (durationMs: Long) -> Unit = {},
    private val onPlayerError: (String) -> Unit = {},
    private val onPlaybackEnded: () -> Unit = {},
    private val onHdrStateDetected: (isHdr: Boolean, hdrType: String) -> Unit = { _, _ -> }
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    var exoPlayer: ExoPlayer? = null
        private set

    val audioFocusHelper = AudioFocusHelper(
        context = context,
        onPausePlayback = {
            mainHandler.post { pause() }
        },
        onResumePlayback = {
            mainHandler.post { play() }
        }
    )

    private var isPlayerReady = false
    private var isLooping = false
    private var currentSpeed: Float = 1.0f
    @Volatile
    private var isReleased = false

    private val progressRunnable = object : Runnable {
        override fun run() {
            if (isReleased) return
            exoPlayer?.let { p ->
                if (p.isPlaying) {
                    val pos = p.currentPosition
                    val dur = if (p.duration > 0) p.duration else 0L
                    onPositionChanged(pos, dur)
                }
            }
            mainHandler.postDelayed(this, 300L)
        }
    }

    init {
        initExoPlayer()
    }

    private fun initExoPlayer() {
        val player = ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            playbackParameters = PlaybackParameters(currentSpeed)

            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isReleased) return
                    onPlaybackStateChanged(isPlaying)
                    if (isPlaying) {
                        audioFocusHelper.requestFocus()
                        mainHandler.removeCallbacks(progressRunnable)
                        mainHandler.post(progressRunnable)
                    } else {
                        mainHandler.removeCallbacks(progressRunnable)
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (isReleased) return
                    when (playbackState) {
                        Player.STATE_READY -> {
                            if (!isPlayerReady) {
                                isPlayerReady = true
                                val dur = duration.takeIf { it > 0 } ?: 0L
                                onVideoReady(dur)
                            }
                            inspectHdrFormat()
                        }
                        Player.STATE_ENDED -> {
                            onPlaybackEnded()
                        }
                        Player.STATE_IDLE -> {}
                        Player.STATE_BUFFERING -> {}
                    }
                }

                override fun onTracksChanged(tracks: Tracks) {
                    if (isReleased) return
                    inspectHdrFormat()
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (isReleased) return
                    Log.e("ExoPlayerEngine", "ExoPlayer Error: ${error.message}", error)
                    onPlayerError(error.localizedMessage ?: "Kesalahan pemutaran video ExoPlayer")
                }
            })
        }

        exoPlayer = player
        playerView.player = player
    }

    private var currentTitle: String = ""

    private fun inspectHdrFormat() {
        val player = exoPlayer ?: return
        val format = player.videoFormat
        val isExplicitHdr10Plus = REGEX_HDR10_PLUS.containsMatchIn(currentTitle)
        val isExplicitDolby = REGEX_DOLBY_VISION.containsMatchIn(currentTitle)

        if (format != null) {
            val colorInfo = format.colorInfo
            val transfer = colorInfo?.colorTransfer ?: -1
            val space = colorInfo?.colorSpace ?: -1
            val hdrStaticInfo = colorInfo?.hdrStaticInfo
            val isDolbyCodec = format.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION

            val isHdr = isDolbyCodec ||
                    ColorInfo.isTransferHdr(colorInfo) ||
                    transfer == C.COLOR_TRANSFER_ST2084 ||
                    transfer == C.COLOR_TRANSFER_HLG ||
                    space == C.COLOR_SPACE_BT2020 ||
                    hdrStaticInfo != null ||
                    isExplicitHdr10Plus ||
                    isExplicitDolby

            val type = when {
                isDolbyCodec || isExplicitDolby -> "Dolby Vision"
                isExplicitHdr10Plus -> "HDR10+"
                transfer == C.COLOR_TRANSFER_ST2084 -> "HDR10"
                transfer == C.COLOR_TRANSFER_HLG -> "HLG"
                REGEX_HDR10.containsMatchIn(currentTitle) -> "HDR10"
                isHdr -> "HDR"
                else -> "SDR"
            }
            mainHandler.post {
                if (!isReleased) onHdrStateDetected(isHdr, type)
            }
        } else if (isExplicitHdr10Plus || isExplicitDolby) {
            mainHandler.post {
                if (!isReleased) onHdrStateDetected(true, if (isExplicitDolby) "Dolby Vision" else "HDR10+")
            }
        } else {
            mainHandler.post {
                if (!isReleased) onHdrStateDetected(false, "SDR")
            }
        }
    }

    fun playMedia(
        videoUri: Uri,
        startPositionMs: Long = 0L,
        title: String = "",
        externalSubtitles: List<Uri> = emptyList()
    ) {
        currentTitle = title
        isPlayerReady = false

        val isExplicitHdr10Plus = REGEX_HDR10_PLUS.containsMatchIn(title)
        val isExplicitDolby = REGEX_DOLBY_VISION.containsMatchIn(title)
        val isExplicitHdr10 = REGEX_HDR10.containsMatchIn(title)
        val isExplicitHdr = isExplicitHdr10Plus || isExplicitDolby || isExplicitHdr10 || REGEX_HDR.containsMatchIn(title)

        if (isExplicitHdr) {
            val type = when {
                isExplicitDolby -> "Dolby Vision"
                isExplicitHdr10Plus -> "HDR10+"
                isExplicitHdr10 -> "HDR10"
                else -> "HDR"
            }
            mainHandler.post {
                if (!isReleased) onHdrStateDetected(true, type)
            }
        } else {
            mainHandler.post {
                if (!isReleased) onHdrStateDetected(false, "SDR")
            }
        }

        val subConfigs = externalSubtitles.map { subUri ->
            val ext = subUri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
            val mimeType = when (ext) {
                "ass", "ssa" -> MimeTypes.TEXT_SSA
                "vtt" -> MimeTypes.TEXT_VTT
                else -> MimeTypes.APPLICATION_SUBRIP
            }
            MediaItem.SubtitleConfiguration.Builder(subUri)
                .setMimeType(mimeType)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
        }

        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(title)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(videoUri)
            .setMediaMetadata(mediaMetadata)
            .setSubtitleConfigurations(subConfigs)
            .build()

        exoPlayer?.let { player ->
            val safeStartPos = startPositionMs.coerceAtLeast(0L)
            if (safeStartPos > 0L) {
                player.setMediaItem(mediaItem, safeStartPos)
            } else {
                player.setMediaItem(mediaItem)
            }
            player.prepare()
            player.play()
        }
    }

    fun play() {
        audioFocusHelper.requestFocus()
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.playWhenReady = false
        exoPlayer?.pause()
        audioFocusHelper.abandonFocus(delayedMs = 0L)
    }

    fun stop() {
        try {
            mainHandler.removeCallbacks(progressRunnable)
            audioFocusHelper.abandonFocus(delayedMs = 0L)
            exoPlayer?.playWhenReady = false
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
        } catch (e: Exception) {
            Log.e("ExoPlayerEngine", "Error stopping ExoPlayer: ${e.message}")
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun setPlaybackSpeed(speed: Float) {
        val safeSpeed = speed.coerceIn(0.25f, 4.0f)
        currentSpeed = safeSpeed
        exoPlayer?.playbackParameters = PlaybackParameters(safeSpeed)
    }

    fun setLooping(loop: Boolean) {
        isLooping = loop
        exoPlayer?.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun setSongDetectionMode(enabled: Boolean) {
        audioFocusHelper.songDetectionMode = enabled
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        val contentFrame = playerView.findViewById<AspectRatioFrameLayout>(
            androidx.media3.ui.R.id.exo_content_frame
        )
        val videoSize = exoPlayer?.videoSize
        val naturalAspect = if (videoSize != null && videoSize.width > 0 && videoSize.height > 0) {
            (videoSize.width * videoSize.pixelWidthHeightRatio) / videoSize.height.toFloat()
        } else {
            0f
        }

        when (mode) {
            AspectRatioMode.FIT -> {
                if (naturalAspect > 0f) contentFrame?.setAspectRatio(naturalAspect)
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            AspectRatioMode.STRETCH -> {
                if (naturalAspect > 0f) contentFrame?.setAspectRatio(naturalAspect)
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            }
            AspectRatioMode.ZOOM -> {
                if (naturalAspect > 0f) contentFrame?.setAspectRatio(naturalAspect)
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
            AspectRatioMode.SIXTEEN_NINE -> {
                contentFrame?.setAspectRatio(16f / 9f)
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            AspectRatioMode.FOUR_THREE -> {
                contentFrame?.setAspectRatio(4f / 3f)
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        }
    }

    fun loadExternalSubtitle(uri: Uri, select: Boolean = true) {
        val player = exoPlayer ?: return
        val currentItem = player.currentMediaItem ?: return
        val currentPos = player.currentPosition
        val isCurrentlyPlaying = player.isPlaying

        val ext = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
        val mimeType = when (ext) {
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            "vtt" -> MimeTypes.TEXT_VTT
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        val subConfig = MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(mimeType)
            .setSelectionFlags(if (select) C.SELECTION_FLAG_DEFAULT else 0)
            .build()

        val currentSubs = currentItem.localConfiguration?.subtitleConfigurations.orEmpty()
        val updatedItem = currentItem.buildUpon()
            .setSubtitleConfigurations(currentSubs + subConfig)
            .build()

        player.setMediaItem(updatedItem, currentPos)
        if (isCurrentlyPlaying) player.play()
    }

    fun setSubtitleScale(scalePercent: Int) {
        val scale = (scalePercent / 100f).coerceIn(0.5f, 3.0f)
        playerView.subtitleView?.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * scale)
    }

    fun getSubtitleTracks(): List<SubtitleTrackInfo> {
        val tracks = mutableListOf<SubtitleTrackInfo>()
        val player = exoPlayer ?: return tracks
        val currentTracks = player.currentTracks
        var index = 0
        for (group in currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val isSelected = group.isTrackSelected(i)
                    tracks.add(
                        SubtitleTrackInfo(
                            label = format.label ?: format.language ?: "Subtitle #${index + 1}",
                            language = format.language ?: "und",
                            trackIndex = index,
                            isSelected = isSelected
                        )
                    )
                    index++
                }
            }
        }
        return tracks
    }

    fun selectSubtitleTrack(trackId: Int) {
        val player = exoPlayer ?: return
        if (trackId == -1) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            return
        }
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()

        var count = 0
        for (group in player.currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (i in 0 until group.length) {
                    if (count == trackId) {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .setOverrideForType(
                                TrackSelectionOverride(group.mediaTrackGroup, i)
                            )
                            .build()
                        return
                    }
                    count++
                }
            }
        }
    }

    fun getAudioTracks(): List<AudioTrackInfo> {
        val player = exoPlayer ?: return emptyList()
        val list = mutableListOf<AudioTrackInfo>()
        val currentTracks = player.currentTracks

        var index = 0
        for (group in currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val isSelected = group.isTrackSelected(i)
                    val title = format.label ?: format.language ?: "Audio Track ${index + 1}"
                    list.add(
                        AudioTrackInfo(
                            label = title,
                            language = format.language ?: "und",
                            channels = format.channelCount,
                            trackIndex = index,
                            isSelected = isSelected
                        )
                    )
                    index++
                }
            }
        }
        return list
    }

    fun selectAudioTrack(trackIndex: Int) {
        val player = exoPlayer ?: return
        val currentTracks = player.currentTracks

        var count = 0
        for (group in currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    if (count == trackIndex) {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .setOverrideForType(
                                TrackSelectionOverride(group.mediaTrackGroup, i)
                            )
                            .build()
                        return
                    }
                    count++
                }
            }
        }
    }

    val currentPosition: Long
        get() = exoPlayer?.currentPosition ?: 0L

    val duration: Long
        get() = exoPlayer?.duration?.takeIf { it > 0 } ?: 0L

    val isPlaying: Boolean
        get() = exoPlayer?.isPlaying == true

    fun getVideoSize(): Pair<Int, Int>? {
        val size = exoPlayer?.videoSize ?: return null
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) return null
        val adjustedW = if (size.pixelWidthHeightRatio > 0f && size.pixelWidthHeightRatio != 1f) {
            (w * size.pixelWidthHeightRatio).toInt().coerceAtLeast(1)
        } else {
            w
        }
        return adjustedW to h
    }

    fun release() {
        isReleased = true
        try {
            stop()
            mainHandler.removeCallbacksAndMessages(null)
            audioFocusHelper.release()
            playerView.player = null
            exoPlayer?.release()
            exoPlayer = null
        } catch (e: Exception) {
            Log.e("ExoPlayerEngine", "Error releasing ExoPlayer: ${e.message}")
        }
    }

    companion object {
        internal val REGEX_HDR10_PLUS = Regex("""(?i)(?:^|[^a-z0-9])(hdr10\+|hdr10plus)(?:$|[^a-z0-9])""")
        internal val REGEX_DOLBY_VISION = Regex("""(?i)(?:^|[^a-z0-9])(dolby[\s._-]*vision|dovi|dv)(?:$|[^a-z0-9])""")
        internal val REGEX_HDR10 = Regex("""(?i)(?:^|[^a-z0-9])hdr10(?:$|[^a-z0-9])""")
        internal val REGEX_HDR = Regex("""(?i)(?:^|[^a-z0-9])hdr(?:$|[^a-z0-9])""")
    }
}
