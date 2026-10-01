package com.tool.tree

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat

@Suppress("DEPRECATION")
class WakeLockService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var isWakeLockActive = false
    private val WAKE_LOCK_TAG get() = "com.tool.tree.WAKE_LOCK"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ENABLE_WAKELOCK -> enableWakeLock()
            ACTION_DISABLE_WAKELOCK -> disableWakeLock()
            ACTION_END_WAKELOCK -> endWakeLock()
            ACTION_STOP_SERVICE -> stopWakeLockAndService()
        }
        return START_NOT_STICKY
    }

    private fun enableWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
        }
    
        if (!isWakeLockActive) {
            wakeLock?.acquire()
            isWakeLockActive = true
            updateNotification()
        }
    }
    
    private fun disableWakeLock() {
        if (isWakeLockActive) {
            releaseWakeLockSafely()
            updateNotification()
        }
    }

    private fun endWakeLock() {
        releaseWakeLockSafely()
        stopForegroundInternal()
        stopSelf()
    }

    private fun stopWakeLockAndService() {
        releaseWakeLockSafely()
        stopForegroundInternal()
        stopSelf()
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appTasks = activityManager.appTasks
        appTasks.forEach { task ->
            task.finishAndRemoveTask()
        }
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1, buildNotification())
    }

    override fun onCreate() {
        super.onCreate()
        
        if (isServiceRunning) return
        isServiceRunning = true
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(1, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.wakelock_service_running),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableLights(false)
                enableVibration(false)
                setLockscreenVisibility(Notification.VISIBILITY_SECRET)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
    
    private fun buildNotification(): Notification {
        val wakelockActionText =
            if (isWakeLockActive) getString(R.string.turn_off_wakelock)
            else getString(R.string.turn_on_wakelock)
    
        val action =
            if (isWakeLockActive) ACTION_DISABLE_WAKELOCK
            else ACTION_ENABLE_WAKELOCK
    
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
    
        val contentPendingIntent = launchIntent?.let {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            PendingIntent.getActivity(this, 0, it, flags)
        }

        val appIconBitmap = drawableToBitmap(packageManager.getApplicationIcon(applicationInfo))
        val sender = Person.Builder()
            .setName(getString(R.string.app_name))
            .setIcon(IconCompat.createWithBitmap(appIconBitmap))
            .build()

        val messageText =
            if (isWakeLockActive) getString(R.string.wakelock_service_running)
            else getString(R.string.service_active_with_wakelock)
        val messagingStyle = NotificationCompat.MessagingStyle(sender)
            .addMessage(messageText, System.currentTimeMillis(), sender)
    
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.tab_favorites)
            .setStyle(messagingStyle)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .addAction(R.mipmap.ic_launcher, getString(R.string.stop), createPendingIntent(ACTION_STOP_SERVICE))
            .addAction(R.mipmap.ic_launcher, wakelockActionText, createPendingIntent(action))
    
        contentPendingIntent?.let {
            builder.setContentIntent(it)
        }
    
        return builder.build()
    }

    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, WakeLockService::class.java).apply { this.action = action }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getService(this, action.hashCode(), intent, flags)
    }

    private fun drawableToBitmap(drawable: Drawable?): Bitmap {
        val targetSize = 256
        if (drawable == null) {
            return Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        }
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return Bitmap.createScaledBitmap(drawable.bitmap, targetSize, targetSize, true)
        }
        val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, targetSize, targetSize)
        drawable.draw(canvas)
        return bitmap
    }

    override fun onDestroy() {
        releaseWakeLockSafely()
        isServiceRunning = false
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        releaseWakeLockSafely()
        stopForegroundInternal()
        stopSelf()
    }

    private fun releaseWakeLockSafely() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null
        isWakeLockActive = false
    }

    private fun stopForegroundInternal() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "WakeLockServiceChannel"
        const val ACTION_ENABLE_WAKELOCK = "com.tool.tree.action.ENABLE_WAKELOCK"
        const val ACTION_DISABLE_WAKELOCK = "com.tool.tree.action.DISABLE_WAKELOCK"
        const val ACTION_END_WAKELOCK = "com.tool.tree.action.END_WAKELOCK"
        const val ACTION_STOP_SERVICE = "com.tool.tree.action.STOP_SERVICE"
        private var isServiceRunning = false

        fun startService(context: Context) {
            val intent = Intent(context, WakeLockService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, WakeLockService::class.java))
        }
    }
}