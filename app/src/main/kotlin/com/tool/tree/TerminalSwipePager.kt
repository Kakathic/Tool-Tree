package com.tool.tree

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.roundToInt

class TerminalSwipePager @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    companion object {
        const val PAGE_SPECIAL = 0
        const val PAGE_KEYS = 1
        const val PAGE_INPUT = 2
    }

    var onPageChanged: ((Int) -> Unit)? = null
    var page = 0
        private set

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val flingVelocity = 600 * resources.displayMetrics.density

    private var offset = 0f
    private var downX = 0f
    private var downY = 0f
    private var startOffset = 0f
    private var dragging = false
    private var tracker: VelocityTracker? = null
    private var animator: ValueAnimator? = null

    private val lastPage: Int get() = (childCount - 1).coerceAtLeast(0)

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (animator == null && !dragging) offset = page * width.toFloat()
        applyOffset()
    }

    private fun applyOffset() {
        val w = width.toFloat()
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val tx = i * w - offset
            child.translationX = tx
            child.visibility = if (w > 0f && abs(tx) >= w) View.INVISIBLE else View.VISIBLE
        }
    }

    fun setPage(index: Int, animate: Boolean) {
        settle(index.coerceIn(0, lastPage), animate)
    }

    private fun settle(target: Int, animate: Boolean = true) {
        animator?.cancel()
        val to = target * width.toFloat()
        val changed = target != page
        if (!animate || width == 0) {
            offset = to
            page = target
            applyOffset()
            if (changed) onPageChanged?.invoke(page)
            return
        }
        animator = ValueAnimator.ofFloat(offset, to).apply {
            duration = 180
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                offset = it.animatedValue as Float
                applyOffset()
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled) return
                    animator = null
                    offset = to
                    page = target
                    applyOffset()
                    if (changed) onPageChanged?.invoke(page)
                }
            })
            start()
        }
    }

    private fun maybeStartDrag(ev: MotionEvent) {
        if (dragging) return
        val dx = ev.x - downX
        val dy = ev.y - downY
        val towardNext = page < lastPage && dx < 0
        val towardPrev = page > 0 && dx > 0
        if (abs(dx) > touchSlop && abs(dx) > abs(dy) && (towardNext || towardPrev)) {
            dragging = true
            startOffset = offset
            downX = ev.x
            parent?.requestDisallowInterceptTouchEvent(true)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tracker?.recycle()
                tracker = VelocityTracker.obtain()
                downX = ev.x
                downY = ev.y
                dragging = false
                animator?.cancel()
                animator = null
                offset = page * width.toFloat()
                applyOffset()
            }
            MotionEvent.ACTION_MOVE -> maybeStartDrag(ev)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
            }
        }
        tracker?.addMovement(ev)
        return dragging
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        tracker?.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                maybeStartDrag(ev)
                if (dragging) {
                    val w = width.toFloat()
                    val minOffset = (page - 1).coerceAtLeast(0) * w
                    val maxOffset = (page + 1).coerceAtMost(lastPage) * w
                    offset = (startOffset - (ev.x - downX)).coerceIn(minOffset, maxOffset)
                    applyOffset()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    tracker?.computeCurrentVelocity(1000)
                    val vx = tracker?.xVelocity ?: 0f
                    val target = when {
                        ev.actionMasked == MotionEvent.ACTION_CANCEL -> page
                        abs(vx) > flingVelocity -> if (vx < 0) page + 1 else page - 1
                        width > 0 -> (offset / width).roundToInt()
                        else -> page
                    }.coerceIn((page - 1).coerceAtLeast(0), (page + 1).coerceAtMost(lastPage))
                    dragging = false
                    settle(target)
                }
                tracker?.recycle()
                tracker = null
            }
        }
        return dragging || ev.actionMasked == MotionEvent.ACTION_DOWN
    }
}
