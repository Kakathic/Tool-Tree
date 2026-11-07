package com.tool.tree

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import com.omarea.common.ui.BlurEngine
import com.omarea.common.ui.BlurViewLinearLayout

// Nền ngăn kéo mục lục bên trái: blur giống thanh trên/dưới, đường kẻ ở mép phải
class MainDrawerPanelLayout(context: Context, attrs: AttributeSet?) : BlurViewLinearLayout(context, attrs) {
    init {
        engine.cornerRadius = 0f
    }

    override fun drawStroke(canvas: Canvas) {
        val paint = BlurEngine.getStrokePaint(context)
        val x = width - paint.strokeWidth / 2f
        canvas.drawLine(x, 0f, x, height.toFloat(), paint)
    }
}
