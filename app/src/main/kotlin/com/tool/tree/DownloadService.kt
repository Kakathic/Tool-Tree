package com.tool.tree

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.IBinder
import android.app.Service
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import java.util.concurrent.ConcurrentHashMap

class DownloadService : Service() {

    companion object {
        private const val CHANNEL_ID = "download_channel"
        private const val MAX_HISTORY_ROWS = 12
        private val largeIconCache = ConcurrentHashMap<Int, Bitmap>()
        private val messageHistory = ConcurrentHashMap<Int, MutableList<String>>()
    }

    private val manager by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }
    private var currentNotificationId: Int = 1001

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent ?: return START_NOT_STICKY

        currentNotificationId = intent.getIntExtra("notificationId", 1001)

        if (intent.getBooleanExtra("stop", false)) {
            manager.cancel(currentNotificationId)
            largeIconCache.remove(currentNotificationId)
            messageHistory.remove(currentNotificationId)
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent.getBooleanExtra("isError", false)) {
            val title = intent.getStringExtra("title") ?: getString(R.string.channel_name)
            val errorText = intent.getStringExtra("text") ?: getString(R.string.kr_download_error)
            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setLargeIcon(resolveLargeIcon(intent))
                .setContentTitle(title)
                .setContentText(errorText)
                .setOnlyAlertOnce(true)
                .setOngoing(false)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setStyle(buildMessagingStyle(title, errorText, addAsNewMessage = true))
            manager.notify(currentNotificationId, builder.build())
            stopSelf()
            return START_NOT_STICKY
        }

        val title = intent.getStringExtra("title")
        val displayTitle = if (title.isNullOrEmpty()) {
            applicationInfo.loadLabel(packageManager).toString()
        } else {
            title
        }
        val customText = intent.getStringExtra("text")

        if (intent.hasExtra("progress")) {
            val progress = intent.getIntExtra("progress", 0)
            val max = intent.getIntExtra("max", 100)
            val downloadedBytes = intent.getLongExtra("downloadedBytes", -1L)
            val totalBytes = intent.getLongExtra("totalBytes", -1L)
            val speedBps = intent.getDoubleExtra("speedBps", 0.0)
            val builder = createProgressBuilder(
                displayTitle, progress, max, customText,
                downloadedBytes, totalBytes, speedBps
            )
            manager.notify(currentNotificationId, builder.build())
        }

        return START_NOT_STICKY
    }

    private fun buildMessagingStyle(title: String, line: String, addAsNewMessage: Boolean): NotificationCompat.MessagingStyle {
        val rows = messageHistory.getOrPut(currentNotificationId) { java.util.Collections.synchronizedList(ArrayList()) }
        synchronized(rows) {
            if (addAsNewMessage) {
                rows.add(line)
                while (rows.size > MAX_HISTORY_ROWS) rows.removeAt(0)
            } else {
                if (rows.isEmpty()) {
                    rows.add(line)
                } else {
                    rows[rows.size - 1] = line
                }
            }
        }

        val person = Person.Builder().setName(title).build()
        val messagingStyle = NotificationCompat.MessagingStyle(person)
        val snapshot = synchronized(rows) { rows.toList() }
        snapshot.forEach { row ->
            messagingStyle.addMessage(row, System.currentTimeMillis(), person)
        }
        return messagingStyle
    }

    private fun buildProgressText(percent: Int, downloadedBytes: Long, totalBytes: Long, speedBps: Double): String {
        val parts = ArrayList<String>(3)
        if (percent >= 0) parts.add("$percent%")
        if (downloadedBytes >= 0) {
            parts.add(
                if (totalBytes > 0) {
                    "${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)}"
                } else {
                    formatBytes(downloadedBytes)
                }
            )
        }
        if (speedBps > 0) parts.add(formatSpeed(speedBps))
        return if (parts.isEmpty()) getString(R.string.processing) else parts.joinToString(" • ")
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.0f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        return String.format("%.2f GB", mb / 1024.0)
    }

    private fun formatSpeed(speedBps: Double): String {
        val mbps = speedBps / (1024.0 * 1024.0)
        return if (mbps >= 0.1) String.format("%.1f MB/s", mbps) else String.format("%.0f KB/s", speedBps / 1024.0)
    }

    private fun createProgressBuilder(
        title: String,
        progress: Int,
        max: Int,
        customText: String?,
        downloadedBytes: Long = -1L,
        totalBytes: Long = -1L,
        speedBps: Double = 0.0
    ): NotificationCompat.Builder {
        val hasBar = progress >= 0 && max > 0
        val percent = if (hasBar) ((progress * 100f) / max).toInt() else -1

        val displayText = when {
            customText != null -> customText
            hasBar -> buildProgressText(percent, downloadedBytes, totalBytes, speedBps)
            downloadedBytes >= 0 -> buildProgressText(-1, downloadedBytes, totalBytes, speedBps)
            else -> getString(R.string.processing)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(displayText)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (hasBar) {
            builder.setProgress(max, progress, false)
        }

        return builder
    }

    private fun resolveLargeIcon(intent: Intent): Bitmap {
        try {
            val fromIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("largeIcon", Bitmap::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra("largeIcon") as? Bitmap
            }
            if (fromIntent != null) {
                largeIconCache[currentNotificationId] = fromIntent
                return fromIntent
            }
            return largeIconCache[currentNotificationId] ?: drawableToBitmap(packageManager.getApplicationIcon(applicationInfo)).also {
                largeIconCache[currentNotificationId] = it
            }
        } catch (_: Exception) {
            return largeIconCache[currentNotificationId] ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        val targetSize = 256
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return Bitmap.createScaledBitmap(drawable.bitmap, targetSize, targetSize, true)
        }
        val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, targetSize, targetSize)
        drawable.draw(canvas)
        return bitmap
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
