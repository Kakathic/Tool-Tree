package com.omarea.krscript.config

import android.content.Context
import com.omarea.krscript.model.ActionNode

object ActionShowMemory {
    private const val PREF_NAME = "kr-show-memory"

    private fun buildKey(action: ActionNode): String {
        val actionId = action.key.ifEmpty { action.title }
        return "${action.currentPageConfigPath}|$actionId"
    }

    fun isConfirmed(context: Context, action: ActionNode): Boolean {
        val spf = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return spf.getBoolean(buildKey(action), false)
    }

    fun markConfirmed(context: Context, action: ActionNode) {
        val spf = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        spf.edit().putBoolean(buildKey(action), true).apply()
    }
}
