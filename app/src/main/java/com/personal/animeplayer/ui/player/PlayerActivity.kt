package com.personal.animeplayer.ui.player

import android.app.Activity
import android.app.Dialog
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Rational
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.updateLayoutParams
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.personal.animeplayer.R
import com.personal.animeplayer.data.model.FileOpResult
import com.personal.animeplayer.databinding.ActivityPlayerBinding
import com.personal.animeplayer.databinding.DialogDeleteConfirmBinding
import com.personal.animeplayer.databinding.BottomSheetPlayerOptionsBinding
import com.personal.animeplayer.databinding.BottomSheetSpeedBinding
import com.personal.animeplayer.databinding.BottomSheetSubtitleStyleBinding
import com.personal.animeplayer.databinding.BottomSheetTracksBinding
import com.personal.animeplayer.player.ExoPlayerEngine
import com.personal.animeplayer.player.GestureListener
import com.personal.animeplayer.player.GestureManager
import com.personal.animeplayer.player.PlayerEngine
import com.personal.animeplayer.player.PlaylistHolder
import com.personal.animeplayer.util.FileUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlayerActivity : AppCompatActivity(), GestureListener {

    private lateinit var binding: ActivityPlayerBinding
    private val viewModel: PlayerViewModel by viewModels()

    private lateinit var playerEngine: PlayerEngine
    private lateinit var exoPlayerEngine: ExoPlayerEngine
    private var activeEngine: String = "mpv" // "mpv" or "exoplayer"
    private lateinit var gestureManager: GestureManager

    private val isPlaying: Boolean
        get() = if (activeEngine == "exoplayer" && ::exoPlayerEngine.isInitialized) {
            exoPlayerEngine.isPlaying
        } else if (::playerEngine.isInitialized) {
            playerEngine.isPlaying
        } else false

    private val currentPosition: Long
        get() = if (activeEngine == "exoplayer" && ::exoPlayerEngine.isInitialized) {
            exoPlayerEngine.currentPosition
        } else if (::playerEngine.isInitialized) {
            playerEngine.currentPosition
        } else 0L

    private val duration: Long
        get() = if (activeEngine == "exoplayer" && ::exoPlayerEngine.isInitialized) {
            exoPlayerEngine.duration
        } else if (::playerEngine.isInitialized) {
            playerEngine.duration
        } else 0L

    private val isLoopingEnabled: Boolean
        get() = if (::playerEngine.isInitialized) playerEngine.isLoopingEnabled else false

    private fun doPlay() {
        if (activeEngine == "exoplayer" && ::exoPlayerEngine.isInitialized) {
            exoPlayerEngine.play()
        } else if (::playerEngine.isInitialized) {
            playerEngine.play()
        }
    }

    private fun doPause() {
        if (activeEngine == "exoplayer" && ::exoPlayerEngine.isInitialized) {
            exoPlayerEngine.pause()
        } else if (::playerEngine.isInitialized) {
            playerEngine.pause()
        }
    }

    private fun doSeekTo(posMs: Long, fast: Boolean = false) {
        if (activeEngine == "exoplayer" && ::exoPlayerEngine.isInitialized) {
            exoPlayerEngine.seekTo(posMs, fast)
        } else if (::playerEngine.isInitialized) {
            playerEngine.seekTo(posMs, fast)
        }
        lastDisplayedSec = -1L
        binding.seekbarProgress.progress = posMs.toInt()
        binding.tvCurrentTime.text = FileUtils.formatDuration(posMs)
    }

    private fun doSeekBy(deltaMs: Long) {
        val cur = currentPosition
        val dur = duration
        val target = (cur + deltaMs).coerceIn(0L, if (dur > 0) dur else Long.MAX_VALUE)
        doSeekTo(target)
    }

    private fun doSetPlaybackSpeed(speed: Float) {
        if (::exoPlayerEngine.isInitialized) {
            exoPlayerEngine.setPlaybackSpeed(speed)
        }
        if (::playerEngine.isInitialized) {
            playerEngine.setPlaybackSpeed(speed)
        }
    }

    private fun doSetLooping(loop: Boolean) {
        if (::exoPlayerEngine.isInitialized) {
            exoPlayerEngine.setLooping(loop)
        }
        if (::playerEngine.isInitialized) {
            playerEngine.setLooping(loop)
        }
    }

    private val deleteConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            handlePostDelete()
        } else {
            Toast.makeText(this, "Izin hapus video ditolak oleh pengguna", Toast.LENGTH_SHORT).show()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable { hideControls() }
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            val delay = if (isControlsVisible) {
                16L // ~60 FPS update untuk timeline berjalan sangat mulus pada video pendek maupun panjang
            } else {
                1000L // Interval hemat daya saat kontrol tersembunyi
            }
            handler.postDelayed(this, delay)
        }
    }

    private var videoUri: Uri? = null
    private var videoTitle: String = ""
    private var seriesFolderUri: String? = null
    private var isControlsVisible = true
    private var isMediaStarted = false
    private var cachedStatusBarHeight: Int = 0
    private var cachedNavBarHeight: Int = 0

    private var normalPlaybackSpeed: Float = 1.0f
    private var currentSpeedIndex: Int = 3
    private var wasPlayingBeforeSpeedPress: Boolean = true

    // Real-time scrubbing state
    private var isUserScrubbing = false
    private var wasPlayingBeforeScrub = false
    private var lastScrubSeekTime = 0L
    private var pendingScrubPos: Long? = null
    private val scrubThrottleMs = 35L

    private val scrubRunnable = Runnable {
        pendingScrubPos?.let { pos ->
            doSeekTo(pos, fast = true)
            pendingScrubPos = null
            lastScrubSeekTime = SystemClock.uptimeMillis()
        }
    }

    private fun performLiveScrub(posMs: Long) {
        val now = SystemClock.uptimeMillis()
        pendingScrubPos = posMs
        handler.removeCallbacks(scrubRunnable)
        if (now - lastScrubSeekTime >= scrubThrottleMs) {
            lastScrubSeekTime = now
            doSeekTo(posMs, fast = true)
            pendingScrubPos = null
        } else {
            val delay = scrubThrottleMs - (now - lastScrubSeekTime)
            handler.postDelayed(scrubRunnable, delay)
        }
    }

    private fun confirmLiveScrub(posMs: Long) {
        handler.removeCallbacks(scrubRunnable)
        pendingScrubPos = null
        doSeekTo(posMs, fast = false)
    }

    private val pickSubtitleLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            if (activeEngine == "exoplayer" && ::exoPlayerEngine.isInitialized) {
                exoPlayerEngine.loadExternalSubtitle(uri, select = true)
            } else if (::playerEngine.isInitialized) {
                playerEngine.loadExternalSubtitle(uri, select = true)
            }
            Toast.makeText(this, "Subtitle eksternal termuat!", Toast.LENGTH_SHORT).show()
        }
    }

    // Inactivity Sleep Detection (Solution B: 60 minutes continuous playback without screen touch)
    private var continuousPlaybackDurationWithoutTouchMs: Long = 0L
    private val inactivityThresholdMs: Long = 60 * 60 * 1000L // 60 minutes
    private var pendingNextEpisode: com.personal.animeplayer.data.model.Episode? = null
    private var inactivityCountdownTimer: CountDownTimer? = null
    private var isInactivityPromptShowing: Boolean = false

    private var isSwitchingEpisode = false
    private var lastEpisodeSwitchTimestamp: Long = 0L
    private val resetSwitchingRunnable = Runnable {
        isSwitchingEpisode = false
        if (::binding.isInitialized) {
            binding.loadingIndicator.visibility = View.GONE
        }
    }
    private var lastPlayPauseClickTime: Long = 0L

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (::binding.isInitialized && !isInactivityPromptShowing) {
            continuousPlaybackDurationWithoutTouchMs = 0L
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setupFullscreen()

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isInactivityPromptShowing) {
                    continueWatchingAfterInactivity()
                    return
                }
                if (isScreenLocked) {
                    showUnlockButtonTemporarily()
                    binding.btnUnlock.animate()
                        .translationXBy(10f)
                        .setDuration(50)
                        .withEndAction {
                            binding.btnUnlock.animate()
                                .translationXBy(-20f)
                                .setDuration(50)
                                .withEndAction {
                                    binding.btnUnlock.animate()
                                        .translationX(0f)
                                        .setDuration(50)
                                        .start()
                                }
                                .start()
                        }
                        .start()
                    return
                }
                pauseAllEngines(stopPlayback = true)
                finish()
            }
        })

        parseIntent()
        initPlayerComponents()
        setupUIEvents()
        observeViewModel()
        applyOrientationLayout(resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
    }

    private fun setupFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (isControlsVisible) {
            controller.show(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.topControlBar) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            if (statusBarHeight > 0) {
                cachedStatusBarHeight = statusBarHeight
            }
            val effectiveSbHeight = if (cachedStatusBarHeight > 0) cachedStatusBarHeight else statusBarHeight
            v.setPadding(
                v.paddingLeft,
                effectiveSbHeight + (8 * resources.displayMetrics.density).toInt(),
                v.paddingRight,
                (28 * resources.displayMetrics.density).toInt()
            )
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomControlBar) { v, insets ->
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            if (navBarHeight > 0) {
                cachedNavBarHeight = navBarHeight
            }
            val effectiveNbHeight = if (cachedNavBarHeight > 0) cachedNavBarHeight else navBarHeight
            val topPadDp = if (isLandscape) 2 else 4
            val bottomPadDp = if (isLandscape) 2 else 4
            v.setPadding(
                v.paddingLeft,
                (topPadDp * resources.displayMetrics.density).toInt(),
                v.paddingRight,
                effectiveNbHeight + (bottomPadDp * resources.displayMetrics.density).toInt()
            )
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.speedOverlayView) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            val baseMarginDp = if (isPortrait) 98 else 24
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarHeight + (baseMarginDp * resources.displayMetrics.density).toInt()
            }
            insets
        }
    }

    private fun parseIntent() {
        val uriStr = intent.getStringExtra(EXTRA_VIDEO_URI) ?: intent.dataString
        videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: intent.data?.lastPathSegment ?: "Anime Video"
        seriesFolderUri = intent.getStringExtra(EXTRA_SERIES_FOLDER_URI)

        if (uriStr != null) {
            videoUri = Uri.parse(uriStr)
            binding.tvVideoTitle.text = videoTitle
            PlaylistHolder.lastPlayedUri = uriStr
            PlaylistHolder.shouldScrollToLastPlayed = true
        } else {
            Toast.makeText(this, "URI video tidak valid", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun initPlayerComponents() {
        playerEngine = PlayerEngine(
            context = this,
            mpvView = binding.mpvView,
            onPlaybackStateChanged = { isPlaying ->
                if (activeEngine == "mpv") {
                    handleEnginePlaybackStateChanged(isPlaying)
                }
            },
            onPositionChanged = { currentPos, duration ->
                if (activeEngine == "mpv") {
                    handleEnginePositionChanged(currentPos, duration)
                }
            },
            onVideoReady = { duration ->
                if (activeEngine == "mpv") {
                    handleEngineVideoReady(duration)
                }
            },
            onPlayerError = { errorMessage ->
                if (activeEngine == "mpv") {
                    showErrorDialog(errorMessage)
                }
            },
            onPlaybackEnded = {
                if (activeEngine == "mpv") {
                    handlePlaybackEnded()
                }
            },
            onHdrStateDetected = { isHdr, hdrType ->
                if (activeEngine == "mpv") {
                    val prefEngine = viewModel.playerData.value.playerEngineMode
                    if (isHdr && prefEngine == "auto") {
                        switchActiveEngine("exoplayer")
                    } else {
                        handleHdrDetected(isHdr, hdrType)
                    }
                }
            }
        )

        exoPlayerEngine = ExoPlayerEngine(
            context = this,
            playerView = binding.exoPlayerView,
            onPlaybackStateChanged = { isPlaying ->
                if (activeEngine == "exoplayer") {
                    handleEnginePlaybackStateChanged(isPlaying)
                }
            },
            onPositionChanged = { currentPos, duration ->
                if (activeEngine == "exoplayer") {
                    handleEnginePositionChanged(currentPos, duration)
                }
            },
            onVideoReady = { duration ->
                if (activeEngine == "exoplayer") {
                    handleEngineVideoReady(duration)
                }
            },
            onPlayerError = { errorMessage ->
                if (activeEngine == "exoplayer") {
                    showErrorDialog(errorMessage)
                }
            },
            onPlaybackEnded = {
                if (activeEngine == "exoplayer") {
                    handlePlaybackEnded()
                }
            },
            onHdrStateDetected = { isHdr, hdrType ->
                if (activeEngine == "exoplayer") {
                    handleHdrDetected(isHdr, hdrType)
                }
            }
        )

        gestureManager = GestureManager(
            activity = this,
            overlayView = binding.gestureOverlay,
            listener = this,
            getCurrentPosition = { currentPosition },
            getDuration = { duration }
        )

        // Request initial data from ViewModel
        videoUri?.let {
            viewModel.initVideo(it, videoTitle, seriesFolderUri)
        }
    }

    private fun handleEnginePlaybackStateChanged(isPlaying: Boolean) {
        binding.btnPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        )
        if (isPlaying) {
            scheduleHideControls()
        } else {
            handler.removeCallbacks(hideControlsRunnable)
        }
    }

    private fun handleEnginePositionChanged(currentPos: Long, duration: Long) {
        if (duration > 0 && !isUserScrubbing && !binding.seekbarProgress.isPressed) {
            binding.seekbarProgress.progress = currentPos.toInt()
            binding.tvCurrentTime.text = FileUtils.formatDuration(currentPos)
        }
    }

    private fun handleEngineVideoReady(duration: Long) {
        binding.loadingIndicator.visibility = View.GONE
        binding.tvTotalDuration.text = FileUtils.formatDuration(duration)
        binding.seekbarProgress.max = duration.toInt()
        isSwitchingEpisode = false
        handler.removeCallbacks(resetSwitchingRunnable)
        updateNavigationButtonsState()
    }

    private var detectedHdrType: String = "SDR"
    private var isHdrActive: Boolean = false
    private var previousSdrBrightness: Float = -1f

    private fun handleHdrDetected(isHdr: Boolean, hdrType: String) {
        detectedHdrType = hdrType
        isHdrActive = isHdr
        if (isHdr) {
            binding.tvHdrBadge.text = hdrType
            binding.tvHdrBadge.visibility = View.VISIBLE

            // 1. Enable HDR color mode on Window (API 26+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window.colorMode = ActivityInfo.COLOR_MODE_HDR
            }

            // 2. Set maximum HDR headroom for Android 15+ (API 35+) to enable full AMOLED HDR luminance
            if (Build.VERSION.SDK_INT >= 35) {
                window.setDesiredHdrHeadroom(10000.0f)
            }

            // 3. Auto-boost brightness to 100% for HDR
            val lp = window.attributes
            if (previousSdrBrightness < 0f) {
                val current = lp.screenBrightness
                previousSdrBrightness = if (current in 0.01f..1.0f) current else {
                    val saved = viewModel.playerData.value.savedBrightness
                    if (saved in 0.01f..1.0f) saved else 0.5f
                }
            }
            lp.screenBrightness = 1.0f
            window.attributes = lp
        } else {
            binding.tvHdrBadge.visibility = View.GONE

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
            }
            if (Build.VERSION.SDK_INT >= 35) {
                window.setDesiredHdrHeadroom(0.0f)
            }

            // Restore previous SDR brightness if it was boosted
            if (previousSdrBrightness > 0f) {
                val lp = window.attributes
                lp.screenBrightness = previousSdrBrightness
                window.attributes = lp
                previousSdrBrightness = -1f
            }
        }
    }

    private fun isLikelyHdrOrCamera(uri: Uri?, title: String): Boolean {
        if (uri == null) return false
        val titleLower = title.lowercase()
        val uriStrLower = uri.toString().lowercase()
        val pathLower = (uri.path ?: "").lowercase()
        val lastSegmentLower = (uri.lastPathSegment ?: "").lowercase()

        // Check against active playlist item if present
        val playlistItem = PlaylistHolder.currentPlaylist.find { it.uri == uri.toString() }
        val playlistFileName = playlistItem?.fileName?.lowercase() ?: ""
        val playlistTitle = playlistItem?.title?.lowercase() ?: ""

        // Check file name via ContentResolver if content uri
        var contentDisplayName = ""
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        contentDisplayName = cursor.getString(0)?.lowercase() ?: ""
                    }
                }
            } catch (e: Exception) {}
        }

        val allNames = listOf(titleLower, playlistTitle, playlistFileName, contentDisplayName, uriStrLower, pathLower, lastSegmentLower)

        val hdrKeywords = listOf("hdr", "hdr10", "hdr10plus", "hdr10+", "dolby", "dv", "dovi", "hlg", "pq")
        for (kw in hdrKeywords) {
            if (allNames.any { it.contains(kw) }) {
                return true
            }
        }

        val cameraKeywords = listOf("/dcim/", "/camera/", "camera", "dcim", "vid_")
        for (kw in cameraKeywords) {
            if (allNames.any { it.contains(kw) }) {
                return true
            }
        }

        return false
    }

    private fun determineActiveEngine(uri: Uri?, title: String, prefEngine: String): String {
        return when (prefEngine) {
            "exoplayer" -> "exoplayer"
            "mpv" -> "mpv"
            else -> {
                if (isLikelyHdrOrCamera(uri, title)) "exoplayer" else "mpv"
            }
        }
    }

    private fun playMediaOnActiveEngine(
        uri: Uri,
        title: String,
        startPositionMs: Long,
        externalSubtitles: List<Uri> = emptyList()
    ) {
        val isNextLikelyHdr = isLikelyHdrOrCamera(uri, title)
        if (!isNextLikelyHdr) {
            handleHdrDetected(false, "SDR")
        }
        val prefEngine = viewModel.playerData.value.playerEngineMode
        val targetEngine = determineActiveEngine(uri, title, prefEngine)
        activeEngine = targetEngine

        if (targetEngine == "exoplayer") {
            if (::playerEngine.isInitialized) {
                playerEngine.pause()
            }
            binding.mpvView.visibility = View.GONE
            binding.exoPlayerView.visibility = View.VISIBLE
            exoPlayerEngine.playMedia(uri, startPositionMs, title, externalSubtitles)
        } else {
            if (::exoPlayerEngine.isInitialized) {
                exoPlayerEngine.pause()
            }
            binding.exoPlayerView.visibility = View.GONE
            binding.mpvView.visibility = View.VISIBLE
            playerEngine.playMedia(
                videoUri = uri,
                title = title,
                startPositionMs = startPositionMs,
                externalSubtitles = externalSubtitles
            )
        }
    }

    private fun switchActiveEngine(newEngine: String) {
        val target = if (newEngine == "auto") {
            determineActiveEngine(videoUri, videoTitle, "auto")
        } else newEngine

        if (target == activeEngine) return

        val currentPos = currentPosition
        val currentPlaying = isPlaying
        val uri = videoUri ?: return

        activeEngine = target

        if (target == "exoplayer") {
            playerEngine.pause()
            binding.mpvView.visibility = View.GONE
            binding.exoPlayerView.visibility = View.VISIBLE
            exoPlayerEngine.playMedia(uri, currentPos, videoTitle, viewModel.playerData.value.externalSubtitles)
            if (!currentPlaying) exoPlayerEngine.pause()
        } else {
            exoPlayerEngine.pause()
            binding.exoPlayerView.visibility = View.GONE
            binding.mpvView.visibility = View.VISIBLE
            playerEngine.playMedia(
                videoUri = uri,
                title = videoTitle,
                startPositionMs = currentPos,
                externalSubtitles = viewModel.playerData.value.externalSubtitles
            )
            if (!currentPlaying) playerEngine.pause()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.playerData.collectLatest { data ->
                playerEngine.setSubtitleDelay(data.subtitleDelayMs)
                playerEngine.setSubtitleScale(data.subtitleScale)
                doSetPlaybackSpeed(data.playbackSpeed)

                // Restore saved brightness if set
                if (!isHdrActive && data.isLoaded && data.savedBrightness in 0.01f..1.0f) {
                    val lp = window.attributes
                    if (lp.screenBrightness != data.savedBrightness) {
                        lp.screenBrightness = data.savedBrightness
                        window.attributes = lp
                    }
                }

                // Restore saved repeat / loop mode
                if (data.isLoaded) {
                    doSetLooping(data.isLoopEnabled)
                    updateRepeatButtonState(data.isLoopEnabled)
                    updateNavigationButtonsState()
                    binding.btnSkipOpEd.visibility = if (data.isSkipOpEdEnabled) View.VISIBLE else View.GONE
                    playerEngine.setDecoderMode(data.decoderMode)
                    playerEngine.audioFocusHelper.songDetectionMode = data.isSongDetectionEnabled
                    if (::exoPlayerEngine.isInitialized) {
                        exoPlayerEngine.audioFocusHelper.songDetectionMode = data.isSongDetectionEnabled
                    }
                }

                // Start playback once initial data is loaded from database
                if (videoUri != null && !isMediaStarted && data.isLoaded) {
                    isMediaStarted = true
                    playMediaOnActiveEngine(
                        uri = videoUri!!,
                        title = videoTitle,
                        startPositionMs = data.initialPositionMs,
                        externalSubtitles = data.externalSubtitles
                    )
                }
            }
        }
    }

    private fun setupUIEvents() {
        // Play / Pause
        binding.btnPlayPause.setOnClickListener {
            val now = SystemClock.uptimeMillis()
            if (now - lastPlayPauseClickTime < 150L) {
                return@setOnClickListener
            }
            lastPlayPauseClickTime = now

            val willPlay = !isPlaying

            // Immediate tactile & visual micro-animation
            binding.btnPlayPause.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            binding.btnPlayPause.animate()
                .scaleX(0.85f)
                .scaleY(0.85f)
                .setDuration(60)
                .withEndAction {
                    binding.btnPlayPause.animate().scaleX(1.0f).scaleY(1.0f).setDuration(60).start()
                }
                .start()

            // Optimistic instant icon update (0ms latency)
            binding.btnPlayPause.setImageResource(
                if (willPlay) R.drawable.ic_pause else R.drawable.ic_play_arrow
            )

            if (willPlay) {
                doPlay()
                scheduleHideControls()
            } else {
                doPause()
                handler.removeCallbacks(hideControlsRunnable)
            }
        }

        // Previous Episode
        binding.btnPrev.setOnClickListener {
            if (isSwitchingEpisode) {
                return@setOnClickListener
            }
            continuousPlaybackDurationWithoutTouchMs = 0L
            val prev = viewModel.getPreviousEpisode()
            if (prev != null) {
                switchVideo(prev, isNext = false)
            } else {
                Toast.makeText(this, "Tidak ada episode sebelumnya", Toast.LENGTH_SHORT).show()
            }
            scheduleHideControls()
        }

        // Next Episode
        binding.btnNext.setOnClickListener {
            if (isSwitchingEpisode) {
                return@setOnClickListener
            }
            continuousPlaybackDurationWithoutTouchMs = 0L
            val next = viewModel.getNextEpisode()
            if (next != null) {
                switchVideo(next, isNext = true)
            } else {
                Toast.makeText(this, "Tidak ada episode selanjutnya", Toast.LENGTH_SHORT).show()
            }
            scheduleHideControls()
        }

        // Back Button
        binding.btnBack.setOnClickListener { finish() }

        // Player Options (3-Dots Menu)
        binding.btnMoreOptions.setOnClickListener { showPlayerOptionsBottomSheet() }

        // Skip OP/ED fixed 85 seconds
        binding.btnSkipOpEd.setOnClickListener {
            doSeekBy(85_000)
            scheduleHideControls()
        }

        // Screen Lock (Floating on right edge)
        binding.btnLock.setOnClickListener { lockScreen() }
        binding.btnUnlock.setOnClickListener { unlockScreen() }

        // Screen Orientation Toggle (Berdiri <-> Miring)
        binding.btnOrientation.setOnClickListener {
            val currentOrientation = resources.configuration.orientation
            requestedOrientation = if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            scheduleHideControls()
        }

        // Automatic Video Repeat / Loop Toggle
        binding.btnRepeat.setOnClickListener {
            val newLoop = !isLoopingEnabled
            doSetLooping(newLoop)
            updateRepeatButtonState(newLoop)
            viewModel.saveLoopEnabled(newLoop)
            scheduleHideControls()
        }

        // Aspect Ratio & Fullscreen Mode Toggle (Red mark on bottom-right)
        binding.btnAspectRatio.setOnClickListener {
            cycleAspectRatio()
            scheduleHideControls()
        }
        binding.btnAspectRatio.setOnLongClickListener {
            showAspectRatioBottomSheet()
            true
        }

        // Seekbar interaction with real-time visual scrubbing
        binding.seekbarProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val posMs = progress.toLong()
                    binding.tvCurrentTime.text = FileUtils.formatDuration(posMs)
                    performLiveScrub(posMs)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserScrubbing = true
                handler.removeCallbacks(hideControlsRunnable)
                wasPlayingBeforeScrub = isPlaying
                if (wasPlayingBeforeScrub) {
                    doPause()
                }
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserScrubbing = false
                seekBar?.let {
                    confirmLiveScrub(it.progress.toLong())
                }
                if (wasPlayingBeforeScrub) {
                    doPlay()
                }
                scheduleHideControls()
            }
        })

        // Inactivity Sleep Prompt Action Buttons
        binding.inactivityPrompt.btnContinueWatching.setOnClickListener {
            continueWatchingAfterInactivity()
        }
    }

    private var lastProgressSaveTimestamp = 0L
    private var lastInactivityCheckTimestamp = 0L
    private var lastDisplayedSec = -1L

    private fun updateProgress() {
        val currentPos = currentPosition
        val dur = duration

        if (dur > 0 && !isUserScrubbing && !binding.seekbarProgress.isPressed) {
            binding.seekbarProgress.progress = currentPos.toInt()

            val currentSec = currentPos / 1000L
            if (currentSec != lastDisplayedSec) {
                lastDisplayedSec = currentSec
                binding.tvCurrentTime.text = FileUtils.formatDuration(currentPos)
            }
        }

        val now = SystemClock.elapsedRealtime()

        // Periodic progress save (maksimal 1 detik sekali untuk efisiensi penyimpanan database)
        if (isPlaying && currentPos > 0 && dur > 0) {
            if (now - lastProgressSaveTimestamp >= 1000L) {
                lastProgressSaveTimestamp = now
                viewModel.saveProgress(currentPos, dur)
            }
        }

        // Inactivity tracking: increment waktu putar nyata
        if (lastInactivityCheckTimestamp == 0L) {
            lastInactivityCheckTimestamp = now
        }
        val elapsed = (now - lastInactivityCheckTimestamp).coerceAtLeast(0L)
        lastInactivityCheckTimestamp = now

        if (isPlaying && !isInactivityPromptShowing) {
            continuousPlaybackDurationWithoutTouchMs += elapsed
            if (continuousPlaybackDurationWithoutTouchMs >= inactivityThresholdMs) {
                showInactivityPrompt(viewModel.getNextEpisode())
            }
        }
    }

    private fun switchVideo(episode: com.personal.animeplayer.data.model.Episode, isNext: Boolean = true) {
        isSwitchingEpisode = true
        lastEpisodeSwitchTimestamp = System.currentTimeMillis()

        // 1. Hentikan pemutaran audio/video lama seketika
        pauseAllEngines()

        // 2. Tampilkan pembaruan antarmuka langsung (judul baru di bilah atas, reset timeline)
        val isNextHdr = isLikelyHdrOrCamera(Uri.parse(episode.uri), episode.title)
        if (!isNextHdr) {
            handleHdrDetected(false, "SDR")
        }
        binding.gestureFeedbackContainer.visibility = View.GONE
        handler.removeCallbacks(hideFeedbackRunnable)
        binding.loadingIndicator.visibility = View.VISIBLE
        binding.tvVideoTitle.text = episode.title
        binding.tvCurrentTime.text = "00:00"
        binding.tvTotalDuration.text = "--:--"
        binding.seekbarProgress.progress = 0

        lastDisplayedSec = -1L
        lastProgressSaveTimestamp = 0L
        lastInactivityCheckTimestamp = 0L
        val pos = currentPosition
        val dur = duration
        if (pos > 0 && dur > 0) {
            viewModel.saveProgress(pos, dur)
        }
        videoUri = Uri.parse(episode.uri)
        videoTitle = episode.title
        PlaylistHolder.lastPlayedUri = episode.uri
        PlaylistHolder.shouldScrollToLastPlayed = true

        // 3. Panggil switchToEpisode dengan callback langsung agar pemutaran dieksekusi secara instan dan pasti
        viewModel.switchToEpisode(episode) { startPos, _, externalSubs ->
            runOnUiThread {
                if (videoUri?.toString() == episode.uri) {
                    playMediaOnActiveEngine(
                        uri = Uri.parse(episode.uri),
                        title = episode.title,
                        startPositionMs = startPos,
                        externalSubtitles = externalSubs
                    )
                }
            }
        }

        updateNavigationButtonsState()
        handler.removeCallbacks(resetSwitchingRunnable)
        handler.postDelayed(resetSwitchingRunnable, 1500L)
    }

    private fun updateNavigationButtonsState() {
        binding.btnPrev.alpha = if (viewModel.hasPrevious()) 1.0f else 0.4f
        binding.btnNext.alpha = if (viewModel.hasNext()) 1.0f else 0.4f
    }

    private fun handlePlaybackEnded() {
        if (isSwitchingEpisode || isInactivityPromptShowing) return
        val currentPos = currentPosition
        val totalDuration = duration
        // Verify playback is genuinely near the end (within last 3 seconds)
        if (totalDuration > 0 && currentPos < totalDuration - 3000L) {
            return
        }
        if (!isLoopingEnabled) {
            val next = viewModel.getNextEpisode()
            if (next != null) {
                // If 60 minutes of uninterrupted playback reached, prompt before auto-playing next
                if (continuousPlaybackDurationWithoutTouchMs >= inactivityThresholdMs) {
                    showInactivityPrompt(next)
                } else {
                    switchVideo(next)
                }
            } else {
                Toast.makeText(this, "Semua video telah selesai diputar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showInactivityPrompt(nextEpisode: com.personal.animeplayer.data.model.Episode? = null) {
        isInactivityPromptShowing = true
        pendingNextEpisode = nextEpisode
        doPause()
        hideControls()

        val prompt = binding.inactivityPrompt
        prompt.tvPromptDesc.text = "Pemutaran telah berjalan selama 60 menit tanpa sentuhan layar. Aplikasi akan keluar otomatis dalam 30 detik untuk menghemat baterai."
        prompt.progressCountdown.max = 30
        prompt.progressCountdown.progress = 30
        prompt.tvCountdownSeconds.text = "Otomatis keluar dalam 30 detik"
        prompt.layoutInactivityPrompt.visibility = View.VISIBLE

        inactivityCountdownTimer?.cancel()
        inactivityCountdownTimer = object : CountDownTimer(30_000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = ((millisUntilFinished + 500) / 1000).toInt().coerceAtLeast(0)
                prompt.progressCountdown.progress = secondsLeft
                prompt.tvCountdownSeconds.text = "Otomatis keluar dalam $secondsLeft detik"
            }

            override fun onFinish() {
                prompt.progressCountdown.progress = 0
                prompt.tvCountdownSeconds.text = "Otomatis keluar dalam 0 detik"
                exitAppOnInactivity()
            }
        }.start()
    }

    private fun exitAppOnInactivity() {
        inactivityCountdownTimer?.cancel()
        inactivityCountdownTimer = null
        isInactivityPromptShowing = false
        binding.inactivityPrompt.layoutInactivityPrompt.visibility = View.GONE

        doPause()
        val pos = currentPosition
        val dur = duration
        if (pos > 0 && dur > 0) {
            viewModel.saveProgress(pos, dur)
        }

        // Send to Home Screen and finish all activities cleanly
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finishAffinity()
    }

    private fun continueWatchingAfterInactivity() {
        inactivityCountdownTimer?.cancel()
        inactivityCountdownTimer = null
        isInactivityPromptShowing = false
        binding.inactivityPrompt.layoutInactivityPrompt.visibility = View.GONE
        continuousPlaybackDurationWithoutTouchMs = 0L
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val next = pendingNextEpisode
        pendingNextEpisode = null
        val dur = duration
        if (next != null && dur > 0 && currentPosition >= (dur - 1000L)) {
            switchVideo(next)
        } else {
            doPlay()
        }
    }


    // ================= GestureListener Implementation =================

    override fun onSingleTap() {
        if (isScreenLocked) {
            showUnlockButtonTemporarily()
            return
        }
        toggleControlsVisibility()
    }

    override fun onDoubleTapSeek(isForward: Boolean) {
        val delta = if (isForward) 10_000L else -10_000L
        doSeekBy(delta)

        // Update progress on seekbar & current time UI
        val dur = duration
        val targetPos = (currentPosition + delta).coerceIn(0L, if (dur > 0) dur else Long.MAX_VALUE)
        binding.tvCurrentTime.text = FileUtils.formatDuration(targetPos)
        if (dur > 0) {
            binding.seekbarProgress.progress = targetPos.toInt()
        }

        // Trigger smooth double-tap seek animation overlay
        binding.doubleTapOverlay.showSeekAnimation(isForward, stepSeconds = 10)

        if (isControlsVisible) {
            scheduleHideControls()
        }
    }

    override fun onSeekGesture(deltaMs: Long, targetMs: Long) {
        if (!isUserScrubbing) {
            wasPlayingBeforeScrub = isPlaying
            if (wasPlayingBeforeScrub) {
                doPause()
            }
            isUserScrubbing = true
        }
        binding.tvCurrentTime.text = FileUtils.formatDuration(targetMs)
        binding.seekbarProgress.progress = targetMs.toInt()
        val sign = if (deltaMs >= 0) "+" else ""
        showGestureFeedback(
            iconRes = if (deltaMs >= 0) R.drawable.ic_forward_10 else R.drawable.ic_replay_10,
            text = "$sign${deltaMs / 1000}s (${FileUtils.formatDuration(targetMs)})"
        )
        performLiveScrub(targetMs)
    }

    override fun onSeekConfirmed(targetMs: Long) {
        isUserScrubbing = false
        confirmLiveScrub(targetMs)
        if (wasPlayingBeforeScrub) {
            doPlay()
        }
        scheduleHideControls()
    }

    override fun onVolumeChanged(percent: Int) {
        val iconRes = if (percent == 0) R.drawable.ic_volume_off else R.drawable.ic_volume
        showGestureFeedback(
            iconRes = iconRes,
            text = "$percent%"
        )
    }

    override fun onBrightnessChanged(percent: Int) {
        showGestureFeedback(
            iconRes = R.drawable.ic_brightness,
            text = "$percent%"
        )
    }

    override fun onBrightnessConfirmed(brightness: Float) {
        if (!isHdrActive) {
            viewModel.saveBrightness(brightness)
        }
    }

    private val hideSpeedOverlayRunnable = Runnable {
        binding.speedOverlayView.hide()
    }

    private fun scheduleHideSpeedOverlay() {
        handler.removeCallbacks(hideSpeedOverlayRunnable)
        handler.postDelayed(hideSpeedOverlayRunnable, 2000L)
    }

    override fun onSpeedPressStart() {
        normalPlaybackSpeed = viewModel.playerData.value.playbackSpeed
        wasPlayingBeforeSpeedPress = isPlaying

        currentSpeedIndex = 3 // 2.0x
        doSetPlaybackSpeed(2.0f)
        if (!wasPlayingBeforeSpeedPress) {
            doPlay()
        }

        hideControls()
        binding.speedOverlayView.show(initialIndex = 3)
        scheduleHideSpeedOverlay()
    }

    override fun onSpeedPressMove(deltaX: Float) {
        val speeds = binding.speedOverlayView.speeds
        val stepPx = resources.displayMetrics.density * 22f
        val stepDelta = (deltaX / stepPx).toInt()
        val targetIndex = (3 + stepDelta).coerceIn(0, speeds.size - 1)

        if (targetIndex != currentSpeedIndex) {
            currentSpeedIndex = targetIndex
            val targetSpeed = speeds[targetIndex]
            doSetPlaybackSpeed(targetSpeed)

            if (binding.speedOverlayView.visibility != View.VISIBLE || binding.speedOverlayView.alpha < 0.9f) {
                binding.speedOverlayView.show(initialIndex = targetIndex)
            } else {
                binding.speedOverlayView.setSpeedIndex(targetIndex)
            }
            scheduleHideSpeedOverlay()
        }
    }

    override fun onSpeedPressEnd() {
        handler.removeCallbacks(hideSpeedOverlayRunnable)
        doSetPlaybackSpeed(normalPlaybackSpeed)

        if (!wasPlayingBeforeSpeedPress) {
            doPause()
        }

        binding.speedOverlayView.hide()
    }

    private fun showGestureFeedback(iconRes: Int, text: String) {
        binding.ivGestureIcon.setImageResource(iconRes)
        binding.tvGestureValue.text = text

        handler.removeCallbacks(hideFeedbackRunnable)

        val container = binding.gestureFeedbackContainer
        if (container.visibility != View.VISIBLE) {
            container.visibility = View.VISIBLE
            container.alpha = 0f
            container.scaleX = 0.92f
            container.scaleY = 0.92f
            container.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(120)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        } else {
            container.animate().cancel()
            container.alpha = 1f
            container.scaleX = 1f
            container.scaleY = 1f
        }

        handler.postDelayed(hideFeedbackRunnable, 1200)
    }

    private val hideFeedbackRunnable = Runnable {
        val container = binding.gestureFeedbackContainer
        container.animate()
            .alpha(0f)
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(180)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                container.visibility = View.GONE
                container.scaleX = 1f
                container.scaleY = 1f
            }
            .start()
    }

    private fun toggleControlsVisibility() {
        if (isScreenLocked) {
            showUnlockButtonTemporarily()
            return
        }
        if (isControlsVisible) hideControls() else showControls()
    }

    private fun showControls() {
        if (isScreenLocked) return
        isControlsVisible = true

        val topSlideDistance = if (binding.topControlBar.height > 0) {
            binding.topControlBar.height.toFloat()
        } else {
            120 * resources.displayMetrics.density
        }
        val bottomSlideDistance = if (binding.bottomControlBar.height > 0) {
            binding.bottomControlBar.height.toFloat()
        } else {
            140 * resources.displayMetrics.density
        }

        binding.controlsContainer.apply {
            animate().cancel()
            visibility = View.VISIBLE
            alpha = 1f
        }

        binding.topControlBar.apply {
            animate().cancel()
            visibility = View.VISIBLE
            translationY = -topSlideDistance
            alpha = 0f
            animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(240)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }

        binding.bottomControlBar.apply {
            animate().cancel()
            visibility = View.VISIBLE
            translationY = bottomSlideDistance
            alpha = 0f
            animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(240)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }

        binding.btnLock.apply {
            animate().cancel()
            visibility = View.VISIBLE
            alpha = 0f
            animate()
                .alpha(1f)
                .setDuration(240)
                .start()
        }

        binding.rowAboveTimeline.visibility = View.VISIBLE
        binding.bottomPlaybackControls.visibility = View.VISIBLE
        // Restore touches
        binding.seekbarProgress.setOnTouchListener(null)
        binding.seekbarProgress.isEnabled = true
        binding.bottomControlBar.setBackgroundResource(R.drawable.bg_gradient_bottom)

        binding.btnSkipOpEd.visibility = if (viewModel.playerData.value.isSkipOpEdEnabled) View.VISIBLE else View.GONE
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.statusBars())
        scheduleHideControls()
        handler.removeCallbacks(updateProgressRunnable)
        handler.post(updateProgressRunnable)
    }

    private fun hideControls() {
        isControlsVisible = false

        val topSlideDistance = if (binding.topControlBar.height > 0) {
            binding.topControlBar.height.toFloat()
        } else {
            120 * resources.displayMetrics.density
        }
        val bottomSlideDistance = if (binding.bottomControlBar.height > 0) {
            binding.bottomControlBar.height.toFloat()
        } else {
            140 * resources.displayMetrics.density
        }

        binding.topControlBar.apply {
            animate().cancel()
            animate()
                .translationY(-topSlideDistance)
                .alpha(0f)
                .setDuration(250)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .start()
        }

        binding.bottomControlBar.apply {
            animate().cancel()
            animate()
                .translationY(bottomSlideDistance)
                .alpha(0f)
                .setDuration(250)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .start()
        }

        binding.btnLock.apply {
            animate().cancel()
            animate()
                .alpha(0f)
                .setDuration(200)
                .start()
        }

        binding.controlsContainer.apply {
            animate().cancel()
            animate()
                .alpha(0f)
                .setDuration(250)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    if (!isControlsVisible) {
                        visibility = View.GONE
                        alpha = 1f
                        binding.topControlBar.translationY = 0f
                        binding.topControlBar.alpha = 1f
                        binding.bottomControlBar.translationY = 0f
                        binding.bottomControlBar.alpha = 1f
                        binding.btnLock.alpha = 1f
                    }
                }
                .start()
        }
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun scheduleHideControls() {
        handler.removeCallbacks(hideControlsRunnable)
        if (playerEngine.isPlaying) {
            handler.postDelayed(hideControlsRunnable, 3500)
        }
    }

    private var isScreenLocked = false
    private var isPipMode = false

    private val hideUnlockButtonRunnable = Runnable {
        if (isScreenLocked && ::binding.isInitialized) {
            binding.btnUnlock.animate()
                .alpha(0f)
                .setDuration(250)
                .withEndAction {
                    binding.btnUnlock.visibility = View.GONE
                }
                .start()
                
            binding.controlsContainer.animate()
                .alpha(0f)
                .setDuration(250)
                .withEndAction {
                    binding.controlsContainer.visibility = View.GONE
                    binding.controlsContainer.alpha = 1f
                }
                .start()
        }
    }

    private fun showUnlockButtonTemporarily() {
        if (!isScreenLocked || !::binding.isInitialized) return
        handler.removeCallbacks(hideUnlockButtonRunnable)
        
        binding.btnUnlock.alpha = 1f
        binding.btnUnlock.visibility = View.VISIBLE
        
        // Show timeline only
        binding.controlsContainer.visibility = View.VISIBLE
        binding.topControlBar.visibility = View.GONE
        binding.btnLock.visibility = View.GONE
        binding.rowAboveTimeline.visibility = View.GONE
        binding.bottomPlaybackControls.visibility = View.GONE
        // Prevent touches without graying out the seekbar
        binding.seekbarProgress.setOnTouchListener { _, _ -> true }
        binding.bottomControlBar.setBackgroundResource(R.drawable.bg_gradient_bottom)
        
        handler.postDelayed(hideUnlockButtonRunnable, 2500)
    }

    private fun updateRepeatButtonState(isLooping: Boolean) {
        val tintColor = if (isLooping) {
            ContextCompat.getColor(this, R.color.secondary)
        } else {
            ContextCompat.getColor(this, R.color.white)
        }
        binding.btnRepeat.imageTintList = ColorStateList.valueOf(tintColor)
    }

    private fun lockScreen() {
        isScreenLocked = true
        gestureManager.isLocked = true
        val currentOrientation = resources.configuration.orientation
        requestedOrientation = if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        hideControls()
        binding.gestureOverlay.visibility = View.VISIBLE
        showUnlockButtonTemporarily()
    }

    private fun unlockScreen() {
        isScreenLocked = false
        gestureManager.isLocked = false
        handler.removeCallbacks(hideUnlockButtonRunnable)
        binding.btnUnlock.animate().cancel()
        binding.btnUnlock.visibility = View.GONE
        binding.gestureOverlay.visibility = View.VISIBLE
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        showControls()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        cachedStatusBarHeight = 0
        cachedNavBarHeight = 0
        activeAdaptiveSheet?.dismiss()
        setupFullscreen()
        applyOrientationLayout(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
    }

    private fun updatePipParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val paramsBuilder = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    paramsBuilder.setAutoEnterEnabled(false)
                }
                setPictureInPictureParams(paramsBuilder.build())
            } catch (e: Exception) {
                Log.w("PlayerActivity", "Gagal memperbarui PictureInPictureParams: ${e.message}")
            }
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val paramsBuilder = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
            enterPictureInPictureMode(paramsBuilder.build())
        } else {
            Toast.makeText(this, "Picture in Picture memerlukan Android 8.0+", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPipMode = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            hideControls()
            binding.gestureOverlay.visibility = View.GONE
        } else {
            binding.gestureOverlay.visibility = View.VISIBLE
        }
    }

    // ================= Adaptive Sheets (Right Side Sheet in Landscape, Bottom Sheet in Portrait) =================

    private interface AdaptiveSheet {
        fun dismiss()
        fun show()
    }

    private var activeAdaptiveSheet: AdaptiveSheet? = null

    private fun createAdaptiveSheet(
        contentView: View,
        blurBehind: Boolean = false,
        dimAmount: Float = 0.0f
    ): AdaptiveSheet {
        activeAdaptiveSheet?.dismiss()
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val sheet = if (isLandscape) {
            val dialog = Dialog(this, R.style.Theme_AniPlay_SideSheet)
            val scrollView = NestedScrollView(this).apply {
                isFillViewport = true
                background = ContextCompat.getDrawable(this@PlayerActivity, R.drawable.bg_glass_sidesheet)
                addView(
                    contentView,
                    android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
            dialog.setContentView(scrollView)
            dialog.window?.let { win ->
                win.setGravity(android.view.Gravity.END)
                val widthPx = (360 * resources.displayMetrics.density).toInt()
                win.setLayout(widthPx, WindowManager.LayoutParams.MATCH_PARENT)
                win.setWindowAnimations(R.style.Animation_SideSheet)
                win.setDimAmount(dimAmount)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    win.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    val params = win.attributes
                    params.blurBehindRadius = 0
                    win.attributes = params
                }
            }
            object : AdaptiveSheet {
                override fun dismiss() {
                    dialog.dismiss()
                    if (activeAdaptiveSheet === this) activeAdaptiveSheet = null
                }
                override fun show() {
                    dialog.show()
                }
            }
        } else {
            val dialog = BottomSheetDialog(this, R.style.Theme_AniPlay_BottomSheetDialog)
            dialog.setContentView(contentView)
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            dialog.behavior.skipCollapsed = true
            dialog.setOnShowListener {
                val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                    ?: (contentView.parent as? View)
                bottomSheet?.background = ContextCompat.getDrawable(this@PlayerActivity, R.drawable.bg_glass_sheet)
                bottomSheet?.let {
                    val behavior = BottomSheetBehavior.from(it)
                    behavior.state = BottomSheetBehavior.STATE_EXPANDED
                    behavior.skipCollapsed = true
                }
            }
            dialog.window?.let { win ->
                win.setDimAmount(dimAmount)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    win.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    val params = win.attributes
                    params.blurBehindRadius = 0
                    win.attributes = params
                }
            }
            object : AdaptiveSheet {
                override fun dismiss() {
                    dialog.dismiss()
                    if (activeAdaptiveSheet === this) activeAdaptiveSheet = null
                }
                override fun show() {
                    dialog.show()
                }
            }
        }
        activeAdaptiveSheet = sheet
        return sheet
    }

    enum class AspectRatioMode(val title: String) {
        FIT("Fit Layar (Asli)"),
        STRETCH("Regangkan Penuh"),
        ZOOM("Potong / Zoom Layar"),
        SIXTEEN_NINE("16:9 Standar")
    }

    private var currentAspectRatioMode: AspectRatioMode = AspectRatioMode.FIT

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun setVideoAspectRatio(mode: AspectRatioMode, showFeedback: Boolean = true) {
        currentAspectRatioMode = mode
        // 1. ExoPlayer
        when (mode) {
            AspectRatioMode.FIT -> binding.exoPlayerView.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            AspectRatioMode.STRETCH -> binding.exoPlayerView.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioMode.ZOOM -> binding.exoPlayerView.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioMode.SIXTEEN_NINE -> binding.exoPlayerView.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        }

        // 2. MPV
        try {
            when (mode) {
                AspectRatioMode.FIT -> {
                    playerEngine.mpv.setPropertyString("video-aspect-override", "-1")
                    playerEngine.mpv.setPropertyDouble("panscan", 0.0)
                }
                AspectRatioMode.STRETCH -> {
                    val dm = resources.displayMetrics
                    val displayAspect = dm.widthPixels.toDouble() / dm.heightPixels.toDouble()
                    playerEngine.mpv.setPropertyString("video-aspect-override", String.format(java.util.Locale.US, "%.4f", displayAspect))
                    playerEngine.mpv.setPropertyDouble("panscan", 0.0)
                }
                AspectRatioMode.ZOOM -> {
                    playerEngine.mpv.setPropertyString("video-aspect-override", "-1")
                    playerEngine.mpv.setPropertyDouble("panscan", 1.0)
                }
                AspectRatioMode.SIXTEEN_NINE -> {
                    playerEngine.mpv.setPropertyString("video-aspect-override", "16:9")
                    playerEngine.mpv.setPropertyDouble("panscan", 0.0)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("PlayerActivity", "Failed to set MPV aspect ratio: ${e.message}")
        }

        // Aspect ratio changed smoothly without obstructing overlay
    }

    private fun cycleAspectRatio() {
        val nextMode = when (currentAspectRatioMode) {
            AspectRatioMode.FIT -> AspectRatioMode.STRETCH
            AspectRatioMode.STRETCH -> AspectRatioMode.ZOOM
            AspectRatioMode.ZOOM -> AspectRatioMode.FIT
            AspectRatioMode.SIXTEEN_NINE -> AspectRatioMode.FIT
        }
        setVideoAspectRatio(nextMode)
    }

    private fun showAspectRatioBottomSheet() {
        val sheetBinding = com.personal.animeplayer.databinding.BottomSheetAspectRatioBinding.inflate(layoutInflater)
        val sheet = createAdaptiveSheet(sheetBinding.root)

        sheetBinding.btnBackSheet.setOnClickListener {
            sheet.dismiss()
        }

        val checkedId = when (currentAspectRatioMode) {
            AspectRatioMode.FIT -> R.id.chip_ratio_fit
            AspectRatioMode.STRETCH -> R.id.chip_ratio_stretch
            AspectRatioMode.ZOOM -> R.id.chip_ratio_zoom
            AspectRatioMode.SIXTEEN_NINE -> R.id.chip_ratio_16_9
        }
        sheetBinding.chipGroupAspectRatio.check(checkedId)

        sheetBinding.chipGroupAspectRatio.setOnCheckedStateChangeListener { _, checkedIds ->
            val mode = when (checkedIds.firstOrNull()) {
                R.id.chip_ratio_stretch -> AspectRatioMode.STRETCH
                R.id.chip_ratio_zoom -> AspectRatioMode.ZOOM
                R.id.chip_ratio_16_9 -> AspectRatioMode.SIXTEEN_NINE
                else -> AspectRatioMode.FIT
            }
            setVideoAspectRatio(mode)
            sheet.dismiss()
        }

        sheet.show()
    }

    private fun showPlayerOptionsBottomSheet() {
        val sheetBinding = BottomSheetPlayerOptionsBinding.inflate(layoutInflater)
        val sheet = createAdaptiveSheet(sheetBinding.root)

        // Current Speed display
        val currentSpeed = viewModel.playerData.value.playbackSpeed
        sheetBinding.tvOptSpeedValue.text = if (currentSpeed == 1.0f) "1.0x (Normal)" else "${currentSpeed}x"

        sheetBinding.optSpeed.setOnClickListener {
            sheet.dismiss()
            showSpeedBottomSheet()
        }

        sheetBinding.optAudio.setOnClickListener {
            sheet.dismiss()
            showAudioTracksBottomSheet()
        }

        sheetBinding.optSubtitle.setOnClickListener {
            sheet.dismiss()
            showSubtitlesBottomSheet()
        }
        sheetBinding.optPip.setOnClickListener {
            sheet.dismiss()
            enterPipMode()
        }

        // Current Skip 85s display
        val isSkip = viewModel.playerData.value.isSkipOpEdEnabled
        sheetBinding.tvOptSkipOpEdValue.text = if (isSkip) "Aktif" else "Nonaktif"
        sheetBinding.switchOptSkipOpEd.isChecked = isSkip
        sheetBinding.ivOptSkipOpEdIcon.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, if (isSkip) R.color.secondary else R.color.on_surface_muted)
        )

        sheetBinding.optSkipOpEd.setOnClickListener {
            val newSkip = !viewModel.playerData.value.isSkipOpEdEnabled
            viewModel.saveSkipOpEdEnabled(newSkip)
            binding.btnSkipOpEd.visibility = if (newSkip) View.VISIBLE else View.GONE
            sheetBinding.switchOptSkipOpEd.isChecked = newSkip
            sheetBinding.tvOptSkipOpEdValue.text = if (newSkip) "Aktif" else "Nonaktif"
            sheetBinding.ivOptSkipOpEdIcon.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, if (newSkip) R.color.secondary else R.color.on_surface_muted)
            )
        }

        sheetBinding.optDelete.setOnClickListener {
            sheet.dismiss()
            showDeleteConfirmDialog()
        }

        sheet.show()
    }

    private var activeDeleteDialog: androidx.appcompat.app.AlertDialog? = null

    private fun showDeleteConfirmDialog() {
        val currentEp = viewModel.getCurrentEpisode()
        val title = currentEp?.title ?: videoTitle
        val dialogBinding = DialogDeleteConfirmBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.tvDeleteTitle.text = "Hapus Video?"
        dialogBinding.tvDeleteMessage.text = "Apakah Anda yakin ingin menghapus \"$title\"? Video akan dihapus secara permanen dari perangkat dan otomatis memutar video selanjutnya."

        dialogBinding.btnCancelDelete.setOnClickListener {
            dialog.dismiss()
            activeDeleteDialog = null
        }
        dialogBinding.btnConfirmDelete.setOnClickListener {
            dialog.dismiss()
            activeDeleteDialog = null
            executeDeleteCurrentVideo()
        }

        activeDeleteDialog = dialog
        dialog.show()
    }

    private fun executeDeleteCurrentVideo() {
        lifecycleScope.launch {
            pauseAllEngines()
            when (val res = viewModel.deleteCurrentVideo()) {
                is FileOpResult.Success -> {
                    handlePostDelete()
                }
                is FileOpResult.RequiresConsent -> {
                    deleteConsentLauncher.launch(
                        IntentSenderRequest.Builder(res.intentSender).build()
                    )
                }
                is FileOpResult.Error -> {
                    Toast.makeText(this@PlayerActivity, res.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun handlePostDelete() {
        val nextEp = viewModel.removeCurrentEpisodeAndGetNext()
        if (nextEp != null) {
            switchVideo(nextEp)
        } else {
            finish()
        }
    }

    private fun applyOrientationLayout(isLandscape: Boolean) {
        val density = resources.displayMetrics.density
        val padH = (16 * density).toInt()

        val rootInsets = ViewCompat.getRootWindowInsets(binding.root)
        val navInsets = rootInsets?.getInsets(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout())
        val effectiveNbBottom = navInsets?.bottom?.takeIf { it > 0 } ?: cachedNavBarHeight
        val effectiveNbLeft = navInsets?.left ?: 0
        val effectiveNbRight = navInsets?.right ?: 0

        if (isLandscape) {
            val padV = (2 * density).toInt()
            binding.bottomControlBar.setPadding(
                padH + effectiveNbLeft,
                padV,
                padH + effectiveNbRight,
                effectiveNbBottom + padV
            )

            val playPauseSize = (52 * density).toInt()
            val skipSize = (38 * density).toInt()
            binding.btnPlayPause.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                width = playPauseSize
                height = playPauseSize
            }
            binding.btnPrev.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                width = skipSize
                height = skipSize
                marginEnd = (24 * density).toInt()
            }
            binding.btnNext.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                width = skipSize
                height = skipSize
                marginStart = (24 * density).toInt()
            }
        } else {
            val padTop = (4 * density).toInt()
            val padBottom = (4 * density).toInt()
            binding.bottomControlBar.setPadding(
                padH,
                padTop,
                padH,
                effectiveNbBottom + padBottom
            )

            val playPauseSize = (52 * density).toInt()
            val skipSize = (38 * density).toInt()
            binding.btnPlayPause.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                width = playPauseSize
                height = playPauseSize
            }
            binding.btnPrev.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                width = skipSize
                height = skipSize
                marginEnd = (24 * density).toInt()
            }
            binding.btnNext.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                width = skipSize
                height = skipSize
                marginStart = (24 * density).toInt()
            }
        }

        val speedMarginDp = if (isLandscape) 24 else 98
        val statusBarHeight = rootInsets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: cachedStatusBarHeight
        binding.speedOverlayView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight + (speedMarginDp * density).toInt()
        }
    }

    private fun showSpeedBottomSheet() {
        val sheetBinding = BottomSheetSpeedBinding.inflate(layoutInflater)
        val sheet = createAdaptiveSheet(sheetBinding.root)

        sheetBinding.btnBackSheet.setOnClickListener {
            sheet.dismiss()
            showPlayerOptionsBottomSheet()
        }

        val currentSpeed = viewModel.playerData.value.playbackSpeed
        val chipId = when (currentSpeed) {
            0.5f -> R.id.chip_05x
            0.75f -> R.id.chip_075x
            1.25f -> R.id.chip_125x
            2.0f -> R.id.chip_20x
            3.0f -> R.id.chip_30x
            else -> R.id.chip_10x
        }
        sheetBinding.chipGroupSpeed.check(chipId)

        sheetBinding.chipGroupSpeed.setOnCheckedStateChangeListener { _, checkedIds ->
            val speed = when (checkedIds.firstOrNull()) {
                R.id.chip_05x -> 0.5f
                R.id.chip_075x -> 0.75f
                R.id.chip_125x -> 1.25f
                R.id.chip_20x -> 2.0f
                R.id.chip_30x -> 3.0f
                else -> 1.0f
            }
            doSetPlaybackSpeed(speed)
            viewModel.savePlaybackSpeed(speed)
            sheet.dismiss()
        }

        sheet.show()
    }

    private fun showSubtitlesBottomSheet() {
        val sheetBinding = BottomSheetTracksBinding.inflate(layoutInflater)
        val sheet = createAdaptiveSheet(sheetBinding.root)

        sheetBinding.btnBackSheet.setOnClickListener {
            sheet.dismiss()
            showPlayerOptionsBottomSheet()
        }

        sheetBinding.tvSheetTitle.text = getString(R.string.subtitles)
        sheetBinding.containerSubtitleActions.visibility = View.VISIBLE

        sheetBinding.btnLoadExternalSub.setOnClickListener {
            sheet.dismiss()
            pickSubtitleLauncher.launch(arrayOf("*/*"))
        }

        sheetBinding.btnCustomStyle.setOnClickListener {
            sheet.dismiss()
            showSubtitleStyleBottomSheet()
        }

        val tracks = if (activeEngine == "exoplayer" && ::exoPlayerEngine.isInitialized) {
            exoPlayerEngine.getSubtitleTracks()
        } else {
            playerEngine.getSubtitleTracks()
        }
        val items = mutableListOf<TrackItemDisplay>()

        // Option to turn off subtitles
        items.add(
            TrackItemDisplay(
                title = "Nonaktifkan Subtitle",
                subtitle = "Matikan subtitle",
                isSelected = tracks.none { it.isSelected },
                trackGroupIndex = -1,
                trackIndex = -1
            )
        )

        tracks.forEach { track ->
            items.add(
                TrackItemDisplay(
                    title = track.label,
                    subtitle = track.language ?: "Embedded Subtitle",
                    isSelected = track.isSelected,
                    trackGroupIndex = track.trackGroupIndex,
                    trackIndex = track.trackIndex
                )
            )
        }

        sheetBinding.rvTracks.adapter = TrackChoiceAdapter(
            items = items,
            onTrackSelected = { item ->
                if (activeEngine == "exoplayer" && ::exoPlayerEngine.isInitialized) {
                    exoPlayerEngine.selectSubtitleTrack(item.trackIndex)
                } else {
                    playerEngine.selectSubtitleTrack(item.trackIndex)
                }
                sheet.dismiss()
            }
        )

        sheet.show()
    }

    private fun showSubtitleStyleBottomSheet() {
        val sheetBinding = BottomSheetSubtitleStyleBinding.inflate(layoutInflater)
        val sheet = createAdaptiveSheet(sheetBinding.root, blurBehind = false, dimAmount = 0.0f)

        sheetBinding.btnBackSheet.setOnClickListener {
            sheet.dismiss()
            showSubtitlesBottomSheet()
        }

        val data = viewModel.playerData.value
        sheetBinding.tvOffsetValue.text = "Offset: ${data.subtitleDelayMs / 1000.0}s"

        var currentOffset = data.subtitleDelayMs
        sheetBinding.btnOffsetMinus.setOnClickListener {
            currentOffset -= 500
            playerEngine.setSubtitleDelay(currentOffset)
            sheetBinding.tvOffsetValue.text = "Offset: ${currentOffset / 1000.0}s"
            viewModel.updateSubtitlePreferences(delayMs = currentOffset)
        }
        sheetBinding.btnOffsetPlus.setOnClickListener {
            currentOffset += 500
            playerEngine.setSubtitleDelay(currentOffset)
            sheetBinding.tvOffsetValue.text = "Offset: ${currentOffset / 1000.0}s"
            viewModel.updateSubtitlePreferences(delayMs = currentOffset)
        }
        sheetBinding.btnOffsetReset.setOnClickListener {
            currentOffset = 0L
            playerEngine.setSubtitleDelay(0L)
            sheetBinding.tvOffsetValue.text = "Offset: 0.0s"
            viewModel.updateSubtitlePreferences(delayMs = 0L)
        }

        val currentScale = viewModel.playerData.value.subtitleScale
        sheetBinding.tvFontSizeLabel.text = "Skala Subtitle: ${currentScale}%"
        sheetBinding.seekbarFontSize.max = 30
        val initialProgress = ((currentScale - 50) / 5).coerceIn(0, 30)
        sheetBinding.seekbarFontSize.progress = initialProgress

        sheetBinding.seekbarFontSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val scalePercent = 50 + (progress * 5)
                sheetBinding.tvFontSizeLabel.text = "Skala Subtitle: ${scalePercent}%"
                if (fromUser) {
                    playerEngine.setSubtitleScale(scalePercent)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                sheetBinding.root.animate().alpha(0.65f).setDuration(120).start()
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                sheetBinding.root.animate().alpha(1.0f).setDuration(120).start()
                val scalePercent = 50 + ((seekBar?.progress ?: 0) * 5)
                playerEngine.setSubtitleScale(scalePercent)
                viewModel.updateSubtitlePreferences(scalePercent = scalePercent)
            }
        })

        sheet.show()
    }

    private fun showAudioTracksBottomSheet() {
        val sheetBinding = BottomSheetTracksBinding.inflate(layoutInflater)
        val sheet = createAdaptiveSheet(sheetBinding.root)

        sheetBinding.btnBackSheet.setOnClickListener {
            sheet.dismiss()
            showPlayerOptionsBottomSheet()
        }

        sheetBinding.tvSheetTitle.text = getString(R.string.audio_tracks)
        sheetBinding.containerSubtitleActions.visibility = View.GONE

        val tracks = if (activeEngine == "exoplayer") exoPlayerEngine.getAudioTracks() else playerEngine.getAudioTracks()
        val items = tracks.map { track ->
            TrackItemDisplay(
                title = track.label,
                subtitle = "${track.language ?: "Audio"} • ${track.channels}ch",
                isSelected = track.isSelected,
                trackGroupIndex = track.trackGroupIndex,
                trackIndex = track.trackIndex
            )
        }

        sheetBinding.rvTracks.adapter = TrackChoiceAdapter(
            items = items,
            onTrackSelected = { item ->
                if (activeEngine == "exoplayer") {
                    exoPlayerEngine.selectAudioTrack(item.trackIndex)
                } else {
                    playerEngine.selectAudioTrack(item.trackIndex)
                }
                sheet.dismiss()
            }
        )

        sheet.show()
    }

    private fun showErrorDialog(message: String) {
        binding.loadingIndicator.visibility = View.GONE
        isSwitchingEpisode = false
        handler.removeCallbacks(resetSwitchingRunnable)
        AlertDialog.Builder(this)
            .setTitle("Kesalahan Pemutaran")
            .setMessage(message)
            .setPositiveButton("Tutup") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    override fun onResume() {
        super.onResume()
        isPipMode = false
        updatePipParams()
        handler.post(updateProgressRunnable)
    }

    override fun finish() {
        // Hentikan pemutaran audio seketika saat keluar dari video
        pauseAllEngines(stopPlayback = true)
        super.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_CLOSE,
                R.anim.player_close_enter,
                R.anim.player_close_exit
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(
                R.anim.player_close_enter,
                R.anim.player_close_exit
            )
        }
    }

    private fun pauseAllEngines(stopPlayback: Boolean = false) {
        handler.removeCallbacks(hideControlsRunnable)
        if (::exoPlayerEngine.isInitialized) {
            try {
                if (stopPlayback) {
                    exoPlayerEngine.stop()
                } else {
                    exoPlayerEngine.pause()
                }
            } catch (e: Exception) {
                Log.e("PlayerActivity", "Error stopping/pausing ExoPlayer: ${e.message}")
            }
        }
        if (::playerEngine.isInitialized) {
            try {
                if (stopPlayback) {
                    playerEngine.stop()
                } else {
                    playerEngine.pause()
                }
            } catch (e: Exception) {
                Log.e("PlayerActivity", "Error stopping/pausing MPV: ${e.message}")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        activeAdaptiveSheet?.dismiss()
        inactivityCountdownTimer?.cancel()
        inactivityCountdownTimer = null
        handler.removeCallbacks(hideSpeedOverlayRunnable)
        if (::playerEngine.isInitialized || ::exoPlayerEngine.isInitialized) {
            val pos = currentPosition
            val dur = duration
            if (pos > 0 && dur > 0) {
                viewModel.saveProgress(pos, dur)
            }
        }

        // HANYA jeda pemutaran jika aktivitas sedang keluar (isFinishing).
        // Pada Android 7.0+ (API 24+), onPause dipanggil saat overlay (seperti Circle to Search,
        // Google Assistant, popup deteksi lagu, floating window, atau split-screen) muncul di layar.
        // Pemutaran video dan audio TETAP BERJALAN di onPause agar Google Deteksi Lagu dapat
        // mendengarkan lagu yang sedang berputar secara real-time tanpa henti.
        if (isFinishing) {
            pauseAllEngines(stopPlayback = true)
        }
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(updateProgressRunnable)
        val wasInPip = isPipMode || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode)
        if (wasInPip) {
            isPipMode = false
            finish()
        }
        // onStop() menandakan jendela aktivitas tidak lagi tampil sama sekali di layar (misal user ke Home tanpa PiP, atau layar mati).
        // Jeda pemutaran audio/video jika tidak dalam mode PiP!
        if (!isPipMode && (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !isInPictureInPictureMode)) {
            pauseAllEngines(stopPlayback = isFinishing)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(hideUnlockButtonRunnable)
        handler.removeCallbacksAndMessages(null)
        pauseAllEngines(stopPlayback = true)
        if (::playerEngine.isInitialized || ::exoPlayerEngine.isInitialized) {
            val pos = currentPosition
            val dur = duration
            if (pos > 0 && dur > 0) {
                viewModel.saveProgress(pos, dur)
            }
        }
        val brightnessToSave = if (isHdrActive && previousSdrBrightness in 0.01f..1.0f) {
            previousSdrBrightness
        } else {
            window.attributes.screenBrightness
        }
        if (brightnessToSave in 0.01f..1.0f) {
            viewModel.saveBrightness(brightnessToSave)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
        }
        if (Build.VERSION.SDK_INT >= 35) {
            window.setDesiredHdrHeadroom(0.0f)
        }
        inactivityCountdownTimer?.cancel()
        inactivityCountdownTimer = null
        if (::exoPlayerEngine.isInitialized) {
            exoPlayerEngine.release()
        }
        if (::playerEngine.isInitialized) {
            playerEngine.release()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isScreenLocked && event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) {
                showUnlockButtonTemporarily()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_VIDEO_TITLE = "extra_video_title"
        const val EXTRA_SERIES_FOLDER_URI = "extra_series_folder_uri"

        fun start(context: Context, videoUri: Uri, title: String, seriesFolderUri: String?) {
            val intent = Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URI, videoUri.toString())
                putExtra(EXTRA_VIDEO_TITLE, title)
                putExtra(EXTRA_SERIES_FOLDER_URI, seriesFolderUri)
            }
            val options = ActivityOptionsCompat.makeCustomAnimation(
                context,
                R.anim.player_open_enter,
                R.anim.player_open_exit
            )
            context.startActivity(intent, options.toBundle())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                (context as? Activity)?.overrideActivityTransition(
                    Activity.OVERRIDE_TRANSITION_OPEN,
                    R.anim.player_open_enter,
                    R.anim.player_open_exit
                )
            } else {
                @Suppress("DEPRECATION")
                (context as? Activity)?.overridePendingTransition(
                    R.anim.player_open_enter,
                    R.anim.player_open_exit
                )
            }
        }
    }
}
