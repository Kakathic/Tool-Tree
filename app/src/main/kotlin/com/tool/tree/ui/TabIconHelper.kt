package com.tool.tree.ui

import android.app.Activity
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.tabs.TabLayout
import com.tool.tree.R
import android.widget.LinearLayout

class TabIconHelper(
    private val activity: Activity
) {

    private val views = ArrayList<View>()
    fun createTabView(text: String, drawable: Drawable, isFirst: Boolean): View {
        val layout = View.inflate(activity, R.layout.list_item_tab, null)
    
        val iconBadge = layout.findViewById<View>(R.id.IconBadge)
        val imageView = layout.findViewById<ImageView>(R.id.ItemIcon)
        val textView = layout.findViewById<TextView>(R.id.ItemTitle)
    
        textView.text = text
        imageView.setImageDrawable(drawable)

        // Ô bo màu phía sau icon (kích thước cố định, xem layout list_item_tab.xml) CHỈ hiện ở
        // tab đang được chọn - tab chưa chọn không có nền (background = null).
        iconBadge.setBackgroundResource(if (isFirst) R.drawable.tab_icon_badge_bg else 0)
    
        layout.alpha = if (isFirst) 1f else 0.3f
        views.add(layout)
        return layout
    }

    fun updateHighlight(tabLayout: TabLayout, position: Int) {
        for (i in 0 until tabLayout.tabCount) {
            val tabView = tabLayout.getTabAt(i)?.customView ?: continue
            tabView.alpha = if (i == position) 1f else 0.3f
            tabView.findViewById<View>(R.id.IconBadge)
                ?.setBackgroundResource(if (i == position) R.drawable.tab_icon_badge_bg else 0)
        }
    }
}