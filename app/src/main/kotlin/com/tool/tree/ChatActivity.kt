package com.tool.tree

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import android.widget.LinearLayout
import android.widget.ListPopupWindow
import android.widget.ProgressBar
import android.widget.TableLayout
import android.widget.TableRow
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.omarea.common.ui.DialogHelper
import com.omarea.krscript.config.IconPathAnalysis
import com.omarea.krscript.config.ActionParamMemory
import com.omarea.krscript.config.ActionShowMemory
import com.omarea.krscript.executor.ScriptEnvironmen
import com.omarea.krscript.executor.ShellExecutor
import com.omarea.common.ui.BlurTopBarLayout
import com.tool.tree.ui.SwipeBackHelper
import com.tool.tree.ui.SwipeBackPreviewCache
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
import com.tool.tree.ui.SyntaxHighlighterFactory

class ChatActivity : AppCompatActivity(), KrScriptActionHandler {
    companion object {
        private const val EXTRA_CHAT = "chat"
        private const val MAX_REPLY_CHARS = 20000
        private const val REFRESH_DELAY_MS = 60L
        private val CLOSE_TYPES = setOf("exit", "finish", "close")
        private val UNSUPPORTED_TYPES = setOf("file", "folder", "spinner", "checkbox", "refresh", "reload", "restart", "killapp")

        private const val SWIPE_HORIZONTAL_RATIO = 2f
        private const val CLOSE_SESSIONS_SWIPE_DP = 48

        private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
        private val activeRuns = HashMap<String, ActiveRun>()

        private fun persistTo(store: ChatSessionStore, target: ChatSession) {
            val snapshot = target.messages
                .filter { !it.transient && (it.fromUser || it.text.isNotEmpty()) }
                .map { ChatSessionStore.Stored(it.fromUser, it.text.toString(), ChatSpanCodec.encode(it.text), it.durationMs) }
            try {
                ioExecutor.execute { store.save(target.id, snapshot) }
            } catch (_: Exception) {
            }
        }

        fun start(context: Context, node: ChatNode) {
            val intent = Intent(context, ChatActivity::class.java).apply {
                putExtra(EXTRA_CHAT, node)
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context is Activity) {
                SwipeBackPreviewCache.capture(context) { context.startActivity(intent) }
            } else {
                context.startActivity(intent)
            }
        }
    }

    private lateinit var node: ChatNode
    private lateinit var adapter: ChatAdapter
    private lateinit var listView: RecyclerView
    private lateinit var inputView: EditText
    private var pendingAutoShowIme = false
    private var greetingCache: CharSequence? = null
    private lateinit var sendButton: ImageButton
    private var actionHost: ActionListFragment? = null
    private val uiHandler = Handler(Looper.getMainLooper())

    private val chatParams = HashMap<String, String>()
    @Volatile private var running = false
    private var activeRun: ActiveRun? = null
    private var refreshPending = false
    private var refreshTarget: ChatMessage? = null

    private lateinit var store: ChatSessionStore
    private lateinit var session: ChatSession
    private lateinit var drawer: DrawerLayout
    private lateinit var swipeBackHelper: SwipeBackHelper
    private var swipePreview: SwipeBackPreviewCache.Preview? = null
    private lateinit var sessionDrawer: ChatSessionDrawer
    private val shellStarter: ExecutorService = Executors.newSingleThreadExecutor()
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
        setupSwipeBack()

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
        store = ChatSessionStore(this, pageKey(), node.title.ifEmpty { getString(R.string.chat_default_title) })
        val attached = activeRuns[pageKey()]
        val latest = if (attached == null) store.list().firstOrNull() else null
        session = attached?.session ?: (if (latest != null) openStored(latest.id) else newEmptySession())
        if (attached != null) {
            activeRun = attached
            running = true
            bindRunUi(attached)
        }
        adapter.bind(session.messages)
        scrollToEnd()

        inputView = findViewById(R.id.chat_input)
        inputView.hint = node.placeholder.ifEmpty { getString(R.string.chat_input_hint) }
        inputView.maxLines = node.maxLines
        pendingAutoShowIme = savedInstanceState == null
        sendButton = findViewById(R.id.chat_send)
        sendButton.setOnClickListener { if (running) stopRunning() else send() }
        updateSendButton()
        setupOptionsButton()
        setupClearButton()
        setupInputExpand()
        setupDrawer()
        if (savedInstanceState == null) runGreeting()

