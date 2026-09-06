package com.omarea.krscript.ui

import android.animation.Animator
import android.animation.LayoutTransition
import android.content.Context
import android.view.View
import android.view.ViewGroup
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

    // load-after: số view thực tế đã có trong group này - dùng để ghi lại "vị trí đúng" của 1
    // group con đang rỗng (xem PageLayoutRender.renderNode()/insertNode()).
    val childCount: Int get() = children.size

    // process = true: các khung skeleton/placeholder đang hiện tạm trong lúc chờ item thật build
    // xong - xem PageLayoutRender.addLoadingPlaceholders()/appendNode(). Animator gắn ở
    // view.tag (do bên tạo view set) được huỷ khi placeholder bị gỡ, tránh chạy vô ích sau khi
    // view đã rời layout.
    private val placeholderViews = ArrayList<View>()

    fun addPlaceholders(views: List<View>) {
        val content = layout.findViewById<ViewGroup>(android.R.id.content)
        for (view in views) {
            content.addView(view)
            placeholderViews.add(view)
        }
    }

    // Thế 1 item THẬT vào ĐÚNG vị trí của khung placeholder ĐẦU TIÊN (nếu còn) rồi gỡ khung đó -
    // giữ item xuất hiện đúng chỗ thay vì luôn nối sau các placeholder còn lại. Hết placeholder
    // thì quay về hành vi thêm cuối như addView() thường. Bật LayoutTransition để item mới
    // fade-in mượt thay vì hiện đột ngột (giống cơ chế load-after).
    fun addViewReplacingPlaceholder(item: ListItemView): ListItemGroup {
        val content = layout.findViewById<ViewGroup>(android.R.id.content)
        if (content.layoutTransition == null) {
            content.layoutTransition = LayoutTransition()
        }
        val placeholder = if (placeholderViews.isNotEmpty()) placeholderViews.removeAt(0) else null
        if (placeholder != null) {
            val at = content.indexOfChild(placeholder)
            (placeholder.tag as? Animator)?.cancel()
            content.removeView(placeholder)
            content.addView(item.getView(), if (at >= 0) at else content.childCount)
        } else {
            content.addView(item.getView())
        }
        children.add(item)
        return this
    }

    // Gỡ hết placeholder còn dư (vd trang có ít item thật hơn số khung đã hiện sẵn) - gọi khi
    // trang process = true đã build xong toàn bộ.
    fun clearPlaceholders() {
        val content = layout.findViewById<ViewGroup>(android.R.id.content)
        for (view in placeholderViews) {
            (view.tag as? Animator)?.cancel()
            content.removeView(view)
        }
        placeholderViews.clear()
    }

    fun addView(item: ListItemView): ListItemGroup {
        val content = layout.findViewById<ViewGroup>(android.R.id.content)
        content.addView(item.getView())
        children.add(item)
        return this
    }

    // load-after: chèn vào ĐÚNG vị trí atIndex thay vì thêm cuối - xem PageLayoutRender.insertNode().
    // Overload này CHỈ được gọi từ luồng chèn load-after (không dùng lúc build trang lần đầu),
    // nên bật LayoutTransition ngay tại đây: các item phía dưới tự động animate trượt xuống
    // nhường chỗ, còn item mới thì tự fade-in (mặc định của LayoutTransition) - không ảnh hưởng
    // gì tới addView() thường ở trên.
    fun addView(item: ListItemView, atIndex: Int): ListItemGroup {
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
