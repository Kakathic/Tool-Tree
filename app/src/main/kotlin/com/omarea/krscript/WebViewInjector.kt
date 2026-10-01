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

    private var loadingDialog: ProgressBarDialog? = null

    @SuppressLint("JavascriptInterface", "SetJavaScriptEnabled")
    fun inject(activity: Activity, credible: Boolean) {
        loadingDialog = ProgressBarDialog(activity, null)

        val webSettings: WebSettings = webView.settings

        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT

        webSettings.blockNetworkImage = false

        webSettings.allowFileAccess = credible
        webSettings.allowUniversalAccessFromFileURLs = credible
        webSettings.allowFileAccessFromFileURLs = credible
        webSettings.allowContentAccess = true
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true

        webView.addJavascriptInterface(
            KrScriptEngine(context),
            "KrScriptCore"
        )

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
