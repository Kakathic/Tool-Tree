package com.omarea.common.ui

import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.omarea.common.shared.FileSha256
import com.tool.tree.OpenFileActivity
import com.tool.tree.R
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * Dialog thông báo có phiên bản ứng dụng mới - dạng full-screen kế thừa DialogFullScreen
 * (thay cho AppUpdateDialog.kt cũ dùng DialogHelper.customDialog thủ công), đồng bộ style +
 * vuốt-để-đóng/edge-to-edge với các dialog full-screen khác trong app.
 *
 * - Hiển thị nội dung cập nhật bằng WebView tải từ [changelogUrl] (html online).
 * - Thanh tiến trình phía trên 2 nút dùng chung: tiến trình tải trang khi mới mở dialog,
 *   tiến trình tải file apk sau khi bấm Xác nhận.
 * - Khi đang tải apk: ẩn nút Xác nhận, nút Hủy bỏ chiếm trọn hàng nút.
 * - Không thể đóng dialog bằng vuốt / back / chạm ra ngoài (isCancelable = false) - CHỈ bấm nút
 *   Hủy bỏ mới thoát được.
 * - Nếu đã có sẵn file apk hợp lệ từ lần tải trước thì nút Xác nhận đổi thành Cài đặt.
 * - Tải xong: dùng OpenFileActivity mở file apk, kích hoạt trình cài đặt hệ thống.
 */
class DialogAppUpdate(
    darkMode: Boolean,
    private val apkUrl: String,
    private val changelogUrl: String,
    private val fileName: String? = null,
    // sha256 lấy từ API, dùng để kiểm tra toàn vẹn file apk sau khi tải xong. Bỏ qua nếu null.
    private val expectedSha256: String? = null,
    // Dung lượng file apk (byte) lấy từ API - null/<=0 nếu không xác định được, khi đó ẩn dòng
    // hiển thị dung lượng.
    private val apkSize: Long? = null,
    // Gọi khi người dùng bấm Hủy bỏ LÚC CHƯA TẢI (từ chối hẳn bản cập nhật này).
    private val onCancel: (() -> Unit)? = null
) : DialogFullScreen(R.layout.dialog_app_update, darkMode) {

    // Trạng thái 1 lượt tải file apk, giữ tham chiếu connection để có thể ngắt giữa chừng.
    private class DownloadState {
        @Volatile var connection: HttpURLConnection? = null
        @Volatile var cancelled = false
    }

    init {
        isCancelable = false
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnCancel: Button
    private lateinit var btnConfirm: Button
    private lateinit var titleText: TextView

    private var activeDownload: DownloadState? = null
    private var readyToInstall = false
    private lateinit var destFile: File
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity()

        webView = view.findViewById(R.id.update_webview)
        progressBar = view.findViewById(R.id.update_progress)
        btnCancel = view.findViewById(R.id.btn_cancel)
        btnConfirm = view.findViewById(R.id.btn_confirm)
        titleText = view.findViewById(R.id.update_title)

        // Gộp dung lượng vào ngay dòng tiêu đề - "Cập nhật mới | 8 MB" - chỉ khi biết kích thước.
        if (apkSize != null && apkSize > 0) {
            titleText.text = activity.getString(R.string.app_update_title) + " | " + formatFileSize(apkSize)
        }

        val apkFileName = fileName?.takeIf { it.isNotEmpty() }
            ?: apkUrl.substringAfterLast('/').substringBefore('?')
                .takeIf { it.endsWith(".apk", true) }
            ?: "app_update.apk"
        destFile = File(activity.cacheDir, apkFileName)

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
                    progressBar.visibility = View.INVISIBLE
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
                    progressBar.visibility = View.INVISIBLE
                }
            }
        }
        if (changelogUrl.isNotEmpty()) {
            webView.loadUrl(changelogUrl)
        } else {
            webView.visibility = View.GONE
        }

        // Nếu trước đó đã tải xong đúng file này rồi thì cho cài lại ngay, không bắt tải lại từ
        // đầu - đổi nút Xác nhận thành Cài đặt. Kiểm tra sha256 ở thread nền để tránh treo UI.
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
                dismiss()
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
                // Tính sha256 ngay trên thread nền, rồi mới báo kết quả cuối cùng lên UI.
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
                            progressBar.visibility = View.INVISIBLE
                            btnConfirm.visibility = View.VISIBLE
                            Toast.makeText(
                                activity,
                                activity.getString(R.string.app_update_download_fail) + ": " + error,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        hashMismatch -> {
                            destFile.delete()
                            progressBar.visibility = View.INVISIBLE
                            btnConfirm.visibility = View.VISIBLE
                            Toast.makeText(
                                activity,
                                activity.getString(R.string.app_update_sha256_mismatch),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        else -> {
                            dismiss()
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
                progressBar.visibility = View.INVISIBLE
                btnConfirm.visibility = View.VISIBLE
                Toast.makeText(
                    activity,
                    activity.getString(R.string.app_update_download_cancelled),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                onCancel?.invoke()
                dismiss()
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        activeDownload?.let { state ->
            state.cancelled = true
            try {
                state.connection?.disconnect()
            } catch (_: Exception) {
            }
        }
        activeDownload = null
        try {
            if (::webView.isInitialized) {
                webView.stopLoading()
                webView.destroy()
            }
        } catch (_: Exception) {
        }
        super.onDismiss(dialog)
    }

    // Định dạng dung lượng file (byte) thành chuỗi dễ đọc - "12.3 MB" (dùng MB cho mọi kích
    // thước apk thực tế, không cần xử lý GB).
    private fun formatFileSize(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return String.format("%.1f MB", mb)
    }

    // Mở file apk vừa tải bằng OpenFileActivity có sẵn để kích hoạt cài đặt.
    private fun openApk(activity: android.app.Activity, file: File) {
        val intent = Intent(activity, OpenFileActivity::class.java)
        intent.putExtra("path", file.absolutePath)
        activity.startActivity(intent)
    }

    // Tải file qua HttpURLConnection, báo tiến trình qua onProgress; trả về null nếu thành công,
    // hoặc thông báo lỗi.
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
