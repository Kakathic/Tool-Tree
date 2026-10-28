package com.tool.tree

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.termux.view.TerminalView

class TerminalSpecialKeys(
    private val container: LinearLayout,
    private val terminalView: TerminalView
) {
    private val layout = listOf(
        listOf("-", "_", "|", "\\", "~", "'", "\"", "`", "&", ";"),
        listOf("{", "}", "[", "]", "(", ")", "<", ">", "*", "#")
    )

    init {
        build()
    }

    private fun dp(value: Int): Int = (value * container.resources.displayMetrics.density).toInt()

    private fun textColors(): ColorStateList = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
        intArrayOf(Color.parseColor("#8A8A8A"), Color.WHITE)
    )

    private fun keyBackground(): Drawable {
        val pressed = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#33FFFFFF"))
            cornerRadius = dp(4).toFloat()
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        }
    }

    private fun build() {
        container.setPadding(container.paddingLeft, container.paddingTop, container.paddingRight, dp(8))
        for (rowKeys in layout) {
            val row = LinearLayout(container.context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(ROW_HEIGHT_DP))
            }
            for (ch in rowKeys) {
                val button = TextView(container.context).apply {
                    text = ch
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setPadding(0, 0, 0, 0)
                    setTextColor(textColors())
                    textSize = 16f
                    background = keyBackground()
                    val margin = dp(2)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                        setMargins(margin, margin, margin, margin)
                    }
                    setOnClickListener {
                        terminalView.inputCodePoint(ch.codePointAt(0), false, false)
                    }
                }
                row.addView(button)
            }
            container.addView(row)
        }
    }

    private companion object {
        const val ROW_HEIGHT_DP = 30
    }
}
