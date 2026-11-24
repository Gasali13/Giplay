package com.personal.animeplayer.ui.compose.components

import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.personal.animeplayer.ui.compose.theme.IosMotion
import com.personal.animeplayer.ui.compose.theme.rememberIosPressScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.ceil
import kotlin.math.roundToInt

@Immutable
data class LiquidGlassCorners(
    val topStart: Dp,
    val topEnd: Dp,
    val bottomEnd: Dp,
    val bottomStart: Dp
) {
    constructor(all: Dp) : this(all, all, all, all)
}

@Immutable
data class LiquidGlassStyle(
    val blurRadius: Dp = 18.dp,
    val bezelWidth: Dp = 10.dp,
    val refractionStrength: Dp = 6.dp,
    val chromaticAberration: Dp = 1.2.dp,
    val tintColor: Color = Color(0x66121620),
    val highlightStrength: Float = 0.82f,
    val vibrancy: Float = 1.20f,
    val grainStrength: Float = 0.0f
) {
    companion object {
        val Default = LiquidGlassStyle()

        val CircleButton = LiquidGlassStyle(
            blurRadius = 6.dp,
            bezelWidth = 11.dp,
            refractionStrength = 10.dp,
            chromaticAberration = 1.8.dp,
            tintColor = Color(0x4D101622),
            highlightStrength = 0.92f,
            vibrancy = 1.30f,
            grainStrength = 0.0f
        )

        val PlayerControl = LiquidGlassStyle(
            blurRadius = 11.dp,
            bezelWidth = 9.dp,
            refractionStrength = 7.dp,
            chromaticAberration = 1.4.dp,
            tintColor = Color(0x540E131D),
            highlightStrength = 0.90f,
            vibrancy = 1.28f,
            grainStrength = 0.015f
        )

        val TabBar = LiquidGlassStyle(
            blurRadius = 20.dp,
            bezelWidth = 11.dp,
            refractionStrength = 6.5.dp,
            chromaticAberration = 1.2.dp,
            tintColor = Color(0x5E141A26),
            highlightStrength = 0.85f,
            vibrancy = 1.20f
        )

        val Capsule = LiquidGlassStyle(
            blurRadius = 18.dp,
            bezelWidth = 10.dp,
            refractionStrength = 5.5.dp,
            chromaticAberration = 1.1.dp,
            tintColor = Color(0x64141A26),
            highlightStrength = 0.80f,
            vibrancy = 1.18f
        )

        val LensBubble = LiquidGlassStyle(
            blurRadius = 5.dp,
            bezelWidth = 18.dp,
            refractionStrength = 14.dp,
            chromaticAberration = 2.2.dp,
            tintColor = Color(0x480B1526),
            highlightStrength = 0.95f,
            vibrancy = 1.32f,
            grainStrength = 0.0f
        )

        val Subtle = LiquidGlassStyle(
            blurRadius = 12.dp,
            bezelWidth = 5.dp,
            refractionStrength = 3.5.dp,
            chromaticAberration = 0.8.dp,
            tintColor = Color(0x52101524),
            highlightStrength = 0.65f,
            vibrancy = 1.15f
        )

        val Sheet = LiquidGlassStyle(
            blurRadius = 22.dp,
            bezelWidth = 12.dp,
            refractionStrength = 6.dp,
            chromaticAberration = 1.0.dp,
            tintColor = Color(0x8E101520),
            highlightStrength = 0.85f,
            vibrancy = 1.24f,
            grainStrength = 0.028f
        )
    }
}

@Stable
class LiquidGlassBackdropState(
    var scrollSignal: () -> Int
) {
    var backdropLayer: GraphicsLayer? = null
    var rootPositionInWindow by mutableStateOf(Offset.Zero)
    var isReady by mutableStateOf(false)
}

@Composable
fun rememberLiquidGlassBackdropState(
    scrollSignal: () -> Int = { 0 }
): LiquidGlassBackdropState {
    val state = remember { LiquidGlassBackdropState(scrollSignal) }
    state.scrollSignal = scrollSignal
    return state
}

/**
 * Captures the underlying scrollable backdrop into an offscreen [GraphicsLayer] so sibling
 * floating UI controls can sample, blur, and optically refract live content behind them.
 */
@Composable
fun Modifier.liquidGlassBackdrop(state: LiquidGlassBackdropState): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return this
    }
    val layer = rememberGraphicsLayer()
    return this
        .onGloballyPositioned { coords ->
            state.rootPositionInWindow = coords.positionInWindow()
        }
        .drawWithContent {
            if (size.width >= 1f && size.height >= 1f) {
                layer.record {
                    drawRect(Color.Black)
                    this@drawWithContent.drawContent()
                }
                state.backdropLayer = layer
                if (!state.isReady) {
                    state.isReady = true
                }
                drawLayer(layer)
            } else {
                drawContent()
            }
        }
}

