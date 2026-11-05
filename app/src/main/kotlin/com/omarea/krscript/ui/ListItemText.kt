package com.omarea.krscript.ui

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.tool.tree.R
import com.omarea.krscript.model.TextNode

class ListItemText(context: Context,
                   private val config: TextNode,
                   compact: Boolean = false) : ListItemView(context, R.layout.kr_text_list_item, config) {

    private val rowsView = layout.findViewById<TextView?>(R.id.kr_rows)
    protected var extraIconView = layout.findViewById<ImageView?>(R.id.kr_extra_icon_text)
    private val rowsHtmlView = layout.findViewById<FrameLayout?>(R.id.kr_rows_html)
    private val bgView = layout.findViewById<ImageView?>(R.id.kr_extra_bg)

    override fun updateViewByShell() {
        super.updateViewByShell()
        RowsRenderHelper.refreshToggleStates(context, rowsView, config.rows)
    }

    init {
        if (compact) {
            // Text nằm trực tiếp ngoài group: lề ngang 12dp để thẳng hàng với tiêu đề item clickable ngoài group (2dp lề + 10dp), padding trên 8dp, dưới 2dp; trong group giữ padding của layout
            val d = context.resources.displayMetrics.density
            layout.findViewById<View?>(R.id.kr_text_content)?.setPadding((1 * d).toInt(), (8 * d).toInt(), (1 * d).toInt(), (2 * d).toInt())
        }
        // Khối rows của text chỉ cách phần mô tả 4dp, thêm 8dp phía trên đường kẻ đầu khối để đường kẻ không dính sát chữ
        RowsRenderHelper.markCompactLeadingDivider(rowsView, 8)
        RowsRenderHelper.bind(context, rowsView, extraIconView, config.rows, config, rowsHtmlView, bgView)
    }
}
