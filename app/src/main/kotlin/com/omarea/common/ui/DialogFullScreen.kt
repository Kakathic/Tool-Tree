package com.omarea.common.ui

import android.app.Activity
import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.tool.tree.R


open class DialogFullScreen(private val layout: Int, private val darkMode: Boolean) : androidx.fragment.app.DialogFragment() {
    class SwipeToDismissBinding internal constructor(
        private val helper: DialogSwipeBackHelper
    ) {
        fun release(dialog: Dialog) {
            helper.release()
        }
        fun setEnabled(enabled: Boolean) {
            helper.enabled = enabled
        }
    }

    companion object {
        fun bindSwipeToDismiss(activity: Activity, dialog: Dialog, onBack: () -> Unit): SwipeToDismissBinding? {
            val window = dialog.window ?: return null

            // Ưu tiên LIVE BLUR (API 31+, xem DialogHelper.canUseLiveBlur/setWindowBlurBg): nền
            // mờ là thuộc tính của WINDOW nên không "trượt" theo cùng content khi vuốt, NHƯNG vì
            // hệ thống tự blur đúng nội dung THẬT đang hiển thị theo thời gian thực (không phải
            // ảnh chụp tĩnh), phần lộ ra lúc vuốt luôn hiện đúng nội dung hiện tại - không bị lỗi
            // "lộ ra ảnh blur tĩnh cũ" mà DialogSwipeBackBlurWrapper (bọc ảnh chụp + blur bằng
            // RenderScript, dùng cho API < 31 hoặc khi live blur không khả dụng) từng phải giải
            // quyết bằng cách bọc ảnh vào 1 view con trượt cùng content. Vì vậy không cần bọc gì
            // thêm, chỉ cần bind thẳng content view làm swipeTarget.
            val swipeTarget = if (DialogHelper.canUseLiveBlur(activity)) {
                DialogHelper.setWindowBlurBg(window, activity)
                window.findViewById(android.R.id.content)
            } else {
                DialogSwipeBackBlurWrapper.wrap(activity, window) ?: run {
                    DialogHelper.setWindowBlurBg(window, activity)
                    window.findViewById(android.R.id.content)
                }
            }
            val helper = DialogSwipeBackHelper.bind(dialog, swipeTarget) { onBack() } ?: return null
            return SwipeToDismissBinding(helper)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        currentView = inflater.inflate(layout, container)
        return currentView
    }

    private var themeResId: Int = 0
    private lateinit var currentView: View

    protected var swipeToDismissEnabled = true
    private var swipeToDismissBinding: SwipeToDismissBinding? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Dialog(activity!!, if (themeResId != 0) themeResId else R.style.dialog_full_screen_light)
        } else {
            Dialog(activity!!, -1)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = this.activity
        val d = dialog
        if (activity != null && d != null) {
            d.window?.run {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    setWindowAnimations(android.R.style.Animation_Translucent)
                }
                DialogHelper.applyEdgeToEdge(this, darkMode, view)
            }

            if (swipeToDismissEnabled && isCancelable) {
                // Trước đây dùng view.post{} - chạy SAU khi frame đầu tiên (chữ, chưa có lớp mờ
                // nền) đã hiện ra màn hình, vì DialogSwipeBackBlurWrapper.wrap() bên trong
                // bindSwipeToDismiss() chỉ chạy lúc đó -> gây hiệu ứng "chữ hiện trước, mờ hiện
                // sau". runBeforeFirstDraw() chạy TRƯỚC khi frame đầu tiên được vẽ (nội dung đã
                // attach xong nên wrap() vẫn lấy được view con), nên mờ + chữ cùng hiện 1 lượt.
                runBeforeFirstDraw(view) {
                    if (d.window == null) return@runBeforeFirstDraw
                    swipeToDismissBinding = bindSwipeToDismiss(activity, d) { closeView() }
                }
            } else {
                d.window?.run { DialogHelper.setWindowBlurBg(this, activity) }
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
    }

    override fun onDestroyView() {
        dialog?.let { swipeToDismissBinding?.release(it) }
        swipeToDismissBinding = null
        super.onDestroyView()
    }

    // isCancelable chỉ được đọc 1 LẦN lúc bindSwipeToDismiss() ở onViewCreated() - đổi
    // isCancelable sau đó (lúc dialog đang chạy) không tự tắt vuốt-lùi đã bind sẵn, nên dialog
    // con cần chủ động gọi hàm này để tắt/bật vuốt-lùi đúng lúc (vd: khi bắt đầu/kết thúc tải).
    // Tên hàm KHÔNG được trùng "setSwipeToDismissEnabled" vì property var swipeToDismissEnabled
    // ở trên đã tự sinh sẵn setter cùng chữ ký JVM đó, gây lỗi "Platform declaration clash".
    protected fun setSwipeBackRuntimeEnabled(enabled: Boolean) {
        swipeToDismissBinding?.setEnabled(enabled)
    }

    /**
     * Chạy [action] NGAY TRƯỚC khi frame đầu tiên của [view] được vẽ ra màn hình (khác
     * view.post{} - chạy SAU khi frame đầu tiên đã hiện). Nội dung đã attach xong vào lúc này
     * nên DialogSwipeBackBlurWrapper.wrap() (gọi bên trong bindSwipeToDismiss()) vẫn lấy được
     * view con của android.R.id.content như bình thường.
     * Tự gỡ listener sau đúng 1 lần gọi. Xử lý cả trường hợp [view] CHƯA attach vào window lúc
     * gọi hàm này (viewTreeObserver lấy lúc chưa attach không phải observer thật của cây view -
     * phải đợi onViewAttachedToWindow rồi mới addOnPreDrawListener lên observer thật).
     */
    private fun runBeforeFirstDraw(view: View, action: () -> Unit) {
        fun attachPreDraw() {
            val observer = view.viewTreeObserver
            if (!observer.isAlive) {
                action()
                return
            }
            observer.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    view.viewTreeObserver.removeOnPreDrawListener(this)
                    action()
                    return true
                }
            })
        }

        if (view.isAttachedToWindow) {
            attachPreDraw()
        } else {
            view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    view.removeOnAttachStateChangeListener(this)
                    attachPreDraw()
                }
                override fun onViewDetachedFromWindow(v: View) {}
            })
        }
    }

    fun closeView() {
        try {
            dismiss()
        } catch (ex: java.lang.Exception) {
        }
    }

    init {
        themeResId = if (darkMode) R.style.dialog_full_screen_dark else R.style.dialog_full_screen_light
    }
}