private const val LIQUID_GLASS_REFRACTION_AGSL = """
uniform shader content;
uniform float2 uOrigin;
uniform float2 uHalfSize;
uniform float2 uLayerSize;
uniform float4 uRadii;
uniform float uBezelWidth;
uniform float uRefraction;
uniform float uChromatic;
uniform float4 uTintColor;
uniform float uHighlight;
uniform float uVibrancy;
uniform float uPressProgress;
uniform float uGrain;

float sdRoundedBox(float2 p, float2 b, float4 radii) {
    float rTop = (p.x > 0.0) ? radii.y : radii.x;
    float rBot = (p.x > 0.0) ? radii.z : radii.w;
    float r = (p.y > 0.0) ? rBot : rTop;
    r = min(r, min(b.x, b.y));
    float2 q = abs(p) - b + float2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;
}

half4 main(float2 fragCoord) {
    float2 center = uOrigin + uHalfSize;
    float2 p = fragCoord - center;
    float d = sdRoundedBox(p, uHalfSize, uRadii);

    if (d > 0.75) {
        return half4(0.0);
    }

    float2 grad = float2(
        sdRoundedBox(p + float2(1.0, 0.0), uHalfSize, uRadii) - sdRoundedBox(p - float2(1.0, 0.0), uHalfSize, uRadii),
        sdRoundedBox(p + float2(0.0, 1.0), uHalfSize, uRadii) - sdRoundedBox(p - float2(0.0, 1.0), uHalfSize, uRadii)
    );
    float lenG = length(grad);
    float2 normal = (lenG > 0.0001) ? (grad / lenG) : float2(0.0, -1.0);

    float distFromEdge = max(-d, 0.0);
    float bezel = max(uBezelWidth, 1.0);
    float t = clamp(1.0 - distFromEdge / bezel, 0.0, 1.0);
    float rimCurve = t * t * (3.0 - 2.0 * t);
    float domeShift = 1.0 - smoothstep(0.0, max(min(uHalfSize.x, uHalfSize.y), 1.0), distFromEdge);

    float shift = (rimCurve * 0.85 + domeShift * 0.12) * uRefraction;
    float2 baseSample = fragCoord - normal * shift;
    float disp = (rimCurve + 0.12 * domeShift) * uChromatic;

    float2 minBounds = float2(1.0);
    float2 maxBounds = max(uLayerSize - float2(1.0), float2(2.0));

    half4 colR = content.eval(clamp(baseSample + normal * disp, minBounds, maxBounds));
    half4 colG = content.eval(clamp(baseSample, minBounds, maxBounds));
    half4 colB = content.eval(clamp(baseSample - normal * disp, minBounds, maxBounds));

    half3 bgRgb = half3(colR.r, colG.g, colB.b);

    half luma = dot(bgRgb, half3(0.299, 0.587, 0.114));
    bgRgb = clamp(mix(half3(luma), bgRgb, half(uVibrancy)), half3(0.0), half3(1.0));

    bgRgb = mix(bgRgb, half3(uTintColor.rgb), half(uTintColor.a));
    bgRgb += half3(0.02 + 0.14 * uPressProgress);

    float2 lightDir = normalize(float2(-0.65, -0.76));
    float lightAlign = dot(normal, lightDir);
    float topLeftArc = pow(clamp(lightAlign, 0.0, 1.0), 1.35);
    float botRightArc = pow(clamp(-lightAlign, 0.0, 1.0), 1.8) * 0.52;

    float rimMask = smoothstep(2.0, 0.0, distFromEdge);
    float rimIntensity = rimMask * (0.18 + topLeftArc * 0.85 + botRightArc + 0.35 * uPressProgress) * uHighlight;
    half3 rimTint = mix(half3(0.95, 0.98, 1.0), half3(0.55, 0.84, 1.0), half(clamp(botRightArc * 1.4, 0.0, 1.0)));
    bgRgb += rimTint * half(rimIntensity * 0.62);

    float topArc = clamp(-p.y / max(uHalfSize.y, 1.0), 0.0, 1.0);
    float meniscus = pow(topArc, 2.3) * smoothstep(bezel * 1.35, 1.0, distFromEdge) * 0.085 * uHighlight;
    bgRgb += half3(0.90, 0.96, 1.0) * half(meniscus);

    float grain = (fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453) - 0.5) * uGrain;
    bgRgb += half3(grain);

    float alpha = clamp(0.75 - d, 0.0, 1.0);
    bgRgb = clamp(bgRgb, half3(0.0), half3(1.0));
    return half4(bgRgb * half(alpha), half(alpha));
}
"""

private const val LIQUID_GLASS_SURFACE_AGSL = """
uniform float2 uHalfSize;
uniform float4 uRadii;
uniform float4 uTintColor;
uniform float uHighlight;
uniform float uPressProgress;
uniform float uGrain;

float sdRoundedBox(float2 p, float2 b, float4 radii) {
    float rTop = (p.x > 0.0) ? radii.y : radii.x;
    float rBot = (p.x > 0.0) ? radii.z : radii.w;
    float r = (p.y > 0.0) ? rBot : rTop;
    r = min(r, min(b.x, b.y));
    float2 q = abs(p) - b + float2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;
}

half4 main(float2 fragCoord) {
    float2 p = fragCoord - uHalfSize;
    float d = sdRoundedBox(p, uHalfSize, uRadii);

    if (d > 0.75) {
        return half4(0.0);
    }

    float2 grad = float2(
        sdRoundedBox(p + float2(1.0, 0.0), uHalfSize, uRadii) - sdRoundedBox(p - float2(1.0, 0.0), uHalfSize, uRadii),
        sdRoundedBox(p + float2(0.0, 1.0), uHalfSize, uRadii) - sdRoundedBox(p - float2(0.0, 1.0), uHalfSize, uRadii)
    );
    float lenG = length(grad);
    float2 normal = (lenG > 0.0001) ? (grad / lenG) : float2(0.0, -1.0);

    float distFromEdge = max(-d, 0.0);
    float minDim = max(min(uHalfSize.x, uHalfSize.y), 1.0);
    float bezel = min(minDim * 0.55, 16.0);
    float t = clamp(1.0 - distFromEdge / max(bezel, 1.0), 0.0, 1.0);
    float fresnel = pow(t, 2.1);

    float2 lightDir = normalize(float2(-0.65, -0.76));
    float lightAlign = dot(normal, lightDir);
    float topLeftArc = pow(clamp(lightAlign, 0.0, 1.0), 1.35);
    float botRightArc = pow(clamp(-lightAlign, 0.0, 1.0), 1.8) * 0.50;

    float rimMask = smoothstep(2.2, 0.0, distFromEdge);
    float rimGlow = rimMask * (0.20 + topLeftArc * 0.85 + botRightArc + 0.35 * uPressProgress) * uHighlight;

    float topArc = clamp(-p.y / max(uHalfSize.y, 1.0), 0.0, 1.0);
    float meniscus = pow(topArc, 2.2) * (0.09 + 0.14 * fresnel) * uHighlight;

    half3 baseRgb = half3(uTintColor.rgb);
    half3 innerSheen = half3(0.70, 0.84, 1.0) * half((fresnel * 0.16 + meniscus) * uHighlight);
    half3 rimTint = mix(half3(0.96, 0.98, 1.0), half3(0.55, 0.85, 1.0), half(clamp(botRightArc * 1.5, 0.0, 1.0)));
    half3 pressGlow = half3(0.85, 0.94, 1.0) * half(0.18 * uPressProgress);
    float grain = (fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453) - 0.5) * uGrain;

    half3 outRgb = clamp(baseRgb + innerSheen + rimTint * half(rimGlow * 0.78) + pressGlow + half3(grain), half3(0.0), half3(1.0));
    float bodyAlpha = clamp(uTintColor.a + fresnel * 0.12 + rimMask * 0.30 * uHighlight + uPressProgress * 0.16, 0.0, 1.0);
    float shapeAlpha = clamp(0.75 - d, 0.0, 1.0);
    float finalAlpha = bodyAlpha * shapeAlpha;

    return half4(outRgb * half(finalAlpha), half(finalAlpha));
}
"""

