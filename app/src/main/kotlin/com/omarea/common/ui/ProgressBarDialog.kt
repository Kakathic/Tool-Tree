package com.omarea.common.ui

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.tool.tree.R
import com.omarea.common.shell.AsynSuShellUnit

open class ProgressBarDialog(private var context: Activity, private var uniqueId: String? = null) {
    private var alert: DialogHelper.DialogWrap? = null
    private var textView: TextView? = null
    private var cancelButton: Button? = null
    private var cancelCallback: (() -> Unit)? = null

    companion object {
        private val dialogs = LinkedHashMap<String, DialogHelper.DialogWrap>()
    }

    class DefaultHandler(private var alertDialog: DialogHelper.DialogWrap?) : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)

            try {
                if (alertDialog == null) {
                    return
                }
                if (msg.what == 10) {
                    alertDialog?.dismiss()
                    if (msg.obj == true) {
                        alertDialog?.context?.let {Toast.makeText(it, R.string.execute_success, Toast.LENGTH_SHORT).show()}
                    } else {
                        alertDialog?.context?.let {Toast.makeText(it, R.string.execute_fail, Toast.LENGTH_LONG).show()}
                    }
                } else if (msg.what == -1) {
                    alertDialog?.context?.let {Toast.makeText(it, R.string.execute_fail, Toast.LENGTH_LONG).show()}
                } else if (msg.what == 0 && msg.obj == false) {
                    alertDialog?.dismiss()
                    alertDialog?.context?.let {Toast.makeText(it, R.string.execute_fail, Toast.LENGTH_LONG).show()}
                }
            } catch (_: Exception) {
            }
        }
    }

    fun execShell(cmd: String, handler: Handler? = null) {
        val layoutInflater = LayoutInflater.from(context)
        val dialog = layoutInflater.inflate(R.layout.dialog_loading, null)
        val textView: TextView = (dialog.findViewById(R.id.dialog_text))
        textView.text = context.getString(R.string.execute_wait)
        alert = DialogHelper.customDialog(context, dialog, false)
        if (handler == null) {
            AsynSuShellUnit(DefaultHandler(alert)).exec(cmd).waitFor()
        } else {
            AsynSuShellUnit(handler).exec(cmd).waitFor()
        }
    }

    fun execShell(sb: StringBuilder, handler: Handler? = null) {
        execShell(sb.toString(), handler)
    }

    fun isDialogShow(): Boolean {
        return alert?.isShowing == true
    }

    fun hideDialog() {
        cancelCallback = null
        try {
            if (alert != null) {
                alert?.dismiss()
                alert = null
            }
        } catch (_: Exception) {
        }
        textView = null
        cancelButton = null

        uniqueId?.run {
            if (dialogs.containsKey(this)) {
                dialogs.remove(this)
            }
        }
    }

    fun setCancelCallback(onCancel: (() -> Unit)?) {
        cancelCallback = onCancel
        val btn = cancelButton ?: return
        if (onCancel != null) {
            btn.visibility = View.VISIBLE
            btn.setOnClickListener {
                val cb = cancelCallback
                hideDialog()
                cb?.invoke()
            }
        } else {
            btn.visibility = View.GONE
        }
    }

    fun showDialogWithCancel(text: String, onCancel: () -> Unit): ProgressBarDialog {
        cancelCallback = onCancel
        showDialog(text)
        return this
    }

    fun showDialog(text: String = "Loading, please wait..."): ProgressBarDialog {
        if (textView != null && alert?.isShowing == true) {
            textView?.text = text
        } else {
            val layoutInflater = LayoutInflater.from(context)
            val dialog = layoutInflater.inflate(R.layout.dialog_loading, null)

            textView = dialog.findViewById(R.id.dialog_text)
            textView?.text = text

            cancelButton = dialog.findViewById(R.id.dialog_cancel_button)
            val cb = cancelCallback
            if (cb != null) {
                cancelButton?.visibility = View.VISIBLE
                cancelButton?.setOnClickListener {
                    val captured = cancelCallback
                    hideDialog()
                    captured?.invoke()
                }
            } else {
                cancelButton?.visibility = View.GONE
            }

            alert = DialogHelper.customDialog(context, dialog, false)
        }

        uniqueId?.run {
            dialogs.remove(this)
            alert?.let { dialogs[this] = it }
        }

        return this
    }
}
