package com.personal.animeplayer.ui.player

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.widget.AppCompatSeekBar
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Modern Minimalist Timeline with Chasing Glow (Animasi glow mengejar titik saat digeser):
 * 1. Dark navy translucent background track with rounded caps.
 * 2. Solid, crisp electric blue active progress line (NO glow along the straight line body).
 * 3. Elastic Chasing Glow: saat digeser, ekor glow tertinggal dan meregang mengejar titiknya secara dinamis.
 * 4. Animasi spring back / settle yang halus saat berhenti atau dilepas.
 */
class GlowingSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatSeekBar(context, attrs, defStyleAttr) {

    private var changeListener: OnSeekBarChangeListener? = null

    // Density helper
    private val density = context.resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    // Dimensions
    private val barHeight = dp(5f)
    private val normalGlowTrail = dp(16f)
    private val pressedGlowTrail = dp(24f)
    private val normalGlowRadius = dp(9f)
    private val pressedGlowRadius = dp(12f)

    private var currentGlowTrail = normalGlowTrail
    private var currentGlowRadius = normalGlowRadius
    private var glowIntensityScale = 1.0f

    // Elastic chasing physics state
    private var chasingGlowX = -1f

    // Preallocated gradient buffers for zero object allocation in onDraw
    private val gradientPositions = floatArrayOf(0f, 0.45f, 1f)
    private val glowColors = IntArray(3)
    private val tipBloomColors = IntArray(3)

