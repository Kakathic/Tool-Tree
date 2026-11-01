package com.tool.tree

import android.app.Activity
import android.content.Context
import androidx.core.content.edit

class ThemeConfig(private val context: Context) {

    private val config = context.getSharedPreferences("kr-script-config", Context.MODE_PRIVATE)

    companion object {
        const val KEY_THEME_MODE = "ThemeMode"
        const val KEY_BLUR_DISABLED = "BlurDisabled"
        const val KEY_DIRECT_BG = "DirectBg"
    }

    fun getThemeMode(): Int {
        return config.getInt(KEY_THEME_MODE, 0)
    }

    fun isBlurDisabled(): Boolean {
        return config.getBoolean(KEY_BLUR_DISABLED, false)
    }

    fun setBlurDisabled(disabled: Boolean) {
        config.edit { putBoolean(KEY_BLUR_DISABLED, disabled) }
    }

    fun isDirectBgEnabled(): Boolean {
        return config.getBoolean(KEY_DIRECT_BG, false)
    }

    fun setDirectBgEnabled(enabled: Boolean) {
        config.edit { putBoolean(KEY_DIRECT_BG, enabled) }
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
