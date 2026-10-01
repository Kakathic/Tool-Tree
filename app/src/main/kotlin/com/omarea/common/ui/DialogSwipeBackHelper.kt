package com.omarea.common.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.Window
import android.view.animation.DecelerateInterpolator
import android.widget.AbsSeekBar
import kotlin.math.abs

class DialogSwipeBackHelper(
    context: Context,
    private val contentView: View,
    private val onDragStateChanged: (dragging: Boolean) -> Unit = {},
    private val onDragProgress: (progress: Float) -> Unit = {},
    private val onBack: () -> Unit
) {
    companion object {
        private const val COMMIT_DISTANCE_RATIO = 0.28f
        private const val SETTLE_DURATION_MIN_MS = 150L
        private const val SETTLE_DURATION_MAX_MS = 300L

        fun bind(
            dialog: Dialog,
            contentView: View,
            onDragStateChanged: (dragging: Boolean) -> Unit = {},
            onDragProgress: (progress: Float) -> Unit = {},
            onBack: () -> Unit
        ): DialogSwipeBackHelper? {
            val window = dialog.window ?: return null
            val helper = DialogSwipeBackHelper(contentView.context, contentView, onDragStateChanged, onDragProgress, onBack)
            val original = window.callback
            window.callback = object : Window.Callback by original {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    if (helper.dispatchTouchEvent(event)) return true
                    return original.dispatchTouchEvent(event)
                }
            }
            return helper
        }
    }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val maxFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity

    private val dragElevationPx = 8f * context.resources.displayMetrics.density

    private val maxLeftPullPx = com.tool.tree.ui.SwipeBounceEffect.maxPullPx(context.resources.displayMetrics.density)

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
                candidate = isIdle && !isTouchOnSeekBar(contentView, ev.rawX, ev.rawY)
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
                        dx > touchSlop && dx > abs(dy) -> {
                            beginNewDragSession()
                            dragging = true
                            onDragStateChanged(true)
                            contentView.elevation = dragElevationPx
                            val cancelEvent = MotionEvent.obtain(ev)
                            cancelEvent.action = MotionEvent.ACTION_CANCEL
                            contentView.dispatchTouchEvent(cancelEvent)
                            cancelEvent.recycle()
                        }
                        dx < -touchSlop && abs(dx) > abs(dy) -> {
                            beginNewDragSession()
                            draggingLeft = true
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
                    val pulled = com.tool.tree.ui.SwipeBounceEffect.dampen((-dx).coerceAtLeast(0f), maxLeftPullPx)
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
                    animateTo(0f, 0f, null, durationMultiplier = 1.3f, bounce = true, notifyStateChange = false)
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

    private fun isTouchOnSeekBar(view: View, rawX: Float, rawY: Float): Boolean {
        if (view.visibility != View.VISIBLE) return false
        if (view is AbsSeekBar) {
            if (!view.isEnabled) return false
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            val left = location[0]
            val top = location[1]
            val right = left + view.width
            val bottom = top + view.height
            return rawX >= left && rawX <= right && rawY >= top && rawY <= bottom
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (isTouchOnSeekBar(view.getChildAt(i), rawX, rawY)) return true
            }
        }
        return false
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
    }

    private fun settleAfterDrag(velocityX: Float) {
        val width = contentView.width.takeIf { it > 0 } ?: return
        val distance = contentView.translationX
        val shouldGoBack = distance > width * COMMIT_DISTANCE_RATIO || velocityX > minFlingVelocity

        if (shouldGoBack) {
            animateTo(width.toFloat(), velocityX, onEnd = { onBack() })
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
            interpolator = if (bounce) com.tool.tree.ui.SwipeBounceEffect.bounceInterpolator else DecelerateInterpolator(1.2f)
            addUpdateListener { applyProgress(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
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
