package com.tool.tree

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.omarea.krscript.NotiShellTaskLauncher
import com.omarea.krscript.config.StringResRef
import com.omarea.krscript.model.RunnableNode

class NotiService : Service() {
    private val CHANNEL_ID = "notification_id_am"
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }

    private fun deleteNotification(id: Int) {
        notificationManager?.cancel(id)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getIntExtra("id", 10) ?: 10

        if (intent?.getBooleanExtra("delete", false) == true) {
            deleteNotification(id)
            return START_NOT_STICKY
        }

        if (intent?.getBooleanExtra("execute", false) == true) {
            executeShell(intent, id)
            return START_NOT_STICKY
        }

        val rawMessage = intent?.getStringExtra("message")
        val rawTitle = intent?.getStringExtra("title")

        if (rawMessage != null) {
            val title = if (rawTitle != null) {
                StringResRef.resolve(this, rawTitle)
            } else {
                getString(R.string.app_name)
            }

            val message = StringResRef.resolve(this, rawMessage)

            showNotification(id, message, title, intent)
        }

        return START_NOT_STICKY
    }

    private fun executeShell(intent: Intent, id: Int) {
        val shell = intent.getStringExtra("shell")
        if (shell.isNullOrEmpty()) {
            return
        }

        val rawTitle = intent.getStringExtra("title")
        val title = if (rawTitle != null) {
            StringResRef.resolve(this, rawTitle)
        } else {
            getString(R.string.app_name)
        }

        deleteNotification(id)

        val nodeInfo = RunnableNode("").apply {
            this.title = title
            this.shell = RunnableNode.shellModeBgTask
            this.interruptable = true
        }

        NotiShellTaskLauncher.startTask(
            applicationContext,
            shell,
            nodeInfo
        )
    }

    private fun buildExecutePendingIntent(id: Int, rawTitle: String?, shell: String): PendingIntent {
        val executeIntent = Intent(this, NotiService::class.java).apply {
            putExtra("execute", true)
            putExtra("id", id)
            putExtra("title", rawTitle)
            putExtra("shell", shell)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getService(this, id, executeIntent, flags)
    }

    private fun showNotification(id: Int, message: String, title: String, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                CHANNEL_ID,
                "Notification",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                enableLights(false)
                enableVibration(false)
            }

            notificationManager?.createNotificationChannel(notificationChannel)
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }

        val contentPendingIntent = launchIntent?.let {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            PendingIntent.getActivity(this, 0, it, flags)
        }

        val avatarBitmap = getAvatarBitmap(intent)
        val iconCompat = IconCompat.createWithBitmap(avatarBitmap)

        val sender = Person.Builder()
            .setName(title)
            .setIcon(iconCompat)
            .build()

        val messagingStyle = NotificationCompat.MessagingStyle(sender)
            .addMessage(message, System.currentTimeMillis(), sender)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID).apply {
            setSmallIcon(R.drawable.tab_favorites)
            setStyle(messagingStyle)
            setLargeIcon(avatarBitmap)
            setAutoCancel(true)
            setPriority(NotificationCompat.PRIORITY_HIGH)

            contentPendingIntent?.let {
                setContentIntent(it)
            }
        }

        val shell = intent.getStringExtra("shell")
        if (!shell.isNullOrEmpty()) {
            val executePendingIntent = buildExecutePendingIntent(id, intent.getStringExtra("title"), shell)
            builder.addAction(R.drawable.kr_run, getString(R.string.btn_execute), executePendingIntent)
        }

        notificationManager?.notify(id, builder.build())
    }

    private fun getAvatarBitmap(intent: Intent): Bitmap {
        val targetSize = 200
        var bitmap: Bitmap? = null

        val bitmapExtra = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("large_icon", Bitmap::class.java)
                ?: intent.getParcelableExtra("icon", Bitmap::class.java)
        } else {
            @Suppress("DEPRECATION")
            (intent.getParcelableExtra("large_icon") as? Bitmap)
                ?: (intent.getParcelableExtra("icon") as? Bitmap)
        }

        if (bitmapExtra != null) {
            bitmap = bitmapExtra
        } else {
            var resId = intent.getIntExtra("large_icon", 0)
            if (resId == 0) resId = intent.getIntExtra("icon", 0)

            if (resId != 0) {
                bitmap = drawableToBitmap(ContextCompat.getDrawable(this, resId))
            } else {
                val pathOrUri = intent.getStringExtra("large_icon") ?: intent.getStringExtra("icon")
                if (!pathOrUri.isNullOrEmpty()) {
                    bitmap = loadBitmapFromString(pathOrUri)
                }
            }
        }

        if (bitmap == null) {
            val appDrawable = packageManager.getApplicationIcon(applicationInfo)
            bitmap = drawableToBitmap(appDrawable)
        }

        return Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true)
    }

    private fun String?.isNull_Or_Empty(): Boolean = this.isNullOrEmpty()

    private fun loadBitmapFromString(source: String): Bitmap? {
        return try {
            val fileBitmap = BitmapFactory.decodeFile(source)
            if (fileBitmap != null) return fileBitmap

            val uri = Uri.parse(source)
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable?): Bitmap {
        if (drawable == null) {
            return Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        }
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 200
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 200
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}