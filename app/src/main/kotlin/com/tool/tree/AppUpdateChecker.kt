package com.tool.tree

import android.app.Activity
import com.omarea.common.shared.FileSha256
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object AppUpdateChecker {
    private const val API_LATEST = "https://api.github.com/repos/Kakathic/Tool-Tree/releases/latest"
    private const val API_BETA = "https://api.github.com/repos/Kakathic/Tool-Tree/releases/tags/beta"
    private const val ASSET_BETA_FLAG = "beta"
    private const val CHANGELOG_URL = "https://raw.githubusercontent.com/Kakathic/Tool-Tree/refs/heads/main/Version.md"
    private const val APK_FILE_NAME = "Tool-Tree.apk"

    fun fetchUpdateInfo(activity: Activity): AppUpdateInfo? {
        return try {
            val apiUrl = if (isBetaBuild(activity)) API_BETA else API_LATEST
            val release = fetchJson(apiUrl) ?: return null

            val assets = release.optJSONArray("assets")
            if (assets == null || assets.length() == 0) return null
            val firstAsset = assets.getJSONObject(0)

            val apkUrl = firstAsset.optString("browser_download_url")
                .takeIf { it.isNotEmpty() } ?: return null
            val remoteSha256 = firstAsset.optString("digest")
                .substringAfter(":", "")
                .takeIf { it.isNotEmpty() } ?: return null
            val changelogUrl = CHANGELOG_URL
            val changelogText = fetchText(changelogUrl)
            val apkSize = firstAsset.optLong("size", -1)

            val currentApk = File(activity.applicationInfo.sourceDir)
            val localSha256 = FileSha256().getFileSha256(currentApk)
            val cachedApk = File(activity.cacheDir, APK_FILE_NAME)

            if (localSha256 != null && localSha256.equals(remoteSha256, ignoreCase = true)) {
                if (cachedApk.exists()) cachedApk.delete()
                return null
            }

            if (cachedApk.exists()) {
                val cachedSha256 = FileSha256().getFileSha256(cachedApk)
                if (cachedSha256 == null || !cachedSha256.equals(remoteSha256, ignoreCase = true)) {
                    cachedApk.delete()
                }
            }

            AppUpdateInfo(apkUrl, changelogUrl, remoteSha256, apkSize, APK_FILE_NAME, changelogText)
        } catch (_: Exception) {
            null
        }
    }

    private fun isBetaBuild(activity: Activity): Boolean {
        return try {
            activity.assets.open(ASSET_BETA_FLAG).use { true }
        } catch (_: Exception) {
            false
        }
    }

    private fun fetchJson(url: String): JSONObject? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            connection.connect()
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body)
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun fetchText(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = true
            }
            connection.connect()
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
