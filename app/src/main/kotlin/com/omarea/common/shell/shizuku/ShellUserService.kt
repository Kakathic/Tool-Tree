package com.omarea.common.shell.shizuku

import java.io.BufferedReader
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.concurrent.locks.ReentrantLock

class ShellUserService : IShellUserService.Stub() {
    private var process: Process? = null
    private var out: OutputStream? = null
    private var reader: BufferedReader? = null
    private val lock = ReentrantLock()

    private val startTag = "|SH>>|"
    private val endTag = "|<<SH|"
    private val startTagBytes = "\necho '$startTag'\n".toByteArray(Charset.defaultCharset())
    private val endTagBytes = "\necho '$endTag'\n".toByteArray(Charset.defaultCharset())

    private fun ensureProcess() {
        if (process != null) return
        process = ProcessBuilder("sh").redirectErrorStream(true).start()
        out = process!!.outputStream
        reader = process!!.inputStream.bufferedReader()
    }

    override fun execCommand(cmd: String?): String {
        if (cmd == null) return ""
        lock.lock()
        try {
            ensureProcess()
            val output = StringBuilder()
            out?.let {
                it.write(startTagBytes)
                it.write(cmd.toByteArray(Charset.defaultCharset()))
                it.write(endTagBytes)
                it.flush()
            }

            var unstart = true
            while (reader != null) {
                val line = reader!!.readLine() ?: break
                if (line.contains(endTag)) {
                    output.append(line.substringBefore(endTag))
                    break
                } else if (line.contains(startTag)) {
                    output.clear()
                    output.append(line.substring(line.indexOf(startTag) + startTag.length))
                    unstart = false
                } else if (!unstart) {
                    output.append(line)
                    output.append("\n")
                }
            }
            return output.toString().trim()
        } catch (e: Exception) {
            try {
                process?.destroy()
            } catch (_: Exception) {
            }
            process = null
            out = null
            reader = null
            return "error"
        } finally {
            lock.unlock()
        }
    }

    override fun destroy() {
        lock.lock()
        try {
            out?.close()
            reader?.close()
            process?.destroy()
        } catch (_: Exception) {
        } finally {
            lock.unlock()
        }
        System.exit(0)
    }
}