private data class RefractionShaderKey(
    val width: Int,
    val height: Int,
    val pad: Int,
    val topLeft: Float,
    val topRight: Float,
    val bottomRight: Float,
    val bottomLeft: Float,
    val style: LiquidGlassStyle,
    val pressProgress: Float
)

private data class SurfaceShaderKey(
    val width: Float,
    val height: Float,
    val topLeft: Float,
    val topRight: Float,
    val bottomRight: Float,
    val bottomLeft: Float,
    val tintArgb: Long,
    val highlightStrength: Float,
    val pressProgress: Float,
    val grainStrength: Float
)

private fun resolveCornerRadiiPx(
    width: Float,
    height: Float,
    isCircle: Boolean,
    cornerRadiusPx: Float,
    cornersPx: FloatArray?
): FloatArray {
    if (isCircle) {
        val r = minOf(width, height) * 0.5f
        return floatArrayOf(r, r, r, r)
    }
    return cornersPx ?: floatArrayOf(cornerRadiusPx, cornerRadiusPx, cornerRadiusPx, cornerRadiusPx)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AgslRefractionCache {
    var shader: RuntimeShader? = null
    var shaderFailed = false
    var lastKey: RefractionShaderKey? = null
    var cachedComposeEffect: androidx.compose.ui.graphics.RenderEffect? = null

    fun getOrCreateEffect(
        key: RefractionShaderKey,
        blurRadiusPx: Float,
        bezelWidthPx: Float,
        refractionPx: Float,
        chromaticPx: Float
    ): androidx.compose.ui.graphics.RenderEffect? {
        if (shaderFailed) return null
        if (key == lastKey && cachedComposeEffect != null) {
            return cachedComposeEffect
        }
        return try {
            val runtimeShader = shader ?: RuntimeShader(LIQUID_GLASS_REFRACTION_AGSL).also { shader = it }
            val padF = key.pad.toFloat()
            val layerW = (key.width + key.pad * 2).toFloat()
            val layerH = (key.height + key.pad * 2).toFloat()
            val tint = key.style.tintColor

            runtimeShader.setFloatUniform("uOrigin", padF, padF)
            runtimeShader.setFloatUniform("uHalfSize", key.width * 0.5f, key.height * 0.5f)
            runtimeShader.setFloatUniform("uLayerSize", layerW, layerH)
            runtimeShader.setFloatUniform(
                "uRadii",
                key.topLeft,
                key.topRight,
                key.bottomRight,
                key.bottomLeft
            )
            runtimeShader.setFloatUniform("uBezelWidth", bezelWidthPx)
            runtimeShader.setFloatUniform("uRefraction", refractionPx)
            runtimeShader.setFloatUniform("uChromatic", chromaticPx)
            runtimeShader.setFloatUniform("uTintColor", tint.red, tint.green, tint.blue, tint.alpha)
            runtimeShader.setFloatUniform("uHighlight", key.style.highlightStrength)
            runtimeShader.setFloatUniform("uVibrancy", key.style.vibrancy)
            runtimeShader.setFloatUniform("uPressProgress", key.pressProgress)
            runtimeShader.setFloatUniform("uGrain", key.style.grainStrength)

            val safeBlur = blurRadiusPx.coerceAtLeast(0.5f)
            val blurEffect = RenderEffect.createBlurEffect(safeBlur, safeBlur, Shader.TileMode.CLAMP)
            val shaderEffect = RenderEffect.createRuntimeShaderEffect(runtimeShader, "content")
            val chained = RenderEffect.createChainEffect(shaderEffect, blurEffect).asComposeRenderEffect()

            lastKey = key
            cachedComposeEffect = chained
            chained
        } catch (_: Throwable) {
            shaderFailed = true
            cachedComposeEffect = null
            null
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AgslSurfaceCache {
    var shader: RuntimeShader? = null
    var shaderFailed = false
    var lastKey: SurfaceShaderKey? = null
    var cachedBrush: ShaderBrush? = null

    fun getOrCreateBrush(
        key: SurfaceShaderKey,
        tintColor: Color
    ): ShaderBrush? {
        if (shaderFailed) return null
        if (key == lastKey && cachedBrush != null) {
            return cachedBrush
        }
        return try {
            val runtimeShader = shader ?: RuntimeShader(LIQUID_GLASS_SURFACE_AGSL).also { shader = it }
            runtimeShader.setFloatUniform("uHalfSize", key.width * 0.5f, key.height * 0.5f)
            runtimeShader.setFloatUniform(
                "uRadii",
                key.topLeft,
                key.topRight,
                key.bottomRight,
                key.bottomLeft
            )
            runtimeShader.setFloatUniform(
                "uTintColor",
                tintColor.red,
                tintColor.green,
                tintColor.blue,
                tintColor.alpha
            )
            runtimeShader.setFloatUniform("uHighlight", key.highlightStrength)
            runtimeShader.setFloatUniform("uPressProgress", key.pressProgress)
            runtimeShader.setFloatUniform("uGrain", key.grainStrength)

            val brush = ShaderBrush(runtimeShader)
            lastKey = key
            cachedBrush = brush
            brush
        } catch (_: Throwable) {
            shaderFailed = true
            cachedBrush = null
            null
        }
    }
}

private const val LIQUID_GLASS_BAR_AGSL = """
uniform shader content;
uniform float2 uOrigin;
uniform float2 uSize;
uniform float2 uLayerSize;
uniform float uBottomFade;
uniform float uVibrancy;

half4 main(float2 fragCoord) {
    float2 localPos = fragCoord - uOrigin;
    if (localPos.x < 0.0 || localPos.y < 0.0 || localPos.x > uSize.x || localPos.y > uSize.y) {
        return half4(0.0);
    }

    float2 samplePos = clamp(fragCoord, float2(1.0), max(uLayerSize - float2(1.0), float2(2.0)));
    half4 sampled = content.eval(samplePos);
    half3 bgRgb = half3(sampled.r, sampled.g, sampled.b);

    half luma = dot(bgRgb, half3(0.299, 0.587, 0.114));
    bgRgb = clamp(mix(half3(luma), bgRgb, half(uVibrancy)), half3(0.0), half3(1.0));

    float yFrac = clamp(localPos.y / max(uSize.y, 1.0), 0.0, 1.0);
    float darkCurve = pow(max(yFrac, 0.0001), 0.85);
    float darkMix = mix(0.92, 0.56, darkCurve);

    float distFromBottom = uSize.y - localPos.y;
    float t = (uBottomFade > 0.5) ? clamp(distFromBottom / uBottomFade, 0.0, 1.0) : 1.0;
    float smoothFade = t * t * (3.0 - 2.0 * t);
    float fade = pow(max(smoothFade, 0.0001), 1.35);

    float tailDark = mix(0.76, darkMix, smoothFade);
    bgRgb = mix(bgRgb, half3(0.015, 0.02, 0.035), half(tailDark));
    return half4(bgRgb * half(fade), half(fade));
}
"""

private data class BarBlurKey(
    val width: Int,
    val height: Int,
    val pad: Int,
    val bottomFade: Float,
    val vibrancy: Float,
    val blurPx: Float
)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AgslBarBlurCache {
    var shader: RuntimeShader? = null
    var shaderFailed = false
    var lastKey: BarBlurKey? = null
    var cachedEffect: androidx.compose.ui.graphics.RenderEffect? = null

    fun getOrCreateEffect(key: BarBlurKey): androidx.compose.ui.graphics.RenderEffect? {
        if (shaderFailed) return null
        if (key == lastKey && cachedEffect != null) {
            return cachedEffect
        }
        return try {
            val runtimeShader = shader ?: RuntimeShader(LIQUID_GLASS_BAR_AGSL).also { shader = it }
            val padF = key.pad.toFloat()
            val layerW = (key.width + key.pad * 2).toFloat()
            val layerH = (key.height + key.pad * 2).toFloat()

            runtimeShader.setFloatUniform("uOrigin", padF, padF)
            runtimeShader.setFloatUniform("uSize", key.width.toFloat(), key.height.toFloat())
            runtimeShader.setFloatUniform("uLayerSize", layerW, layerH)
            runtimeShader.setFloatUniform("uBottomFade", key.bottomFade)
            runtimeShader.setFloatUniform("uVibrancy", key.vibrancy)

            val safeBlur = key.blurPx.coerceAtLeast(0.5f)
            val blurEffect = RenderEffect.createBlurEffect(safeBlur, safeBlur, Shader.TileMode.CLAMP)
            val shaderEffect = RenderEffect.createRuntimeShaderEffect(runtimeShader, "content")
            val chained = RenderEffect.createChainEffect(shaderEffect, blurEffect).asComposeRenderEffect()

            lastKey = key
            cachedEffect = chained
            chained
        } catch (_: Throwable) {
            shaderFailed = true
            cachedEffect = null
            null
        }
    }
}

private fun DrawScope.drawHeaderBlurFallback(
    bottomFadePx: Float,
    bottomExtendPx: Float
) {
    val totalH = (size.height + bottomExtendPx).coerceAtLeast(1f)
    val fadeStartFrac = ((totalH - bottomFadePx) / totalH).coerceIn(0f, 0.98f)
    val midFadeFrac = ((fadeStartFrac + 1f) * 0.5f).coerceIn(fadeStartFrac, 1f)
    drawRect(
        brush = Brush.verticalGradient(
            0.0f to Color(0xF5040509),
            (fadeStartFrac * 0.65f) to Color(0xD805070C),
            fadeStartFrac to Color(0xB806080E),
            midFadeFrac to Color(0x5206080E),
            1.0f to Color.Transparent
        ),
        size = Size(size.width, totalH)
    )
}

/**
 * Applies a real-time frosted GPU blur (progressive iPhone iOS 26 header style) across a
 * full-width header bar, blurring live scrolling backdrop content underneath with a soft
 * dark downward gradient and bottom dissolve feather. Falls back to a smooth dark translucent
 * gradient on Android 12 and below.
 */
@Composable
fun Modifier.liquidGlassHeaderBlur(
    backdropState: LiquidGlassBackdropState,
    blurRadius: Dp = 26.dp,
    bottomFadeHeight: Dp = 46.dp,
    bottomExtend: Dp = 18.dp,
    vibrancy: Float = 1.22f
): Modifier {
    val density = LocalDensity.current
    val blurPx = with(density) { blurRadius.toPx() }
    val bottomFadePx = with(density) { bottomFadeHeight.toPx() }
    val bottomExtendPx = with(density) { bottomExtend.toPx() }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return this.drawBehind {
            if (size.width >= 2f && size.height >= 2f) {
                drawHeaderBlurFallback(bottomFadePx, bottomExtendPx)
            }
        }
    }

    val padPx = remember(blurPx) {
        ceil(blurPx * 0.6f).toInt().coerceIn(16, 48)
    }
    var elementPosInWindow by remember { mutableStateOf(Offset.Zero) }
    val headerLayer = rememberGraphicsLayer()
    val cache = remember { AgslBarBlurCache() }

    return this
        .onGloballyPositioned { coords ->
            elementPosInWindow = coords.positionInWindow()
        }
        .drawWithContent {
            backdropState.scrollSignal()

            val w = size.width.toInt()
            val h = size.height.toInt()
            if (w < 4 || h < 4) {
                drawContent()
                return@drawWithContent
            }

            val drawH = (size.height + bottomExtendPx).roundToInt().coerceAtLeast(h)
            val backdropLayer = backdropState.backdropLayer
            if (backdropState.isReady && backdropLayer != null) {
                val ds = 0.5f
                val scaledW = (w * ds).roundToInt().coerceAtLeast(4)
                val scaledH = (drawH * ds).roundToInt().coerceAtLeast(4)
                val scaledPad = (padPx * ds).roundToInt().coerceIn(8, 28)
                val scaledBlur = (blurPx * ds).coerceAtLeast(0.5f)
                val scaledFade = bottomFadePx * ds

                val effect = cache.getOrCreateEffect(
                    key = BarBlurKey(
                        width = scaledW,
                        height = scaledH,
                        pad = scaledPad,
                        bottomFade = scaledFade,
                        vibrancy = vibrancy,
                        blurPx = scaledBlur
                    )
                )
                if (effect != null) {
                    val relOffset = elementPosInWindow - backdropState.rootPositionInWindow
                    val targetSize = IntSize(scaledW + scaledPad * 2, scaledH + scaledPad * 2)
                    headerLayer.record(size = targetSize) {
                        scale(scaleX = ds, scaleY = ds, pivot = Offset.Zero) {
                            translate(
                                left = -relOffset.x + scaledPad / ds,
                                top = -relOffset.y + scaledPad / ds
                            ) {
                                drawLayer(backdropLayer)
                            }
                        }
                    }
                    headerLayer.renderEffect = effect
                    scale(
                        scaleX = w.toFloat() / scaledW.toFloat(),
                        scaleY = drawH.toFloat() / scaledH.toFloat(),
                        pivot = Offset.Zero
                    ) {
                        translate(left = -scaledPad.toFloat(), top = -scaledPad.toFloat()) {
                            drawLayer(headerLayer)
                        }
                    }
                    drawContent()
                    return@drawWithContent
                }
            }

            drawHeaderBlurFallback(bottomFadePx, bottomExtendPx)
            drawContent()
        }
}

private fun DrawScope.drawGlassFallback(
    radii: FloatArray,
    tintColor: Color,
    highlightStrength: Float,
    pressProgress: Float
) {
    val rectPath = Path().apply {
        addRoundRect(
            RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                topLeftCornerRadius = CornerRadius(radii[0], radii[0]),
                topRightCornerRadius = CornerRadius(radii[1], radii[1]),
                bottomRightCornerRadius = CornerRadius(radii[2], radii[2]),
                bottomLeftCornerRadius = CornerRadius(radii[3], radii[3])
            )
        )
    }

    val effectiveAlpha = (tintColor.alpha + pressProgress * 0.14f).coerceIn(0f, 1f)
    drawPath(path = rectPath, color = tintColor.copy(alpha = effectiveAlpha))

    val meniscusAlpha = (0.14f * highlightStrength + 0.08f * pressProgress).coerceIn(0f, 1f)
    drawPath(
        path = rectPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = meniscusAlpha),
                Color.Transparent
            ),
            endY = size.height * 0.55f
        )
    )

    val topRimAlpha = (0.42f * highlightStrength + 0.18f * pressProgress).coerceIn(0f, 1f)
    val botRimAlpha = (0.20f * highlightStrength).coerceIn(0f, 1f)
    drawPath(
        path = rectPath,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = topRimAlpha),
                Color(0xFF93C5FD).copy(alpha = 0.08f * highlightStrength),
                Color(0xFFBAE6FD).copy(alpha = botRimAlpha)
            ),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        ),
        style = Stroke(width = 1.25.dp.toPx())
    )
}

