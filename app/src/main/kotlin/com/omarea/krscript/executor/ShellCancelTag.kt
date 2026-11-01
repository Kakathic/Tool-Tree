package com.omarea.krscript.executor

import android.content.Context
import kotlinx.coroutines.asContextElement
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

/**
 * Gắn một tag cho mọi lệnh shell chạy trong một lượt tải (dialog loading có nút Hủy),
 * để nút Hủy kill được lệnh đang chạy cùng các lệnh con bằng `shell_progres='<tag>' killtree`.
 *
 * Cách dùng: launch(Dispatchers.IO + cancelTag.element()) { ... } - mọi ScriptEnvironmen.executeResultRoot /
 * executeMultipleResultRoot gọi trong coroutine đó (kể cả lồng sâu) tự được truyền tag, không cần sửa từng chỗ gọi.
 */
class ShellCancelTag {
    val tag: String = "ld_${System.currentTimeMillis()}_${seq.incrementAndGet()}"

    /** Phần tử CoroutineContext mang tag này sang luồng đang chạy coroutine */
    fun element(): CoroutineContext.Element = threadTag.asContextElement(tag)

    /** Kill cây tiến trình của mọi lệnh mang tag này (chạy ở luồng riêng, an toàn khi gọi từ UI) */
    fun kill(context: Context) {
        val app = context.applicationContext
        val t = tag
        Thread {
            try {
                ScriptEnvironmen.killTagTree(app, t)
            } catch (_: Exception) {
            }
        }.start()
    }

    companion object {
        private val seq = AtomicInteger()
        private val threadTag = ThreadLocal<String?>()

        /** Tag của lượt tải đang chạy trên luồng hiện tại, null nếu không thuộc lượt nào */
        @JvmStatic
        fun current(): String? = threadTag.get()
    }
}
