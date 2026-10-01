package com.tool.tree

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

class NotificationCopyLogActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val text = intent?.getStringExtra(EXTRA_LOG_TEXT).orEmpty()

        val messageRes = when {
            text.isBlank() -> {
                R.string.kr_task_notify_copy_empty
            }
            else -> {
                val copied = runCatching {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("text", text))
                }.isSuccess
                if (copied) R.string.copy_success else R.string.copy_fail
            }
        }
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()

        finish()
    }

    companion object {
        const val EXTRA_LOG_TEXT = "log_text"
    }
}