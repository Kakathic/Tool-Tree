package com.tool.tree

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import com.omarea.common.ui.CurrentActivityHolder
import com.omarea.common.ui.DialogHelper
import com.omarea.krscript.NotiShellTaskLauncher
import com.omarea.krscript.config.StringResRef
import com.omarea.krscript.model.PageNode
import com.omarea.krscript.model.RunnableNode
import java.io.File

class ShowDialogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val rawDesc = intent.getStringExtra("desc") ?: intent.getStringExtra("text") ?: ""
        val message = StringResRef.resolve(context, rawDesc)

        val activity = CurrentActivityHolder.get()
        if (activity == null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            return
        }

        val rawTitle = intent.getStringExtra("title")
        val title = if (rawTitle.isNullOrEmpty()) {
            activity.getString(R.string.app_name)
        } else {
            StringResRef.resolve(context, rawTitle)
        }

        val confirmText = intent.getStringExtra("confirm")
            ?.let { StringResRef.resolve(context, it) }
            ?: activity.getString(R.string.btn_confirm)
        val cancelText = intent.getStringExtra("cancel")
            ?.let { StringResRef.resolve(context, it) }
            ?: activity.getString(R.string.btn_cancel)

        val script = intent.getStringExtra("script")
        val config = intent.getStringExtra("config")
        val configSh = intent.getStringExtra("config-sh")
        val countdownSeconds = intent.getIntExtra("countdown", 0)
        val force = intent.getBooleanExtra("force", false)

        val hasConfigPage = (!config.isNullOrEmpty() && File(config).isFile) || !configSh.isNullOrEmpty()
        val hasAction = !script.isNullOrEmpty() || hasConfigPage

        val handler = Handler(Looper.getMainLooper())
        var countdownRunnable: Runnable? = null

        val dialogWrap: DialogHelper.DialogWrap
        val countdownBtnView: TextView?
        val countdownBaseLabel: String

        if (hasAction) {
            val onConfirm = DialogHelper.DialogButton(confirmText, Runnable {
                countdownRunnable?.let { handler.removeCallbacks(it) }
                if (!script.isNullOrEmpty()) {
                    runConfirmScript(activity, title, script)
                }
                openConfigPageIfAny(activity, config, configSh)
            })
            val onCancel = DialogHelper.DialogButton(cancelText, Runnable {
                countdownRunnable?.let { handler.removeCallbacks(it) }
            })

            dialogWrap = DialogHelper.confirm(
                activity, title, message, null, onConfirm, onCancel,
                cancelable = !force
            )
            countdownBtnView = dialogWrap.dialog.findViewById(R.id.btn_cancel)
            countdownBaseLabel = cancelText
        } else {
            dialogWrap = DialogHelper.helpInfo(activity, title, message, null)
            dialogWrap.setCancelable(!force)
            countdownBtnView = dialogWrap.dialog.findViewById(R.id.btn_confirm)
            countdownBtnView?.text = confirmText
            countdownBaseLabel = confirmText
        }

        if (countdownSeconds > 0) {
            var remaining = countdownSeconds - 1
            val tick = object : Runnable {
                override fun run() {
                    if (!dialogWrap.isShowing) return
                    if (remaining <= 0) {
                        dialogWrap.dismiss()
                        return
                    }
                    countdownBtnView?.text = "$countdownBaseLabel ($remaining)"
                    remaining--
                    handler.postDelayed(this, 1000L)
                }
            }
            countdownRunnable = tick
            handler.postDelayed(tick, 1000L)
        }
    }

    private fun runConfirmScript(activity: Activity, dialogTitle: String, script: String) {
        val nodeInfo = RunnableNode("").apply {
            title = dialogTitle
            shell = RunnableNode.shellModeBgTask
            interruptable = true
        }
        NotiShellTaskLauncher.startTask(activity.applicationContext, script, nodeInfo)
    }

    private fun openConfigPageIfAny(activity: Activity, config: String?, configSh: String?) {
        val pageNode = PageNode("")
        if (!config.isNullOrEmpty() && File(config).isFile) {
            pageNode.pageConfigPath = config
        } else if (!configSh.isNullOrEmpty()) {
            pageNode.pageConfigSh = configSh
        } else {
            return
        }
        activity.startActivity(Intent(activity, ActionPage::class.java).apply {
            putExtra("page", pageNode)
        })
    }
}