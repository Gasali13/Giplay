package com.personal.animeplayer.ui.compose.components

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.personal.animeplayer.R
import com.personal.animeplayer.data.model.Episode
import com.personal.animeplayer.data.model.SortOrder
import com.personal.animeplayer.data.model.VideoDetailInfo
import com.personal.animeplayer.ui.compose.theme.AniPlayColors
import com.personal.animeplayer.ui.compose.theme.IosMotion
import com.personal.animeplayer.ui.compose.theme.rememberIosPressScale
import com.personal.animeplayer.util.FileUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val GlassTextShadow = Shadow(
    color = Color(0x99000000),
    offset = Offset(0f, 1f),
    blurRadius = 5f
)

@Composable
private fun FloatingLiquidGlassSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    backdropState: LiquidGlassBackdropState?,
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dismissThresholdPx = with(density) { 90.dp.toPx() }
    val dragOffsetY = remember { Animatable(0f) }

    LaunchedEffect(visible) {
        if (visible) {
            dragOffsetY.snapTo(0f)
        }
    }

    BackHandler(enabled = visible) {
        onDismiss()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(IosMotion.scrimIn),
            exit = fadeOut(IosMotion.scrimOut)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x5C000000))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                animationSpec = IosMotion.sheetEnterOffset
            ) { it } + fadeIn(IosMotion.fadeIn),
            exit = slideOutVertically(
                animationSpec = IosMotion.sheetExitOffset
            ) { it } + fadeOut(IosMotion.fadeOut),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val sheetShape = RoundedCornerShape(28.dp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
                    .offset { IntOffset(0, dragOffsetY.value.roundToInt()) }
                    .shadow(22.dp, sheetShape, clip = false)
                    .liquidGlass(
                        backdropState = backdropState,
                        cornerRadius = 28.dp,
                        style = LiquidGlassStyle.Sheet
                    )
                    .clip(sheetShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            scope.launch {
                                val next = (dragOffsetY.value + delta).coerceAtLeast(0f)
                                dragOffsetY.snapTo(next)
                            }
                        },
                        onDragStopped = { velocity ->
                            if (dragOffsetY.value > dismissThresholdPx || velocity > 650f) {
                                onDismiss()
                            } else {
                                scope.launch {
                                    dragOffsetY.animateTo(
                                        0f,
                                        IosMotion.dragSettle
                                    )
                                }
                            }
                        }
                    )
                    .padding(start = 18.dp, top = 10.dp, end = 18.dp, bottom = 20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(36.dp)
                        .height(4.5.dp)
                        .clip(CircleShape)
                        .background(Color(0x52FFFFFF))
                )

                Spacer(modifier = Modifier.height(14.dp))

                content()
            }
        }
    }
}

