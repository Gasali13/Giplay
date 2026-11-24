package com.personal.animeplayer.ui.player

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.personal.animeplayer.R
import com.personal.animeplayer.data.model.AudioTrackInfo
import com.personal.animeplayer.data.model.SubtitleTrackInfo
import com.personal.animeplayer.player.AspectRatioMode
import com.personal.animeplayer.player.DualEngineManager
import com.personal.animeplayer.player.MPVView
import com.personal.animeplayer.ui.compose.components.LiquidGlassCircleButton
import com.personal.animeplayer.ui.compose.components.LiquidGlassCircleCombinedButton
import com.personal.animeplayer.ui.compose.components.LiquidGlassStyle
import com.personal.animeplayer.ui.compose.components.VideoBackdropFrame
import com.personal.animeplayer.ui.compose.components.liquidGlassBitmapBackdrop
import com.personal.animeplayer.ui.compose.components.rememberSurfaceVideoBackdrop
import com.personal.animeplayer.ui.compose.player.DoubleTapOverlayState
import com.personal.animeplayer.ui.compose.player.DoubleTapSeekOverlay
import com.personal.animeplayer.ui.compose.player.GestureFeedbackPill
import com.personal.animeplayer.ui.compose.player.GestureFeedbackState
import com.personal.animeplayer.ui.compose.player.InactivityPromptOverlay
import com.personal.animeplayer.ui.compose.player.PlayerGlowingSeekBar
import com.personal.animeplayer.ui.compose.player.PlayerSheetState
import com.personal.animeplayer.ui.compose.player.PlayerSheetsHost
import com.personal.animeplayer.ui.compose.player.SpeedControlOverlay
import com.personal.animeplayer.ui.compose.theme.AniPlayColors
import com.personal.animeplayer.ui.compose.theme.IosMotion
import com.personal.animeplayer.ui.compose.theme.rememberIosPressScale
import com.personal.animeplayer.util.FileUtils
import kotlin.math.roundToInt