/**
 * Real-time iPhone Liquid Glass modifier with live GPU backdrop blur, convex optical bezel
 * refraction, RGB chromatic dispersion, and specular rim lighting on Android 13+.
 * Automatically falls back to [liquidGlassSurface] / Canvas frosted glass when no ready backdrop
 * is attached or on Android 12 and below.
 */
@Composable
fun Modifier.liquidGlass(
    backdropState: LiquidGlassBackdropState?,
    cornerRadius: Dp = 20.dp,
    isCircle: Boolean = false,
    cornerRadii: LiquidGlassCorners? = null,
    style: LiquidGlassStyle = LiquidGlassStyle.Default,
    pressProgress: Float = 0f
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || backdropState == null) {
        return this.liquidGlassSurface(
            cornerRadius = cornerRadius,
            isCircle = isCircle,
            cornerRadii = cornerRadii,
            tintColor = style.tintColor,
            highlightStrength = style.highlightStrength,
            pressProgress = pressProgress
        )
    }

    val density = LocalDensity.current
    val blurPx = with(density) { style.blurRadius.toPx() }
    val bezelPx = with(density) { style.bezelWidth.toPx() }
    val refractionPx = with(density) { style.refractionStrength.toPx() }
    val chromaticPx = with(density) { style.chromaticAberration.toPx() }
    val cornerPx = with(density) { cornerRadius.toPx() }
    val cornersPx = remember(cornerRadii, density) {
        cornerRadii?.let {
            with(density) {
                floatArrayOf(
                    it.topStart.toPx(),
                    it.topEnd.toPx(),
                    it.bottomEnd.toPx(),
                    it.bottomStart.toPx()
                )
            }
        }
    }
    val padPx = remember(blurPx, refractionPx, chromaticPx) {
        ceil(maxOf(refractionPx + chromaticPx + 6f, blurPx * 0.75f + 6f)).toInt().coerceIn(8, 64)
    }

    var elementPosInWindow by remember { mutableStateOf(Offset.Zero) }
    val glassLayer = rememberGraphicsLayer()
    val cache = remember { AgslRefractionCache() }

    return this
        .onGloballyPositioned { coords ->
            elementPosInWindow = coords.positionInWindow()
        }
        .drawWithContent {
            // Draw-phase-only subscription to backdrop scroll state for 60-120fps with 0 recompositions.
            backdropState.scrollSignal()

            val w = size.width.toInt()
            val h = size.height.toInt()
            if (w < 4 || h < 4) {
                drawContent()
                return@drawWithContent
            }

            val radii = resolveCornerRadiiPx(
                width = size.width,
                height = size.height,
                isCircle = isCircle,
                cornerRadiusPx = cornerPx,
                cornersPx = cornersPx
            )

            val backdropLayer = backdropState.backdropLayer
            if (backdropState.isReady && backdropLayer != null) {
                val key = RefractionShaderKey(
                    width = w,
                    height = h,
                    pad = padPx,
                    topLeft = radii[0],
                    topRight = radii[1],
                    bottomRight = radii[2],
                    bottomLeft = radii[3],
                    style = style,
                    pressProgress = pressProgress
                )
                val effect = cache.getOrCreateEffect(
                    key = key,
                    blurRadiusPx = blurPx,
                    bezelWidthPx = bezelPx,
                    refractionPx = refractionPx,
                    chromaticPx = chromaticPx
                )
                if (effect != null) {
                    val relOffset = elementPosInWindow - backdropState.rootPositionInWindow
                    val targetSize = IntSize(w + padPx * 2, h + padPx * 2)
                    glassLayer.record(size = targetSize) {
                        translate(left = -relOffset.x + padPx, top = -relOffset.y + padPx) {
                            drawLayer(backdropLayer)
                        }
                    }
                    glassLayer.renderEffect = effect
                    translate(left = -padPx.toFloat(), top = -padPx.toFloat()) {
                        drawLayer(glassLayer)
                    }
                    drawContent()
                    return@drawWithContent
                }
            }

            drawGlassFallback(
                radii = radii,
                tintColor = style.tintColor,
                highlightStrength = style.highlightStrength,
                pressProgress = pressProgress
            )
            drawContent()
        }
}

