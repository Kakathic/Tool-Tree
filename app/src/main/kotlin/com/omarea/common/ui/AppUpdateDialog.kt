package com.omarea.common.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import com.omarea.common.shared.FileSha256
import com.tool.tree.OpenFileActivity
import com.tool.tree.R
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * Dialog thông báo có phiên bản ứng dụng mới.
 * - Hiển thị nội dung cập nhật bằng WebView tải từ [changelogUrl] (html online).
 * - Thanh tiến trình phía trên 2 nút dùng chung: tiến trình tải trang khi mới mở dialog,
 *   tiến trình tải file apk sau khi bấm Xác nhận.
 * - Khi đang tải apk: ẩn nút Xác nhận, nút Hủy bỏ chiếm trọn hàng nút (LinearLayout tự giãn
 *   theo weight khi view còn lại bị GONE) để hủy tải giữa chừng.
 * - Không thể đóng dialog bằng vuốt / back / chạm ra ngoài (cancelable = false) - CHỈ bấm nút
 *   Hủy bỏ mới thoát được.
 * - Nếu đã có sẵn file apk hợp lệ từ lần tải trước (vd: người dùng thoát trình cài đặt hệ thống
 *   mà không cài) thì nút Xác nhận đổi thành Cài đặt, bấm là cài luôn - không bắt tải lại.
 * - Tải xong: dùng OpenFileActivity (đã có sẵn, xử lý FileProvider + ACTION_VIEW) để mở file apk,
 *   kích hoạt trình cài đặt hệ thống.
 */
class AppUpdateDialog {
    // Trạng thái 1 lượt tải file apk, giữ tham chiếu connection để có thể ngắt giữa chừng khi
    // người dùng bấm Hủy (giống cách làm của DownloadTaskHelper.Session).
    private class DownloadState {
        @Volatile var connection: HttpURLConnection? = null
        @Volatile var cancelled = false
    }

