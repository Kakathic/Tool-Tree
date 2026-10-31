package com.tool.tree

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
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
import android.widget.LinearLayout
import android.widget.ListPopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.omarea.common.ui.DialogHelper
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

    private lateinit var store: ChatSessionStore
    private lateinit var session: ChatSession
    private lateinit var drawer: DrawerLayout
    private lateinit var sessionDrawer: ChatSessionDrawer
    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val deletedIds = HashSet<String>()

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
        title = node.title.ifEmpty { getString(R.string.chat_default_title) }
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
        store = ChatSessionStore(this, node.currentPageConfigPath + "\u0000" + node.title, node.title.ifEmpty { getString(R.string.chat_default_title) })
        val latest = store.list().firstOrNull()
        session = if (latest != null) openStored(latest.id) else newEmptySession()
        adapter.bind(session.messages)
        scrollToEnd()

        inputView = findViewById(R.id.chat_input)
        inputView.hint = node.placeholder.ifEmpty { getString(R.string.chat_input_hint) }
        inputView.maxLines = node.maxLines
        sendButton = findViewById(R.id.chat_send)
        sendButton.setOnClickListener { if (running) stopRunning() else send() }
        updateSendButton()
        setupOptionsButton()
        setupDrawer()

        val bar = findViewById<View>(R.id.chat_input_bar)
        val panel = findViewById<View>(R.id.chat_drawer_panel)
        val basePadBottom = bar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.chat_root)) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            bar.setPadding(bar.paddingLeft, bar.paddingTop, bar.paddingRight, basePadBottom + maxOf(sys.bottom, ime.bottom))
            panel.setPadding(0, sys.top, 0, maxOf(sys.bottom, ime.bottom))
            insets
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawer.isDrawerOpen(GravityCompat.END)) {
                    drawer.closeDrawer(GravityCompat.END)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onStop() {
        super.onStop()
        if (::session.isInitialized) saveSession(session)
    }

    override fun onDestroy() {
        super.onDestroy()
        uiHandler.removeCallbacksAndMessages(null)
        if (running) stopRunnable?.run()
        if (::session.isInitialized) saveSession(session)
        ioExecutor.shutdown()
    }

    private fun newSession(id: String): ChatSession {
        val s = ChatSession(id)
        if (node.greeting.isNotEmpty()) s.messages.add(ChatMessage(false, node.greeting).apply { transient = true })
        return s
    }

    private fun newEmptySession(): ChatSession = newSession(store.newId())

    private fun openStored(id: String): ChatSession {
        val s = newSession(id)
        for (m in store.load(id)) {
            s.messages.add(
                ChatMessage(m.fromUser, m.text).apply {
                    title = (if (m.fromUser) node.userTitle else node.chatTitle).ifEmpty { null }
                }
            )
        }
        return s
    }

    private fun saveSession(target: ChatSession) {
        val id = target.id
        if (id in deletedIds) return
        val snapshot = target.messages
            .filter { !it.transient && (it.fromUser || it.text.isNotEmpty()) }
            .map { ChatSessionStore.Stored(it.fromUser, it.text.toString()) }
        try {
            ioExecutor.execute { store.save(id, snapshot) }
        } catch (_: Exception) {
        }
    }

    private fun setupDrawer() {
        drawer = findViewById(R.id.chat_drawer)
        findViewById<TextView>(R.id.chat_drawer_title).text = "Phiên chat"
        val newButton = findViewById<TextView>(R.id.chat_new_session)
        newButton.text = "Phiên mới"
        sessionDrawer = ChatSessionDrawer(
            findViewById(R.id.chat_session_list),
            newButton,
            onSelect = { info -> if (info.id != session.id) switchTo(openStored(info.id)) else drawer.closeDrawer(GravityCompat.END) },
            onDelete = { info -> confirmDelete(info) },
            onNew = { startNewSession() }
        )
        drawer.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerStateChanged(newState: Int) {
                // Vuốt từ mép phải: nạp danh sách ngay khi bắt đầu kéo
                if (newState == DrawerLayout.STATE_DRAGGING && !drawer.isDrawerOpen(GravityCompat.END)) prepareSessionList()
            }
        })

        // Android 10+ dùng vuốt mép để Back: nhường dải mép phải cho ngăn kéo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val density = resources.displayMetrics.density
            drawer.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                val height = minOf(v.height, (200 * density).toInt())
                val top = (v.height - height) / 2
                v.setSystemGestureExclusionRects(listOf(Rect(v.width - (24 * density).toInt(), top, v.width, top + height)))
            }
        }
    }

    private fun refreshSessionList() {
        val infos = store.list().toMutableList()
        if (infos.none { it.id == session.id }) infos.add(0, ChatSessionStore.Info(session.id, store.emptyName, 0))
        sessionDrawer.refresh(infos, session.id)
    }

    private fun prepareSessionList() {
        saveSession(session)
        ioExecutor.execute { uiHandler.post { refreshSessionList() } }
    }

    private fun toggleDrawer() {
        if (drawer.isDrawerOpen(GravityCompat.END)) {
            drawer.closeDrawer(GravityCompat.END)
        } else {
            prepareSessionList()
            drawer.openDrawer(GravityCompat.END)
        }
    }

    private fun switchTo(target: ChatSession) {
        if (running) stopRunning()
        session = target
        adapter.bind(target.messages)
        scrollToEnd()
        drawer.closeDrawer(GravityCompat.END)
    }

    private fun startNewSession() {
        if (session.messages.none { !it.transient }) {
            drawer.closeDrawer(GravityCompat.END)
            return
        }
        saveSession(session)
        switchTo(newEmptySession())
    }

    private fun confirmDelete(info: ChatSessionStore.Info) {
        DialogHelper.confirm(this, info.name, "Xoá phiên này?", Runnable {
            deletedIds.add(info.id)
            ioExecutor.execute {
                store.delete(info.id)
                uiHandler.post {
                    if (info.id == session.id) {
                        val next = store.list().firstOrNull()
                        switchTo(if (next != null) openStored(next.id) else newEmptySession())
                        drawer.openDrawer(GravityCompat.END)
                    }
                    refreshSessionList()
                }
            }
        }, null)
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

    private fun accentColor(): Int {
        val tv = TypedValue()
        return if (theme.resolveAttribute(androidx.appcompat.R.attr.colorAccent, tv, true)) tv.data else Color.GRAY
    }

    private fun updateSendButton() {
        sendButton.setImageResource(if (running) R.drawable.ic_chat_stop else R.drawable.ic_chat_send)
        sendButton.imageTintList = ColorStateList.valueOf(Color.WHITE)
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
        val owner = session
        adapter.add(ChatMessage(true, text).apply { title = node.userTitle.ifEmpty { null } })
        val reply = ChatMessage(false, "").apply {
            running = true
            title = node.chatTitle.ifEmpty { null }
        }
        adapter.add(reply)
        scrollToEnd()
        saveSession(owner)
        runScript(text, reply, owner)
    }

    private fun runScript(text: String, reply: ChatMessage, owner: ChatSession) {
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
                saveSession(owner)
            }
        )
        currentHandler = handler
        val params = HashMap(chatParams).apply {
            put("MSG", text)
            put("CHAT_ID", owner.id)
        }
        val process = ShellExecutor().execute(this, node, node.script, null, params, handler)
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
        if (running) {
            stopRunning()
            Toast.makeText(this, R.string.chat_cleared_stop, Toast.LENGTH_SHORT).show()
        }
        session.messages.removeAll { !it.transient }
        adapter.bind(session.messages)
        saveSession(session)
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

        val sessionsButton = toolbarButton(R.drawable.ic_chat_sessions, "Phiên chat")
        sessionsButton.setOnClickListener { toggleDrawer() }
        val sessionsItem = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Phiên chat")
        sessionsItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        sessionsItem.actionView = sessionsButton

        val options = node.menuOptions.filter { isSupported(it) }
        if (options.isNotEmpty()) {
            val button = toolbarButton(R.drawable.ic_more_vert, getString(R.string.kr_more_options))
            button.setOnClickListener { anchor -> showOptionsPopup(anchor, options) }
            val item = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, getString(R.string.kr_more_options))
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            item.actionView = button
        }
        return true
    }

    private fun toolbarButton(iconRes: Int, description: String): ImageButton {
        val density = resources.displayMetrics.density
        return ImageButton(this).apply {
            val size = (48 * density).toInt()
            layoutParams = ViewGroup.LayoutParams(size, size)
            val tv = TypedValue()
            if (theme.resolveAttribute(androidx.appcompat.R.attr.actionBarItemBackground, tv, true)) {
                setBackgroundResource(tv.resourceId)
            } else {
                background = null
            }
            scaleType = android.widget.ImageView.ScaleType.CENTER
            contentDescription = description
            setImageResource(iconRes)
        }
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
        // Không lấy focus để bàn phím đang mở không bị tắt
        popup.isModal = false
        popup.inputMethodMode = ListPopupWindow.INPUT_METHOD_NOT_NEEDED
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

    private class ChatSession(val id: String) {
        val messages = ArrayList<ChatMessage>()
    }

    private class ChatMessage(val fromUser: Boolean, initial: CharSequence) {
        val text = SpannableStringBuilder(initial)
        var title: CharSequence? = null
        var transient = false
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
        private var lineStart = 0
        private var pendingOverwrite = false
        private var prevEndedWithCr = false
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
            append(parsed)
            onChanged()
        }

        // Same carriage-return rules as the shell log dialog: "\r" rewinds to the start of the line,
        // the next text overwrites it, "\n" commits the line.
        private fun append(text: CharSequence) {
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
            if (buf.length > MAX_REPLY_CHARS) {
                val cut = buf.length - MAX_REPLY_CHARS
                buf.delete(0, cut)
                lineStart = (lineStart - cut).coerceAtLeast(0)
            }
        }
    }

    private class ChatAdapter(
        private val activity: ChatActivity,
        private val onCopy: (CharSequence) -> Unit
    ) : RecyclerView.Adapter<ChatAdapter.VH>() {
        private var items = ArrayList<ChatMessage>()

        @android.annotation.SuppressLint("NotifyDataSetChanged")
        fun bind(list: ArrayList<ChatMessage>) {
            items = list
            notifyDataSetChanged()
        }

        class VH(view: View, val bubble: View, val title: TextView, val text: TextView, val progress: ProgressBar) : RecyclerView.ViewHolder(view) {
            var dots: Runnable? = null
        }

        fun add(message: ChatMessage) {
            items.add(message)
            notifyItemInserted(items.size - 1)
        }

        fun changed(message: ChatMessage) {
            val index = items.indexOf(message)
            if (index >= 0) notifyItemChanged(index)
        }

        private fun visibleText(text: SpannableStringBuilder): CharSequence {
            var end = text.length
            while (end > 0 && (text[end - 1] == '\n' || text[end - 1] == '\r')) end--
            return if (end == text.length) text else text.subSequence(0, end)
        }

        private fun stopDots(holder: VH) {
            holder.dots?.let { holder.text.removeCallbacks(it) }
            holder.dots = null
        }

        // Ba chấm chờ phản hồi: chấm sáng chạy lần lượt, độ rộng cố định nên bong bóng không giật
        private fun startDots(holder: VH) {
            val tv = holder.text
            val base = tv.currentTextColor and 0x00FFFFFF
            var phase = 0
            val step = object : Runnable {
                override fun run() {
                    val dots = SpannableString("•••")
                    for (i in 0..2) {
                        val alpha = when ((i - phase + 3) % 3) {
                            0 -> 255
                            1 -> 140
                            else -> 60
                        }
                        dots.setSpan(ForegroundColorSpan(base or (alpha shl 24)), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    tv.text = dots
                    phase = (phase + 1) % 3
                    tv.postDelayed(this, 350)
                }
            }
            holder.dots = step
            step.run()
        }

        override fun onViewRecycled(holder: VH) {
            stopDots(holder)
            super.onViewRecycled(holder)
        }

        override fun getItemCount() = items.size
        override fun getItemViewType(position: Int) = if (items[position].fromUser) 1 else 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
            val column = view.findViewById<View>(R.id.chat_column)
            val bubble = view.findViewById<View>(R.id.chat_bubble)
            val title = view.findViewById<TextView>(R.id.chat_title)
            val tv = view.findViewById<TextView>(R.id.chat_text)
            val progress = view.findViewById<ProgressBar>(R.id.chat_progress)
            val density = parent.resources.displayMetrics.density
            val fromUser = viewType == 1
            val bg = GradientDrawable().apply {
                cornerRadius = 18 * density
                setColor(if (fromUser) (activity.accentColor() and 0x00FFFFFF) or 0x40000000 else 0x26808080)
            }
            bubble.background = bg
            tv.maxWidth = parent.resources.displayMetrics.widthPixels - (24 * density).toInt()
            (column.layoutParams as FrameLayout.LayoutParams).gravity = if (fromUser) Gravity.END else Gravity.START
            if (fromUser) {
                (title.layoutParams as LinearLayout.LayoutParams).apply {
                    gravity = Gravity.END
                    marginStart = 0
                    marginEnd = (8 * density).toInt()
                }
            }
            return VH(view, bubble, title, tv, progress)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val m = items[position]
            val label = m.title
            holder.title.visibility = if (label != null) View.VISIBLE else View.GONE
            holder.title.text = label
            val visible = visibleText(m.text)
            stopDots(holder)
            if (visible.isEmpty() && m.running) startDots(holder) else holder.text.text = visible
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
