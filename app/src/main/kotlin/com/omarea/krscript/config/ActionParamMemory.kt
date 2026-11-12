package com.omarea.krscript.config

import android.content.Context
import com.omarea.krscript.model.ActionNode
import com.omarea.krscript.model.ActionParamInfo

object ActionParamMemory {
    private const val PREF_NAME = "kr-param-memory"

    private fun buildKey(action: ActionNode, paramName: String): String {
        val actionId = action.key.ifEmpty { action.title }
        return "${action.currentPageConfigPath}|$actionId|$paramName"
    }

    fun load(context: Context, action: ActionNode, param: ActionParamInfo): String? {
        if (!param.remember) return null
        val name = param.name ?: return null
        val spf = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val stored = spf.getString(buildKey(action, name), null) ?: return null
        return if (param.type == "password") ParamSecretCipher.decrypt(stored) else stored
    }

    fun save(context: Context, action: ActionNode, actionParamInfos: List<ActionParamInfo>, values: Map<String, String>) {
        val toRemember = actionParamInfos.filter { it.remember && !it.name.isNullOrEmpty() }
        if (toRemember.isEmpty()) return

        val spf = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        spf.edit().apply {
            for (param in toRemember) {
                val name = param.name ?: continue
                val value = values[name] ?: continue
                if (param.type == "password") {
                    val encrypted = ParamSecretCipher.encrypt(value)
                    if (encrypted == null) remove(buildKey(action, name)) else putString(buildKey(action, name), encrypted)
                } else {
                    putString(buildKey(action, name), value)
                }
            }
            apply()
        }
    }
}
