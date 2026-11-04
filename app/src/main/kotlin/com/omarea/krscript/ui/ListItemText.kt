package com.omarea.krscript.ui

import android.content.Context
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.tool.tree.R
import com.omarea.krscript.model.TextNode

class ListItemText(context: Context,
                   layoutId: Int,
                   private val config: TextNode) : ListItemView(context, layoutId, config) {

    private val rowsView = layout.findViewById<TextView?>(R.id.kr_rows)
    protected var extraIconView = layout.findViewById<ImageView?>(R.id.kr_extra_icon_text)
    private val rowsHtmlView = layout.findViewById<FrameLayout?>(R.id.kr_rows_html)
    private val bgView = layout.findViewById<ImageView?>(R.id.kr_extra_bg)

    override fun updateViewByShell() {
        super.updateViewByShell()
        RowsRenderHelper.refreshToggleStates(context, rowsView, config.rows)
    }

    init {
        // Khối rows của text chỉ cách phần mô tả 4dp, thêm 8dp phía trên đường kẻ đầu khối để đường kẻ không dính sát chữ
        RowsRenderHelper.markCompactLeadingDivider(rowsView, 8)
        RowsRenderHelper.bind(context, rowsView, extraIconView, config.rows, config, rowsHtmlView, bgView)
    }
}
