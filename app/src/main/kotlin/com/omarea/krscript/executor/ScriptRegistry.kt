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
 * Chỉ kill script còn đang chạy (shell gốc và mọi tiến trình con của nó). Daemon đã tách ra
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
     * Shell gốc của mỗi lần chạy = tiến trình mang shell_progres=<tag> mà cha không mang tag đó và cha không phải init;
     * kill nó cùng mọi hậu duệ (theo ppid). Tiến trình mang tag có cha là init (daemon đã tách) không bị đụng tới.
     * § được đổi thành $ để khỏi phải escape trong Kotlin.
     */
    private fun buildScript(busybox: String, tags: String, pids: String): String {
        fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"
        return ("B=${q(busybox)}; TAGS=${q(tags)}; JP=${q(pids)}; ME=§§; all=\"\"\n" +
                "for d in /proc/[0-9]*; do\n" +
                "  pid=§{d#/proc/}; [ \"§pid\" = \"§ME\" ] && continue\n" +
                "  read -r l < §d/stat 2>/dev/null || continue\n" +
                "  set -- §{l##*) }\n" +
                "  eval \"pp_§pid=§2\"\n" +
                "  all=\"§all §pid\"\n" +
                "  §B grep -qaE \"shell_progres=(§TAGS)\" §d/environ 2>/dev/null && eval \"t_§pid=1\"\n" +
                "done\n" +
                "kl=\" \"\n" +
                "for pid in §all; do\n" +
                "  eval \"pt=\\§t_§pid; pa=\\§pp_§pid\"\n" +
                "  [ -n \"§pt\" ] || continue\n" +
                "  eval \"ptp=\\§t_§pa\"\n" +
                "  if [ -z \"§ptp\" ] && [ \"§pa\" != 1 ] && [ \"§pa\" != 0 ]; then kl=\"§kl§pid \"; fi\n" +
                "done\n" +
                "for p in §JP; do case \"§all\" in *\" §p\"*) kl=\"§kl§p \";; esac; done\n" +
                "ch=1\n" +
                "while [ §ch = 1 ]; do\n" +
                "  ch=0\n" +
                "  for pid in §all; do\n" +
                "    case \"§kl\" in *\" §pid \"*) continue;; esac\n" +
                "    eval \"pa=\\§pp_§pid\"\n" +
                "    case \"§kl\" in *\" §pa \"*) kl=\"§kl§pid \"; ch=1;; esac\n" +
                "  done\n" +
                "done\n" +
                "for p in §kl; do kill -HUP §p 2>/dev/null; done\n" +
                "sleep 0.3\n" +
                "for p in §kl; do kill -9 §p 2>/dev/null; done\n").replace('§', '$')
    }
}
