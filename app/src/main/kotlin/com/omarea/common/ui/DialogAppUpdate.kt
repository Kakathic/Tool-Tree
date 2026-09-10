package com.omarea.common.ui

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.text.HtmlCompat
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
 * - Hiển thị nội dung cập nhật bằng TEXT thuần, tải từ [changelogUrl] (vd: Version.md raw trên
 *   GitHub) - không dùng WebView (bỏ chi phí khởi tạo engine WebView lần đầu, tránh chậm).
 * - Thanh tiến trình phía trên 2 nút dùng chung: tiến trình tải nội dung text khi mới mở dialog,
 *   tiến trình tải file apk sau khi bấm Xác nhận.
 * - Khi đang tải apk: ẩn nút Xác nhận, nút Hủy bỏ chiếm trọn hàng nút.
 * - Cho phép đóng dialog bằng vuốt lùi / back / chạm ra ngoài (giống các dialog full-screen
 *   khác). Nếu đang tải apk mà dialog bị đóng theo cách này thì lượt tải KHÔNG bị hủy - vẫn
 *   tiếp tục chạy ngầm cho đến khi xong rồi tự mở trình cài đặt; chỉ bấm nút Hủy bỏ mới thật sự
 *   ngắt lượt tải đang chạy.
 * - Nếu file apk đã tồn tại sẵn trong cache (từ lần tải trước) thì cho cài lại ngay, không bắt
 *   tải lại từ đầu - không kiểm tra sha256 (chỉ cần tải về không lỗi là cho phép cài).
 * - Tải xong: dùng OpenFileActivity mở file apk, kích hoạt trình cài đặt hệ thống.
 */
