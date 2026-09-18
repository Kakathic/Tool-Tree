package com.omarea.krscript.ui

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.tool.tree.R
import com.omarea.krscript.model.ActionNode

class ListItemAction(context: Context, private val config: ActionNode) : ListItemClickable(context, R.layout.kr_action_list_item, config) {
    private val widgetView = layout.findViewById<ImageView?>(R.id.kr_widget)
    private val rowsView = layout.findViewById<TextView?>(R.id.kr_rows)
    private val rowsPhotoView = layout.findViewById<ImageView?>(R.id.kr_rows_photo)
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
        widgetView?.visibility = View.VISIBLE
        if (config.params != null && config.params!!.isNotEmpty()) {
            widgetView?.setImageDrawable(context.getDrawable(R.drawable.kr_list))
        } else {
            widgetView?.setImageDrawable(context.getDrawable(R.drawable.kr_run))
        }
        WidgetTintHelper.applyTint(context, widgetView, iconDrawable)

        // Giống text.rows: hiển thị thêm các dòng rich-text (nếu có khai báo action.rows)
        RowsRenderHelper.bind(context, rowsView, rowsPhotoView, config.rows, config, rowsHtmlView)
    }
}
