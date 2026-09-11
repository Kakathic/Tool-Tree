package com.omarea.krscript.ui

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.tool.tree.R
import com.omarea.krscript.model.DownloadNode

class ListItemDownload(context: Context, config: DownloadNode) :
    ListItemClickable(context, R.layout.kr_download_list_item, config) {

    private val widgetView = layout.findViewById<ImageView?>(R.id.kr_widget)
    private val ringView = layout.findViewById<DownloadProgressRing?>(R.id.kr_download_ring)
    private val rowsView = layout.findViewById<TextView?>(R.id.kr_rows)
    private val rowsPhotoView = layout.findViewById<ImageView?>(R.id.kr_rows_photo)

    private val originalDesc = config.desc

    var isBusy: Boolean = false
        private set

    override fun allowLongClick(): Boolean = isBusy || super.allowLongClick()

    private var cancelAction: (() -> Unit)? = null

    init {
        widgetView?.visibility = View.VISIBLE
        widgetView?.setImageDrawable(context.getDrawable(R.drawable.kr_download))
        WidgetTintHelper.applyTint(context, widgetView, iconDrawable)
        RowsRenderHelper.bind(context, rowsView, rowsPhotoView, config.rows, config)
    }

    fun markBusy(onCancel: () -> Unit) {
        isBusy = true
        cancelAction = onCancel
        widgetView?.visibility = View.GONE
        ringView?.visibility = View.VISIBLE
        ringView?.setIndeterminate(true)
    }

    fun updateDownloadProgress(downloaded: Long, total: Long, speedBytesPerSecond: Double = 0.0) {
        if (downloaded > 0) {
            desc = formatProgress(downloaded, total, speedBytesPerSecond)
        }
        if (total > 0) {
            ringView?.setIndeterminate(false)
            ringView?.setProgress(downloaded * 100f / total)
        } else {
            ringView?.setIndeterminate(true)
        }
    }

    fun clearCancelAction() {
        cancelAction = null
    }

    fun cancelIfDownloading(): Boolean {
        val action = cancelAction ?: return false
        cancelAction = null
        action.invoke()
        return true
    }

    fun showStatusLabel(label: String, spin: Boolean = true) {
        desc = label
        if (spin) {
            widgetView?.visibility = View.GONE
            ringView?.visibility = View.VISIBLE
            ringView?.setIndeterminate(true)
        } else {
            ringView?.visibility = View.GONE
            widgetView?.visibility = View.VISIBLE
        }
    }

    fun restoreDesc() {
        desc = originalDesc
    }

    fun finishBusy(showDoneIcon: Boolean = false) {
        isBusy = false
        cancelAction = null
        ringView?.visibility = View.GONE
        widgetView?.setImageDrawable(
            context.getDrawable(if (showDoneIcon) R.drawable.download_done else R.drawable.kr_download)
        )
        widgetView?.visibility = View.VISIBLE
    }

    companion object {
        fun formatProgress(downloaded: Long, total: Long, speedBytesPerSecond: Double = 0.0): String {
            val sizePart = if (total > 0) "${formatBytes(downloaded)} / ${formatBytes(total)}" else formatBytes(downloaded)
            val speedPart = formatSpeed(speedBytesPerSecond)
            return if (total > 0) {
                val percent = (downloaded * 100 / total).toInt()
                "$percent% • $sizePart • $speedPart"
            } else {
                "$sizePart • $speedPart"
            }
        }

        private fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format("%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format("%.1f MB", mb)
            return String.format("%.2f GB", mb / 1024.0)
        }

        private fun formatSpeed(speedBytesPerSecond: Double): String {
            if (speedBytesPerSecond <= 0.0) return "-- KB/s"
            val mbps = speedBytesPerSecond / (1024.0 * 1024.0)
            return if (mbps >= 0.1) String.format("%.1f MB/s", mbps) else String.format("%.0f KB/s", speedBytesPerSecond / 1024.0)
        }
    }
}
