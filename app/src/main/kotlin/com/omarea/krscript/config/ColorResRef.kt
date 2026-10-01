package com.omarea.krscript.config

import android.content.Context
import androidx.core.content.ContextCompat

object ColorResRef {
    private val COLOR_REF_REGEX =
        Regex("""^@(android:)?color[:/]([_a-zA-Z][_a-zA-Z0-9.]*)$""")

    fun isColorRef(raw: String?): Boolean {
        if (raw.isNullOrEmpty()) return false
        return COLOR_REF_REGEX.matches(raw.trim())
    }

    fun resolve(context: Context, raw: String?): Int? {
        if (raw.isNullOrEmpty()) return null
        val match = COLOR_REF_REGEX.matchEntire(raw.trim()) ?: return null
        val isAndroidNs = match.groupValues[1].isNotEmpty()
        val name = match.groupValues[2]

        return try {
            val packageName = if (isAndroidNs) "android" else context.packageName
            val id = context.resources.getIdentifier(name, "color", packageName)
            if (id != 0) ContextCompat.getColor(context, id) else null
        } catch (_: Exception) {
            null
        }
    }
}
