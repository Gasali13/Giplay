package com.personal.animeplayer.ui.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import com.personal.animeplayer.R

class DoubleTapOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val containerRewind: View
    private val contentRewind: View
    private val tvRewindSeconds: TextView

    private val containerForward: View
    private val contentForward: View
    private val tvForwardSeconds: TextView

    private val mainHandler = Handler(Looper.getMainLooper())
    private var accumulatedSeconds = 0
    private var isCurrentForward = false
    private var isAnimationActive = false

    private val HIDE_DELAY = 650L

    init {
        LayoutInflater.from(context).inflate(R.layout.view_double_tap_overlay, this, true)

        isClickable = false
        isFocusable = false

        containerRewind = findViewById(R.id.container_rewind)
        contentRewind = findViewById(R.id.content_rewind)
        tvRewindSeconds = findViewById(R.id.tv_rewind_seconds)

        containerForward = findViewById(R.id.container_forward)
        contentForward = findViewById(R.id.content_forward)
        tvForwardSeconds = findViewById(R.id.tv_forward_seconds)
    }

    /**
     * Triggers the smooth double-tap seek animation.
     * Displays only the elapsed/skipped seconds text without background arcs.
     */
    fun showSeekAnimation(isForward: Boolean, stepSeconds: Int = 10) {
        mainHandler.removeCallbacks(hideRunnable)

        // If the direction changed while active, immediately hide previous side
        if (isAnimationActive && isCurrentForward != isForward) {
            cancelAllAnimations()
            hideImmediate()
        }

        isAnimationActive = true
        isCurrentForward = isForward

        accumulatedSeconds += stepSeconds

        if (isForward) {
            containerRewind.visibility = View.INVISIBLE
            containerForward.visibility = View.VISIBLE
            containerForward.alpha = 1.0f

            tvForwardSeconds.text = "+$accumulatedSeconds Detik"

            // Text pop and bounce animation
            contentForward.scaleX = 1.25f
            contentForward.scaleY = 1.25f
            contentForward.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(180)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            containerForward.visibility = View.INVISIBLE
            containerRewind.visibility = View.VISIBLE
            containerRewind.alpha = 1.0f

            tvRewindSeconds.text = "-$accumulatedSeconds Detik"

            // Text pop and bounce animation
            contentRewind.scaleX = 1.25f
            contentRewind.scaleY = 1.25f
            contentRewind.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(180)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        mainHandler.postDelayed(hideRunnable, HIDE_DELAY)
    }

    private val hideRunnable = Runnable {
        smoothFadeOut()
    }

    private fun smoothFadeOut() {
        cancelAllAnimations()

        val activeContainer = if (isCurrentForward) containerForward else containerRewind

        activeContainer.animate()
            .alpha(0f)
            .setDuration(220)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                hideImmediate()
            }
            .start()
    }

    private fun hideImmediate() {
        containerForward.visibility = View.INVISIBLE
        containerRewind.visibility = View.INVISIBLE
        containerForward.alpha = 1.0f
        containerRewind.alpha = 1.0f
        accumulatedSeconds = 0
        isAnimationActive = false
    }

    private fun cancelAllAnimations() {
        contentForward.animate().cancel()
        contentRewind.animate().cancel()
        containerForward.animate().cancel()
        containerRewind.animate().cancel()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mainHandler.removeCallbacks(hideRunnable)
        cancelAllAnimations()
    }
}
