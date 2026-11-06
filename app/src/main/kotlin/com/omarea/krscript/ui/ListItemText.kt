package com.omarea.krscript.ui

import android.content.Context
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.tool.tree.R
import com.omarea.krscript.model.TextNode

class ListItemText(context: Context,
                   private val config: TextNode) : ListItemView(context, R.layout.kr_text_list_item, config) {

    private val rowsView = layout.findViewById<TextView?>(R.id.kr_rows)
    protected var extraIconView = layout.findViewById<ImageView?>(R.id.kr_extra_icon_text)
    private val rowsHtmlView = layout.findViewById<FrameLayout?>(R.id.kr_rows_html)
    private val bgView = layout.findViewById<ImageView?>(R.id.kr_extra_bg)

    override fun updateViewByShell() {
        super.updateViewByShell()
        RowsRenderHelper.refreshToggleStates(context, rowsView, config.rows)
    }

    init {
        RowsRenderHelper.bind(context, rowsView, extraIconView, config.rows, config, rowsHtmlView, bgView)
    }
}
