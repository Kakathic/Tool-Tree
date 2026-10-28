package com.omarea.common.shared

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object ShareFileHelper {
    private val invalidChars = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

    @JvmStatic
    fun safeFileName(title: String?, fallback: String): String {
        val cleaned = (title ?: "").replace(invalidChars, "_").trim().trim('.').take(80).trim()
        return cleaned.ifEmpty { fallback }
    }

    @JvmStatic
    fun shareTextAsFile(
        context: Context,
        text: String,
        title: String?,
        fallbackName: String,
        chooserTitle: String?,
        failText: String
    ) {
        val appContext = context.applicationContext
        val baseName = safeFileName(title, fallbackName)
        Thread {
            val shareIntent = runCatching {
                val dir = File(appContext.cacheDir, "share").apply { mkdirs() }
                val file = File(dir, "$baseName.txt")
                file.writeText(text)
                val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.provider", file)
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }.getOrNull()

            Handler(Looper.getMainLooper()).post {
                if (shareIntent == null) {
                    Toast.makeText(appContext, failText, Toast.LENGTH_SHORT).show()
                } else {
                    runCatching {
                        val chooser = Intent.createChooser(shareIntent, chooserTitle)
                        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(chooser)
                    }.onFailure { Toast.makeText(appContext, failText, Toast.LENGTH_SHORT).show() }
                }
            }
        }.start()
    }
}