@Composable
private fun FloatingLiquidGlassDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    backdropState: LiquidGlassBackdropState?,
    content: @Composable ColumnScope.() -> Unit
) {
    BackHandler(enabled = visible) {
        onDismiss()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(IosMotion.scrimIn),
            exit = fadeOut(IosMotion.scrimOut)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(
                initialScale = 0.94f,
                animationSpec = IosMotion.dialogEnterScale
            ) + fadeIn(IosMotion.fadeIn),
            exit = scaleOut(
                targetScale = 0.96f,
                animationSpec = IosMotion.dialogExitScale
            ) + fadeOut(IosMotion.fadeOut),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                val dialogShape = RoundedCornerShape(26.dp)
                Column(
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .fillMaxWidth()
                        .shadow(24.dp, dialogShape, clip = false)
                        .liquidGlass(
                            backdropState = backdropState,
                            cornerRadius = 26.dp,
                            style = LiquidGlassStyle.Sheet
                        )
                        .clip(dialogShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .padding(20.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
fun EpisodeOptionsBottomSheet(
    episode: Episode?,
    onDismiss: () -> Unit,
    onRename: (Episode) -> Unit,
    onShare: (Episode) -> Unit,
    onInfo: (Episode) -> Unit,
    onDelete: (Episode) -> Unit,
    backdropState: LiquidGlassBackdropState? = null
) {
    val view = LocalView.current
    var lastEpisode by remember { mutableStateOf(episode) }
    if (episode != null) {
        lastEpisode = episode
    }

    FloatingLiquidGlassSheet(
        visible = episode != null,
        onDismiss = onDismiss,
        backdropState = backdropState
    ) {
        val current = lastEpisode ?: return@FloatingLiquidGlassSheet
        val thumbState by rememberEpisodeThumbnail(
            uri = current.uri,
            knownDurationMs = current.durationMs
        )
        val effectiveDuration = thumbState.second

        val metaText = remember(effectiveDuration, current.fileSize) {
            val parts = mutableListOf<String>()
            if (effectiveDuration > 0L) {
                parts.add(FileUtils.formatBadgeDuration(effectiveDuration))
            }
            if (current.fileSize > 0L) {
                parts.add(FileUtils.formatFileSize(current.fileSize))
            }
            parts.joinToString(" • ")
        }

        // Minimalist Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Text(
                text = current.title,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    shadow = GlassTextShadow
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (metaText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = metaText,
                    color = Color(0xFF8E97A8),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LiquidGlassActionRow(
            iconRes = R.drawable.ic_edit,
            label = stringResource(R.string.action_rename),
            iconTint = Color(0xFFA0AEC0),
            textColor = Color(0xFFE5EAF2),
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onDismiss()
                onRename(current)
            }
        )

        Spacer(modifier = Modifier.height(4.dp))

        LiquidGlassActionRow(
            iconRes = R.drawable.ic_share,
            label = stringResource(R.string.action_share),
            iconTint = Color(0xFFA0AEC0),
            textColor = Color(0xFFE5EAF2),
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onDismiss()
                onShare(current)
            }
        )

        Spacer(modifier = Modifier.height(4.dp))

        LiquidGlassActionRow(
            iconRes = R.drawable.ic_info,
            label = stringResource(R.string.action_video_info_full),
            iconTint = Color(0xFFA0AEC0),
            textColor = Color(0xFFE5EAF2),
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onDismiss()
                onInfo(current)
            }
        )

        Spacer(modifier = Modifier.height(4.dp))

        LiquidGlassActionRow(
            iconRes = R.drawable.ic_delete,
            label = stringResource(R.string.action_delete),
            iconTint = AniPlayColors.DangerIos,
            textColor = AniPlayColors.DangerIos,
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onDismiss()
                onDelete(current)
            }
        )
    }
}

@Composable
private fun LiquidGlassActionRow(
    @DrawableRes iconRes: Int,
    label: String,
    iconTint: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale by rememberIosPressScale(
        interactionSource = interactionSource,
        pressedScale = 0.98f,
        label = "actionRowScale"
    )

    val rowShape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(rowShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = Color(0x33FFFFFF)),
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = TextStyle(
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                shadow = GlassTextShadow
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun SortOptionsBottomSheet(
    visible: Boolean,
    currentOrder: SortOrder,
    onDismiss: () -> Unit,
    onApplyOrder: (SortOrder) -> Unit,
    backdropState: LiquidGlassBackdropState? = null
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var selectedOrder by remember(currentOrder, visible) { mutableStateOf(currentOrder) }
    var isClosing by remember(visible) { mutableStateOf(false) }

    fun selectAndApply(order: SortOrder) {
        if (isClosing) return
        isClosing = true
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        selectedOrder = order
        if (order != currentOrder) {
            onApplyOrder(order)
        }
        scope.launch {
            delay(240L)
            onDismiss()
        }
    }

    FloatingLiquidGlassSheet(
        visible = visible,
        onDismiss = onDismiss,
        backdropState = backdropState
    ) {
        // Minimalist Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.dialog_sort_title),
                style = TextStyle(
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    shadow = GlassTextShadow
                ),
                modifier = Modifier.weight(1f)
            )

            Canvas(modifier = Modifier.size(20.dp)) {
                val w = size.width
                val h = size.height
                val funnelPath = Path().apply {
                    moveTo(w * 0.14f, h * 0.22f)
                    lineTo(w * 0.86f, h * 0.22f)
                    lineTo(w * 0.57f, h * 0.56f)
                    lineTo(w * 0.57f, h * 0.82f)
                    lineTo(w * 0.43f, h * 0.74f)
                    lineTo(w * 0.43f, h * 0.56f)
                    close()
                }
                drawPath(
                    path = funnelPath,
                    color = Color(0xFFA0A8B8),
                    style = Stroke(
                        width = 1.7.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "URUTKAN BERDASARKAN",
            color = Color(0xFF8E97A8),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        SortOptionRow(
            label = stringResource(R.string.sort_newest),
            isSelected = selectedOrder == SortOrder.NEWEST,
            onClick = { selectAndApply(SortOrder.NEWEST) }
        )

        Spacer(modifier = Modifier.height(4.dp))

        SortOptionRow(
            label = stringResource(R.string.sort_oldest),
            isSelected = selectedOrder == SortOrder.OLDEST,
            onClick = { selectAndApply(SortOrder.OLDEST) }
        )

        Spacer(modifier = Modifier.height(4.dp))

        SortOptionRow(
            label = stringResource(R.string.sort_name),
            isSelected = selectedOrder == SortOrder.NAME,
            onClick = { selectAndApply(SortOrder.NAME) }
        )
    }
}

@Composable
private fun SortOptionRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale by rememberIosPressScale(
        interactionSource = interactionSource,
        pressedScale = 0.98f,
        label = "sortRowScale"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) Color(0x660B2D59) else Color.Transparent,
        animationSpec = IosMotion.color,
        label = "sortRowBg"
    )

    val rowShape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(rowShape)
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = Color(0x33FFFFFF)),
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = if (isSelected) Color.White else Color(0xFFE5EAF2),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                shadow = GlassTextShadow
            ),
            modifier = Modifier.weight(1f)
        )

        Box(
            modifier = Modifier.size(22.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = isSelected,
                enter = scaleIn(
                    initialScale = 0.6f,
                    animationSpec = spring(
                        dampingRatio = 0.8f,
                        stiffness = 420f
                    )
                ) + fadeIn(IosMotion.fadeIn),
                exit = scaleOut(
                    targetScale = 0.6f,
                    animationSpec = IosMotion.dialogExitScale
                ) + fadeOut(IosMotion.fadeOut)
            ) {
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
            }
        }
    }
}

