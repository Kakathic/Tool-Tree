package com.tool.tree

import android.app.Activity
import com.omarea.common.shared.FileSha256
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Kiểm tra bản cập nhật mới từ GitHub Releases của repo Kakathic/Tool-Tree.
 *
 * - Mặc định lấy release mới nhất: /releases/latest
 * - Nếu bản build hiện tại đóng gói kèm tệp assets/beta (kiểm tra qua AssetManager) thì lấy
 *   release gắn tag "beta" thay vào: /releases/tags/beta
 * - Lấy file apk đầu tiên trong "assets": .assets[0].browser_download_url
 * - Lấy sha256 do GitHub tính sẵn: .assets[0].digest (dạng "sha256:<hex>")
 * - So sánh với sha256 của file apk đang cài trên máy - khác nhau thì coi là có bản mới.
 * - Link xem log/nội dung cập nhật (đưa vào AppUpdateInfo.changelogUrl) luôn là trang cố định
 *   CHANGELOG_URL bên dưới, không lấy từ html_url của GitHub. Nội dung trang này được tải sẵn
 *   luôn ở đây (AppUpdateInfo.changelogText) - DialogAppUpdate chỉ hiển thị lại, không tự tải.
 *
 * Lưu ý: trường "digest" chỉ có khi GitHub đã tính xong checksum cho asset (có thể rỗng/null với
 * asset vừa upload) - trường hợp đó bỏ qua, coi như không có cập nhật.
 *
 * Class này CHỈ chịu trách nhiệm kiểm tra + trả dữ liệu, KHÔNG tự hiện dialog - nơi gọi
 * (SplashActivity, chạy trong coroutine IO lúc đang tải dữ liệu khác) chuyển kết quả cho
 * MainActivity để hiện AppUpdateDialog (xem AppUpdateInfo).
 */
object AppUpdateChecker {
    private const val API_LATEST = "https://api.github.com/repos/Kakathic/Tool-Tree/releases/latest"
    private const val API_BETA = "https://api.github.com/repos/Kakathic/Tool-Tree/releases/tags/beta"
    private const val ASSET_BETA_FLAG = "beta"
    // Trang xem log/nội dung cập nhật hiện trong WebView của AppUpdateDialog - dùng trang này
    // thay vì html_url (link trang release) của GitHub.
    private const val CHANGELOG_URL = "https://raw.githubusercontent.com/Kakathic/Tool-Tree/refs/heads/main/Version.md"
    // Tên file apk cache CỐ ĐỊNH (không suy theo URL nữa) - dùng chung cho mọi bản, nơi DUY NHẤT
    // khai báo giá trị này (AppUpdateInfo.apkFileName lấy từ đây, DialogAppUpdate dùng lại).
    private const val APK_FILE_NAME = "Tool-Tree.apk"

    /**
     * Hàm chặn (blocking) - PHẢI gọi từ thread nền / coroutine IO, không gọi trên main thread.
     * Trả về null nếu: đã là bản mới nhất, hoặc có lỗi mạng/parse (bỏ qua âm thầm, không làm
     * phiền người dùng lúc khởi động app).
     */
    fun fetchUpdateInfo(activity: Activity): AppUpdateInfo? {
        return try {
            val apiUrl = if (isBetaBuild(activity)) API_BETA else API_LATEST
            val release = fetchJson(apiUrl) ?: return null

            val assets = release.optJSONArray("assets")
            if (assets == null || assets.length() == 0) return null
            val firstAsset = assets.getJSONObject(0)

            val apkUrl = firstAsset.optString("browser_download_url")
                .takeIf { it.isNotEmpty() } ?: return null
            // digest dạng "sha256:<hex>" - tách lấy phần hex phía sau dấu ":"
            val remoteSha256 = firstAsset.optString("digest")
                .substringAfter(":", "")
                .takeIf { it.isNotEmpty() } ?: return null
            val changelogUrl = CHANGELOG_URL
            val changelogText = fetchText(changelogUrl)
            val apkSize = firstAsset.optLong("size", -1)

            val currentApk = File(activity.applicationInfo.sourceDir)
            val localSha256 = FileSha256().getFileSha256(currentApk)
            val cachedApk = File(activity.cacheDir, APK_FILE_NAME)

            // App đang cài đã khớp sha256 với bản mới nhất trên GitHub -> đã cập nhật xong, xoá
            // file cache (nếu còn) vì không cần nữa, không hiện dialog cập nhật
            if (localSha256 != null && localSha256.equals(remoteSha256, ignoreCase = true)) {
                if (cachedApk.exists()) cachedApk.delete()
                return null
            }

            // Còn cần cập nhật - nếu đã có file tải sẵn từ lần trước (tải về nhưng chưa cài), so
            // sha256 với bản online: khớp thì GIỮ LẠI (DialogAppUpdate sẽ cho cài luôn, không tải
            // lại) - khác thì XOÁ (bản online đã đổi mới hơn kể từ lần tải trước)
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

    // Bản build hiện tại có phải bản beta không - dựa vào việc apk có đóng gói kèm tệp assets/beta.
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
                // Timeout ngắn hơn so với tải apk (chỉ là 1 lệnh gọi API nhỏ, chạy lúc khởi
                // động app) - tránh làm chậm splash quá lâu khi mạng yếu/GitHub không phản hồi.
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

    // Tải nội dung changelog (markdown thô) - dùng chung timeout ngắn như fetchJson, chạy
    // ngay trong lúc kiểm tra cập nhật ở SplashActivity thay vì đợi lúc mở DialogAppUpdate.
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
