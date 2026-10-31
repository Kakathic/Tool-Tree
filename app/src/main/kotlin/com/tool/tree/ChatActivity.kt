package com.tool.tree

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ListPopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.io.InputStreamReader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.omarea.krscript.config.IconPathAnalysis
import com.omarea.krscript.config.ActionParamMemory
import com.omarea.krscript.executor.ScriptEnvironmen
import com.omarea.krscript.executor.ShellExecutor
import com.omarea.krscript.model.ChatNode
import com.omarea.krscript.model.ClickableNode
import com.omarea.krscript.model.KrScriptActionHandler
import com.omarea.krscript.model.PageMenuOption
import com.omarea.krscript.model.PageNode
import com.omarea.krscript.model.RunnableNode
import com.omarea.krscript.model.SilentShellOutputHandler
import com.omarea.krscript.ui.ActionListFragment
import com.omarea.krscript.ui.DialogLogFragment
import com.omarea.krscript.ui.ParamsFileChooserRender
import com.tool.tree.ui.PopupMenuListAdapter
import com.tool.tree.ui.PopupMenuRow
import com.tool.tree.ui.PopupRowTypeIcon
import com.tool.tree.ui.SpinnerPopupHelper

class ChatActivity : AppCompatActivity(), KrScriptActionHandler {
    companion object {
        private const val EXTRA_CHAT = "chat"
        private const val MAX_REPLY_CHARS = 20000
        private const val SESSION_MAX_CHARS = 60000
        private const val SESSION_COLS = 160
        private const val SESSION_ROWS = 24
        private const val PROMPT_IDLE_MS = 500L
        private const val SESSION_DEFAULT_HINT = "shell~$ "
        private const val REFRESH_DELAY_MS = 60L
        private val CLOSE_TYPES = setOf("exit", "finish", "close")
        private val UNSUPPORTED_TYPES = setOf("file", "folder", "spinner", "checkbox", "refresh", "reload", "restart", "killapp")

        fun start(context: Context, node: ChatNode) {
            val intent = Intent(context, ChatActivity::class.java).apply {
                putExtra(EXTRA_CHAT, node)
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private lateinit var node: ChatNode
    private lateinit var adapter: ChatAdapter
    private lateinit var listView: RecyclerView
    private lateinit var inputView: EditText
    private lateinit var sendButton: ImageButton
    private var actionHost: ActionListFragment? = null
    private val uiHandler = Handler(Looper.getMainLooper())

    private val chatParams = HashMap<String, String>()
    private var running = false
    private var stopRunnable: Runnable? = null
    private var currentHandler: ChatShellHandler? = null
    private var refreshPending = false
    private var refreshTarget: ChatMessage? = null

    private var sessionMessage: ChatMessage? = null
    private var sessionWriter: OutputWriter? = null
    private var sessionLauncher: TerminalLauncher? = null
    private var sessionProcess: Process? = null
    private var sessionGen = 0
    private var sessionAlive = false
    private var sessionBusy = false
    private val sessionExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val promptCheck = Runnable { onPromptIdle() }

    @Suppress("DEPRECATION")
    private fun readNode(): ChatNode? = intent?.extras?.getSerializable(EXTRA_CHAT) as? ChatNode

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val chat = readNode()
        if (chat == null || !ScriptEnvironmen.isInited()) {
            finish()
            return
        }
        node = chat

        ThemeModeState.switchTheme(this)
        setContentView(R.layout.activity_chat)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        title = node.chatTitle.ifEmpty { node.title }.ifEmpty { getString(R.string.chat_default_title) }
        supportActionBar?.apply {
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_arrow_back)
        }
        toolbar.setNavigationOnClickListener { finish() }

        node.paramsNode?.let { setupParams(it) }

        val fragment = ActionListFragment.create(ArrayList(), this, null, ThemeModeState.getThemeMode())
        supportFragmentManager.beginTransaction().replace(R.id.chat_action_host, fragment).commitNowAllowingStateLoss()
        actionHost = fragment

        adapter = ChatAdapter(this) { text -> copyText(text) }
        listView = findViewById(R.id.chat_list)
        listView.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        listView.adapter = adapter
        listView.itemAnimator = null
        listView.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top < oldBottom - oldTop) scrollToEnd()
        }
        addGreeting()

