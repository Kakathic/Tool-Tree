package com.omarea.krscript.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.ScrollView

class KrScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.scrollViewStyle
) : ScrollView(context, attrs, defStyleAttr) {

    override fun canScrollVertically(direction: Int): Boolean {
        if (super.canScrollVertically(direction)) return true
        if (direction <= 0) return false
        val child = getChildAt(0) ?: return false
        val scrollRange = child.height - (height - paddingTop - paddingBottom)
        return scrollRange > 0 && scrollY < scrollRange
    }
}
