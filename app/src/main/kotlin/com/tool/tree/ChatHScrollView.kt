package com.tool.tree

import android.content.Context
import android.view.View
import android.widget.HorizontalScrollView

class ChatHScrollView(context: Context) : HorizontalScrollView(context) {
    var maxWidthPx = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var spec = widthMeasureSpec
        if (maxWidthPx > 0) {
            val mode = View.MeasureSpec.getMode(widthMeasureSpec)
            val size = View.MeasureSpec.getSize(widthMeasureSpec)
            if (mode == View.MeasureSpec.UNSPECIFIED || size > maxWidthPx) {
                spec = View.MeasureSpec.makeMeasureSpec(maxWidthPx, View.MeasureSpec.AT_MOST)
            }
        }
        super.onMeasure(spec, heightMeasureSpec)
    }
}
