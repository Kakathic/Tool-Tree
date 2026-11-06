package com.tool.tree

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * Danh sách chat. Chạm vào bong bóng (TextView selectable) làm view đó nhận focus, RecyclerView mặc định
 * sẽ tự cuộn để đưa view đang focus vào màn hình nên danh sách bị nhảy. Ở đây bỏ qua việc cuộn đó,
 * chỉ áp dụng lúc đang chuyển focus; các yêu cầu cuộn khác (kéo tay cầm chọn chữ...) vẫn hoạt động.
 */
class ChatRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {
    private var changingFocus = false

    override fun requestChildFocus(child: View?, focused: View?) {
        changingFocus = true
        try {
            super.requestChildFocus(child, focused)
        } finally {
            changingFocus = false
        }
    }

    override fun requestChildRectangleOnScreen(child: View, rect: Rect, immediate: Boolean): Boolean {
        if (changingFocus) return false
        return super.requestChildRectangleOnScreen(child, rect, immediate)
    }
}
