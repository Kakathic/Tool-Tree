package com.tool.tree

import android.content.Context
import android.graphics.Rect
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

    // Mọi yêu cầu "đưa vùng này vào màn hình" từ bong bóng (nhận focus, chọn chữ, layout lại) đều đi qua đây;
    // bong bóng dài hơn màn hình nên danh sách bị kéo về đầu bong bóng. Chặn hẳn ở nguồn, không tuỳ phiên bản Android.
    override fun requestRectangleOnScreen(rectangle: Rect, immediate: Boolean): Boolean = false
}
