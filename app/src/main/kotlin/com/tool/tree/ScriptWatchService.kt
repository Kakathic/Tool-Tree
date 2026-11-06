package com.tool.tree

import android.app.Service
import android.content.Context
import android.content.Intent
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
        ScriptRegistry.killAll(KILL_WAIT_MS)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        ScriptRegistry.onEmpty = null
        super.onDestroy()
    }
}
