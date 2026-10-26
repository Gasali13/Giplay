package com.personal.animeplayer.ui.player

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

class SpeedOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    val speeds = floatArrayOf(0.25f, 0.5f, 1.0f, 2.0f, 3.0f, 4.0f)
    val speedLabels = arrayOf("0.25x", "0.5x", "1.0x", "2.0x", "3.0x", "4.0x")

    var currentIndex: Int = 3 // Default to 2.0x (index 3)
        private set

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity

    private val bgRect = RectF()
    private val cornerRadius = 20f * density

    // Paints
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(225, 18, 22, 30) // Deep dark translucent glass
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(55, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
    }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
    }

    private val tickDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 56, 189, 248) // Sky blue halo glow
        style = Paint.Style.FILL
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8") // Vivid Sky Blue
        style = Paint.Style.FILL
    }

    private val innerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val statusTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12f * scaledDensity
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.LEFT
    }

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8")
        style = Paint.Style.FILL
    }

    private val iconPath = Path()

    // Smooth animated thumb position
    private var currentThumbX: Float = -1f
    private var thumbAnimator: ValueAnimator? = null

    private fun getTickX(index: Int): Float {
        val leftPadding = 24f * density
        val rightPadding = 24f * density
        val usableWidth = width.toFloat() - leftPadding - rightPadding
        val step = usableWidth / (speeds.size - 1)
        return leftPadding + index * step
    }

    fun show(initialIndex: Int = 3) {
        currentIndex = initialIndex.coerceIn(0, speeds.size - 1)
        currentThumbX = -1f
        thumbAnimator?.cancel()
        animate().cancel()
        alpha = 0f
        scaleX = 0.94f
        scaleY = 0.94f
        visibility = VISIBLE
        animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(180)
            .setInterpolator(DecelerateInterpolator())
            .start()
        invalidate()
    }

    fun hide() {
        thumbAnimator?.cancel()
        animate().cancel()
        animate()
            .alpha(0f)
            .scaleX(0.94f)
            .scaleY(0.94f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                visibility = GONE
            }
            .start()
    }

    fun setSpeedIndex(index: Int) {
        val clamped = index.coerceIn(0, speeds.size - 1)
        if (clamped == currentIndex && currentThumbX > 0) return

        currentIndex = clamped
        if (width > 0) {
            val targetX = getTickX(currentIndex)
            if (currentThumbX < 0) {
                currentThumbX = targetX
                invalidate()
            } else {
                thumbAnimator?.cancel()
                thumbAnimator = ValueAnimator.ofFloat(currentThumbX, targetX).apply {
                    duration = 140
                    interpolator = DecelerateInterpolator()
                    addUpdateListener { anim ->
                        currentThumbX = anim.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
            }
        } else {
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxW = (400 * density).toInt()
        val specWidth = MeasureSpec.getSize(widthMeasureSpec)
        val desiredW = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.EXACTLY -> specWidth
            MeasureSpec.AT_MOST -> minOf(specWidth, maxW)
            else -> maxW
        }
        val desiredH = (74 * density).toInt()
        val w = resolveSize(desiredW, widthMeasureSpec)
        val h = resolveSize(desiredH, heightMeasureSpec)
        setMeasuredDimension(w, h)
        bgRect.set(1.5f * density, 1.5f * density, w.toFloat() - 1.5f * density, h.toFloat() - 1.5f * density)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bgRect.set(1.5f * density, 1.5f * density, w.toFloat() - 1.5f * density, h.toFloat() - 1.5f * density)
        if (w > 0) {
            currentThumbX = getTickX(currentIndex)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val w = width.toFloat()
        val h = height.toFloat()

        if (bgRect.isEmpty) {
            bgRect.set(1.5f * density, 1.5f * density, w - 1.5f * density, h - 1.5f * density)
        }

        // 1. Draw rounded container & border
        canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)
        canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, borderPaint)

        val trackY = h * 0.40f
        val labelY = trackY - 11f * density

        if (currentThumbX < 0) {
            currentThumbX = getTickX(currentIndex)
        }

        // 2. Draw track line
        val firstX = getTickX(0)
        val lastX = getTickX(speeds.size - 1)
        canvas.drawLine(firstX, trackY, lastX, trackY, trackPaint)

        // 3. Draw tick dots and labels
        for (i in speeds.indices) {
            val tickX = getTickX(i)
            val isCurrent = (i == currentIndex)

            // Tick dot
            if (isCurrent) {
                tickDotPaint.color = Color.parseColor("#38BDF8")
            } else {
                tickDotPaint.color = Color.argb(140, 200, 215, 230)
            }
            canvas.drawCircle(tickX, trackY, 2.5f * density, tickDotPaint)

            // Tick label text
            if (isCurrent) {
                labelPaint.color = Color.parseColor("#38BDF8")
                labelPaint.textSize = 11.5f * scaledDensity
                labelPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            } else {
                labelPaint.color = Color.argb(160, 160, 175, 195)
                labelPaint.textSize = 10.5f * scaledDensity
                labelPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            canvas.drawText(speedLabels[i], tickX, labelY, labelPaint)
        }

        // 4. Draw glowing active thumb
        canvas.drawCircle(currentThumbX, trackY, 8f * density, haloPaint)
        canvas.drawCircle(currentThumbX, trackY, 5f * density, thumbPaint)
        canvas.drawCircle(currentThumbX, trackY, 2f * density, innerDotPaint)

        // 5. Draw bottom status: "▶▶ 2.0x Speed Playing"
        val statusText = "${speedLabels[currentIndex]} Speed Playing"
        val textWidth = statusTextPaint.measureText(statusText)
        val iconWidth = 13f * density
        val iconSpacing = 5f * density
        val totalStatusWidth = iconWidth + iconSpacing + textWidth

        val statusStartX = (w - totalStatusWidth) / 2f
        val statusY = h * 0.83f

        // Draw double chevron icon
        val iconY = statusY - (4.5f * density)
        val iconH = 7.5f * density
        val halfW = iconWidth / 2f

        iconPath.reset()
        // First triangle
        iconPath.moveTo(statusStartX, iconY - iconH / 2f)
        iconPath.lineTo(statusStartX + halfW * 0.8f, iconY)
        iconPath.lineTo(statusStartX, iconY + iconH / 2f)
        iconPath.close()
        // Second triangle
        iconPath.moveTo(statusStartX + halfW * 0.7f, iconY - iconH / 2f)
        iconPath.lineTo(statusStartX + iconWidth, iconY)
        iconPath.lineTo(statusStartX + halfW * 0.7f, iconY + iconH / 2f)
        iconPath.close()

        canvas.drawPath(iconPath, iconPaint)

        // Draw text
        canvas.drawText(statusText, statusStartX + iconWidth + iconSpacing, statusY, statusTextPaint)
    }
}