    // 1. Background Track: Dark navy translucent with rounded caps
    private val bgTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#38223854") // Translucent dark navy
        strokeWidth = barHeight
    }

    // 2. Active Progress Line: Solid crisp electric blue (NO glow along line body)
    private val activeTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#0084FF") // Electric blue
        strokeWidth = barHeight
    }

    // 3. Dynamic Chasing Glow Trail Paint
    private val glowTrailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = barHeight + dp(7f)
        maskFilter = BlurMaskFilter(dp(4.5f), BlurMaskFilter.Blur.NORMAL)
    }

    // 4. Soft Radial Bloom right at the rounded tip
    private val tipBloomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // 5. Bright Luminous Cyan-Blue Tip Core
    private val tipCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#38BDF8") // Bright cyan highlight
    }

    private var pressAnimator: ValueAnimator? = null
    private var isDragging = false

    init {
        // Disable hardware acceleration on this specific view to allow smooth Gaussian blur
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        // Disable default seekbar drawables
        splitTrack = false
        thumb = null
        progressDrawable = null
    }

    override fun setOnSeekBarChangeListener(l: OnSeekBarChangeListener?) {
        super.setOnSeekBarChangeListener(l)
        this.changeListener = l
    }

    private fun animatePress(pressed: Boolean) {
        pressAnimator?.cancel()
        val targetTrail = if (pressed) pressedGlowTrail else normalGlowTrail
        val targetRadius = if (pressed) pressedGlowRadius else normalGlowRadius
        val targetScale = if (pressed) 1.35f else 1.0f

        val startTrail = currentGlowTrail
        val startRadius = currentGlowRadius
        val startScale = glowIntensityScale

        pressAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 180
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                currentGlowTrail = startTrail + (targetTrail - startTrail) * fraction
                currentGlowRadius = startRadius + (targetRadius - startRadius) * fraction
                glowIntensityScale = startScale + (targetScale - startScale) * fraction
                postInvalidateOnAnimation()
            }
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        val maxVal = max
        val trackStartX = paddingLeft.toFloat() + barHeight / 2f
        val trackEndX = width.toFloat() - paddingRight.toFloat() - barHeight / 2f
        val availableTrackWidth = (trackEndX - trackStartX).coerceAtLeast(1f)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                isPressed = true
                parent?.requestDisallowInterceptTouchEvent(true)
                animatePress(true)

                val newProgress = calculateProgress(event.x, trackStartX, availableTrackWidth, maxVal)
                progress = newProgress
                changeListener?.onStartTrackingTouch(this)
                changeListener?.onProgressChanged(this, newProgress, true)
                postInvalidateOnAnimation()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val newProgress = calculateProgress(event.x, trackStartX, availableTrackWidth, maxVal)
                    progress = newProgress
                    changeListener?.onProgressChanged(this, newProgress, true)
                    postInvalidateOnAnimation()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    isPressed = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    animatePress(false)

                    val newProgress = calculateProgress(event.x, trackStartX, availableTrackWidth, maxVal)
                    progress = newProgress
                    changeListener?.onStopTrackingTouch(this)
                    if (event.action == MotionEvent.ACTION_UP) {
                        performClick()
                    }
                    postInvalidateOnAnimation()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun calculateProgress(touchX: Float, trackStartX: Float, availWidth: Float, maxVal: Int): Int {
        val clampedX = (touchX - trackStartX).coerceIn(0f, availWidth)
        val ratio = clampedX / availWidth
        return (ratio * maxVal).roundToInt().coerceIn(0, maxVal)
    }

    override fun setProgress(progress: Int) {
        super.setProgress(progress)
        invalidate()
    }

    override fun setProgress(progress: Int, animate: Boolean) {
        super.setProgress(progress, animate)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val totalWidth = width.toFloat()
        val totalHeight = height.toFloat()
        if (totalWidth <= 0 || totalHeight <= 0) return

        val centerY = totalHeight / 2f
        val trackStartX = paddingLeft.toFloat() + barHeight / 2f
        val trackEndX = totalWidth - paddingRight.toFloat() - barHeight / 2f
        val availableTrackWidth = (trackEndX - trackStartX).coerceAtLeast(0f)

        val progressRatio = if (max > 0) (progress.toFloat() / max).coerceIn(0f, 1f) else 0f
        val currentHeadX = trackStartX + availableTrackWidth * progressRatio

        // Initialize chasing glow position on first run
        if (chasingGlowX < 0f) {
            chasingGlowX = currentHeadX
        }

        // Elastic follow physics (Glow lags behind and chases the head)
        val diffX = currentHeadX - chasingGlowX
        if (abs(diffX) > 0.4f) {
            chasingGlowX += diffX * 0.24f // Smooth follow inertia
            postInvalidateOnAnimation()
        } else {
            chasingGlowX = currentHeadX
        }

        // 1. Background Track (Navy translucent bar with rounded caps)
        canvas.drawLine(trackStartX, centerY, trackEndX, centerY, bgTrackPaint)

        if (currentHeadX > trackStartX) {
            // 2. Elastic Chasing Glow Trail (Mengejar titik saat digeser)
            val lag = chasingGlowX - currentHeadX // < 0 saat geser ke kanan, > 0 saat geser ke kiri

            if (lag <= 0f) {
                // Digeser ke KANAN atau diam: Ekor glow tertinggal di sebelah kiri mengejar ke kanan
                val glowStartX = (chasingGlowX - currentGlowTrail).coerceAtLeast(trackStartX)
                if (currentHeadX > glowStartX) {
                    glowColors[0] = Color.argb(0, 0, 140, 255)
                    glowColors[1] = Color.argb((75 * glowIntensityScale).toInt().coerceIn(0, 255), 0, 160, 255)
                    glowColors[2] = Color.argb((200 * glowIntensityScale).toInt().coerceIn(0, 255), 56, 189, 248)
                    glowTrailPaint.shader = LinearGradient(
                        glowStartX, centerY, currentHeadX, centerY,
                        glowColors,
                        gradientPositions,
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawLine(glowStartX, centerY, currentHeadX, centerY, glowTrailPaint)
                }
            } else {
                // Digeser ke KIRI: Ekor glow tertinggal di sebelah kanan mengejar ke kiri
                val glowEndX = (chasingGlowX + currentGlowTrail).coerceAtMost(trackEndX)
                if (glowEndX > currentHeadX) {
                    glowColors[0] = Color.argb(0, 0, 140, 255)
                    glowColors[1] = Color.argb((75 * glowIntensityScale).toInt().coerceIn(0, 255), 0, 160, 255)
                    glowColors[2] = Color.argb((200 * glowIntensityScale).toInt().coerceIn(0, 255), 56, 189, 248)
                    glowTrailPaint.shader = LinearGradient(
                        glowEndX, centerY, currentHeadX, centerY,
                        glowColors,
                        gradientPositions,
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawLine(currentHeadX, centerY, glowEndX, centerY, glowTrailPaint)
                }
            }

            // 3. Compact Radial Bloom right around the rounded tip
            val tipRadius = currentGlowRadius
            tipBloomColors[0] = Color.argb((215 * glowIntensityScale).toInt().coerceIn(0, 255), 56, 189, 248)
            tipBloomColors[1] = Color.argb((115 * glowIntensityScale).toInt().coerceIn(0, 255), 0, 140, 255)
            tipBloomColors[2] = Color.argb(0, 0, 90, 240)
            tipBloomPaint.shader = RadialGradient(
                currentHeadX, centerY, tipRadius,
                tipBloomColors,
                gradientPositions,
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(currentHeadX, centerY, tipRadius, tipBloomPaint)

            // 4. Solid Active Progress Line (Garis durasi solid tanpa glow di badannya)
            canvas.drawLine(trackStartX, centerY, currentHeadX, centerY, activeTrackPaint)

            // 5. Luminous Highlight Dot at the very tip
            val coreDotRadius = barHeight / 2f
            canvas.drawCircle(currentHeadX, centerY, coreDotRadius, tipCorePaint)
        }
    }
}