@Composable
fun DeleteConfirmDialog(
    visible: Boolean,
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    backdropState: LiquidGlassBackdropState? = null,
    videoBackdropFrame: VideoBackdropFrame? = null,
    videoBackdropProvider: (() -> VideoBackdropFrame?)? = null
) {
    val view = LocalView.current
    var lastTitle by remember { mutableStateOf(title) }
    var lastMessage by remember { mutableStateOf(message) }
    if (visible) {
        if (title.isNotEmpty()) lastTitle = title
        if (message.isNotEmpty()) lastMessage = message
    }

    BackHandler(enabled = visible) {
        onDismiss()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(IosMotion.scrimIn),
            exit = fadeOut(IosMotion.scrimOut)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(
                initialScale = 0.94f,
                animationSpec = IosMotion.dialogEnterScale
            ) + fadeIn(IosMotion.fadeIn),
            exit = scaleOut(
                targetScale = 0.96f,
                animationSpec = IosMotion.dialogExitScale
            ) + fadeOut(IosMotion.fadeOut),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                val dialogShape = RoundedCornerShape(26.dp)
                val surfaceModifier = when {
                    videoBackdropProvider != null -> Modifier.liquidGlassBitmapBackdrop(
                        frameProvider = videoBackdropProvider,
                        cornerRadius = 26.dp,
                        style = LiquidGlassStyle.Sheet,
                        fallbackTintColor = Color(0xCC101520)
                    )
                    videoBackdropFrame != null -> Modifier.liquidGlassBitmapBackdrop(
                        frame = videoBackdropFrame,
                        cornerRadius = 26.dp,
                        style = LiquidGlassStyle.Sheet,
                        fallbackTintColor = Color(0xCC101520)
                    )
                    backdropState != null -> Modifier.liquidGlass(
                        backdropState = backdropState,
                        cornerRadius = 26.dp,
                        style = LiquidGlassStyle.Sheet
                    )
                    else -> Modifier.liquidGlassSurface(
                        cornerRadius = 26.dp,
                        tintColor = Color(0xCC101520),
                        highlightStrength = 0.88f,
                        grainStrength = 0.028f
                    )
                }

                Column(
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .fillMaxWidth()
                        .shadow(24.dp, dialogShape, clip = false)
                        .then(surfaceModifier)
                        .clip(dialogShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .padding(22.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(AniPlayColors.DangerBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = null,
                                tint = AniPlayColors.DangerIos,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = lastTitle,
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                shadow = GlassTextShadow
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = lastMessage,
                        color = Color(0xFFD0D8E8),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    val pillShape = RoundedCornerShape(14.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(pillShape)
                                .background(Color(0x24FFFFFF))
                                .border(0.8.dp, Color(0x33FFFFFF), pillShape)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onDismiss()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.action_cancel),
                                style = TextStyle(
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    shadow = GlassTextShadow
                                )
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(pillShape)
                                .background(Color(0xD9EF4444))
                                .border(0.8.dp, Color(0x3DFFFFFF), pillShape)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    onDismiss()
                                    onConfirm()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.action_delete),
                                style = TextStyle(
                                    color = Color.White,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    shadow = GlassTextShadow
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RenameVideoDialog(
    episode: Episode?,
    onDismiss: () -> Unit,
    onConfirmRename: (Episode, String) -> Unit,
    backdropState: LiquidGlassBackdropState? = null
) {
    val view = LocalView.current
    var lastEpisode by remember { mutableStateOf(episode) }
    if (episode != null) {
        lastEpisode = episode
    }

    FloatingLiquidGlassDialog(
        visible = episode != null,
        onDismiss = onDismiss,
        backdropState = backdropState
    ) {
        val current = lastEpisode ?: return@FloatingLiquidGlassDialog
        val focusRequester = remember { FocusRequester() }
        var textFieldValue by remember(current.uri, episode != null) {
            mutableStateOf(
                TextFieldValue(
                    text = current.title,
                    selection = TextRange(0, current.title.length)
                )
            )
        }
        var errorText by remember(current.uri, episode != null) { mutableStateOf<String?>(null) }

        LaunchedEffect(episode != null, current.uri) {
            if (episode != null) {
                delay(80L)
                try {
                    focusRequester.requestFocus()
                } catch (_: IllegalStateException) {
                }
            }
        }

        // Minimalist Header
        Text(
            text = stringResource(R.string.dialog_rename_title),
            style = TextStyle(
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                shadow = GlassTextShadow
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.dialog_rename_desc),
            color = Color(0xFFB0BAC9),
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = textFieldValue,
            onValueChange = {
                textFieldValue = it
                errorText = null
            },
            placeholder = {
                Text(
                    text = stringResource(R.string.dialog_rename_hint),
                    color = Color(0xFF7D8798),
                    fontSize = 14.sp
                )
            },
            isError = errorText != null,
            supportingText = errorText?.let { err ->
                {
                    Text(
                        text = err,
                        color = AniPlayColors.DangerIos,
                        fontSize = 12.sp
                    )
                }
            },
            maxLines = 2,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0x47060910),
                unfocusedContainerColor = Color(0x36060910),
                errorContainerColor = Color(0x4718080C),
                focusedBorderColor = AniPlayColors.SkyBlue,
                unfocusedBorderColor = Color(0x33FFFFFF),
                errorBorderColor = AniPlayColors.DangerIos,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = AniPlayColors.SkyBlue
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )

        Spacer(modifier = Modifier.height(18.dp))

        val btnShape = RoundedCornerShape(14.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(btnShape)
                    .background(Color(0x1FFFFFFF))
                    .border(0.8.dp, Color(0x28FFFFFF), btnShape)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    style = TextStyle(
                        color = Color(0xFFE5EAF2),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        shadow = GlassTextShadow
                    )
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(btnShape)
                    .background(AniPlayColors.AppleBlue)
                    .clickable {
                        val newName = textFieldValue.text.trim()
                        if (newName.isBlank()) {
                            errorText = "Nama tidak boleh kosong"
                            return@clickable
                        }
                        val invalidChars = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
                        if (newName.any { it in invalidChars }) {
                            errorText = "Nama file tidak boleh mengandung karakter khusus"
                            return@clickable
                        }
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (newName == current.title) {
                            onDismiss()
                            return@clickable
                        }
                        onDismiss()
                        onConfirmRename(current, newName)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.action_save),
                    color = Color.White,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun VideoInfoDialog(
    episode: Episode?,
    fetchDetails: suspend (Episode) -> VideoDetailInfo,
    onDismiss: () -> Unit,
    backdropState: LiquidGlassBackdropState? = null
) {
    var lastEpisode by remember { mutableStateOf(episode) }
    if (episode != null) {
        lastEpisode = episode
    }

    FloatingLiquidGlassDialog(
        visible = episode != null,
        onDismiss = onDismiss,
        backdropState = backdropState
    ) {
        val current = lastEpisode ?: return@FloatingLiquidGlassDialog
        var details by remember(current.uri) { mutableStateOf<VideoDetailInfo?>(null) }

        LaunchedEffect(episode?.uri) {
            val target = episode ?: return@LaunchedEffect
            details = fetchDetails(target)
        }

        // Minimalist Header
        Text(
            text = stringResource(R.string.dialog_info_title),
            style = TextStyle(
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                shadow = GlassTextShadow
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        HorizontalDivider(
            color = Color(0x1CFFFFFF),
            thickness = 0.5.dp
        )

        Spacer(modifier = Modifier.height(12.dp))

        val specCardShape = RoundedCornerShape(14.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 440.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(specCardShape)
                    .background(Color(0x14FFFFFF))
                    .border(0.6.dp, Color(0x1FFFFFFF), specCardShape)
                    .padding(14.dp)
            ) {
                InfoField(
                    label = stringResource(R.string.info_label_filename),
                    value = details?.fileName ?: current.fileName
                )
                Spacer(modifier = Modifier.height(10.dp))
                InfoField(
                    label = stringResource(R.string.info_label_path),
                    value = details?.path ?: "Memuat path...",
                    valueFontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InfoSpecCard(
                    label = stringResource(R.string.info_label_size),
                    value = details?.fileSizeFormatted ?: FileUtils.formatFileSize(current.fileSize),
                    modifier = Modifier.weight(1f)
                )
                InfoSpecCard(
                    label = stringResource(R.string.info_label_duration),
                    value = details?.durationFormatted ?: if (current.durationMs > 0L) {
                        FileUtils.formatDuration(current.durationMs)
                    } else {
                        "--:--"
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InfoSpecCard(
                    label = stringResource(R.string.info_label_resolution),
                    value = details?.resolution ?: "Memuat...",
                    modifier = Modifier.weight(1f)
                )
                InfoSpecCard(
                    label = stringResource(R.string.info_label_mime),
                    value = details?.mimeType ?: "Memuat...",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InfoSpecCard(
                    label = stringResource(R.string.info_label_hdr),
                    value = details?.hdrType ?: "Memuat...",
                    valueColor = AniPlayColors.SkyBlue,
                    valueFontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                InfoSpecCard(
                    label = stringResource(R.string.info_label_color),
                    value = details?.let {
                        it.colorDetails.ifBlank { "BT.709 (8-bit)" }
                    } ?: "Memuat...",
                    valueFontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InfoSpecCard(
                    label = stringResource(R.string.info_label_bitrate),
                    value = details?.bitrateFormatted ?: "Memuat...",
                    modifier = Modifier.weight(1f)
                )
                InfoSpecCard(
                    label = stringResource(R.string.info_label_date),
                    value = details?.dateAddedFormatted ?: "Memuat...",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val closeShape = RoundedCornerShape(14.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(closeShape)
                .background(Color(0x2BFFFFFF))
                .border(0.9.dp, Color(0x38FFFFFF), closeShape)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.action_close),
                style = TextStyle(
                    color = Color.White,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    shadow = GlassTextShadow
                )
            )
        }
    }
}

@Composable
private fun InfoSpecCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.White,
    valueFontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    valueFontWeight: FontWeight = FontWeight.SemiBold
) {
    val cardShape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .clip(cardShape)
            .background(Color(0x14FFFFFF))
            .border(0.6.dp, Color(0x1FFFFFFF), cardShape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        InfoField(
            label = label,
            value = value,
            valueColor = valueColor,
            valueFontSize = valueFontSize,
            valueFontWeight = valueFontWeight
        )
    }
}

@Composable
private fun InfoField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.White,
    valueFontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    valueFontWeight: FontWeight = FontWeight.Medium
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = Color(0xFF94A3B8),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = TextStyle(
                color = valueColor,
                fontSize = valueFontSize,
                fontWeight = valueFontWeight,
                shadow = GlassTextShadow
            )
        )
    }
}