/**
 * Snapshot frame and window-space geometry of an external hardware [android.view.SurfaceView]
 * captured asynchronously (e.g., via PixelCopy) so Compose [liquidGlassBitmapBackdrop] can apply
 * real-time frosted GPU blur, refraction, and grain behind floating panels.
 */
@Immutable
data class VideoBackdropFrame(
    val bitmap: ImageBitmap,
    val surfacePosInWindow: Offset,
    val surfaceSize: IntSize,
    val frameToken: Int
)

@Composable
fun rememberSurfaceVideoBackdrop(
    activeSurfaceView: SurfaceView?,
    isActive: Boolean,
    maxDimPx: Float = 320f,
    frameIntervalMs: Long = 66L,
    releaseDelayMs: Long = 360L
): () -> VideoBackdropFrame? {
    val frameState = remember { mutableStateOf<VideoBackdropFrame?>(null) }

    LaunchedEffect(isActive, activeSurfaceView) {
        if (!isActive) {
            delay(releaseDelayMs)
            frameState.value = null
            return@LaunchedEffect
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activeSurfaceView == null) {
            frameState.value = null
            return@LaunchedEffect
        }

        val handlerThread = HandlerThread("PlayerBlurCopy").apply { start() }
        val copyHandler = Handler(handlerThread.looper)
        var buffer0: Bitmap? = null
        var buffer1: Bitmap? = null
        var imageBuffer0: ImageBitmap? = null
        var imageBuffer1: ImageBitmap? = null
        var allocatedW = 0
        var allocatedH = 0
        var bufferToggle = 0
        var frameCounter = 0
        val loc = IntArray(2)

        try {
            while (isActive) {
                val sv = activeSurfaceView
                val sw = sv.width
                val sh = sv.height
                val surfaceValid = sv.isAttachedToWindow &&
                    sv.visibility == View.VISIBLE &&
                    sw > 0 && sh > 0 &&
                    sv.holder.surface?.isValid == true

                if (surfaceValid) {
                    val scale = (maxDimPx / maxOf(sw, sh).toFloat()).coerceAtMost(1f)
                    val targetW = (sw * scale).roundToInt().coerceAtLeast(16)
                    val targetH = (sh * scale).roundToInt().coerceAtLeast(16)

                    if (targetW != allocatedW ||
                        targetH != allocatedH ||
                        buffer0 == null ||
                        buffer1 == null ||
                        imageBuffer0 == null ||
                        imageBuffer1 == null
                    ) {
                        buffer0 = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                        buffer1 = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                        imageBuffer0 = buffer0.asImageBitmap()
                        imageBuffer1 = buffer1.asImageBitmap()
                        allocatedW = targetW
                        allocatedH = targetH
                    }

                    val slot = bufferToggle and 1
                    val targetBitmap = if (slot == 0) buffer0 else buffer1
                    val targetImage = if (slot == 0) imageBuffer0 else imageBuffer1

                    val copyResult = suspendCancellableCoroutine<Int> { cont ->
                        try {
                            PixelCopy.request(
                                sv,
                                targetBitmap,
                                { result ->
                                    if (cont.isActive) {
                                        cont.resume(result)
                                    }
                                },
                                copyHandler
                            )
                        } catch (_: Throwable) {
                            if (cont.isActive) {
                                cont.resume(PixelCopy.ERROR_UNKNOWN)
                            }
                        }
                    }

                    if (copyResult == PixelCopy.SUCCESS && isActive) {
                        sv.getLocationInWindow(loc)
                        frameCounter++
                        bufferToggle++
                        frameState.value = VideoBackdropFrame(
                            bitmap = targetImage,
                            surfacePosInWindow = Offset(loc[0].toFloat(), loc[1].toFloat()),
                            surfaceSize = IntSize(sw, sh),
                            frameToken = frameCounter
                        )
                    }
                    delay(frameIntervalMs)
                } else {
                    delay(120L)
                }
            }
        } finally {
            handlerThread.quitSafely()
        }
    }

    return remember { { frameState.value } }
}