        val bar = findViewById<ChatBottomBarLayout>(R.id.chat_input_bar)
        val topBar = findViewById<BlurTopBarLayout>(R.id.blur_top_container)
        val panel = findViewById<View>(R.id.chat_drawer_panel)
        topBar.blurSource = listView
        bar.blurSource = listView
        val updateListInsets = {
            val gap = (8 * resources.displayMetrics.density).toInt()
            val top = topBar.height + gap
            val bottom = bar.height + gap
            if (listView.paddingTop != top || listView.paddingBottom != bottom) {
                val stick = !listView.canScrollVertically(1)
                listView.setPadding(listView.paddingLeft, top, listView.paddingRight, bottom)
                if (stick) scrollToEnd()
            }
        }
        topBar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateListInsets() }
        bar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateListInsets() }
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

        node.paramsNode?.let { paramsNode ->
            if (paramsNode.show && !ActionShowMemory.isConfirmed(this, paramsNode)) {
                pendingAutoShowIme = false
                window.decorView.post {
                    if (isFinishing || isDestroyed) return@post
                    actionHost?.takeIf { it.isAdded }?.onActionClick(paramsNode, Runnable {}, true)
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && pendingAutoShowIme && ::inputView.isInitialized) {
            pendingAutoShowIme = false
            inputView.requestFocus()
            inputView.post {
                WindowCompat.getInsetsController(window, inputView).show(WindowInsetsCompat.Type.ime())
            }
        }
    }

    private fun setupSwipeBack() {
        val previewBlur = findViewById<ImageView>(R.id.chat_swipe_preview_blur)
        val previewSharp = findViewById<ImageView>(R.id.chat_swipe_preview_sharp)
        swipePreview = SwipeBackPreviewCache.consume()
        swipePreview?.let {
            previewSharp.setImageBitmap(it.sharp)
            previewBlur.setImageBitmap(it.blurred ?: it.sharp)
        }
        swipeBackHelper = SwipeBackHelper(
            activity = this,
            contentView = findViewById(R.id.chat_drawer),
            onDragStateChanged = { dragging ->
                if (swipePreview != null) {
                    val visibility = if (dragging) View.VISIBLE else View.GONE
                    previewBlur.visibility = visibility
                    previewSharp.visibility = visibility
                    if (!dragging) previewSharp.alpha = 0f
                }
            },
            onDragProgress = { progress -> previewSharp.alpha = progress * progress },
            canStart = { !drawer.isDrawerVisible(GravityCompat.END) },
            allowLeftPull = false,
            canDrag = { ev -> canSwipeNow(ev) },
            horizontalRatio = SWIPE_HORIZONTAL_RATIO
        )
    }

    private var hScrollUnderFinger: ChatHScrollView? = null
    private var swipeDownRawX = 0f

    private fun findChatHScroll(view: View, x: Int, y: Int): ChatHScrollView? {
        if (view.visibility != View.VISIBLE) return null
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        if (x < loc[0] || x >= loc[0] + view.width || y < loc[1] || y >= loc[1] + view.height) return null
        if (view is ChatHScrollView) return view
        if (view is ViewGroup) {
            for (i in view.childCount - 1 downTo 0) {
                val found = findChatHScroll(view.getChildAt(i), x, y)
                if (found != null) return found
            }
        }
        return null
    }

    private fun canSwipeNow(ev: MotionEvent): Boolean {
        if (ev.eventTime - ev.downTime >= ViewConfiguration.getLongPressTimeout()) return false
        val scroller = hScrollUnderFinger
        if (scroller != null) {
            val dx = ev.rawX - swipeDownRawX
            if (dx > 0 && scroller.canScrollHorizontally(-1)) return false
            if (dx < 0 && scroller.canScrollHorizontally(1)) return false
        }
        val focused = currentFocus as? TextView
        return focused == null || !focused.hasSelection()
    }

    private var closeSwipeTracking = false
    private var closeSwipeDownX = 0f
    private var closeSwipeDownY = 0f

    private fun handleCloseSessionsSwipe(ev: MotionEvent): Boolean {
        if (!::drawer.isInitialized) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val panel = findViewById<View>(R.id.chat_drawer_panel)
                closeSwipeTracking = drawer.isDrawerOpen(GravityCompat.END) && ev.rawX < panel.left
                closeSwipeDownX = ev.rawX
                closeSwipeDownY = ev.rawY
            }
            MotionEvent.ACTION_MOVE -> if (closeSwipeTracking) {
                val dx = ev.rawX - closeSwipeDownX
                val dy = ev.rawY - closeSwipeDownY
                if (dx >= CLOSE_SESSIONS_SWIPE_DP * resources.displayMetrics.density && dx > Math.abs(dy) * SWIPE_HORIZONTAL_RATIO) {
                    closeSwipeTracking = false
                    val cancelEvent = MotionEvent.obtain(ev)
                    cancelEvent.action = MotionEvent.ACTION_CANCEL
                    super.dispatchTouchEvent(cancelEvent)
                    cancelEvent.recycle()
                    drawer.closeDrawer(GravityCompat.END)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> closeSwipeTracking = false
        }
        return false
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            swipeDownRawX = ev.rawX
            hScrollUnderFinger = findChatHScroll(window.decorView, ev.rawX.toInt(), ev.rawY.toInt())
        }
        if (handleCloseSessionsSwipe(ev)) return true
        if (::swipeBackHelper.isInitialized && swipeBackHelper.dispatchTouchEvent(ev)) return true
        return super.dispatchTouchEvent(ev)
    }

    private fun recycleImageBitmap(imageView: ImageView) {
        val drawable = imageView.drawable
        if (drawable is android.graphics.drawable.BitmapDrawable) {
            imageView.setImageDrawable(null)
            drawable.bitmap?.takeIf { !it.isRecycled }?.recycle()
        }
    }

    override fun onStop() {
        super.onStop()
        if (::session.isInitialized) saveSession(session)
    }

    override fun onDestroy() {
        super.onDestroy()
        uiHandler.removeCallbacksAndMessages(null)
        activeRun?.let {
            it.onChanged = null
            it.onFinished = null
        }
        if (::session.isInitialized) saveSession(session)
        shellStarter.shutdown()
        if (::swipeBackHelper.isInitialized) swipeBackHelper.release()
        if (isFinishing) {
            findViewById<ImageView>(R.id.chat_swipe_preview_blur)?.let { recycleImageBitmap(it) }
            findViewById<ImageView>(R.id.chat_swipe_preview_sharp)?.let { recycleImageBitmap(it) }
        }
    }

    private fun newSession(id: String): ChatSession {
        val s = ChatSession(id)
        if (node.greetingSh.isBlank()) {
            if (node.greeting.isNotEmpty()) s.messages.add(ChatMessage(false, node.greeting).apply { transient = true })
        } else {
            greetingCache?.let { cached ->
                s.messages.add(ChatMessage(false, SpannableStringBuilder(cached)).apply {
                    transient = true
                    title = node.chatTitle.ifEmpty { null }
                })
            }
        }
        return s
    }

    private fun runGreeting() {
        if (node.greetingSh.isBlank() || running) return
        val owner = session
        val msg = ChatMessage(false, "").apply {
            transient = true
            running = true
            title = node.chatTitle.ifEmpty { null }
        }
        owner.messages.add(0, msg)
        adapter.bind(owner.messages)
        scrollToEnd()
        runScript("", msg, owner, node.greetingSh, true) {
            if (msg.text.isEmpty()) {
                owner.messages.remove(msg)
                if (node.greeting.isNotEmpty()) {
                    owner.messages.add(0, ChatMessage(false, node.greeting).apply { transient = true })
                }
            } else {
                greetingCache = SpannableStringBuilder(msg.text)
            }
            if (owner === session) adapter.bind(owner.messages)
        }
    }

    private fun newEmptySession(): ChatSession = newSession(store.newId())

    private fun openStored(id: String): ChatSession {
        val s = newSession(id)
        for (m in store.load(id)) {
            s.messages.add(
                ChatMessage(m.fromUser, m.text).apply {
                    ChatSpanCodec.apply(text, m.spans)
                    durationMs = m.durationMs
                    title = (if (m.fromUser) node.userTitle else node.chatTitle).ifEmpty { null }
                }
            )
        }
        return s
    }

    private fun saveSession(target: ChatSession) {
        if (target.id in deletedIds) return
        persistTo(store, target)
    }

    private fun pageKey(): String = node.currentPageConfigPath + "\u0000" + node.title

    private fun bindRunUi(run: ActiveRun) {
        run.onChanged = { scheduleRefresh(run.reply) }
        run.onFinished = {
            running = false
            if (activeRun === run) activeRun = null
            if (session === run.session) refreshNow(run.reply)
            updateSendButton()
            saveSession(run.session)
        }
    }

    private fun setupDrawer() {
        drawer = findViewById(R.id.chat_drawer)
        findViewById<TextView>(R.id.chat_drawer_title).setText(R.string.chat_sessions_title)
        val newButton = findViewById<TextView>(R.id.chat_new_session)
        newButton.setText(R.string.terminal_new_session)
        sessionDrawer = ChatSessionDrawer(
            findViewById(R.id.chat_session_list),
            newButton,
            onSelect = { info -> if (info.id != session.id) switchTo(openStored(info.id)) else drawer.closeDrawer(GravityCompat.END) },
            onDelete = { info -> confirmDelete(info) },
            onNew = { startNewSession() }
        )
        // Không cho vuốt từ mép phải để mở ngăn kéo; chỉ mở bằng nút. Khi đã mở thì vẫn vuốt để đóng được.
        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END)
        drawer.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.END)
            }

            override fun onDrawerClosed(drawerView: View) {
                drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END)
            }
        })
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
        DialogHelper.confirm(this, info.name, getString(R.string.chat_delete_session_confirm), Runnable {
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

    private var inputExpanded = false
    private var inputSingleAvail = 0

    /**
     * Khi chữ cần từ 2 dòng trở lên: chuyển ô nhập thành dạng 2 hàng
     * (chữ chiếm toàn bộ chiều rộng ở hàng trên, nút tuỳ chọn/gửi xuống hàng dưới)
     * để không bị thừa khoảng trống hai bên. Khi chữ vừa 1 dòng lại thì trả về dạng 1 hàng.
     */
    private fun setupInputExpand() {
        val box = findViewById<ConstraintLayout>(R.id.chat_input_box)
        val density = resources.displayMetrics.density
        val sendW = (44 * density).toInt()
        val optionsW = (44 * density).toInt()
        val optionsView = findViewById<View>(R.id.chat_options)
        box.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            val opt = if (optionsView.visibility == View.VISIBLE) optionsW else 0
            val avail = (right - left) - opt - sendW - inputSingleStartPad() - inputSingleEndPad()
            if (avail != inputSingleAvail) {
                inputSingleAvail = avail
                updateInputLayoutMode()
            }
        }
        inputView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateInputLayoutMode()
                inputView.post { updateClearButton() }
            }
        })
        inputView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateClearButton() }
    }

    private fun setupClearButton() {
        val button = findViewById<TextView>(R.id.chat_clear)
        button.text = getString(R.string.chat_clear_input)
        val tint = TypedValue()
        if (theme.resolveAttribute(android.R.attr.textColorSecondary, tint, true) && tint.resourceId != 0) {
            button.compoundDrawableTintList = ContextCompat.getColorStateList(this, tint.resourceId)
        }
        button.setOnClickListener { inputView.setText("") }
    }

    /** Nút xóa chỉ hiện khi ô nhập đang ở dạng mở rộng và có từ 2 dòng trở lên. */
    private fun updateClearButton() {
        if (!::inputView.isInitialized) return
        val button = findViewById<View>(R.id.chat_clear) ?: return
        val wanted = if (inputExpanded && inputView.lineCount >= 2) View.VISIBLE else View.GONE
        if (button.visibility != wanted) button.visibility = wanted
    }

    private fun inputSingleStartPad() = (8 * resources.displayMetrics.density).toInt()
    private fun inputSingleEndPad() = (4 * resources.displayMetrics.density).toInt()

    private fun updateInputLayoutMode() {
        if (!::inputView.isInitialized) return
        val avail = inputSingleAvail
        if (avail <= 0) return
        val text = inputView.text?.toString().orEmpty()
        val hasBreak = text.indexOf('\n') >= 0
        val width = inputView.paint.measureText(text)
        val margin = 8 * resources.displayMetrics.density
        val wantExpanded = if (inputExpanded) {
            hasBreak || width > avail - margin
        } else {
            hasBreak || width > avail
        }
        if (wantExpanded != inputExpanded) applyInputMode(wantExpanded)
    }

    private fun applyInputMode(expanded: Boolean) {
        inputExpanded = expanded
        val box = findViewById<ConstraintLayout>(R.id.chat_input_box)
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val input = R.id.chat_input
        val options = R.id.chat_options
        val send = R.id.chat_send
        val clearBtn = R.id.chat_clear
        val parent = ConstraintSet.PARENT_ID
        ConstraintSet().apply {
            clone(box)
            if (expanded) {
                connect(input, ConstraintSet.START, parent, ConstraintSet.START)
                connect(input, ConstraintSet.END, parent, ConstraintSet.END)
                connect(input, ConstraintSet.TOP, parent, ConstraintSet.TOP)
                clear(input, ConstraintSet.BOTTOM)
                connect(options, ConstraintSet.TOP, input, ConstraintSet.BOTTOM)
                connect(send, ConstraintSet.TOP, input, ConstraintSet.BOTTOM)
                connect(clearBtn, ConstraintSet.TOP, input, ConstraintSet.BOTTOM)
            } else {
                connect(input, ConstraintSet.START, options, ConstraintSet.END)
                connect(input, ConstraintSet.END, send, ConstraintSet.START)
                connect(input, ConstraintSet.TOP, parent, ConstraintSet.TOP)
                connect(input, ConstraintSet.BOTTOM, parent, ConstraintSet.BOTTOM)
                clear(options, ConstraintSet.TOP)
                clear(send, ConstraintSet.TOP)
                clear(clearBtn, ConstraintSet.TOP)
            }
            applyTo(box)
        }
        if (expanded) {
            inputView.setPaddingRelative(dp(16), dp(12), dp(16), dp(8))
        } else {
            inputView.setPaddingRelative(inputSingleStartPad(), dp(10), inputSingleEndPad(), dp(10))
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

    private fun runScript(
        text: String,
        reply: ChatMessage,
        owner: ChatSession,
        script: String = node.script,
        quietEmpty: Boolean = false,
        onDone: (() -> Unit)? = null
    ) {
        running = true
        reply.startMs = SystemClock.elapsedRealtime()
        reply.durationMs = -1L
        updateSendButton()
        AnsiColorParser.reset()
        val run = ActiveRun(pageKey(), owner, reply, store)
        run.onDone = onDone
        activeRuns[run.pageKey] = run
        activeRun = run
        val handler = ChatShellHandler(
            applicationContext,
            reply,
            { run.onChanged?.invoke() },
            { run.stopRunnable = it },
            {
                if (activeRuns[run.pageKey] === run) activeRuns.remove(run.pageKey)
                reply.running = false
                reply.durationMs = if (reply.startMs > 0) SystemClock.elapsedRealtime() - reply.startMs else -1L
                run.onDone?.invoke()
                val ui = run.onFinished
                if (ui != null) ui() else persistTo(run.store, run.session)
            },
            quietEmpty
        )
        run.handler = handler
        bindRunUi(run)
        val params = HashMap(chatParams).apply {
            put("MSG", text)
            put("CHAT_ID", owner.id)
        }
        val appContext = applicationContext
        shellStarter.execute {
            val started = try {
                ShellExecutor().execute(appContext, node, script, null, params, handler) != null
            } catch (ex: Throwable) {
                false
            }
            if (!started) uiHandler.post { handler.finishNow(-1) }
        }
    }

    private fun stopRunning() {
        val run = activeRun ?: return
        run.stopRunnable?.run()
        val handler = run.handler ?: return
        uiHandler.postDelayed({ if (running && activeRun === run) handler.finishNow(-1) }, 1500)
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

        val sessionsButton = toolbarButton(R.drawable.ic_chat_sessions, getString(R.string.chat_sessions_title))
        sessionsButton.setOnClickListener { toggleDrawer() }
        val sessionsItem = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, getString(R.string.chat_sessions_title))
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
        val rows = ArrayList<PopupMenuRow>(options.map { buildRow(it) })
        val adapter = PopupMenuListAdapter(this, rows)
        val background = ContextCompat.getDrawable(this, R.drawable.kr_spinner_popup_bg)

        val popup = ListPopupWindow(this)
        popup.anchorView = anchor
        popup.setAdapter(adapter)
        popup.setBackgroundDrawable(background)
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
            option.type == "clear" -> {
                val wasRunning = running
                clearChat()
                if (option.script.isNotBlank()) {
                    if (wasRunning) uiHandler.postDelayed({ runSilent(option) }, 1600) else runSilent(option)
                }
            }
            option.type in CLOSE_TYPES -> finish()
            option.silent -> runSilent(option)
            else -> showLog(option)
        }
    }

    private fun menuParams(option: PageMenuOption) = HashMap(chatParams).apply {
        put("state", option.key)
        put("menu_id", option.key)
        put("CHAT_ID", session.id)
    }

    private fun runSilent(option: PageMenuOption) {
        val appContext = applicationContext
        val params = menuParams(option)
        Thread {
            try {
                ScriptEnvironmen.executeResultRoot(appContext, option.script, option, params)
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

    private class ActiveRun(val pageKey: String, val session: ChatSession, val reply: ChatMessage, val store: ChatSessionStore) {
        @Volatile var stopRunnable: Runnable? = null
        var handler: ChatShellHandler? = null
        var onChanged: (() -> Unit)? = null
        var onFinished: (() -> Unit)? = null
        var onDone: (() -> Unit)? = null
    }

    private class ChatMessage(val fromUser: Boolean, initial: CharSequence) {
        val text = SpannableStringBuilder(initial)
        var title: CharSequence? = null
        var transient = false
        var running = false
        var progressCur = 0
        var progressTotal = 0
        var startMs = 0L
        var durationMs = -1L
    }

    private class ChatShellHandler(
        context: Context,
        private val message: ChatMessage,
        private val onChanged: () -> Unit,
        private val onForceStop: (Runnable?) -> Unit,
        private val onFinished: () -> Unit,
        private val quietEmpty: Boolean = false
    ) : SilentShellOutputHandler(context) {
        private val eraseAtStart = Regex("^\u001B\\[([0-2]?)K")
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
            if (pendingOverwrite && lineStart in 0..buf.length) {
                buf.delete(lineStart, buf.length)
                pendingOverwrite = false
            }
            while (buf.isNotEmpty() && buf.last() == '\n') buf.delete(buf.length - 1, buf.length)
            if (quietEmpty && buf.isEmpty()) {
                // greeting-sh: no output -> no bubble
            } else if (code != 0) {
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
            var raw = msg.toString()
            val erase = eraseAtStart.find(raw)
            if (erase != null) {
                val buf = message.text
                if ((pendingOverwrite || erase.groupValues[1] == "2") && lineStart in 0..buf.length) {
                    buf.delete(lineStart, buf.length)
                    pendingOverwrite = false
                }
                raw = raw.substring(erase.range.last + 1)
                if (raw.isEmpty()) {
                    onChanged()
                    return
                }
            }
            var parsed: CharSequence = ChatAnsi.stripDefaultColor(AnsiColorParser.parse(raw))
            if (forcedColor != null && !raw.contains("\u001B[")) {
                parsed = SpannableStringBuilder(parsed).apply {
                    setSpan(ForegroundColorSpan(forcedColor), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            append(parsed)
            onChanged()
        }

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
        private val TICK_MS = 100L
        private val ticker = Handler(Looper.getMainLooper())
        private var tickerOn = false
        private val tick = object : Runnable {
            override fun run() {
                val rv = activity.listView
                for (i in 0 until rv.childCount) {
                    val h = rv.getChildViewHolder(rv.getChildAt(i)) as? VH ?: continue
                    val pos = h.bindingAdapterPosition
                    if (pos in items.indices && items[pos].running) bindTitle(h, items[pos])
                }
                if (items.any { it.running }) ticker.postDelayed(this, TICK_MS) else tickerOn = false
            }
        }

        private fun startTicker() {
            if (tickerOn) return
            tickerOn = true
            ticker.postDelayed(tick, TICK_MS)
        }

        override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
            ticker.removeCallbacks(tick)
            tickerOn = false
            super.onDetachedFromRecyclerView(recyclerView)
        }

        private fun formatElapsed(ms: Long, finalValue: Boolean): String {
            val total = ms / 1000
            return when {
                total >= 3600 -> "${total / 3600}h " + ((total % 3600) / 60).toString().padStart(2, '0') + "m"
                total >= 60 -> "${total / 60}m " + (total % 60).toString().padStart(2, '0') + "s"
                else -> "$total.${(ms / 100) % 10}s"
            }
        }

        private fun bindTitle(holder: VH, m: ChatMessage) {
            val base = m.title?.toString()
            val time = when {
                m.fromUser || m.transient -> null
                m.running && m.startMs > 0 -> formatElapsed(SystemClock.elapsedRealtime() - m.startMs, false)
                !m.running && m.durationMs >= 0 -> formatElapsed(m.durationMs, true)
                else -> null
            }
            val label = when {
                time == null -> base
                base.isNullOrEmpty() -> time
                else -> "$base \u2022 $time"
            }
            holder.title.visibility = if (label != null) View.VISIBLE else View.GONE
            holder.title.text = label
        }

        @android.annotation.SuppressLint("NotifyDataSetChanged")
        fun bind(list: ArrayList<ChatMessage>) {
            items = list
            notifyDataSetChanged()
        }

        class VH(
            view: View,
            val bubble: View,
            val title: TextView,
            val text: TextView,
            val blocks: LinearLayout,
            val progress: ProgressBar,
            val dots: TypingDotsView
        ) : RecyclerView.ViewHolder(view)

        private fun dp(view: View, value: Float) = (value * view.resources.displayMetrics.density).toInt()

        private fun buildTextBlock(container: LinearLayout, holder: VH, source: CharSequence, first: Boolean, last: Boolean): View {
            val tv = ChatTextView(container.context)
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            tv.setTextColor(holder.text.currentTextColor)
            tv.setPadding(dp(tv, 14f), dp(tv, if (first) 9f else 3f), dp(tv, 14f), dp(tv, if (last) 9f else 3f))
            tv.maxWidth = holder.text.maxWidth
            tv.setTextIsSelectable(true)
            tv.movementMethod = ChatMarkdown.SelectableLinkMovement
            tv.setLinkTextColor(activity.accentColor())
            tv.text = ChatMarkdown.linkify(ChatMarkdown.render(tv.context, source))
            return tv
        }

        private fun buildCodeBlock(container: LinearLayout, holder: VH, code: CharSequence, lang: String): View {
            val ctx = container.context
            val textColor = holder.text.currentTextColor
            val card = FrameLayout(ctx)
            card.minimumHeight = dp(card, 36f)
            card.background = GradientDrawable().apply {
                cornerRadius = 10 * ctx.resources.displayMetrics.density
                setColor(0x44808080)
            }

            val tv = ChatTextView(ctx)
            tv.typeface = Typeface.MONOSPACE
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            tv.setTextColor(textColor)
            tv.setPadding(dp(tv, 10f), dp(tv, 8f), dp(tv, 38f), dp(tv, 8f))
            if (Build.VERSION.SDK_INT >= 23) tv.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
            tv.setTextIsSelectable(true)
            val colored = SpannableStringBuilder(code)
            val lightToml = lang.equals("toml", ignoreCase = true) && !ThemeModeState.getThemeMode().isDarkMode
            if (!lightToml && colored.getSpans(0, colored.length, CharacterStyle::class.java).isEmpty()) {
                SyntaxHighlighterFactory.highlightSpannable(lang, colored)
            }
            tv.text = colored
            val codeScroll = ChatHScrollView(ctx)
            codeScroll.maxWidthPx = holder.text.maxWidth - dp(card, 20f)
            codeScroll.isHorizontalScrollBarEnabled = false
            codeScroll.overScrollMode = View.OVER_SCROLL_NEVER
            codeScroll.addView(tv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            card.addView(codeScroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            val icon = ImageView(ctx)
            icon.setImageResource(R.drawable.ic_chat_copy)
            icon.imageTintList = ColorStateList.valueOf((textColor and 0x00FFFFFF) or (0xB3 shl 24))
            icon.setPadding(dp(icon, 7f), dp(icon, 7f), dp(icon, 7f), dp(icon, 7f))
            icon.contentDescription = ctx.getString(R.string.btn_copy_output)
            val ripple = TypedValue()
            if (ctx.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true) && ripple.resourceId != 0) {
                icon.background = ContextCompat.getDrawable(ctx, ripple.resourceId)
            }
            val plain = code.toString()
            icon.setOnClickListener { onCopy(plain) }
            card.addView(icon, FrameLayout.LayoutParams(dp(icon, 34f), dp(icon, 34f), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(icon, 2f)
                marginEnd = dp(icon, 2f)
            })
            return card
        }

        private fun dividerDrawable(color: Int, sizePx: Int) = GradientDrawable().apply {
            setColor(color)
            setSize(sizePx, sizePx)
        }

        private fun buildTableBlock(container: LinearLayout, holder: VH, table: ChatMarkdown.Table, raw: CharSequence): View {
            val ctx = container.context
            val density = ctx.resources.displayMetrics.density
            val line = (1 * density).toInt().coerceAtLeast(1)
            val lineColor = 0x55808080
            val textColor = holder.text.currentTextColor
            val cols = table.header.size
            val colMax = (holder.text.maxWidth * 0.7f).toInt()

            val card = FrameLayout(ctx)
            card.background = GradientDrawable().apply {
                cornerRadius = 10 * density
                setColor(0x14808080)
                setStroke(line, lineColor)
            }
            card.clipToOutline = true

            val tl = TableLayout(ctx)
            tl.showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
            tl.dividerDrawable = dividerDrawable(lineColor, line)

            fun addRow(cells: List<CharSequence>, header: Boolean) {
                val tr = TableRow(ctx)
                tr.showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
                tr.dividerDrawable = dividerDrawable(lineColor, line)
                if (header) tr.setBackgroundColor(0x22808080)
                for (c in 0 until cols) {
                    val tv = ChatTextView(ctx)
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    tv.setTextColor(textColor)
                    tv.setPadding(dp(tv, 10f), dp(tv, 6f), dp(tv, if (header && c == cols - 1) 38f else 10f), dp(tv, 6f))
                    tv.maxWidth = colMax
                    tv.gravity = when (table.aligns.getOrElse(c) { 0 }) { 1 -> Gravity.CENTER_HORIZONTAL; 2 -> Gravity.END; else -> Gravity.START }
                    if (header) tv.setTypeface(tv.typeface, Typeface.BOLD)
                    tv.setTextIsSelectable(true)
                    tv.movementMethod = ChatMarkdown.SelectableLinkMovement
                    tv.setLinkTextColor(activity.accentColor())
                    tv.text = ChatMarkdown.linkify(ChatMarkdown.renderInline(ctx, cells.getOrElse(c) { "" }))
                    tr.addView(tv, TableRow.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                }
                tl.addView(tr, TableLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            addRow(table.header, true)
            table.rows.forEach { addRow(it, false) }

            val scroll = ChatHScrollView(ctx)
            scroll.maxWidthPx = holder.text.maxWidth - dp(card, 20f)
            scroll.isHorizontalScrollBarEnabled = false
            scroll.overScrollMode = View.OVER_SCROLL_NEVER
            scroll.addView(tl, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            card.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            val icon = ImageView(ctx)
            icon.setImageResource(R.drawable.ic_chat_copy)
            icon.imageTintList = ColorStateList.valueOf((textColor and 0x00FFFFFF) or (0xB3 shl 24))
            icon.setPadding(dp(icon, 7f), dp(icon, 7f), dp(icon, 7f), dp(icon, 7f))
            icon.contentDescription = ctx.getString(R.string.btn_copy_output)
            val ripple = TypedValue()
            if (ctx.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true) && ripple.resourceId != 0) {
                icon.background = ContextCompat.getDrawable(ctx, ripple.resourceId)
            }
            val plain = raw.toString()
            icon.setOnClickListener { onCopy(plain) }
            card.addView(icon, FrameLayout.LayoutParams(dp(icon, 34f), dp(icon, 34f), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(icon, 1f)
                marginEnd = dp(icon, 2f)
            })
            return card
        }

        private fun bindBlocks(holder: VH, blocks: List<ChatMarkdown.Block>) {
            val container = holder.blocks
            container.removeAllViews()
            blocks.forEachIndexed { index, block ->
                val first = index == 0
                val last = index == blocks.lastIndex
                if (block.isTable) {
                    val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginStart = dp(container, 10f)
                        marginEnd = dp(container, 10f)
                        topMargin = dp(container, if (first) 10f else 3f)
                        bottomMargin = dp(container, if (last) 10f else 3f)
                    }
                    container.addView(buildTableBlock(container, holder, block.table!!, block.text), params)
                } else if (block.isCode) {
                    val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginStart = dp(container, 10f)
                        marginEnd = dp(container, 10f)
                        topMargin = dp(container, if (first) 10f else 3f)
                        bottomMargin = dp(container, if (last) 10f else 3f)
                    }
                    container.addView(buildCodeBlock(container, holder, block.text, block.lang), params)
                } else {
                    container.addView(
                        buildTextBlock(container, holder, block.text, first, last),
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    )
                }
            }
        }

        fun add(message: ChatMessage) {
            items.add(message)
            notifyItemInserted(items.size - 1)
        }

        @android.annotation.SuppressLint("NotifyDataSetChanged")
        fun refreshAll() {
            notifyDataSetChanged()
        }

        fun changed(message: ChatMessage) {
            val index = items.indexOf(message)
            if (index >= 0) notifyItemChanged(index)
        }

        private fun visibleText(text: SpannableStringBuilder): CharSequence {
            var end = text.length
            while (end > 0 && (text[end - 1] == '\n' || text[end - 1] == '\r')) end--
            var start = 0
            var i = 0
            while (i < end) {
                val c = text[i]
                if (c == '\n') start = i + 1
                else if (c != '\r' && c != ' ' && c != '\t') break
                i++
            }
            if (i >= end) start = end
            return if (start == 0 && end == text.length) text else text.subSequence(start, end)
        }

        override fun getItemCount() = items.size
        override fun getItemViewType(position: Int) = if (items[position].fromUser) 1 else 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
            val column = view.findViewById<View>(R.id.chat_column)
            val bubble = view.findViewById<ChatBubbleLayout>(R.id.chat_bubble)
            val title = view.findViewById<TextView>(R.id.chat_title)
            val tv = view.findViewById<TextView>(R.id.chat_text)
            val blocks = view.findViewById<LinearLayout>(R.id.chat_blocks)
            val progress = view.findViewById<ProgressBar>(R.id.chat_progress)
            val dots = view.findViewById<TypingDotsView>(R.id.chat_dots)
            dots.setDotColor(tv.currentTextColor)
            dots.minimumHeight = tv.lineHeight + tv.paddingTop + tv.paddingBottom
            val density = parent.resources.displayMetrics.density
            val fromUser = viewType == 1
            val tint = if (fromUser) (activity.accentColor() and 0x00FFFFFF) or 0x40000000 else 0x26808080
            val bg = GradientDrawable().apply {
                cornerRadius = 18 * density
                setColor(tint)
            }
            bubble.background = bg
            bubble.engine.cornerRadius = 18 * density
            bubble.overlayColor = if (fromUser) tint else 0
            tv.maxWidth = parent.resources.displayMetrics.widthPixels - (24 * density).toInt()
            tv.setTextIsSelectable(true)
            tv.movementMethod = ChatMarkdown.SelectableLinkMovement
            tv.setLinkTextColor(activity.accentColor())
            (column.layoutParams as FrameLayout.LayoutParams).gravity = if (fromUser) Gravity.END else Gravity.START
            if (fromUser) {
                (title.layoutParams as LinearLayout.LayoutParams).apply {
                    gravity = Gravity.END
                    marginStart = 0
                    marginEnd = (8 * density).toInt()
                }
            }
            return VH(view, bubble, title, tv, blocks, progress, dots)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val m = items[position]
            bindTitle(holder, m)
            if (m.running) startTicker()
            val visible = visibleText(m.text)
            val renderMd = activity.node.markdown && !m.fromUser && !m.running
            val showDots = visible.isEmpty() && m.running
            holder.dots.visibility = if (m.running) View.VISIBLE else View.GONE
            if (showDots) {
                holder.dots.minimumHeight = holder.text.lineHeight + holder.text.paddingTop + holder.text.paddingBottom
                holder.dots.setPadding(dp(holder.dots, 14f), 0, dp(holder.dots, 14f), 0)
            } else {
                holder.dots.minimumHeight = 0
                holder.dots.setPadding(dp(holder.dots, 14f), 0, dp(holder.dots, 14f), dp(holder.dots, 8f))
            }
            val blocks = if (renderMd) ChatMarkdown.splitBlocks(visible).takeIf { list -> list.any { it.isCode || it.isTable } } else null
            if (blocks != null) {
                holder.text.visibility = View.GONE
                holder.blocks.visibility = View.VISIBLE
                bindBlocks(holder, blocks)
            } else {
                holder.blocks.removeAllViews()
                holder.blocks.visibility = View.GONE
                holder.text.visibility = if (showDots) View.GONE else View.VISIBLE
                if (!showDots) holder.text.text = ChatMarkdown.linkify(if (renderMd) ChatMarkdown.render(holder.text.context, visible) else visible)
            }
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
        }
    }
}
