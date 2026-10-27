package com.tool.tree

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Giữ các phiên terminal sống ngoài Activity (ẩn/mở lại, nhiều phiên).
 * Mọi thao tác phải gọi trên main thread.
 */
object TerminalSessionStore {
    class Request(val title: String, val cwd: String, val cmd: String, val root: Int, val pagePath: String = "") {
        val key: String get() = "$title\u0000$cwd\u0000$cmd\u0000$root\u0000$pagePath"
    }

    interface Ui {
        fun onScreenUpdated()
        fun onColorsChanged()
    }

    interface Listener {
        fun onSessionsChanged()
        fun onSessionFinished(entry: Entry)
    }

    class Entry(
        val id: Long,
        val request: Request,
        val label: String,
        val launcher: TerminalLauncher,
        appContext: Context
    ) {
        val createdAt = SystemClock.elapsedRealtime()
        var lastActive = createdAt
        var finished = false

        // Khác null khi phiên đang hiện trên Activity
        var ui: Ui? = null

        val session: TerminalSession = TerminalSession(launcher, 2000, EntryClient(appContext, this))
    }

    private class EntryClient(private val context: Context, private val entry: Entry) : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) {
            entry.ui?.onScreenUpdated()
        }

        override fun onTitleChanged(changedSession: TerminalSession) {}

        override fun onSessionFinished(finishedSession: TerminalSession) {
            handleFinished(entry)
        }

        override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
            if (text.isNullOrEmpty()) return
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("terminal", text))
        }

        override fun onPasteTextFromClipboard(session: TerminalSession?) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
            if (!text.isNullOrEmpty()) session?.emulator?.paste(text)
        }

        override fun onBell(session: TerminalSession) {}

        override fun onColorsChanged(session: TerminalSession) {
            entry.ui?.onColorsChanged()
        }

        override fun onTerminalCursorStateChange(state: Boolean) {}

        override fun getTerminalCursorStyle(): Int? = null

        override fun logError(tag: String?, message: String?) { Log.e(tag, message ?: "") }

        override fun logWarn(tag: String?, message: String?) { Log.w(tag, message ?: "") }

        override fun logInfo(tag: String?, message: String?) { Log.i(tag, message ?: "") }

        override fun logDebug(tag: String?, message: String?) { Log.d(tag, message ?: "") }

        override fun logVerbose(tag: String?, message: String?) { Log.v(tag, message ?: "") }

        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
            Log.e(tag, message ?: "", e)
        }

        override fun logStackTrace(tag: String?, e: Exception?) {
            Log.e(tag, "", e)
        }
    }

    private val entries = ArrayList<Entry>()
    private val listeners = CopyOnWriteArrayList<Listener>()
    private val labelCounters = HashMap<String, Int>()
    private var nextId = 1L

    fun addListener(listener: Listener) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun all(): List<Entry> = ArrayList(entries)

    fun latest(): Entry? = entries.maxByOrNull { it.lastActive }

    /** Phiên đang chạy gần nhất của cùng một item [[terminal]] */
    fun latestForRequest(request: Request): Entry? =
        entries.filter { it.request.key == request.key && !it.finished && it.session.isRunning }.maxByOrNull { it.lastActive }

    fun create(context: Context, request: Request, launcher: TerminalLauncher): Entry {
        val app = context.applicationContext
        val n = (labelCounters[request.key] ?: 0) + 1
        labelCounters[request.key] = n
        val base = request.title.ifEmpty { app.getString(R.string.terminal_default_title) }
        val label = if (n == 1) base else "$base ($n)"
        val entry = Entry(nextId++, request, label, launcher, app)
        entries.add(entry)
        TerminalService.ensureStarted(app)
        notifyChanged()
        return entry
    }

    fun remove(entry: Entry) {
        if (!entries.remove(entry)) return
        entry.ui = null
        entry.session.finishIfRunning()
        entry.launcher.cleanup()
        if (entries.none { it.request.key == entry.request.key }) labelCounters.remove(entry.request.key)
        notifyChanged()
    }

    fun killAll() {
        all().forEach { remove(it) }
    }

    private fun handleFinished(entry: Entry) {
        entry.finished = true
        if (entry.ui == null) {
            // Phiên đang ẩn tự kết thúc: không ai đọc nữa nên bỏ luôn
            remove(entry)
        } else {
            listeners.forEach { it.onSessionFinished(entry) }
            notifyChanged()
        }
    }

    private fun notifyChanged() {
        listeners.forEach { it.onSessionsChanged() }
    }
}
