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
        fun onTerminalClick(item: TerminalNode, onCompleted: Runnable) {}
        fun onChatClick(item: ChatNode, onCompleted: Runnable) {}
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
            is TerminalNode -> clickListener.onTerminalClick(item, getCommonOnExitRunnable(item, listItemView))
            is ChatNode -> clickListener.onChatClick(item, getCommonOnExitRunnable(item, listItemView))
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

    private val groupViewMap = HashMap<GroupNode, ListItemGroup>()
    private val groupParentMap = HashMap<GroupNode, ListItemGroup>()
    private val groupInsertIndexMap = HashMap<GroupNode, Int>()
    private val attachedGroups = HashSet<GroupNode>()

    // Các mục nằm ngoài [[group]] được gom vào "thẻ ngầm" (giống group nhưng không có tiêu đề)
    private val looseGroupOf = HashMap<NodeInfoBase, ListItemGroup>()
    private val looseMembers = HashMap<ListItemGroup, ArrayList<NodeInfoBase>>()
    private var currentLoose: ListItemGroup? = null
    private var insertPrev: NodeInfoBase? = null
    private var insertNext: NodeInfoBase? = null

    private fun newLooseGroup(): ListItemGroup {
        val node = GroupNode("")
        node.key = "__loose_" + node.index
        val card = ListItemGroup(mContext, false, node)
        card.enableDividers()
        looseMembers[card] = ArrayList()
        return card
    }

    private fun addLoose(view: ListItemView, node: NodeInfoBase, atIndex: Int, replacePlaceholder: Boolean) {
        if (atIndex < 0) {
            if (node.newBlock) currentLoose = null
            var card = currentLoose
            if (card == null) {
                card = newLooseGroup()
                if (replacePlaceholder) rootGroup.addViewBeforePlaceholder(card) else rootGroup.addView(card)
                currentLoose = card
            }
            card.addView(view)
            looseMembers[card]?.add(node)
            looseGroupOf[node] = card
            return
        }
        val prevCard = if (node.newBlock) null else insertPrev?.let { looseGroupOf[it] }
        val nextCard = if (insertNext?.newBlock == true) null else insertNext?.let { looseGroupOf[it] }
        val card: ListItemGroup
        val pos: Int
        if (prevCard != null) {
            card = prevCard
            pos = (insertPrev?.let { looseMembers[card]?.indexOf(it) } ?: -1) + 1
        } else if (nextCard != null) {
            card = nextCard
            pos = (insertNext?.let { looseMembers[card]?.indexOf(it) } ?: 0).coerceAtLeast(0)
        } else {
            card = newLooseGroup()
            rootGroup.addView(card, atIndex)
            pos = 0
        }
        val members = looseMembers[card]
        val at = pos.coerceIn(0, members?.size ?: 0)
        members?.add(at, node)
        card.addView(view, at)
        looseGroupOf[node] = card
    }

    private fun mapConfigList(parent: ListItemGroup, actionInfos: ArrayList<NodeInfoBase>) {
        for (index in 0 until actionInfos.size) {
            renderNode(parent, actionInfos[index])
        }
    }

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
                uiRender = createTextItem(it)
            } else if (it is EditorNode) {
                uiRender = createEditorItem(it)
            } else if (it is TerminalNode) {
                uiRender = ListItemTerminal(mContext, it)
            } else if (it is ChatNode) {
                uiRender = ListItemChat(mContext, it)
            } else if (it is GroupNode) {
                val subGroup = createItemGroup(it)
                groupViewMap[it] = subGroup
                groupParentMap[it] = parent
                if (it.children.isNotEmpty()) {
                    if (parent === rootGroup) currentLoose = null
                    if (replacePlaceholder) parent.addViewBeforePlaceholder(subGroup) else parent.addView(subGroup)
                    attachedGroups.add(it)
                    mapConfigList(subGroup, it.children)
                } else {
                    groupInsertIndexMap[it] = parent.childCount
                }
            }

            if (uiRender != null) {
                if (uiRender is ListItemClickable) {
                    uiRender.setOnClickListener(this.onItemClickListener)
                    uiRender.setOnLongClickListener(this.onItemLongClickListener)
                }
                if (parent === rootGroup && it !is GroupNode) {
                    addLoose(uiRender, it, atIndex, replacePlaceholder)
                } else if (atIndex >= 0) {
                    parent.addView(uiRender, atIndex)
                } else if (replacePlaceholder) {
                    parent.addViewBeforePlaceholder(uiRender)
                } else {
                    parent.addView(uiRender)
                }
            }
        } catch (ex: Exception) {
            Toast.makeText(mContext, it.title + "Interface rendering error" + ex.message, Toast.LENGTH_SHORT).show()
        }
    }

    fun addLoadingPlaceholders(count: Int) {
        if (count <= 0) return
        RowsHtmlDeferral.begin()
        val views = (0 until count).map { createSkeletonView() }
        rootGroup.addPlaceholders(views)
    }

    fun clearLoadingPlaceholders() {
        rootGroup.clearPlaceholders()
        RowsHtmlDeferral.end()
    }

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

    fun appendNode(node: NodeInfoBase) {
        itemConfigList.add(node)
        renderNode(rootGroup, node, replacePlaceholder = true)
    }

    private fun realRootViewIndex(modelIndex: Int): Int {
        var realIndex = 0
        val counted = HashSet<ListItemGroup>()
        for (i in 0 until modelIndex) {
            val existing = itemConfigList[i]
            if (existing is GroupNode) {
                if (!attachedGroups.contains(existing)) continue
                realIndex++
            } else {
                val card = looseGroupOf[existing] ?: continue
                if (counted.add(card)) realIndex++
            }
        }
        return realIndex
    }

    fun insertNode(group: GroupNode?, node: NodeInfoBase, index: Int) {
        if (group == null) {
            val at = index.coerceIn(0, itemConfigList.size)
            val realAt = realRootViewIndex(at)
            insertPrev = if (at > 0) itemConfigList[at - 1] else null
            insertNext = if (at < itemConfigList.size) itemConfigList[at] else null
            itemConfigList.add(at, node)
            try {
                renderNode(rootGroup, node, realAt)
            } finally {
                insertPrev = null
                insertNext = null
            }
            return
        }
        val subGroup = groupViewMap[group] ?: return
        val at = index.coerceIn(0, group.children.size)
        group.children.add(at, node)
        if (!attachedGroups.contains(group)) {
            val parentView = groupParentMap[group] ?: return
            val atGroupIndex = groupInsertIndexMap[group] ?: parentView.childCount
            parentView.addView(subGroup, atGroupIndex)
            attachedGroups.add(group)
        }
        renderNode(subGroup, node, at)
    }

    private fun createTextItem(node: TextNode): ListItemView {
        return ListItemText(mContext, node)
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
        DownloadTaskHelper.findSessionForNode(node)?.let { session ->
            if (node.url.isBlank() && session.url.isNotBlank()) {
                node.url = session.url
                node.urlResolved = true
            }
            DownloadTaskHelper.bindView(session, view)
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