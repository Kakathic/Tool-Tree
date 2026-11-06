package com.tool.tree

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.widget.HorizontalScrollView

class ChatHScrollView(context: Context) : HorizontalScrollView(context) {
    var maxWidthPx = 0

    // Chạm vào khối code làm TextView bên trong nhận focus, HorizontalScrollView sẽ tự cuộn ngang để
    // đưa nó vào màn hình. Bỏ qua việc cuộn đó lúc chuyển focus.
    private var changingFocus = false

    override fun requestChildFocus(child: View?, focused: View?) {
        changingFocus = true
        try {
            super.requestChildFocus(child, focused)
        } finally {
            changingFocus = false
        }
    }

    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int {
        if (changingFocus) return 0
        return super.computeScrollDeltaToGetChildRectOnScreen(rect)
    }

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
