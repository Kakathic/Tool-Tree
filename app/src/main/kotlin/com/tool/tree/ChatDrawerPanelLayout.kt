package com.tool.tree

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import com.omarea.common.ui.BlurEngine
import com.omarea.common.ui.BlurViewLinearLayout

/**
 * Nền ngăn kéo lịch sử chat. Khi blur đang bật (chế độ ảnh nền) thì vẽ blur giống thanh trên/dưới;
 * khi blur tắt thì lộ android:background (colorBackground) như cũ.
 */
class ChatDrawerPanelLayout(context: Context, attrs: AttributeSet?) : BlurViewLinearLayout(context, attrs) {
    init {
        engine.cornerRadius = 0f
    }

    // Ngăn kéo nằm mép phải: chỉ kẻ một đường ở mép trái
    override fun drawStroke(canvas: Canvas) {
        val paint = BlurEngine.getStrokePaint(context)
        val x = paint.strokeWidth / 2f
        canvas.drawLine(x, 0f, x, height.toFloat(), paint)
    }
}
