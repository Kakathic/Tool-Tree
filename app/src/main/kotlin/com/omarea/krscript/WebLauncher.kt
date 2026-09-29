package com.omarea.krscript

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

object WebLauncher {
    /**
     * http/https -> Chrome Custom Tabs; scheme khác (market://, tel:, mailto:...) hoặc máy không có
     * trình duyệt hỗ trợ Custom Tabs -> ACTION_VIEW như cũ. Ném exception nếu không mở được.
     */
    fun open(context: Context, url: String) {
        val uri = Uri.parse(url.trim())
        val scheme = uri.scheme?.lowercase()
        if (scheme == "http" || scheme == "https") {
            try {
                val tabs = CustomTabsIntent.Builder().setShowTitle(true).build()
                if (context !is Activity) tabs.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                tabs.launchUrl(context, uri)
                return
            } catch (_: ActivityNotFoundException) {
            }
        }
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
