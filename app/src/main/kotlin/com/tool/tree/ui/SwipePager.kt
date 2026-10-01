package com.tool.tree.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import kotlin.math.abs
import kotlin.math.max

class SwipePager @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    interface OnPageChangeListener {
        fun onPageSelected(position: Int) {}
        fun onPageScrolled(position: Int, offset: Float) {}
        fun onPageScrollStateChanged(state: Int) {}
    }

    companion object {
        const val SCROLL_STATE_IDLE = 0
        const val SCROLL_STATE_DRAGGING = 1
        const val SCROLL_STATE_SETTLING = 2

        private const val SETTLE_DURATION_MIN_MS = 150
        private const val SETTLE_DURATION_MAX_MS = 320
        private const val SETTLE_DURATION_DEFAULT_MS = 300

        private const val MAX_OVERSCROLL_RATIO = 0.40f
        private const val MIN_OVERSCROLL_RESISTANCE = 0.15f
    }

    interface PageTransformer {
        fun transformPage(page: View, position: Float)
    }

    private val pages = ArrayList<View>()
    private var listener: OnPageChangeListener? = null
    private var pageTransformer: PageTransformer? = null

    var currentItem: Int = 0
        private set

    private val scroller = OverScroller(context, DecelerateInterpolator(1.4f))
    private var velocityTracker: VelocityTracker? = null
    private val minFlingVelocity: Int
    private val maxFlingVelocity: Int

    private val touchSlop: Int

    private var initialX = 0f
    private var initialY = 0f
    private var lastX = 0f
    private var isDragging = false

    private var pendingItem: Int? = null
    private var interruptedAnimation = false
    private var isHardwareLayerAttached = false
    private var scrollState = SCROLL_STATE_IDLE

    private fun setScrollState(state: Int) {
        if (scrollState == state) return
        scrollState = state
        listener?.onPageScrollStateChanged(state)
    }

    init {
        val vc = ViewConfiguration.get(context)
        minFlingVelocity = vc.scaledMinimumFlingVelocity
        maxFlingVelocity = vc.scaledMaximumFlingVelocity
        touchSlop = (vc.scaledTouchSlop * 0.6f).toInt()
    }

    fun setOnPageChangeListener(l: OnPageChangeListener) {
        listener = l
    }

    override fun shouldDelayChildPressedState(): Boolean = true

    fun setPageTransformer(transformer: PageTransformer?) {
        pageTransformer = transformer
        applyPageTransform()
    }

    private fun applyPageTransform() {
        val transformer = pageTransformer ?: return
        if (width == 0) return
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val position = (child.left - scrollX).toFloat() / width
            transformer.transformPage(child, position)
        }
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        applyPageTransform()
    }

    fun addPage(view: View) {
        pages.add(view)
        addView(view)
        requestLayout()
        applyPageTransform()
    }

    fun getPageCount(): Int = pages.size

    private fun enableHardwareLayers() {
        if (!isHardwareLayerAttached) {
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child.visibility == VISIBLE && child.layerType != LAYER_TYPE_HARDWARE) {
                    child.setLayerType(LAYER_TYPE_HARDWARE, null)
                }
            }
            isHardwareLayerAttached = true
        }
    }

    private fun disableHardwareLayers() {
        if (isHardwareLayerAttached) {
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child.layerType != LAYER_TYPE_NONE) {
                    child.setLayerType(LAYER_TYPE_NONE, null)
                }
            }
            isHardwareLayerAttached = false
        }
    }

    fun setCurrentItem(position: Int, smoothScroll: Boolean = true, durationMs: Int = SETTLE_DURATION_DEFAULT_MS) {
        if (pages.isEmpty()) return
        val target = position.coerceIn(0, pages.size - 1)

        if (width == 0) {
            pendingItem = target
            if (currentItem != target) {
                currentItem = target
                listener?.onPageSelected(target)
            }
            return
        }

        val targetX = target * width
        scroller.abortAnimation()
        if (smoothScroll) {
            enableHardwareLayers()
            if (targetX != scrollX) {
                setScrollState(SCROLL_STATE_SETTLING)
            }
            scroller.startScroll(scrollX, 0, targetX - scrollX, 0, durationMs)
            postInvalidateOnAnimation()
        } else {
            scrollTo(targetX, 0)
            if (currentItem != target) {
                currentItem = target
                listener?.onPageSelected(target)
            }
        }
    }

    private fun computeSettleDuration(distancePx: Int, velocityPxPerSec: Float): Int {
        val distance = abs(distancePx)
        if (distance == 0) return SETTLE_DURATION_MIN_MS
        val velocity = abs(velocityPxPerSec).coerceAtLeast(1f)
        val estimatedMs = (distance / velocity * 1000).toInt()
        return estimatedMs.coerceIn(SETTLE_DURATION_MIN_MS, SETTLE_DURATION_MAX_MS)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val childWidthSpec = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY)
        val childHeightSpec = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) {
            getChildAt(i).measure(childWidthSpec, childHeightSpec)
        }
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val left = i * w
            child.layout(left, 0, left + w, b - t)
        }
        if (changed && w > 0) {
            val target = pendingItem ?: currentItem
            pendingItem = null
            scrollTo(target * w, 0)
        }
        applyPageTransform()
    }

    private fun handleActionDown(ev: MotionEvent) {
        initialX = ev.x
        initialY = ev.y
        lastX = ev.x
        interruptedAnimation = !scroller.isFinished
        if (interruptedAnimation) scroller.abortAnimation()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (pages.size <= 1) return false

        val action = ev.actionMasked
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            if (interruptedAnimation && !isDragging) {
                settle(0f)
            }
            resetTouchState()
            return false
        }
        if (action != MotionEvent.ACTION_DOWN && isDragging) return true

        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()
        velocityTracker?.addMovement(ev)

        if (action == MotionEvent.ACTION_DOWN) {
            isDragging = false
            handleActionDown(ev)
        } else if (action == MotionEvent.ACTION_MOVE) {
            val dx = ev.x - initialX
            val dy = ev.y - initialY
            if (abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                isDragging = true
                setScrollState(SCROLL_STATE_DRAGGING)
                lastX = ev.x
                parent?.requestDisallowInterceptTouchEvent(true)
            }
        }
        return isDragging
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (pages.size <= 1) return false

        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()
        velocityTracker?.addMovement(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                handleActionDown(ev)
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) {
                    val dx = ev.x - initialX
                    val dy = ev.y - initialY
                    if (abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                        isDragging = true
                        setScrollState(SCROLL_STATE_DRAGGING)
                    }
                }
                if (isDragging) {
                    enableHardwareLayers()
                    var dx = lastX - ev.x
                    lastX = ev.x

                    val maxScroll = max(0, (pages.size - 1) * width).toFloat()
                    val maxOverscroll = width * MAX_OVERSCROLL_RATIO

                    if (scrollX < 0 && dx < 0) {
                        val overscroll = -scrollX
                        val resistance = (1f - overscroll / maxOverscroll).coerceIn(MIN_OVERSCROLL_RESISTANCE, 1f)
                        dx *= resistance
                    } else if (scrollX > maxScroll && dx > 0) {
                        val overscroll = scrollX - maxScroll
                        val resistance = (1f - overscroll / maxOverscroll).coerceIn(MIN_OVERSCROLL_RESISTANCE, 1f)
                        dx *= resistance
                    }

                    val newScrollX = (scrollX + dx).coerceIn(-maxOverscroll, maxScroll + maxOverscroll)
                    scrollTo(newScrollX.toInt(), 0)
                    notifyScrolled()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    velocityTracker?.computeCurrentVelocity(1000, maxFlingVelocity.toFloat())
                    settle(velocityTracker?.xVelocity ?: 0f)
                } else if (interruptedAnimation) {
                    settle(0f)
                }
                resetTouchState()
            }
        }
        return true
    }

    private fun resetTouchState() {
        interruptedAnimation = false
        isDragging = false
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun settle(velocityX: Float) {
        if (width == 0 || pages.isEmpty()) return
        val maxScroll = max(0, (pages.size - 1) * width)

        if (scrollX < 0) {
            setCurrentItem(0, true, computeSettleDuration(-scrollX, velocityX))
            return
        } else if (scrollX > maxScroll) {
            setCurrentItem(pages.size - 1, true, computeSettleDuration(scrollX - maxScroll, velocityX))
            return
        }

        val page = width
        val current = scrollX / page
        val fraction = (scrollX % page).toFloat() / page

        val target = when {
            abs(velocityX) > minFlingVelocity -> if (velocityX < 0) current + 1 else current
            fraction > 0.5f -> current + 1
            else -> current
        }.coerceIn(0, pages.size - 1)

        val distanceToTarget = target * width - scrollX
        setCurrentItem(target, true, computeSettleDuration(distanceToTarget, velocityX))
    }

    private fun notifyScrolled() {
        if (width == 0 || pages.isEmpty()) return
        val maxScroll = max(0, (pages.size - 1) * width)
        val clampedScrollX = scrollX.coerceIn(0, maxScroll)
        val position = (clampedScrollX / width).coerceIn(0, pages.size - 1)
        val offset = (clampedScrollX % width).toFloat() / width
        listener?.onPageScrolled(position, offset)
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            enableHardwareLayers()
            scrollTo(scroller.currX, scroller.currY)
            notifyScrolled()
            postInvalidateOnAnimation()
        } else {
            disableHardwareLayers()
            if (!isDragging && width > 0 && pages.isNotEmpty()) {
                val settled = (scrollX / width).coerceIn(0, pages.size - 1)
                if (settled != currentItem) {
                    currentItem = settled
                    listener?.onPageSelected(settled)
                }
                setScrollState(SCROLL_STATE_IDLE)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scroller.abortAnimation()
        disableHardwareLayers()
        if (velocityTracker != null) {
            velocityTracker?.recycle()
            velocityTracker = null
        }
    }
}