package com.tool.tree

import android.app.Activity
import android.content.Intent
import com.omarea.common.shell.KeepShellPublic
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

object AppUpdateInstaller {
    class DownloadState {
        @Volatile var connection: HttpURLConnection? = null
        @Volatile var cancelled = false
    }

    fun formatFileSize(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return String.format("%.1f MB", mb)
    }

    fun markdownToHtml(markdown: String): String {
        val linkRegex = Regex("\\[(.+?)\\]\\((.+?)\\)")
        val boldRegex = Regex("\\*\\*(.+?)\\*\\*")
        val headingRegex = Regex("^#+\\s*(.*)$")

        fun applyInlineStyles(raw: String): String {
            var result = linkRegex.replace(raw) { m -> "<a href=\"${m.groupValues[2]}\">${m.groupValues[1]}</a>" }
            result = boldRegex.replace(result) { m -> "<b>${m.groupValues[1]}</b>" }
            return result
        }

        val sb = StringBuilder()
        for (rawLine in markdown.lines()) {
            val line = android.text.TextUtils.htmlEncode(rawLine.trimEnd())
            when {
                line.isBlank() -> sb.append("<br>")
                headingRegex.matches(line) -> {
                    val inner = applyInlineStyles(headingRegex.find(line)!!.groupValues[1])
                    sb.append("<b><big>").append(inner).append("</big></b><br>")
                }
                line.startsWith("+ ") -> {
                    val inner = applyInlineStyles(line.removePrefix("+ ").trim())
                    sb.append("&#8226;&nbsp;").append(inner).append("<br>")
                }
                else -> sb.append(applyInlineStyles(line)).append("<br>")
            }
        }
        return sb.toString()
    }

    fun installApkWithRoot(file: File): Boolean {
        if (!KeepShellPublic.checkRoot()) return false
        val escapedPath = file.absolutePath.replace("'", "'\\''")
        val result = KeepShellPublic.doCmdSync("pm install -r '$escapedPath'")
        return result.contains("Success", ignoreCase = true)
    }

    fun openApkViaSystemInstaller(activity: Activity, file: File) {
        val intent = Intent(activity, OpenFileActivity::class.java)
        intent.putExtra("path", file.absolutePath)
        activity.startActivity(intent)
    }

    fun downloadApk(
        url: String,
        destFile: File,
        onConnectionOpened: (HttpURLConnection) -> Unit,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 15000
                instanceFollowRedirects = true
            }
            connection.connect()
            onConnectionOpened(connection)

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return "HTTP $responseCode"
            }

            val total = connection.contentLengthLong
            var downloaded = 0L
            var lastReported = 0L

            connection.inputStream.use { input ->
                RandomAccessFile(destFile, "rw").use { output ->
                    output.setLength(0)
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded - lastReported >= 32 * 1024 || lastReported == 0L) {
                            lastReported = downloaded
                            onProgress(downloaded, total)
                        }
                    }
                }
            }
            onProgress(downloaded, total)
            null
        } catch (ex: Exception) {
            "" + ex.message
        } finally {
            connection?.disconnect()
        }
    }
}
