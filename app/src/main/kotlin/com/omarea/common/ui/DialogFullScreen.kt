package com.omarea.common.ui

import android.app.Activity
import android.app.Dialog
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
        return Dialog(activity!!, if (themeResId != 0) themeResId else R.style.dialog_full_screen_light)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = this.activity
        val d = dialog
        if (activity != null && d != null) {
            d.window?.run {
                DialogHelper.applyEdgeToEdge(this, darkMode, view)
            }

            if (swipeToDismissEnabled && isCancelable) {
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

    protected fun setSwipeBackRuntimeEnabled(enabled: Boolean) {
        swipeToDismissBinding?.setEnabled(enabled)
    }

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