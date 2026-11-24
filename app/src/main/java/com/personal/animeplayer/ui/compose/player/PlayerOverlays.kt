package com.personal.animeplayer.ui.compose.player

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.animeplayer.R
import com.personal.animeplayer.ui.compose.components.LiquidGlassStyle
import com.personal.animeplayer.ui.compose.components.VideoBackdropFrame
import com.personal.animeplayer.ui.compose.components.liquidGlassBitmapBackdrop
import com.personal.animeplayer.ui.compose.theme.AniPlayColors
import com.personal.animeplayer.ui.compose.theme.IosMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class DoubleTapOverlayState(
    val isForward: Boolean = false,
    val accumulatedSeconds: Int = 0,
    val tapToken: Int = 0
)

@Composable
fun DoubleTapSeekOverlay(
    state: DoubleTapOverlayState,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    val textScale = remember { Animatable(1f) }

    LaunchedEffect(state.tapToken) {
        if (state.tapToken > 0 && state.accumulatedSeconds > 0) {
            visible = true
            textScale.snapTo(1.25f)
            launch {
                textScale.animateTo(1f, IosMotion.smoothFloat)
            }
            delay(650L)
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 160, easing = IosMotion.EaseOut)),
        exit = fadeOut(animationSpec = IosMotion.fadeOut),
        modifier = modifier
    ) {
        val textStyle = TextStyle(
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            shadow = Shadow(
                color = Color(0xE6000000),
                offset = Offset(0f, 2f),
                blurRadius = 10f
            )
        )

        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                if (!state.isForward && state.accumulatedSeconds > 0) {
                    Text(
                        text = "-${state.accumulatedSeconds} Detik",
                        style = textStyle,
                        modifier = Modifier.graphicsLayer {
                            scaleX = textScale.value
                            scaleY = textScale.value
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                if (state.isForward && state.accumulatedSeconds > 0) {
                    Text(
                        text = "+${state.accumulatedSeconds} Detik",
                        style = textStyle,
                        modifier = Modifier.graphicsLayer {
                            scaleX = textScale.value
                            scaleY = textScale.value
                        }
                    )
                }
            }
        }
    }
}

data class GestureFeedbackState(
    @DrawableRes val iconRes: Int,
    val text: String,
    val token: Int = 0
)

@Composable
fun GestureFeedbackPill(
    state: GestureFeedbackState?,
    modifier: Modifier = Modifier,
    videoBackdropProvider: (() -> VideoBackdropFrame?)? = null
) {
    var lastState by remember { mutableStateOf(state) }
    if (state != null) {
        lastState = state
    }

    AnimatedVisibility(
        visible = state != null,
        enter = fadeIn(IosMotion.fadeIn) + scaleIn(initialScale = 0.94f, animationSpec = IosMotion.dialogEnterScale),
        exit = fadeOut(IosMotion.fadeOut) + scaleOut(targetScale = 0.96f, animationSpec = IosMotion.dialogExitScale),
        modifier = modifier
    ) {
        lastState?.let { current ->
            val shape = RoundedCornerShape(24.dp)
            Row(
                modifier = Modifier
                    .shadow(14.dp, shape, clip = false)
                    .liquidGlassBitmapBackdrop(
                        frameProvider = videoBackdropProvider ?: { null },
                        cornerRadius = 24.dp,
                        style = LiquidGlassStyle.PlayerControl.copy(
                            blurRadius = 14.dp,
                            bezelWidth = 10.dp,
                            refractionStrength = 7.dp,
                            chromaticAberration = 1.4.dp,
                            tintColor = Color(0x5E101520),
                            highlightStrength = 0.90f,
                            vibrancy = 1.28f,
                            grainStrength = 0.018f
                        ),
                        fallbackTintColor = Color(0x88101520)
                    )
                    .clip(shape)
                    .padding(horizontal = 18.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(current.iconRes),
                    contentDescription = null,
                    tint = AniPlayColors.Secondary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = current.text,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        shadow = Shadow(
                            color = Color(0x99000000),
                            offset = Offset(0f, 1.5f),
                            blurRadius = 6f
                        )
                    )
                )
            }
        }
    }
}

