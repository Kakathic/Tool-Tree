package com.omarea.krscript.ui

import android.animation.ObjectAnimator
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Toast
import com.tool.tree.R
import com.omarea.common.ui.DialogHelper
import com.omarea.krscript.downloader.DownloadTaskHelper
import com.omarea.krscript.model.*

class PageLayoutRender(private val mContext: Context,
                       private val itemConfigList: ArrayList<NodeInfoBase>,
                       private val clickListener: OnItemClickListener,
                       private val rootGroup: ListItemGroup) {

    interface OnItemClickListener {
        fun onPageClick(item: PageNode, onCompleted: Runnable)
        fun onActionClick(item: ActionNode, onCompleted: Runnable, isAutoShow: Boolean = false)
        fun onSwitchClick(item: SwitchNode, onCompleted: Runnable)
        fun onPickerClick(item: PickerNode, onCompleted: Runnable)
        fun onEditorClick(item: EditorNode, onCompleted: Runnable)
        fun onDownloadClick(item: DownloadNode, listItemView: ListItemDownload, onCompleted: Runnable)
        fun onItemLongClick(clickableNode: ClickableNode)
    }

    private fun findItemByDynamicIndex(key: String, actionInfos: ArrayList<NodeInfoBase>): NodeInfoBase? {
        for (item in actionInfos) {
            if (item.index == key) {
                return item
            } else if (item is GroupNode && item.children.isNotEmpty()) {
                val result = findItemByDynamicIndex(key, item.children)
                if (result != null) {
                    return result
                }
            }
        }
        return null
    }

    private fun getCommonOnExitRunnable(item: NodeInfoBase, node: ListItemClickable): Runnable {
        val handler = Handler(Looper.getMainLooper())
        return Runnable {
            handler.post {
                node.updateViewByShell()

                if (item is RunnableNode && item.updateBlocks != null) {
                    rootGroup.triggerUpdateByKey(item.updateBlocks!!)
                }
            }
        }
    }

    private fun onItemClick(item: NodeInfoBase, listItemView: ListItemClickable) {
        when (item) {
            is PageNode -> clickListener.onPageClick(item, getCommonOnExitRunnable(item, listItemView))
            is ActionNode -> clickListener.onActionClick(item, getCommonOnExitRunnable(item, listItemView))
            is PickerNode -> clickListener.onPickerClick(item, getCommonOnExitRunnable(item, listItemView))
            is SwitchNode -> clickListener.onSwitchClick(item, getCommonOnExitRunnable(item, listItemView))
            is EditorNode -> clickListener.onEditorClick(item, getCommonOnExitRunnable(item, listItemView))
            is DownloadNode -> clickListener.onDownloadClick(item, listItemView as ListItemDownload, getCommonOnExitRunnable(item, listItemView))
        }
    }

    private val onItemClickListener: ListItemClickable.OnClickListener = object : ListItemClickable.OnClickListener {
        override fun onClick(listItemView: ListItemClickable) {
            val key = listItemView.index
            try {
                val item = findItemByDynamicIndex(key, itemConfigList)
                if (item == null) {
                    Log.e("onItemClick", "Item with the specified ID not found index: $key")
                    return
                } else {
                    onItemClick(item, listItemView)
                }
            } catch (ex: Exception) {
            }
        }
    }

    private val onItemLongClickListener = object : ListItemClickable.OnLongClickListener {
        override fun onLongClick(listItemView: ListItemClickable) {
            val item = findItemByDynamicIndex(listItemView.index, itemConfigList)
            // Nhấn giữ khi mục tải đang bận (đang tải / tạm dừng / đang chạy script) → hiện
            // dialog xác nhận hủy, thay vì luồng "thêm shortcut" mặc định bên dưới.
            if (item is DownloadNode && listItemView is ListItemDownload && listItemView.isBusy) {
                DialogHelper.confirm(
                    mContext,
                    mContext.getString(R.string.kr_download_cancel_confirm_title),
                    mContext.getString(R.string.kr_download_cancel_confirm_message),
                    Runnable { DownloadTaskHelper.cancelByUrl(item.url) }
                )
                return
            }
            if (item is ClickableNode) {
                clickListener.onItemLongClick(item)
            }
        }
    }

    // load-after: GroupNode -> view group tương ứng, điền cho MỌI group kể cả group rỗng ban
    // đầu (có thể toàn bộ con đều bị hoãn) - dùng để chèn thêm mục vào group SAU KHI trang đã
    // render xong (xem insertNode()). groupParentMap/attachedGroups dùng để tự add view group
    // con vào đúng chỗ nếu group đó đang rỗng (chưa từng addView vào parent lúc build ban đầu).
    // groupInsertIndexMap: vị trí THỰC (số view đã có trong parent) tại đúng lúc gặp group rỗng
    // đó - dùng để attach group vào ĐÚNG chỗ thay vì luôn nối cuối parent.
    private val groupViewMap = HashMap<GroupNode, ListItemGroup>()
    private val groupParentMap = HashMap<GroupNode, ListItemGroup>()
    private val groupInsertIndexMap = HashMap<GroupNode, Int>()
    private val attachedGroups = HashSet<GroupNode>()

    private fun mapConfigList(parent: ListItemGroup, actionInfos: ArrayList<NodeInfoBase>) {
        for (index in 0 until actionInfos.size) {
            renderNode(parent, actionInfos[index])
        }
    }

    // atIndex >= 0: chèn view vào ĐÚNG vị trí đó thay vì thêm cuối - dùng cho tính năng
    // load-after (xem insertNode()). Mặc định (-1) giữ nguyên hành vi thêm cuối như cũ.
    // replacePlaceholder: dùng cho process = true (xem appendNode()) - item build xong sẽ thế
    // chỗ 1 khung skeleton đang chờ thay vì luôn nối cuối cùng.
    private fun renderNode(parent: ListItemGroup, it: NodeInfoBase, atIndex: Int = -1, replacePlaceholder: Boolean = false) {
        try {
            var uiRender: ListItemView? = null
            if (it is PageNode) {
                uiRender = createPageItem(it)
            } else if (it is SwitchNode) {
                uiRender = createSwitchItem(it)
            } else if (it is ActionNode) {
                uiRender = createActionItem(it)
            } else if (it is PickerNode) {
                uiRender = createListItem(it)
            } else if (it is DownloadNode) {
                uiRender = createDownloadItem(it)
            } else if (it is TextNode) {
                uiRender = if (parent.isRootGroup) createTextItem(it) else createTextItemWhite(it)
            } else if (it is EditorNode) {
                uiRender = createEditorItem(it)
            } else if (it is GroupNode) {
                val subGroup = createItemGroup(it)
                groupViewMap[it] = subGroup
                groupParentMap[it] = parent
                if (it.children.isNotEmpty()) {
                    if (replacePlaceholder) parent.addViewReplacingPlaceholder(subGroup) else parent.addView(subGroup)
                    attachedGroups.add(it)
                    mapConfigList(subGroup, it.children)
                } else {
                    // load-after: group đang rỗng (toàn bộ con chờ load-after) - ghi lại đúng vị
                    // trí hiện tại của parent để insertNode() attach group này vào ĐÚNG CHỖ.
                    groupInsertIndexMap[it] = parent.childCount
                }
            }

            if (uiRender != null) {
                if (uiRender is ListItemClickable) {
                    uiRender.setOnClickListener(this.onItemClickListener)
                    uiRender.setOnLongClickListener(this.onItemLongClickListener)
                }
                if (atIndex >= 0) {
                    parent.addView(uiRender, atIndex)
                } else if (replacePlaceholder) {
                    parent.addViewReplacingPlaceholder(uiRender)
                } else {
                    parent.addView(uiRender)
                }
            }
        } catch (ex: Exception) {
            Toast.makeText(mContext, it.title + "Interface rendering error" + ex.message, Toast.LENGTH_SHORT).show()
        }
    }

    // process = true: hiện sẵn "count" khung skeleton/placeholder ngay từ đầu cho trang đỡ trống -
    // gọi lúc mới dựng rootGroup, TRƯỚC KHI có item thật nào (xem ActionListFragment.setupProgressiveRoot()).
    // Mỗi khung có 1 dải sáng (shimmer) quét ngang liên tục để báo hiệu đang tải.
    fun addLoadingPlaceholders(count: Int) {
        if (count <= 0) return
        val views = (0 until count).map { createSkeletonView() }
        rootGroup.addPlaceholders(views)
    }

    // Gỡ hết khung skeleton còn dư (trang có ít item thật hơn số khung đã hiện) - gọi khi trang
    // process = true đã build xong toàn bộ (xem ActionListFragment.finishProgressiveList()).
    fun clearLoadingPlaceholders() {
        rootGroup.clearPlaceholders()
    }

    // Shimmer: kr_skeleton_shimmer là 1 View phủ toàn bộ thẻ, nền là gradient trong suốt-sáng-
    // trong suốt (kr_skeleton_shimmer_gradient.xml). Animator dịch nó từ -width (ngoài mép trái,
    // ẩn hoàn toàn) sang +width (ngoài mép phải) liên tục - FrameLayout cha (clipToOutline=true)
    // tự cắt phần tràn ra ngoài thẻ, tạo cảm giác dải sáng lướt qua card. Phải đợi layout xong
    // mới biết width thật của thẻ nên dùng post{} thay vì tạo animator ngay lúc inflate.
    private fun createSkeletonView(): View {
        val view = LayoutInflater.from(mContext).inflate(R.layout.kr_skeleton_list_item, null, false)
        val shimmer = view.findViewById<View>(R.id.kr_skeleton_shimmer)
        shimmer?.post {
            val width = shimmer.width
            if (width > 0) {
                shimmer.translationX = -width.toFloat()
                val anim = ObjectAnimator.ofFloat(shimmer, View.TRANSLATION_X, -width.toFloat(), width.toFloat()).apply {
                    duration = 1200
                    repeatCount = ObjectAnimator.INFINITE
                    interpolator = LinearInterpolator()
                    start()
                }
                view.tag = anim
            }
        }
        return view
    }

    // Dùng cho chế độ process = true: thêm NGAY 1 mục mới vào cuối danh sách gốc (rootGroup)
    // mà không dựng lại các mục đã hiện trước đó - xem ActionListFragment.appendProgressiveItem.
    // Item mới sẽ thế chỗ 1 khung skeleton đang chờ (nếu còn) - xem addLoadingPlaceholders().
    fun appendNode(node: NodeInfoBase) {
        itemConfigList.add(node)
        renderNode(rootGroup, node, replacePlaceholder = true)
    }

    // load-after: quy đổi "index" trong itemConfigList (model, gồm cả group rỗng chưa từng
    // hiện view) sang vị trí view THẬT trong rootGroup - group rỗng nào chưa có trong
    // attachedGroups (chưa từng addView) thì không được tính vào vị trí thật.
    private fun realRootViewIndex(modelIndex: Int): Int {
        var realIndex = 0
        for (i in 0 until modelIndex) {
            val existing = itemConfigList[i]
            if (existing is GroupNode && !attachedGroups.contains(existing)) continue
            realIndex++
        }
        return realIndex
    }

    // load-after: chèn 1 mục ĐÃ BUILD XONG vào ĐÚNG vị trí "index" - group = null nghĩa là
    // chèn vào danh sách gốc trang, ngược lại chèn vào đúng group đó (tự add group vào parent
    // nếu group đang rỗng/chưa từng hiện) - xem ActionListFragment.appendLateItem().
    fun insertNode(group: GroupNode?, node: NodeInfoBase, index: Int) {
        if (group == null) {
            val at = index.coerceIn(0, itemConfigList.size)
            val realAt = realRootViewIndex(at)
            itemConfigList.add(at, node)
            renderNode(rootGroup, node, realAt)
            return
        }
        val subGroup = groupViewMap[group] ?: return
        val at = index.coerceIn(0, group.children.size)
        group.children.add(at, node)
        if (!attachedGroups.contains(group)) {
            // load-after: group này ban đầu HOÀN TOÀN RỖNG (mọi con đều load-after) nên chưa
            // từng add vào parent lúc render đầu - attach vào ĐÚNG vị trí gốc đã ghi lại ở
            // groupInsertIndexMap (xem renderNode()), không còn luôn nối cuối parent nữa.
            val parentView = groupParentMap[group] ?: return
            val atGroupIndex = groupInsertIndexMap[group] ?: parentView.childCount
            parentView.addView(subGroup, atGroupIndex)
            attachedGroups.add(group)
        }
        renderNode(subGroup, node, at)
    }

    private fun createTextItem(node: TextNode): ListItemView {
        return ListItemText(mContext, R.layout.kr_text_list_item, node)
    }

    private fun createTextItemWhite(node: TextNode): ListItemView {
        return ListItemText(mContext, R.layout.kr_text_list_item_white, node)
    }

    private fun createListItem(node: PickerNode): ListItemView {
        return ListItemPicker(mContext, node)
    }

    private fun createPageItem(node: PageNode): ListItemView {
        return ListItemPage(mContext, node)
    }

    private fun createSwitchItem(node: SwitchNode): ListItemView {
        return ListItemSwitch(mContext, node)
    }

    private fun createActionItem(node: ActionNode): ListItemView {
        return ListItemAction(mContext, node)
    }

    private fun createEditorItem(node: EditorNode): ListItemView {
        return ListItemEditor(mContext, node)
    }

    private fun createDownloadItem(node: DownloadNode): ListItemView {
        val view = ListItemDownload(mContext, node)
        // Re-bind view nếu có session đang hoạt động (trả lại trang không đóng tải).
        // Chỉ tra theo url khi KHÔNG rỗng - tránh việc nhiều mục dùng "url-sh" chưa resolve
        // (url = "" mặc định) bị dính chung 1 session (vd: lỗi) của mục khác ở trang khác.
        if (node.url.isNotBlank()) {
            DownloadTaskHelper.getSession(node.url)?.let { session ->
                DownloadTaskHelper.bindView(session, view)
            }
        }
        return view
    }

    private fun createItemGroup(node: GroupNode): ListItemGroup {
        return ListItemGroup(mContext, false, node)
    }

    init {
        mapConfigList(rootGroup, itemConfigList)
    }
}