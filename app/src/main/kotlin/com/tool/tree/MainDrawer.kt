package com.tool.tree

import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.omarea.krscript.config.IconPathAnalysis
import com.omarea.krscript.model.ClickableNode
import com.omarea.krscript.model.GroupNode
import com.omarea.krscript.model.NodeInfoBase
import com.omarea.krscript.model.PageNode
import com.omarea.krscript.shortcut.DrawerFavorites

// Mục lục bên trái: nhóm Yêu thích (JSON) ở đầu, tiếp theo là [[group]] + [[page]] từ drawer.toml
class MainDrawer(
    private val list: LinearLayout,
    private val onPageClick: (PageNode) -> Unit,
    private val onFavoriteLongClick: (DrawerFavorites.Entry) -> Unit
) {
    private val context get() = list.context
    private val iconLoader = IconPathAnalysis()
    private var tomlNodes: List<NodeInfoBase>? = null

    private fun dp(value: Int): Int = (value * list.resources.displayMetrics.density).toInt()

    private fun themeColor(attr: Int, fallback: Int): Int {
        val tv = TypedValue()
        if (!context.theme.resolveAttribute(attr, tv, true)) return fallback
        return if (tv.resourceId != 0) ContextCompat.getColor(context, tv.resourceId) else tv.data
    }

    fun refresh(nodes: List<NodeInfoBase>?) {
        tomlNodes = nodes
        render()
    }

    // Vẽ lại toàn bộ; Yêu thích đọc lại từ file mỗi lần nên luôn mới
    fun render() {
        list.removeAllViews()
        val primary = themeColor(android.R.attr.textColorPrimary, Color.WHITE)
        val secondary = themeColor(android.R.attr.textColorSecondary, Color.GRAY)

        val favorites = DrawerFavorites(context).list()
        if (favorites.isNotEmpty()) {
            addHeader(context.getString(R.string.tab_favorites), secondary)
            for (entry in favorites) {
                addRow(entry.title, entry.toIconNode(), primary, { onPageClick(entry.toPage()) }, { onFavoriteLongClick(entry) })
            }
        }

        for (node in tomlNodes.orEmpty()) {
            when (node) {
                is GroupNode -> {
                    var headerAdded = false
                    for (child in node.children) {
                        if (child !is PageNode) continue
                        if (!headerAdded) {
                            headerAdded = true
                            addHeader(node.title, secondary)
                        }
                        addRow(child.title, child, primary, { onPageClick(child) }, null)
                    }
                }
                is PageNode -> addRow(node.title, node, primary, { onPageClick(node) }, null)
                else -> {}
            }
        }
    }

    private fun addHeader(title: String, color: Int) {
        if (title.isEmpty()) return
        list.addView(TextView(context).apply {
            text = title
            textSize = 12f
            setTextColor(color)
            setPadding(dp(16), dp(14), dp(16), dp(4))
        })
    }

    private fun addRow(title: String, iconNode: ClickableNode, textColor: Int, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            val tv = TypedValue()
            if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
                setBackgroundResource(tv.resourceId)
            }
            setOnClickListener { onClick() }
            if (onLongClick != null) {
                setOnLongClickListener { onLongClick(); true }
            }
        }
        val icon = iconLoader.loadIcon(context, iconNode)
        if (icon != null) {
            row.addView(ImageView(context).apply {
                setImageDrawable(icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(16) }
            })
        }
        row.addView(TextView(context).apply {
            text = title
            textSize = 15f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(textColor)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        list.addView(row)
    }
}
