package com.omarea.krscript.ui

import android.animation.Animator
import android.animation.LayoutTransition
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.core.content.ContextCompat
import com.tool.tree.R
import com.omarea.krscript.model.GroupNode

// Lề (dp) của các item nằm trực tiếp ngoài group, chỉnh tại đây.
// ITEM_*: item clickable (Action, Page, Picker, Editor, Terminal, Chat, Switch, Download).
// ITEM_MIN_HEIGHT_DP: chiều cao tối thiểu (0 = bỏ mức tối thiểu mặc định của hệ thống ~64dp); nội dung luôn căn giữa dọc.
// Muốn nội dung nằm giữa khi chiều cao theo lề thì đặt ITEM_TOP_DP = ITEM_BOTTOM_DP.
// TEXT_*: item text. TEXT_START mặc định = ITEM_START + TITLE_INSET để chữ text thẳng hàng với tiêu đề item.
object RootItemSpacing {
    const val ITEM_TOP_DP = 10
    const val ITEM_BOTTOM_DP = 0
    const val ITEM_START_DP = 1
    const val ITEM_END_DP = 1
    const val ITEM_MIN_HEIGHT_DP = 0

    const val TEXT_TOP_DP = 8
    const val TEXT_BOTTOM_DP = 6
    const val TEXT_START_DP = 0
    const val TEXT_END_DP = 0
}

class ListItemGroup(context: Context,
                    var isRootGroup: Boolean,
                    config: GroupNode) :
        ListItemView(
                context,
                if (isRootGroup) R.layout.kr_group_list_root else R.layout.kr_group_list_item,
                config) {
    protected var children = ArrayList<ListItemView>()

    val childCount: Int get() = children.size

    private val placeholderViews = ArrayList<View>()

    private fun applyRootPressEffect(item: ListItemView) {
        if (!isRootGroup || item !is ListItemClickable) return
        val view = item.getView()
        if (view.foreground != null) {
            view.foreground = ContextCompat.getDrawable(context, R.drawable.krscript_item_ripple_ac)
        }
        compactRootPadding(view)
    }

    private fun compactRootPadding(view: View) {
        if (view.getTag(R.id.kr_layout_ac) != null) return
        val content = view.findViewById<ViewGroup>(R.id.kr_layout_ac) ?: return
        val d = context.resources.displayMetrics.density
        fun px(dp: Int) = (dp * d).toInt()

        view.minimumHeight = px(RootItemSpacing.ITEM_MIN_HEIGHT_DP)
        (content.layoutParams as? RelativeLayout.LayoutParams)?.addRule(RelativeLayout.CENTER_VERTICAL)

        var row: ViewGroup.MarginLayoutParams? = null
        for (i in 0 until content.childCount) {
            val child = content.getChildAt(i)
            if (child is ViewGroup) {
                row = child.layoutParams as? ViewGroup.MarginLayoutParams
                break
            }
        }
        if (row == null) return
        val rowTop = row.topMargin
        val rowBottom = row.bottomMargin

        for (i in 0 until content.childCount) {
            val lp = content.getChildAt(i).layoutParams as? ViewGroup.MarginLayoutParams ?: continue
            if (lp === row) {
                lp.topMargin = px(RootItemSpacing.ITEM_TOP_DP)
                lp.bottomMargin = px(RootItemSpacing.ITEM_BOTTOM_DP)
                lp.marginStart = px(RootItemSpacing.ITEM_START_DP)
            } else {
                // Nút / switch / vòng tải bên phải: giữ độ lệch so với khối tiêu đề như layout gốc để vẫn thẳng hàng
                lp.topMargin = (px(RootItemSpacing.ITEM_TOP_DP) + lp.topMargin - rowTop).coerceAtLeast(0)
                lp.bottomMargin = (px(RootItemSpacing.ITEM_BOTTOM_DP) + lp.bottomMargin - rowBottom).coerceAtLeast(0)
                lp.marginEnd = px(RootItemSpacing.ITEM_END_DP)
            }
        }
        view.setTag(R.id.kr_layout_ac, true)
    }

    fun addPlaceholders(views: List<View>) {
        val content = layout.findViewById<ViewGroup>(android.R.id.content)
        for (view in views) {
            content.addView(view)
            placeholderViews.add(view)
        }
    }

    fun addViewBeforePlaceholder(item: ListItemView): ListItemGroup {
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
        val content = layout.findViewById<ViewGroup>(android.R.id.content)
        content.addView(item.getView())
        children.add(item)
        return this
    }

    fun addView(item: ListItemView, atIndex: Int): ListItemGroup {
        applyRootPressEffect(item)
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
            if (child is ListItemClickable && child.key.equals(key)) {
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
    }
}