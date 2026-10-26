package com.tool.tree

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.termux.terminal.KeyHandler
import com.termux.view.TerminalView

class TerminalExtraKeys(private val container: LinearLayout, private val terminalView: TerminalView) {
    private class Key(val id: String, val label: String, val repeat: Boolean = false)

    private val layout = listOf(
        listOf(Key("ESC", "ESC"), Key("/", "/"), Key("-", "-"), Key("HOME", "HOME"), Key("UP", "↑", true), Key("END", "END"), Key("PGUP", "PGUP", true)),
        listOf(Key("TAB", "TAB"), Key("CTRL", "CTRL"), Key("ALT", "ALT"), Key("LEFT", "←", true), Key("DOWN", "↓", true), Key("RIGHT", "→", true), Key("PGDN", "PGDN", true))
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

    private fun buttonBackground(): StateListDrawable {
        fun shape(color: Int) = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(4).toFloat()
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), shape(Color.parseColor("#4A4A4A")))
            addState(intArrayOf(android.R.attr.state_selected), shape(Color.parseColor("#3F6FD8")))
            addState(intArrayOf(), shape(Color.parseColor("#1E1E1E")))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun build() {
        for (rowKeys in layout) {
            val row = LinearLayout(container.context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44))
            }
            for (key in rowKeys) {
                val button = TextView(container.context).apply {
                    text = key.label
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    typeface = android.graphics.Typeface.MONOSPACE
                    background = buttonBackground()
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                        setMargins(dp(1), dp(1), dp(1), dp(1))
                    }
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
