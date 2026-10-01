package com.tool.tree.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Outline
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import com.omarea.common.ui.BlurEngine
import com.tool.tree.ThemeModeState
import kotlin.math.abs

class SwipeBackHelper(
    private val activity: Activity,
    private val contentView: View,
    private val onDragStateChanged: (dragging: Boolean) -> Unit = {},
    private val onDragProgress: (progress: Float) -> Unit = {},
    private val onBack: () -> Unit = { activity.finish(); activity.overridePendingTransition(0, 0) }
) {
    companion object {
        private const val COMMIT_DISTANCE_RATIO = 0.28f

        private const val SETTLE_DURATION_MIN_MS = 150L
        private const val SETTLE_DURATION_MAX_MS = 300L
    }

    private val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(activity).scaledMinimumFlingVelocity
    private val maxFlingVelocity = ViewConfiguration.get(activity).scaledMaximumFlingVelocity

    private val dragElevationPx = 8f * activity.resources.displayMetrics.density

    private val maxLeftPullPx = SwipeBounceEffect.maxPullPx(activity.resources.displayMetrics.density)

    init {
        applyThemeBackgroundIfMissing()

        contentView.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRect(0, 0, view.width, view.height)
                outline.alpha = 1f
            }
        }
    }

    private fun applyThemeBackgroundIfMissing() {
        if (contentView.background != null) return
        val decorBackground = activity.window.decorView.background ?: return
        contentView.background = decorBackground.constantState
            ?.newDrawable(activity.resources)
            ?.mutate()
            ?: decorBackground
    }

    private var velocityTracker: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f

    private var candidate = false

    private var dragging = false

    private var draggingLeft = false

    private var settleAnimator: ValueAnimator? = null
    var enabled = true

    private var dragSessionId = 0

    fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!enabled) return false

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val isIdle = settleAnimator?.isRunning != true &&
                    contentView.translationX == 0f
                candidate = isIdle
                dragging = false
                draggingLeft = false
                downX = ev.rawX
                downY = ev.rawY
                if (candidate) {
                    velocityTracker?.recycle()
                    velocityTracker = VelocityTracker.obtain()
                    velocityTracker?.addMovement(ev)
                }
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!candidate && !dragging && !draggingLeft) return false
                velocityTracker?.addMovement(ev)

                val dx = ev.rawX - downX
                val dy = ev.rawY - downY

                if (!dragging && !draggingLeft) {
                    when {
                        dx > touchSlop && dx > abs(dy) * 1.2f -> {
                            beginNewDragSession()
                            dragging = true
                            onDragStateChanged(true)
                            contentView.elevation = dragElevationPx

                            val cancelEvent = MotionEvent.obtain(ev)
                            cancelEvent.action = MotionEvent.ACTION_CANCEL
                            contentView.dispatchTouchEvent(cancelEvent)
                            cancelEvent.recycle()
                        }
                        dx < -touchSlop && abs(dx) > abs(dy) * 1.2f -> {
                            beginNewDragSession()
                            draggingLeft = true
                            onDragStateChanged(true)
                            val cancelEvent = MotionEvent.obtain(ev)
                            cancelEvent.action = MotionEvent.ACTION_CANCEL
                            contentView.dispatchTouchEvent(cancelEvent)
                            cancelEvent.recycle()
                        }
                        abs(dy) > touchSlop -> {
                            candidate = false
                            return false
                        }
                        else -> return false
                    }
                }

                if (dragging) {
                    val clampedDx = dx.coerceIn(0f, contentView.width.toFloat().coerceAtLeast(1f))
                    applyProgress(clampedDx)
                    return true
                }

                if (draggingLeft) {
                    val pulled = SwipeBounceEffect.dampen((-dx).coerceAtLeast(0f), maxLeftPullPx)
                    contentView.translationX = -pulled
                    return true
                }
                return false
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasDragging = dragging
                val wasDraggingLeft = draggingLeft
                if (wasDragging) {
                    velocityTracker?.addMovement(ev)
                    velocityTracker?.computeCurrentVelocity(1000, maxFlingVelocity.toFloat())
                    val velocityX = velocityTracker?.xVelocity ?: 0f
                    settleAfterDrag(velocityX)
                } else if (wasDraggingLeft) {
                    animateTo(0f, 0f, null, durationMultiplier = 1.3f, bounce = true, notifyStateChange = true)
                }
                recycleTracker()
                candidate = false
                dragging = false
                draggingLeft = false
                return wasDragging || wasDraggingLeft
            }
        }
        return false
    }

    private fun recycleTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun applyProgress(dx: Float) {
        contentView.translationX = dx
        val width = contentView.width.takeIf { it > 0 } ?: 1
        onDragProgress((dx / width).coerceIn(0f, 1f))
    }

    private fun beginNewDragSession() {
        settleAnimator?.cancel()
        settleAnimator = null
        dragSessionId++
        BlurEngine.isPaused = true
    }

    private fun settleAfterDrag(velocityX: Float) {
        val width = contentView.width.takeIf { it > 0 } ?: return
        val distance = contentView.translationX
        val shouldGoBack = distance > width * COMMIT_DISTANCE_RATIO || velocityX > minFlingVelocity

        if (shouldGoBack) {
            animateTo(width.toFloat(), velocityX, { onBack() })
        } else {
            animateTo(0f, velocityX, null, durationMultiplier = 1.3f, bounce = true)
        }
    }

    private fun animateTo(
        target: Float,
        velocityX: Float,
        onEnd: (() -> Unit)?,
        durationMultiplier: Float = 1f,
        bounce: Boolean = false,
        notifyStateChange: Boolean = true
    ) {
        val start = contentView.translationX
        val distance = abs(target - start)
        val duration = (computeSettleDuration(distance, velocityX) * durationMultiplier).toLong()

        val sessionAtStart = dragSessionId

        settleAnimator?.cancel()
        settleAnimator = ValueAnimator.ofFloat(start, target).apply {
            this.duration = duration
            interpolator = if (bounce) SwipeBounceEffect.bounceInterpolator else DecelerateInterpolator(1.2f)
            addUpdateListener { applyProgress(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    BlurEngine.isPaused = !ThemeModeState.isBlurActive()
                    val isStale = dragSessionId != sessionAtStart
                    if (target == 0f && !isStale) {
                        contentView.translationX = 0f
                        contentView.elevation = 0f
                        if (notifyStateChange) onDragStateChanged(false)
                    }
                    onEnd?.invoke()
                }
            })
            start()
        }
    }

    private fun computeSettleDuration(distancePx: Float, velocityPxPerSec: Float): Long {
        if (distancePx <= 0f) return SETTLE_DURATION_MIN_MS
        val velocity = abs(velocityPxPerSec).coerceAtLeast(1f)
        val estimatedMs = (distancePx / velocity * 1000).toLong()
        return estimatedMs.coerceIn(SETTLE_DURATION_MIN_MS, SETTLE_DURATION_MAX_MS)
    }

    fun release() {
        settleAnimator?.cancel()
        settleAnimator = null
        recycleTracker()
    }
}