object SpeedOverlayDefaults {
    val speeds = floatArrayOf(0.25f, 0.5f, 1.0f, 2.0f, 3.0f, 4.0f)
    val labels = listOf("0.25x", "0.5x", "1.0x", "2.0x", "3.0x", "4.0x")
}

@Composable
fun SpeedControlOverlay(
    visible: Boolean,
    speedIndex: Int,
    topInsetDp: Dp,
    isPortrait: Boolean,
    modifier: Modifier = Modifier,
    videoBackdropProvider: (() -> VideoBackdropFrame?)? = null
) {
    val clampedIndex = speedIndex.coerceIn(0, SpeedOverlayDefaults.speeds.lastIndex)
    val animatedIndex by animateFloatAsState(
        targetValue = clampedIndex.toFloat(),
        animationSpec = tween(durationMillis = 220, easing = IosMotion.EaseOut),
        label = "speedThumbIndex"
    )

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val padHPx = with(density) { 24.dp.toPx() }
    val trackStrokePx = with(density) { 2.dp.toPx() }
    val dotRadiusPx = with(density) { 2.5.dp.toPx() }
    val labelGapPx = with(density) { 11.dp.toPx() }
    val haloRadiusPx = with(density) { 8.dp.toPx() }
    val thumbRadiusPx = with(density) { 5.dp.toPx() }
    val innerDotRadiusPx = with(density) { 2.dp.toPx() }
    val iconW = with(density) { 13.dp.toPx() }
    val iconH = with(density) { 7.5.dp.toPx() }
    val iconGap = with(density) { 5.dp.toPx() }

    val topMargin = topInsetDp + if (isPortrait) 98.dp else 24.dp

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(IosMotion.fadeIn) + scaleIn(initialScale = 0.94f, animationSpec = IosMotion.dialogEnterScale),
        exit = fadeOut(IosMotion.fadeOut) + scaleOut(targetScale = 0.96f, animationSpec = IosMotion.dialogExitScale),
        modifier = modifier.padding(top = topMargin, start = 20.dp, end = 20.dp)
    ) {
        val shape = RoundedCornerShape(20.dp)
        Box(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .height(74.dp)
                .shadow(16.dp, shape, clip = false)
                .liquidGlassBitmapBackdrop(
                    frameProvider = videoBackdropProvider ?: { null },
                    cornerRadius = 20.dp,
                    style = LiquidGlassStyle.PlayerControl.copy(
                        blurRadius = 15.dp,
                        bezelWidth = 10.dp,
                        refractionStrength = 7.dp,
                        chromaticAberration = 1.4.dp,
                        tintColor = Color(0x66101520),
                        highlightStrength = 0.90f,
                        vibrancy = 1.28f,
                        grainStrength = 0.02f
                    ),
                    fallbackTintColor = Color(0x90101520)
                )
                .clip(shape)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val usableWidth = (w - padHPx * 2f).coerceAtLeast(1f)
                val step = usableWidth / (SpeedOverlayDefaults.speeds.size - 1)
                val trackY = h * 0.40f

                // Track line
                drawLine(
                    color = Color(0x3CFFFFFF),
                    start = Offset(padHPx, trackY),
                    end = Offset(padHPx + usableWidth, trackY),
                    strokeWidth = trackStrokePx,
                    cap = StrokeCap.Round
                )

                // Tick dots and labels
                for (i in SpeedOverlayDefaults.labels.indices) {
                    val tickX = padHPx + i * step
                    val isCurrent = (i == clampedIndex)

                    drawCircle(
                        color = if (isCurrent) AniPlayColors.SkyBlue else Color(0x8CC8D7E6),
                        radius = dotRadiusPx,
                        center = Offset(tickX, trackY)
                    )

                    val measured = textMeasurer.measure(
                        text = SpeedOverlayDefaults.labels[i],
                        style = TextStyle(
                            color = if (isCurrent) AniPlayColors.SkyBlue else Color(0xD8E2E8F0),
                            fontSize = if (isCurrent) 11.5.sp else 10.5.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                    drawText(
                        textLayoutResult = measured,
                        topLeft = Offset(
                            x = tickX - measured.size.width / 2f,
                            y = trackY - labelGapPx - measured.size.height * 0.8f
                        )
                    )
                }

                // Glowing active thumb
                val thumbX = padHPx + animatedIndex * step
                drawCircle(
                    color = Color(0x4638BDF8),
                    radius = haloRadiusPx,
                    center = Offset(thumbX, trackY)
                )
                drawCircle(
                    color = AniPlayColors.SkyBlue,
                    radius = thumbRadiusPx,
                    center = Offset(thumbX, trackY)
                )
                drawCircle(
                    color = Color.White,
                    radius = innerDotRadiusPx,
                    center = Offset(thumbX, trackY)
                )

                // Bottom status: "▶▶ 2.0x Speed Playing"
                val statusText = "${SpeedOverlayDefaults.labels[clampedIndex]} Speed Playing"
                val statusMeasured = textMeasurer.measure(
                    text = statusText,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        shadow = Shadow(
                            color = Color(0x99000000),
                            offset = Offset(0f, 1.5f),
                            blurRadius = 5f
                        )
                    )
                )
                val totalWidth = iconW + iconGap + statusMeasured.size.width
                val startX = (w - totalWidth) / 2f
                val statusCenterY = h * 0.77f
                val halfW = iconW / 2f

                val chevronPath = Path().apply {
                    moveTo(startX, statusCenterY - iconH / 2f)
                    lineTo(startX + halfW * 0.8f, statusCenterY)
                    lineTo(startX, statusCenterY + iconH / 2f)
                    close()

                    moveTo(startX + halfW * 0.7f, statusCenterY - iconH / 2f)
                    lineTo(startX + iconW, statusCenterY)
                    lineTo(startX + halfW * 0.7f, statusCenterY + iconH / 2f)
                    close()
                }
                drawPath(path = chevronPath, color = AniPlayColors.SkyBlue)

                drawText(
                    textLayoutResult = statusMeasured,
                    topLeft = Offset(
                        x = startX + iconW + iconGap,
                        y = statusCenterY - statusMeasured.size.height / 2f
                    )
                )
            }
        }
    }
}

