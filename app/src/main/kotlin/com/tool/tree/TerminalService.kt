package com.tool.tree

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** Giữ tiến trình sống và hiện thông báo khi còn phiên terminal (kể cả đang ẩn). */
class TerminalService : Service(), TerminalSessionStore.Listener {
    companion object {
        private const val CHANNEL_ID = "terminal_sessions"
        private const val NOTIFICATION_ID = 0x7E01
        const val ACTION_EXIT_ALL = "com.tool.tree.TERMINAL_EXIT_ALL"

        fun ensureStarted(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TerminalService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, getString(R.string.terminal_default_title), NotificationManager.IMPORTANCE_LOW)
            channel.setShowBadge(false)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        TerminalSessionStore.addListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Phải gọi startForeground ngay sau startForegroundService
        startForeground(NOTIFICATION_ID, buildNotification())
        if (intent?.action == ACTION_EXIT_ALL) {
            TerminalSessionStore.killAll()
            stopForeground(true)
            stopSelf()
        } else if (TerminalSessionStore.all().isEmpty()) {
            stopForeground(true)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        TerminalSessionStore.removeListener(this)
        super.onDestroy()
    }

    override fun onSessionsChanged() {
        if (TerminalSessionStore.all().isEmpty()) {
            stopForeground(true)
            stopSelf()
        } else {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, buildNotification())
        }
    }

    override fun onSessionFinished(entry: TerminalSessionStore.Entry) {}

    private fun buildNotification(): Notification {
        val labels = TerminalSessionStore.all().map { it.label }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val openIntent = PendingIntent.getActivity(this, 1, TerminalActivity.openIntent(this), flags)
        val exitIntent = PendingIntent.getService(
            this, 2, Intent(this, TerminalService::class.java).setAction(ACTION_EXIT_ALL), flags
        )

        val text = getString(R.string.terminal_notif_sessions, labels.size)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.icon_terminal)
            .setContentTitle(getString(R.string.terminal_default_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text + "\n" + labels.joinToString("\n")))
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.terminal_notif_open), openIntent)
            .addAction(0, getString(R.string.terminal_notif_exit_all), exitIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
