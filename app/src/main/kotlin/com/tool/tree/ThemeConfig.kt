package com.tool.tree

import android.app.Activity
import android.content.Context
import androidx.core.content.edit

class ThemeConfig(private val context: Context) {

    private val config = context.getSharedPreferences("kr-script-config", Context.MODE_PRIVATE)

    companion object {
        const val KEY_THEME_MODE = "ThemeMode"
    }

    fun getThemeMode(): Int {
        return config.getInt(KEY_THEME_MODE, 0)
    }

    fun setThemeMode(mode: Int) {
        val newMode = mode.coerceIn(0, 5)
        if (getThemeMode() != newMode) {
            config.edit { putInt(KEY_THEME_MODE, newMode) }
            if (context is Activity) {
                context.recreate()
            }
        }
    }
}
