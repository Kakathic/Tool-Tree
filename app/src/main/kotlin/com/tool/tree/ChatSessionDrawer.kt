package com.tool.tree

import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.text.DateFormat
import java.util.Date

class ChatSessionDrawer(
    private val list: LinearLayout,
    newButton: TextView,
    private val onSelect: (ChatSessionStore.Info) -> Unit,
    private val onDelete: (ChatSessionStore.Info) -> Unit,
    onNew: () -> Unit
) {
    init {
        newButton.setOnClickListener { onNew() }
    }

    private fun dp(value: Int): Int = (value * list.resources.displayMetrics.density).toInt()

    private fun themeColor(attr: Int, fallback: Int): Int {
        val tv = TypedValue()
        if (!list.context.theme.resolveAttribute(attr, tv, true)) return fallback
        return if (tv.resourceId != 0) ContextCompat.getColor(list.context, tv.resourceId) else tv.data
    }

    fun refresh(entries: List<ChatSessionStore.Info>, currentId: String) {
        val primary = themeColor(android.R.attr.textColorPrimary, Color.WHITE)
        val secondary = themeColor(android.R.attr.textColorSecondary, Color.GRAY)
        val accent = themeColor(androidx.appcompat.R.attr.colorAccent, primary)
        val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        list.removeAllViews()
        for (entry in entries) {
            val isCurrent = entry.id == currentId
            val row = LinearLayout(list.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener { onSelect(entry) }
            }
            val labels = LinearLayout(list.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(12), dp(8), dp(12))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            labels.addView(TextView(list.context).apply {
                text = entry.name
                textSize = 15f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(if (isCurrent) accent else primary)
            })
            if (entry.updated > 0) {
                labels.addView(TextView(list.context).apply {
                    text = dateFormat.format(Date(entry.updated))
                    textSize = 12f
                    setTextColor(secondary)
                })
            }
            val delete = TextView(list.context).apply {
                text = "✕"
                textSize = 16f
                setTextColor(secondary)
                setPadding(dp(14), dp(14), dp(16), dp(14))
                setOnClickListener { onDelete(entry) }
            }
            row.addView(labels)
            row.addView(delete)
            list.addView(row)
        }
    }
}
