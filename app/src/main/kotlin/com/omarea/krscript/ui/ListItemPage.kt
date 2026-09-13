package com.omarea.krscript.ui

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.tool.tree.R
import com.omarea.krscript.model.PageNode

class ListItemPage(context: Context, private val config: PageNode) : ListItemClickable(context, R.layout.kr_action_list_item, config) {
    private val widgetView = layout.findViewById<ImageView?>(R.id.kr_widget)
    private val rowsView = layout.findViewById<TextView?>(R.id.kr_rows)
    private val rowsPhotoView = layout.findViewById<ImageView?>(R.id.kr_rows_photo)

    // process = true: sau khi resolvePendingStates() (get shell của rows) chạy xong, phải vẽ
    // lại rows thì checkbox/switch trong rows mới cập nhật đúng trạng thái trên UI.
    override fun updateViewByShell() {
        super.updateViewByShell()
        RowsRenderHelper.bind(context, rowsView, rowsPhotoView, config.rows, config)
    }

    init {
        widgetView?.visibility = View.VISIBLE
        widgetView?.setImageDrawable(context.getDrawable(R.drawable.kr_arrow))
        WidgetTintHelper.applyTint(context, widgetView, iconDrawable)

        // Giống text.rows / action.rows: hiển thị thêm các dòng rich-text (nếu có khai báo page.rows)
        RowsRenderHelper.bind(context, rowsView, rowsPhotoView, config.rows, config)
    }
}
