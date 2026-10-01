package com.omarea.krscript

import android.content.Context
import com.omarea.krscript.executor.ShellExecutor
import com.omarea.krscript.model.RunnableNode

object NotiShellTaskLauncher {
    private var notificationCounter = 84050

    fun startTask(
        context: Context,
        script: String,
        nodeInfo: RunnableNode,
        onExit: Runnable = Runnable {}
    ) {
        val applicationContext = context.applicationContext
        notificationCounter += 1

        val handler = BgTaskThread.ServiceShellHandler(applicationContext, nodeInfo, notificationCounter)
        ShellExecutor().execute(
            context,
            nodeInfo,
            script,
            {
                try {
                    onExit.run()
                } catch (ex: Exception) {
                }
            },
            null,
            handler
        )
    }
}