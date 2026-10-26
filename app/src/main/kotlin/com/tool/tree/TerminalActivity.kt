package com.tool.tree

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.omarea.common.ui.DialogHelper
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

class TerminalActivity : AppCompatActivity(), TerminalViewClient, TerminalSessionClient {
    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_CWD = "cwd"
        private const val EXTRA_CMD = "cmd"
        private const val EXTRA_FONT_SIZE = "font_size"
        private const val DEFAULT_FONT_SP = 14
        private const val MIN_FONT_SP = 8
        private const val MAX_FONT_SP = 40

        fun start(context: Context, title: String?, cwd: String?, cmd: String?, fontSize: Int) {
            val intent = Intent(context, TerminalActivity::class.java).apply {
                putExtra(EXTRA_TITLE, title ?: "")
                putExtra(EXTRA_CWD, cwd ?: "")
                putExtra(EXTRA_CMD, cmd ?: "")
                putExtra(EXTRA_FONT_SIZE, fontSize)
                if (context !is AppCompatActivity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private lateinit var terminalView: TerminalView
    private lateinit var extraKeys: TerminalExtraKeys
    private var session: TerminalSession? = null
    private var launcher: TerminalLauncher? = null
    private var fontSizeSp = DEFAULT_FONT_SP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        setContentView(R.layout.activity_terminal)

        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        setTitle(title.ifEmpty { getString(R.string.terminal_default_title) })

        val customFont = intent.getIntExtra(EXTRA_FONT_SIZE, 0)
        if (customFont > 0) fontSizeSp = customFont.coerceIn(MIN_FONT_SP, MAX_FONT_SP)

        terminalView = findViewById(R.id.terminal_view)
        terminalView.setTerminalViewClient(this)
        applyFontSize()
        extraKeys = TerminalExtraKeys(findViewById<LinearLayout>(R.id.terminal_extra_keys), terminalView)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val current = session
                if (current != null && current.isRunning) {
                    DialogHelper.confirm(
                        this@TerminalActivity,
                        getString(R.string.terminal_default_title),
                        getString(R.string.terminal_close_confirm),
                        Runnable { finish() },
                        null
                    )
                } else {
                    finish()
                }
            }
        })

        startSession(intent.getStringExtra(EXTRA_CWD).orEmpty(), intent.getStringExtra(EXTRA_CMD).orEmpty())
    }

    private fun startSession(cwd: String, cmd: String) {
        val appContext = applicationContext
        Thread {
            val prepared = try {
                TerminalLauncher.prepare(appContext, cwd, cmd)
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, e.message ?: "Terminal error", Toast.LENGTH_LONG).show()
                    finish()
                }
                return@Thread
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    prepared.cleanup()
                    return@runOnUiThread
                }
                launcher = prepared
                val newSession = TerminalSession(prepared, 2000, this)
                session = newSession
                terminalView.attachSession(newSession)
                terminalView.requestFocus()
            }
        }.start()
    }

    private fun applyFontSize() {
        terminalView.setTextSize((fontSizeSp * resources.displayMetrics.density).toInt())
    }

    private fun changeFontSize(increase: Boolean) {
        fontSizeSp = (fontSizeSp + if (increase) 1 else -1).coerceIn(MIN_FONT_SP, MAX_FONT_SP)
        applyFontSize()
    }

    override fun onResume() {
        super.onResume()
        terminalView.onScreenUpdated()
    }

    override fun onDestroy() {
        if (isFinishing) session?.finishIfRunning()
        launcher?.cleanup()
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
            finish()
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

    override fun onTextChanged(changedSession: TerminalSession) {
        terminalView.onScreenUpdated()
    }

    override fun onTitleChanged(changedSession: TerminalSession) {}

    override fun onSessionFinished(finishedSession: TerminalSession) {
        terminalView.onScreenUpdated()
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
        if (text.isNullOrEmpty()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("terminal", text))
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        if (!text.isNullOrEmpty()) session?.emulator?.paste(text)
    }

    override fun onBell(session: TerminalSession) {}

    override fun onColorsChanged(session: TerminalSession) {
        terminalView.invalidate()
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