/**
 * Real-time GPU Liquid Glass modifier backed by an external [VideoBackdropFrame] (such as an
 * active video SurfaceView). Draws and crops the corresponding screen region into an offscreen
 * GraphicsLayer, runs [AgslRefractionCache]'s blur + curved-bezel refraction + vibrancy + frosted
 * film grain shader on Android 13+, and falls back gracefully when no frame is ready or on older
 * Android versions.
 */
@Composable
fun Modifier.liquidGlassBitmapBackdrop(
    frameProvider: () -> VideoBackdropFrame?,
    cornerRadius: Dp = 26.dp,
    isCircle: Boolean = false,
    style: LiquidGlassStyle = LiquidGlassStyle.Sheet,
    fallbackTintColor: Color = Color(0xBD101520),
    pressProgress: Float = 0f
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return this.liquidGlassSurface(
            cornerRadius = cornerRadius,
            isCircle = isCircle,
            tintColor = fallbackTintColor,
            highlightStrength = style.highlightStrength,
            pressProgress = pressProgress,
            grainStrength = style.grainStrength
        )
    }

    val density = LocalDensity.current
    val blurPx = with(density) { style.blurRadius.toPx() }
    val bezelPx = with(density) { style.bezelWidth.toPx() }
    val refractionPx = with(density) { style.refractionStrength.toPx() }
    val chromaticPx = with(density) { style.chromaticAberration.toPx() }
    val cornerPx = with(density) { cornerRadius.toPx() }
    val padPx = remember(blurPx, refractionPx, chromaticPx) {
        ceil(maxOf(refractionPx + chromaticPx + 6f, blurPx * 0.75f + 6f)).toInt().coerceIn(8, 64)
    }

    var elementPosInWindow by remember { mutableStateOf(Offset.Zero) }
    val glassLayer = rememberGraphicsLayer()
    val cache = remember { AgslRefractionCache() }

    return this
        .onGloballyPositioned { coords ->
            elementPosInWindow = coords.positionInWindow()
        }
        .drawWithContent {
            val w = size.width.toInt()
            val h = size.height.toInt()
            if (w < 4 || h < 4) {
                drawContent()
                return@drawWithContent
            }

            val radii = resolveCornerRadiiPx(
                width = size.width,
                height = size.height,
                isCircle = isCircle,
                cornerRadiusPx = cornerPx,
                cornersPx = null
            )

            val frame = frameProvider()
            if (frame != null && frame.surfaceSize.width > 0 && frame.surfaceSize.height > 0) {
                // Subscribe in draw phase so every captured PixelCopy frame updates the blurred layer.
                @Suppress("UNUSED_VARIABLE")
                val token = frame.frameToken
                val key = RefractionShaderKey(
                    width = w,
                    height = h,
                    pad = padPx,
                    topLeft = radii[0],
                    topRight = radii[1],
                    bottomRight = radii[2],
                    bottomLeft = radii[3],
                    style = style,
                    pressProgress = pressProgress
                )
                val effect = cache.getOrCreateEffect(
                    key = key,
                    blurRadiusPx = blurPx,
                    bezelWidthPx = bezelPx,
                    refractionPx = refractionPx,
                    chromaticPx = chromaticPx
                )
                if (effect != null) {
                    val targetSize = IntSize(w + padPx * 2, h + padPx * 2)
                    val dstLeft = (frame.surfacePosInWindow.x - elementPosInWindow.x + padPx).roundToInt()
                    val dstTop = (frame.surfacePosInWindow.y - elementPosInWindow.y + padPx).roundToInt()
                    glassLayer.record(size = targetSize) {
                        drawRect(
                            color = Color.Black,
                            size = Size(targetSize.width.toFloat(), targetSize.height.toFloat())
                        )
                        drawImage(
                            image = frame.bitmap,
                            dstOffset = IntOffset(dstLeft, dstTop),
                            dstSize = frame.surfaceSize
                        )
                    }
                    glassLayer.renderEffect = effect
                    translate(left = -padPx.toFloat(), top = -padPx.toFloat()) {
                        drawLayer(glassLayer)
                    }
                    drawContent()
                    return@drawWithContent
                }
            }

            drawGlassFallback(
                radii = radii,
                tintColor = fallbackTintColor,
                highlightStrength = style.highlightStrength,
                pressProgress = pressProgress
            )
            drawContent()
        }
}

