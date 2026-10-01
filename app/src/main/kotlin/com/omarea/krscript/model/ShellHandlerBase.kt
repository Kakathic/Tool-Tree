package com.omarea.krscript.model

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Message
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.Locale
import java.util.regex.Pattern

abstract class ShellHandlerBase(
    protected var context: Context
) : Handler() {

    private var stdin: DataOutputStream? = null

    protected abstract fun onProgress(current: Int, total: Int)
    protected abstract fun onStart(msg: Any?)
    abstract fun onStart(forceStop: Runnable?)
    protected abstract fun onExit(msg: Any?)
    protected abstract fun updateLog(msg: SpannableString)

    fun bindStdin(stdin: DataOutputStream) {
        this.stdin = stdin
    }

    fun unbindStdin() {
        this.stdin = null
    }

    fun writeInput(text: String?): Boolean {
        val stdin = this.stdin
        if (stdin == null || text == null) {
            return false
        }
        return try {
            stdin.write(text.toByteArray(StandardCharsets.UTF_8))
            stdin.writeBytes("\n")
            stdin.flush()
            true
        } catch (e: IOException) {
            this.stdin = null
            false
        }
    }

    protected open fun onInputRequest(prompt: String) {
    }

    class ChoiceOption(@JvmField val value: String, @JvmField val label: String)

    protected open fun onChooseRequest(options: List<ChoiceOption>) {
    }

    protected open fun onPickRequest(options: List<ChoiceOption>, vertical: Boolean) {
    }

    private fun parseChooseOptions(content: String?): List<ChoiceOption> {
        val options = ArrayList<ChoiceOption>()
        if (content == null || content.trim().isEmpty()) return options

        for (rawItem in content.split(",")) {
            val item = rawItem.trim()
            if (item.isEmpty()) continue

            val sep = item.indexOf('|')
            if (sep >= 0) {
                val value = item.substring(0, sep).trim()
                val label = item.substring(sep + 1).trim()
                if (value.isNotEmpty()) {
                    options.add(ChoiceOption(value, if (label.isEmpty()) value else label))
                }
            } else {
                options.add(ChoiceOption(item, item))
            }
        }
        return options
    }

    protected open fun onKillRequest() {
    }

    override fun handleMessage(msg: Message) {
        super.handleMessage(msg)
        when (msg.what) {
            EVENT_EXIT -> onExit(msg.obj)
            EVENT_START -> onStart(msg.obj)
            EVENT_REDE -> onReaderMsg(msg.obj)
            EVENT_READ_ERROR -> onError(msg.obj)
            EVENT_WRITE -> onWrite(msg.obj)
        }
    }

    protected open fun onReaderMsg(msg: Any?) {
        if (msg == null) return

        val log = msg.toString()
        val cleanLog = ANSI_ESCAPE_PATTERN.matcher(log).replaceAll("").trim()

        val exitMatcher = EXIT_PATTERN.matcher(cleanLog)
        if (exitMatcher.find()) {
            val args = exitMatcher.group(1)!!.trim().lowercase(Locale.US)
            if (args == "kill") {
                killApp(false)
            } else if (args == "restart") {
                killApp(true)
            }
            return
        }

        val chooseMatcher = CHOOSE_PATTERN.matcher(cleanLog)
        if (chooseMatcher.find()) {
            val options = parseChooseOptions(chooseMatcher.group(1)!!.trim())
            if (options.isNotEmpty()) {
                onChooseRequest(options)
                return
            }
        }

        val pickMatcher = PICK_PATTERN.matcher(cleanLog)
        if (pickMatcher.find()) {
            val options = parseChooseOptions(pickMatcher.group(2)!!.trim())
            if (options.isNotEmpty()) {
                val vertical = "h" != pickMatcher.group(1)
                onPickRequest(options, vertical)
                return
            }
        }

        val amMatcher = AM_PATTERN.matcher(cleanLog)
        if (amMatcher.find()) {
            val args = amMatcher.group(1)!!.trim()
            if (args.equals("help", ignoreCase = true)) {
                updateLog(SpannableString(getAmHelp()))
            } else if (args.isNotEmpty()) {
                onAm(args)
            }
            return
        }

        val inputMatcher = INPUT_PATTERN.matcher(cleanLog)
        if (inputMatcher.find()) {
            val prompt = inputMatcher.group(1)!!.trim()
            onInputRequest(prompt)
            return
        }

        val progressMatcher = PROGRESS_PATTERN.matcher(cleanLog)
        if (progressMatcher.find()) {
            try {
                val content = progressMatcher.group(1)!!.trim()
                val slashIdx = content.indexOf('/')
                if (slashIdx > 0) {
                    val start = content.substring(0, slashIdx).trim().toInt()
                    val total = content.substring(slashIdx + 1).trim().toInt()
                    onProgress(start, total)
                    return
                }
            } catch (e: Exception) {
                updateLog("Format error: $cleanLog", "#ff0000")
                return
            }
        }

        onReader(msg)
    }

    protected open fun onReader(msg: Any?) {
        updateLog(msg, "#00cc55")
    }

    protected open fun onWrite(msg: Any?) {
        updateLog(msg, "#808080")
    }

    protected open fun onError(msg: Any?) {
        updateLog(msg, "#ff0000")
    }

    private fun killApp(restart: Boolean) {
        try {
            onKillRequest()
        } catch (ignored: Exception) {
        } finally {
            if (restart) {
                try {
                    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    if (launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        context.startActivity(launch)
                    }
                } catch (ignored: Exception) {
                }
            }
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(0)
        }
    }

    private fun getAmHelp(): String {
        return "am:[command] syntax:\n\n" +
            "am:[start -a ACTION -d URI -n PACKAGE/CLASS]\n" +
            "am:[startservice -n PACKAGE/CLASS]\n" +
            "am:[foregroundservice -n PACKAGE/CLASS]\n" +
            "am:[broadcast -a ACTION]\n\n" +
            "Extras:\n" +
            "  --es key value    String\n" +
            "  --ei key value    Int\n" +
            "  --ez key value    Boolean\n" +
            "  --el key value    Long\n" +
            "  --ef key value    Float\n" +
            "  --ed key value    Double\n" +
            "  --eu key value    Uri\n" +
            "  --esa key v1 v2   String[]\n" +
            "  --eia key v1 v2   Int[]\n"
    }

    private fun onAm(args: String) {
        val tokens = splitArgs(args)
        if (tokens.isEmpty()) return

        val cmd = tokens[0].lowercase(Locale.US)

        try {
            val intent = parseIntentFromTokens(tokens)

            if (Intent.ACTION_SEND == intent.action || Intent.ACTION_SEND_MULTIPLE == intent.action) {
                var uri: Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)
                if (uri == null) uri = intent.data

                if (uri != null) {
                    intent.putExtra(Intent.EXTRA_STREAM, uri)
                    intent.clipData = ClipData.newRawUri(null, uri)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

            when (cmd) {
                "start" -> {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if ((Intent.ACTION_SEND == intent.action || Intent.ACTION_SEND_MULTIPLE == intent.action) &&
                        intent.component == null
                    ) {
                        val chooser = Intent.createChooser(intent, null)
                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(chooser)
                    } else {
                        context.startActivity(intent)
                    }
                }

                "foregroundservice" -> {
                    if (Build.VERSION.SDK_INT >= 26) {
                        context.startForegroundService(intent)
                    }
                }

                "startservice" -> context.startService(intent)

                "broadcast" -> context.sendBroadcast(intent)
            }
        } catch (e: Exception) {
            updateLog(e.toString(), "#ff0000")
        }
    }

    private fun parseIntentFromTokens(tokens: ArrayList<String>): Intent {
        val intent = Intent()
        var i = 1

        while (i < tokens.size) {
            val token = tokens[i]
            try {
                when (token) {
                    "-a" -> if (i + 1 < tokens.size) intent.action = tokens[++i]

                    "-d" -> if (i + 1 < tokens.size) {
                        val value = stripQuote(tokens[++i])
                        val uri: Uri = if (value.contains("://"))
                            Uri.parse(value)
                        else if (value.startsWith("/"))
                            Uri.fromFile(File(value))
                        else
                            Uri.parse(value)
                        intent.data = uri
                    }

                    "-t" -> if (i + 1 < tokens.size) intent.type = tokens[++i]

                    "-n" -> if (i + 1 < tokens.size) {
                        val cn = tokens[++i].split("/", limit = 2)
                        if (cn.size == 2)
                            intent.component = ComponentName(cn[0], cn[1])
                    }

                    "-p" -> if (i + 1 < tokens.size) intent.setPackage(tokens[++i])

                    "-c" -> if (i + 1 < tokens.size) intent.addCategory(tokens[++i])

                    "-f" -> if (i + 1 < tokens.size) {
                        val v = tokens[++i]
                        val flags = if (v.startsWith("0x"))
                            Integer.parseInt(v.substring(2), 16)
                        else
                            Integer.parseInt(v)
                        intent.addFlags(flags)
                    }

                    "--es" -> if (i + 2 < tokens.size)
                        intent.putExtra(tokens[++i], tokens[++i])

                    "--ei" -> if (i + 2 < tokens.size)
                        intent.putExtra(tokens[++i], Integer.parseInt(tokens[++i]))

                    "--el" -> if (i + 2 < tokens.size)
                        intent.putExtra(tokens[++i], java.lang.Long.parseLong(tokens[++i]))

                    "--ez" -> if (i + 2 < tokens.size)
                        intent.putExtra(tokens[++i], java.lang.Boolean.parseBoolean(tokens[++i]))

                    "--ef" -> if (i + 2 < tokens.size)
                        intent.putExtra(tokens[++i], java.lang.Float.parseFloat(tokens[++i]))

                    "--ed" -> if (i + 2 < tokens.size)
                        intent.putExtra(tokens[++i], java.lang.Double.parseDouble(tokens[++i]))

                    "--eu" -> if (i + 2 < tokens.size) {
                        val key = tokens[++i]
                        val value = stripQuote(tokens[++i])
                        val uri = if (value.contains("://")) Uri.parse(value) else Uri.fromFile(File(value))
                        intent.putExtra(key, uri)
                    }

                    "--esn" -> if (i + 1 < tokens.size)
                        intent.putExtra(tokens[++i], null as String?)

                    "--esa" -> if (i + 1 < tokens.size) {
                        val key = tokens[++i]
                        val list = ArrayList<String>()
                        while (i + 1 < tokens.size && !tokens[i + 1].startsWith("-")) {
                            list.add(tokens[++i])
                        }
                        intent.putExtra(key, list.toTypedArray())
                    }

                    "--eia" -> if (i + 1 < tokens.size) {
                        val key = tokens[++i]
                        val list = ArrayList<Int>()
                        while (i + 1 < tokens.size && !tokens[i + 1].startsWith("-")) {
                            list.add(Integer.parseInt(tokens[++i]))
                        }
                        val arr = IntArray(list.size)
                        for (j in list.indices) arr[j] = list[j]
                        intent.putExtra(key, arr)
                    }

                    "--grant-read-uri-permission" -> intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                    "--grant-write-uri-permission" -> intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
            } catch (e: NumberFormatException) {
                updateLog("Number formatting error at the parameter: $token", "#ff0000")
            }
            i++
        }
        return intent
    }

    private fun stripQuote(s: String): String {
        if (s.length >= 2) {
            val first = s[0]
            val last = s[s.length - 1]
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return s.substring(1, s.length - 1)
            }
        }
        return s
    }

    private fun splitArgs(args: String?): ArrayList<String> {
        val out = ArrayList<String>()
        if (args.isNullOrEmpty()) return out

        val cur = StringBuilder()
        var inQuote = false
        var quoteChar = 0.toChar()

        var i = 0
        while (i < args.length) {
            val c = args[i]

            if (inQuote) {
                if (c == quoteChar) {
                    inQuote = false
                } else if (c == '\\' && i + 1 < args.length) {
                    val n = args[++i]
                    when (n) {
                        'n' -> cur.append('\n')
                        't' -> cur.append('\t')
                        '\\' -> cur.append('\\')
                        '"' -> cur.append('"')
                        '\'' -> cur.append('\'')
                        else -> cur.append(n)
                    }
                } else {
                    cur.append(c)
                }
            } else {
                if (c == '"' || c == '\'') {
                    inQuote = true
                    quoteChar = c
                } else if (Character.isWhitespace(c)) {
                    if (cur.isNotEmpty()) {
                        out.add(cur.toString())
                        cur.setLength(0)
                    }
                } else {
                    cur.append(c)
                }
            }
            i++
        }

        if (cur.isNotEmpty()) {
            out.add(cur.toString())
        }

        return out
    }

    protected fun updateLog(msg: Any?, color: String) {
        if (msg != null) {
            updateLog(msg, Color.parseColor(color))
        }
    }

    protected fun updateLog(msg: Any?, color: Int) {
        if (msg != null) {
            val msgStr = msg.toString()
            val spannableString = SpannableString(msgStr)
            spannableString.setSpan(ForegroundColorSpan(color), 0, msgStr.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            updateLog(spannableString)
        }
    }

    companion object {
        const val EVENT_START = 0
        const val EVENT_REDE = 2
        const val EVENT_READ_ERROR = 4
        const val EVENT_WRITE = 6
        const val EVENT_EXIT = -2

        private val ANSI_ESCAPE_PATTERN: Pattern = Pattern.compile("\\x1B\\[[0-9;]*[a-zA-Z]")
        private val AM_PATTERN: Pattern = Pattern.compile("am:\\[(.*?)\\]")
        private val PROGRESS_PATTERN: Pattern = Pattern.compile("progress:\\[(.*?)\\]")
        private val INPUT_PATTERN: Pattern = Pattern.compile("input:\\[(.*?)\\]")

        private val CHOOSE_PATTERN: Pattern = Pattern.compile("choose:\\[(.*)\\]")

        private val PICK_PATTERN: Pattern = Pattern.compile("pick(v|h)?:\\[(.*)\\]")
        private val EXIT_PATTERN: Pattern = Pattern.compile("exit:\\[(.*?)\\]")
    }
}