@Immutable
data class PlayerTimelineState(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

@Immutable
data class PlayerScreenUiState(
    val videoTitle: String = "",
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val isControlsVisible: Boolean = true,
    val isScreenLocked: Boolean = false,
    val isUnlockPromptVisible: Boolean = false,
    val unlockShakeToken: Int = 0,
    val isPipMode: Boolean = false,
    val isLandscape: Boolean = false,
    val isHdrActive: Boolean = false,
    val hdrType: String = "",
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val currentAspectRatio: AspectRatioMode = AspectRatioMode.FIT,
    val doubleTapState: DoubleTapOverlayState = DoubleTapOverlayState(),
    val gestureFeedback: GestureFeedbackState? = null,
    val isSpeedOverlayVisible: Boolean = false,
    val speedOverlayIndex: Int = 3,
    val isInactivityPromptVisible: Boolean = false,
    val inactivitySecondsLeft: Int = 30,
    val activeSheet: PlayerSheetState = PlayerSheetState.None,
    val subtitleTracks: List<SubtitleTrackInfo> = emptyList(),
    val audioTracks: List<AudioTrackInfo> = emptyList(),
    val activeEngine: String = DualEngineManager.ENGINE_MPV
)

@Composable
fun PlayerScreen(
    uiState: PlayerScreenUiState,
    timelineStateProvider: () -> PlayerTimelineState,
    playerData: PlayerUiData,
    mpvView: MPVView,
    exoPlayerView: PlayerView,
    gestureOverlayView: View,
    onBackClick: () -> Unit,
    onMoreOptionsClick: () -> Unit,
    onLockClick: () -> Unit,
    onUnlockClick: () -> Unit,
    onSkipOpEdClick: () -> Unit,
    onOrientationToggleClick: () -> Unit,
    onRepeatToggleClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onAspectRatioClick: () -> Unit,
    onAspectRatioLongClick: () -> Unit,
    onScrubStart: () -> Unit,
    onScrubChange: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    onContinueWatchingClick: () -> Unit,
    onDismissSheet: () -> Unit,
    onNavigateSheet: (PlayerSheetState) -> Unit,
    onSelectAspectRatio: (AspectRatioMode) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onSelectAudioTrack: (Int) -> Unit,
    onSelectSubtitleTrack: (Int) -> Unit,
    onPickExternalSubtitle: () -> Unit,
    onSubtitleDelayChange: (Long) -> Unit,
    onSubtitleScaleLiveChange: (Int) -> Unit,
    onSubtitleScaleCommit: (Int) -> Unit,
    onToggleSkipOpEd: (Boolean) -> Unit,
    onEnterPipMode: () -> Unit,
    onConfirmDeleteVideo: () -> Unit,
    onErrorDismissed: () -> Unit
) {
    val view = LocalView.current
    val layoutDirection = LocalLayoutDirection.current

    val statusTopRaw = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navInsets = WindowInsets.navigationBars.asPaddingValues()
    val cutoutInsets = WindowInsets.displayCutout.asPaddingValues()

    var cachedStatusTop by remember(uiState.isLandscape) { mutableStateOf(0.dp) }
    var cachedNavBottom by remember(uiState.isLandscape) { mutableStateOf(0.dp) }
    if (statusTopRaw > 0.dp) cachedStatusTop = statusTopRaw
    val navBottomRaw = navInsets.calculateBottomPadding()
    if (navBottomRaw > 0.dp) cachedNavBottom = navBottomRaw

    val effectiveStatusTop = maxOf(statusTopRaw, cachedStatusTop)
    val effectiveNavBottom = maxOf(navBottomRaw, cachedNavBottom)
    val cutoutStart = maxOf(
        navInsets.calculateStartPadding(layoutDirection),
        cutoutInsets.calculateStartPadding(layoutDirection)
    )
    val cutoutEnd = maxOf(
        navInsets.calculateEndPadding(layoutDirection),
        cutoutInsets.calculateEndPadding(layoutDirection)
    )
    val landscapeSideInset = if (uiState.isLandscape) maxOf(cutoutStart, cutoutEnd) else 0.dp
    val controlEdgePadding = 16.dp + landscapeSideInset

    val activeSurfaceView = if (uiState.activeEngine == DualEngineManager.ENGINE_EXOPLAYER) {
        exoPlayerView.videoSurfaceView as? android.view.SurfaceView
    } else {
        mpvView
    }
    val isPanelSheetOpen = uiState.activeSheet != PlayerSheetState.None &&
        uiState.activeSheet !is PlayerSheetState.DeleteConfirm &&
        uiState.activeSheet !is PlayerSheetState.PlaybackError
    val isAnySheetOrDialogOpen = uiState.activeSheet != PlayerSheetState.None &&
        uiState.activeSheet !is PlayerSheetState.PlaybackError
    val isAnyControlVisible = !isPanelSheetOpen &&
        ((uiState.isControlsVisible && !uiState.isScreenLocked) ||
            (uiState.isScreenLocked && uiState.isUnlockPromptVisible))
    val isAnyOverlayVisible = uiState.gestureFeedback != null ||
        uiState.isSpeedOverlayVisible ||
        uiState.isInactivityPromptVisible
    val videoBackdropProvider = rememberSurfaceVideoBackdrop(
        activeSurfaceView = activeSurfaceView,
        isActive = !uiState.isPipMode &&
            (isAnyControlVisible || isAnySheetOrDialogOpen || isAnyOverlayVisible)
    )

    val unlockShakeOffsetX = remember { Animatable(0f) }
    LaunchedEffect(uiState.unlockShakeToken) {
        if (uiState.unlockShakeToken > 0) {
            unlockShakeOffsetX.snapTo(0f)
            unlockShakeOffsetX.animateTo(9f, tween(70, easing = IosMotion.EaseInOut))
            unlockShakeOffsetX.animateTo(-7f, tween(90, easing = IosMotion.EaseInOut))
            unlockShakeOffsetX.animateTo(4f, tween(90, easing = IosMotion.EaseInOut))
            unlockShakeOffsetX.animateTo(0f, IosMotion.smoothFloat)
        }
    }

    val playPauseInteractionSource = remember { MutableInteractionSource() }
    val playPauseScale by rememberIosPressScale(
        interactionSource = playPauseInteractionSource,
        pressedScale = 0.96f,
        label = "playPauseScale"
    )

    val skipInteractionSource = remember { MutableInteractionSource() }
    val isSkipPressed by skipInteractionSource.collectIsPressedAsState()
    val skipScale by rememberIosPressScale(
        interactionSource = skipInteractionSource,
        pressedScale = 0.96f,
        label = "skipScale"
    )
    val skipPressProgress by animateFloatAsState(
        targetValue = if (isSkipPressed) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = 200, easing = IosMotion.EaseOut),
        label = "skipPressProgress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. MPV Surface
        AndroidView(
            factory = { mpvView },
            modifier = Modifier.fillMaxSize()
        )

        // 2. ExoPlayer Surface
        AndroidView(
            factory = { exoPlayerView },
            modifier = Modifier.fillMaxSize()
        )

        // 3. Fullscreen Touch Gesture Overlay
        AndroidView(
            factory = { gestureOverlayView },
            update = { viewRef ->
                viewRef.visibility = if (uiState.isPipMode) View.GONE else View.VISIBLE
            },
            modifier = Modifier.fillMaxSize()
        )

        // 4. Double-Tap Seek Animation Overlay
        DoubleTapSeekOverlay(
            state = uiState.doubleTapState,
            modifier = Modifier.fillMaxSize()
        )

        // 5. Floating Gesture Feedback Pill (Volume, Brightness, Seek)
        GestureFeedbackPill(
            state = uiState.gestureFeedback,
            videoBackdropProvider = videoBackdropProvider,
            modifier = Modifier.align(Alignment.Center)
        )

        // 6. Central Loading Indicator
        if (uiState.isLoading && !uiState.isPipMode) {
            CircularProgressIndicator(
                color = AniPlayColors.Secondary,
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center)
            )
        }

        // 7. Player Controls Overlay
        if (!uiState.isPipMode) {
            // Top Action Bar
            AnimatedVisibility(
                visible = !isPanelSheetOpen && uiState.isControlsVisible && !uiState.isScreenLocked,
                enter = slideInVertically(IosMotion.controlsEnterOffset) { -it } +
                    fadeIn(IosMotion.fadeIn),
                exit = slideOutVertically(IosMotion.controlsExitOffset) { -it } +
                    fadeOut(IosMotion.fadeOut),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color(0xD9000000),
                                    0.5f to Color(0x66000000),
                                    1.0f to Color.Transparent
                                )
                            )
                        )
                        .padding(
                            start = controlEdgePadding,
                            end = controlEdgePadding,
                            top = effectiveStatusTop + 8.dp,
                            bottom = 28.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassCircleButton(
                        iconRes = R.drawable.ic_arrow_back,
                        contentDescription = stringResource(R.string.action_back),
                        videoBackdropProvider = videoBackdropProvider,
                        onClick = onBackClick
                    )

                    Text(
                        text = uiState.videoTitle,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp, end = 10.dp)
                    )

                    if (uiState.isHdrActive && uiState.hdrType.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .liquidGlassBitmapBackdrop(
                                    frameProvider = videoBackdropProvider,
                                    cornerRadius = 8.dp,
                                    style = LiquidGlassStyle.PlayerControl.copy(
                                        blurRadius = 10.dp,
                                        bezelWidth = 5.dp,
                                        refractionStrength = 4.dp,
                                        chromaticAberration = 0.8.dp,
                                        tintColor = Color(0x520284C7),
                                        highlightStrength = 0.80f
                                    ),
                                    fallbackTintColor = Color(0x470284C7)
                                )
                                .clip(RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = uiState.hdrType,
                                color = AniPlayColors.SkyBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    GlassCircleButton(
                        iconRes = R.drawable.ic_more_vert,
                        contentDescription = stringResource(R.string.cd_player_options),
                        videoBackdropProvider = videoBackdropProvider,
                        onClick = onMoreOptionsClick
                    )
                }
            }

            // Unified Lock / Unlock Floating Button (Right Edge)
            val showLockToggleButton = !isPanelSheetOpen &&
                ((uiState.isControlsVisible && !uiState.isScreenLocked) ||
                    (uiState.isScreenLocked && uiState.isUnlockPromptVisible))

            AnimatedVisibility(
                visible = showLockToggleButton,
                enter = fadeIn(IosMotion.fadeIn),
                exit = fadeOut(IosMotion.fadeOut),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = controlEdgePadding)
                    .offset {
                        IntOffset(
                            if (uiState.isScreenLocked) unlockShakeOffsetX.value.roundToInt() else 0,
                            0
                        )
                    }
            ) {
                GlassCircleButton(
                    iconRes = if (uiState.isScreenLocked) R.drawable.ic_lock else R.drawable.ic_lock_open,
                    contentDescription = stringResource(
                        if (uiState.isScreenLocked) R.string.cd_unlock_screen else R.string.cd_lock_screen
                    ),
                    iconTint = if (uiState.isScreenLocked) AniPlayColors.Secondary else Color.White,
                    iconSize = 21.dp,
                    videoBackdropProvider = videoBackdropProvider,
                    onClick = if (uiState.isScreenLocked) onUnlockClick else onLockClick
                )
            }

            // Bottom Controls Bar (Full controls when unlocked; read-only timeline when locked & revealed)
            val showBottomBar = !isPanelSheetOpen &&
                ((uiState.isControlsVisible && !uiState.isScreenLocked) ||
                    (uiState.isScreenLocked && uiState.isUnlockPromptVisible))

            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(IosMotion.controlsEnterOffset) { it } +
                    fadeIn(IosMotion.fadeIn),
                exit = slideOutVertically(IosMotion.controlsExitOffset) { it } +
                    fadeOut(IosMotion.fadeOut),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val padV = if (uiState.isLandscape) 2.dp else 4.dp
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    0.5f to Color(0x73000000),
                                    1.0f to Color(0xE6000000)
                                )
                            )
                        )
                        .padding(
                            start = controlEdgePadding,
                            end = controlEdgePadding,
                            top = padV,
                            bottom = effectiveNavBottom + padV
                        )
                ) {
                    // Row above timeline: Skip +85s pill & Orientation toggle
                    if (!uiState.isScreenLocked) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (playerData.isSkipOpEdEnabled) {
                                Row(
                                    modifier = Modifier
                                        .height(34.dp)
                                        .graphicsLayer {
                                            scaleX = skipScale
                                            scaleY = skipScale
                                        }
                                        .liquidGlassBitmapBackdrop(
                                            frameProvider = videoBackdropProvider,
                                            cornerRadius = 17.dp,
                                            style = LiquidGlassStyle.PlayerControl.copy(
                                                tintColor = Color(0x540E131D),
                                                highlightStrength = 0.90f
                                            ),
                                            fallbackTintColor = Color(0x580E131D),
                                            pressProgress = skipPressProgress
                                        )
                                        .clip(RoundedCornerShape(17.dp))
                                        .clickable(
                                            interactionSource = skipInteractionSource,
                                            indication = ripple(bounded = true, color = Color(0x52FFFFFF)),
                                            onClick = onSkipOpEdClick
                                        )
                                        .padding(start = 10.dp, end = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_skip_next),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.skip_op_ed_label),
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            GlassCircleButton(
                                iconRes = R.drawable.ic_screen_rotation,
                                contentDescription = stringResource(R.string.rotate_screen),
                                iconSize = 21.dp,
                                videoBackdropProvider = videoBackdropProvider,
                                onClick = onOrientationToggleClick
                            )
                        }
                    }

                    // Timeline Row (isolated state read so 10Hz ticks do not recompose PlayerScreen)
                    PlayerTimelineRow(
                        timelineStateProvider = timelineStateProvider,
                        enabled = !uiState.isScreenLocked,
                        onScrubStart = onScrubStart,
                        onScrubChange = onScrubChange,
                        onScrubEnd = onScrubEnd
                    )

                    // Bottom Playback Controls Row
                    if (!uiState.isScreenLocked) {
                        Box(modifier = Modifier.fillMaxWidth().offset(y = (-4).dp)) {
                            GlassCircleButton(
                                iconRes = R.drawable.ic_repeat,
                                contentDescription = stringResource(R.string.cd_repeat_video),
                                iconTint = if (playerData.isLoopEnabled) {
                                    AniPlayColors.Secondary
                                } else {
                                    Color.White
                                },
                                iconSize = 21.dp,
                                videoBackdropProvider = videoBackdropProvider,
                                onClick = onRepeatToggleClick,
                                modifier = Modifier.align(Alignment.CenterStart)
                            )

                            Row(
                                modifier = Modifier.align(Alignment.Center),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                IconButton(
                                    onClick = onPreviousClick,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .graphicsLayer {
                                            alpha = if (uiState.hasPrevious) 1.0f else 0.4f
                                        }
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_skip_previous),
                                        contentDescription = stringResource(R.string.cd_prev_episode),
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(24.dp))

                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .graphicsLayer {
                                            scaleX = playPauseScale
                                            scaleY = playPauseScale
                                        }
                                        .clip(CircleShape)
                                        .clickable(
                                            interactionSource = playPauseInteractionSource,
                                            indication = ripple(bounded = false, color = Color(0x40FFFFFF)),
                                            onClick = {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                onPlayPauseClick()
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            if (uiState.isPlaying) {
                                                R.drawable.ic_pause
                                            } else {
                                                R.drawable.ic_play_arrow
                                            }
                                        ),
                                        contentDescription = stringResource(R.string.play),
                                        tint = Color.White,
                                        modifier = Modifier.size(44.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(24.dp))

                                IconButton(
                                    onClick = onNextClick,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .graphicsLayer {
                                            alpha = if (uiState.hasNext) 1.0f else 0.4f
                                        }
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_skip_next),
                                        contentDescription = stringResource(R.string.cd_next_episode),
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }

                            GlassCircleCombinedButton(
                                iconRes = R.drawable.ic_aspect_ratio,
                                contentDescription = stringResource(R.string.cd_aspect_ratio),
                                iconSize = 21.dp,
                                videoBackdropProvider = videoBackdropProvider,
                                onClick = onAspectRatioClick,
                                onLongClick = onAspectRatioLongClick,
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        }
                    }
                }
            }
        }

        // 8. Press & Hold Speed Control Overlay
        SpeedControlOverlay(
            visible = uiState.isSpeedOverlayVisible && !uiState.isPipMode,
            speedIndex = uiState.speedOverlayIndex,
            topInsetDp = effectiveStatusTop,
            isPortrait = !uiState.isLandscape,
            videoBackdropProvider = videoBackdropProvider,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // 9. Adaptive Sheets & Dialogs
        PlayerSheetsHost(
            sheetState = uiState.activeSheet,
            isLandscape = uiState.isLandscape,
            videoTitle = uiState.videoTitle,
            playerData = playerData,
            currentAspectRatio = uiState.currentAspectRatio,
            subtitleTracks = uiState.subtitleTracks,
            audioTracks = uiState.audioTracks,
            activeEngine = uiState.activeEngine,
            videoBackdropProvider = videoBackdropProvider,
            onDismiss = onDismissSheet,
            onNavigate = onNavigateSheet,
            onSelectAspectRatio = onSelectAspectRatio,
            onSelectSpeed = onSelectSpeed,
            onSelectAudioTrack = onSelectAudioTrack,
            onSelectSubtitleTrack = onSelectSubtitleTrack,
            onPickExternalSubtitle = onPickExternalSubtitle,
            onSubtitleDelayChange = onSubtitleDelayChange,
            onSubtitleScaleLiveChange = onSubtitleScaleLiveChange,
            onSubtitleScaleCommit = onSubtitleScaleCommit,
            onToggleSkipOpEd = onToggleSkipOpEd,
            onEnterPipMode = onEnterPipMode,
            onConfirmDelete = onConfirmDeleteVideo,
            onErrorDismissed = onErrorDismissed
        )

        // 10. Inactivity / Sleep Detection Prompt Overlay
        InactivityPromptOverlay(
            visible = uiState.isInactivityPromptVisible,
            secondsLeft = uiState.inactivitySecondsLeft,
            onContinueWatching = onContinueWatchingClick,
            videoBackdropProvider = videoBackdropProvider
        )
    }
}

