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
import com.omarea.common.shell.KeepShellPublic
import com.tool.tree.OpenFileActivity
import com.tool.tree.R
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

class DialogAppUpdate(
    darkMode: Boolean,
    private val apkUrl: String,
    private val changelogText: String?,
    private val fileName: String,
    private val apkSize: Long? = null,
    private val onCancel: (() -> Unit)? = null
) : DialogFullScreen(R.layout.dialog_app_update, darkMode) {

    private class DownloadState {
        @Volatile var connection: HttpURLConnection? = null
        @Volatile var cancelled = false
    }

    init {
        isCancelable = true
    }

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

        if (apkSize != null && apkSize > 0) {
            titleText.text = activity.getString(R.string.app_name) + " | " + formatFileSize(apkSize)
        }

        destFile = File(activity.cacheDir, fileName)

        // Changelog đã được AppUpdateChecker tải sẵn từ lúc SplashActivity kiểm tra cập nhật -
        // ở đây chỉ hiển thị lại, không tự tải mạng nữa khi mở dialog.
        contentText.text = if (changelogText != null) {
            HtmlCompat.fromHtml(markdownToHtml(changelogText), HtmlCompat.FROM_HTML_MODE_LEGACY)
        } else {
            activity.getString(R.string.app_update_changelog_fail)
        }

        if (destFile.exists() && destFile.length() > 0) {
            readyToInstall = true
            btnConfirm.text = activity.getString(R.string.app_update_btn_install)
        }

        btnConfirm.setOnClickListener {
            if (activeDownload != null) return@setOnClickListener

            if (readyToInstall) {
                openApk(activity, destFile)
                return@setOnClickListener
            }

            isCancelable = false
            // isCancelable chỉ chặn back/chạm ngoài; vuốt lùi đã bind sẵn từ lúc mở dialog nên
            // phải tắt riêng bằng setSwipeBackRuntimeEnabled để không vuốt đóng được khi đang tải.
            setSwipeBackRuntimeEnabled(false)

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
                    if (state.cancelled) {
                        destFile.delete()
                        return@post
                    }
                    if (activeDownload === state) activeDownload = null

                    if (error != null) {
                        destFile.delete()
                        if (isAdded) {
                            isCancelable = true
                            setSwipeBackRuntimeEnabled(true)
                            progressBar.visibility = View.INVISIBLE
                            btnConfirm.visibility = View.VISIBLE
                        }
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.app_update_download_fail) + ": " + error,
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
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
                isCancelable = true
                setSwipeBackRuntimeEnabled(true)
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
                dismiss()
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (!closingToInstall && activeDownload == null) {
            onCancel?.invoke()
        }
        super.onDismiss(dialog)
    }

    private fun formatFileSize(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return String.format("%.1f MB", mb)
    }

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

    // Có quyền root: cài thẳng qua "pm install -r" ở nền, không cần mở OpenFileActivity (màn
    // hình cài đặt hệ thống). Không root (hoặc lệnh root thất bại) mới fallback về cách cũ.
    private fun openApk(activity: android.app.Activity, file: File) {
        Thread {
            val installed = installApkWithRoot(file)
            mainHandler.post {
                if (installed) {
                    // Cài bằng root xong app sẽ tự khởi động lại/thoát do đang tự cập nhật
                    // chính nó, nên không cần (và không kịp) đóng dialog hay hiện toast báo
                    // thành công - lúc này app coi như đã thoát rồi.
                } else {
                    closingToInstall = true
                    if (isAdded) dismiss()
                    openApkViaSystemInstaller(activity, file)
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun installApkWithRoot(file: File): Boolean {
        if (!KeepShellPublic.checkRoot()) return false
        val escapedPath = file.absolutePath.replace("'", "'\\''")
        val result = KeepShellPublic.doCmdSync("pm install -r '$escapedPath'")
        return result.contains("Success", ignoreCase = true)
    }

    private fun openApkViaSystemInstaller(activity: android.app.Activity, file: File) {
        val intent = Intent(activity, OpenFileActivity::class.java)
        intent.putExtra("path", file.absolutePath)
        activity.startActivity(intent)
    }

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
