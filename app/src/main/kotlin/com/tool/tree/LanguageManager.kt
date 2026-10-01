package com.tool.tree

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.io.File
import java.util.Locale

object LanguageManager {
    @Volatile private var appResources: Resources? = null

    @Synchronized
    @Suppress("DEPRECATION")
    fun init(context: Context) {
        runCatching {
            val lang = File(context.filesDir, "home/usr/log/language")
                .takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
            if (lang != null) {
                val locale = Locale.forLanguageTag(lang.replace("_","-"))
                if (Locale.getDefault() != locale) {
                    Locale.setDefault(locale)
                    val config = Configuration(context.resources.configuration)
                    config.setLocale(locale)
                    context.resources.updateConfiguration(config, context.resources.displayMetrics)
                }
            }
            appResources = context.resources
        }.getOrElse { appResources = context.resources }
    }

    fun resources(context: Context): Resources {
        if (appResources == null) init(context)
        return appResources ?: context.resources
    }

    fun reload(context: Context) {
        appResources = null
        init(context)
    }
}
