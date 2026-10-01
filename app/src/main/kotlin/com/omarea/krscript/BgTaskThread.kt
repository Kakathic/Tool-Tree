package com.omarea.krscript

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.omarea.common.ui.DialogHelper
import com.omarea.krscript.config.IconPathAnalysis
import com.omarea.krscript.executor.ShellExecutor
import com.omarea.krscript.model.RunnableNode
import com.omarea.krscript.model.ShellHandlerBase
import com.tool.tree.AnsiColorParser
import com.tool.tree.R

class BgTaskThread(private var process: Process) : Thread() {
    override fun run() {
        try {
            process.waitFor()
        } catch (ex: java.lang.Exception) {
        }
    }

    class ServiceShellHandler(
        context: Context,
        private val runnableNode: RunnableNode,
        private val notificationID: Int
    ) : ShellHandlerBase(context) {

        private var notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        private val notificationTitle = runnableNode.title
        private var notificationMessageRows = ArrayList<String>()
        private var progressCurrent = 0
        private var progressTotal = 0
        private var someIgnored = false
        private var forceStop: Runnable? = null
        private var isFinished = false

        private val notificationHandler = Handler(Looper.getMainLooper())
        private var pendingNotificationUpdate = false
        private val updateNotificationRunnable = Runnable {
            pendingNotificationUpdate = false
            updateNotificationInternal()
        }

        private var isReceiverRegistered = false
        private var STOP_CLICK_ACTION_NAME = context.packageName + ".TaskStop." + "N" + notificationID
        private val stopIntent = PendingIntent.getBroadcast(
            context, 0, Intent(STOP_CLICK_ACTION_NAME).apply {
                putExtra("id", notificationID)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent != null && intent.hasExtra("id")) {
                    if (intent.getIntExtra("id", 0) == notificationID) {
                        forceStop?.run()
                    }
                }
            }
        }

