package com.personal.animeplayer.ui.compose.components

import android.graphics.Paint
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.animeplayer.data.model.Episode
import com.personal.animeplayer.ui.compose.theme.AniPlayColors
import com.personal.animeplayer.ui.compose.theme.IosMotion
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val AlphabetLetters = listOf('#') + ('A'..'Z')

object AlphabetIndexHelper {
    fun getLetterForEpisode(episode: Episode): Char {
        val name = episode.fileName.ifEmpty { episode.title }
        val first = name.trim().firstOrNull()?.uppercaseChar() ?: return '#'
        return if (first in 'A'..'Z') first else '#'
    }

    fun findPositionForLetter(letter: Char, episodes: List<Episode>): Int {
        if (episodes.isEmpty()) return -1
        if (letter == '#') return 0

        val exactIndex = episodes.indexOfFirst { getLetterForEpisode(it) == letter }
        if (exactIndex != -1) return exactIndex

        val nextIndex = episodes.indexOfFirst {
            val ch = getLetterForEpisode(it)
            ch in 'A'..'Z' && ch > letter
        }
        if (nextIndex != -1) return nextIndex

        return episodes.size - 1
    }
}

@Composable
fun AlphabetIndexOverlay(
    visible: Boolean,
    highlightedLetter: Char,
    onLetterSelected: (Char) -> Unit,
    onDragStateChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    backdropState: LiquidGlassBackdropState? = null
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val topPadPx = with(density) { 6.dp.toPx() }
    val bottomPadPx = with(density) { 6.dp.toPx() }
    val minTextPx = with(density) { 8.5.dp.toPx() }
    val maxTextPx = with(density) { 11.5.dp.toPx() }
    val twoDpPx = with(density) { 2.dp.toPx() }

    val currentOnLetterSelected by rememberUpdatedState(onLetterSelected)
    val currentOnDragStateChanged by rememberUpdatedState(onDragStateChanged)

    var isDragging by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var showBubble by remember { mutableStateOf(false) }
    var bubbleLetter by remember { mutableStateOf('#') }
    var barHeightPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(highlightedLetter, isDragging) {
        if (!isDragging) {
            val upper = highlightedLetter.uppercaseChar()
            val idx = if (upper in 'A'..'Z') AlphabetLetters.indexOf(upper) else 0
            if (idx != -1) {
                selectedIndex = idx
            }
        }
    }

    LaunchedEffect(isDragging) {
        if (isDragging) {
            showBubble = true
        } else {
            delay(300L)
            showBubble = false
        }
    }

    val normalColorArgb = AniPlayColors.OnSurfaceMuted.toArgb()
    val highlightColorArgb = AniPlayColors.Secondary.toArgb()

    val textPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
    }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.CenterEnd
    ) {
        val barHeight = minOf(440.dp, (maxHeight - 12.dp).coerceAtLeast(180.dp))

        // Floating Alphabet Preview Bubble aligned with active letter
        AnimatedVisibility(
            visible = showBubble && (visible || isDragging),
            enter = fadeIn(animationSpec = tween(durationMillis = 220, easing = IosMotion.EaseOut)),
            exit = fadeOut(animationSpec = tween(durationMillis = 320, easing = IosMotion.Standard)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 48.dp)
                .offset {
                    val available = (barHeightPx - topPadPx - bottomPadPx).coerceAtLeast(1f)
                    val itemHeight = available / AlphabetLetters.size
                    val safeIdx = selectedIndex.coerceIn(0, AlphabetLetters.lastIndex)
                    val letterCenterY = topPadPx + safeIdx * itemHeight + itemHeight / 2f
                    val offsetY = if (barHeightPx > 0f) {
                        (letterCenterY - barHeightPx / 2f).roundToInt()
                    } else {
                        0
                    }
                    IntOffset(0, offsetY)
                }
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .liquidGlass(
                        backdropState = backdropState,
                        isCircle = true,
                        style = LiquidGlassStyle.LensBubble
                    )
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = bubbleLetter.toString(),
                    color = AniPlayColors.SkyBlue,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Sidebar A-Z Pill
        AnimatedVisibility(
            visible = visible || isDragging,
            enter = fadeIn(animationSpec = tween(durationMillis = 220, easing = IosMotion.EaseOut)),
            exit = fadeOut(animationSpec = tween(durationMillis = 320, easing = IosMotion.Standard)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(22.dp)
                    .height(barHeight)
                    .liquidGlass(
                        backdropState = backdropState,
                        cornerRadius = 12.dp,
                        style = LiquidGlassStyle.Subtle
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .onSizeChanged { barHeightPx = it.height.toFloat() }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            isDragging = true
                            currentOnDragStateChanged(true)

                            var lastIdx = -1
                            fun updateFromY(y: Float) {
                                val available = (barHeightPx - topPadPx - bottomPadPx).coerceAtLeast(1f)
                                val itemH = available / AlphabetLetters.size
                                val idx = ((y - topPadPx) / itemH)
                                    .toInt()
                                    .coerceIn(0, AlphabetLetters.lastIndex)
                                if (idx != lastIdx) {
                                    lastIdx = idx
                                    selectedIndex = idx
                                    val ch = AlphabetLetters[idx]
                                    bubbleLetter = ch
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    currentOnLetterSelected(ch)
                                }
                            }

                            try {
                                updateFromY(down.position.y)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (!change.pressed) {
                                        change.consume()
                                        break
                                    }
                                    change.consume()
                                    updateFromY(change.position.y)
                                }
                            } finally {
                                isDragging = false
                                currentOnDragStateChanged(false)
                            }
                        }
                    }
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .fillMaxHeight()
                ) {
                    val availableHeight = (size.height - topPadPx - bottomPadPx).coerceAtLeast(1f)
                    val itemHeight = availableHeight / AlphabetLetters.size
                    val optimalTextSize = (itemHeight * 0.70f).coerceIn(minTextPx, maxTextPx)
                    textPaint.textSize = optimalTextSize
                    val centerX = size.width / 2f

                    for (i in AlphabetLetters.indices) {
                        val centerY = topPadPx + i * itemHeight + itemHeight / 2f
                        val isSelected = (i == selectedIndex)

                        if (isSelected && isDragging) {
                            val radius = (itemHeight / 2f).coerceAtMost(size.width / 2f - twoDpPx)
                            drawCircle(
                                color = Color(0x3300A3FF),
                                radius = radius,
                                center = Offset(centerX, centerY)
                            )
                        }

                        textPaint.color = if (isSelected) highlightColorArgb else normalColorArgb
                        textPaint.isFakeBoldText = isSelected
                        val fontMetrics = textPaint.fontMetrics
                        val baseline = centerY - (fontMetrics.descent + fontMetrics.ascent) / 2f

                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawText(
                                AlphabetLetters[i].toString(),
                                centerX,
                                baseline,
                                textPaint
                            )
                        }
                    }
                }
            }
        }
    }
}
