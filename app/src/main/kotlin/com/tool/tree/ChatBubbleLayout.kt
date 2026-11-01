package com.tool.tree

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import com.omarea.common.ui.BlurViewLinearLayout

class ChatBubbleLayout(context: Context, attrs: AttributeSet?) : BlurViewLinearLayout(context, attrs) {
    var overlayColor: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    init {
        setDrawStrokeEnabled(false)
    }

    override fun drawOverBlur(canvas: Canvas) {
        if (Color.alpha(overlayColor) != 0) canvas.drawColor(overlayColor)
    }
}
