package com.omarea.common.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet

class BlurBottomBarLayout(context: Context, attrs: AttributeSet?) : BlurViewLinearLayout(context, attrs) {
    init {
        this.engine.cornerRadius = 0f
    }

    override fun drawStroke(canvas: Canvas) {
        val paint = BlurEngine.getStrokePaint(context)
        val strokeWidth = paint.strokeWidth

        val y = strokeWidth / 2f

        canvas.drawLine(0f, y, width.toFloat(), y, paint)
    }
}
