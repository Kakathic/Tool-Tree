package com.omarea.krscript.executor

import android.content.Context
import com.omarea.common.shell.ShellTranslation
import com.omarea.krscript.model.ShellHandlerBase
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class SimpleShellWatcher {

    private class LogEntry(val timestamp: Long, val what: Int, val message: String) : Comparable<LogEntry> {
        val sequenceNumber: Long = SEQUENCE_GENERATOR.incrementAndGet()

        override fun compareTo(other: LogEntry): Int {
            val timeCompare = timestamp.compareTo(other.timestamp)
            if (timeCompare != 0) {
                return timeCompare
            }
            return sequenceNumber.compareTo(other.sequenceNumber)
        }

        companion object {
            val SEQUENCE_GENERATOR = AtomicLong(0)
        }
    }

    private class StreamState {
        val partial = StringBuilder()
    }

    private fun readStreamToQueue(
        inputStream: InputStream, eventType: Int,
        queue: PriorityBlockingQueue<LogEntry>,
        shellTranslation: ShellTranslation,
        streamRunning: AtomicBoolean
    ) {
        val state = StreamState()
        val chunk = CharArray(READ_CHUNK_SIZE)

        try {
            InputStreamReader(inputStream, StandardCharsets.UTF_8).use { isr ->
                var n: Int
                while (isr.read(chunk, 0, chunk.size).also { n = it } != -1) {
                    processChunkToQueue(chunk, n, state, eventType, queue, shellTranslation)
                }
                flushPartialToQueue(state, eventType, queue, shellTranslation, true)
            }
        } catch (e: IOException) {
            flushPartialToQueue(state, eventType, queue, shellTranslation, true)
        } finally {
            streamRunning.set(false)
        }
    }

    private fun processChunkToQueue(
        chunk: CharArray, len: Int, state: StreamState, what: Int,
        queue: PriorityBlockingQueue<LogEntry>,
        shellTranslation: ShellTranslation
    ) {
        var lastIdx = 0
        for (i in 0 until len) {
            val c = chunk[i]
            if (c == '\n' || c == '\r') {
                state.partial.append(chunk, lastIdx, (i - lastIdx) + 1)
                val segment = state.partial.toString()
                state.partial.setLength(0)
                val resolved = shellTranslation.resolveRow(segment)

                queue.put(LogEntry(System.nanoTime(), what, resolved))
                lastIdx = i + 1
            }
        }
        if (lastIdx < len) {
            state.partial.append(chunk, lastIdx, len - lastIdx)
        }
    }

    private fun flushPartialToQueue(
        state: StreamState, what: Int,
        queue: PriorityBlockingQueue<LogEntry>,
        shellTranslation: ShellTranslation,
        forceTrailingNewline: Boolean
    ) {
        if (state.partial.isEmpty()) return
        val text = state.partial.toString()
        state.partial.setLength(0)
        var resolved = shellTranslation.resolveRow(text)
        if (forceTrailingNewline && !resolved.endsWith("\n") && !resolved.endsWith("\r")) {
            resolved = "$resolved\n"
        }
        queue.put(LogEntry(System.nanoTime(), what, resolved))
    }

    private fun consumeQueue(
        queue: PriorityBlockingQueue<LogEntry>,
        shellHandlerBase: ShellHandlerBase,
        stdoutRunning: AtomicBoolean,
        stderrRunning: AtomicBoolean
    ) {
        try {
            while (stdoutRunning.get() || stderrRunning.get() || !queue.isEmpty()) {
                val entry = queue.poll()
                if (entry != null) {
                    shellHandlerBase.sendMessage(
                        shellHandlerBase.obtainMessage(entry.what, entry.message)
                    )
                } else if (stdoutRunning.get() || stderrRunning.get()) {
                    try {
                        Thread.sleep(POLL_INTERVAL_MS)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }

            var remaining: LogEntry?
            while (queue.poll().also { remaining = it } != null) {
                shellHandlerBase.sendMessage(
                    shellHandlerBase.obtainMessage(remaining!!.what, remaining!!.message)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setHandler(
        context: Context, process: Process,
        shellHandlerBase: ShellHandlerBase,
        onExit: Runnable?
    ) {
        val shellTranslation = ShellTranslation(context)
        val inputStream = process.inputStream
        val errorStream = process.errorStream

        val logQueue = PriorityBlockingQueue<LogEntry>(QUEUE_INITIAL_CAPACITY)

        val stdoutRunning = AtomicBoolean(true)
        val stderrRunning = AtomicBoolean(true)

        val readerOut = Thread({
            readStreamToQueue(
                inputStream, ShellHandlerBase.EVENT_REDE,
                logQueue, shellTranslation, stdoutRunning
            )
        }, "ShellStdoutReader")

        val readerErr = Thread({
            readStreamToQueue(
                errorStream, ShellHandlerBase.EVENT_READ_ERROR,
                logQueue, shellTranslation, stderrRunning
            )
        }, "ShellStderrReader")

        val consumer = Thread({
            consumeQueue(logQueue, shellHandlerBase, stdoutRunning, stderrRunning)
        }, "ShellLogConsumer")

        val waitExit = Thread({
            var status = -1
            try {
                status = process.waitFor()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                try {
                    Thread.sleep(50)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                }

                shellHandlerBase.sendMessage(
                    shellHandlerBase.obtainMessage(ShellHandlerBase.EVENT_EXIT, status)
                )

                if (readerOut.isAlive) {
                    readerOut.interrupt()
                }
                if (readerErr.isAlive) {
                    readerErr.interrupt()
                }

                if (consumer.isAlive) {
                    try {
                        consumer.join(1000)
                    } catch (e: InterruptedException) {
                        consumer.interrupt()
                    }
                }

                onExit?.run()
            }
        }, "ShellWaiter-Thread")

        consumer.start()
        readerOut.start()
        readerErr.start()
        waitExit.start()
    }

    companion object {
        private const val READ_CHUNK_SIZE = 1024
        private const val POLL_INTERVAL_MS = 40L
        private const val QUEUE_INITIAL_CAPACITY = 256
    }
}
