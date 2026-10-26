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
import androidx.media3.ui.PlayerView
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
    private var pendingStartPositionMs: Long = 0L
    private var isLooping = false
    private var currentSpeed: Float = 1.0f

    private val progressRunnable = object : Runnable {
        override fun run() {
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
                    when (playbackState) {
                        Player.STATE_READY -> {
                            if (!isPlayerReady) {
                                isPlayerReady = true
                                val dur = duration.takeIf { it > 0 } ?: 0L
                                onVideoReady(dur)
                                if (pendingStartPositionMs > 0) {
                                    seekTo(pendingStartPositionMs)
                                    pendingStartPositionMs = 0L
                                }
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
                    inspectHdrFormat()
                }

                override fun onPlayerError(error: PlaybackException) {
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
        val titleLower = currentTitle.lowercase()
        val isExplicitHdr10Plus = titleLower.contains("hdr10plus") || titleLower.contains("hdr10+")
        val isExplicitDolby = titleLower.contains("dolby") || titleLower.contains("dv") || titleLower.contains("dovi")

        if (format != null) {
            val colorInfo = format.colorInfo
            val transfer = colorInfo?.colorTransfer ?: -1
            val space = colorInfo?.colorSpace ?: -1
            val hdrStaticInfo = colorInfo?.hdrStaticInfo

            val isHdr = ColorInfo.isTransferHdr(colorInfo) ||
                    transfer == C.COLOR_TRANSFER_ST2084 ||
                    transfer == C.COLOR_TRANSFER_HLG ||
                    space == C.COLOR_SPACE_BT2020 ||
                    hdrStaticInfo != null ||
                    isExplicitHdr10Plus ||
                    isExplicitDolby

            val type = when {
                isExplicitDolby -> "Dolby Vision"
                isExplicitHdr10Plus -> "HDR10+"
                transfer == C.COLOR_TRANSFER_ST2084 -> "HDR10+"
                transfer == C.COLOR_TRANSFER_HLG -> "HLG"
                titleLower.contains("hdr10") -> "HDR10"
                isHdr -> "HDR"
                else -> "SDR"
            }
            mainHandler.post {
                onHdrStateDetected(isHdr, type)
            }
        } else if (isExplicitHdr10Plus || isExplicitDolby) {
            mainHandler.post {
                onHdrStateDetected(true, if (isExplicitDolby) "Dolby Vision" else "HDR10+")
            }
        } else {
            mainHandler.post {
                onHdrStateDetected(false, "SDR")
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
        pendingStartPositionMs = startPositionMs

        val titleLower = title.lowercase()
        val isExplicitHdr10Plus = titleLower.contains("hdr10plus") || titleLower.contains("hdr10+")
        val isExplicitDolby = titleLower.contains("dolby") || titleLower.contains("dv") || titleLower.contains("dovi")
        val isExplicitHdr = titleLower.contains("hdr") || titleLower.contains("hdr10") || isExplicitHdr10Plus || isExplicitDolby

        if (isExplicitHdr) {
            val type = when {
                isExplicitDolby -> "Dolby Vision"
                isExplicitHdr10Plus -> "HDR10+"
                titleLower.contains("hdr10") -> "HDR10"
                else -> "HDR"
            }
            mainHandler.post {
                onHdrStateDetected(true, type)
            }
        } else {
            mainHandler.post {
                onHdrStateDetected(false, "SDR")
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
            player.setMediaItem(mediaItem)
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

    fun seekTo(positionMs: Long, fast: Boolean = false) {
        exoPlayer?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun setPlaybackSpeed(speed: Float) {
        currentSpeed = speed
        exoPlayer?.playbackParameters = PlaybackParameters(speed)
    }

    fun setLooping(loop: Boolean) {
        isLooping = loop
        exoPlayer?.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
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
                            id = index.toString(),
                            label = format.label ?: format.language ?: "Subtitle #${index + 1}",
                            language = format.language ?: "und",
                            trackGroupIndex = index,
                            trackIndex = i,
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
                            id = index.toString(),
                            label = title,
                            language = format.language ?: "und",
                            channels = format.channelCount,
                            sampleRate = format.sampleRate,
                            trackGroupIndex = index,
                            trackIndex = i,
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

    fun release() {
        try {
            stop()
            exoPlayer?.release()
            exoPlayer = null
        } catch (e: Exception) {
            Log.e("ExoPlayerEngine", "Error releasing ExoPlayer: ${e.message}")
        }
    }
}