@Composable
fun Modifier.liquidGlassBitmapBackdrop(
    frame: VideoBackdropFrame?,
    cornerRadius: Dp = 26.dp,
    isCircle: Boolean = false,
    style: LiquidGlassStyle = LiquidGlassStyle.Sheet,
    fallbackTintColor: Color = Color(0xBD101520),
    pressProgress: Float = 0f
): Modifier = liquidGlassBitmapBackdrop(
    frameProvider = { frame },
    cornerRadius = cornerRadius,
    isCircle = isCircle,
    style = style,
    fallbackTintColor = fallbackTintColor,
    pressProgress = pressProgress
)

/**
 * Standalone optical Liquid Glass surface modifier (ideal for floating overlays, playback
 * controls above a hardware SurfaceView, and adaptive sheets). Renders GPU Fresnel refraction
 * rims, curved meniscus highlights, and interactive sheen on Android 13+.
 */
@Composable
fun Modifier.liquidGlassSurface(
    cornerRadius: Dp = 20.dp,
    isCircle: Boolean = false,
    cornerRadii: LiquidGlassCorners? = null,
    tintColor: Color = Color(0x66121722),
    highlightStrength: Float = 0.85f,
    pressProgress: Float = 0f,
    grainStrength: Float = 0.0f
): Modifier {
    val density = LocalDensity.current
    val cornerPx = with(density) { cornerRadius.toPx() }
    val cornersPx = remember(cornerRadii, density) {
        cornerRadii?.let {
            with(density) {
                floatArrayOf(
                    it.topStart.toPx(),
                    it.topEnd.toPx(),
                    it.bottomEnd.toPx(),
                    it.bottomStart.toPx()
                )
            }
        }
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return this.drawBehind {
            if (size.width < 2f || size.height < 2f) return@drawBehind
            val radii = resolveCornerRadiiPx(
                width = size.width,
                height = size.height,
                isCircle = isCircle,
                cornerRadiusPx = cornerPx,
                cornersPx = cornersPx
            )
            drawGlassFallback(
                radii = radii,
                tintColor = tintColor,
                highlightStrength = highlightStrength,
                pressProgress = pressProgress
            )
        }
    }

    val cache = remember { AgslSurfaceCache() }
    return this.drawBehind {
        if (size.width < 2f || size.height < 2f) return@drawBehind
        val radii = resolveCornerRadiiPx(
            width = size.width,
            height = size.height,
            isCircle = isCircle,
            cornerRadiusPx = cornerPx,
            cornersPx = cornersPx
        )
        val key = SurfaceShaderKey(
            width = size.width,
            height = size.height,
            topLeft = radii[0],
            topRight = radii[1],
            bottomRight = radii[2],
            bottomLeft = radii[3],
            tintArgb = tintColor.value.toLong(),
            highlightStrength = highlightStrength,
            pressProgress = pressProgress,
            grainStrength = grainStrength
        )
        val brush = cache.getOrCreateBrush(key, tintColor)
        if (brush != null) {
            drawRect(brush = brush)
        } else {
            drawGlassFallback(
                radii = radii,
                tintColor = tintColor,
                highlightStrength = highlightStrength,
                pressProgress = pressProgress
            )
        }
    }
}

