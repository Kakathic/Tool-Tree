package com.tool.tree

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import com.omarea.common.shared.FileWrite
import com.omarea.krscript.executor.ScriptEnvironmen
import com.termux.terminal.TerminalSession
import java.io.File

class TerminalLauncher private constructor(
    private val appContext: Context,
    private val tag: String,
    private val useRoot: Boolean,
    private val home: File,
    private val bash: String,
    private val busybox: String,
    private val rcFile: File,
    private val ttyFile: File,
    private val pidFile: File
) : TerminalSession.ProcessFactory {

    private val resizeThread = HandlerThread("terminal-resize").also { it.start() }
    private val resizeHandler = Handler(resizeThread.looper)

    @Volatile
    private var killing = false

    companion object {
        private const val ASSETS_FILE = "file:///android_asset/"

        private fun q(s: String): String = "'" + s.replace("'", "'\\''") + "'"

        fun prepare(context: Context, cwdArg: String, cmd: String, root: Int = 0, pagePath: String = ""): TerminalLauncher {
            val spf = context.getSharedPreferences("kr-script-config", Context.MODE_PRIVATE)
            val executor = spf.getString("executor", "root/executor.sh") ?: "root/executor.sh"
            val toolkit = spf.getString("toolkitDir", "home") ?: "home"
            val executorReady = ScriptEnvironmen.init(context, executor, toolkit)
            val executorName = if (executor.startsWith(ASSETS_FILE)) executor.substring(ASSETS_FILE.length) else executor
            val executorPath = FileWrite.getPrivateFilePath(context, executorName)

            val home = File(context.filesDir, "home")
            val bash = File(home, "bin/bash")
            val busybox = File(home, "bin/busybox")
            if (!bash.exists() || !busybox.exists()) {
                throw IllegalStateException("Missing ${bash.absolutePath} or ${busybox.absolutePath}")
            }

            val tmp = File(home, "tmp").apply { mkdirs() }
            // Dọn file phiên cũ còn sót (tiến trình bị kill đột ngột)
            val staleBefore = System.currentTimeMillis() - 24 * 3600 * 1000L
            tmp.listFiles()?.filter { it.name.startsWith(".term_") && it.lastModified() < staleBefore }?.forEach { it.delete() }
            val id = System.currentTimeMillis().toString()
            val rcFile = File(tmp, ".term_$id.rc")
            val ttyFile = File(tmp, ".term_$id.tty")
            val pidFile = File(tmp, ".term_$id.pid")

            val pageVars = ScriptEnvironmen.getPageVariables(context, pagePath)
            var cwd = cwdArg.trim().replace("{HOME}", home.absolutePath)
            for (k in listOf("PAGE_CONFIG_DIR", "PAGE_WORK_DIR")) pageVars[k]?.let { cwd = cwd.replace("{$k}", it) }
            if (cwd.isEmpty()) cwd = home.absolutePath

            val rc = StringBuilder().apply {
                append("export TERM=xterm-256color\n")
                append("export COLORTERM=truecolor\n")
                append("export SHELL=").append(q(bash.absolutePath)).append("\n")
                if (executorReady && File(executorPath).exists()) {
                    append(". ").append(q(executorPath)).append("\n")
                } else {
                    append("echo ").append(q("executor.sh not ready: $executorPath")).append("\n")
                }
                for ((k, v) in pageVars) append("export ").append(k).append("=").append(q(v)).append("\n")
                append("unset COLUMNS LINES\n")
                append("cd ").append(q(cwd)).append(" 2>/dev/null || cd \"\$HOME\"\n")
                append("export HISTFILE=\"\$HOME/.bash_history\"\n")
                append("export HISTSIZE=5000 HISTFILESIZE=5000\n")
                append("export HISTCONTROL=ignoreboth\n")
                append("shopt -s histappend\n")
                append("PROMPT_COMMAND=\"history -a\${PROMPT_COMMAND:+;\$PROMPT_COMMAND}\"\n")
                append("PS1='\\['\"\\033[0;32m\"'\\]\\w\\['\"\\033[0m\"'\\] \\['\"\\033[0;97m\"'\\]\\$\\['\"\\033[0m\"'\\] '\n")
                append("clear() { printf '\\033[H\\033[2J\\033[3J'; }\n")
                append("bind '\"\\C-l\": clear-display' 2>/dev/null\n")
                append("bind 'set enable-bracketed-paste on' 2>/dev/null\n")
                append("bind 'set completion-ignore-case on' 2>/dev/null\n")
                append("bind 'set show-all-if-ambiguous on' 2>/dev/null\n")
                append("bind '\"\\e[A\": history-search-backward' 2>/dev/null\n")
                append("bind '\"\\e[B\": history-search-forward' 2>/dev/null\n")
                append("bind '\"\\eOA\": history-search-backward' 2>/dev/null\n")
                append("bind '\"\\eOB\": history-search-forward' 2>/dev/null\n")
                append("[ -f \"\$HOME/.bashrc\" ] && . \"\$HOME/.bashrc\"\n")
                if (cmd.isNotBlank()) append(cmd).append("\n")
            }
            rcFile.writeText(rc.toString())

            // root = 1: mở shell bằng su nếu máy có root; còn lại chạy quyền app (gõ su trong terminal vẫn dùng được)
            val useRoot = root == 1 && ScriptEnvironmen.isRooted()
            return TerminalLauncher(context.applicationContext, "term_$id", useRoot, home, bash.absolutePath, busybox.absolutePath, rcFile, ttyFile, pidFile)
        }
    }

    override fun start(columns: Int, rows: Int): Process {
        val inner = "${q(busybox)} stty rows $rows cols $columns -ixon; " +
                "echo \$\$ > ${q(pidFile.absolutePath)}; " +
                "${q(busybox)} readlink /proc/\$\$/fd/0 > ${q(ttyFile.absolutePath)}; " +
                "exec ${q(bash)} --rcfile ${q(rcFile.absolutePath)} -i"

        return if (useRoot) {
            val full = "shell_progres=${q(tag)} SHELL=${q(bash)} ${q(busybox)} script -qc ${q(inner)} /dev/null"
            ProcessBuilder("su", "-c", full).redirectErrorStream(true).start()
        } else {
            val pb = ProcessBuilder(busybox, "script", "-qc", inner, "/dev/null")
            pb.directory(home)
            pb.environment().apply {
                put("SHELL", bash)
                put("HOME", home.absolutePath)
                put("TERM", "xterm-256color")
                put("shell_progres", tag)
            }
            pb.redirectErrorStream(true).start()
        }
    }

    override fun resize(columns: Int, rows: Int) {
        resizeHandler.removeCallbacksAndMessages(null)
        resizeHandler.postDelayed({
            val ttyPath = q(ttyFile.absolutePath)
            runShell(
                "i=0; while [ ! -s $ttyPath ] && [ \$i -lt 50 ]; do sleep 0.1; i=\$((i+1)); done; " +
                        "${q(busybox)} stty -F \"\$(cat $ttyPath)\" rows $rows cols $columns"
            )
        }, 60)
    }

    override fun kill(process: Process) {
        killing = true
        val pidPath = q(pidFile.absolutePath)
        Thread {
            try {
                ScriptEnvironmen.executeResultRoot(appContext, "shell_progres='$tag' killtree", null)
            } catch (ignored: Exception) {
            }
            runShell("kill -HUP \$(cat $pidPath) 2>/dev/null; sleep 0.2; kill -9 \$(cat $pidPath) 2>/dev/null")
            deleteFiles()
        }.start()
    }

    private fun runShell(script: String) {
        try {
            val p = if (useRoot) ProcessBuilder("su", "-c", script) else ProcessBuilder("sh", "-c", script)
            val proc = p.redirectErrorStream(true).start()
            proc.outputStream.close()
            proc.inputStream.readBytes()
            proc.waitFor()
        } catch (ignored: Exception) {
        }
    }

    fun cleanup() {
        resizeThread.quitSafely()
        // Đang kill thì để luồng kill tự xoá file sau khi đọc xong pid
        if (!killing) deleteFiles()
    }

    private fun deleteFiles() {
        rcFile.delete()
        ttyFile.delete()
        pidFile.delete()
    }
}
