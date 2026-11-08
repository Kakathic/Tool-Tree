package com.tool.tree

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView

/**
 * TextView cho bong bóng chat. Bản selectable + wrap_content tự cuộn ngang (bringPointIntoView)
 * khi text dài ra lúc stream, làm chữ bị đẩy lệch và cắt đầu dòng. Bong bóng luôn xuống dòng
 * theo chiều rộng nên không bao giờ cần cuộn, khóa scroll về 0.
 */
class ChatTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : TextView(context, attrs, defStyleAttr) {

    override fun scrollTo(x: Int, y: Int) {
        super.scrollTo(0, 0)
    }

    override fun bringPointIntoView(offset: Int): Boolean = false
}
