package com.omarea.krscript.downloader

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.tool.tree.DownloadService
import com.tool.tree.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object WebDownloadHelper {
    private const val CHANNEL_ID = "download_channel"
    private const val PROGRESS_BASE_ID = 3000
    private const val DONE_BASE_ID = 5000
    private const val NOTIFY_MIN_INTERVAL_MS = 400L
    private const val MAX_RETRY = 3

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeUrls = ConcurrentHashMap.newKeySet<String>()
    private val counter = AtomicInteger(0)

    private class Saved(val uri: Uri, val name: String, val openMime: String)

    fun start(
        context: Context,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long,
        cookie: String?,
        referer: String?
    ) {
        if (!activeUrls.add(url)) return
        val ctx = context.applicationContext
        val fileName = sanitizeName(URLUtil.guessFileName(url, contentDisposition, mimeType))
        val seq = counter.incrementAndGet()
        val progressId = PROGRESS_BASE_ID + seq % 1000
        val doneId = DONE_BASE_ID + seq % 1000

        toast(ctx, ctx.getString(R.string.kr_download_create_success))

        appScope.launch {
            val tmp = File(ctx.cacheDir, "kr_web_download_" + UUID.randomUUID().toString().replace("-", "") + ".tmp")
            try {
                sendProgress(ctx, progressId, fileName, 0, -1L, -1L, 0.0, ctx.getString(R.string.kr_download_create_success))

                var downloaded = 0L
                var lastTs = 0L
                var lastBytes = 0L
                var speed = 0.0
                var lastNotifyTs = 0L

                var attempt = 0
                var error: String?
                while (true) {
                    error = downloadToFile(url, tmp, userAgent, cookie, referer, contentLength) { done, total ->
                        val now = System.currentTimeMillis()
                        if (lastTs > 0L && now > lastTs) {
                            val instant = (done - lastBytes) * 1000.0 / (now - lastTs)
                            speed = if (speed <= 0.0) instant else speed * 0.7 + instant * 0.3
                        }
                        lastTs = now
                        lastBytes = done
                        downloaded = done
                        if (now - lastNotifyTs >= NOTIFY_MIN_INTERVAL_MS) {
                            lastNotifyTs = now
                            val percent = if (total > 0) (done * 100 / total).toInt() else -1
                            sendProgress(ctx, progressId, fileName, percent, done, total, speed, null)
                        }
                    }
                    if (error == null) break
                    if (attempt < MAX_RETRY && !error.startsWith("HTTP ")) {
                        attempt++
                        lastTs = 0L
                        speed = 0.0
                        delay(1000)
                        continue
                    }
                    break
                }

                if (error != null) {
                    sendError(ctx, progressId, fileName, error)
                    return@launch
                }

                val saved = try {
                    saveToDownloads(ctx, tmp, fileName, mimeType)
                } catch (ex: Exception) {
                    sendError(ctx, progressId, fileName, "" + ex.message)
                    return@launch
                }

                sendStop(ctx, progressId)
                notifyDone(ctx, doneId, saved)
            } finally {
                activeUrls.remove(url)
                tmp.delete()
            }
        }
    }

    private fun downloadToFile(
        url: String,
        destFile: File,
        userAgent: String?,
        cookie: String?,
        referer: String?,
        fallbackTotal: Long,
        onProgress: (Long, Long) -> Unit
    ): String? {
        var connection: HttpURLConnection? = null
        return try {
            val existing = if (destFile.exists()) destFile.length() else 0L
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 15000
                instanceFollowRedirects = true
                setRequestProperty("Accept-Encoding", "identity")
                if (!userAgent.isNullOrBlank()) setRequestProperty("User-Agent", userAgent)
                if (!cookie.isNullOrBlank()) setRequestProperty("Cookie", cookie)
                if (!referer.isNullOrBlank()) setRequestProperty("Referer", referer)
                if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
            }
            connection.connect()

            val code = connection.responseCode
            if (code !in 200..299) return "HTTP $code"

            val append = code == 206 && existing > 0
            val lengthHeader = connection.contentLengthLong
            var total = when {
                append && lengthHeader > 0 -> lengthHeader + existing
                !append && lengthHeader > 0 -> lengthHeader
                else -> fallbackTotal
            }
            var downloaded = if (append) existing else 0L
            var lastReported = 0L

            connection.inputStream.use { input ->
                RandomAccessFile(destFile, "rw").use { output ->
                    if (append) output.seek(existing) else output.setLength(0)
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
            if (total > 0 && downloaded < total) return "Incomplete"
            onProgress(downloaded, total)
            null
        } catch (ex: Exception) {
            "" + ex.message
        } finally {
            connection?.disconnect()
        }
    }

    private fun saveToDownloads(ctx: Context, tmp: File, name: String, mimeHint: String?): Saved {
        val ext = name.substringAfterLast('.', "").lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: mimeHint?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
            ?: "application/octet-stream"
        val openMime = if (mime == "application/octet-stream") "*/*" else mime

        val useFileApi = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager())

        if (useFileApi) {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            val target = uniqueFile(dir, name)
            tmp.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            MediaScannerConnection.scanFile(ctx, arrayOf(target.absolutePath), arrayOf(mime), null)
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".provider", target)
            return Saved(uri, target.name, openMime)
        }
        return saveByMediaStore(ctx, tmp, name, mime, openMime)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveByMediaStore(ctx: Context, tmp: File, name: String, mime: String, openMime: String): Saved {
        val resolver = ctx.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Cannot create file in Downloads")
        try {
            val output = resolver.openOutputStream(uri) ?: throw IOException("Cannot open output stream")
            output.use { out -> tmp.inputStream().use { input -> input.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (ex: Exception) {
            resolver.delete(uri, null, null)
            throw ex
        }
        val finalName = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: name
        return Saved(uri, finalName, openMime)
    }

    private fun uniqueFile(dir: File, name: String): File {
        var target = File(dir, name)
        if (!target.exists()) return target
        val dot = name.lastIndexOf('.')
        var base = if (dot > 0) name.substring(0, dot) else name
        var ext = if (dot > 0) name.substring(dot) else ""
        if (base.lowercase().endsWith(".tar")) {
            ext = base.substring(base.length - 4) + ext
            base = base.substring(0, base.length - 4)
        }
        var index = 1
        while (true) {
            target = File(dir, "$base ($index)$ext")
            if (!target.exists()) return target
            index++
        }
    }

    private fun sanitizeName(raw: String?): String {
        val cleaned = (raw ?: "").replace('/', '_').replace('\\', '_').replace('\u0000', '_').trim()
        return if (cleaned.isEmpty() || cleaned == "." || cleaned == "..") "download.bin" else cleaned
    }

    private fun sendProgress(
        ctx: Context,
        notificationId: Int,
        title: String,
        percent: Int,
        downloaded: Long,
        total: Long,
        speed: Double,
        text: String?
    ) {
        val intent = Intent(ctx, DownloadService::class.java).apply {
            putExtra("title", title)
            putExtra("progress", percent)
            putExtra("max", 100)
            putExtra("text", text)
            putExtra("notificationId", notificationId)
            if (text == null) {
                putExtra("downloadedBytes", downloaded)
                putExtra("totalBytes", total)
                putExtra("speedBps", speed)
            }
        }
        try { ctx.startService(intent) } catch (_: Exception) {}
    }

    private fun sendError(ctx: Context, notificationId: Int, title: String, error: String) {
        val intent = Intent(ctx, DownloadService::class.java).apply {
            putExtra("title", title)
            putExtra("text", ctx.getString(R.string.kr_download_error) + ": " + error)
            putExtra("notificationId", notificationId)
            putExtra("isError", true)
        }
        try { ctx.startService(intent) } catch (_: Exception) {}
    }

    private fun sendStop(ctx: Context, notificationId: Int) {
        val intent = Intent(ctx, DownloadService::class.java).apply {
            putExtra("stop", true)
            putExtra("notificationId", notificationId)
        }
        try { ctx.startService(intent) } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private fun notifyDone(ctx: Context, notificationId: Int, saved: Saved) {
        try {
            val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, ctx.getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
                )
            }
            val open = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(saved.uri, saved.openMime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pending = PendingIntent.getActivity(
                ctx, notificationId, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(saved.name)
                .setContentText(ctx.getString(R.string.kr_download_completed))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setOngoing(false)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()
            NotificationManagerCompat.from(ctx).notify(notificationId, notification)
        } catch (_: Exception) {}
    }

    private fun toast(ctx: Context, text: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show() }
    }
}
