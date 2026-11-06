package com.omarea.krscript.executor

import android.content.Context
import android.os.Build
import com.tool.tree.ScriptWatchService
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Theo dõi các script (action/switch/picker/download/chat/bg-task...) đang chạy qua [ShellExecutor]
 * để kill chúng khi thoát app (vuốt khỏi recents, crash, exit:[kill]/[restart]).
 *
 * Chỉ kill script còn đang chạy (executor gốc và mọi tiến trình con của nó). Daemon đã tách ra
 * (nohup/setsid, đã mồ côi về init) được giữ nguyên.
 */
object ScriptRegistry {
    private class Run(val tag: String, val process: Process, val pid: Int)

    private val runs = CopyOnWriteArrayList<Run>()

    @Volatile
    private var appContext: Context? = null

    /** Gọi khi không còn script nào đang chạy (ScriptWatchService dùng để tự dừng) */
    @Volatile
    var onEmpty: (() -> Unit)? = null

    fun hasRuns(): Boolean = runs.isNotEmpty()

    fun register(context: Context, tag: String, process: Process) {
        val app = context.applicationContext ?: context
        appContext = app
        val run = Run(tag, process, pidOf(process))
        runs.add(run)
        ScriptWatchService.ensureStarted(app)
        Thread {
            try {
                process.waitFor()
            } catch (ignored: Exception) {
            }
            runs.remove(run)
            if (runs.isEmpty()) onEmpty?.invoke()
        }.also { it.isDaemon = true }.start()
    }

    private fun pidOf(p: Process): Int = try {
        if (Build.VERSION.SDK_INT >= 26) {
            p.pid().toInt()
        } else {
            val f = p.javaClass.getDeclaredField("pid")
            f.isAccessible = true
            f.getInt(p)
        }
    } catch (ignored: Throwable) {
        -1
    }

    /** Dùng ngay trước khi tự kill tiến trình app: không bao giờ ném lỗi */
    fun killAllBeforeExit(waitMs: Long = 1500) {
        try {
            killAll(waitMs)
        } catch (ignored: Throwable) {
        }
    }

    /** Kill mọi script đang chạy; [waitMs] > 0: chờ (tối đa) cho việc kill xong */
    fun killAll(waitMs: Long = 0) {
        val snapshot = runs.toList()
        val context = appContext
        if (snapshot.isEmpty() || context == null) return

        val busybox = File(context.filesDir, "home/bin/busybox").absolutePath
        val tags = snapshot.joinToString("|") { it.tag }
        val pids = snapshot.filter { it.pid > 0 }.joinToString(" ") { it.pid.toString() }
        val script = buildScript(busybox, tags, pids)

        val worker = Thread {
            try {
                val pb = if (ScriptEnvironmen.isRooted()) ProcessBuilder("su", "-c", script) else ProcessBuilder("sh", "-c", script)
                val proc = pb.redirectErrorStream(true).start()
                proc.outputStream.close()
                proc.inputStream.readBytes()
                proc.waitFor()
            } catch (ignored: Exception) {
            }
            snapshot.forEach { run ->
                try {
                    run.process.destroy()
                } catch (ignored: Exception) {
                }
            }
        }
        worker.start()
        if (waitMs > 0) {
            try {
                worker.join(waitMs)
            } catch (ignored: InterruptedException) {
            }
        }
    }

    /**
     * Mỗi lần chạy có một tiến trình executor (cha trực tiếp của các lệnh) mà argv chứa đúng tag, ví dụ
     * "executor.sh <file> pio_123". Gốc kill = executor đó + tiến trình Java đã tạo (su/sh, phải là con trực tiếp của app,
     * chống trùng pid). Kill gốc cùng mọi hậu duệ theo ppid, theo thứ tự cha trước con (shell chết trước nên không kịp chạy lệnh kế tiếp). Daemon đã mồ côi (cha là init) không phải hậu duệ nên được giữ.
     * Chỉ khớp argv đúng bằng tag nên không dính tiến trình dọn dẹp (tag nằm lẫn trong chuỗi script dài).
     * § được đổi thành $ để khỏi phải escape trong Kotlin.
     */
    private fun buildScript(busybox: String, tags: String, pids: String): String {
        fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"
        return ("B=${q(busybox)}; TAGS=${q(tags)}; JP=${q(pids)}; APP=${android.os.Process.myPid()}; ME=§§; kl=\" \"\n" +
                "scan() {\n" +
                "  all=\"\"; es=\" \"\n" +
                "  for d in /proc/[0-9]*; do\n" +
                "    pid=§{d#/proc/}; [ \"§pid\" = \"§ME\" ] && continue\n" +
                "    read -r l < §d/stat 2>/dev/null || continue\n" +
                "    set -- §{l##*) }\n" +
                "    eval \"pp_§pid=§2\"\n" +
                "    all=\"§all §pid\"\n" +
                "    case \"§kl\" in *\" §pid \"*) continue;; esac\n" +
                "    if §B grep -qa \"pio_\" §d/cmdline 2>/dev/null; then\n" +
                "      §B tr '\\0' '\\n' < §d/cmdline 2>/dev/null | §B grep -qxE \"(§TAGS)\" && es=\"§es§pid \"\n" +
                "    fi\n" +
                "  done\n" +
                "}\n" +
                "grow() {\n" +
                "  ch=1\n" +
                "  while [ §ch = 1 ]; do\n" +
                "    ch=0\n" +
                "    for pid in §all; do\n" +
                "      case \"§kl\" in *\" §pid \"*) continue;; esac\n" +
                "      eval \"pa=\\§pp_§pid\"\n" +
                "      case \"§kl\" in *\" §pa \"*) kl=\"§kl§pid \"; ch=1;; esac\n" +
                "    done\n" +
                "  done\n" +
                "}\n" +
                "scan\n" +
                "for p in §JP; do eval \"pa=\\§pp_§p\"; [ \"§pa\" = \"§APP\" ] && kl=\"§kl§p \"; done\n" +
                "kl=\"§kl§{es# }\"\n" +
                "grow\n" +
                "for p in §kl; do kill -9 §p 2>/dev/null; done\n").replace('§', '$')
    }
}
