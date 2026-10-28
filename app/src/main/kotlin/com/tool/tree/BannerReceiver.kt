package com.tool.tree

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.omarea.common.ui.BannerNotificationManager
import com.omarea.common.ui.BannerPosition
import com.omarea.common.ui.BannerType
import com.omarea.krscript.config.StringResRef
import java.io.File

class BannerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val rawText = intent.getStringExtra("text") ?: return
        val message = StringResRef.resolve(context, rawText)
        val title = intent.getStringExtra("title")?.let { StringResRef.resolve(context, it) }
        val typeExtra = intent.getStringExtra("type")
        val colorHex = typeExtra?.takeIf { it.startsWith("#") }
        val type = when (typeExtra?.lowercase()) {
            "success" -> BannerType.SUCCESS
            "warning" -> BannerType.WARNING
            "error" -> BannerType.ERROR
            else -> BannerType.INFO
        }
        val position = when (intent.getStringExtra("position")?.lowercase()) {
            "top" -> BannerPosition.TOP
            else -> BannerPosition.BOTTOM
        }
        val icon = intent.getStringExtra("icon")
        val script = intent.getStringExtra("script")
        val confirm = intent.getStringExtra("confirm")?.let { StringResRef.resolve(context, it) }
        val cancel = intent.getStringExtra("cancel")?.let { StringResRef.resolve(context, it) }
        val countdown = intent.getIntExtra("countdown", 5)
        val config = intent.getStringExtra("config")
        val configSh = intent.getStringExtra("config-sh")
        val itemId = intent.getStringExtra("id")
        val hasConfigPage = (!config.isNullOrEmpty() && File(config).isFile) || !configSh.isNullOrEmpty()

        BannerNotificationManager.show(
            title = title,
            message = message,
            type = type,
            position = position,
            icon = icon,
            colorHex = colorHex,
            script = script,
            confirmText = confirm,
            cancelText = cancel,
            countdownSeconds = countdown,
            onConfirm = if (hasConfigPage) {
                { activity -> ShowDialogReceiver.openConfigPage(activity, config, configSh, itemId) }
            } else null,
            onNoActivity = {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        )
    }
}