package com.omarea.krscript.ui

import android.content.Context
import android.view.View
import android.widget.ImageView
import com.omarea.krscript.model.ChatNode
import com.tool.tree.R

class ListItemChat(context: Context, config: ChatNode) : ListItemClickable(context, R.layout.kr_action_list_item, config) {
    private val widgetView = layout.findViewById<ImageView?>(R.id.kr_widget)

    init {
        widgetView?.visibility = View.VISIBLE
        widgetView?.setImageDrawable(context.getDrawable(R.drawable.icon_chat))
        WidgetTintHelper.applyTint(context, widgetView, iconDrawable)
    }
}
