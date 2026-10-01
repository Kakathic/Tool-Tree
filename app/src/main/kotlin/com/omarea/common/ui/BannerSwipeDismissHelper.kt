package com.omarea.common.ui

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs

class BannerSwipeDismissHelper(
    context: Context,
    private val target: View,
    private val onDismiss: () -> Unit
) {
    companion object {
        private const val COMMIT_DISTANCE_RATIO = 0.35f
        private const val SETTLE_DURATION_MS = 200L
        private const val FLY_OUT_DURATION_MS = 180L
    }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val maxFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity
    private val screenWidthPx = context.resources.displayMetrics.widthPixels

    private var velocityTracker: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var dragging = false

    private var dismissed = false

    init {
        target.setOnTouchListener { _, event -> handleTouch(event) }
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        if (dismissed) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                dragging = false
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().apply { addMovement(event) }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (!dragging) {
                    if (abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                        dragging = true
                        target.parent?.requestDisallowInterceptTouchEvent(true)
                    } else {
                        return false
                    }
                }
                target.translationX = dx
                target.alpha = (1f - abs(dx) / target.width.coerceAtLeast(1)).coerceIn(0.3f, 1f)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) {
                    velocityTracker?.recycle()
                    velocityTracker = null
                    return false
                }
                dragging = false
                val tracker = velocityTracker
                tracker?.computeCurrentVelocity(1000, maxFlingVelocity.toFloat())
                val velocityX = tracker?.xVelocity ?: 0f
                tracker?.recycle()
                velocityTracker = null

                val dx = target.translationX
                val width = target.width.coerceAtLeast(1)
                val shouldDismiss = abs(dx) > width * COMMIT_DISTANCE_RATIO || abs(velocityX) > minFlingVelocity
                if (shouldDismiss) {
                    flyOutAndDismiss(if (dx >= 0f) 1 else -1)
                } else {
                    settleBack()
                }
                return true
            }
        }
        return false
    }

    private fun flyOutAndDismiss(direction: Int) {
        dismissed = true
        val distance = (target.width + screenWidthPx).toFloat()
        target.animate()
            .translationX(direction * distance)
            .alpha(0f)
            .setDuration(FLY_OUT_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { onDismiss() }
            .start()
    }

    private fun settleBack() {
        target.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(SETTLE_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
}
