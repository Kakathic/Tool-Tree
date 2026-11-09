package com.omarea.krscript.config

import android.content.Context
import com.omarea.krscript.model.PageNode

object MenuStateStore {
    private const val PREFS = "kr-menu-state"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun k(kind: String, pageId: String, key: String) = "$kind|$pageId|$key"

    fun pageId(config: PageNode): String =
        config.pageConfigPath.ifEmpty { config.pageConfigSh }.ifEmpty { config.key }

    fun getInt(context: Context, pageId: String, key: String): Int? {
        val p = prefs(context)
        val name = k("i", pageId, key)
        return if (p.contains(name)) p.getInt(name, 0) else null
    }

    fun putInt(context: Context, pageId: String, key: String, value: Int) {
        prefs(context).edit().putInt(k("i", pageId, key), value).apply()
    }

    fun getBool(context: Context, pageId: String, key: String): Boolean? {
        val p = prefs(context)
        val name = k("b", pageId, key)
        return if (p.contains(name)) p.getBoolean(name, false) else null
    }

    fun putBool(context: Context, pageId: String, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(k("b", pageId, key), value).apply()
    }

    fun getString(context: Context, pageId: String, key: String): String? =
        prefs(context).getString(k("s", pageId, key), null)

    fun putString(context: Context, pageId: String, key: String, value: String) {
        prefs(context).edit().putString(k("s", pageId, key), value).apply()
    }
}
