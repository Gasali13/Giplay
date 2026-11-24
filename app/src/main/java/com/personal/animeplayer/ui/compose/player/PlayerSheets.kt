package com.personal.animeplayer.ui.compose.player

import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.personal.animeplayer.ui.compose.theme.IosMotion
import com.personal.animeplayer.ui.compose.theme.rememberIosPressScale
import com.personal.animeplayer.R
import com.personal.animeplayer.data.model.AudioTrackInfo
import com.personal.animeplayer.data.model.SubtitleTrackInfo
import com.personal.animeplayer.player.AspectRatioMode
import com.personal.animeplayer.player.DualEngineManager
import com.personal.animeplayer.ui.compose.components.DeleteConfirmDialog
import com.personal.animeplayer.ui.compose.components.LiquidGlassCircleButton
import com.personal.animeplayer.ui.compose.components.LiquidGlassStyle
import com.personal.animeplayer.ui.compose.components.VideoBackdropFrame
import com.personal.animeplayer.ui.compose.components.liquidGlassBitmapBackdrop
import com.personal.animeplayer.ui.compose.components.liquidGlassSurface
import com.personal.animeplayer.ui.compose.theme.AniPlayColors
import com.personal.animeplayer.ui.player.PlayerUiData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val PlayerGlassTextShadow = Shadow(
    color = Color(0xAA000000),
    offset = Offset(0f, 1.5f),
    blurRadius = 6f
)

sealed interface PlayerSheetState {
    data object None : PlayerSheetState
    data object PlayerOptions : PlayerSheetState
    data object AspectRatio : PlayerSheetState
    data object PlaybackSpeed : PlayerSheetState
    data object AudioTracks : PlayerSheetState
    data object Subtitles : PlayerSheetState
    data object SubtitleStyle : PlayerSheetState
    data class DeleteConfirm(val videoTitle: String) : PlayerSheetState
    data class PlaybackError(val message: String) : PlayerSheetState
}

private fun PlayerSheetState.isPanel(): Boolean = when (this) {
    PlayerSheetState.PlayerOptions,
    PlayerSheetState.AspectRatio,
    PlayerSheetState.PlaybackSpeed,
    PlayerSheetState.AudioTracks,
    PlayerSheetState.Subtitles,
    PlayerSheetState.SubtitleStyle -> true
    else -> false
}

private fun PlayerSheetState.navDepth(): Int = when (this) {
    PlayerSheetState.PlayerOptions,
    PlayerSheetState.AspectRatio -> 0
    PlayerSheetState.PlaybackSpeed,
    PlayerSheetState.AudioTracks,
    PlayerSheetState.Subtitles -> 1
    PlayerSheetState.SubtitleStyle -> 2
    else -> 0
}

