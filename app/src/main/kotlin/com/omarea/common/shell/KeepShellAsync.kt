package com.omarea.common.shell

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class KeepShellAsync(private var context: Context?, private var rootMode: Boolean = true) : ShellEvents() {
    companion object {
        private val keepShells = HashMap<String, KeepShellAsync>()
        fun getInstance(key: String): KeepShellAsync {
            synchronized(keepShells) {
                if (!keepShells.containsKey(key)) {
                    keepShells[key] = KeepShellAsync(null)
                }
                return keepShells.get(key)!!
            }
        }

        fun destoryInstance(key: String) {
            synchronized(keepShells) {
                if (!keepShells.containsKey(key)) {
                    return
                } else {
                    val keepShell = keepShells.get(key)!!
                    keepShells.remove(key)
                    keepShell.tryExit()
                }
            }
        }

        fun destoryAll() {
            synchronized(keepShells) {
                while (keepShells.isNotEmpty()) {
                    val key = keepShells.keys.first()
                    val keepShell = keepShells.get(key)!!
                    keepShells.remove(key)
                    keepShell.tryExit()
                }
            }
        }
    }

    private var p: Process? = null
    private var out: BufferedWriter? = null
    private var handler: Handler = Handler(Looper.getMainLooper())
    private val mLock = ReentrantLock()

    fun setHandler(handler: Handler) {
        this.processHandler = handler
    }

    private fun showMsg(msg: String) {
        try {
            if (context != null)
                handler.post {
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
        } catch (ex: Exception) {
        }
    }

    fun tryExit() {
        mLock.withLock {
            try {
                out?.close()
            } catch (ex: Exception) {
            }
            out = null
            try {
                p?.destroy()
            } catch (ex: Exception) {
            }
            p = null
        }
    }

    private val GET_ROOT_TIMEOUT = 20000L
    private var threadStarted = false
    private var cmdsCache = StringBuilder()

    private fun getRuntimeShell(cmd: String?, error: Runnable?) {
        val shouldStartThread = mLock.withLock {
            if (threadStarted) {
                if (cmd != null) {
                    cmdsCache.append(cmd)
                    cmdsCache.append("\n\n")
                }
                false
            } else {
                threadStarted = true
                true
            }
        }
        if (!shouldStartThread) return

        val thread = Thread {
            var errorReported = false
            fun reportError() {
                if (!errorReported) {
                    errorReported = true
                    error?.run()
                }
            }

            try {
                tryExit()
                val newProcess =
                    if (rootMode) ShellExecutor.getSuperUserRuntime() else ShellExecutor.getRuntime()

                mLock.withLock { p = newProcess }

                if (processHandler != null) {
                    processHandler!!.sendMessage(processHandler!!.obtainMessage(PROCESS_EVENT_STAR))
                }
                if (newProcess != null) {
                    Thread {
                        val bufferedreader = BufferedReader(InputStreamReader(newProcess.inputStream))
                        try {
                            while (true) {
                                val line = bufferedreader.readLine() ?: break
                                processHandler?.sendMessage(
                                    processHandler!!.obtainMessage(PROCESS_EVENT_CONTENT, line)
                                )
                            }
                        } catch (ex: Exception) {
                        } finally {
                            try { bufferedreader.close() } catch (ex: Exception) {}
                        }
                    }.apply { isDaemon = true }.start()
                    Thread {
                        val bufferedreader = BufferedReader(InputStreamReader(newProcess.errorStream))
                        try {
                            while (true) {
                                val line = bufferedreader.readLine() ?: break
                                processHandler?.sendMessage(
                                    processHandler!!.obtainMessage(PROCESS_EVENT_ERROR_CONTENT, line)
                                )
                            }
                        } catch (ex: Exception) {
                        } finally {
                            try { bufferedreader.close() } catch (ex: Exception) {}
                        }
                    }.apply { isDaemon = true }.start()

                    val newOut = newProcess.outputStream.bufferedWriter()
                    mLock.withLock { out = newOut }

                    if (cmd != null) {
                        mLock.withLock {
                            out?.write(cmd)
                            out?.write("\n\n")
                            if (cmdsCache.isNotEmpty()) {
                                out?.write(cmdsCache.toString())
                                cmdsCache = StringBuilder()
                            }
                            out?.flush()
                        }
                    }
                } else {
                    reportError()
                }
            } catch (e: Exception) {
                val hasOut = mLock.withLock { out } != null
                if (!hasOut) {
                    reportError()
                } else {
                    showMsg("Failed to obtain ROOT privileges!")
                }
            } finally {
                mLock.withLock { threadStarted = false }
            }
        }
        thread.isDaemon = true
        thread.start()
        handler.postDelayed({
            val stillNoProcess = mLock.withLock { p == null }
            if (stillNoProcess && thread.isAlive && !thread.isInterrupted) {
                thread.interrupt()
                tryExit()
                mLock.withLock { threadStarted = false }
                if (error != null) {
                    error.run()
                } else {
                    showMsg("Root access granted timed out!")
                }
            }
        }, GET_ROOT_TIMEOUT)
    }

    fun doCmd(cmd: String, isRedo: Boolean = false) {
        try {
            val needNewShell = mLock.withLock { p == null || isRedo || out == null }
            if (needNewShell) {
                getRuntimeShell(cmd) {
                    if (!isRedo)
                        doCmd(cmd, true)
                    else
                        showMsg("Failed execution action!\nError message : Unable to obtain Root permissions\n\n\ncommand : \r\n$cmd")
                }
            } else {
                mLock.withLock {
                    out?.write(cmd)
                    out?.write("\n\n")
                    out?.flush()
                }
            }
        } catch (e: IOException) {
            if (!isRedo)
                doCmd(cmd, true)
            else
                showMsg("Failed execution action!\nError message : " + e.message + "\n\n\ncommand : \r\n" + cmd)
        }
    }
}
