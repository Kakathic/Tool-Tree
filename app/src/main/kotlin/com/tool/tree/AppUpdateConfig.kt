package com.tool.tree

import android.content.Context
import androidx.core.content.edit

class AppUpdateConfig(context: Context) {
    private val config = context.getSharedPreferences("kr-script-config", Context.MODE_PRIVATE)

    companion object {
        const val KEY_DISMISSED_SHA256 = "UpdateDismissedSha256"
    }

    fun getDismissedSha256(): String? {
        return config.getString(KEY_DISMISSED_SHA256, null)
    }

    fun setDismissedSha256(sha256: String) {
        config.edit { putString(KEY_DISMISSED_SHA256, sha256) }
    }

    fun isDismissed(sha256: String): Boolean {
        return getDismissedSha256()?.equals(sha256, ignoreCase = true) == true
    }
}
