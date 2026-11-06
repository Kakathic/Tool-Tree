package com.tool.tree

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.omarea.krscript.executor.ScriptRegistry

/**
 * Service nhẹ (không foreground) chỉ để nhận onTaskRemoved: khi app bị vuốt khỏi recents
 * mà còn script đang chạy thì kill chúng. Tự dừng khi không còn script nào.
 */
class ScriptWatchService : Service() {
    companion object {
        private const val KILL_WAIT_MS = 3000L

        fun ensureStarted(context: Context) {
            try {
                context.startService(Intent(context, ScriptWatchService::class.java))
            } catch (ignored: Throwable) {
                // Có thể bị chặn khi app đang ở nền (Android 8+): bỏ qua, không ảnh hưởng việc chạy script
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ScriptRegistry.onEmpty = { stopSelf() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!ScriptRegistry.hasRuns()) stopSelf()
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // App có nhiều task (Terminal singleTask, trang mở bằng NEW_TASK...): chỉ kill khi đã hết task giao diện,
        // tránh kill script khi người dùng chỉ vuốt bỏ một task phụ trong lúc task chính vẫn đang dùng
        if (!hasOtherUiTask(rootIntent)) {
            ScriptRegistry.killAll(KILL_WAIT_MS)
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun hasOtherUiTask(removed: Intent?): Boolean {
        return try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.appTasks.any { task ->
                val info = task.taskInfo ?: return@any false
                val base = info.baseIntent
                val excluded = base != null && (base.flags and Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) != 0
                val sameAsRemoved = removed?.component != null && base?.component == removed.component
                val empty = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info.numActivities <= 0
                !excluded && !sameAsRemoved && !empty
            }
        } catch (ignored: Throwable) {
            false
        }
    }

    override fun onDestroy() {
        ScriptRegistry.onEmpty = null
        super.onDestroy()
    }
}
