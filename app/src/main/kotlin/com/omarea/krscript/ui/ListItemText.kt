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

    // process = true: sau khi resolvePendingStates() (get shell của rows) chạy xong, phải cập
    // nhật lại icon on/off của checkbox/switch trong rows cho đúng trạng thái - chỉ refresh
    // riêng toggle (không rebuild cả rows), tránh chạy lại text-sh/icon-sh/photo-sh/progress-sh
    // của rows lần thứ 2. Xem RowsRenderHelper.refreshToggleStates().
    override fun updateViewByShell() {
        super.updateViewByShell()
        RowsRenderHelper.refreshToggleStates(context, rowsView, config.rows)
    }

    init {
        RowsRenderHelper.bind(context, rowsView, extraIconView, config.rows, config, rowsHtmlView)
    }
}
