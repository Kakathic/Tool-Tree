package com.tool.tree

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import com.omarea.common.ui.BlurEngine
import com.omarea.common.ui.BlurTopBarLayout

class ChatBottomBarLayout(context: Context, attrs: AttributeSet?) : BlurTopBarLayout(context, attrs) {
    override val sampleAbove: Boolean = true

    override fun drawStroke(canvas: Canvas) {
        val paint = BlurEngine.getStrokePaint(context)
        val y = paint.strokeWidth / 2f
        canvas.drawLine(0f, y, width.toFloat(), y, paint)
    }
}
