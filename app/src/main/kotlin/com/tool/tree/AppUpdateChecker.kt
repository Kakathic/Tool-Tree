package com.tool.tree

import android.app.Activity
import android.content.Context
import android.util.Log
import com.omarea.common.shared.FileSha256
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object AppUpdateChecker {
    private const val API_LATEST = "https://api.github.com/repos/Kakathic/Tool-Tree/releases/latest"
    private const val API_BETA = "https://api.github.com/repos/Kakathic/Tool-Tree/releases/tags/beta"
    private const val ASSET_BETA_FLAG = "beta"
    private const val ASSET_MODULE = "module"
    private const val OFFLINE_BETA_NAME = "Tool-Tree-offline-beta.apk"
    private const val OFFLINE_PREFIX = "Tool-Tree-offline."
    private const val OFFLINE_SUFFIX = ".apk"
    private const val CHANGELOG_URL = "https://raw.githubusercontent.com/Kakathic/Tool-Tree/refs/heads/main/Version.md"
    const val APK_FILE_NAME = "Tool-Tree.apk"
    private const val TAG = "AppUpdateChecker"
    private const val CHANGELOG_CACHE = "changelog_cache.md"
    private const val RESULT_TTL_MS = 3 * 60 * 1000L

    @Volatile private var lastResult: AppUpdateCheckResult? = null
    @Volatile private var lastResultTime = 0L

    fun cachedChangelog(context: Context): String? {
        return try {
            val file = File(context.filesDir, CHANGELOG_CACHE)
            if (file.isFile) file.readText(Charsets.UTF_8).takeIf { it.isNotBlank() } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun saveChangelog(context: Context, text: String) {
        try {
            File(context.filesDir, CHANGELOG_CACHE).writeText(text, Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    fun checkCached(activity: Activity): AppUpdateCheckResult {
        val cached = lastResult
        if (cached != null && System.currentTimeMillis() - lastResultTime < RESULT_TTL_MS) return cached
        return check(activity)
    }

    fun fetchUpdateInfo(activity: Activity): AppUpdateInfo? {
        return (check(activity) as? AppUpdateCheckResult.Available)?.info
    }

    fun check(activity: Activity): AppUpdateCheckResult {
        val result = doCheck(activity)
        if (result !is AppUpdateCheckResult.Failed) {
            lastResult = result
            lastResultTime = System.currentTimeMillis()
        }
        return result
    }

    private fun doCheck(activity: Activity): AppUpdateCheckResult {
        return try {
            val changelogText = fetchText(CHANGELOG_URL)?.also { saveChangelog(activity, it) }
            val beta = isBetaBuild(activity)
            val apiUrl = if (beta) API_BETA else API_LATEST
            val release = fetchJson(apiUrl) ?: return AppUpdateCheckResult.Failed

            val assets = release.optJSONArray("assets")
            val firstAsset = if (hasOfflineModule(activity)) {
                findOfflineAsset(assets, beta) ?: return AppUpdateCheckResult.UpToDate(changelogText)
            } else if (assets != null && assets.length() > 0) {
                assets.getJSONObject(0)
            } else null
            val apkUrl = firstAsset?.optString("browser_download_url")?.takeIf { it.isNotEmpty() }
            val remoteSha256 = firstAsset?.optString("digest")?.substringAfter(":", "")?.takeIf { it.isNotEmpty() }
            if (firstAsset == null || apkUrl == null || remoteSha256 == null) {
                return AppUpdateCheckResult.UpToDate(changelogText)
            }
            val apkSize = firstAsset.optLong("size", -1)

            val currentApk = File(activity.applicationInfo.sourceDir)
            val localSha256 = FileSha256().getFileSha256(currentApk)
            val cachedApk = File(activity.cacheDir, APK_FILE_NAME)

            if (localSha256 != null && localSha256.equals(remoteSha256, ignoreCase = true)) {
                if (cachedApk.exists()) cachedApk.delete()
                return AppUpdateCheckResult.UpToDate(changelogText)
            }

            if (cachedApk.exists()) {
                val cachedSha256 = FileSha256().getFileSha256(cachedApk)
                if (cachedSha256 == null || !cachedSha256.equals(remoteSha256, ignoreCase = true)) {
                    cachedApk.delete()
                }
            }

            AppUpdateCheckResult.Available(
                AppUpdateInfo(apkUrl, CHANGELOG_URL, remoteSha256, apkSize, APK_FILE_NAME, changelogText)
            )
        } catch (ex: Exception) {
            Log.w(TAG, "check failed: $ex")
            AppUpdateCheckResult.Failed
        }
    }

    private fun hasOfflineModule(activity: Activity): Boolean {
        return try {
            activity.assets.open(ASSET_MODULE).use { true }
        } catch (_: Exception) {
            try {
                (activity.assets.list(ASSET_MODULE)?.isNotEmpty()) == true
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun findOfflineAsset(assets: org.json.JSONArray?, beta: Boolean): JSONObject? {
        if (assets == null) return null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name")
            val match = if (beta) {
                name.equals(OFFLINE_BETA_NAME, ignoreCase = true)
            } else {
                name.startsWith(OFFLINE_PREFIX, ignoreCase = true) && name.endsWith(OFFLINE_SUFFIX, ignoreCase = true) && !name.contains("beta", ignoreCase = true)
            }
            if (match) return asset
        }
        return null
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
                setRequestProperty("User-Agent", "Tool-Tree")
            }
            connection.connect()
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "GitHub API HTTP ${connection.responseCode} for $url")
                return null
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body)
        } catch (ex: Exception) {
            Log.w(TAG, "GitHub API error for $url: $ex")
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
