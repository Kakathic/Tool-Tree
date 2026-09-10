package com.omarea.common.ui

import android.app.Activity
import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
            val swipeTarget = DialogSwipeBackBlurWrapper.wrap(activity, window) ?: run {
                DialogHelper.setWindowBlurBg(window, activity)
                window.findViewById(android.R.id.content)
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
                view.post {
                    if (d.window == null) return@post
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
    protected fun setSwipeToDismissEnabled(enabled: Boolean) {
        swipeToDismissBinding?.setEnabled(enabled)
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