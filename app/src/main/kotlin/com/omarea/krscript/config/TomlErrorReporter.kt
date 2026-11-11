package com.omarea.krscript.config

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.omarea.common.ui.CurrentActivityHolder
import com.omarea.common.ui.DialogHelper
import java.lang.ref.WeakReference

object TomlErrorReporter {
    private val handler = Handler(Looper.getMainLooper())
    private const val FIRST_DELAY_MS = 500L
    private const val RETRY_DELAY_MS = 400L
    private const val MAX_RETRY = 8

    private var showing = false
    private var lastKey = ""
    private var lastActivity: WeakReference<Activity>? = null

    // Dialog chỉ có nút Xác nhận; trễ nhẹ để trang lỗi kịp đóng rồi mới hiện lên màn hình đang mở
    // repeatable=false (cảnh báo khi trang vẫn tải): cùng nội dung trên cùng màn hình chỉ hiện 1 lần, tránh lặp mỗi lần tự làm mới
    fun show(context: Context, title: String, message: String, repeatable: Boolean = false) {
        Log.e("KrConfig", "$title\n$message")
        val appContext = context.applicationContext ?: context
        handler.postDelayed({ attempt(appContext, title, message, repeatable, 0) }, FIRST_DELAY_MS)
    }

    private fun attempt(context: Context, title: String, message: String, repeatable: Boolean, retry: Int) {
        if (showing) return
        val key = "$title\n$message"

        val activity = CurrentActivityHolder.get()
        if (activity == null) {
            if (retry < MAX_RETRY) {
                handler.postDelayed({ attempt(context, title, message, repeatable, retry + 1) }, RETRY_DELAY_MS)
            } else {
                Toast.makeText(context, key, Toast.LENGTH_LONG).show()
            }
            return
        }
        if (!repeatable && key == lastKey && lastActivity?.get() === activity) return

        lastKey = key
        lastActivity = WeakReference(activity)
        showing = true
        try {
            DialogHelper.helpInfo(activity, title, message, Runnable { showing = false })
        } catch (ex: Exception) {
            showing = false
            Toast.makeText(context, key, Toast.LENGTH_LONG).show()
        }
    }
}
