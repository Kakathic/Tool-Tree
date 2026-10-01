package com.tool.tree.ui

import android.content.Context
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.LinearLayout
import android.widget.ListPopupWindow

object SpinnerPopupHelper {

    private const val EDGE_INSET_DP = 16f

    @JvmStatic
    fun applyRoundedClip(popup: ListPopupWindow, radiusPx: Float) {
        val listView = popup.listView ?: return
        listView.divider = null
        listView.dividerHeight = 0
        listView.clipToOutline = true
        listView.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
            }
        }
        listView.isVerticalScrollBarEnabled = false
    }

    @JvmStatic
    fun applyWidthAndPosition(
        popup: ListPopupWindow,
        anchor: View,
        itemViews: List<View>,
        background: Drawable?,
        minWidthPx: Int,
        alignRight: Boolean,
        extraTopGapPx: Int = 0,
        applyVerticalOffset: Boolean = false
    ) {
        val context = anchor.context
        val bgPadding = Rect()
        background?.getPadding(bgPadding)
        val screenWidth = context.resources.displayMetrics.widthPixels
        val marginPx = dpToPx(context, EDGE_INSET_DP)
        val maxWidth = screenWidth.coerceAtLeast(minWidthPx)

        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        var maxItemWidth = 0
        for (itemView in itemViews) {
            val naturalWidth = measureNaturalWidth(itemView, unspecified)
            if (naturalWidth > maxItemWidth) {
                maxItemWidth = naturalWidth
            }
        }

        val contentWidth = maxItemWidth + bgPadding.left + bgPadding.right
        val desiredWidth = contentWidth.coerceAtLeast(minWidthPx).coerceAtMost(maxWidth)
        popup.width = desiredWidth

        val rowWidthPx = View.MeasureSpec.makeMeasureSpec(
            (desiredWidth - bgPadding.left - bgPadding.right).coerceAtLeast(0),
            View.MeasureSpec.EXACTLY
        )

        var contentHeight = 0
        for (itemView in itemViews) {
            itemView.measure(rowWidthPx, unspecified)
            contentHeight += itemView.measuredHeight
        }

        val screenHeight = context.resources.displayMetrics.heightPixels
        val topMarginPx = dpToPx(context, EDGE_INSET_DP)
        val maxHeight = (screenHeight - topMarginPx).coerceAtLeast(0)

        popup.height = (contentHeight + bgPadding.top + bgPadding.bottom).coerceAtMost(maxHeight)

        val anchorLocation = IntArray(2)
        anchor.getLocationOnScreen(anchorLocation)
        val anchorX = anchorLocation[0]

        var offset = if (alignRight) {
            (screenWidth - desiredWidth) - anchorX
        } else {
            val overflowRight = (anchorX + desiredWidth) - (screenWidth - marginPx)
            if (overflowRight > 0) -overflowRight else 0
        }
        val leftEdge = anchorX + offset
        if (leftEdge < marginPx) {
            offset += (marginPx - leftEdge)
        }
        popup.horizontalOffset = offset
        if (applyVerticalOffset) {
            popup.verticalOffset = if (extraTopGapPx > 0) -extraTopGapPx else -anchor.height
        }
    }

    private fun measureNaturalWidth(view: View, unspecified: Int): Int {
        val relaxed = HashMap<View, Int>()
        relaxWidths(view, relaxed)
        view.measure(unspecified, unspecified)
        val naturalWidth = view.measuredWidth
        for ((relaxedView, originalWidth) in relaxed) {
            relaxedView.layoutParams = relaxedView.layoutParams.apply { width = originalWidth }
        }
        return naturalWidth
    }

    private fun relaxWidths(view: View, relaxed: MutableMap<View, Int>) {
        val lp = view.layoutParams
        val isWeighted = lp is LinearLayout.LayoutParams && lp.weight > 0f
        if (lp != null && (lp.width == ViewGroup.LayoutParams.MATCH_PARENT || isWeighted)) {
            relaxed[view] = lp.width
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                relaxWidths(view.getChildAt(i), relaxed)
            }
        }
    }

    private fun dpToPx(context: Context, dp: Float): Int =
        (context.resources.displayMetrics.density * dp).toInt()
}
