package com.omarea.common.ui

import android.view.Window
import java.lang.ref.WeakReference

object TopWindowHolder {
    private val stack = ArrayDeque<WeakReference<Window>>()

    @Synchronized
    fun push(window: Window) {
        stack.removeAll { it.get() == null || it.get() === window }
        stack.addLast(WeakReference(window))
    }

    @Synchronized
    fun pop(window: Window) {
        stack.removeAll { it.get() == null || it.get() === window }
    }

    @Synchronized
    fun current(): Window? {
        while (stack.isNotEmpty()) {
            val window = stack.last().get()
            if (window == null) {
                stack.removeLast()
            } else {
                return window
            }
        }
        return null
    }
}