class DialogAppUpdate(
    darkMode: Boolean,
    private val apkUrl: String,
    private val changelogUrl: String,
    // Tên file dùng khi cache ở cacheDir - LUÔN lấy từ AppUpdateInfo.apkFileName (tính sẵn, 1
    // chỗ duy nhất trong AppUpdateChecker) để khớp đúng với file mà AppUpdateChecker giữ lại /
    // dọn dẹp ở mỗi lần khởi động app - không tự suy tên riêng ở đây nữa.
    private val fileName: String,
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
        isCancelable = true
    }

    // true khi dialog đang tự đóng để tiến hành cài đặt (đã có/vừa tải xong apk hợp lệ) - dùng
    // để onDismiss() không hiểu nhầm thành người dùng hủy cập nhật.
    private var closingToInstall = false

    private lateinit var contentText: TextView
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

        contentText = view.findViewById(R.id.update_content)
        contentText.movementMethod = LinkMovementMethod.getInstance()
        progressBar = view.findViewById(R.id.update_progress)
        btnCancel = view.findViewById(R.id.btn_cancel)
        btnConfirm = view.findViewById(R.id.btn_confirm)
        titleText = view.findViewById(R.id.update_title)

        // Gộp dung lượng vào ngay dòng tiêu đề - "Tên app | 8 MB" - chỉ khi biết kích thước.
        if (apkSize != null && apkSize > 0) {
            titleText.text = activity.getString(R.string.app_name) + " | " + formatFileSize(apkSize)
        }

        destFile = File(activity.cacheDir, fileName)

        // --- Tải nội dung cập nhật (text thuần) chạy nền, không chặn nút bấm ---
        if (changelogUrl.isNotEmpty()) {
            progressBar.isIndeterminate = true
            progressBar.visibility = View.VISIBLE
            contentText.text = activity.getString(R.string.onloading)

            val thread = Thread {
                val (text, _) = fetchTextContent(changelogUrl)
                mainHandler.post {
                    if (activeDownload == null) {
                        progressBar.visibility = View.INVISIBLE
                    }
                    contentText.text = if (text != null) {
                        HtmlCompat.fromHtml(markdownToHtml(text), HtmlCompat.FROM_HTML_MODE_LEGACY)
                    } else {
                        activity.getString(R.string.app_update_changelog_fail)
                    }
                }
            }
            thread.isDaemon = true
            thread.start()
        }

        // Nếu trước đó đã tải xong file này rồi thì cho cài lại ngay, không bắt tải lại từ đầu -
        // đổi nút Xác nhận thành Cài đặt. Không kiểm tra sha256: file tải về không lỗi là coi
        // như hợp lệ (sha256 lấy tại thời điểm mở app có thể đã cũ hơn bản apk thật trên server).
        if (destFile.exists() && destFile.length() > 0) {
            readyToInstall = true
            btnConfirm.text = activity.getString(R.string.app_update_btn_install)
        }

        btnConfirm.setOnClickListener {
            if (activeDownload != null) return@setOnClickListener

            if (readyToInstall) {
                // Đã có sẵn file hợp lệ - cài luôn, không tải lại
                closingToInstall = true
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
                mainHandler.post {
                    // Nếu đã bị hủy, phần dọn dẹp UI đã được xử lý ngay tại nút Hủy bỏ
                    if (state.cancelled) {
                        destFile.delete()
                        return@post
                    }
                    if (activeDownload === state) activeDownload = null

                    // Dialog có thể đã bị vuốt/back đóng từ trước trong lúc lượt tải này vẫn
                    // chạy ngầm - khi đó fragment không còn gắn (isAdded = false) nên bỏ qua
                    // phần cập nhật UI, chỉ xử lý kết quả tải (xóa file lỗi / mở trình cài đặt).
                    if (error != null) {
                        destFile.delete()
                        if (isAdded) {
                            progressBar.visibility = View.INVISIBLE
                            btnConfirm.visibility = View.VISIBLE
                        }
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.app_update_download_fail) + ": " + error,
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        // Tải về không lỗi -> cho phép cài luôn, không kiểm tra sha256
                        closingToInstall = true
                        if (isAdded) dismiss()
                        openApk(activity, destFile)
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
                // Không đang tải - onDismiss() bên dưới sẽ tự gọi onCancel khi dialog đóng
                dismiss()
            }
        }
    }

    // Gọi khi dialog đóng bằng BẤT KỲ cách nào (nút Hủy bỏ lúc không tải, vuốt lùi, back,
    // chạm ra ngoài, hoặc tự dismiss() để cài đặt).
    // - Nếu đang tải apk: KHÔNG hủy lượt tải - để nó tiếp tục chạy ngầm, tự mở trình cài đặt khi
    //   xong (xem mainHandler.post ở trên).
    // - onCancel chỉ được gọi khi đóng lúc không có lượt tải nào đang chạy VÀ không phải do
    //   chuẩn bị cài đặt (closingToInstall) - tức người dùng thực sự từ chối bản cập nhật.
    override fun onDismiss(dialog: DialogInterface) {
        if (!closingToInstall && activeDownload == null) {
            onCancel?.invoke()
        }
        super.onDismiss(dialog)
    }

    // Định dạng dung lượng file (byte) thành chuỗi dễ đọc - "12.3 MB" (dùng MB cho mọi kích
    // thước apk thực tế, không cần xử lý GB).
    private fun formatFileSize(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return String.format("%.1f MB", mb)
    }

    // Chuyển 1 tập con Markdown đơn giản (đủ dùng cho Version.md) sang HTML để hiển thị bằng
    // HtmlCompat.fromHtml(): "# tiêu đề" (kèm [text](url) bên trong) -> in đậm cỡ lớn, có thể
    // bấm link; "**...**" -> in đậm; dòng bắt đầu "+ " -> gạch đầu dòng dạng chấm tròn "•".
    // Escape HTML trước khi áp quy tắc markdown để nội dung tải về không phá layout.
    private fun markdownToHtml(markdown: String): String {
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

    // Tải nội dung text thuần (vd: Version.md raw) qua HttpURLConnection - trả về
    // Pair(nộiDung, null) nếu thành công, hoặc Pair(null, lỗi) nếu thất bại.
    private fun fetchTextContent(url: String): Pair<String?, String?> {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                instanceFollowRedirects = true
            }
            connection.connect()
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return Pair(null, "HTTP $responseCode")
            }
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            Pair(text, null)
        } catch (ex: Exception) {
            Pair(null, "" + ex.message)
        } finally {
            connection?.disconnect()
        }
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
