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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.MenuItemCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.omarea.krscript.config.IconPathAnalysis
import com.omarea.krscript.executor.ScriptEnvironmen
import com.omarea.krscript.executor.ShellExecutor
import com.omarea.krscript.model.ActionNode
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
        private val ANSI_REGEX = Regex("\u001B\\[[;\\d]*[ -/]*[@-~]")

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

    private var running = false
    private var stopRunnable: Runnable? = null
    private var currentHandler: ChatShellHandler? = null
    private var refreshPending = false
    private var refreshTarget: ChatMessage? = null

    private val toolbarIconTint: ColorStateList? by lazy {
        val a = theme.obtainStyledAttributes(intArrayOf(R.attr.toolbarIconTint))
        try {
            if (a.hasValue(0)) a.getColorStateList(0) else null
        } finally {
            a.recycle()
        }
    }

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
        inputView.hint = node.placeholder.ifEmpty { getString(R.string.chat_input_hint) }
        inputView.maxLines = node.maxLines
        sendButton = findViewById(R.id.chat_send)
        sendButton.setOnClickListener { if (running) stopRunning() else send() }
        updateSendButton()

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
    }

    private fun addGreeting() {
        if (node.desc.isNotEmpty()) adapter.add(ChatMessage(false, node.desc))
    }

    private fun accentColor(): Int {
        val tv = TypedValue()
        return if (theme.resolveAttribute(androidx.appcompat.R.attr.colorAccent, tv, true)) tv.data else Color.GRAY
    }

    private fun updateSendButton() {
        sendButton.setImageResource(if (running) R.drawable.ic_chat_stop else R.drawable.ic_chat_send)
        sendButton.imageTintList = ColorStateList.valueOf(accentColor())
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
        val process = ShellExecutor().execute(this, node, node.script, null, hashMapOf("MSG" to text), handler)
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
        adapter.clear()
        addGreeting()
    }

    private fun headerId(action: ActionNode) = ("header:" + action.key).hashCode()

    private fun isSupported(o: PageMenuOption): Boolean {
        if (o.link.isNotEmpty() || o.activity.isNotEmpty() || o.onlineHtmlPage.isNotEmpty() ||
            o.pageConfigSh.isNotEmpty() || o.pageConfigPath.isNotEmpty()
        ) return false
        return o.type !in UNSUPPORTED_TYPES
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu ?: return false
        menu.clear()

        node.headerActions.forEach { action ->
            val item = menu.add(Menu.NONE, headerId(action), Menu.NONE, action.title)
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            val custom = try {
                IconPathAnalysis().loadIcon(this, action)
            } catch (_: Exception) {
                null
            }
            if (custom != null) {
                item.icon = custom
                MenuItemCompat.setIconTintList(item, toolbarIconTint)
            } else {
                item.icon = ContextCompat.getDrawable(this, R.drawable.ic_menu)
            }
        }

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
        val action = node.headerActions.find { headerId(it) == item.itemId }
        if (action != null) {
            actionHost?.takeIf { it.isAdded }?.onActionClick(action, Runnable {})
            return true
        }
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
    }

    private class ChatShellHandler(
        context: Context,
        private val message: ChatMessage,
        private val onChanged: () -> Unit,
        private val onForceStop: (Runnable?) -> Unit,
        private val onFinished: () -> Unit
    ) : SilentShellOutputHandler(context) {
        private var skipNextLf = false
        private var finished = false

        override fun onStart(forceStop: Runnable?) {
            onForceStop(forceStop)
        }

        override fun onReader(msg: Any?) = appendLine(msg, null)
        override fun onError(msg: Any?) = appendLine(msg, Color.RED)
        override fun onWrite(msg: Any?) {}

        override fun onExit(msg: Any?) {
            finishNow((msg as? Int) ?: -1)
        }

        fun finishNow(code: Int) {
            if (finished) return
            finished = true
            while (message.text.isNotEmpty() && message.text.last() == '\n') {
                message.text.delete(message.text.length - 1, message.text.length)
            }
            if (code != 0) {
                append((if (message.text.isEmpty()) "" else "\n") + context.getString(R.string.kr_shell_finish_error), Color.RED)
            } else if (message.text.isEmpty()) {
                append(context.getString(R.string.kr_shell_completed), null)
            }
            onFinished()
        }

        private fun appendLine(msg: Any?, color: Int?) {
            if (msg == null || finished) return
            val text = ANSI_REGEX.replace(msg.toString(), "")
            if (text == "\n" && skipNextLf) {
                skipNextLf = false
                return
            }
            skipNextLf = text.endsWith("\r")
            append(text.trimEnd('\r', '\n') + "\n", color)
        }

        private fun append(s: String, color: Int?) {
            val start = message.text.length
            message.text.append(s)
            if (color != null) {
                message.text.setSpan(ForegroundColorSpan(color), start, message.text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (message.text.length > MAX_REPLY_CHARS) {
                message.text.delete(0, message.text.length - MAX_REPLY_CHARS)
            }
            onChanged()
        }
    }

    private class ChatAdapter(
        private val activity: ChatActivity,
        private val onCopy: (CharSequence) -> Unit
    ) : RecyclerView.Adapter<ChatAdapter.VH>() {
        private val items = ArrayList<ChatMessage>()

        class VH(view: View, val text: TextView) : RecyclerView.ViewHolder(view)

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
            val tv = view.findViewById<TextView>(R.id.chat_text)
            val density = parent.resources.displayMetrics.density
            val fromUser = viewType == 1
            val bg = GradientDrawable().apply {
                cornerRadius = 18 * density
                setColor(if (fromUser) (activity.accentColor() and 0x00FFFFFF) or 0x40000000 else 0x26808080)
            }
            tv.background = bg
            tv.maxWidth = (parent.resources.displayMetrics.widthPixels * 0.82f).toInt()
            (tv.layoutParams as FrameLayout.LayoutParams).gravity = if (fromUser) Gravity.END else Gravity.START
            return VH(view, tv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val m = items[position]
            holder.text.text = if (m.text.isEmpty() && m.running) "…" else m.text
            holder.text.setOnLongClickListener {
                if (m.text.isNotEmpty()) onCopy(m.text.toString())
                true
            }
        }
    }
}