        inputView = findViewById(R.id.chat_input)
        inputView.hint = if (node.session) sessionHint() else node.placeholder.ifEmpty { getString(R.string.chat_input_hint) }
        inputView.maxLines = node.maxLines
        sendButton = findViewById(R.id.chat_send)
        sendButton.setOnClickListener {
            when {
                node.session -> onSessionButton()
                running -> stopRunning()
                else -> send()
            }
        }
        updateSendButton()
        setupOptionsButton()
        if (node.session) startSession()

        val bar = findViewById<View>(R.id.chat_input_bar)
        val basePadBottom = bar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.chat_root)) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            bar.setPadding(bar.paddingLeft, bar.paddingTop, bar.paddingRight, basePadBottom + maxOf(sys.bottom, ime.bottom))
            insets
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        uiHandler.removeCallbacksAndMessages(null)
        if (running) stopRunnable?.run()
        stopSession()
        sessionExecutor.shutdown()
    }

    private fun setupParams(paramsNode: com.omarea.krscript.model.ActionNode) {
        if (paramsNode.title.isEmpty()) paramsNode.title = getString(R.string.chat_params_title)
        paramsNode.params?.forEach { p ->
            val name = p.name ?: return@forEach
            val v = ActionParamMemory.load(this, paramsNode, p) ?: p.value
            if (v != null) chatParams[name] = v
        }
        paramsNode.onParamsConfirmed = { values ->
            chatParams.clear()
            chatParams.putAll(values)
            if (node.session) uiHandler.post { restartSession() }
        }
    }

    private fun setupOptionsButton() {
        val paramsNode = node.paramsNode ?: return
        val button = findViewById<ImageButton>(R.id.chat_options)
        val custom = try {
            IconPathAnalysis().loadIcon(this, paramsNode)
        } catch (_: Exception) {
            null
        }
        if (custom != null) button.setImageDrawable(custom) else button.setImageResource(R.drawable.ic_chat_options)
        val tint = TypedValue()
        if (theme.resolveAttribute(android.R.attr.textColorSecondary, tint, true) && tint.resourceId != 0) {
            button.imageTintList = ContextCompat.getColorStateList(this, tint.resourceId)
        }
        button.contentDescription = paramsNode.title
        button.visibility = View.VISIBLE
        button.setOnClickListener {
            actionHost?.takeIf { it.isAdded }?.onActionClick(paramsNode, Runnable {})
        }
    }

    private fun addGreeting() {
        if (node.greeting.isNotEmpty()) adapter.add(ChatMessage(false, node.greeting))
    }

    private fun accentColor(): Int {
        val tv = TypedValue()
        return if (theme.resolveAttribute(androidx.appcompat.R.attr.colorAccent, tv, true)) tv.data else Color.GRAY
    }

    private fun updateSendButton() {
        val busy = if (node.session) sessionAlive && sessionBusy else running
        sendButton.setImageResource(if (busy) R.drawable.ic_chat_stop else R.drawable.ic_chat_send)
        sendButton.imageTintList = ColorStateList.valueOf(Color.WHITE)
    }

    private fun sessionHint() = node.placeholder.ifEmpty { SESSION_DEFAULT_HINT }

    private fun shellQuote(s: String) = "'" + s.replace("'", "'\\''") + "'"

    private fun buildSessionCmd(): String {
        val sb = StringBuilder()
        val validName = Regex("[A-Za-z_][A-Za-z0-9_]*")
        for ((k, v) in chatParams) {
            if (validName.matches(k)) sb.append("export ").append(k).append('=').append(shellQuote(v)).append('\n')
        }
        sb.append(node.init)
        return sb.toString()
    }

    private fun startSession() {
        val gen = ++sessionGen
        sessionAlive = true
        sessionBusy = true
        AnsiColorParser.reset()
        inputView.hint = sessionHint()
        val message = sessionMessage ?: ChatMessage(false, "").also {
            sessionMessage = it
            adapter.add(it)
        }
        if (message.text.isNotEmpty() && message.text.last() != '\n') message.text.append('\n')
        sessionWriter = OutputWriter(message, SESSION_MAX_CHARS)
        updateSendButton()
        val appContext = applicationContext
        val cmd = buildSessionCmd()
        Thread {
            val started = try {
                val l = TerminalLauncher.prepare(appContext, node.cwd, cmd, node.root, node.currentPageConfigPath, false)
                Pair(l, l.start(SESSION_COLS, SESSION_ROWS))
            } catch (e: Exception) {
                uiHandler.post { if (gen == sessionGen) onSessionEnded("Cannot start shell: " + (e.message ?: e.toString())) }
                return@Thread
            }
            val launcher = started.first
            val process = started.second
            uiHandler.post {
                if (gen == sessionGen) {
                    sessionLauncher = launcher
                    sessionProcess = process
                } else {
                    teardown(launcher, process)
                }
            }
            val reader = InputStreamReader(process.inputStream, Charsets.UTF_8)
            val buf = CharArray(4096)
            try {
                while (true) {
                    val n = reader.read(buf)
                    if (n < 0) break
                    val chunk = String(buf, 0, n)
                    uiHandler.post { if (gen == sessionGen) onSessionOutput(chunk) }
                }
            } catch (_: Exception) {
            }
            val code = try {
                process.waitFor()
            } catch (_: Exception) {
                -1
            }
            uiHandler.post { if (gen == sessionGen) onSessionEnded("Session ended (code $code). Press send to restart.") }
        }.start()
    }

    private fun teardown(launcher: TerminalLauncher, process: Process) {
        try {
            launcher.kill(process)
        } catch (_: Exception) {
        }
        try {
            process.destroy()
        } catch (_: Exception) {
        }
        launcher.cleanup()
    }

    private fun stopSession() {
        sessionGen++
        uiHandler.removeCallbacks(promptCheck)
        val launcher = sessionLauncher
        val process = sessionProcess
        sessionLauncher = null
        sessionProcess = null
        sessionAlive = false
        if (launcher != null && process != null) teardown(launcher, process)
    }

    private fun restartSession() {
        stopSession()
        startSession()
    }

    private fun writeSession(bytes: ByteArray) {
        val process = sessionProcess ?: return
        try {
            sessionExecutor.execute {
                try {
                    process.outputStream.write(bytes)
                    process.outputStream.flush()
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun onSessionButton() {
        when {
            !sessionAlive -> restartSession()
            sessionBusy -> writeSession(byteArrayOf(3))
            else -> sendSession()
        }
    }

    private fun sendSession() {
        val line = inputView.text?.toString().orEmpty().replace("\r", "").replace('\n', ' ')
        inputView.setText("")
        sessionBusy = true
        updateSendButton()
        writeSession((line + "\n").toByteArray(Charsets.UTF_8))
    }

    private fun onSessionOutput(chunk: String) {
        val message = sessionMessage ?: return
        val writer = sessionWriter ?: return
        var text = chunk
        val cut = maxOf(text.lastIndexOf("\u001B[2J"), text.lastIndexOf("\u001B[3J"))
        if (cut >= 0) {
            message.text.clear()
            writer.reset()
            text = text.substring(cut + 4)
        }
        if (text.isNotEmpty()) writer.append(AnsiColorParser.parse(text))
        scheduleRefresh(message)
        uiHandler.removeCallbacks(promptCheck)
        uiHandler.postDelayed(promptCheck, PROMPT_IDLE_MS)
    }

    private fun onPromptIdle() {
        val message = sessionMessage ?: return
        if (!sessionAlive) return
        val buf = message.text
        if (buf.isEmpty() || buf.last() == '\n') return
        sessionBusy = false
        updateSendButton()
        if (node.placeholder.isEmpty()) {
            val line = buf.substring(buf.lastIndexOf('\n') + 1).filter { it >= ' ' }
            if (line.isNotBlank()) inputView.hint = line.takeLast(60)
        }
    }

    private fun onSessionEnded(note: String) {
        val message = sessionMessage ?: return
        sessionAlive = false
        sessionBusy = false
        uiHandler.removeCallbacks(promptCheck)
        val buf = message.text
        val start = buf.length
        buf.append(if (buf.isEmpty() || buf.last() == '\n') "" else "\n").append("[").append(note).append("]")
        buf.setSpan(ForegroundColorSpan(Color.RED), start, buf.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sessionLauncher = null
        sessionProcess = null
        updateSendButton()
        refreshNow(message)
    }

    private fun scrollToEnd() {
        val last = adapter.itemCount - 1
        if (last >= 0) listView.scrollToPosition(last)
    }

    private fun copyText(text: CharSequence) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("chat", text))
        Toast.makeText(this, R.string.chat_copied, Toast.LENGTH_SHORT).show()
    }

    private fun send() {
        if (running) return
        val text = inputView.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        if (node.script.isBlank()) {
            Toast.makeText(this, R.string.chat_script_empty, Toast.LENGTH_SHORT).show()
            return
        }
        inputView.setText("")
        adapter.add(ChatMessage(true, text))
        val reply = ChatMessage(false, "").apply { running = true }
        adapter.add(reply)
        scrollToEnd()
        runScript(text, reply)
    }

    private fun runScript(text: String, reply: ChatMessage) {
        running = true
        updateSendButton()
        AnsiColorParser.reset()
        val handler = ChatShellHandler(
            this,
            reply,
            { scheduleRefresh(reply) },
            { stopRunnable = it },
            {
                running = false
                stopRunnable = null
                currentHandler = null
                reply.running = false
                refreshNow(reply)
                updateSendButton()
            }
        )
        currentHandler = handler
        val process = ShellExecutor().execute(this, node, node.script, null, HashMap(chatParams).apply { put("MSG", text) }, handler)
        if (process == null) handler.finishNow(-1)
    }

    private fun stopRunning() {
        stopRunnable?.run()
        val handler = currentHandler ?: return
        uiHandler.postDelayed({ if (running && currentHandler === handler) handler.finishNow(-1) }, 1500)
    }

    private fun scheduleRefresh(message: ChatMessage) {
        refreshTarget = message
        if (refreshPending) return
        refreshPending = true
        uiHandler.postDelayed({
            refreshPending = false
            refreshTarget?.let { refreshNow(it) }
        }, REFRESH_DELAY_MS)
    }

    private fun refreshNow(message: ChatMessage) {
        val stick = !listView.canScrollVertically(1)
        adapter.changed(message)
        if (stick) scrollToEnd()
    }

    private fun clearChat() {
        if (node.session) {
            sessionMessage?.let {
                it.text.clear()
                sessionWriter?.reset()
                adapter.changed(it)
            }
            if (sessionAlive) writeSession("\n".toByteArray())
            return
        }
        if (running) {
            stopRunning()
            Toast.makeText(this, R.string.chat_cleared_stop, Toast.LENGTH_SHORT).show()
        }
        adapter.clear()
        addGreeting()
    }

    private fun isSupported(o: PageMenuOption): Boolean {
        if (o.link.isNotEmpty() || o.activity.isNotEmpty() || o.onlineHtmlPage.isNotEmpty() ||
            o.pageConfigSh.isNotEmpty() || o.pageConfigPath.isNotEmpty()
        ) return false
        return o.type !in UNSUPPORTED_TYPES
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu ?: return false
        menu.clear()

        val options = node.menuOptions.filter { isSupported(it) }
        if (options.isNotEmpty()) {
            val density = resources.displayMetrics.density
            val button = ImageButton(this).apply {
                val size = (48 * density).toInt()
                layoutParams = ViewGroup.LayoutParams(size, size)
                val tv = TypedValue()
                if (theme.resolveAttribute(androidx.appcompat.R.attr.actionBarItemBackground, tv, true)) {
                    setBackgroundResource(tv.resourceId)
                } else {
                    background = null
                }
                scaleType = android.widget.ImageView.ScaleType.CENTER
                contentDescription = getString(R.string.kr_more_options)
                setImageResource(R.drawable.ic_more_vert)
                setOnClickListener { anchor -> showOptionsPopup(anchor, options) }
            }
            val item = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, getString(R.string.kr_more_options))
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            item.actionView = button
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return super.onOptionsItemSelected(item)
    }

    private fun showOptionsPopup(anchor: View, options: List<PageMenuOption>) {
        val rows = options.map { buildRow(it) }
        val adapter = PopupMenuListAdapter(this, rows)
        val background = ContextCompat.getDrawable(this, R.drawable.kr_spinner_popup_bg)

        val popup = ListPopupWindow(this)
        popup.anchorView = anchor
        popup.setAdapter(adapter)
        popup.setBackgroundDrawable(background)
        popup.isModal = true
        popup.setOnItemClickListener { _, _, position, _ ->
            popup.dismiss()
            rows.getOrNull(position)?.onClick?.invoke()
        }

        val parent = anchor.parent as? ViewGroup
        val itemViews = rows.indices.map { adapter.getView(it, null, parent) }
        val minWidthPx = (resources.displayMetrics.density * 200).toInt()
        SpinnerPopupHelper.applyWidthAndPosition(
            popup, anchor, itemViews, background, minWidthPx, alignRight = true,
            extraTopGapPx = 0, applyVerticalOffset = true
        )
        popup.show()
        SpinnerPopupHelper.applyRoundedClip(popup, resources.getDimension(R.dimen.kr_spinner_popup_radius))
    }

    private fun buildRow(option: PageMenuOption): PopupMenuRow {
        val isResetType = option.type == "clear" || option.type in CLOSE_TYPES
        val leftIcon = try {
            IconPathAnalysis().loadIcon(this, option)
        } catch (_: Exception) {
            null
        }
        return PopupMenuRow(
            title = option.title,
            leftIcon = leftIcon,
            typeIcon = if (isResetType) PopupRowTypeIcon.REFRESH else PopupRowTypeIcon.SCRIPT,
            checked = false
        ) { onMenuOption(option) }
    }

    private fun onMenuOption(option: PageMenuOption) {
        when {
            option.type == "clear" -> clearChat()
            option.type in CLOSE_TYPES -> finish()
            option.silent -> runSilent(option)
            else -> showLog(option)
        }
    }

    private fun menuParams(option: PageMenuOption) = hashMapOf("state" to option.key, "menu_id" to option.key)

    private fun runSilent(option: PageMenuOption) {
        val appContext = applicationContext
        Thread {
            try {
                ScriptEnvironmen.executeResultRoot(appContext, option.script, option, menuParams(option))
            } catch (_: Exception) {
            }
        }.start()
    }

    private fun showLog(option: PageMenuOption) {
        val dialog = DialogLogFragment.create(
            option,
            Runnable {},
            Runnable { if (option.autoFinish && !isFinishing && !isDestroyed) finish() },
            option.script,
            menuParams(option),
            ThemeModeState.getThemeMode().isDarkMode
        )
        dialog.show(supportFragmentManager, "")
        dialog.isCancelable = false
    }

    override fun openFileChooser(fileSelectedInterface: ParamsFileChooserRender.FileSelectedInterface): Boolean = false

    override fun onSubPageClick(pageNode: PageNode) {
        OpenPageHelper(this).openPage(pageNode)
    }

    override fun onActionCompleted(runnableNode: RunnableNode) {
        if (runnableNode.autoFinish) finish()
    }

    override fun addToFavorites(clickableNode: ClickableNode, addToFavoritesHandler: KrScriptActionHandler.AddToFavoritesHandler) {
    }

    private class ChatMessage(val fromUser: Boolean, initial: CharSequence) {
        val text = SpannableStringBuilder(initial)
        var running = false
        var progressCur = 0
        var progressTotal = 0
    }

    private class ChatShellHandler(
        context: Context,
        private val message: ChatMessage,
        private val onChanged: () -> Unit,
        private val onForceStop: (Runnable?) -> Unit,
        private val onFinished: () -> Unit
    ) : SilentShellOutputHandler(context) {
        private val writer = OutputWriter(message, MAX_REPLY_CHARS)
        private var finished = false

        override fun onStart(forceStop: Runnable?) {
            onForceStop(forceStop)
        }

        override fun onReader(msg: Any?) = dispatch(msg, null)
        override fun onError(msg: Any?) = dispatch(msg, Color.RED)
        override fun onWrite(msg: Any?) {}

        override fun onProgress(current: Int, total: Int) {
            message.progressCur = current
            message.progressTotal = when {
                current < 0 -> -1
                current >= total -> 0
                else -> total
            }
            onChanged()
        }

        override fun onExit(msg: Any?) {
            finishNow((msg as? Int) ?: -1)
        }

        fun finishNow(code: Int) {
            if (finished) return
            finished = true
            message.progressTotal = 0
            val buf = message.text
            while (buf.isNotEmpty() && buf.last() == '\n') buf.delete(buf.length - 1, buf.length)
            if (code != 0) {
                appendPlain((if (buf.isEmpty()) "" else "\n") + context.getString(R.string.kr_shell_finish_error), Color.RED)
            } else if (buf.isEmpty()) {
                appendPlain(context.getString(R.string.kr_shell_completed), null)
            }
            onFinished()
        }

        private fun appendPlain(s: String, color: Int?) {
            val buf = message.text
            val start = buf.length
            buf.append(s)
            if (color != null) buf.setSpan(ForegroundColorSpan(color), start, buf.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        private fun dispatch(msg: Any?, forcedColor: Int?) {
            if (msg == null || finished) return
            val raw = msg.toString()
            var parsed: CharSequence = AnsiColorParser.parse(raw)
            if (forcedColor != null && !raw.contains("\u001B[")) {
                parsed = SpannableStringBuilder(parsed).apply {
                    setSpan(ForegroundColorSpan(forcedColor), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            writer.append(parsed)
            onChanged()
        }
    }

    // Carriage-return rules as in the shell log dialog: "\r" rewinds to the start of the line,
    // the next text overwrites it, "\n" commits the line.
    private class OutputWriter(private val message: ChatMessage, private val maxChars: Int) {
        private var lineStart = message.text.length
        private var pendingOverwrite = false
        private var prevEndedWithCr = false

        fun reset() {
            lineStart = 0
            pendingOverwrite = false
            prevEndedWithCr = false
        }

        fun append(text: CharSequence) {
            val buf = message.text
            val len = text.length
            if (prevEndedWithCr && len > 0 && text[0] == '\n') pendingOverwrite = false
            var i = 0
            while (i < len) {
                var j = i
                while (j < len && text[j] != '\r' && text[j] != '\n') j++
                if (j > i) {
                    if (pendingOverwrite) {
                        if (lineStart in 0..buf.length) buf.delete(lineStart, buf.length)
                        pendingOverwrite = false
                    }
                    buf.append(text.subSequence(i, j))
                }
                if (j < len) {
                    if (text[j] == '\r') {
                        val isCrlf = j + 1 < len && text[j + 1] == '\n'
                        if (!isCrlf) pendingOverwrite = true
                    } else {
                        if (pendingOverwrite && lineStart in 0..buf.length) {
                            buf.delete(lineStart, buf.length)
                            pendingOverwrite = false
                        }
                        buf.append('\n')
                        lineStart = buf.length
                    }
                    j++
                }
                i = j
            }
            prevEndedWithCr = len > 0 && text[len - 1] == '\r'
            if (buf.length > maxChars) {
                val cut = buf.length - maxChars
                buf.delete(0, cut)
                lineStart = (lineStart - cut).coerceAtLeast(0)
            }
        }
    }

    private class ChatAdapter(
        private val activity: ChatActivity,
        private val onCopy: (CharSequence) -> Unit
    ) : RecyclerView.Adapter<ChatAdapter.VH>() {
        private val items = ArrayList<ChatMessage>()

        class VH(view: View, val bubble: View, val text: TextView, val progress: ProgressBar) : RecyclerView.ViewHolder(view)

        fun add(message: ChatMessage) {
            items.add(message)
            notifyItemInserted(items.size - 1)
        }

        fun changed(message: ChatMessage) {
            val index = items.indexOf(message)
            if (index >= 0) notifyItemChanged(index)
        }

        @android.annotation.SuppressLint("NotifyDataSetChanged")
        fun clear() {
            items.clear()
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size
        override fun getItemViewType(position: Int) = if (items[position].fromUser) 1 else 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
            val bubble = view.findViewById<View>(R.id.chat_bubble)
            val tv = view.findViewById<TextView>(R.id.chat_text)
            val progress = view.findViewById<ProgressBar>(R.id.chat_progress)
            val density = parent.resources.displayMetrics.density
            val fromUser = viewType == 1
            val bg = GradientDrawable().apply {
                cornerRadius = 18 * density
                setColor(if (fromUser) (activity.accentColor() and 0x00FFFFFF) or 0x40000000 else 0x26808080)
            }
            bubble.background = bg
            tv.maxWidth = (parent.resources.displayMetrics.widthPixels * 0.82f).toInt()
            (bubble.layoutParams as FrameLayout.LayoutParams).gravity = if (fromUser) Gravity.END else Gravity.START
            return VH(view, bubble, tv, progress)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val m = items[position]
            holder.text.text = if (m.text.isEmpty() && m.running) "…" else m.text
            if (m.running && m.progressTotal != 0) {
                holder.progress.visibility = View.VISIBLE
                holder.progress.isIndeterminate = m.progressTotal < 0
                if (m.progressTotal > 0) {
                    holder.progress.max = m.progressTotal
                    holder.progress.progress = m.progressCur
                }
            } else {
                holder.progress.visibility = View.GONE
            }
            holder.text.setOnLongClickListener {
                if (m.text.isNotEmpty()) onCopy(m.text.toString())
                true
            }
        }
    }
}
