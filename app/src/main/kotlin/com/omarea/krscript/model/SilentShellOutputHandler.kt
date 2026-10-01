package com.omarea.krscript.model

import android.content.Context
import android.text.SpannableString

open class SilentShellOutputHandler(context: Context) : ShellHandlerBase(context) {
    override fun onProgress(current: Int, total: Int) {
    }

    override fun onStart(msg: Any?) {}
    override fun onStart(forceStop: Runnable?) {}
    override fun onExit(msg: Any?) {}

    override fun updateLog(msg: SpannableString) {
    }

    fun processOutput(output: String) {
        output.lineSequence().forEach { line ->
            if (line.isNotBlank()) {
                onReaderMsg(line)
            }
        }
    }
}
