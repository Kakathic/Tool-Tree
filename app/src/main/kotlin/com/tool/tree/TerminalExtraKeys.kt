package com.tool.tree

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.termux.terminal.KeyHandler
import com.termux.view.TerminalView

class TerminalExtraKeys(private val container: LinearLayout, private val terminalView: TerminalView) {
    private class Key(val id: String, val label: String, val repeat: Boolean = false, val size: Float = 14f)

    private companion object {
        const val ARROW_SIZE = 20f
        const val ROW_HEIGHT_DP = 34
    }

    private val layout = listOf(
        listOf(Key("ESC", "ESC"), Key("/", "/"), Key("-", "-"), Key("HOME", "HOME"), Key("UP", "↑", true, ARROW_SIZE), Key("END", "END"), Key("PGUP", "PGUP", true)),
        listOf(Key("TAB", "TAB"), Key("CTRL", "CTRL"), Key("ALT", "ALT"), Key("LEFT", "←", true, ARROW_SIZE), Key("DOWN", "↓", true, ARROW_SIZE), Key("RIGHT", "→", true, ARROW_SIZE), Key("PGDN", "PGDN", true))
    )

    private val handler = Handler(Looper.getMainLooper())
    private var ctrlActive = false
    private var altActive = false
    private var ctrlButton: TextView? = null
    private var altButton: TextView? = null

    init {
        build()
    }

    fun consumeCtrl(): Boolean {
        val value = ctrlActive
        if (value) {
            ctrlActive = false
            ctrlButton?.isSelected = false
        }
        return value
    }

    fun consumeAlt(): Boolean {
        val value = altActive
        if (value) {
            altActive = false
            altButton?.isSelected = false
        }
        return value
    }

    private fun dp(value: Int): Int = (value * container.resources.displayMetrics.density).toInt()

    private fun keyTextColors(): ColorStateList = ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_selected),
            intArrayOf(android.R.attr.state_pressed),
            intArrayOf()
        ),
        intArrayOf(Color.parseColor("#6FA8FF"), Color.parseColor("#8A8A8A"), Color.WHITE)
    )

    @SuppressLint("ClickableViewAccessibility")
    private fun build() {
        for (rowKeys in layout) {
            val row = LinearLayout(container.context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(ROW_HEIGHT_DP))
            }
            for (key in rowKeys) {
                val button = TextView(container.context).apply {
                    text = key.label
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setPadding(0, 0, 0, 0)
                    setTextColor(keyTextColors())
                    textSize = key.size
                    typeface = android.graphics.Typeface.MONOSPACE
                    background = null
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                }
                if (key.id == "CTRL") ctrlButton = button
                if (key.id == "ALT") altButton = button
                attachTouch(button, key)
                row.addView(button)
            }
            container.addView(row)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachTouch(button: TextView, key: Key) {
        val repeater = object : Runnable {
            override fun run() {
                press(key)
                handler.postDelayed(this, 80)
            }
        }
        button.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    press(key)
                    if (key.repeat) handler.postDelayed(repeater, 400)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    handler.removeCallbacks(repeater)
                    if (event.actionMasked == MotionEvent.ACTION_UP) v.performClick()
                }
            }
            true
        }
    }

    private fun press(key: Key) {
        when (key.id) {
            "CTRL" -> {
                ctrlActive = !ctrlActive
                ctrlButton?.isSelected = ctrlActive
            }
            "ALT" -> {
                altActive = !altActive
                altButton?.isSelected = altActive
            }
            "/", "-" -> terminalView.inputCodePoint(key.id[0].code, false, false)
            else -> {
                val keyCode = when (key.id) {
                    "ESC" -> KeyEvent.KEYCODE_ESCAPE
                    "TAB" -> KeyEvent.KEYCODE_TAB
                    "HOME" -> KeyEvent.KEYCODE_MOVE_HOME
                    "END" -> KeyEvent.KEYCODE_MOVE_END
                    "UP" -> KeyEvent.KEYCODE_DPAD_UP
                    "DOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
                    "LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
                    "RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
                    "PGUP" -> KeyEvent.KEYCODE_PAGE_UP
                    "PGDN" -> KeyEvent.KEYCODE_PAGE_DOWN
                    else -> return
                }
                if (terminalView.currentSession == null) return
                var mod = 0
                if (consumeCtrl()) mod = mod or KeyHandler.KEYMOD_CTRL
                if (consumeAlt()) mod = mod or KeyHandler.KEYMOD_ALT
                terminalView.handleKeyCode(keyCode, mod)
            }
        }
    }
}
