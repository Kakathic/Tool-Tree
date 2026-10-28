package com.tool.tree

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
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
    private class Key(val id: String, val label: String, val repeat: Boolean = false, val size: Float = 14f, val isBold: Boolean = false)

    private companion object {
        const val ARROW_SIZE = 14f
        const val ROW_HEIGHT_DP = 30
    }

    private val layout = listOf(
        listOf(Key("ESC", "ESC"), Key("/", "/"), Key("$", "$"), Key("HOME", "HOME"), Key("UP", "↑", true, ARROW_SIZE, true), Key("END", "END"), Key("PGUP", "PGUP", true)),
        listOf(Key("TAB", "TAB"), Key("CTRL", "CTRL"), Key("ALT", "ALT"), Key("LEFT", "←", true, ARROW_SIZE, true), Key("DOWN", "↓", true, ARROW_SIZE, true), Key("RIGHT", "→", true, ARROW_SIZE, true), Key("PGDN", "PGDN", true))
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

    private fun keyBackground(): Drawable {
        val normal = ColorDrawable(Color.TRANSPARENT)
        val pressed = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#33FFFFFF"))
            cornerRadius = dp(4).toFloat()
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun build() {
        // Tạo khoảng hở ở đáy
        container.setPadding(container.paddingLeft, container.paddingTop, container.paddingRight, dp(8))

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
                    
                    if (key.isBold) {
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    } else {
                        typeface = android.graphics.Typeface.DEFAULT
                    }

                    background = keyBackground()
                    val margin = dp(2)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                        setMargins(margin, margin, margin, margin)
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
        var repeating = false
        val repeater = object : Runnable {
            override fun run() {
                if (!repeating) button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                repeating = true
                press(key)
                handler.postDelayed(this, 80)
            }
        }
        fun isInside(v: View, event: MotionEvent): Boolean =
            event.x >= 0 && event.x <= v.width && event.y >= 0 && event.y <= v.height

        button.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    repeating = false
                    v.isPressed = true
                    if (key.repeat) handler.postDelayed(repeater, 400)
                }
                MotionEvent.ACTION_MOVE -> v.isPressed = isInside(v, event)
                MotionEvent.ACTION_UP -> {
                    v.isPressed = false
                    handler.removeCallbacks(repeater)
                    if (!repeating && isInside(v, event)) {
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        press(key)
                        v.performClick()
                    }
                    repeating = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    handler.removeCallbacks(repeater)
                    repeating = false
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
            "/", "$" -> terminalView.inputCodePoint(key.id[0].code, false, false)
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