@Composable
fun InactivityPromptOverlay(
    visible: Boolean,
    secondsLeft: Int,
    onContinueWatching: () -> Unit,
    modifier: Modifier = Modifier,
    videoBackdropProvider: (() -> VideoBackdropFrame?)? = null
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(IosMotion.scrimIn),
        exit = fadeOut(IosMotion.scrimOut),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            contentAlignment = Alignment.Center
        ) {
            val cardShape = RoundedCornerShape(22.dp)
            Box(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .shadow(20.dp, cardShape, clip = false)
                    .liquidGlassBitmapBackdrop(
                        frameProvider = videoBackdropProvider ?: { null },
                        cornerRadius = 22.dp,
                        style = LiquidGlassStyle.Sheet,
                        fallbackTintColor = Color(0xDE141824)
                    )
                    .clip(cardShape)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0x4D818CF8),
                                        Color(0x1A6366F1),
                                        Color.Transparent
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_bedtime),
                            contentDescription = null,
                            tint = Color(0xFFA5B4FC),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.inactivity_title),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.inactivity_desc),
                        color = Color(0xFFC7D2FE),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0x26FFFFFF))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = (secondsLeft / 30f).coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF818CF8))
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Otomatis keluar dalam $secondsLeft detik",
                        color = AniPlayColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = onContinueWatching,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4F46E5),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_play_arrow),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.inactivity_continue),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
