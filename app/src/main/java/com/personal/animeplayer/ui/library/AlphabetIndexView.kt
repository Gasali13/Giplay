package com.personal.animeplayer.ui.library

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.personal.animeplayer.R
import com.personal.animeplayer.data.model.Episode

/**
 * Custom View that displays a vertical A-Z alphabet index sidebar for fast navigation.
 * Designed with smooth drag tracking, haptic tick feedback, and dynamic letter highlighting.
 */
class AlphabetIndexView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    val letters = listOf(
        '#', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L',
        'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'
    )

    private var selectedIndex: Int = -1
    private var isDragging: Boolean = false

    var onLetterSelected: ((letter: Char, index: Int) -> Unit)? = null
    var onLetterReleased: (() -> Unit)? = null

    private val normalColor = ContextCompat.getColor(context, R.color.on_surface_muted)
    private val highlightColor = ContextCompat.getColor(context, R.color.secondary)
    private val highlightBgColor = 0x3300A3FF.toInt() // 20% alpha secondary blue

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = highlightBgColor
        style = Paint.Style.FILL
    }

    init {
        // Set background pill drawable
        background = ContextCompat.getDrawable(context, R.drawable.bg_alphabet_bar)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (letters.isEmpty()) return

        val availableHeight = height - paddingTop - paddingBottom
        val itemHeight = availableHeight.toFloat() / letters.size
        val density = resources.displayMetrics.density

        // Adjust text size based on available item height (typically 9sp - 11sp)
        val optimalTextSize = (itemHeight * 0.70f).coerceIn(8.5f * density, 11.5f * density)
        textPaint.textSize = optimalTextSize

        val centerX = width / 2f

        for (i in letters.indices) {
            val centerY = paddingTop + i * itemHeight + itemHeight / 2f
            val isSelected = (i == selectedIndex)

            if (isSelected && isDragging) {
                // Draw glowing circular background for the active letter
                val radius = (itemHeight / 2f).coerceAtMost(width / 2f - 2f * density)
                canvas.drawCircle(centerX, centerY, radius, circlePaint)
            }

            textPaint.color = if (isSelected) highlightColor else normalColor
            textPaint.isFakeBoldText = isSelected

            val fontMetrics = textPaint.fontMetrics
            val baseline = centerY - (fontMetrics.descent + fontMetrics.ascent) / 2f

            canvas.drawText(letters[i].toString(), centerX, baseline, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                isDragging = true

                val availableHeight = height - paddingTop - paddingBottom
                if (availableHeight <= 0) return true

                val itemHeight = availableHeight.toFloat() / letters.size
                val touchY = event.y - paddingTop
                val newIndex = (touchY / itemHeight).toInt().coerceIn(0, letters.size - 1)

                if (newIndex != selectedIndex) {
                    selectedIndex = newIndex
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onLetterSelected?.invoke(letters[newIndex], newIndex)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                isDragging = false
                if (event.action == MotionEvent.ACTION_UP) {
                    performClick()
                }
                onLetterReleased?.invoke()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /**
     * Programmatically highlight a letter when the user is scrolling the list naturally.
     */
    fun setHighlightedLetter(letter: Char) {
        if (isDragging) return
        val upper = letter.uppercaseChar()
        val index = if (upper in 'A'..'Z') letters.indexOf(upper) else 0
        if (index != -1 && index != selectedIndex) {
            selectedIndex = index
            invalidate()
        }
    }

    fun getLetterCenterY(index: Int): Float {
        if (letters.isEmpty()) return height / 2f
        val availableHeight = height - paddingTop - paddingBottom
        if (availableHeight <= 0) return height / 2f
        val itemHeight = availableHeight.toFloat() / letters.size
        val safeIndex = index.coerceIn(0, letters.size - 1)
        return paddingTop + safeIndex * itemHeight + itemHeight / 2f
    }

    companion object {
        fun getLetterForEpisode(episode: Episode): Char {
            val name = episode.fileName.ifEmpty { episode.title }
            val first = name.trim().firstOrNull()?.uppercaseChar() ?: return '#'
            return if (first in 'A'..'Z') first else '#'
        }

        fun findPositionForLetter(letter: Char, episodes: List<Episode>): Int {
            if (episodes.isEmpty()) return -1
            if (letter == '#') return 0

            // 1. Exact match
            val exactIndex = episodes.indexOfFirst { getLetterForEpisode(it) == letter }
            if (exactIndex != -1) return exactIndex

            // 2. Next closest letter greater than the target letter
            val nextIndex = episodes.indexOfFirst {
                val ch = getLetterForEpisode(it)
                ch in 'A'..'Z' && ch > letter
            }
            if (nextIndex != -1) return nextIndex

            return episodes.size - 1
        }
    }
}
