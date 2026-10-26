package com.personal.animeplayer.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

interface GestureListener {
    fun onSeekGesture(deltaMs: Long, targetMs: Long)
    fun onSeekConfirmed(targetMs: Long)
    fun onBrightnessChanged(percent: Int)
    fun onVolumeChanged(percent: Int)
    fun onDoubleTapSeek(isForward: Boolean)
    fun onSingleTap()

    // Press & Hold Speed Control
    fun onSpeedPressStart()
    fun onSpeedPressMove(deltaX: Float)
    fun onSpeedPressEnd()

    fun onBrightnessConfirmed(brightness: Float) {}
}

class GestureManager(
    private val activity: Activity,
    private val overlayView: View,
    private val listener: GestureListener,
    private val getCurrentPosition: () -> Long,
    private val getDuration: () -> Long
) {

    private val audioManager = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    private val mainHandler = Handler(Looper.getMainLooper())

    var isLocked: Boolean = false

    private var initialBrightness: Float = 0.5f
    private var initialVolume: Int = 0
    private var initialSeekPosition: Long = 0L

    private var downX: Float = 0f
    private var downY: Float = 0f
    private var lastTouchX: Float = 0f
    private var speedPressStartX: Float = 0f

    private var isSeeking = false
    private var isAdjustingBrightness = false
    private var isAdjustingVolume = false
    private var isSpeedControlActive = false

    private val LONG_PRESS_DELAY = 350L
    private val MOVE_THRESHOLD = 24f

    private val longPressRunnable = Runnable {
        if (!isSeeking && !isAdjustingBrightness && !isAdjustingVolume && !isLocked) {
            isSpeedControlActive = true
            speedPressStartX = lastTouchX
            overlayView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            listener.onSpeedPressStart()
        }
    }

    private val gestureDetector = GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (!isSpeedControlActive) {
                listener.onSingleTap()
            }
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (isLocked || isSpeedControlActive) return false
            mainHandler.removeCallbacks(longPressRunnable)
            val screenWidth = overlayView.width
            val isRightSide = e.x > screenWidth / 2
            listener.onDoubleTapSeek(isRightSide)
            return true
        }
    })

    init {
        gestureDetector.setIsLongpressEnabled(false)
        setupTouchListener()
    }

    private fun setupTouchListener() {
        overlayView.setOnTouchListener { _, event ->
            if (isLocked) {
                mainHandler.removeCallbacks(longPressRunnable)
                gestureDetector.onTouchEvent(event)
                return@setOnTouchListener true
            }

            val screenWidth = overlayView.width.toFloat().coerceAtLeast(1f)
            val screenHeight = overlayView.height.toFloat().coerceAtLeast(1f)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    lastTouchX = event.x
                    speedPressStartX = event.x

                    isSeeking = false
                    isAdjustingBrightness = false
                    isAdjustingVolume = false
                    isSpeedControlActive = false

                    // Capture current brightness
                    var currentBrightness = activity.window.attributes.screenBrightness
                    if (currentBrightness < 0) currentBrightness = 0.5f
                    initialBrightness = currentBrightness

                    // Capture current volume
                    initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    initialSeekPosition = getCurrentPosition()

                    mainHandler.removeCallbacks(longPressRunnable)
                    mainHandler.postDelayed(longPressRunnable, LONG_PRESS_DELAY)

                    gestureDetector.onTouchEvent(event)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    lastTouchX = event.x

                    // If speed control is active, handle horizontal speed sliding
                    if (isSpeedControlActive) {
                        val deltaX = event.x - speedPressStartX
                        listener.onSpeedPressMove(deltaX)
                        return@setOnTouchListener true
                    }

                    val totalDeltaX = event.x - downX
                    val totalDeltaY = downY - event.y // positive = upward swipe

                    // Cancel long press if finger moved significantly
                    if (abs(totalDeltaX) > MOVE_THRESHOLD || abs(totalDeltaY) > MOVE_THRESHOLD) {
                        mainHandler.removeCallbacks(longPressRunnable)
                    }

                    // Determine gesture intent
                    if (!isSeeking && !isAdjustingBrightness && !isAdjustingVolume) {
                        if (abs(totalDeltaX) > MOVE_THRESHOLD && abs(totalDeltaX) > abs(totalDeltaY)) {
                            isSeeking = true
                        } else if (abs(totalDeltaY) > MOVE_THRESHOLD && abs(totalDeltaY) > abs(totalDeltaX)) {
                            if (downX < screenWidth / 2f) {
                                isAdjustingBrightness = true
                            } else {
                                isAdjustingVolume = true
                            }
                        }
                    }

                    if (isSeeking) {
                        val duration = getDuration().coerceAtLeast(1L)
                        val seekDelta = ((totalDeltaX / screenWidth) * 90_000).toLong()
                        val targetPos = (initialSeekPosition + seekDelta).coerceIn(0L, duration)
                        listener.onSeekGesture(seekDelta, targetPos)
                    } else if (isAdjustingBrightness) {
                        val deltaPercent = (totalDeltaY / screenHeight) * 1.2f
                        val newBrightness = (initialBrightness + deltaPercent).coerceIn(0.01f, 1.0f)

                        val lp = activity.window.attributes
                        lp.screenBrightness = newBrightness
                        activity.window.attributes = lp

                        listener.onBrightnessChanged((newBrightness * 100).toInt())
                    } else if (isAdjustingVolume) {
                        val deltaSteps = ((totalDeltaY / screenHeight) * maxVolume * 1.5f).toInt()
                        val targetVolume = (initialVolume + deltaSteps).coerceIn(0, maxVolume)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)

                        val volumePercent = if (maxVolume > 0) (targetVolume * 100) / maxVolume else 0
                        listener.onVolumeChanged(volumePercent)
                    }

                    gestureDetector.onTouchEvent(event)
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longPressRunnable)

                    if (isSpeedControlActive) {
                        isSpeedControlActive = false
                        listener.onSpeedPressEnd()
                        return@setOnTouchListener true
                    }

                    if (isSeeking) {
                        val totalDeltaX = event.x - downX
                        val duration = getDuration().coerceAtLeast(1L)
                        val seekDelta = ((totalDeltaX / screenWidth) * 90_000).toLong()
                        val targetPos = (initialSeekPosition + seekDelta).coerceIn(0L, duration)
                        listener.onSeekConfirmed(targetPos)
                    }
                    if (isAdjustingBrightness) {
                        val currentBrightness = activity.window.attributes.screenBrightness
                        if (currentBrightness in 0.01f..1.0f) {
                            listener.onBrightnessConfirmed(currentBrightness)
                        }
                    }
                    isSeeking = false
                    isAdjustingBrightness = false
                    isAdjustingVolume = false

                    gestureDetector.onTouchEvent(event)
                    true
                }

                else -> {
                    gestureDetector.onTouchEvent(event)
                    true
                }
            }
        }
    }
}
