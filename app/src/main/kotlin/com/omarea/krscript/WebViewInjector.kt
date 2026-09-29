package com.omarea.krscript

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import com.omarea.common.shell.KeepShellPublic
import com.omarea.common.shell.ShellExecutor
import com.omarea.common.ui.DialogHelper
import com.omarea.common.ui.ProgressBarDialog
import com.omarea.krscript.downloader.Downloader
import com.omarea.krscript.executor.ExtractAssets
import com.omarea.krscript.executor.ScriptEnvironmen
import com.omarea.krscript.model.NodeInfoBase
import com.omarea.krscript.model.ShellHandlerBase
import com.omarea.krscript.ui.ParamsFileChooserRender
import com.tool.tree.R
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.UUID

class WebViewInjector(
    private val webView: WebView,
    private val fileChooser: ParamsFileChooserRender.FileChooserInterface?
) {
    private val context: Context = webView.context
    private val mainHandler = Handler(Looper.getMainLooper())

    // Dialog loading dùng chung layout dialog_loading.xml, có sẵn nút Hủy (dialog_cancel_button)
    // để người dùng có thể hủy việc tải trang khi đang duyệt web.
    private var loadingDialog: ProgressBarDialog? = null

    @SuppressLint("JavascriptInterface", "SetJavaScriptEnabled")
    fun inject(activity: Activity, credible: Boolean) {
        loadingDialog = ProgressBarDialog(activity, null)

        val webSettings: WebSettings = webView.settings

        // --- TỐI ƯU HÓA WEBVIEW ---
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true // Quan trọng để web chạy nhanh
        webSettings.databaseEnabled = true
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT // Sử dụng cache hệ thống

        // Tạm thời chặn ảnh để ưu tiên tải cấu trúc HTML và Script Shell
        webSettings.blockNetworkImage = false

        webSettings.allowFileAccess = credible
        webSettings.allowUniversalAccessFromFileURLs = credible
        webSettings.allowFileAccessFromFileURLs = credible
        webSettings.allowContentAccess = true
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true
        // ---------------------------

        webView.addJavascriptInterface(
            KrScriptEngine(context),
            "KrScriptCore"
        )

        // Xác nhận tải về bằng hộp thoại confirm có sẵn của app (không cho đóng ngoài nút).
        webView.setDownloadListener { url, _, contentDisposition, mimetype, contentLength ->
            DialogHelper.confirm(
                context = activity,
                title = activity.getString(R.string.kr_download_confirm),
                message = "$url\n\n$mimetype\n${contentLength}Bytes",
                contentView = null,
                onConfirm = DialogHelper.DialogButton(activity.getString(R.string.btn_confirm), Runnable {
                    Downloader(context, null).downloadBySystem(
                        url, contentDisposition, mimetype, UUID.randomUUID().toString(), null
                    )
                }),
                onCancel = DialogHelper.DialogButton(activity.getString(R.string.btn_cancel)),
                cancelable = false
            )
        }
    }

    /**
     * Hiển thị dialog loading (layout dialog_loading.xml) kèm nút Hủy khi đang tải trang web.
     * Bấm nút Hủy sẽ gọi webView.stopLoading() để dừng việc tải trang giữa chừng.
     * Gọi hàm này trong WebViewClient.onPageStarted khi duyệt web.
     */
    fun showLoading(message: String) {
        val dialog = loadingDialog ?: return
        dialog.showDialogWithCancel(message) {
            webView.stopLoading()
            kotlin.Unit
        }
    }

    fun showLoading() {
        showLoading(context.getString(R.string.please_wait))
    }

    /**
     * Ẩn dialog loading. Gọi trong WebViewClient.onPageFinished (hoặc khi tải lỗi)
     * khi duyệt web.
     */
    fun hideLoading() {
        loadingDialog?.hideDialog()
    }

    private inner class KrScriptEngine(private val context: Context) {
        private val virtualRootNode = NodeInfoBase("")

        @JavascriptInterface
        fun rootCheck(): Boolean {
            return KeepShellPublic.checkRoot()
        }

        @JavascriptInterface
        fun executeShell(script: String?): String {
            if (!script.isNullOrEmpty()) {
                return ScriptEnvironmen.executeResultRoot(context, script, virtualRootNode)
            }
            return ""
        }

        @JavascriptInterface
        fun executeShellAsync(script: String?, callbackFunction: String, env: String?): Boolean {
            val params = HashMap<String, String>()
            var process: Process? = null
            try {
                if (!env.isNullOrEmpty()) {
                    val paramsObject = JSONObject(env)
                    val it = paramsObject.keys()
                    while (it.hasNext()) {
                        val key = it.next()
                        params[key] = paramsObject.getString(key)
                    }
                }
                process = ShellExecutor.getSuperUserRuntime()
            } catch (ex: Exception) {
                Toast.makeText(context, ex.message, Toast.LENGTH_SHORT).show()
            }

            return if (process != null) {
                val outputStream = process.outputStream
                val dataOutputStream = DataOutputStream(outputStream)

                setHandler(process, callbackFunction) { }

                ScriptEnvironmen.executeShell(context, dataOutputStream, script, params, null, null)
                true
            } else {
                false
            }
        }

        @JavascriptInterface
        fun extractAssets(assets: String): String {
            return ExtractAssets(context).extractResource(assets) ?: ""
        }

        @JavascriptInterface
        fun fileChooser(callbackFunction: String): Boolean {
            val chooser = fileChooser ?: return false
            return chooser.openFileChooser(object : ParamsFileChooserRender.FileSelectedInterface {
                override fun type(): Int {
                    return ParamsFileChooserRender.FileSelectedInterface.Companion.TYPE_FILE
                }

                override fun suffix(): String? {
                    return null
                }

                override fun mimeType(): String {
                    return "*/*"
                }

                override fun onFileSelected(path: String?) {
                    // Trả dữ liệu về UI thread để tránh crash khi gọi JS
                    mainHandler.post {
                        try {
                            val message = JSONObject()
                            message.put("absPath", if (path.isNullOrEmpty()) null else path)
                            webView.evaluateJavascript("$callbackFunction($message)", null)
                        } catch (ignored: Exception) {
                        }
                    }
                }
            })
        }

        private fun setHandler(process: Process, callbackFunction: String, onExit: Runnable) {
            val inputStream: InputStream = process.inputStream
            val errorStream: InputStream = process.errorStream

            // Thread đọc luồng Standard Output
            val reader = Thread {
                try {
                    BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { br ->
                        var line: String?
                        while (br.readLine().also { line = it } != null) {
                            sendJsLog(callbackFunction, ShellHandlerBase.EVENT_REDE, line + "\n")
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }

            // Thread đọc luồng Error Output
            val readerError = Thread {
                try {
                    BufferedReader(InputStreamReader(errorStream, StandardCharsets.UTF_8)).use { br ->
                        var line: String?
                        while (br.readLine().also { line = it } != null) {
                            sendJsLog(callbackFunction, ShellHandlerBase.EVENT_READ_ERROR, line + "\n")
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }

            val processFinal = process
            val waitExit = Thread {
                var status = -1
                try {
                    status = processFinal.waitFor()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                } finally {
                    sendJsLog(callbackFunction, ShellHandlerBase.EVENT_EXIT, status.toString())

                    if (reader.isAlive) reader.interrupt()
                    if (readerError.isAlive) readerError.interrupt()
                    onExit.run()
                }
            }

            reader.start()
            readerError.start()
            waitExit.start()
        }

        // Hàm hỗ trợ gửi log về WebView thông qua mainHandler để ổn định hiệu suất
        private fun sendJsLog(callback: String, type: Int, messageStr: String) {
            mainHandler.post {
                try {
                    val message = JSONObject()
                    message.put("type", type)
                    message.put("message", messageStr)
                    webView.evaluateJavascript("$callback($message)", null)
                } catch (ignored: Exception) {
                }
            }
        }
    }
}
