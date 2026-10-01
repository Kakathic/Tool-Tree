package com.omarea.krscript.downloader

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Context.DOWNLOAD_SERVICE
import android.content.Intent
import android.os.Environment
import android.webkit.URLUtil
import android.widget.Toast
import com.omarea.common.shared.FileWrite
import com.omarea.common.ui.DialogHelper
import com.tool.tree.R
import org.json.JSONObject
import java.io.File
import java.nio.charset.Charset
import java.util.Locale.getDefault
import androidx.core.net.toUri
import androidx.core.content.edit

class Downloader(private var context: Context, private var activity: Activity? = null) {
    companion object {
        private const val HISTORY_CONFIG = "kr_downloader"
    }

    fun downloadByBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.data = url.toUri()
        activity?.startActivity(intent)
    }

    fun downloadBySystem(
        url: String,
        contentDisposition: String?,
        mimeType: String?,
        taskAliasId: String?,
        fileName: String? = null): Long? {
        try {
            val request = DownloadManager.Request(url.toUri())
            request.allowScanningByMediaScanner()
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setAllowedOverMetered(true)
            request.setVisibleInDownloadsUi(true)
            request.setAllowedOverRoaming(true)
            val outName = if(fileName.isNullOrEmpty()) URLUtil.guessFileName(url, contentDisposition, mimeType) else fileName
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, outName)
            val downloadManager = context.getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)
            if (taskAliasId?.isNotEmpty() ?: false) {
                addTaskHisotry(downloadId, taskAliasId, url)
            }
            Toast.makeText(context, context.getString(R.string.kr_download_create_success), Toast.LENGTH_SHORT).show()
            DownloaderReceiver.autoRegister(context.applicationContext)
            return downloadId
        } catch (ex: Exception) {
            DialogHelper.helpInfo(context, context.getString(R.string.kr_download_create_fail), "" + ex.message)
            return null
        }
    }

    private fun addTaskHisotry(downloadId: Long, taskAliasId: String, url: String) {
        val historyList = context.getSharedPreferences(HISTORY_CONFIG, Context.MODE_PRIVATE)

        val history = JSONObject()
        history.put("url", url)
        history.put("taskAliasId", taskAliasId)

        historyList.edit { putString(downloadId.toString(), history.toString(2)) }
    }

    fun saveTaskStatus(taskAliasId: String?, ratio: Int) {
        FileWrite.writePrivateFile(ratio.toString().toByteArray(Charset.defaultCharset()),
            "downloader/status/$taskAliasId", context)
    }

    fun saveTaskCompleted(downloadId: Long, absPath: String) {
        val historyList = context.getSharedPreferences(HISTORY_CONFIG, Context.MODE_PRIVATE)
        val historyStr = historyList.getString(downloadId.toString(), null)
        var taskAliasId: String? = ""
        if (historyStr != null) {
            val hisotry = JSONObject(historyStr)
            hisotry.put("absPath", absPath)
            historyList.edit { putString(downloadId.toString(), hisotry.toString(2)) }
            taskAliasId = hisotry.getString("taskAliasId")
        }
        try {
            val file = File(absPath)
            if (file.exists() && file.canRead()) {
                val md5 = (FileMD5().getFileMD5(file) ?: "").lowercase(getDefault())
                FileWrite.writePrivateFile(absPath.toByteArray(Charset.defaultCharset()),
                    "downloader/path/$md5", context)
                taskAliasId?.run {
                    FileWrite.writePrivateFile(absPath.toByteArray(Charset.defaultCharset()),
                        "downloader/result/$taskAliasId", context)
                }
            }
        } catch (_: java.lang.Exception) {

        }
    }
}
