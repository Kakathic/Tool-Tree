package com.tool.tree

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.core.content.ContextCompat
import com.omarea.common.shell.KeepShellPublic
import com.omarea.common.shell.ShellExecutor
import com.omarea.krscript.executor.ScriptEnvironmen
import com.omarea.krscript.model.ClickableNode
import com.omarea.krscript.model.EditorNode
import com.omarea.krscript.model.TerminalNode
import com.omarea.krscript.shortcut.ActionShortcutManager

class ShortcutLaunchActivity : Activity() {
    companion object {
        const val EXTRA_FAST_ID = "fastTargetId"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        disableTransition()

        val fastId = intent.getStringExtra(EXTRA_FAST_ID)
        if (fastId.isNullOrEmpty()) {
            fallbackToPage()
            return
        }

        LanguageManager.init(this)
        ShellExecutor.setTmpDir(cacheDir.absolutePath)

        val appContext = applicationContext
        Thread {
            val node = try {
                ActionShortcutManager(appContext).getFastTarget(fastId)
            } catch (_: Exception) {
                null
            }
            val ready = node != null && prepareEnvironment(appContext)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (ready && node != null && launchTarget(node)) {
                    finish()
                } else {
                    fallbackToPage()
                }
            }
        }.start()
    }

    private fun prepareEnvironment(context: Context): Boolean {
        if (ScriptEnvironmen.isInited()) return true
        if (!hasStoragePermission(context)) return false

        val config = KrScriptConfig()
        if (config.peekBeforeStartSh(context).isNotEmpty()) return false
        if (!KeepShellPublic.checkRoot()) return false

        config.init(context)
        return ScriptEnvironmen.isInited()
    }

    private fun hasStoragePermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return Environment.isExternalStorageManager()
        return listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ).all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun sdkAllowed(node: ClickableNode): Boolean {
        val sdk = Build.VERSION.SDK_INT
        if (node.targetSdkVersion > 0 && sdk != node.targetSdkVersion) return false
        if (sdk > node.maxSdkVersion) return false
        if (sdk < node.minSdkVersion) return false
        return true
    }

    private fun launchTarget(node: ClickableNode): Boolean {
        if (node.locked || node.lockShell.isNotEmpty() || !sdkAllowed(node)) return false
        return when (node) {
            is TerminalNode -> {
                TerminalActivity.start(
                    this, node.title, node.cwd, node.cmd, node.fontSize, node.root,
                    node.currentPageConfigPath, node.exitOnBack, node.closeOnExit
                )
                true
            }
            is EditorNode -> {
                if (node.file.isEmpty()) return false
                TextEditorActivity.start(
                    this, node.file, node.title, node.desc, node.wrap, node.pageConfigDir,
                    node.placeholder, node.readonly, node.needInput, node.value, node.valueSh, node.run
                )
                true
            }
            else -> false
        }
    }

    private fun fallbackToPage() {
        startActivity(Intent(this, ActionPage::class.java).apply {
            putExtras(intent)
            removeExtra(EXTRA_FAST_ID)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        })
        finish()
    }

    @Suppress("DEPRECATION")
    private fun disableTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            overridePendingTransition(0, 0)
        }
    }

    override fun finish() {
        super.finish()
        disableTransition()
    }
}
