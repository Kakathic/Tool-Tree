package com.omarea.krscript.ui

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.tool.tree.R
import com.omarea.krscript.model.PickerNode

class ListItemPicker(context: Context, private val config: PickerNode) : ListItemClickable(context, R.layout.kr_action_list_item, config) {
    private val widgetView = layout.findViewById<ImageView?>(R.id.kr_widget)
    private val rowsView = layout.findViewById<TextView?>(R.id.kr_rows)
    private val rowsPhotoView = layout.findViewById<ImageView?>(R.id.kr_rows_photo)
    private val rowsHtmlView = layout.findViewById<FrameLayout?>(R.id.kr_rows_html)

    override fun updateViewByShell() {
        super.updateViewByShell()
        RowsRenderHelper.refreshToggleStates(context, rowsView, config.rows)
    }

    init {
        widgetView?.visibility = View.VISIBLE
        widgetView?.setImageDrawable(context.getDrawable(R.drawable.kr_picker))
        WidgetTintHelper.applyTint(context, widgetView, iconDrawable)

        // Giống action.rows: hiển thị thêm các dòng rich-text (nếu có khai báo picker.rows)
        RowsRenderHelper.bind(context, rowsView, rowsPhotoView, config.rows, config, rowsHtmlView)
    }
}