        private var COPY_CLICK_ACTION_NAME = context.packageName + ".TaskCopyLog." + "N" + notificationID
        private val copyIntent = PendingIntent.getBroadcast(
            context, 0, Intent(COPY_CLICK_ACTION_NAME).apply {
                putExtra("id", notificationID)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        private val copyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent != null && intent.hasExtra("id") && intent.getIntExtra("id", 0) == notificationID) {
                    copyLogToClipboard()
                }
            }
        }

        private fun cleanupReceivers() {
            if (isReceiverRegistered) {
                runCatching { context.unregisterReceiver(receiver) }
                runCatching { context.unregisterReceiver(copyReceiver) }
                isReceiverRegistered = false
            }
        }

        private fun copyLogToClipboard() {
            val text = synchronized(notificationMessageRows) { notificationMessageRows.joinToString("") }.trim()
            runCatching {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("text", text))
                Toast.makeText(context, R.string.copy_success, Toast.LENGTH_SHORT).show()
            }
        }

        private fun buildContentPendingIntent(): PendingIntent? {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            } ?: return null
            return PendingIntent.getActivity(
                context, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun drawableToIcon(drawable: Drawable?, targetSizePx: Int = 200): Icon? {
            if (drawable == null) return null
            val bitmap = Bitmap.createBitmap(targetSizePx, targetSizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, targetSizePx, targetSizePx)
            drawable.draw(canvas)
            return Icon.createWithBitmap(bitmap)
        }

        private fun updateNotificationInternal() {
            synchronized(notificationMessageRows) {
                if (notificationMessageRows.size > 20) {
                    notificationMessageRows.removeAt(0)
                    someIgnored = true
                }
            }

            val shortLog = synchronized(notificationMessageRows) {
                notificationMessageRows.lastOrNull()?.trim().orEmpty()
            }

            val personIcon = (if (runnableNode.iconPath.isNotEmpty() || runnableNode.logoPath.isNotEmpty()) {
                val drawable = IconPathAnalysis().loadLogo(context, runnableNode, false)
                drawableToIcon(drawable, 200)
            } else {
                drawableToIcon(ContextCompat.getDrawable(context, R.drawable.kr_shortcut_logo), 200)
            }) ?: Icon.createWithResource(context, R.drawable.kr_shortcut_logo)

            val sender = android.app.Person.Builder()
                .setName(notificationTitle)
                .setIcon(personIcon)
                .build()

            val messagingStyle = Notification.MessagingStyle(sender)

            val rows = synchronized(notificationMessageRows) { notificationMessageRows.toList() }
            if (someIgnored) {
                messagingStyle.addMessage(Notification.MessagingStyle.Message("……", System.currentTimeMillis(), sender))
            }
            rows.forEach { row ->
                messagingStyle.addMessage(Notification.MessagingStyle.Message(row.trim(), System.currentTimeMillis(), sender))
            }

            val notificationBuilder = Notification.Builder(context, channelId)
                .setContentTitle(notificationTitle)
                .setContentText(shortLog)
                .setSmallIcon(R.drawable.kr_run)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
                .setStyle(messagingStyle)

            if (progressTotal != progressCurrent) {
                notificationBuilder.setProgress(progressTotal, progressCurrent, progressTotal < 0)
            }

            buildContentPendingIntent()?.let { notificationBuilder.setContentIntent(it) }

            if (runnableNode.interruptable && forceStop != null && !isFinished) {
                notificationBuilder.addAction(R.drawable.kr_cancel, context.getString(R.string.btn_cancel), stopIntent)
            }
            notificationBuilder.addAction(R.drawable.kr_copy, context.getString(R.string.btn_copy_output), copyIntent)

            if (!channelCreated) {
                val channel = NotificationChannel(
                    channelId,
                    context.getString(R.string.kr_script_task_notification),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                channel.enableLights(false)
                channel.enableVibration(false)
                channel.setSound(null, null)
                notificationManager.createNotificationChannel(channel)
                channelCreated = true
            }
            notificationBuilder.setChannelId(channelId)

            val notification = notificationBuilder.build()

            if (!isFinished) {
                notification.flags = Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT
            }

            notificationManager.notify(notificationID, notification)
        }

        private fun updateNotification() {
            if (!pendingNotificationUpdate) {
                pendingNotificationUpdate = true
                notificationHandler.postDelayed(updateNotificationRunnable, 300)
            }
        }

        private fun updateNotificationImmediately() {
            notificationHandler.removeCallbacks(updateNotificationRunnable)
            pendingNotificationUpdate = false
            updateNotificationInternal()
        }

        override fun updateLog(msg: SpannableString) {}

        override fun onReader(msg: Any?) {
            val text = AnsiColorParser.stripToPlainText(msg?.toString())
            if (text.isNotEmpty()) {
                synchronized(notificationMessageRows) {
                    notificationMessageRows.add(text)
                }
                updateNotification()
            }
        }

        override fun onError(msg: Any?) {
            val text = AnsiColorParser.stripToPlainText(msg?.toString())
            if (text.isNotEmpty()) {
                synchronized(notificationMessageRows) {
                    notificationMessageRows.add(text)
                }
                updateNotification()
            }
        }

        override fun onWrite(msg: Any?) {}

        override fun onExit(msg: Any?) {
            isFinished = true
            synchronized(notificationMessageRows) {
                if (msg == 0) {
                    notificationMessageRows.add(context.getString(R.string.kr_shell_completed) + "\n")
                } else {
                    notificationMessageRows.add("${context.getString(R.string.kr_shell_finish_error)}\n")
                }
            }
            updateNotificationImmediately()
            
            cleanupReceivers()
        }

        override fun onStart(forceStop: Runnable?) {
            this.forceStop = forceStop
            runCatching {
                ContextCompat.registerReceiver(
                    context, receiver, IntentFilter(STOP_CLICK_ACTION_NAME), ContextCompat.RECEIVER_NOT_EXPORTED
                )
                ContextCompat.registerReceiver(
                    context, copyReceiver, IntentFilter(COPY_CLICK_ACTION_NAME), ContextCompat.RECEIVER_NOT_EXPORTED
                )
                isReceiverRegistered = true
            }

            updateNotificationImmediately()
        }

        override fun onStart(msg: Any?) {}

        override fun onProgress(current: Int, total: Int) {
            progressCurrent = current
            progressTotal = total
            updateNotification()
        }
    }

    companion object {
        private var channelCreated = false
        private const val channelId = "kr_script_task_notification"
        private var notificationCounter = 34050

        fun startTask(
            context: Context,
            script: String,
            params: HashMap<String, String>?,
            nodeInfo: RunnableNode,
            onExit: Runnable,
            onDismiss: Runnable
        ) {
            val applicationContext = context.applicationContext
            notificationCounter += 1

            val handler = ServiceShellHandler(applicationContext, nodeInfo, notificationCounter)
            ShellExecutor().execute(
                context,
                nodeInfo,
                script,
                {
                    try {
                        onExit.run()
                        onDismiss.run()
                    } catch (ex: Exception) {
                    }
                },
                params,
                handler
            )

            val bundle = Bundle()
            params?.run {
                bundle.putSerializable("params", params)
            }
            DialogHelper.helpInfo(
                context,
                context.getString(R.string.kr_bg_task_start),
                context.getString(R.string.kr_bg_task_start_desc)
            )
        }
    }
}