package com.tool.tree

import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** Danh sách phiên trong ngăn kéo bên trái. */
class TerminalSessionDrawer(
    private val list: LinearLayout,
    newButton: TextView,
    private val onSelect: (TerminalSessionStore.Entry) -> Unit,
    private val onClose: (TerminalSessionStore.Entry) -> Unit,
    onNew: () -> Unit
) {
    init {
        newButton.setOnClickListener { onNew() }
    }

    private fun dp(value: Int): Int = (value * list.resources.displayMetrics.density).toInt()

    fun refresh(entries: List<TerminalSessionStore.Entry>, current: TerminalSessionStore.Entry?) {
        list.removeAllViews()
        for (entry in entries) {
            val row = LinearLayout(list.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener { onSelect(entry) }
            }
            val label = TextView(list.context).apply {
                text = entry.label
                textSize = 15f
                setPadding(dp(16), dp(14), dp(8), dp(14))
                setTextColor(
                    when {
                        entry === current -> Color.parseColor("#6FA8FF")
                        entry.finished -> Color.parseColor("#8A8A8A")
                        else -> Color.WHITE
                    }
                )
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val close = TextView(list.context).apply {
                text = "✕"
                textSize = 16f
                setTextColor(Color.parseColor("#B0B0B0"))
                setPadding(dp(14), dp(14), dp(16), dp(14))
                setOnClickListener { onClose(entry) }
            }
            row.addView(label)
            row.addView(close)
            list.addView(row)
        }
    }
}
