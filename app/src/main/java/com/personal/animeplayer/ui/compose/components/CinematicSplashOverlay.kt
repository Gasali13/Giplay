package com.personal.animeplayer.ui.compose.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.animeplayer.R
import com.personal.animeplayer.ui.compose.theme.IosMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

@Composable
fun CinematicSplashOverlay(
    loadingProgress: Float,
    statusText: String,
    isDataReady: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnFinished by rememberUpdatedState(onFinished)
    val density = LocalDensity.current
    val dp24Px = with(density) { 24.dp.toPx() }
    val dp16Px = with(density) { 16.dp.toPx() }

    val overlayAlpha = remember { Animatable(1f) }
    val logoScale = remember { Animatable(0.86f) }
    val logoAlpha = remember { Animatable(0f) }
    val glowScale = remember { Animatable(0.7f) }
    val glowAlpha = remember { Animatable(0f) }
    val titleAlpha = remember { Animatable(0f) }
    val titleOffsetY = remember { Animatable(dp24Px) }
    val subtitleAlpha = remember { Animatable(0f) }
    val subtitleOffsetY = remember { Animatable(dp16Px) }
    val progressContainerAlpha = remember { Animatable(0f) }
    val progressFill = remember { Animatable(0.05f) }
    val versionAlpha = remember { Animatable(0f) }

    var introCompleted by remember { mutableStateOf(false) }
    var exitStarted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val jobs = listOf(
            launch { logoScale.animateTo(1f, IosMotion.smoothFloat) },
            launch { logoAlpha.animateTo(1f, tween(420, easing = IosMotion.EaseOut)) },
            launch { glowScale.animateTo(1f, tween(420, easing = IosMotion.EaseOut)) },
            launch { glowAlpha.animateTo(0.85f, tween(420, easing = IosMotion.EaseOut)) },
            launch {
                delay(70)
                titleAlpha.animateTo(1f, tween(420, easing = IosMotion.EaseOut))
            },
            launch {
                delay(70)
                titleOffsetY.animateTo(0f, tween(420, easing = IosMotion.EaseOut))
            },
            launch {
                delay(120)
                subtitleAlpha.animateTo(1f, tween(420, easing = IosMotion.EaseOut))
            },
            launch {
                delay(120)
                subtitleOffsetY.animateTo(0f, tween(420, easing = IosMotion.EaseOut))
            },
            launch {
                delay(140)
                progressContainerAlpha.animateTo(1f, tween(280, easing = IosMotion.EaseOut))
            },
            launch {
                delay(180)
                versionAlpha.animateTo(0.85f, tween(320, easing = IosMotion.EaseOut))
            }
        )
        jobs.joinAll()
        introCompleted = true
    }

    LaunchedEffect(introCompleted, exitStarted) {
        if (!introCompleted || exitStarted) return@LaunchedEffect
        while (isActive && !exitStarted) {
            glowScale.animateTo(1.06f, tween(1200, easing = IosMotion.EaseInOut))
            if (!isActive || exitStarted) break
            glowScale.animateTo(1.0f, tween(1200, easing = IosMotion.EaseInOut))
        }
    }

    LaunchedEffect(loadingProgress) {
        val target = loadingProgress.coerceIn(0.05f, 1f)
        if (target > progressFill.value) {
            progressFill.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = 260, easing = IosMotion.EaseOut)
            )
        }
    }

    LaunchedEffect(introCompleted, isDataReady) {
        if (introCompleted && isDataReady && !exitStarted) {
            exitStarted = true
            progressFill.animateTo(1f, tween(260, easing = IosMotion.EaseOut))
            val exitEasing = IosMotion.Standard
            val exitJobs = listOf(
                launch { logoScale.animateTo(1.08f, tween(380, easing = exitEasing)) },
                launch { logoAlpha.animateTo(0f, tween(380, easing = exitEasing)) },
                launch { glowScale.animateTo(1.12f, tween(380, easing = exitEasing)) },
                launch { glowAlpha.animateTo(0f, tween(380, easing = exitEasing)) },
                launch { titleAlpha.animateTo(0f, tween(380, easing = exitEasing)) },
                launch { subtitleAlpha.animateTo(0f, tween(380, easing = exitEasing)) },
                launch { progressContainerAlpha.animateTo(0f, tween(380, easing = exitEasing)) },
                launch { versionAlpha.animateTo(0f, tween(380, easing = exitEasing)) },
                launch { overlayAlpha.animateTo(0f, tween(380, easing = exitEasing)) }
            )
            exitJobs.joinAll()
            currentOnFinished()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha.value }
            .background(Color(0xFF0E0F12))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(160.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .graphicsLayer {
                            alpha = glowAlpha.value
                            scaleX = glowScale.value
                            scaleY = glowScale.value
                        }
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0x4D7C4DFF),
                                    Color(0x1E00E5FF),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Image(
                    painter = painterResource(R.drawable.ic_app_logo),
                    contentDescription = stringResource(R.string.cd_app_logo),
                    modifier = Modifier
                        .size(88.dp)
                        .graphicsLayer {
                            alpha = logoAlpha.value
                            scaleX = logoScale.value
                            scaleY = logoScale.value
                        }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.app_name),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = titleAlpha.value
                    translationY = titleOffsetY.value
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.splash_subtitle),
                color = Color(0xFF8E8E93),
                fontSize = 12.sp,
                letterSpacing = 1.4.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = subtitleAlpha.value
                    translationY = subtitleOffsetY.value
                }
            )

            Spacer(modifier = Modifier.height(28.dp))

            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(5.dp)
                    .graphicsLayer { alpha = progressContainerAlpha.value }
                    .clip(RoundedCornerShape(100.dp))
                    .background(Color(0xFF212631))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .graphicsLayer {
                            scaleX = progressFill.value
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .clip(RoundedCornerShape(100.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color(0xFF00E5FF), Color(0xFF7C4DFF))
                            )
                        )
                )
            }

            if (statusText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = statusText,
                    color = Color(0xFF9AA3B5),
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer { alpha = progressContainerAlpha.value }
                )
            }
        }

        Text(
            text = stringResource(R.string.splash_slogan),
            color = Color(0xFF707585),
            fontSize = 11.sp,
            letterSpacing = 1.6.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 36.dp)
                .graphicsLayer { alpha = versionAlpha.value }
        )
    }
}
