package com.personal.animeplayer.ui.compose.player

import android.graphics.BlurMaskFilter
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint as AndroidPaint
import android.graphics.Shader
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.personal.animeplayer.ui.compose.theme.IosMotion
import kotlin.math.abs
import kotlin.math.roundToLong

@Composable
fun PlayerGlowingSeekBar(
    positionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onScrubStart: () -> Unit,
    onScrubChange: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val horizontalPadPx = with(density) { 14.dp.toPx() }
    val barHeightPx = with(density) { 5.dp.toPx() }
    val trailStrokePx = barHeightPx + with(density) { 7.dp.toPx() }
    val normalBlurPx = with(density) { 5.dp.toPx() }
    val pressedBlurPx = with(density) { 6.5.dp.toPx() }

    val currentDurationMs by rememberUpdatedState(durationMs)
    val currentOnScrubStart by rememberUpdatedState(onScrubStart)
    val currentOnScrubChange by rememberUpdatedState(onScrubChange)
    val currentOnScrubEnd by rememberUpdatedState(onScrubEnd)

    var isDragging by remember { mutableStateOf(false) }
    var dragRatio by remember { mutableFloatStateOf(0f) }

    val progressRatio = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val displayRatio = if (isDragging) dragRatio else progressRatio

    var chasingRatio by remember { mutableFloatStateOf(displayRatio) }
    var hasInitializedChasing by remember { mutableStateOf(false) }

    LaunchedEffect(displayRatio, durationMs) {
        if (!hasInitializedChasing || durationMs <= 0L) {
            chasingRatio = displayRatio
            hasInitializedChasing = true
            return@LaunchedEffect
        }
        while (abs(displayRatio - chasingRatio) > 0.001f) {
            withFrameNanos {
                val diff = displayRatio - chasingRatio
                chasingRatio += if (abs(diff) <= 0.001f) diff else diff * 0.24f
            }
        }
        chasingRatio = displayRatio
    }

    val glowTrailDp by animateDpAsState(
        targetValue = if (isDragging) 24.dp else 16.dp,
        animationSpec = tween(durationMillis = 220, easing = IosMotion.EaseOut),
        label = "seekbarGlowTrail"
    )
    val glowRadiusDp by animateDpAsState(
        targetValue = if (isDragging) 17.dp else 13.dp,
        animationSpec = tween(durationMillis = 220, easing = IosMotion.EaseOut),
        label = "seekbarGlowRadius"
    )
    val glowIntensity by animateFloatAsState(
        targetValue = if (isDragging) 1.35f else 1.0f,
        animationSpec = tween(durationMillis = 220, easing = IosMotion.EaseOut),
        label = "seekbarGlowIntensity"
    )

    val glowTrailPx = with(density) { glowTrailDp.toPx() }
    val glowRadiusPx = with(density) { glowRadiusDp.toPx() }

    val shaderMatrix = remember { Matrix() }
    val unitTrailShader = remember {
        LinearGradient(
            0f, 0f, 1f, 0f,
            intArrayOf(
                AndroidColor.argb(0, 0, 140, 255),
                AndroidColor.argb(56, 0, 160, 255),
                AndroidColor.argb(148, 56, 189, 248)
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    val normalBlurFilter = remember(normalBlurPx) {
        BlurMaskFilter(normalBlurPx, BlurMaskFilter.Blur.NORMAL)
    }
    val pressedBlurFilter = remember(pressedBlurPx) {
        BlurMaskFilter(pressedBlurPx, BlurMaskFilter.Blur.NORMAL)
    }
    val trailPaint = remember(trailStrokePx) {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
            strokeWidth = trailStrokePx
            shader = unitTrailShader
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()

                    fun calculateRatio(touchX: Float): Float {
                        val trackStartX = horizontalPadPx + barHeightPx / 2f
                        val trackEndX = size.width.toFloat() - horizontalPadPx - barHeightPx / 2f
                        val avail = (trackEndX - trackStartX).coerceAtLeast(1f)
                        return ((touchX - trackStartX) / avail).coerceIn(0f, 1f)
                    }

                    fun ratioToMs(ratio: Float): Long {
                        val dur = currentDurationMs
                        return if (dur > 0L) (ratio * dur).roundToLong().coerceIn(0L, dur) else 0L
                    }

                    isDragging = true
                    dragRatio = calculateRatio(down.position.x)
                    currentOnScrubStart()
                    currentOnScrubChange(ratioToMs(dragRatio))

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) {
                                change.consume()
                                dragRatio = calculateRatio(change.position.x)
                                break
                            }
                            change.consume()
                            dragRatio = calculateRatio(change.position.x)
                            currentOnScrubChange(ratioToMs(dragRatio))
                        }
                    } finally {
                        val finalMs = ratioToMs(dragRatio)
                        isDragging = false
                        currentOnScrubEnd(finalMs)
                    }
                }
            }
    ) {
        val centerY = size.height / 2f
        val trackStartX = horizontalPadPx + barHeightPx / 2f
        val trackEndX = size.width - horizontalPadPx - barHeightPx / 2f
        val availableWidth = (trackEndX - trackStartX).coerceAtLeast(0f)

        val currentHeadX = trackStartX + availableWidth * displayRatio
        val chasingGlowX = trackStartX + availableWidth * chasingRatio

        // 1. Background Track
        drawLine(
            color = Color(0x38223854),
            start = Offset(trackStartX, centerY),
            end = Offset(trackEndX, centerY),
            strokeWidth = barHeightPx,
            cap = StrokeCap.Round
        )

        if (currentHeadX > trackStartX) {
            val trailAlpha = (glowIntensity / 1.35f).coerceIn(0.70f, 1f)
            val lag = chasingGlowX - currentHeadX

            // 2. Elastic Gaussian Chasing Glow Trail
            trailPaint.maskFilter = if (isDragging) pressedBlurFilter else normalBlurFilter
            trailPaint.alpha = (255 * trailAlpha).toInt().coerceIn(160, 255)

            if (lag <= 0f) {
                val glowStartX = (chasingGlowX - glowTrailPx).coerceAtLeast(trackStartX)
                val span = currentHeadX - glowStartX
                if (span > 0.5f) {
                    shaderMatrix.reset()
                    shaderMatrix.setScale(span, 1f)
                    shaderMatrix.postTranslate(glowStartX, centerY)
                    unitTrailShader.setLocalMatrix(shaderMatrix)
                    trailPaint.shader = unitTrailShader
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawLine(
                            glowStartX,
                            centerY,
                            currentHeadX,
                            centerY,
                            trailPaint
                        )
                    }
                }
            } else {
                val glowEndX = (chasingGlowX + glowTrailPx).coerceAtMost(trackEndX)
                val span = glowEndX - currentHeadX
                if (span > 0.5f) {
                    shaderMatrix.reset()
                    shaderMatrix.setScale(-span, 1f)
                    shaderMatrix.postTranslate(glowEndX, centerY)
                    unitTrailShader.setLocalMatrix(shaderMatrix)
                    trailPaint.shader = unitTrailShader
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawLine(
                            currentHeadX,
                            centerY,
                            glowEndX,
                            centerY,
                            trailPaint
                        )
                    }
                }
            }

            // 3. Smooth 7-Stop Gaussian Radial Bloom around Tip
            val tipRadius = glowRadiusPx.coerceAtLeast(1f)
            val bloomCyan = Color(0xFF38BDF8)
            val bloomBlue = Color(0xFF008CFF)
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.00f to bloomCyan.copy(alpha = 0.68f * trailAlpha),
                        0.18f to bloomCyan.copy(alpha = 0.48f * trailAlpha),
                        0.36f to bloomBlue.copy(alpha = 0.28f * trailAlpha),
                        0.56f to bloomBlue.copy(alpha = 0.13f * trailAlpha),
                        0.74f to bloomBlue.copy(alpha = 0.045f * trailAlpha),
                        0.88f to bloomBlue.copy(alpha = 0.012f * trailAlpha),
                        1.00f to Color.Transparent
                    ),
                    center = Offset(currentHeadX, centerY),
                    radius = tipRadius
                ),
                radius = tipRadius,
                center = Offset(currentHeadX, centerY)
            )

            // 4. Solid Active Progress Line
            drawLine(
                color = Color(0xFF0084FF),
                start = Offset(trackStartX, centerY),
                end = Offset(currentHeadX, centerY),
                strokeWidth = barHeightPx,
                cap = StrokeCap.Round
            )

            // 5. Soft Radial Luminous Core at Tip
            val coreRadius = (barHeightPx * 0.7f).coerceAtLeast(1f)
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.00f to Color(0xFF8CE0FF),
                        0.45f to Color(0xFF38BDF8),
                        0.80f to Color(0xFF0084FF),
                        1.00f to Color.Transparent
                    ),
                    center = Offset(currentHeadX, centerY),
                    radius = coreRadius
                ),
                radius = coreRadius,
                center = Offset(currentHeadX, centerY)
            )
        }
    }
}
