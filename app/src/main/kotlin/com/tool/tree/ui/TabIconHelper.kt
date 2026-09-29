package com.tool.tree.ui

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.tabs.TabLayout
import com.tool.tree.R
import kotlin.math.min

class TabIconHelper(
    private val activity: Activity
) {

    private val views = ArrayList<View>()
    private var highlightPosition = -1
    private var tabTouching = false
    private var pagerDragging = false

    fun createTabView(text: String, drawable: Drawable, isFirst: Boolean): View {
        val layout = View.inflate(activity, R.layout.list_item_tab, null)

        val iconBadge = layout.findViewById<View>(R.id.IconBadge)
        val imageView = layout.findViewById<ImageView>(R.id.ItemIcon)
        val textView = layout.findViewById<TextView>(R.id.ItemTitle)

        textView.text = text
        imageView.setImageDrawable(drawable)

        val badge = TabBadgeDrawable(ContextCompat.getColor(activity, R.color.tabIconBadgeBg))
        badge.snapTo(if (isFirst) BadgeState.EXPANDED else BadgeState.HIDDEN)
        iconBadge.background = badge
        if (isFirst) highlightPosition = views.size

        layout.alpha = if (isFirst) 1f else 0.3f
        views.add(layout)
        return layout
    }

    fun updateHighlight(tabLayout: TabLayout, position: Int) {
        highlightPosition = position
        for (i in 0 until tabLayout.tabCount) {
            val tabView = tabLayout.getTabAt(i)?.customView ?: continue
            tabView.alpha = if (i == position) 1f else 0.3f
        }
        applyBadges(tabLayout)
    }

    // Đang chạm vào thanh tab (ACTION_DOWN..UP): badge giữ hình tròn, thả tay mới mở rộng
    fun setTabTouching(tabLayout: TabLayout, touching: Boolean) {
        if (tabTouching == touching) return
        tabTouching = touching
        applyBadges(tabLayout)
    }

    // Đang kéo SwipePager bằng tay: badge giữ hình tròn, thả tay mới mở rộng
    fun setPagerDragging(tabLayout: TabLayout, dragging: Boolean) {
        if (pagerDragging == dragging) return
        pagerDragging = dragging
        applyBadges(tabLayout)
    }

    private fun applyBadges(tabLayout: TabLayout) {
        val holding = tabTouching || pagerDragging
        for (i in 0 until tabLayout.tabCount) {
            val badge = tabLayout.getTabAt(i)?.customView
                ?.findViewById<View>(R.id.IconBadge)?.background as? TabBadgeDrawable ?: continue
            badge.animateTo(
                when {
                    i != highlightPosition -> BadgeState.HIDDEN
                    holding -> BadgeState.CIRCLE
                    else -> BadgeState.EXPANDED
                }
            )
        }
    }
}

private enum class BadgeState { HIDDEN, CIRCLE, EXPANDED }

// Nền badge tự vẽ: hình tròn (đường kính = chiều cao) nở dần ngang thành hình viên thuốc
// đúng kích thước khung IconBadge. Chỉ invalidate, không gây relayout.
private class TabBadgeDrawable(color: Int) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private val baseAlpha = Color.alpha(color)
    private val rect = RectF()

    private var state = BadgeState.HIDDEN
    private var visibility = 0f
    private var expand = 0f
    private var outerAlpha = 255
    private var animator: ValueAnimator? = null

    fun snapTo(target: BadgeState) {
        animator?.cancel()
        state = target
        visibility = if (target == BadgeState.HIDDEN) 0f else 1f
        expand = if (target == BadgeState.EXPANDED) 1f else 0f
        invalidateSelf()
    }

    fun animateTo(target: BadgeState) {
        if (target == state) return
        state = target

        val fromVisibility = visibility
        val toVisibility = if (target == BadgeState.HIDDEN) 0f else 1f
        val fromExpand = if (fromVisibility <= 0.001f && target != BadgeState.HIDDEN) 0f else expand
        val toExpand = when (target) {
            BadgeState.HIDDEN -> fromExpand
            BadgeState.CIRCLE -> 0f
            BadgeState.EXPANDED -> 1f
        }

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (target == BadgeState.HIDDEN) HIDE_MS else EXPAND_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val f = it.animatedValue as Float
                visibility = fromVisibility + (toVisibility - fromVisibility) * f
                expand = fromExpand + (toExpand - fromExpand) * f
                invalidateSelf()
            }
            start()
        }
    }

    override fun draw(canvas: Canvas) {
        if (visibility <= 0f) return
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return

        val circle = min(w, h)
        val current = circle + (w - circle) * expand
        val left = bounds.left + (w - current) / 2f
        rect.set(left, bounds.top.toFloat(), left + current, bounds.bottom.toFloat())

        paint.alpha = (baseAlpha * visibility * outerAlpha / 255f).toInt()
        val radius = min(current, h) / 2f
        canvas.drawRoundRect(rect, radius, radius, paint)
    }

    override fun setAlpha(alpha: Int) {
        outerAlpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val EXPAND_MS = 220L
        const val HIDE_MS = 160L
    }
}
