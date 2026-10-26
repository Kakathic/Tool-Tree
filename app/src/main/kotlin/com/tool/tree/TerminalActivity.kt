package com.tool.tree

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.omarea.common.ui.DialogHelper
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

class TerminalActivity : AppCompatActivity(), TerminalViewClient, TerminalSessionStore.Listener {
    companion object {
        const val ACTION_OPEN = "com.tool.tree.TERMINAL_OPEN"
        private const val EXTRA_HAS_REQUEST = "has_request"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_CWD = "cwd"
        private const val EXTRA_CMD = "cmd"
        private const val EXTRA_FONT_SIZE = "font_size"
        private const val EXTRA_ROOT = "root"
        private const val PREFS_NAME = "terminal"
        private const val STARTUP_FAIL_WINDOW_MS = 3000L
        private const val PREF_FONT_SP = "font_sp"
        private const val DEFAULT_FONT_SP = 14
        private const val MIN_FONT_SP = 8
        private const val MAX_FONT_SP = 40

        fun start(context: Context, title: String?, cwd: String?, cmd: String?, fontSize: Int, root: Int = 0) {
            val intent = Intent(context, TerminalActivity::class.java).apply {
                putExtra(EXTRA_HAS_REQUEST, true)
                putExtra(EXTRA_TITLE, title ?: "")
                putExtra(EXTRA_CWD, cwd ?: "")
                putExtra(EXTRA_CMD, cmd ?: "")
                putExtra(EXTRA_FONT_SIZE, fontSize)
                putExtra(EXTRA_ROOT, root)
                if (context !is AppCompatActivity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        /** Mở lại màn hình terminal với phiên dùng gần nhất (từ thông báo) */
        fun openIntent(context: Context): Intent =
            Intent(context, TerminalActivity::class.java).apply {
                action = ACTION_OPEN
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }

    private lateinit var terminalView: TerminalView
    private lateinit var extraKeys: TerminalExtraKeys
    private lateinit var drawer: DrawerLayout
    private lateinit var sessionDrawer: TerminalSessionDrawer
    private var current: TerminalSessionStore.Entry? = null
    private var pendingCreates = 0
    private var fontSizeSp = DEFAULT_FONT_SP

    private val sessionUi = object : TerminalSessionStore.Ui {
        override fun onScreenUpdated() {
            terminalView.onScreenUpdated()
        }

        override fun onColorsChanged() {
            terminalView.invalidate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        setContentView(R.layout.activity_terminal)

        // Cỡ chữ đã chỉnh (zoom) được nhớ và ưu tiên hơn font-size trong TOML
        val savedFont = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(PREF_FONT_SP, 0)
        val customFont = intent.getIntExtra(EXTRA_FONT_SIZE, 0)
        if (savedFont > 0) fontSizeSp = savedFont.coerceIn(MIN_FONT_SP, MAX_FONT_SP)
        else if (customFont > 0) fontSizeSp = customFont.coerceIn(MIN_FONT_SP, MAX_FONT_SP)

        terminalView = findViewById(R.id.terminal_view)
        terminalView.setTerminalViewClient(this)
        applyFontSize()
        extraKeys = TerminalExtraKeys(findViewById<LinearLayout>(R.id.terminal_extra_keys), terminalView)

        setupDrawer()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawer.isDrawerOpen(GravityCompat.START)) {
                    drawer.closeDrawer(GravityCompat.START)
                } else {
                    // Ẩn: phiên vẫn chạy trong TerminalService
                    finish()
                }
            }
        })

        TerminalSessionStore.addListener(this)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun setupDrawer() {
        drawer = findViewById(R.id.terminal_drawer)
        sessionDrawer = TerminalSessionDrawer(
            findViewById(R.id.terminal_session_list),
            findViewById<TextView>(R.id.terminal_new_session),
            onSelect = { entry ->
                switchTo(entry)
                drawer.closeDrawer(GravityCompat.START)
            },
            onClose = { entry -> confirmClose(entry) },
            onNew = {
                current?.let { createSession(it.request) }
                drawer.closeDrawer(GravityCompat.START)
            }
        )
        drawer.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(terminalView.windowToken, 0)
            }

            override fun onDrawerClosed(drawerView: View) {
                terminalView.requestFocus()
            }
        })

