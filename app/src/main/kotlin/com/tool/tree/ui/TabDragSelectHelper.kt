package com.tool.tree.ui

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.google.android.material.tabs.TabLayout
import kotlin.math.abs

// Cho phép nhấn GIỮ 1 icon tab rồi kéo tay qua các icon khác để chuyển tab liên tục, thay vì
// chỉ tap được từng icon một như mặc định của TabLayout. Tap nhanh (không giữ đủ lâu) vẫn chọn
// tab như bình thường.
object TabDragSelectHelper {
    private val handler = Handler(Looper.getMainLooper())

    fun attach(tabLayout: TabLayout) {
        val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
        val touchSlop = ViewConfiguration.get(tabLayout.context).scaledTouchSlop

        for (position in 0 until tabLayout.tabCount) {
            val customView = tabLayout.getTabAt(position)?.customView ?: continue

            var dragging = false
            var downX = 0f
            var downY = 0f
            val startDrag = Runnable { dragging = true }

            customView.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        dragging = false
                        downX = event.rawX
                        downY = event.rawY
                        v.isPressed = true
                        handler.postDelayed(startDrag, longPressTimeout)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!dragging) {
                            // Kéo tay ra xa quá sớm (trước khi đủ thời gian "giữ") -> huỷ ý định
                            // kéo chọn, coi như thao tác vuốt/thả bình thường.
                            if (abs(event.rawX - downX) > touchSlop || abs(event.rawY - downY) > touchSlop * 3) {
                                handler.removeCallbacks(startDrag)
                            }
                        } else {
                            v.isPressed = false
                            findTabAt(tabLayout, event.rawX)?.let { target ->
                                if (tabLayout.selectedTabPosition != target) {
                                    tabLayout.getTabAt(target)?.select()
                                }
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        handler.removeCallbacks(startDrag)
                        v.isPressed = false
                        if (!dragging) {
                            tabLayout.getTabAt(position)?.select()
                            v.performClick()
                        }
                        dragging = false
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacks(startDrag)
                        v.isPressed = false
                        dragging = false
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun findTabAt(tabLayout: TabLayout, rawX: Float): Int? {
        val loc = IntArray(2)
        for (i in 0 until tabLayout.tabCount) {
            val view = tabLayout.getTabAt(i)?.customView ?: continue
            view.getLocationOnScreen(loc)
            if (rawX >= loc[0] && rawX < loc[0] + view.width) return i
        }
        return null
    }
}
