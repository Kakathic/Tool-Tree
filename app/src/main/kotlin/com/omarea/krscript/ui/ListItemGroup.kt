package com.omarea.krscript.ui

import android.animation.Animator
import android.animation.LayoutTransition
import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.tool.tree.R
import com.omarea.krscript.model.GroupNode

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

    // Item clickable nằm trực tiếp ngoài group: ripple bo góc 10dp (cùng bán kính thẻ group), thu gọn padding dọc
    // (margin trên 15dp -> 8dp, dưới 10dp -> 6dp) và lề hai bên (lề trái nội dung 6dp -> 2dp, lề phải nút/switch 16dp -> 8dp)
    // cho giống item text. Lề của khối rows giữ nguyên vì độ rộng khối rows đã được dùng để canh zone khi dựng.
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
        val cutTop = (4 * d).toInt()
        val cutBottom = (4 * d).toInt()
        val startOld = (6 * d).toInt()
        val startNew = (2 * d).toInt()
        val endOld = (16 * d).toInt()
        val endNew = (8 * d).toInt()
        for (i in 0 until content.childCount) {
            val lp = content.getChildAt(i).layoutParams as? ViewGroup.MarginLayoutParams ?: continue
            lp.topMargin = (lp.topMargin - cutTop).coerceAtLeast(0)
            lp.bottomMargin = (lp.bottomMargin - cutBottom).coerceAtLeast(0)
            if (lp.marginStart == startOld) lp.marginStart = startNew
            if (lp.marginEnd == endOld) lp.marginEnd = endNew
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