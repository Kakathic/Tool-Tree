package com.omarea.krscript.ui

import android.animation.Animator
import android.animation.LayoutTransition
import android.content.Context
import android.view.View
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import android.widget.LinearLayout
import com.omarea.common.ui.BlurEngine
import androidx.core.content.ContextCompat
import com.tool.tree.R
import com.omarea.krscript.model.GroupNode

class ListItemGroup(context: Context,
                    var isRootGroup: Boolean,
                    config: GroupNode,
                    val isCell: Boolean = false) :
        ListItemView(
                context,
                if (isRootGroup) R.layout.kr_group_list_root
                else if (isCell) R.layout.kr_group_cell_item
                else if (config.card) R.layout.kr_group_list_item_card
                else R.layout.kr_group_list_item,
                config) {
    protected var children = ArrayList<ListItemView>()

    private val columnWidthDp: Int = config.columnWidthDp
    private val minColumns: Int = config.minColumns.coerceIn(1, 6)
    private val maxColumns: Int = config.maxColumns.coerceIn(minColumns, 6)

    val compact: Boolean = config.compact

    val cardMode: Boolean = !isRootGroup && !isCell && config.card

    val gridMode: Boolean = !isRootGroup && !isCell && (config.columns > 1 || columnWidthDp > 0 || config.card)

    val childCard: Boolean get() = isCell || (gridMode && !cardMode)

    var columns: Int = if (isRootGroup) 1 else if (columnWidthDp > 0) {
        val metrics = context.resources.displayMetrics
        calcColumns(metrics.widthPixels - (32 * metrics.density).toInt())
    } else {
        config.columns.coerceIn(1, 6)
    }
        private set

    private val isGrid: Boolean get() = gridMode

    companion object {
        private const val CARD_GAP_DP = 16
    }

    private fun calcColumns(widthPx: Int): Int {
        val cellPx = columnWidthDp * context.resources.displayMetrics.density
        if (cellPx <= 0f || widthPx <= 0) return minColumns
        return (widthPx / cellPx).toInt().coerceIn(minColumns, maxColumns)
    }

    private fun updateColumnsByWidth(widthPx: Int) {
        if (widthPx <= 0) return
        val next = calcColumns(widthPx)
        if (next == columns) return
        columns = next
        layout.findViewById<ViewGroup>(android.R.id.content).post { relayoutGrid() }
    }

    private var gridLines = false

    val childCount: Int get() = children.size

    private val placeholderViews = ArrayList<View>()

    private fun applyRootPressEffect(item: ListItemView) {
        if (!isRootGroup || item !is ListItemClickable) return
        val view = item.getView()
        if (view.foreground != null) {
            view.foreground = ContextCompat.getDrawable(context, R.drawable.krscript_item_ripple_ac)
        }
    }

    fun enableDividers() {
        gridLines = true
        val content = layout.findViewById<ViewGroup>(android.R.id.content) as? LinearLayout ?: return
        val density = context.resources.displayMetrics.density
        content.dividerDrawable = StrokeDividerDrawable(context)
        content.dividerPadding = (16 * density).toInt()
        content.showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
    }

    private class StrokeDividerDrawable(private val context: Context) : Drawable() {
        override fun getIntrinsicHeight(): Int = 3

        override fun draw(canvas: Canvas) {
            val paint = BlurEngine.getStrokePaint(context)
            val y = bounds.exactCenterY()
            canvas.drawLine(bounds.left.toFloat(), y, bounds.right.toFloat(), y, paint)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private class VerticalStrokeDivider(private val context: Context) : Drawable() {
        override fun getIntrinsicWidth(): Int = 3

        override fun draw(canvas: Canvas) {
            val paint = BlurEngine.getStrokePaint(context)
            val x = bounds.exactCenterX()
            canvas.drawLine(x, bounds.top.toFloat(), x, bounds.bottom.toFloat(), paint)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    // Xếp lại con vào lưới: thẻ chia đều từng hàng; hàng cuối còn lẻ thì thẻ giãn full hàng, không chèn ô trống
    private fun relayoutGrid() {
        val content = layout.findViewById<ViewGroup>(android.R.id.content)
        val density = context.resources.displayMetrics.density
        val gap = if (cardMode) (CARD_GAP_DP * density).toInt() else 0
        val cellCount = children.count { (it is ListItemClickable && it.card) || (it is ListItemGroup && it.isCell) }
        val cols = if (columnWidthDp > 0) columns.coerceAtMost(cellCount.coerceAtLeast(1)) else columns
        content.removeAllViews()
        var row: LinearLayout? = null
        for (child in children) {
            val view = child.getView()
            (view.parent as? ViewGroup)?.removeView(view)
            if ((child is ListItemClickable && child.card) || (child is ListItemGroup && child.isCell)) {
                var current = row
                if (current == null || current.childCount >= cols) {
                    current = LinearLayout(context)
                    current.orientation = LinearLayout.HORIZONTAL
                    if (gridLines) {
                        current.dividerDrawable = VerticalStrokeDivider(context)
                        current.dividerPadding = (8 * density).toInt()
                        current.showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
                    }
                    content.addView(current, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        if (content.childCount > 0) topMargin = gap
                    })
                    row = current
                }
                current.addView(view, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    if (current.childCount > 0) marginStart = gap
                })
            } else {
                row = null
                content.addView(view, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    if (content.childCount > 0) topMargin = gap
                })
            }
        }
    }

    fun addPlaceholders(views: List<View>) {
        val content = layout.findViewById<ViewGroup>(android.R.id.content)
        for (view in views) {
            content.addView(view)
            placeholderViews.add(view)
        }
    }

    fun addViewBeforePlaceholder(item: ListItemView): ListItemGroup {
        if (isGrid) return addView(item)
        applyRootPressEffect(item)
        val content = layout.findViewById<ViewGroup>(android.R.id.content)
        val placeholder = placeholderViews.firstOrNull()
        if (placeholder != null) {
            val at = content.indexOfChild(placeholder)
            content.addView(item.getView(), if (at >= 0) at else content.childCount)
            (placeholder.tag as? Animator)?.cancel()
            content.removeView(placeholder)
            placeholderViews.remove(placeholder)
        } else {
            content.addView(item.getView())
        }
        children.add(item)
        return this
    }

    fun clearPlaceholders() {
        val content = layout.findViewById<ViewGroup>(android.R.id.content)
        for (view in placeholderViews) {
            (view.tag as? Animator)?.cancel()
            content.removeView(view)
        }
        placeholderViews.clear()
    }

    fun addView(item: ListItemView): ListItemGroup {
        applyRootPressEffect(item)
        if (isGrid) {
            children.add(item)
            relayoutGrid()
            return this
        }
        val content = layout.findViewById<ViewGroup>(android.R.id.content)
        content.addView(item.getView())
        children.add(item)
        return this
    }

    fun addView(item: ListItemView, atIndex: Int): ListItemGroup {
        applyRootPressEffect(item)
        if (isGrid) {
            children.add(atIndex.coerceIn(0, children.size), item)
            relayoutGrid()
            return this
        }
        val content = layout.findViewById<ViewGroup>(android.R.id.content)
        if (content.layoutTransition == null) {
            content.layoutTransition = LayoutTransition()
        }
        val at = atIndex.coerceIn(0, children.size)
        content.addView(item.getView(), at)
        children.add(at, item)
        return this
    }

    fun triggerActionByKey(key: String): Boolean {
        for (child in this.children) {
            if (child is ListItemClickable && (child.key == key || (child.key.isEmpty() && child.autoKey == key))) {
                child.triggerAction()
                return true
            } else if (child is ListItemGroup && child.triggerActionByKey(key)) {
                return true
            }
        }
        return false
    }

    fun triggerActionByIndex(index: String): Boolean {
        for (child in this.children) {
            if (child is ListItemClickable && child.index.equals(index)) {
                child.triggerAction()
                return true
            } else if (child is ListItemGroup && child.triggerActionByIndex(index)) {
                return true
            }
        }
        return false
    }

    fun triggerUpdateByKey(keys: Array<String>) {
        for (key in keys) {
            if (key.equals(this.key)) {
                triggerUpdate()
            } else {
                for (child in this.children) {
                    if (child is ListItemGroup) {
                        child.triggerUpdateByKey(keys)
                    } else if (child.key.equals(key)) {
                        child.updateViewByShell()
                    }
                }
            }
        }
    }

    fun triggerUpdate() {
        for (child in this.children) {
            if (child is ListItemGroup) {
                child.triggerUpdate()
            } else {
                child.updateViewByShell()
            }
        }
    }

    init {
        title = config.title
        if (gridMode && columnWidthDp > 0) {
            layout.findViewById<ViewGroup>(android.R.id.content)
                .addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
                    if (right - left != oldRight - oldLeft) updateColumnsByWidth(right - left)
                }
        }
    }
}