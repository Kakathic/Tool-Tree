package com.tool.tree

import android.content.Context
import android.widget.Toast
import com.omarea.common.shared.ShareFileHelper
import com.termux.terminal.TerminalSession

object TerminalShare {
    @JvmStatic
    fun shareTranscript(context: Context, session: TerminalSession?, transcript: String?) {
        if (transcript.isNullOrBlank()) {
            Toast.makeText(context, context.getString(R.string.log_share_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val title = TerminalSessionStore.all().firstOrNull { it.session === session }?.request?.title
        ShareFileHelper.shareTextAsFile(
            context,
            transcript,
            title,
            context.getString(R.string.terminal_default_title),
            context.getString(R.string.terminal_share),
            context.getString(R.string.log_share_fail)
        )
    }
}