    companion object {
        fun show(
            activity: Activity,
            apkUrl: String,
            changelogUrl: String,
            fileName: String? = null,
            // sha256 lấy từ API (vd: field "digest" của GitHub release asset), dùng để kiểm tra
            // toàn vẹn file apk sau khi tải xong, trước khi mở trình cài đặt. Bỏ qua nếu null.
            expectedSha256: String? = null,
            // Gọi khi người dùng bấm Hủy bỏ LÚC CHƯA TẢI (từ chối hẳn bản cập nhật này) - KHÔNG
            // gọi khi hủy giữa lúc đang tải (chỉ hủy lượt tải, không phải từ chối cập nhật) hay
            // khi dialog bị đóng theo cách khác (vuốt, back, ngoài dialog). Dùng để lưu trạng thái
            // "đã từ chối bản này" ở nơi gọi (vd: AppUpdateConfig), tránh tự hiện lại dialog.
            onCancel: (() -> Unit)? = null
        ): DialogHelper.DialogWrap {
            val view = LayoutInflater.from(activity).inflate(R.layout.dialog_app_update, null)

            val webView = view.findViewById<WebView>(R.id.update_webview)
            val progressBar = view.findViewById<ProgressBar>(R.id.update_progress)
            val btnCancel = view.findViewById<Button>(R.id.btn_cancel)
            val btnConfirm = view.findViewById<Button>(R.id.btn_confirm)

            // cancelable = false: tắt hết các cách đóng dialog "ngầm" (vuốt / back / chạm ra
            // ngoài) - CHỈ bấm nút Hủy bỏ mới thoát được dialog. onDismissListener bên dưới vẫn
            // giữ lại để dọn dẹp (hủy tải + destroy WebView) cho các trường hợp dialog.dismiss()
            // được gọi trực tiếp trong code (nút Hủy, hoặc sau khi tải xong).
            val dialog = DialogHelper.customDialog(activity, view, false)

            // Lượt tải apk đang chạy (null nếu chưa bấm Xác nhận / đã xong / đã hủy)
            var activeDownload: DownloadState? = null

            // --- WebView hiển thị nội dung cập nhật, chỉ để xem, không cấp quyền chạy script hệ thống ---
            val settings = webView.settings
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.blockNetworkImage = false
            settings.loadsImagesAutomatically = true

            webView.webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    if (activeDownload != null) return // đang tải apk, không để tiến trình web ghi đè
                    if (newProgress < 100) {
                        progressBar.isIndeterminate = false
                        progressBar.progress = newProgress
                        progressBar.visibility = View.VISIBLE
                    } else {
                        progressBar.visibility = View.GONE
                    }
                }
            }
            webView.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    if (activeDownload == null) {
                        progressBar.isIndeterminate = true
                        progressBar.visibility = View.VISIBLE
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (activeDownload == null) {
                        progressBar.visibility = View.GONE
                    }
                }
            }
            if (changelogUrl.isNotEmpty()) {
                webView.loadUrl(changelogUrl)
            } else {
                webView.visibility = View.GONE
            }

            val mainHandler = Handler(Looper.getMainLooper())
            val apkFileName = fileName?.takeIf { it.isNotEmpty() }
                ?: apkUrl.substringAfterLast('/').substringBefore('?')
                    .takeIf { it.endsWith(".apk", true) }
                ?: "app_update.apk"
            val destFile = File(activity.cacheDir, apkFileName)

            // Nếu trước đó đã tải xong đúng file này rồi (vd: người dùng thoát trình cài đặt hệ
            // thống mà không cài, rồi mở lại dialog) thì cho cài lại ngay, không bắt tải lại từ
            // đầu - đổi nút Xác nhận thành Cài đặt. Kiểm tra sha256 ở thread nền để tránh treo UI.
            var readyToInstall = false
            if (expectedSha256 != null && destFile.exists() && destFile.length() > 0) {
                Thread {
                    val localHash = FileSha256().getFileSha256(destFile)
                    val matches = localHash != null && expectedSha256.equals(localHash, ignoreCase = true)
                    mainHandler.post {
                        if (matches && activeDownload == null) {
                            readyToInstall = true
                            btnConfirm.text = activity.getString(R.string.app_update_btn_install)
                        }
                    }
                }.apply { isDaemon = true }.start()
            }

            btnConfirm.setOnClickListener {
                if (activeDownload != null) return@setOnClickListener

                if (readyToInstall) {
                    // Đã có sẵn file hợp lệ - cài luôn, không tải lại
                    dialog.dismiss()
                    openApk(activity, destFile)
                    return@setOnClickListener
                }

                // Bắt đầu tải: ẩn nút Xác nhận - nút Hủy tự chiếm trọn hàng nút nhờ layout_weight
                btnConfirm.visibility = View.GONE
                progressBar.isIndeterminate = false
                progressBar.progress = 0
                progressBar.visibility = View.VISIBLE

                val state = DownloadState()
                activeDownload = state

                val thread = Thread {
                    val error = downloadApk(
                        apkUrl,
                        destFile,
                        onConnectionOpened = { state.connection = it },
                        onProgress = { downloaded, total ->
                            if (total > 0) {
                                mainHandler.post {
                                    if (activeDownload === state) {
                                        progressBar.progress = (downloaded * 100 / total).toInt()
                                    }
                                }
                            }
                        }
                    )
                    // Tính sha256 ngay trên thread nền (file apk có thể vài chục MB, không tính
                    // trên main thread để tránh treo UI/ANR) rồi mới báo kết quả cuối cùng lên UI.
                    var hashMismatch = false
                    if (error == null && !state.cancelled && expectedSha256 != null) {
                        val localHash = FileSha256().getFileSha256(destFile)
                        hashMismatch = localHash == null || !expectedSha256.equals(localHash, ignoreCase = true)
                    }
                    mainHandler.post {
                        // Nếu đã bị hủy, phần dọn dẹp UI đã được xử lý ngay tại nút Hủy bỏ
                        if (state.cancelled) {
                            destFile.delete()
                            return@post
                        }
                        if (activeDownload === state) activeDownload = null
                        when {
                            error != null -> {
                                destFile.delete()
                                progressBar.visibility = View.GONE
                                btnConfirm.visibility = View.VISIBLE
                                Toast.makeText(
                                    activity,
                                    activity.getString(R.string.app_update_download_fail) + ": " + error,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            hashMismatch -> {
                                destFile.delete()
                                progressBar.visibility = View.GONE
                                btnConfirm.visibility = View.VISIBLE
                                Toast.makeText(
                                    activity,
                                    activity.getString(R.string.app_update_sha256_mismatch),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            else -> {
                                dialog.dismiss()
                                openApk(activity, destFile)
                            }
                        }
                    }
                }
                thread.isDaemon = true
                thread.start()
            }

            btnCancel.setOnClickListener {
                val state = activeDownload
                if (state != null) {
                    // Đang tải: hủy tải, KHÔNG đóng dialog - quay lại trạng thái ban đầu
                    activeDownload = null
                    state.cancelled = true
                    try {
                        state.connection?.disconnect()
                    } catch (_: Exception) {
                    }
                    progressBar.visibility = View.GONE
                    btnConfirm.visibility = View.VISIBLE
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.app_update_download_cancelled),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    onCancel?.invoke()
                    dialog.dismiss()
                }
            }

            dialog.setOnDismissListener {
                activeDownload?.let { state ->
                    state.cancelled = true
                    try {
                        state.connection?.disconnect()
                    } catch (_: Exception) {
                    }
                }
                activeDownload = null
                try {
                    webView.stopLoading()
                    webView.destroy()
                } catch (_: Exception) {
                }
            }

            return dialog
        }

        // Mở file apk vừa tải bằng OpenFileActivity có sẵn (xử lý FileProvider + ACTION_VIEW +
        // fallback mime application/vnd.android.package-archive) để kích hoạt cài đặt.
        private fun openApk(activity: Activity, file: File) {
            val intent = Intent(activity, OpenFileActivity::class.java)
            intent.putExtra("path", file.absolutePath)
            activity.startActivity(intent)
        }

        // Tải file qua HttpURLConnection, báo tiến trình qua onProgress; trả về null nếu thành
        // công, hoặc thông báo lỗi (khi bị hủy giữa chừng, connection.disconnect() khiến
        // input.read() ném IOException, rơi vào nhánh lỗi - nơi gọi kiểm tra DownloadState.cancelled
        // để phân biệt với lỗi tải thật sự).
        private fun downloadApk(
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
}