@Composable
private fun PlayerTimelineRow(
    timelineStateProvider: () -> PlayerTimelineState,
    enabled: Boolean,
    onScrubStart: () -> Unit,
    onScrubChange: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit
) {
    val timeline = timelineStateProvider()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (timeline.durationMs > 0L || timeline.positionMs > 0L) {
                FileUtils.formatDuration(timeline.positionMs)
            } else {
                "00:00"
            },
            style = TextStyle(
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = "tnum"
            )
        )

        PlayerGlowingSeekBar(
            positionMs = timeline.positionMs,
            durationMs = timeline.durationMs,
            enabled = enabled,
            onScrubStart = onScrubStart,
            onScrubChange = onScrubChange,
            onScrubEnd = onScrubEnd,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )

        Text(
            text = if (timeline.durationMs > 0L) {
                FileUtils.formatDuration(timeline.durationMs)
            } else {
                "--:--"
            },
            style = TextStyle(
                color = Color(0xFFD1D5DB),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = "tnum"
            )
        )
    }
}

@Composable
private fun GlassCircleButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.White,
    iconSize: Dp = 23.dp,
    videoBackdropProvider: (() -> VideoBackdropFrame?)? = null
) {
    LiquidGlassCircleButton(
        iconRes = iconRes,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        size = 38.dp,
        iconSize = iconSize,
        iconTint = iconTint,
        tintColor = Color(0x540E131D),
        videoBackdropProvider = videoBackdropProvider
    )
}

@Composable
private fun GlassCircleCombinedButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.White,
    iconSize: Dp = 21.dp,
    videoBackdropProvider: (() -> VideoBackdropFrame?)? = null
) {
    LiquidGlassCircleCombinedButton(
        iconRes = iconRes,
        contentDescription = contentDescription,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        size = 38.dp,
        iconSize = iconSize,
        iconTint = iconTint,
        tintColor = Color(0x540E131D),
        videoBackdropProvider = videoBackdropProvider
    )
}
