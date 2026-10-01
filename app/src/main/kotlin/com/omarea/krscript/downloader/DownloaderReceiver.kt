package com.omarea.krscript.downloader

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.TextUtils
import android.widget.Toast
import com.omarea.common.shared.FilePathResolver
import com.omarea.common.ui.DialogHelper
import com.tool.tree.R

class DownloaderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent != null) {
            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE == intent.action) {
                try {
                    val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    var type = downloadManager.getMimeTypeForDownloadedFile(downloadId)
                    if (TextUtils.isEmpty(type)) {
                        type = "*/*"
                    }
                    val uri = downloadManager.getUriForDownloadedFile(downloadId)
                    val path = FilePathResolver().getPath(context, uri)
                    if (!path.isNullOrEmpty()) {
                        Downloader(context, null).saveTaskCompleted(downloadId, path)
                        try {
                            DialogHelper.Companion.helpInfo(context, context.getString(R.string.kr_download_completed), path, null)
                        } catch (ex: Exception) {
                            Toast.makeText(context, context.getString(R.string.kr_download_completed) + "\n" + path, Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (ex: Exception) {
                }
            }
        }
    }

    companion object {
        private var downloaderReceiver: DownloaderReceiver? = null

        @JvmStatic
        fun autoRegister(context: Context) {
            if (downloaderReceiver == null) {
                downloaderReceiver = DownloaderReceiver()
                val intentFilter = IntentFilter()
                intentFilter.addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                context.registerReceiver(downloaderReceiver, intentFilter)
            }
        }

        @JvmStatic
        fun autoUnRegister(context: Context) {
            if (downloaderReceiver != null) {
                context.unregisterReceiver(downloaderReceiver)
                downloaderReceiver = null
            }
        }
    }
}
