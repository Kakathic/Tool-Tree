package com.omarea.krscript.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.ScrollView

// ScrollView gốc có paddingBottom (clipToPadding=false) cho phép cuộn thêm đúng phần padding,
// nhưng canScrollVertically(1) lại tính theo chiều cao đầy đủ, KHÔNG trừ padding. Trang ngắn
// (nội dung nằm trong khoảng padding đó) bị coi là "không cuộn được" nên onInterceptTouchEvent()
// không bao giờ chiếm cử chỉ - chạm vào thẻ clickable rồi vuốt thì thẻ giữ cử chỉ, không cuộn.
// Ghi đè để canScrollVertically(1) khớp với phạm vi cuộn thật.
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
