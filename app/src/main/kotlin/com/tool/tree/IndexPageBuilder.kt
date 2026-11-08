package com.tool.tree

import android.content.Context
import com.omarea.krscript.model.GroupNode
import com.omarea.krscript.model.NodeInfoBase
import com.omarea.krscript.model.TextNode
import com.omarea.krscript.shortcut.DrawerFavorites

// Dựng danh sách node cho trang Mục lục: nhóm Yêu thích, nhóm Công cụ (card, tự chia cột), hoặc dòng hướng dẫn khi trống
object IndexPageBuilder {
    fun build(context: Context): ArrayList<NodeInfoBase> {
        val store = DrawerFavorites(context)
        val result = ArrayList<NodeInfoBase>()

        fun addSection(title: String, entries: List<DrawerFavorites.Entry>) {
            if (entries.isEmpty()) return
            val group = GroupNode("").also {
                it.title = title
                it.card = true
                it.columnWidthDp = 160
                it.minColumns = 2
                it.maxColumns = 4
            }
            entries.forEach { group.children.add(it.toPage()) }
            result.add(group)
        }

        addSection(context.getString(R.string.tab_favorites), store.list(DrawerFavorites.SECTION_FAVORITE))
        addSection(context.getString(R.string.index_tools), store.list(DrawerFavorites.SECTION_TOOL))

        if (result.isEmpty()) {
            result.add(TextNode("").also {
                it.rows.add(TextNode.TextRow().also { row ->
                    row.text = context.getString(R.string.index_empty)
                    row.align = android.text.Layout.Alignment.ALIGN_CENTER
                    row.marginTop = 32
                    row.marginTopSet = true
                })
            })
        }
        return result
    }
}
