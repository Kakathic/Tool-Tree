package com.omarea.common.shell

import android.content.Context
import com.omarea.krscript.config.StringResRef

class ShellTranslation(private val context: Context) {

    fun resolveRow(originRow: String): String {
        return StringResRef.resolve(context, originRow)
    }

    fun resolveRows(rows: List<String>): String {
        return rows.joinToString("\n") {
            resolveRow(it)
        }
    }

    fun getTranslatedResult(shellCommand: String, executor: KeepShell?): String {
        val shell = executor ?: KeepShellPublic.getDefaultInstance()
        return resolveRows(shell.doCmdSync(shellCommand).split('\n'))
    }
}