package com.personal.animeplayer.ui.compose.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/** iOS-like motion tokens: critically damped springs, soft ease-out curves, no overshoot. */
object IosMotion {
    val EaseOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val Sheet = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
    val Standard = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
    val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

    val fadeIn: FiniteAnimationSpec<Float> = tween(280, easing = EaseOut)
    val fadeOut: FiniteAnimationSpec<Float> = tween(220, easing = Standard)
    val scrimIn: FiniteAnimationSpec<Float> = tween(320, easing = EaseOut)
    val scrimOut: FiniteAnimationSpec<Float> = tween(260, easing = Standard)

    val sheetEnterOffset: FiniteAnimationSpec<IntOffset> =
        spring(dampingRatio = 1f, stiffness = 170f, visibilityThreshold = IntOffset.VisibilityThreshold)
    val sheetExitOffset: FiniteAnimationSpec<IntOffset> = tween(300, easing = Standard)
    val sheetEnterScale: FiniteAnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = 170f)

    val navEnterOffset: FiniteAnimationSpec<IntOffset> = tween(380, easing = Sheet)
    val navExitOffset: FiniteAnimationSpec<IntOffset> = tween(320, easing = Sheet)
    val navFadeIn: FiniteAnimationSpec<Float> = tween(300, delayMillis = 40, easing = EaseOut)
    val navFadeOut: FiniteAnimationSpec<Float> = tween(200, easing = Standard)
    val sizeChange: FiniteAnimationSpec<IntSize> =
        spring(dampingRatio = 1f, stiffness = 260f, visibilityThreshold = IntSize.VisibilityThreshold)

    val dialogEnterScale: FiniteAnimationSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 260f)
    val dialogExitScale: FiniteAnimationSpec<Float> = tween(200, easing = Standard)

    val controlsEnterOffset: FiniteAnimationSpec<IntOffset> = tween(340, easing = EaseOut)
    val controlsExitOffset: FiniteAnimationSpec<IntOffset> = tween(260, easing = Standard)

    val smoothFloat: FiniteAnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = 260f)
    val indicator: FiniteAnimationSpec<Float> = spring(dampingRatio = 0.88f, stiffness = 300f)
    val dragSettle: FiniteAnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = 380f)
    val page: FiniteAnimationSpec<Float> = tween(380, easing = Sheet)
    val color: FiniteAnimationSpec<Color> = tween(260, easing = EaseOut)

    val pressDown: AnimationSpec<Float> = tween(110, easing = EaseOut)
    val pressRelease: AnimationSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 380f)
}

/** iOS-style press feedback: quick soft press-in, gentle no-bounce release. */
@Composable
fun rememberIosPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f,
    enabled: Boolean = true,
    label: String = "iosPressScale"
): State<Float> {
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressed = enabled && isPressed
    return animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = if (pressed) IosMotion.pressDown else IosMotion.pressRelease,
        label = label
    )
}
