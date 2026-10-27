package com.tool.tree

import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import com.termux.view.TerminalView

class TerminalInputBar(
    pager: TerminalSwipePager,
    private val edit: EditText,
    private val terminalView: TerminalView
) {
    init {
        edit.setOnEditorActionListener { _, actionId, event ->
            val isSend = actionId == EditorInfo.IME_ACTION_SEND ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (isSend) {
                send()
                true
            } else {
                false
            }
        }
        pager.onPageChanged = { page ->
            if (page == 1) focusInput() else terminalView.requestFocus()
        }
    }

    private fun send() {
        val session = terminalView.currentSession
        if (session == null) return
        val text = edit.text.toString()
        if (text.isNotEmpty()) {
            val emulator = session.emulator
            if (emulator != null) emulator.paste(text) else session.write(text)
        }
        session.write("\r")
        edit.setText("")
        edit.requestFocus()
    }

    private fun focusInput() {
        edit.requestFocus()
        edit.post {
            val imm = edit.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
        }
    }
}
