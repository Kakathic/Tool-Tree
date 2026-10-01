package com.tool.tree

import android.content.Context
import android.content.Intent
import android.util.Log

class CrashHandler(context: Context) : Thread.UncaughtExceptionHandler {

    private val context: Context = context.applicationContext
    private val defaultHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        val shownCrashUi = try {
            val stackTrace = Log.getStackTraceString(ex)
            Log.e("CrashHandler", "Uncaught exception in thread: " + thread.name, ex)

            val intent = Intent(context, CrashLogActivity::class.java)
            intent.putExtra("crash_log", stackTrace)
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_CLEAR_TASK
            )

            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            Log.e("CrashHandler", "Failed to handle crash gracefully", t)
            false
        }

        if (shownCrashUi) {
            android.os.Process.killProcess(android.os.Process.myPid())
            kotlin.system.exitProcess(10)
        } else {
            defaultHandler?.uncaughtException(thread, ex)
        }
    }

    companion object {
        @JvmStatic
        fun install(context: Context) {
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(context))
        }
    }
}