@Composable
fun LiquidGlassCircleButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    iconTint: Color = Color.White,
    backdropState: LiquidGlassBackdropState? = null,
    tintColor: Color = Color(0x5C141A26),
    videoBackdropProvider: (() -> VideoBackdropFrame?)? = null,
    videoGlassStyle: LiquidGlassStyle = LiquidGlassStyle.PlayerControl
) {
    LiquidGlassCircleButtonImpl(
        iconRes = iconRes,
        contentDescription = contentDescription,
        onClick = onClick,
        onLongClick = null,
        modifier = modifier,
        size = size,
        iconSize = iconSize,
        iconTint = iconTint,
        backdropState = backdropState,
        tintColor = tintColor,
        videoBackdropProvider = videoBackdropProvider,
        videoGlassStyle = videoGlassStyle
    )
}

@Composable
fun LiquidGlassCircleCombinedButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 21.dp,
    iconTint: Color = Color.White,
    backdropState: LiquidGlassBackdropState? = null,
    tintColor: Color = Color(0x5C141A26),
    videoBackdropProvider: (() -> VideoBackdropFrame?)? = null,
    videoGlassStyle: LiquidGlassStyle = LiquidGlassStyle.PlayerControl
) {
    LiquidGlassCircleButtonImpl(
        iconRes = iconRes,
        contentDescription = contentDescription,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        size = size,
        iconSize = iconSize,
        iconTint = iconTint,
        backdropState = backdropState,
        tintColor = tintColor,
        videoBackdropProvider = videoBackdropProvider,
        videoGlassStyle = videoGlassStyle
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiquidGlassCircleButtonImpl(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier,
    size: Dp,
    iconSize: Dp,
    iconTint: Color,
    backdropState: LiquidGlassBackdropState?,
    tintColor: Color,
    videoBackdropProvider: (() -> VideoBackdropFrame?)?,
    videoGlassStyle: LiquidGlassStyle
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by rememberIosPressScale(
        interactionSource = interactionSource,
        pressedScale = 0.94f,
        label = "liquidGlassCircleScale"
    )
    val pressProgress by animateFloatAsState(
        targetValue = if (isPressed) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = 180, easing = IosMotion.EaseOut),
        label = "liquidGlassCirclePress"
    )

    val surfaceModifier = when {
        videoBackdropProvider != null -> Modifier.liquidGlassBitmapBackdrop(
            frameProvider = videoBackdropProvider,
            isCircle = true,
            style = videoGlassStyle.copy(tintColor = tintColor),
            fallbackTintColor = tintColor,
            pressProgress = pressProgress
        )
        backdropState != null -> Modifier.liquidGlass(
            backdropState = backdropState,
            isCircle = true,
            style = LiquidGlassStyle.CircleButton.copy(tintColor = tintColor),
            pressProgress = pressProgress
        )
        else -> Modifier.liquidGlassSurface(
            isCircle = true,
            tintColor = tintColor,
            highlightStrength = 0.92f,
            pressProgress = pressProgress
        )
    }

    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = ripple(bounded = true, color = Color(0x52FFFFFF)),
            onClick = onClick,
            onLongClick = onLongClick
        )
    } else {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = ripple(bounded = true, color = Color(0x52FFFFFF)),
            onClick = onClick
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(surfaceModifier)
            .clip(CircleShape)
            .then(clickModifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(iconSize)
        )
    }
}