        // Android 10+ dùng vuốt mép trái để Back: nhường một dải mép trái cho ngăn kéo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val density = resources.displayMetrics.density
            drawer.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                val height = minOf(v.height, (200 * density).toInt())
                val top = (v.height - height) / 2
                v.setSystemGestureExclusionRects(listOf(Rect(0, top, (24 * density).toInt(), top + height)))
            }
        }
    }

    private fun handleIntent(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_HAS_REQUEST, false)) {
            val request = TerminalSessionStore.Request(
                intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                intent.getStringExtra(EXTRA_CWD).orEmpty(),
                intent.getStringExtra(EXTRA_CMD).orEmpty(),
                intent.getIntExtra(EXTRA_ROOT, 0)
            )
            // Mở lại phiên đang ẩn gần nhất của item này, chưa có mới tạo
            val existing = TerminalSessionStore.latestForRequest(request)
            if (existing != null) switchTo(existing) else createSession(request)
        } else {
            val target = TerminalSessionStore.latest()
            if (target != null) switchTo(target) else if (current == null && pendingCreates == 0) finish()
        }
    }

    private fun createSession(request: TerminalSessionStore.Request) {
        val appContext = applicationContext
        pendingCreates++
        Thread {
            val prepared = try {
                TerminalLauncher.prepare(appContext, request.cwd, request.cmd, request.root)
            } catch (e: Exception) {
                runOnUiThread {
                    pendingCreates--
                    Toast.makeText(this, e.message ?: "Terminal error", Toast.LENGTH_LONG).show()
                    if (current == null && TerminalSessionStore.all().isEmpty()) finish()
                }
                return@Thread
            }
            runOnUiThread {
                pendingCreates--
                if (isFinishing || isDestroyed) {
                    prepared.cleanup()
                    return@runOnUiThread
                }
                switchTo(TerminalSessionStore.create(appContext, request, prepared))
            }
        }.start()
    }

    private fun switchTo(entry: TerminalSessionStore.Entry) {
        val previous = current
        previous?.let { if (it !== entry) it.ui = null }
        current = entry
        entry.ui = sessionUi
        entry.lastActive = SystemClock.elapsedRealtime()
        title = entry.label
        terminalView.attachSession(entry.session)
        terminalView.onScreenUpdated()
        terminalView.requestFocus()
        sessionDrawer.refresh(TerminalSessionStore.all(), current)
        // Phiên cũ đã chết (đang giữ lại để đọc lỗi) thì bỏ khi chuyển đi
        if (previous != null && previous !== entry && previous.finished) TerminalSessionStore.remove(previous)
    }

    private fun confirmClose(entry: TerminalSessionStore.Entry) {
        if (entry.session.isRunning) {
            DialogHelper.confirm(
                this,
                entry.label,
                getString(R.string.terminal_close_confirm),
                Runnable { closeSession(entry) },
                null
            )
        } else {
            closeSession(entry)
        }
    }

    private fun closeSession(entry: TerminalSessionStore.Entry) {
        val wasCurrent = entry === current
        if (wasCurrent) current = null
        TerminalSessionStore.remove(entry)
        if (wasCurrent) {
            val next = TerminalSessionStore.latest()
            if (next != null) switchTo(next) else finish()
        }
    }

    override fun onSessionsChanged() {
        sessionDrawer.refresh(TerminalSessionStore.all(), current)
        if (TerminalSessionStore.all().isEmpty() && pendingCreates == 0) finish()
    }

    override fun onSessionFinished(entry: TerminalSessionStore.Entry) {
        if (entry !== current) return
        // exit / Ctrl+D: đóng phiên luôn. Nếu shell chết ngay lúc mở với mã lỗi thì giữ màn hình để đọc lỗi (Enter để đóng)
        val diedAtStartup = SystemClock.elapsedRealtime() - entry.createdAt < STARTUP_FAIL_WINDOW_MS && entry.session.exitStatus != 0
        if (diedAtStartup) terminalView.onScreenUpdated() else closeSession(entry)
    }

    private fun applyFontSize() {
        terminalView.setTextSize((fontSizeSp * resources.displayMetrics.density).toInt())
    }

    private fun changeFontSize(increase: Boolean) {
        fontSizeSp = (fontSizeSp + if (increase) 1 else -1).coerceIn(MIN_FONT_SP, MAX_FONT_SP)
        applyFontSize()
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(PREF_FONT_SP, fontSizeSp).apply()
    }

    override fun onResume() {
        super.onResume()
        terminalView.onScreenUpdated()
    }

    override fun onDestroy() {
        // Không kill phiên ở đây: phiên sống tiếp trong TerminalService để mở lại
        TerminalSessionStore.removeListener(this)
        current?.let {
            it.ui = null
            if (it.finished) TerminalSessionStore.remove(it)
        }
        super.onDestroy()
    }

    override fun onScale(scale: Float): Float {
        if (scale < 0.9f || scale > 1.1f) {
            changeFontSize(scale > 1f)
            return 1.0f
        }
        return scale
    }

    override fun onSingleTapUp(e: MotionEvent) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false

    override fun shouldEnforceCharBasedInput(): Boolean = false

    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

    override fun isTerminalViewSelected(): Boolean = true

    override fun copyModeChanged(copyMode: Boolean) {}

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ENTER && !session.isRunning) {
            current?.let { closeSession(it) }
            return true
        }
        return false
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false

    override fun onLongPress(event: MotionEvent): Boolean = false

    override fun readControlKey(): Boolean = extraKeys.consumeCtrl()

    override fun readAltKey(): Boolean = extraKeys.consumeAlt()

    override fun readShiftKey(): Boolean = false

    override fun readFnKey(): Boolean = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false

    override fun onEmulatorSet() {}

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
