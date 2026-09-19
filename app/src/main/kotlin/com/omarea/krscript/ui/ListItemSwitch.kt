package com.omarea.krscript.ui

import android.content.Context
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import com.tool.tree.R
import com.omarea.krscript.executor.ScriptEnvironmen
import com.omarea.krscript.model.SwitchNode
import java.util.Locale.getDefault

class ListItemSwitch(context: Context,
                     private val config: SwitchNode) : ListItemClickable(context, R.layout.kr_switch_list_item, config) {
    protected var switchView = layout.findViewById<Switch?>(R.id.kr_switch)
    private val rowsView = layout.findViewById<TextView?>(R.id.kr_rows)
    private val rowsPhotoView = layout.findViewById<ImageView?>(R.id.kr_rows_photo)
    private val rowsHtmlView = layout.findViewById<FrameLayout?>(R.id.kr_rows_html)

    var checked: Boolean
        get() {
            return if (switchView != null) switchView!!.isChecked else false
        }
        set(value) {
            switchView?.isChecked = value
        }

    override fun updateViewByShell() {
        super.updateViewByShell()

        if (config.getState.isNotEmpty()) {
            val shellResult = ScriptEnvironmen.executeResultRoot(context, config.getState, config)
            config.checked = shellResult == "1" || shellResult.lowercase(getDefault()) == "true"
        }
        checked = config.checked
        RowsRenderHelper.refreshToggleStates(context, rowsView, config.rows)
    }

    init {
        checked = config.checked
        WidgetTintHelper.applyTint(context, switchView, iconDrawable)

        // Giống action.rows: hiển thị thêm các dòng rich-text (nếu có khai báo switch.rows)
        RowsRenderHelper.bind(context, rowsView, rowsPhotoView, config.rows, config, rowsHtmlView)
    }
}
