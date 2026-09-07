package com.omarea.krscript.downloader

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import androidx.lifecycle.LifecycleCoroutineScope
import com.tool.tree.DownloadService
import com.tool.tree.R
import com.omarea.krscript.config.IconPathAnalysis
import com.omarea.krscript.executor.ShellExecutor
import com.omarea.krscript.model.DownloadNode
import com.omarea.krscript.model.ShellHandlerBase
import com.omarea.krscript.ui.ListItemDownload
import kotlinx.coroutines.*
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object DownloadTaskHelper {
    private const val STATUS_HOLD_MS = 900L
    private const val TAG = "DownloadTaskHelper"
    private const val NOTIFY_MIN_INTERVAL_MS = 400L

    enum class Status { IDLE, DOWNLOADING, PAUSED, COMPLETING, COMPLETED, ERROR }

    class Session(
        val id: String,
        val url: String,
        val urlSh: String,
        val destFile: File,
        val title: String,
        val item: DownloadNode,
        val appContext: Context,
        var downloaded: Long = 0L,
        var total: Long = -1L,
        var status: Status = Status.IDLE,
        var error: String? = null,
        var retriedOnNetworkChange: Boolean = false,
        var connection: HttpURLConnection? = null,
        var job: Job? = null,
        var onFinished: (() -> Unit)? = null,
        var lastProgressTimestampMs: Long = 0L,
        var lastProgressBytes: Long = 0L,
        var speedBytesPerSecond: Double = 0.0,
        var lastNotifyTimestampMs: Long = 0L
    ) {
        @Volatile var viewRef: ListItemDownload? = null
            private set

        fun bindView(v: ListItemDownload?) { viewRef = v }
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessions = ConcurrentHashMap<String, Session>()
    private val completedFiles = ConcurrentHashMap<String, File>()

    fun getSession(url: String): Session? = sessions[url]

    // Tra session cho 1 DownloadNode khi dựng lại trang (PageLayoutRender.createDownloadItem).
    // Ưu tiên tra theo "url" như getSession(); nếu rỗng (mục dùng "url-sh" và trang vừa được
    // parse lại thành instance DownloadNode MỚI - url đã resolve trước đó chỉ nằm trên instance
    // cũ, không còn giữ được) thì tra theo "urlSh" (chuỗi shell không đổi giữa các lần parse
    // config) để tìm lại đúng session đang chạy ngầm.
    fun findSessionForNode(node: DownloadNode): Session? {
        if (node.url.isNotBlank()) return sessions[node.url]
        if (node.urlSh.isNotBlank()) {
            return sessions.values.firstOrNull { it.urlSh.isNotBlank() && it.urlSh == node.urlSh }
        }
        return null
    }

    fun start(
        context: Context,
        scope: LifecycleCoroutineScope,
        item: DownloadNode,
        view: ListItemDownload,
        onFinished: () -> Unit
    ) {
        val existing = sessions[item.url]
        if (existing != null && existing.status != Status.COMPLETED && existing.status != Status.ERROR) {
            bindView(existing, view)
            return
        }
        existing?.let { sessions.remove(item.url) }

        val cachedFile = completedFiles[item.url]
        if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0L) {
            val session = Session(
                id = UUID.randomUUID().toString(),
                url = item.url,
                urlSh = item.urlSh,
                destFile = cachedFile,
                title = item.title.ifEmpty { item.url.substringAfterLast('/').substringBefore('?') },
                item = item,
                appContext = context.applicationContext,
                downloaded = cachedFile.length(),
                total = cachedFile.length(),
                status = Status.COMPLETING,
                onFinished = onFinished
            )
            sessions[item.url] = session
            bindView(session, view)
            postMain { runScriptOnly(context, session) }
            return
        }

        val destFile = try {
            File(context.cacheDir, "kr_download_" + UUID.randomUUID().toString().replace("-", "") + guessSuffix(item.url))
        } catch (ex: Exception) {
            view.showStatusLabel(context.getString(R.string.kr_download_error), spin = false)
            finishAfterDelay(view, onFinished)
            return
        }

        val session = Session(
            id = UUID.randomUUID().toString(),
            url = item.url,
            urlSh = item.urlSh,
            destFile = destFile,
            title = item.title.ifEmpty { item.url.substringAfterLast('/').substringBefore('?') },
            item = item,
            appContext = context.applicationContext,
            onFinished = onFinished
        )
        sessions[item.url] = session
        bindView(session, view)

        session.job = appScope.launch {
            runDownload(context, session)
        }
    }

    fun pause(session: Session) {
        if (session.status != Status.DOWNLOADING) return
        session.status = Status.PAUSED
        try {
            session.connection?.disconnect()
        } catch (_: Exception) {}
        session.connection = null
        postMain {
            session.viewRef?.showStatusLabel(session.viewRef?.context?.getString(R.string.kr_download_paused_tap) ?: "Paused", spin = false)
        }
        updateNotification(session, textOverride = session.appContext.getString(R.string.kr_download_paused_tap))
    }

    fun resume(context: Context, session: Session) {
        if (session.status != Status.PAUSED) return
        session.status = Status.DOWNLOADING
        session.lastProgressTimestampMs = 0L
        session.speedBytesPerSecond = 0.0
        session.job = appScope.launch {
            runDownload(context, session)
        }
    }

    fun cancel(session: Session) {
        session.status = Status.IDLE
        session.job?.cancel()
        try { session.connection?.disconnect() } catch (_: Exception) {}
        session.connection = null
        session.destFile.delete()
        completedFiles.remove(session.url)
        sessions.remove(session.url)
        dismissNotification(session)
        postMain {
            session.viewRef?.let { v ->
                v.restoreDesc()
                v.finishBusy()
            }
        }
    }

    fun bindView(session: Session, view: ListItemDownload) {
        postMain {
            session.bindView(view)
            when (session.status) {
                Status.DOWNLOADING -> {
                    view.markBusy { pause(session) }
                    view.updateDownloadProgress(session.downloaded, session.total, session.speedBytesPerSecond)
                }
                Status.PAUSED -> {
                    view.markBusy { resume(view.context, session) }
                    view.showStatusLabel(view.context.getString(R.string.kr_download_paused_tap), spin = false)
                }
                Status.COMPLETING -> {
                    view.markBusy {}
                    view.showStatusLabel(view.context.getString(R.string.kr_download_execute_wait))
                }
                Status.COMPLETED -> {
                    view.showStatusLabel(view.context.getString(R.string.kr_download_execute_success))
                    view.finishBusy()
                }
                Status.ERROR -> {
                    view.showStatusLabel(view.context.getString(R.string.kr_download_error) + ": " + (session.error ?: ""), spin = false)
                }
                Status.IDLE -> {
                    view.restoreDesc()
                    view.finishBusy()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun runDownload(context: Context, session: Session) {
        session.status = Status.DOWNLOADING
        postMain { session.viewRef?.markBusy { pause(session) } }
        startNotification(session)

        val networkCallback = registerNetworkCallback(context) {
            if (session.status == Status.DOWNLOADING && !session.retriedOnNetworkChange) {
                session.retriedOnNetworkChange = true
                postMain {
                    session.viewRef?.showStatusLabel(
                        session.viewRef?.context?.getString(R.string.kr_download_retrying)
                            ?: "Network changed, retrying…"
                    )
                }
                updateNotification(session, textOverride = context.getString(R.string.kr_download_retrying))
                try { session.connection?.disconnect() } catch (_: Exception) {}
                session.connection = null
            }
        }

        try {
            while (session.status == Status.DOWNLOADING) {
                val error = downloadToFile(
                    url = session.url,
                    destFile = session.destFile,
                    existingBytes = session.downloaded,
                    onConnectionOpened = { conn -> session.connection = conn },
                    onProgress = { downloaded, total ->
                        val now = System.currentTimeMillis()
                        if (session.lastProgressTimestampMs > 0L) {
                            val elapsedMs = now - session.lastProgressTimestampMs
                            if (elapsedMs > 0) {
                                val instantSpeed = (downloaded - session.lastProgressBytes) * 1000.0 / elapsedMs
                                session.speedBytesPerSecond = if (session.speedBytesPerSecond <= 0.0) {
                                    instantSpeed
                                } else {
                                    session.speedBytesPerSecond * 0.7 + instantSpeed * 0.3
                                }
                            }
                        }
                        session.lastProgressTimestampMs = now
                        session.lastProgressBytes = downloaded
                        session.downloaded = downloaded
                        session.total = total
                        postMain {
                            val v = session.viewRef
                            if (v != null && session.status == Status.DOWNLOADING) {
                                v.updateDownloadProgress(downloaded, total, session.speedBytesPerSecond)
                            }
                        }
                        // Throttle service starts: onProgress fires every 32KB and each
                        // updateNotification() restarts the foreground service, which
                        // floods the main thread on fast connections.
                        if (now - session.lastNotifyTimestampMs >= NOTIFY_MIN_INTERVAL_MS) {
                            session.lastNotifyTimestampMs = now
                            updateNotification(session)
                        }
                    }
                )

                if (session.status == Status.PAUSED) break
                if (session.status != Status.DOWNLOADING) break

                if (error != null) {
                    if (!session.retriedOnNetworkChange && isNetworkRelatedError(error)) {
                        session.retriedOnNetworkChange = true
                        postMain {
                            session.viewRef?.showStatusLabel(
                                session.viewRef?.context?.getString(R.string.kr_download_retrying)
                                    ?: "Network changed, retrying…"
                            )
                        }
                        updateNotification(session, textOverride = context.getString(R.string.kr_download_retrying))
                        delay(500)
                        session.lastProgressTimestampMs = 0L
                        continue
                    }
                    session.status = Status.ERROR
                    session.error = error
                    postMain {
                        session.viewRef?.let { v ->
                            v.clearCancelAction()
                            v.showStatusLabel(v.context.getString(R.string.kr_download_error) + ": " + error, spin = false)
                        }
                    }
                    updateNotificationError(session)
                    break
                }

                postMain { session.viewRef?.clearCancelAction() }
                session.status = Status.COMPLETING
                updateNotification(session, textOverride = context.getString(R.string.kr_download_execute_wait))
                withContext(Dispatchers.Main) {
                    runPostScript(context, session)
                }
                break
            }
        } finally {
            unregisterNetworkCallback(context, networkCallback)
        }
    }

    private fun downloadToFile(
        url: String,
        destFile: File,
        existingBytes: Long,
        onConnectionOpened: (HttpURLConnection) -> Unit,
        onProgress: (Long, Long) -> Unit
    ): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 15000
                instanceFollowRedirects = true
            }

            if (existingBytes > 0) {
                connection.setRequestProperty("Range", "bytes=$existingBytes-")
            }

            connection.connect()
            onConnectionOpened(connection)

            val responseCode = connection.responseCode
            if (responseCode !in 200..299 && responseCode != 206) {
                return "HTTP $responseCode"
            }

            val acceptRanges = connection.getHeaderField("Accept-Ranges")
            val contentRange = connection.getHeaderField("Content-Range")
            var total: Long
            var append: Boolean

            if (responseCode == 206 && (acceptRanges == "bytes" || contentRange != null)) {
                append = true
                total = parseTotalFromContentRange(contentRange)
                    ?: (connection.contentLengthLong + existingBytes)
            } else {
                append = false
                total = connection.contentLengthLong
            }

            var downloaded = if (append) existingBytes else 0L
            var lastReported = 0L

            connection.inputStream.use { input ->
                RandomAccessFile(destFile, if (append) "rw" else "rw").use { output ->
                    if (append) {
                        output.seek(existingBytes)
                    } else {
                        output.setLength(0)
                    }
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
        } catch (ex: CancellationException) {
            "cancelled"
        } catch (ex: Exception) {
            "" + ex.message
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseTotalFromContentRange(contentRange: String?): Long? {
        if (contentRange == null) return null
        val slashIndex = contentRange.lastIndexOf('/')
        if (slashIndex < 0) return null
        return contentRange.substring(slashIndex + 1).trim().toLongOrNull()
    }

    private fun isNetworkRelatedError(error: String): Boolean {
        val lower = error.lowercase()
        return lower.contains("timeout")
            || lower.contains("socket")
            || lower.contains("network")
            || lower.contains("connection")
            || lower.contains("eof")
            || lower.contains("reset")
            || lower.contains("unreachable")
            || lower.contains("unknownhost")
    }

    @SuppressLint("MissingPermission")
    private fun runScriptOnly(context: Context, session: Session) {
        session.status = Status.COMPLETING
        startNotification(session)
        updateNotification(session, textOverride = context.getString(R.string.kr_download_execute_wait))
        runPostScript(context, session)
    }

    private fun runPostScript(
        context: Context,
        session: Session
    ) {
        val item = session.item
        val script = item.setState
        if (script.isNullOrEmpty()) {
            completeSession(context, session, success = true)
            return
        }

        postMain {
            session.viewRef?.showStatusLabel(
                session.viewRef?.context?.getString(R.string.kr_download_execute_wait)
                ?: "Running script…"
            )
        }

        val errorRows = ArrayList<String>()
        val handler = object : ShellHandlerBase(context.applicationContext) {
            override fun updateLog(msg: SpannableString) {}
            override fun onError(msg: Any?) {
                synchronized(errorRows) { errorRows.add("" + msg?.toString()) }
            }
            override fun onExit(msg: Any?) {
                val success = errorRows.isEmpty()
                val errorText = if (success) null else synchronized(errorRows) { errorRows.joinToString("\n") }
                completeSession(context, session, success, errorText)
            }
            override fun onStart(forceStop: Runnable?) {}
            override fun onStart(msg: Any?) {}
            override fun onProgress(current: Int, total: Int) {}
        }

        ShellExecutor().execute(context, item, script, null, hashMapOf("state" to session.destFile.absolutePath), handler)
    }

    // success=false ở đây LUÔN là lỗi SCRIPT (chạy sau khi tải xong), khác với lỗi TẢI (network,
    // xử lý riêng trong runDownload() -> updateNotificationError()). Theo yêu cầu: lỗi script chỉ
    // hiện ở UI item (desc) trong trang, KHÔNG đẩy ra thông báo hệ thống - notification chỉ dành
    // cho lỗi tải.
    private fun completeSession(context: Context, session: Session, success: Boolean, errorText: String? = null) {
        if (success) {
            session.status = Status.COMPLETED
            completedFiles[session.url] = session.destFile
            postMain {
                session.viewRef?.let { v ->
                    v.showStatusLabel(v.context.getString(R.string.kr_download_execute_success))
                    v.finishBusy()
                }
                session.onFinished?.invoke()
            }
            dismissNotification(session)
            appScope.launch {
                delay(2000)
                sessions.remove(session.url)
            }
        } else {
            session.status = Status.ERROR
            session.error = errorText?.takeIf { it.isNotBlank() } ?: "Script failed"
            postMain {
                session.viewRef?.let { v ->
                    v.clearCancelAction()
                    v.showStatusLabel(v.context.getString(R.string.kr_download_execute_fail) + ": " + session.error, spin = false)
                }
            }
            // Không gọi updateNotificationError() ở đây - lỗi script chỉ hiện trong UI item.
            dismissNotification(session)
        }
    }

    private const val NOTIFICATION_BASE_ID = 2000

    private fun notificationId(session: Session): Int {
        return NOTIFICATION_BASE_ID + session.id.hashCode().mod(1000)
    }

    private fun resolveLargeIcon(context: Context, item: DownloadNode): Bitmap {
        val customIcon = if (item.iconPath.isNotEmpty()) IconPathAnalysis().loadIcon(context, item) else null
        val drawable = customIcon ?: context.packageManager.getApplicationIcon(context.applicationInfo)
        return drawableToBitmap(drawable)
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        val targetSize = 256
        val frame = if (drawable is AnimationDrawable && drawable.numberOfFrames > 0) drawable.getFrame(0) else drawable
        if (frame is BitmapDrawable && frame.bitmap != null) {
            return Bitmap.createScaledBitmap(frame.bitmap, targetSize, targetSize, true)
        }
        val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        frame.setBounds(0, 0, targetSize, targetSize)
        frame.draw(canvas)
        return bitmap
    }

    // DownloadService giờ chỉ là Service thường (giống NotiService), gọi notify() trực tiếp,
    // không còn startForeground - nên phải dùng startService(), KHÔNG được dùng
    // startForegroundService() (nếu dùng startForegroundService() mà Service không gọi
    // startForeground() trong onStartCommand() thì Android sẽ crash app với
    // ForegroundServiceDidNotStartInTimeException). Đổi lại: tải file khi app bị đưa xuống nền
    // có thể bị hệ thống dừng giữa chừng (không còn được bảo vệ như foreground service).
    private fun startNotification(session: Session) {
        val ctx = session.appContext
        val intent = Intent(ctx, DownloadService::class.java).apply {
            putExtra("title", session.title)
            putExtra("progress", 0)
            putExtra("max", 100)
            putExtra("text", ctx.getString(R.string.kr_download_create_success))
            putExtra("notificationId", notificationId(session))
            putExtra("largeIcon", resolveLargeIcon(ctx, session.item))
        }
        ctx.startService(intent)
    }

    private fun updateNotification(session: Session, textOverride: String? = null) {
        val ctx = session.appContext
        val intent = Intent(ctx, DownloadService::class.java).apply {
            putExtra("title", session.title)
            val progress = if (session.total > 0) (session.downloaded * 100 / session.total).toInt() else -1
            putExtra("progress", progress)
            putExtra("max", 100)
            putExtra("text", textOverride)
            putExtra("notificationId", notificationId(session))
            if (textOverride == null) {
                putExtra("downloadedBytes", session.downloaded)
                putExtra("totalBytes", session.total)
                putExtra("speedBps", session.speedBytesPerSecond)
            }
        }
        ctx.startService(intent)
    }

    private fun updateNotificationError(session: Session) {
        val ctx = session.appContext
        val intent = Intent(ctx, DownloadService::class.java).apply {
            putExtra("title", session.title)
            putExtra("text", ctx.getString(R.string.kr_download_error) + ": " + (session.error ?: ""))
            putExtra("notificationId", notificationId(session))
            putExtra("isError", true)
        }
        ctx.startService(intent)
    }

    private fun dismissNotification(session: Session) {
        val ctx = session.appContext
        val intent = Intent(ctx, DownloadService::class.java).apply {
            putExtra("stop", true)
            putExtra("notificationId", notificationId(session))
        }
        ctx.startService(intent)
    }

    private val networkCallbacks = ConcurrentHashMap<Context, ConnectivityManager.NetworkCallback>()

    private fun registerNetworkCallback(
        context: Context,
        onNetworkChanged: () -> Unit
    ): ConnectivityManager.NetworkCallback? {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            var isFirstCallback = true
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (isFirstCallback) {
                        isFirstCallback = false
                        return
                    }
                    onNetworkChanged()
                }
                override fun onLost(network: Network) {
                    isFirstCallback = false
                    onNetworkChanged()
                }
            }
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, callback)
            networkCallbacks[context] = callback
            callback
        } catch (e: Exception) {
            null
        }
    }

    private fun unregisterNetworkCallback(context: Context, callback: ConnectivityManager.NetworkCallback?) {
        if (callback == null) return
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(callback)
        } catch (_: Exception) {}
        networkCallbacks.remove(context)
    }

    private fun postMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            Handler(Looper.getMainLooper()).post(action)
        }
    }

    private fun finishAfterDelay(view: ListItemDownload, onFinished: () -> Unit) {
        Handler(Looper.getMainLooper()).postDelayed({
            view.finishBusy()
            onFinished()
        }, STATUS_HOLD_MS)
    }

    private fun guessSuffix(url: String): String {
        val clean = url.substringBefore('?').substringBefore('#')
        val name = clean.substringAfterLast('/')
        val dot = name.lastIndexOf('.')
        return if (dot in 1 until name.length - 1) name.substring(dot) else ""
    }

    fun cancelByUrl(url: String) {
        sessions[url]?.let { cancel(it) }
    }

    fun pauseByUrl(url: String) {
        sessions[url]?.let { pause(it) }
    }

    fun resumeByUrl(context: Context, url: String) {
        sessions[url]?.let { resume(context, it) }
    }

    fun hasActiveSession(url: String): Boolean {
        val s = sessions[url] ?: return false
        return s.status == Status.DOWNLOADING || s.status == Status.PAUSED || s.status == Status.COMPLETING
    }
}