@Composable
fun PlayerSheetsHost(
    sheetState: PlayerSheetState,
    isLandscape: Boolean,
    videoTitle: String,
    playerData: PlayerUiData,
    currentAspectRatio: AspectRatioMode,
    subtitleTracks: List<SubtitleTrackInfo>,
    audioTracks: List<AudioTrackInfo>,
    activeEngine: String,
    videoBackdropProvider: (() -> VideoBackdropFrame?)? = null,
    onDismiss: () -> Unit,
    onNavigate: (PlayerSheetState) -> Unit,
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
    onConfirmDelete: () -> Unit,
    onErrorDismissed: () -> Unit
) {
    BackHandler(
        enabled = sheetState != PlayerSheetState.None && sheetState !is PlayerSheetState.PlaybackError
    ) {
        when (sheetState) {
            PlayerSheetState.SubtitleStyle -> onNavigate(PlayerSheetState.Subtitles)
            PlayerSheetState.Subtitles,
            PlayerSheetState.AudioTracks,
            PlayerSheetState.PlaybackSpeed -> onNavigate(PlayerSheetState.PlayerOptions)
            else -> onDismiss()
        }
    }

    val activePanel = sheetState.takeIf { it.isPanel() }
    val isPanelOpen = activePanel != null
    var lastPanel by remember { mutableStateOf<PlayerSheetState?>(null) }
    LaunchedEffect(activePanel) {
        if (activePanel != null) {
            lastPanel = activePanel
        }
    }
    val displayPanel = activePanel ?: lastPanel

    var isSliderDragging by remember { mutableStateOf(false) }
    LaunchedEffect(activePanel) {
        isSliderDragging = false
    }

    val sheetAlpha by animateFloatAsState(
        targetValue = if (activePanel is PlayerSheetState.SubtitleStyle && isSliderDragging) {
            0.65f
        } else {
            1.0f
        },
        animationSpec = tween(durationMillis = 200, easing = IosMotion.EaseOut),
        label = "playerSheetAlpha"
    )

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dismissThresholdPx = with(density) { 90.dp.toPx() }
    val dragOffset = remember { Animatable(0f) }
    LaunchedEffect(isPanelOpen, isLandscape) {
        if (isPanelOpen) {
            dragOffset.snapTo(0f)
        }
    }

    // Adaptive Real-Time Video-Blurred Liquid Glass Panel (Floating Bottom Sheet in Portrait, Side Sheet in Landscape)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val layoutDirection = LocalLayoutDirection.current
        val navInsets = WindowInsets.navigationBars.asPaddingValues()
        val cutoutInsets = WindowInsets.displayCutout.asPaddingValues()
        val endSafePad = maxOf(
            navInsets.calculateEndPadding(layoutDirection),
            cutoutInsets.calculateEndPadding(layoutDirection)
        )
        val maxPortraitHeight = maxHeight * 0.80f

        AnimatedVisibility(
            visible = isPanelOpen,
            enter = fadeIn(animationSpec = IosMotion.scrimIn),
            exit = fadeOut(animationSpec = IosMotion.scrimOut),
            modifier = Modifier.fillMaxSize()
        ) {
            val scrimAlpha by animateFloatAsState(
                targetValue = if (displayPanel is PlayerSheetState.SubtitleStyle) 0f else 0.30f,
                animationSpec = IosMotion.fadeIn,
                label = "playerScrimAlpha"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        AnimatedVisibility(
            visible = isPanelOpen && displayPanel != null,
            enter = if (isLandscape) {
                slideInHorizontally(
                    animationSpec = IosMotion.sheetEnterOffset
                ) { it } +
                    scaleIn(
                        initialScale = 0.97f,
                        transformOrigin = TransformOrigin(1f, 0.5f),
                        animationSpec = IosMotion.sheetEnterScale
                    ) +
                    fadeIn(IosMotion.fadeIn)
            } else {
                slideInVertically(
                    animationSpec = IosMotion.sheetEnterOffset
                ) { it } +
                    scaleIn(
                        initialScale = 0.97f,
                        transformOrigin = TransformOrigin(0.5f, 1f),
                        animationSpec = IosMotion.sheetEnterScale
                    ) +
                    fadeIn(IosMotion.fadeIn)
            },
            exit = if (isLandscape) {
                slideOutHorizontally(
                    animationSpec = IosMotion.sheetExitOffset
                ) { it } +
                    fadeOut(IosMotion.fadeOut)
            } else {
                slideOutVertically(
                    animationSpec = IosMotion.sheetExitOffset
                ) { it } +
                    fadeOut(IosMotion.fadeOut)
            },
            modifier = Modifier.align(
                if (isLandscape) Alignment.CenterEnd else Alignment.BottomCenter
            )
        ) {
            val targetPanel = displayPanel ?: return@AnimatedVisibility
            val shape = RoundedCornerShape(26.dp)
            val panelModifier = if (isLandscape) {
                Modifier
                    .padding(top = 10.dp, bottom = 10.dp, end = 10.dp + endSafePad)
                    .width(minOf(352.dp, (maxWidth - 20.dp - endSafePad).coerceAtLeast(220.dp)))
                    .fillMaxHeight()
            } else {
                Modifier
                    .padding(horizontal = 14.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxPortraitHeight)
            }

            val landscapeSwipeModifier = if (isLandscape && targetPanel !is PlayerSheetState.SubtitleStyle) {
                Modifier.pointerInput(isLandscape, targetPanel) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, dragAmount ->
                            val next = (dragOffset.value + dragAmount).coerceAtLeast(0f)
                            scope.launch { dragOffset.snapTo(next) }
                        },
                        onDragEnd = {
                            if (dragOffset.value > dismissThresholdPx) {
                                onDismiss()
                            } else {
                                scope.launch {
                                    dragOffset.animateTo(
                                        0f,
                                        IosMotion.dragSettle
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                dragOffset.animateTo(
                                    0f,
                                    IosMotion.dragSettle
                                )
                            }
                        }
                    )
                }
            } else {
                Modifier
            }

            Column(
                modifier = panelModifier
                    .offset {
                        if (isLandscape) {
                            IntOffset(dragOffset.value.roundToInt(), 0)
                        } else {
                            IntOffset(0, dragOffset.value.roundToInt())
                        }
                    }
                    .then(landscapeSwipeModifier)
                    .graphicsLayer { alpha = sheetAlpha }
                    .liquidGlassBitmapBackdrop(
                        frameProvider = videoBackdropProvider ?: { null },
                        cornerRadius = 26.dp,
                        style = LiquidGlassStyle.Sheet,
                        fallbackTintColor = Color(0xBD101520)
                    )
                    .clip(shape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                if (!isLandscape) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onVerticalDrag = { _, dragAmount ->
                                        val next = (dragOffset.value + dragAmount).coerceAtLeast(0f)
                                        scope.launch { dragOffset.snapTo(next) }
                                    },
                                    onDragEnd = {
                                        if (dragOffset.value > dismissThresholdPx) {
                                            onDismiss()
                                        } else {
                                            scope.launch {
                                                dragOffset.animateTo(
                                                    0f,
                                                    IosMotion.dragSettle
                                                )
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        scope.launch {
                                            dragOffset.animateTo(
                                                0f,
                                                IosMotion.dragSettle
                                            )
                                        }
                                    }
                                )
                            }
                            .padding(top = 10.dp, bottom = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .height(4.5.dp)
                                .clip(CircleShape)
                                .background(Color(0x4DFFFFFF))
                        )
                    }
                }

                val contentModifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)

                AnimatedContent(
                    targetState = targetPanel,
                    transitionSpec = {
                        val isForward = targetState.navDepth() >= initialState.navDepth()
                        val enterAnim = if (isForward) {
                            slideInHorizontally(
                                animationSpec = IosMotion.navEnterOffset
                            ) { (it * 0.30f).roundToInt().coerceAtLeast(1) } + fadeIn(IosMotion.navFadeIn)
                        } else {
                            slideInHorizontally(
                                animationSpec = IosMotion.navEnterOffset
                            ) { -(it * 0.20f).roundToInt().coerceAtLeast(1) } + fadeIn(IosMotion.navFadeIn)
                        }
                        val exitAnim = if (isForward) {
                            slideOutHorizontally(
                                animationSpec = IosMotion.navExitOffset
                            ) { -(it * 0.20f).roundToInt().coerceAtLeast(1) } + fadeOut(IosMotion.navFadeOut)
                        } else {
                            slideOutHorizontally(
                                animationSpec = IosMotion.navExitOffset
                            ) { (it * 0.30f).roundToInt().coerceAtLeast(1) } + fadeOut(IosMotion.navFadeOut)
                        }
                        (enterAnim togetherWith exitAnim).using(
                            SizeTransform(
                                clip = false,
                                sizeAnimationSpec = { _, _ -> IosMotion.sizeChange }
                            )
                        )
                    },
                    contentAlignment = Alignment.TopCenter,
                    label = "playerSheetNavigation",
                    modifier = contentModifier
                ) { panel ->
                    when (panel) {
                        PlayerSheetState.PlayerOptions -> PlayerOptionsContent(
                            isLandscape = isLandscape,
                            playerData = playerData,
                            onOpenSpeed = { onNavigate(PlayerSheetState.PlaybackSpeed) },
                            onOpenAudio = { onNavigate(PlayerSheetState.AudioTracks) },
                            onOpenSubtitles = { onNavigate(PlayerSheetState.Subtitles) },
                            onEnterPip = {
                                onDismiss()
                                onEnterPipMode()
                            },
                            onToggleSkipOpEd = { onToggleSkipOpEd(!playerData.isSkipOpEdEnabled) },
                            onRequestDelete = { onNavigate(PlayerSheetState.DeleteConfirm(videoTitle)) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        PlayerSheetState.AspectRatio -> AspectRatioContent(
                            isLandscape = isLandscape,
                            currentAspectRatio = currentAspectRatio,
                            onBack = onDismiss,
                            onSelectAspectRatio = { mode ->
                                onSelectAspectRatio(mode)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        PlayerSheetState.PlaybackSpeed -> PlaybackSpeedContent(
                            isLandscape = isLandscape,
                            currentSpeed = playerData.playbackSpeed,
                            onBack = { onNavigate(PlayerSheetState.PlayerOptions) },
                            onSelectSpeed = { speed ->
                                onSelectSpeed(speed)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        PlayerSheetState.Subtitles -> SubtitlesContent(
                            isLandscape = isLandscape,
                            tracks = subtitleTracks,
                            onBack = { onNavigate(PlayerSheetState.PlayerOptions) },
                            onPickExternalSubtitle = {
                                onDismiss()
                                onPickExternalSubtitle()
                            },
                            onOpenSubtitleStyle = { onNavigate(PlayerSheetState.SubtitleStyle) },
                            onSelectTrack = { trackIndex ->
                                onSelectSubtitleTrack(trackIndex)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        PlayerSheetState.AudioTracks -> AudioTracksContent(
                            isLandscape = isLandscape,
                            tracks = audioTracks,
                            onBack = { onNavigate(PlayerSheetState.PlayerOptions) },
                            onSelectTrack = { trackIndex ->
                                onSelectAudioTrack(trackIndex)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        PlayerSheetState.SubtitleStyle -> SubtitleStyleContent(
                            isLandscape = isLandscape,
                            delayMs = playerData.subtitleDelayMs,
                            scalePercent = playerData.subtitleScale,
                            activeEngine = activeEngine,
                            onBack = { onNavigate(PlayerSheetState.Subtitles) },
                            onDelayChange = onSubtitleDelayChange,
                            onScaleDragChange = { pct ->
                                isSliderDragging = true
                                onSubtitleScaleLiveChange(pct)
                            },
                            onScaleCommit = { pct ->
                                isSliderDragging = false
                                onSubtitleScaleCommit(pct)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        else -> {}
                    }
                }
            }
        }

        // Delete Confirmation Dialog (Frosted Liquid Glass)
        val deleteTitle = (sheetState as? PlayerSheetState.DeleteConfirm)?.videoTitle
        DeleteConfirmDialog(
            visible = sheetState is PlayerSheetState.DeleteConfirm,
            title = "Hapus Video?",
            message = if (deleteTitle != null) {
                "Apakah Anda yakin ingin menghapus \"$deleteTitle\"? Video akan dihapus secara permanen dari perangkat dan otomatis memutar video selanjutnya."
            } else {
                ""
            },
            onDismiss = onDismiss,
            onConfirm = onConfirmDelete,
            videoBackdropProvider = videoBackdropProvider
        )
    }

    // Playback Error Dialog (Frosted Liquid Glass)
    if (sheetState is PlayerSheetState.PlaybackError) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            val errorShape = RoundedCornerShape(24.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlassSurface(
                        cornerRadius = 24.dp,
                        tintColor = Color(0xCC121724),
                        highlightStrength = 0.88f,
                        grainStrength = 0.026f
                    )
                    .clip(errorShape)
                    .padding(22.dp)
            ) {
                Text(
                    text = "Kesalahan Pemutaran",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        shadow = PlayerGlassTextShadow
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = sheetState.message,
                    color = Color(0xFFD0D8E8),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    val closeShape = RoundedCornerShape(14.dp)
                    Box(
                        modifier = Modifier
                            .height(42.dp)
                            .clip(closeShape)
                            .background(Color(0xD90A84FF))
                            .border(0.8.dp, Color(0x3DFFFFFF), closeShape)
                            .clickable(onClick = onErrorDismissed)
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.action_close),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeaderWithBack(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiquidGlassCircleButton(
            iconRes = R.drawable.ic_arrow_back,
            contentDescription = stringResource(R.string.action_back),
            onClick = onBack,
            size = 38.dp,
            iconSize = 19.dp,
            tintColor = Color(0x4D121724)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = TextStyle(
                color = Color.White,
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
                shadow = PlayerGlassTextShadow
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StaggeredOptionItem(
    index: Int,
    isLandscape: Boolean,
    content: @Composable () -> Unit
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (index > 0) {
            delay(index * 28L)
        }
        progress.animateTo(
            targetValue = 1f,
            animationSpec = IosMotion.smoothFloat
        )
    }
    Box(
        modifier = Modifier.graphicsLayer {
            val p = progress.value
            alpha = p.coerceIn(0f, 1f)
            if (isLandscape) {
                translationX = (1f - p) * 24f
            } else {
                translationY = (1f - p) * 18f
            }
        }
    ) {
        content()
    }
}

@Composable
private fun PlayerOptionsContent(
    isLandscape: Boolean,
    playerData: PlayerUiData,
    onOpenSpeed: () -> Unit,
    onOpenAudio: () -> Unit,
    onOpenSubtitles: () -> Unit,
    onEnterPip: () -> Unit,
    onToggleSkipOpEd: () -> Unit,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rowHeight = if (isLandscape) 48.dp else 64.dp
    val rowVerticalPad = if (isLandscape) 6.dp else 10.dp
    val view = LocalView.current

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.player_options_title),
            style = TextStyle(
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                shadow = PlayerGlassTextShadow
            ),
            modifier = Modifier.padding(
                start = 20.dp,
                end = 20.dp,
                top = if (isLandscape) 14.dp else 6.dp,
                bottom = if (isLandscape) 6.dp else 10.dp
            )
        )

        val speedText = if (playerData.playbackSpeed == 1.0f) {
            "1.0x (Normal)"
        } else {
            "${playerData.playbackSpeed}x"
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(bottom = if (isLandscape) 10.dp else 16.dp)
        ) {
            StaggeredOptionItem(index = 0, isLandscape = isLandscape) {
                Column {
                    PlayerOptionRow(
                        iconRes = R.drawable.ic_speed,
                        title = stringResource(R.string.playback_speed),
                        subtitle = speedText,
                        rowHeight = rowHeight,
                        verticalPadding = rowVerticalPad,
                        onClick = onOpenSpeed
                    )
                    PlayerOptionDivider()
                }
            }

            StaggeredOptionItem(index = 1, isLandscape = isLandscape) {
                Column {
                    PlayerOptionRow(
                        iconRes = R.drawable.ic_volume,
                        title = stringResource(R.string.audio_tracks),
                        subtitle = stringResource(R.string.audio_tracks_desc),
                        rowHeight = rowHeight,
                        verticalPadding = rowVerticalPad,
                        onClick = onOpenAudio
                    )
                    PlayerOptionDivider()
                }
            }

            StaggeredOptionItem(index = 2, isLandscape = isLandscape) {
                Column {
                    PlayerOptionRow(
                        iconRes = R.drawable.ic_subtitles,
                        title = stringResource(R.string.subtitle_and_style),
                        subtitle = stringResource(R.string.subtitle_and_style_desc),
                        rowHeight = rowHeight,
                        verticalPadding = rowVerticalPad,
                        onClick = onOpenSubtitles
                    )
                    PlayerOptionDivider()
                }
            }

            StaggeredOptionItem(index = 3, isLandscape = isLandscape) {
                Column {
                    PlayerOptionRow(
                        iconRes = R.drawable.ic_pip,
                        title = stringResource(R.string.pip_title),
                        subtitle = stringResource(R.string.pip_desc),
                        rowHeight = rowHeight,
                        verticalPadding = rowVerticalPad,
                        onClick = onEnterPip
                    )
                    PlayerOptionDivider()
                }
            }

            // Skip OP/ED Toggle Row
            StaggeredOptionItem(index = 4, isLandscape = isLandscape) {
                Column {
                    val skipInteraction = remember { MutableInteractionSource() }
                    val skipPressScale by rememberIosPressScale(
                        interactionSource = skipInteraction,
                        pressedScale = 0.97f,
                        label = "skipOptionPressScale"
                    )
                    val skipIconTint by animateColorAsState(
                        targetValue = if (playerData.isSkipOpEdEnabled) {
                            AniPlayColors.Secondary
                        } else {
                            AniPlayColors.OnSurfaceMuted
                        },
                        animationSpec = IosMotion.color,
                        label = "skipIconTint"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = rowHeight)
                            .graphicsLayer {
                                scaleX = skipPressScale
                                scaleY = skipPressScale
                            }
                            .clickable(
                                interactionSource = skipInteraction,
                                indication = androidx.compose.material3.ripple(),
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    onToggleSkipOpEd()
                                }
                            )
                            .padding(horizontal = 20.dp, vertical = rowVerticalPad),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_skip_next),
                            contentDescription = null,
                            tint = skipIconTint,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 18.dp, end = 12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.skip_op_ed_title),
                                style = TextStyle(
                                    color = Color(0xFFF3F4F6),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    shadow = PlayerGlassTextShadow
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (playerData.isSkipOpEdEnabled) "Aktif" else "Nonaktif",
                                color = Color(0xFFC5CEDD),
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = playerData.isSkipOpEdEnabled,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AniPlayColors.Primary,
                                uncheckedThumbColor = AniPlayColors.TextSecondary,
                                uncheckedTrackColor = Color(0x33FFFFFF)
                            )
                        )
                    }

                    PlayerOptionDivider()
                }
            }

            StaggeredOptionItem(index = 5, isLandscape = isLandscape) {
                PlayerOptionRow(
                    iconRes = R.drawable.ic_delete,
                    title = stringResource(R.string.delete_video_title),
                    subtitle = stringResource(R.string.delete_video_option_desc),
                    iconTint = AniPlayColors.Danger,
                    titleColor = AniPlayColors.Danger,
                    rowHeight = rowHeight,
                    verticalPadding = rowVerticalPad,
                    onClick = onRequestDelete
                )
            }
        }
    }
}

@Composable
private fun PlayerOptionRow(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    iconTint: Color = Color(0xFFD6E0F0),
    titleColor: Color = Color(0xFFF3F4F6),
    rowHeight: androidx.compose.ui.unit.Dp = 64.dp,
    verticalPadding: androidx.compose.ui.unit.Dp = 10.dp,
    onClick: () -> Unit
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale by rememberIosPressScale(
        interactionSource = interactionSource,
        pressedScale = 0.97f,
        label = "playerOptionRowPressScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = rowHeight)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onClick()
                }
            )
            .padding(horizontal = 20.dp, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 18.dp, end = 12.dp)
        ) {
            Text(
                text = title,
                style = TextStyle(
                    color = titleColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    shadow = PlayerGlassTextShadow
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = Color(0xFFC5CEDD),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = Color(0xFFA8B3C5),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun PlayerOptionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 62.dp, end = 20.dp),
        thickness = 0.5.dp,
        color = Color(0x15FFFFFF)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AspectRatioContent(
    isLandscape: Boolean,
    currentAspectRatio: AspectRatioMode,
    onBack: () -> Unit,
    onSelectAspectRatio: (AspectRatioMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        AspectRatioMode.FIT to stringResource(R.string.aspect_ratio_fit),
        AspectRatioMode.STRETCH to stringResource(R.string.aspect_ratio_stretch),
        AspectRatioMode.ZOOM to stringResource(R.string.aspect_ratio_zoom),
        AspectRatioMode.SIXTEEN_NINE to stringResource(R.string.aspect_ratio_16_9),
        AspectRatioMode.FOUR_THREE to stringResource(R.string.aspect_ratio_4_3)
    )

    Column(
        modifier = modifier.padding(
            start = 18.dp,
            top = if (isLandscape) 18.dp else 4.dp,
            end = 18.dp
        )
    ) {
        SheetHeaderWithBack(
            title = stringResource(R.string.aspect_ratio_title),
            onBack = onBack
        )
        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp)
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { (mode, label) ->
                    SelectableGlassChip(
                        label = label,
                        selected = currentAspectRatio == mode,
                        onClick = { onSelectAspectRatio(mode) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackSpeedContent(
    isLandscape: Boolean,
    currentSpeed: Float,
    onBack: () -> Unit,
    onSelectSpeed: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val speedRows = listOf(
        listOf(
            Triple(0.25f, "0.25x", null),
            Triple(0.5f, "0.5x", null),
            Triple(0.75f, "0.75x", null)
        ),
        listOf(
            Triple(1.0f, "1.0x", "Normal"),
            Triple(1.25f, "1.25x", null),
            Triple(1.5f, "1.5x", null)
        ),
        listOf(
            Triple(1.75f, "1.75x", null),
            Triple(2.0f, "2.0x", null),
            Triple(2.5f, "2.5x", null)
        ),
        listOf(
            Triple(3.0f, "3.0x", null),
            Triple(3.5f, "3.5x", null),
            Triple(4.0f, "4.0x", null)
        )
    )

    Column(
        modifier = modifier.padding(
            start = 18.dp,
            top = if (isLandscape) 18.dp else 4.dp,
            end = 18.dp
        )
    ) {
        SheetHeaderWithBack(
            title = stringResource(R.string.playback_speed),
            onBack = onBack
        )
        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            speedRows.forEach { rowPresets ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowPresets.forEach { (speed, label, subtitle) ->
                        SelectableGlassChip(
                            label = label,
                            subtitle = subtitle,
                            selected = kotlin.math.abs(currentSpeed - speed) < 0.01f,
                            onClick = { onSelectSpeed(speed) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectableGlassChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale by rememberIosPressScale(
        interactionSource = interactionSource,
        pressedScale = 0.96f,
        label = "chipPressScale"
    )
    val tintColor by animateColorAsState(
        targetValue = if (selected) Color(0x4D0A84FF) else Color(0x24FFFFFF),
        animationSpec = IosMotion.color,
        label = "chipTintColor"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) Color.White else AniPlayColors.OnSurface,
        animationSpec = IosMotion.color,
        label = "chipTextColor"
    )
    val shape = RoundedCornerShape(16.dp)
    val highlightStrength = if (selected) 0.95f else 0.70f

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .liquidGlassSurface(
                cornerRadius = 16.dp,
                tintColor = tintColor,
                highlightStrength = highlightStrength
            )
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onClick()
                }
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (subtitle != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = label,
                    color = textColor,
                    fontSize = 13.5.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    color = if (selected) Color(0xCCFFFFFF) else Color(0xB8FFFFFF),
                    fontSize = 10.sp,
                    lineHeight = 11.sp,
                    maxLines = 1
                )
            }
        } else {
            Text(
                text = label,
                color = textColor,
                fontSize = 13.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun SubtitlesContent(
    isLandscape: Boolean,
    tracks: List<SubtitleTrackInfo>,
    onBack: () -> Unit,
    onPickExternalSubtitle: () -> Unit,
    onOpenSubtitleStyle: () -> Unit,
    onSelectTrack: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(
            start = 18.dp,
            top = if (isLandscape) 18.dp else 4.dp,
            end = 18.dp
        )
    ) {
        SheetHeaderWithBack(
            title = stringResource(R.string.subtitles),
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SubtitleActionGlassPill(
                    label = stringResource(R.string.load_external_sub),
                    iconRes = R.drawable.ic_folder,
                    onClick = onPickExternalSubtitle,
                    modifier = Modifier.weight(1f)
                )
                SubtitleActionGlassPill(
                    label = stringResource(R.string.custom_style_and_sync),
                    onClick = onOpenSubtitleStyle,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            TrackChoiceCard(
                title = "Nonaktifkan Subtitle",
                subtitle = "Matikan subtitle",
                isSelected = tracks.none { it.isSelected },
                onClick = { onSelectTrack(-1) }
            )

            tracks.forEach { track ->
                Spacer(modifier = Modifier.height(8.dp))
                TrackChoiceCard(
                    title = track.label,
                    subtitle = track.language ?: "Embedded Subtitle",
                    isSelected = track.isSelected,
                    onClick = { onSelectTrack(track.trackIndex) }
                )
            }
        }
    }
}

@Composable
private fun AudioTracksContent(
    isLandscape: Boolean,
    tracks: List<AudioTrackInfo>,
    onBack: () -> Unit,
    onSelectTrack: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(
            start = 18.dp,
            top = if (isLandscape) 18.dp else 4.dp,
            end = 18.dp
        )
    ) {
        SheetHeaderWithBack(
            title = stringResource(R.string.audio_tracks),
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            if (tracks.isEmpty()) {
                Text(
                    text = "Tidak ada trek audio tersedia.",
                    color = AniPlayColors.OnSurfaceMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                tracks.forEachIndexed { index, track ->
                    if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                    TrackChoiceCard(
                        title = track.label,
                        subtitle = "${track.language ?: "Audio"} • ${track.channels}ch",
                        isSelected = track.isSelected,
                        onClick = { onSelectTrack(track.trackIndex) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SubtitleActionGlassPill(
    label: String,
    @DrawableRes iconRes: Int? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale by rememberIosPressScale(
        interactionSource = interactionSource,
        pressedScale = 0.96f,
        label = "subtitleActionPillScale"
    )
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = modifier
            .height(44.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .liquidGlassSurface(
                cornerRadius = 14.dp,
                tintColor = Color(0x28FFFFFF),
                highlightStrength = 0.80f
            )
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onClick()
                }
            )
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TrackChoiceCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale by rememberIosPressScale(
        interactionSource = interactionSource,
        pressedScale = 0.97f,
        label = "trackChoiceScale"
    )
    val tintColor by animateColorAsState(
        targetValue = if (isSelected) Color(0x4D0A84FF) else Color(0x1EFFFFFF),
        animationSpec = IosMotion.color,
        label = "trackChoiceTint"
    )
    val titleColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else AniPlayColors.OnSurface,
        animationSpec = IosMotion.color,
        label = "trackChoiceTitleColor"
    )
    val shape = RoundedCornerShape(15.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .liquidGlassSurface(
                cornerRadius = 15.dp,
                tintColor = tintColor,
                highlightStrength = if (isSelected) 0.95f else 0.65f
            )
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onClick()
                }
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp)
        ) {
            Text(
                text = title,
                color = titleColor,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = if (isSelected) Color(0xCCFFFFFF) else AniPlayColors.TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2589FF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = Color(0xFF0A192F),
                    modifier = Modifier.size(13.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, Color(0x4DFFFFFF), CircleShape)
            )
        }
    }
}

@Composable
private fun SubtitleStyleContent(
    isLandscape: Boolean,
    delayMs: Long,
    scalePercent: Int,
    activeEngine: String,
    onBack: () -> Unit,
    onDelayChange: (Long) -> Unit,
    onScaleDragChange: (Int) -> Unit,
    onScaleCommit: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasWarnedExoOffset by remember { mutableStateOf(false) }
    var localScale by remember(scalePercent) {
        mutableIntStateOf(scalePercent.coerceIn(50, 200))
    }

    fun applyDelay(newOffsetMs: Long) {
        onDelayChange(newOffsetMs)
        if (activeEngine == DualEngineManager.ENGINE_EXOPLAYER && !hasWarnedExoOffset) {
            hasWarnedExoOffset = true
            Toast.makeText(
                context,
                "Sinkronisasi offset subtitle hanya berlaku di mode MPV",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Column(
        modifier = modifier.padding(
            start = 18.dp,
            top = if (isLandscape) 18.dp else 4.dp,
            end = 18.dp
        )
    ) {
        SheetHeaderWithBack(
            title = stringResource(R.string.subtitle_style),
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = stringResource(R.string.subtitle_sync),
                color = AniPlayColors.Secondary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { applyDelay(delayMs - 500L) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0x24FFFFFF),
                        contentColor = Color.White
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text(text = stringResource(R.string.sub_offset_minus))
                }

                Text(
                    text = "Offset: ${delayMs / 1000.0}s",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                OutlinedButton(
                    onClick = { applyDelay(delayMs + 500L) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0x24FFFFFF),
                        contentColor = Color.White
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text(text = stringResource(R.string.sub_offset_plus))
                }

                TextButton(
                    onClick = { applyDelay(0L) },
                    modifier = Modifier.height(40.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_reset),
                        color = AniPlayColors.Secondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Skala Subtitle: ${localScale}%",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Slider(
                value = ((localScale - 50) / 5).toFloat(),
                onValueChange = { raw ->
                    val pct = (50 + raw.roundToInt() * 5).coerceIn(50, 200)
                    localScale = pct
                    onScaleDragChange(pct)
                },
                onValueChangeFinished = {
                    onScaleCommit(localScale)
                },
                valueRange = 0f..30f,
                steps = 29,
                colors = SliderDefaults.colors(
                    thumbColor = AniPlayColors.Secondary,
                    activeTrackColor = AniPlayColors.Primary,
                    inactiveTrackColor = Color(0x33FFFFFF)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
