package com.personal.animeplayer.ui.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Rational
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.personal.animeplayer.R
import com.personal.animeplayer.data.model.Episode
import com.personal.animeplayer.data.model.FileOpResult
import com.personal.animeplayer.player.AspectRatioMode
import com.personal.animeplayer.player.DualEngineManager
import com.personal.animeplayer.player.GestureListener
import com.personal.animeplayer.player.GestureManager
import com.personal.animeplayer.player.LockAction
import com.personal.animeplayer.player.MPVView
import com.personal.animeplayer.player.PlaylistHolder
import com.personal.animeplayer.player.ScreenLockController
import com.personal.animeplayer.ui.compose.player.DoubleTapOverlayState
import com.personal.animeplayer.ui.compose.player.GestureFeedbackState
import com.personal.animeplayer.ui.compose.player.PlayerSheetState
import com.personal.animeplayer.ui.compose.player.SpeedOverlayDefaults
import com.personal.animeplayer.ui.compose.theme.AniPlayTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity(), GestureListener {

    private val viewModel: PlayerViewModel by viewModels()

    private lateinit var mpvView: MPVView
    private lateinit var exoPlayerView: PlayerView
    private lateinit var gestureOverlayView: View

    private lateinit var dualEngineManager: DualEngineManager
    private lateinit var hdrDisplayController: HdrDisplayController
    private lateinit var inactivityMonitor: InactivityMonitor
    private lateinit var gestureManager: GestureManager

    private val screenLockController = ScreenLockController()
    private val isScreenLocked: Boolean
        get() = screenLockController.isLocked

    private var uiState by mutableStateOf(PlayerScreenUiState())
    private var timelineState by mutableStateOf(PlayerTimelineState())
    private var isPipReceiverRegistered = false

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_PIP_CONTROL || !::dualEngineManager.isInitialized) return
            when (intent.getIntExtra(EXTRA_PIP_CONTROL_TYPE, 0)) {
                PIP_CONTROL_REWIND -> doSeekBy(-10_000L)
                PIP_CONTROL_PLAY -> {
                    dualEngineManager.play()
                    uiState = uiState.copy(isPlaying = true)
                    updatePipParams(playingOverride = true)
                }
                PIP_CONTROL_PAUSE -> {
                    dualEngineManager.pause()
                    uiState = uiState.copy(isPlaying = false)
                    updatePipParams(playingOverride = false)
                }
                PIP_CONTROL_FORWARD -> doSeekBy(10_000L)
                PIP_CONTROL_NEXT -> {
                    if (!isSwitchingEpisode) {
                        viewModel.getNextEpisode()?.let { switchVideo(it) }
                    }
                }
            }
        }
    }

    private val isPlaying: Boolean
        get() = ::dualEngineManager.isInitialized && dualEngineManager.isPlaying

    private val currentPosition: Long
        get() = if (::dualEngineManager.isInitialized) dualEngineManager.currentPosition else 0L

    private val duration: Long
        get() = if (::dualEngineManager.isInitialized) dualEngineManager.duration else 0L

    private val isLoopingEnabled: Boolean
        get() = viewModel.playerData.value.isLoopEnabled

    private val deleteConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            handlePostDelete()
        } else {
            Toast.makeText(this, "Izin hapus video ditolak oleh pengguna", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickSubtitleLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                Log.w("PlayerActivity", "Persistable URI permission not granted for subtitle: ${e.message}")
            }
            if (::dualEngineManager.isInitialized) {
                dualEngineManager.loadExternalSubtitle(uri, select = true)
            }
            Toast.makeText(this, "Subtitle eksternal termuat!", Toast.LENGTH_SHORT).show()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable { hideControls() }
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            val delay = if (isControlsVisible) 100L else 1000L
            handler.postDelayed(this, delay)
        }
    }

    private var videoUri: Uri? = null
    private var videoTitle: String = ""
    private var seriesFolderUri: String? = null
    private var isControlsVisible = true
    private var isMediaStarted = false

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

    // Double-tap accumulation state
    private var accumulatedDoubleTapSec = 0
    private var lastDoubleTapForward = false
    private var doubleTapActive = false
    private var doubleTapToken = 0
    private val resetDoubleTapRunnable = Runnable {
        doubleTapActive = false
        accumulatedDoubleTapSec = 0
    }

    private var gestureFeedbackToken = 0
    private val hideFeedbackRunnable = Runnable {
        uiState = uiState.copy(gestureFeedback = null)
    }

    private val hideSpeedOverlayRunnable = Runnable {
        uiState = uiState.copy(isSpeedOverlayVisible = false)
    }

    private val hideUnlockButtonRunnable = Runnable {
        if (isScreenLocked) {
            uiState = uiState.copy(isUnlockPromptVisible = false)
        }
    }

    private var isSwitchingEpisode = false
    private val resetSwitchingRunnable = Runnable {
        isSwitchingEpisode = false
        uiState = uiState.copy(isLoading = false)
    }
    private var lastPlayPauseClickTime: Long = 0L
    private var isPipMode = false

    private var lastProgressSaveTimestamp = 0L
    private var lastDisplayedSec = -1L

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (::inactivityMonitor.isInitialized) {
            inactivityMonitor.onUserInteraction()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setupFullscreen()

        mpvView = MPVView(this).apply { visibility = View.GONE }
        exoPlayerView = PlayerView(this).apply {
            useController = false
            visibility = View.GONE
        }
        gestureOverlayView = View(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::inactivityMonitor.isInitialized && inactivityMonitor.isPromptShowing) {
                    inactivityMonitor.continueWatching()
                    return
                }
                if (screenLockController.onBackPressed() == LockAction.REVEAL_UNLOCK_ONLY) {
                    showUnlockButtonTemporarily()
                    uiState = uiState.copy(unlockShakeToken = uiState.unlockShakeToken + 1)
                    return
                }
                finish()
            }
        })

        parseIntent()
        initPlayerComponents()
        observeViewModel()

        uiState = uiState.copy(
            videoTitle = videoTitle,
            isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
            isControlsVisible = isControlsVisible
        )

        setContent {
            val playerData by viewModel.playerData.collectAsStateWithLifecycle()

            AniPlayTheme {
                PlayerScreen(
                    uiState = uiState,
                    timelineStateProvider = { timelineState },
                    playerData = playerData,
                    mpvView = mpvView,
                    exoPlayerView = exoPlayerView,
                    gestureOverlayView = gestureOverlayView,
                    onBackClick = { finish() },
                    onMoreOptionsClick = { navigateToSheet(PlayerSheetState.PlayerOptions) },
                    onLockClick = { lockScreen() },
                    onUnlockClick = { unlockScreen() },
                    onSkipOpEdClick = {
                        doSeekBy(85_000L)
                        scheduleHideControls()
                    },
                    onOrientationToggleClick = {
                        val currentOrientation = resources.configuration.orientation
                        requestedOrientation = if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                        scheduleHideControls()
                    },
                    onRepeatToggleClick = {
                        val newLoop = !isLoopingEnabled
                        dualEngineManager.setLooping(newLoop)
                        viewModel.saveLoopEnabled(newLoop)
                        scheduleHideControls()
                    },
                    onPreviousClick = {
                        if (!isSwitchingEpisode) {
                            inactivityMonitor.resetInteractionTimer()
                            val prev = viewModel.getPreviousEpisode()
                            if (prev != null) {
                                switchVideo(prev)
                            } else {
                                Toast.makeText(this, "Tidak ada episode sebelumnya", Toast.LENGTH_SHORT).show()
                            }
                            scheduleHideControls()
                        }
                    },
                    onPlayPauseClick = { handlePlayPauseClick() },
                    onNextClick = {
                        if (!isSwitchingEpisode) {
                            inactivityMonitor.resetInteractionTimer()
                            val next = viewModel.getNextEpisode()
                            if (next != null) {
                                switchVideo(next)
                            } else {
                                Toast.makeText(this, "Tidak ada episode selanjutnya", Toast.LENGTH_SHORT).show()
                            }
                            scheduleHideControls()
                        }
                    },
                    onAspectRatioClick = {
                        val nextMode = dualEngineManager.cycleAspectRatio()
                        uiState = uiState.copy(currentAspectRatio = nextMode)
                        scheduleHideControls()
                    },
                    onAspectRatioLongClick = { navigateToSheet(PlayerSheetState.AspectRatio) },
                    onScrubStart = {
                        isUserScrubbing = true
                        handler.removeCallbacks(hideControlsRunnable)
                        wasPlayingBeforeScrub = isPlaying
                        if (wasPlayingBeforeScrub) {
                            dualEngineManager.pause()
                        }
                    },
                    onScrubChange = { posMs ->
                        timelineState = timelineState.copy(positionMs = posMs)
                        performLiveScrub(posMs)
                    },
                    onScrubEnd = { posMs ->
                        isUserScrubbing = false
                        confirmLiveScrub(posMs)
                        if (wasPlayingBeforeScrub) {
                            dualEngineManager.play()
                        }
                        scheduleHideControls()
                    },
                    onContinueWatchingClick = {
                        if (::inactivityMonitor.isInitialized) {
                            inactivityMonitor.continueWatching()
                        }
                    },
                    onDismissSheet = { dismissActiveSheet() },
                    onNavigateSheet = { target -> navigateToSheet(target) },
                    onSelectAspectRatio = { mode ->
                        dualEngineManager.setAspectRatio(mode)
                        uiState = uiState.copy(currentAspectRatio = mode)
                    },
                    onSelectSpeed = { speed ->
                        dualEngineManager.setPlaybackSpeed(speed)
                        viewModel.savePlaybackSpeed(speed)
                    },
                    onSelectAudioTrack = { trackIndex ->
                        dualEngineManager.selectAudioTrack(trackIndex)
                    },
                    onSelectSubtitleTrack = { trackIndex ->
                        dualEngineManager.selectSubtitleTrack(trackIndex)
                    },
                    onPickExternalSubtitle = {
                        pickSubtitleLauncher.launch(arrayOf("*/*"))
                    },
                    onSubtitleDelayChange = { offsetMs ->
                        dualEngineManager.setSubtitleDelay(offsetMs)
                        viewModel.updateSubtitlePreferences(delayMs = offsetMs)
                    },
                    onSubtitleScaleLiveChange = { scalePercent ->
                        dualEngineManager.setSubtitleScale(scalePercent)
                    },
                    onSubtitleScaleCommit = { scalePercent ->
                        dualEngineManager.setSubtitleScale(scalePercent)
                        viewModel.updateSubtitlePreferences(scalePercent = scalePercent)
                    },
                    onToggleSkipOpEd = { enabled ->
                        viewModel.saveSkipOpEdEnabled(enabled)
                    },
                    onEnterPipMode = { enterPipMode() },
                    onConfirmDeleteVideo = { executeDeleteCurrentVideo() },
                    onErrorDismissed = { finish() }
                )
            }
        }
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
    }

    private fun parseIntent() {
        val uriStr = intent.getStringExtra(EXTRA_VIDEO_URI) ?: intent.dataString
        val parsedUri = uriStr?.let { Uri.parse(it) }
        val explicitTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?.let { com.personal.animeplayer.util.FileUtils.decodePercentEncoded(it) }

        videoTitle = explicitTitle
            ?: parsedUri?.let { resolveDisplayTitleFromUri(it) }
            ?: "Anime Video"
        seriesFolderUri = intent.getStringExtra(EXTRA_SERIES_FOLDER_URI)

        if (parsedUri != null) {
            videoUri = parsedUri
            PlaylistHolder.lastPlayedUri = parsedUri.toString()
            PlaylistHolder.shouldScrollToLastPlayed = true
        } else {
            Toast.makeText(this, "URI video tidak valid", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun resolveDisplayTitleFromUri(uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                contentResolver.query(
                    uri,
                    arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(0)
                        if (!name.isNullOrBlank()) {
                            return com.personal.animeplayer.util.FileUtils.getDisplayTitle(name)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        val rawSegment = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.substringAfterLast(':')
            ?.takeIf { it.isNotBlank() }
        return rawSegment?.let { com.personal.animeplayer.util.FileUtils.getDisplayTitle(it) }
    }

    private fun initPlayerComponents() {
        hdrDisplayController = HdrDisplayController(
            context = this,
            window = window,
            onHdrBadgeChanged = { isHdr, hdrType ->
                uiState = uiState.copy(isHdrActive = isHdr, hdrType = hdrType)
            },
            getSavedBrightness = { viewModel.playerData.value.savedBrightness }
        )

        dualEngineManager = DualEngineManager(
            context = this,
            mpvView = mpvView,
            exoPlayerView = exoPlayerView,
            listener = object : DualEngineManager.Listener {
                override fun onPlaybackStateChanged(isPlaying: Boolean) {
                    handleEnginePlaybackStateChanged(isPlaying)
                }

                override fun onPositionChanged(currentPosMs: Long, durationMs: Long) {
                    handleEnginePositionChanged(currentPosMs, durationMs)
                }

                override fun onVideoReady(durationMs: Long) {
                    handleEngineVideoReady(durationMs)
                }

                override fun onPlayerError(message: String) {
                    isSwitchingEpisode = false
                    handler.removeCallbacks(resetSwitchingRunnable)
                    uiState = uiState.copy(
                        isLoading = false,
                        activeSheet = PlayerSheetState.PlaybackError(message)
                    )
                }

                override fun onPlaybackEnded() {
                    handlePlaybackEnded()
                }

                override fun onHdrStateDetected(isHdr: Boolean, hdrType: String) {
                    hdrDisplayController.applyHdrState(isHdr, hdrType)
                }
            }
        )

        inactivityMonitor = InactivityMonitor(
            onPromptStateChanged = { isShowing, secondsLeft ->
                uiState = uiState.copy(
                    isInactivityPromptVisible = isShowing,
                    inactivitySecondsLeft = secondsLeft
                )
            },
            onPausePlayback = { if (::dualEngineManager.isInitialized) dualEngineManager.pause() },
            onHideControls = { hideControls() },
            onResumePlayback = { if (::dualEngineManager.isInitialized) dualEngineManager.play() },
            onSwitchToEpisode = { ep -> switchVideo(ep) },
            onKeepScreenOn = { window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) },
            onExitApp = { exitAppOnInactivity() },
            getCurrentPosition = { currentPosition },
            getDuration = { duration },
            getNextEpisode = { viewModel.getNextEpisode() }
        )

        gestureManager = GestureManager(
            activity = this,
            overlayView = gestureOverlayView,
            listener = this,
            getCurrentPosition = { currentPosition },
            getDuration = { duration }
        )

        videoUri?.let {
            viewModel.initVideo(it, videoTitle, seriesFolderUri)
        }
    }

    private fun handlePlayPauseClick() {
        val now = SystemClock.uptimeMillis()
        if (now - lastPlayPauseClickTime < 150L) return
        lastPlayPauseClickTime = now

        val willPlay = !isPlaying
        uiState = uiState.copy(isPlaying = willPlay)

        if (willPlay) {
            dualEngineManager.play()
            scheduleHideControls()
        } else {
            dualEngineManager.pause()
            handler.removeCallbacks(hideControlsRunnable)
        }
        updatePipParams(playingOverride = willPlay)
    }

    private fun navigateToSheet(target: PlayerSheetState) {
        val resolvedTarget = if (target is PlayerSheetState.DeleteConfirm) {
            val currentEp = viewModel.getCurrentEpisode()
            PlayerSheetState.DeleteConfirm(currentEp?.title ?: videoTitle)
        } else {
            target
        }

        val subTracks = if (resolvedTarget == PlayerSheetState.Subtitles && ::dualEngineManager.isInitialized) {
            dualEngineManager.getSubtitleTracks()
        } else {
            uiState.subtitleTracks
        }
        val audioTracks = if (resolvedTarget == PlayerSheetState.AudioTracks && ::dualEngineManager.isInitialized) {
            dualEngineManager.getAudioTracks()
        } else {
            uiState.audioTracks
        }
        val engine = if (::dualEngineManager.isInitialized) {
            dualEngineManager.activeEngine
        } else {
            uiState.activeEngine
        }
        val aspect = if (::dualEngineManager.isInitialized) {
            dualEngineManager.currentAspectRatioMode
        } else {
            uiState.currentAspectRatio
        }

        uiState = uiState.copy(
            activeSheet = resolvedTarget,
            subtitleTracks = subTracks,
            audioTracks = audioTracks,
            activeEngine = engine,
            currentAspectRatio = aspect
        )

        val isPanel = resolvedTarget != PlayerSheetState.None &&
            resolvedTarget !is PlayerSheetState.DeleteConfirm &&
            resolvedTarget !is PlayerSheetState.PlaybackError
        if (isPanel) {
            handler.removeCallbacks(hideControlsRunnable)
            WindowInsetsControllerCompat(window, window.decorView)
                .hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun dismissActiveSheet(restoreBars: Boolean = true) {
        if (uiState.activeSheet !is PlayerSheetState.PlaybackError) {
            val hadSheet = uiState.activeSheet != PlayerSheetState.None
            uiState = uiState.copy(activeSheet = PlayerSheetState.None)
            if (restoreBars && hadSheet && isControlsVisible && !isScreenLocked) {
                WindowInsetsControllerCompat(window, window.decorView)
                    .show(WindowInsetsCompat.Type.statusBars())
                scheduleHideControls()
            }
        }
    }

    private fun doSeekTo(posMs: Long, fast: Boolean = false) {
        if (::dualEngineManager.isInitialized) {
            dualEngineManager.seekTo(posMs, fast)
        }
        lastDisplayedSec = -1L
        timelineState = timelineState.copy(positionMs = posMs)
    }

    private fun doSeekBy(deltaMs: Long) {
        if (::dualEngineManager.isInitialized) {
            val target = dualEngineManager.seekBy(deltaMs)
            lastDisplayedSec = -1L
            timelineState = timelineState.copy(positionMs = target)
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

    private fun handleEnginePlaybackStateChanged(isPlaying: Boolean) {
        if (uiState.isPlaying != isPlaying) {
            uiState = uiState.copy(isPlaying = isPlaying)
        }
        if (isPlaying) {
            scheduleHideControls()
        } else {
            handler.removeCallbacks(hideControlsRunnable)
        }
        updatePipParams(playingOverride = isPlaying)
    }

    private fun handleEnginePositionChanged(currentPos: Long, duration: Long) {
        if (duration > 0 && !isUserScrubbing) {
            val isTimelineVisible = !isPipMode &&
                ((isControlsVisible && !isScreenLocked) ||
                    (isScreenLocked && uiState.isUnlockPromptVisible))
            if (isTimelineVisible ||
                currentPos / 1000L != timelineState.positionMs / 1000L ||
                duration != timelineState.durationMs
            ) {
                timelineState = PlayerTimelineState(positionMs = currentPos, durationMs = duration)
            }
        }
    }

    private fun handleEngineVideoReady(duration: Long) {
        isSwitchingEpisode = false
        handler.removeCallbacks(resetSwitchingRunnable)
        timelineState = timelineState.copy(durationMs = duration)
        uiState = uiState.copy(
            isLoading = false,
            hasPrevious = viewModel.hasPrevious(),
            hasNext = viewModel.hasNext()
        )
        updatePipParams()
    }

    private fun playMediaOnActiveEngine(
        uri: Uri,
        title: String,
        startPositionMs: Long,
        externalSubtitles: List<Uri> = emptyList()
    ) {
        val isNextLikelyHdr = hdrDisplayController.isLikelyHdrOrCamera(uri, title)
        if (!isNextLikelyHdr) {
            hdrDisplayController.applyHdrState(false, "SDR")
        }
        dualEngineManager.playMedia(
            uri = uri,
            title = title,
            startPositionMs = startPositionMs,
            externalSubtitles = externalSubtitles,
            prefEngine = DualEngineManager.ENGINE_AUTO,
            isLikelyHdrOrCamera = isNextLikelyHdr
        )
        uiState = uiState.copy(activeEngine = dualEngineManager.activeEngine)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.playerData.collectLatest { data ->
                dualEngineManager.applyPreferences(
                    subtitleDelayMs = data.subtitleDelayMs,
                    subtitleScale = data.subtitleScale,
                    playbackSpeed = data.playbackSpeed,
                    isLoopEnabled = data.isLoopEnabled,
                    decoderMode = "mediacodec",
                    isSongDetectionEnabled = true,
                    playerEngineMode = DualEngineManager.ENGINE_AUTO,
                    isLoaded = data.isLoaded
                )

                if (data.isLoaded) {
                    hdrDisplayController.applySavedSdrBrightnessIfNeeded(data.savedBrightness)
                    uiState = uiState.copy(
                        hasPrevious = viewModel.hasPrevious(),
                        hasNext = viewModel.hasNext()
                    )
                }

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

    private fun updateProgress() {
        val currentPos = currentPosition
        val dur = duration
        val playingNow = isPlaying

        if (playingNow != uiState.isPlaying) {
            uiState = uiState.copy(isPlaying = playingNow)
            updatePipParams(playingOverride = playingNow)
        }

        if (dur > 0 && !isUserScrubbing) {
            val currentSec = currentPos / 1000L
            val isTimelineVisible = !isPipMode &&
                ((isControlsVisible && !isScreenLocked) ||
                    (isScreenLocked && uiState.isUnlockPromptVisible))
            if ((isTimelineVisible && currentPos != timelineState.positionMs) ||
                currentSec != lastDisplayedSec ||
                dur != timelineState.durationMs
            ) {
                lastDisplayedSec = currentSec
                timelineState = PlayerTimelineState(
                    positionMs = currentPos,
                    durationMs = dur
                )
            }
        }

        val now = SystemClock.elapsedRealtime()
        if (playingNow && currentPos > 0 && dur > 0) {
            if (now - lastProgressSaveTimestamp >= 1000L) {
                lastProgressSaveTimestamp = now
                viewModel.saveProgress(currentPos, dur)
            }
        }

        inactivityMonitor.onPlaybackTick(now, playingNow)
    }

    private fun switchVideo(episode: Episode) {
        isSwitchingEpisode = true

        dualEngineManager.pauseAll()

        val isNextHdr = hdrDisplayController.isLikelyHdrOrCamera(Uri.parse(episode.uri), episode.title)
        if (!isNextHdr) {
            hdrDisplayController.applyHdrState(false, "SDR")
        }

        handler.removeCallbacks(hideFeedbackRunnable)
        handler.removeCallbacks(resetDoubleTapRunnable)
        doubleTapActive = false
        accumulatedDoubleTapSec = 0

        lastDisplayedSec = -1L
        lastProgressSaveTimestamp = 0L
        inactivityMonitor.resetClock()

        val pos = currentPosition
        val dur = duration
        if (pos > 0 && dur > 0) {
            viewModel.saveProgress(pos, dur)
        }

        videoUri = Uri.parse(episode.uri)
        videoTitle = episode.title
        PlaylistHolder.lastPlayedUri = episode.uri
        PlaylistHolder.shouldScrollToLastPlayed = true

        timelineState = PlayerTimelineState(positionMs = 0L, durationMs = 0L)
        uiState = uiState.copy(
            videoTitle = episode.title,
            isLoading = true,
            gestureFeedback = null,
            hasPrevious = viewModel.hasPrevious(),
            hasNext = viewModel.hasNext()
        )

        viewModel.switchToEpisode(episode) { startPos, externalSubs ->
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

        uiState = uiState.copy(
            hasPrevious = viewModel.hasPrevious(),
            hasNext = viewModel.hasNext()
        )
        handler.removeCallbacks(resetSwitchingRunnable)
        handler.postDelayed(resetSwitchingRunnable, 1500L)
    }

    private fun handlePlaybackEnded() {
        if (isSwitchingEpisode || inactivityMonitor.isPromptShowing) return
        val currentPos = currentPosition
        val totalDuration = duration
        if (totalDuration > 0 && currentPos < totalDuration - 3000L) {
            return
        }
        if (!isLoopingEnabled) {
            val next = viewModel.getNextEpisode()
            if (next != null) {
                if (inactivityMonitor.hasReachedThreshold()) {
                    inactivityMonitor.showPrompt(next)
                } else {
                    switchVideo(next)
                }
            } else {
                Toast.makeText(this, "Semua video telah selesai diputar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun exitAppOnInactivity() {
        dualEngineManager.pause()
        val pos = currentPosition
        val dur = duration
        if (pos > 0 && dur > 0) {
            viewModel.saveProgress(pos, dur)
        }

        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finishAffinity()
    }

    // ================= GestureListener Implementation =================

    override fun onSingleTap() {
        if (screenLockController.onSingleTap() == LockAction.REVEAL_UNLOCK_ONLY) {
            showUnlockButtonTemporarily()
            return
        }
        toggleControlsVisibility()
    }

    override fun onDoubleTapSeek(isForward: Boolean) {
        val delta = if (isForward) 10_000L else -10_000L
        doSeekBy(delta)

        handler.removeCallbacks(resetDoubleTapRunnable)
        if (doubleTapActive && lastDoubleTapForward != isForward) {
            accumulatedDoubleTapSec = 0
        }
        doubleTapActive = true
        lastDoubleTapForward = isForward
        accumulatedDoubleTapSec += 10
        doubleTapToken++

        uiState = uiState.copy(
            doubleTapState = DoubleTapOverlayState(
                isForward = isForward,
                accumulatedSeconds = accumulatedDoubleTapSec,
                tapToken = doubleTapToken
            )
        )
        handler.postDelayed(resetDoubleTapRunnable, 650L)

        if (isControlsVisible) {
            scheduleHideControls()
        }
    }

    override fun onSeekGesture(deltaMs: Long, targetMs: Long) {
        if (!isUserScrubbing) {
            wasPlayingBeforeScrub = isPlaying
            if (wasPlayingBeforeScrub) {
                dualEngineManager.pause()
            }
            isUserScrubbing = true
        }
        val sign = if (deltaMs >= 0) "+" else ""
        showGestureFeedback(
            iconRes = if (deltaMs >= 0) R.drawable.ic_forward_10 else R.drawable.ic_replay_10,
            text = "$sign${deltaMs / 1000}s (${com.personal.animeplayer.util.FileUtils.formatDuration(targetMs)})"
        )
        timelineState = timelineState.copy(positionMs = targetMs)
        performLiveScrub(targetMs)
    }

    override fun onSeekConfirmed(targetMs: Long) {
        isUserScrubbing = false
        confirmLiveScrub(targetMs)
        if (wasPlayingBeforeScrub) {
            dualEngineManager.play()
        }
        scheduleHideControls()
    }

    override fun onSeekCancelled(originalMs: Long) {
        isUserScrubbing = false
        handler.removeCallbacks(scrubRunnable)
        pendingScrubPos = null
        doSeekTo(originalMs, fast = false)
        if (wasPlayingBeforeScrub) {
            dualEngineManager.play()
        }
        scheduleHideControls()
    }

    override fun onVolumeChanged(percent: Int) {
        val iconRes = if (percent == 0) R.drawable.ic_volume_off else R.drawable.ic_volume
        showGestureFeedback(iconRes = iconRes, text = "$percent%")
    }

    override fun onBrightnessChanged(percent: Int) {
        showGestureFeedback(iconRes = R.drawable.ic_brightness, text = "$percent%")
    }

    override fun onBrightnessConfirmed(brightness: Float) {
        if (!hdrDisplayController.isHdrActive) {
            viewModel.saveBrightness(brightness)
        }
    }

    private fun scheduleHideSpeedOverlay() {
        handler.removeCallbacks(hideSpeedOverlayRunnable)
        handler.postDelayed(hideSpeedOverlayRunnable, 2000L)
    }

    override fun onSpeedPressStart() {
        normalPlaybackSpeed = viewModel.playerData.value.playbackSpeed
        wasPlayingBeforeSpeedPress = isPlaying

        currentSpeedIndex = 3 // 2.0x
        dualEngineManager.setPlaybackSpeed(2.0f)
        if (!wasPlayingBeforeSpeedPress) {
            dualEngineManager.play()
        }

        hideControls()
        uiState = uiState.copy(
            isSpeedOverlayVisible = true,
            speedOverlayIndex = currentSpeedIndex
        )
        scheduleHideSpeedOverlay()
    }

    override fun onSpeedPressMove(deltaX: Float) {
        val speeds = SpeedOverlayDefaults.speeds
        val stepPx = resources.displayMetrics.density * 22f
        val stepDelta = (deltaX / stepPx).toInt()
        val targetIndex = (3 + stepDelta).coerceIn(0, speeds.size - 1)

        if (targetIndex != currentSpeedIndex) {
            currentSpeedIndex = targetIndex
            dualEngineManager.setPlaybackSpeed(speeds[targetIndex])
            uiState = uiState.copy(
                isSpeedOverlayVisible = true,
                speedOverlayIndex = targetIndex
            )
            scheduleHideSpeedOverlay()
        }
    }

    override fun onSpeedPressEnd() {
        handler.removeCallbacks(hideSpeedOverlayRunnable)
        dualEngineManager.setPlaybackSpeed(normalPlaybackSpeed)

        if (!wasPlayingBeforeSpeedPress) {
            dualEngineManager.pause()
        }

        uiState = uiState.copy(isSpeedOverlayVisible = false)
    }

    private fun showGestureFeedback(iconRes: Int, text: String) {
        handler.removeCallbacks(hideFeedbackRunnable)
        gestureFeedbackToken++
        uiState = uiState.copy(
            gestureFeedback = GestureFeedbackState(
                iconRes = iconRes,
                text = text,
                token = gestureFeedbackToken
            )
        )
        handler.postDelayed(hideFeedbackRunnable, 1200L)
    }

    private fun toggleControlsVisibility() {
        if (screenLockController.onToggleControls() == LockAction.REVEAL_UNLOCK_ONLY) {
            showUnlockButtonTemporarily()
            return
        }
        if (isControlsVisible) hideControls() else showControls()
    }

    private fun showControls() {
        if (!screenLockController.canShowControls()) return
        isControlsVisible = true
        uiState = uiState.copy(isControlsVisible = true)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.statusBars())
        scheduleHideControls()
        handler.removeCallbacks(updateProgressRunnable)
        handler.post(updateProgressRunnable)
    }

    private fun hideControls() {
        isControlsVisible = false
        uiState = uiState.copy(isControlsVisible = false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun scheduleHideControls() {
        handler.removeCallbacks(hideControlsRunnable)
        if (isPlaying) {
            handler.postDelayed(hideControlsRunnable, 3500L)
        }
    }

    private fun showUnlockButtonTemporarily() {
        if (!isScreenLocked) return
        handler.removeCallbacks(hideUnlockButtonRunnable)
        uiState = uiState.copy(isUnlockPromptVisible = true)
        handler.postDelayed(hideUnlockButtonRunnable, 2500L)
    }

    private fun lockScreen() {
        screenLockController.lock()
        gestureManager.isLocked = true
        val currentOrientation = resources.configuration.orientation
        requestedOrientation = if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        hideControls()
        uiState = uiState.copy(
            isScreenLocked = true,
            isUnlockPromptVisible = true
        )
        showUnlockButtonTemporarily()
    }

    private fun unlockScreen() {
        screenLockController.unlock()
        gestureManager.isLocked = false
        handler.removeCallbacks(hideUnlockButtonRunnable)
        val currentOrientation = resources.configuration.orientation
        requestedOrientation = if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        uiState = uiState.copy(
            isScreenLocked = false,
            isUnlockPromptVisible = false
        )
        showControls()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        val preserveDialog = uiState.activeSheet is PlayerSheetState.DeleteConfirm ||
            uiState.activeSheet is PlayerSheetState.PlaybackError

        uiState = uiState.copy(
            isLandscape = isLandscape,
            activeSheet = if (preserveDialog) uiState.activeSheet else PlayerSheetState.None
        )
        setupFullscreen()

        if (::dualEngineManager.isInitialized &&
            dualEngineManager.currentAspectRatioMode == AspectRatioMode.STRETCH
        ) {
            gestureOverlayView.post {
                if (!isFinishing && !isDestroyed && ::dualEngineManager.isInitialized) {
                    dualEngineManager.setAspectRatio(AspectRatioMode.STRETCH)
                }
            }
        }
    }

    private fun registerPipReceiverIfNeeded() {
        if (!isPipReceiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                pipReceiver,
                IntentFilter(ACTION_PIP_CONTROL),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isPipReceiverRegistered = true
        }
    }

    private fun unregisterPipReceiverIfNeeded() {
        if (isPipReceiverRegistered) {
            try {
                unregisterReceiver(pipReceiver)
            } catch (_: IllegalArgumentException) {
            }
            isPipReceiverRegistered = false
        }
    }

    private fun resolvePipAspectRatio(): Rational {
        val videoSize = if (::dualEngineManager.isInitialized) {
            dualEngineManager.getVideoSize()
        } else {
            null
        }
        if (videoSize != null) {
            val (w, h) = videoSize
            if (w > 0 && h > 0) {
                val clamped = (w.toFloat() / h.toFloat()).coerceIn(0.42f, 2.38f)
                return Rational((clamped * 1000f).toInt().coerceAtLeast(420), 1000)
            }
        }
        return Rational(16, 9)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipRemoteAction(
        iconRes: Int,
        title: String,
        controlType: Int
    ): RemoteAction {
        val intent = Intent(ACTION_PIP_CONTROL)
            .setPackage(packageName)
            .putExtra(EXTRA_PIP_CONTROL_TYPE, controlType)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(this, controlType, intent, flags)
        return RemoteAction(
            Icon.createWithResource(this, iconRes),
            title,
            title,
            pendingIntent
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipParams(playingOverride: Boolean? = null): PictureInPictureParams {
        val playing = playingOverride ?: isPlaying
        val actions = buildList {
            add(buildPipRemoteAction(R.drawable.ic_replay_10, "Mundur 10d", PIP_CONTROL_REWIND))
            if (playing) {
                add(buildPipRemoteAction(R.drawable.ic_pause, "Jeda", PIP_CONTROL_PAUSE))
            } else {
                add(buildPipRemoteAction(R.drawable.ic_play_arrow, "Putar", PIP_CONTROL_PLAY))
            }
            add(buildPipRemoteAction(R.drawable.ic_forward_10, "Maju 10d", PIP_CONTROL_FORWARD))
            if (maxNumPictureInPictureActions >= 4 && viewModel.hasNext()) {
                add(buildPipRemoteAction(R.drawable.ic_skip_next, "Berikutnya", PIP_CONTROL_NEXT))
            }
        }.take(maxNumPictureInPictureActions.coerceAtLeast(1))

        val paramsBuilder = PictureInPictureParams.Builder()
            .setAspectRatio(resolvePipAspectRatio())
            .setActions(actions)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            paramsBuilder.setAutoEnterEnabled(false)
            paramsBuilder.setSeamlessResizeEnabled(true)
        }
        return paramsBuilder.build()
    }

    private fun updatePipParams(playingOverride: Boolean? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isFinishing && !isDestroyed) {
            try {
                setPictureInPictureParams(buildPipParams(playingOverride))
            } catch (e: Exception) {
                Log.w("PlayerActivity", "Gagal memperbarui PictureInPictureParams: ${e.message}")
            }
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                registerPipReceiverIfNeeded()
                enterPictureInPictureMode(buildPipParams())
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal membuka mode Picture in Picture", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Picture in Picture memerlukan Android 8.0+", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPipMode = isInPictureInPictureMode
        uiState = uiState.copy(isPipMode = isInPictureInPictureMode)
        if (isInPictureInPictureMode) {
            registerPipReceiverIfNeeded()
            hideControls()
            updatePipParams()
        } else {
            unregisterPipReceiverIfNeeded()
        }
    }

    private fun executeDeleteCurrentVideo() {
        lifecycleScope.launch {
            dualEngineManager.pauseAll()
            when (val res = viewModel.deleteCurrentVideo()) {
                is FileOpResult.Success -> handlePostDelete()
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

    override fun onResume() {
        super.onResume()
        isPipMode = false
        uiState = uiState.copy(isPipMode = false)
        updatePipParams()
        handler.post(updateProgressRunnable)
    }

    override fun finish() {
        if (::dualEngineManager.isInitialized && dualEngineManager.hasInitializedEngine) {
            val pos = currentPosition
            val dur = duration
            if (pos > 0 && dur > 0) {
                viewModel.saveProgress(pos, dur)
            }
        }
        if (::dualEngineManager.isInitialized) {
            dualEngineManager.pauseAll(stopPlayback = true)
        }
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

    override fun onPause() {
        super.onPause()
        dismissActiveSheet(restoreBars = false)
        if (::inactivityMonitor.isInitialized) {
            inactivityMonitor.cancelCountdown()
        }
        handler.removeCallbacks(hideSpeedOverlayRunnable)
        uiState = uiState.copy(isSpeedOverlayVisible = false)

        if (::dualEngineManager.isInitialized && dualEngineManager.hasInitializedEngine) {
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
        if (isFinishing && ::dualEngineManager.isInitialized) {
            dualEngineManager.pauseAll(stopPlayback = true)
        }
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(updateProgressRunnable)
        val wasInPip = isPipMode || isInPictureInPictureMode
        if (wasInPip) {
            isPipMode = false
            uiState = uiState.copy(isPipMode = false)
            finish()
        }
        if (!isPipMode && !isInPictureInPictureMode && ::dualEngineManager.isInitialized) {
            dualEngineManager.pauseAll(stopPlayback = isFinishing)
        }
    }

    override fun onDestroy() {
        unregisterPipReceiverIfNeeded()
        handler.removeCallbacks(hideUnlockButtonRunnable)
        handler.removeCallbacksAndMessages(null)
        if (::inactivityMonitor.isInitialized) {
            inactivityMonitor.release()
        }

        if (::dualEngineManager.isInitialized && dualEngineManager.hasInitializedEngine) {
            val pos = currentPosition
            val dur = duration
            if (pos > 0 && dur > 0) {
                viewModel.saveProgress(pos, dur)
            }
        }
        if (::hdrDisplayController.isInitialized) {
            hdrDisplayController.getBrightnessToPersist()?.let { brightnessToSave ->
                viewModel.saveBrightness(brightnessToSave)
            }
            hdrDisplayController.resetOnDestroy()
        }

        if (::gestureManager.isInitialized) {
            gestureManager.release()
        }
        if (::dualEngineManager.isInitialized) {
            dualEngineManager.release()
        }
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (screenLockController.shouldConsumeBackKeyEvent() && event.keyCode == KeyEvent.KEYCODE_BACK) {
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

        private const val ACTION_PIP_CONTROL = "com.personal.animeplayer.ACTION_PIP_CONTROL"
        private const val EXTRA_PIP_CONTROL_TYPE = "extra_pip_control_type"
        private const val PIP_CONTROL_REWIND = 1
        private const val PIP_CONTROL_PLAY = 2
        private const val PIP_CONTROL_PAUSE = 3
        private const val PIP_CONTROL_FORWARD = 4
        private const val PIP_CONTROL_NEXT = 5

        internal val REGEX_HDR_KEYWORDS: Regex
            get() = HdrDisplayController.REGEX_HDR_KEYWORDS

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
