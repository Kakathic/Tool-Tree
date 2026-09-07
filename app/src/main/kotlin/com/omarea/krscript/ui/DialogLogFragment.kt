package com.omarea.krscript.ui

import android.app.ActivityManager
import android.app.Dialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import com.omarea.common.ui.DialogHelper
import com.omarea.krscript.config.IconPathAnalysis
import com.omarea.krscript.executor.ShellExecutor
import com.omarea.krscript.model.RunnableNode
import com.omarea.krscript.model.ShellHandlerBase
import com.tool.tree.AnsiColorParser
import com.tool.tree.NotificationCopyLogActivity
import com.tool.tree.R
import com.tool.tree.WakeLockService
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class DialogLogFragment : DialogFragment() {

    private var _binding: com.tool.tree.databinding.KrDialogLogBinding? = null
    private val binding get() = _binding!!

    private var running = false
    private var canceled = false
    private var uiVisible = true
    private var nodeInfo: RunnableNode? = null
    private lateinit var onExit: Runnable
    private lateinit var script: String
    private var params: HashMap<String, String>? = null
    private var themeResId: Int = 0
    private var onDismissRunnable: Runnable? = null
    private var currentHandler: MyShellHandler? = null

    // Khi dialog được mở lại từ thông báo (sau khi bấm "Ẩn"), handler của tiến trình đang
    // chạy đã tồn tại từ trước (được lấy ra từ HiddenTaskRegistry) — trường hợp này ta chỉ
    // gắn lại UI vào handler cũ (reattachExecutor) thay vì khởi chạy lại script từ đầu.
    private var resumeHandler: MyShellHandler? = null

    private var wrapEnabled = true
    private var noWrapContainer: HorizontalScrollView? = null

    // Window đã push vào TopWindowHolder (xem onStart/onDestroyView) -- lưu lại tham chiếu
    // riêng để pop đúng window lúc đóng, không dựa vào dialog?.window (có thể đã null lúc
    // onDestroyView chạy).
    private var attachedWindow: android.view.Window? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = com.tool.tree.databinding.KrDialogLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val style = if (themeResId != 0) themeResId else R.style.kr_full_screen_dialog_light
        return Dialog(requireActivity(), style)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dialog?.window?.let { window ->
            DialogHelper.setWindowBlurBg(window, requireActivity())
        }

        val resumingHandler = resumeHandler
        if (resumingHandler != null) {
            nodeInfo?.let { node ->
                reattachExecutor(node, resumingHandler)
            } ?: dismissAllowingStateLoss()
            return
        }

        nodeInfo?.let { node ->
            val shellHandler = openExecutor(node)

            if (node.reloadPage) {
                binding.btnHide.visibility = View.GONE
            binding.btnDivider.visibility = View.GONE
            }

            ShellExecutor().execute(
                requireContext().applicationContext,
                node,
                script,
                onExit,
                params,
                shellHandler
            )
        } ?: dismissAllowingStateLoss()
    }

    /**
     * Gắn lại giao diện dialog vào một MyShellHandler đang chạy sẵn (được lấy ra từ
     * HiddenTaskRegistry khi người dùng bấm vào thông báo tiến trình đã ẩn), thay vì chạy
     * lại script từ đầu. Log cũ được phát lại toàn bộ vào ô hiển thị, và trạng thái các nút
     * (Hủy/Thoát/tiến trình) được khôi phục theo trạng thái hiện tại của tiến trình.
     */
    private fun reattachExecutor(nodeInfo: RunnableNode, handler: MyShellHandler) {
        canceled = false
        uiVisible = true

        binding.shellOutput.movementMethod = LinkMovementMethod.getInstance()

        wrapEnabled = readWrapEnabled(requireContext().applicationContext)
        applyWrapState()

        setupClickListeners(
            onForceStop = { handler.getForceStop() },
            nodeInfo = nodeInfo
        )

        handler.reattach(
            logView = binding.shellOutput,
            shellProgress = binding.actionProgress,
            inputRow = binding.inputRow,
            shellInput = binding.shellInput,
            chooseRow = binding.chooseRow,
            chooseOptionsContainer = binding.chooseOptionsContainer,
            actionEventHandler = object : IActionEventHandler {
                override fun onStart(forceStop: Runnable?) {
                    running = true
                    canceled = false
                    dialog?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    binding.btnExit.visibility = View.GONE
                    binding.btnCancel.visibility = if (nodeInfo.interruptable && forceStop != null) View.VISIBLE else View.GONE
                    binding.inputRow.visibility = View.GONE
                    binding.chooseRow.visibility = View.GONE
                }

                override fun onSuccess() {
                    if (nodeInfo.autoOff) closeView()
                }

                override fun onCompleted() {
                    running = false
                    onExit.run()
                    offScreen()
                    _binding?.let { b ->
                        val transition = ChangeBounds().apply { duration = 200 }
                        TransitionManager.beginDelayedTransition(
                            b.root.findViewById(R.id.top_actions),
                            transition
                        )
                        b.btnHide.visibility = View.GONE
                        b.btnDivider.visibility = View.GONE
                        b.btnCancel.visibility = View.GONE
                        b.btnExit.visibility = View.VISIBLE
                        b.actionProgress.visibility = View.GONE
                        b.inputRow.visibility = View.GONE
                        b.chooseRow.visibility = View.GONE
                        hideKeyboard(b.shellInput)
                    }
                    isCancelable = true
                }
            }
        )

        this.currentHandler = handler

        if (nodeInfo.reloadPage) {
            binding.btnHide.visibility = View.GONE
            binding.btnDivider.visibility = View.GONE
        }

        // onStart()/onCompleted() của tiến trình đã được gọi từ trước (trong lúc dialog đang
        // ẩn), nên cần tự khôi phục lại trạng thái giao diện hiện tại thay vì chờ callback.
        if (handler.isFinished) {
            binding.btnHide.visibility = View.GONE
            binding.btnDivider.visibility = View.GONE
            binding.btnCancel.visibility = View.GONE
            binding.btnExit.visibility = View.VISIBLE
            binding.actionProgress.visibility = View.GONE
            binding.inputRow.visibility = View.GONE
            binding.chooseRow.visibility = View.GONE
            isCancelable = true
            running = false
        } else {
            dialog?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            binding.btnExit.visibility = View.GONE
            binding.btnCancel.visibility = if (nodeInfo.interruptable && handler.getForceStop() != null) View.VISIBLE else View.GONE
            binding.inputRow.visibility = View.GONE
            binding.chooseRow.visibility = View.GONE
            running = true
        }
    }

    private fun openExecutor(nodeInfo: RunnableNode): ShellHandlerBase {
        var forceStopRunnable: Runnable? = null
        canceled = false
        uiVisible = true

        binding.shellOutput.movementMethod = LinkMovementMethod.getInstance()

        wrapEnabled = readWrapEnabled(requireContext().applicationContext)
        applyWrapState()

        setupClickListeners(
            onForceStop = { forceStopRunnable },
            nodeInfo = nodeInfo
        )

        val handler = MyShellHandler(
            context = requireContext().applicationContext,
            actionEventHandler = object : IActionEventHandler {
                override fun onStart(forceStop: Runnable?) {
                    running = true
                    canceled = false
                    forceStopRunnable = forceStop
                    dialog?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    binding.btnExit.visibility = View.GONE
                    binding.btnCancel.visibility = if (nodeInfo.interruptable && forceStop != null) View.VISIBLE else View.GONE
                    binding.inputRow.visibility = View.GONE
                    binding.chooseRow.visibility = View.GONE
                }

                override fun onSuccess() {
                    if (nodeInfo.autoOff) closeView()
                }

                override fun onCompleted() {
                    running = false
                    onExit.run()
                    offScreen()
                    _binding?.let { b ->
                        val transition = ChangeBounds().apply { duration = 200 }
                        TransitionManager.beginDelayedTransition(
                            b.root.findViewById(R.id.top_actions),
                            transition
                        )
                        b.btnHide.visibility = View.GONE
                        b.btnDivider.visibility = View.GONE
                        b.btnCancel.visibility = View.GONE
                        b.btnExit.visibility = View.VISIBLE
                        b.actionProgress.visibility = View.GONE
                        b.inputRow.visibility = View.GONE
                        b.chooseRow.visibility = View.GONE
                        hideKeyboard(b.shellInput)
                    }
                    isCancelable = true
                }
            },
            logView = binding.shellOutput,
            shellProgress = binding.actionProgress,
            inputRow = binding.inputRow,
            shellInput = binding.shellInput,
            chooseRow = binding.chooseRow,
            chooseOptionsContainer = binding.chooseOptionsContainer
        )

        this.currentHandler = handler
        return handler
    }

    private fun setupClickListeners(onForceStop: () -> Runnable?, nodeInfo: RunnableNode) {
        val nodeInterruptable = nodeInfo.interruptable

        binding.btnWrap.setOnClickListener {
            wrapEnabled = !wrapEnabled
            applyWrapState()
            persistWrapEnabled(requireContext().applicationContext, wrapEnabled)
        }

        binding.btnHide.setOnClickListener {
            uiVisible = false
            val handler = currentHandler
            handler?.enableNotificationMode(nodeInfo)
            handler?.notificationIdOrNull?.let { id ->
                HiddenTaskRegistry.register(
                    id,
                    HiddenTaskEntry(
                        nodeInfo = nodeInfo,
                        handler = handler,
                        onExit = onExit,
                        onDismiss = onDismissRunnable ?: Runnable {},
                        darkMode = themeResId == R.style.kr_full_screen_dialog_dark
                    )
                )
            }
            offScreen()
            closeView()
        }

        binding.btnCancel.setOnClickListener {
            if (running && !canceled) {
                canceled = true
                onForceStop()?.run()
                binding.btnExit.visibility = View.VISIBLE
                binding.btnCancel.visibility = View.GONE
            }
        }

        binding.btnExit.setOnClickListener {
            isCancelable = true
            closeView()
        }

        binding.shellInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                sendUserInput()
                true
            } else {
                false
            }
        }

        binding.btnCopy.setOnClickListener {
            copyLogToClipboard()
        }

        if (nodeInterruptable) {
            binding.btnHide.visibility = View.VISIBLE
            binding.btnDivider.visibility = View.VISIBLE
            binding.btnCancel.visibility = View.VISIBLE
        } else {
            binding.btnHide.visibility = View.GONE
            binding.btnDivider.visibility = View.GONE
            binding.btnCancel.visibility = View.GONE
        }

        binding.title.text = nodeInfo.title.takeIf { it.isNotEmpty() } ?: run {
            binding.title.visibility = View.GONE
            ""
        }
        binding.desc.text = nodeInfo.desc.takeIf { it.isNotEmpty() } ?: run {
            binding.desc.visibility = View.GONE
            ""
        }
        binding.actionProgress.isIndeterminate = true
    }

    private fun sendUserInput() {
        val text = binding.shellInput.text?.toString().orEmpty()
        if (text.isEmpty()) return

        if (currentHandler?.writeInput(text) == true) {
            binding.shellInput.setText("")
        } else {
            Toast.makeText(requireContext(), getString(R.string.input_send_fail), Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyLogToClipboard() {
        runCatching {
            val text = currentHandler?.getLogText(excludeFinishLine = true)
                ?: binding.shellOutput.text.toString()
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("text", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), getString(R.string.copy_success), Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(requireContext(), getString(R.string.copy_fail), Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyWrapState() {
        val b = _binding ?: return
        val logView = b.shellOutput
        val scrollView = b.logScrollView

        (logView.parent as? ViewGroup)?.removeView(logView)
        noWrapContainer?.let { scrollView.removeView(it) }
        logView.setHorizontallyScrolling(!wrapEnabled)

        if (wrapEnabled) {
            logView.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            scrollView.addView(logView)
        } else {
            val hsv = noWrapContainer ?: HorizontalScrollView(requireContext()).also {
                it.isFillViewport = true
                it.isHorizontalScrollBarEnabled = false
                noWrapContainer = it
            }
            logView.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            hsv.removeAllViews()
            hsv.addView(logView)
            hsv.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            scrollView.addView(hsv)
        }

        b.btnWrap.alpha = if (wrapEnabled) 0.6f else 1f
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun readWrapEnabled(context: Context): Boolean {
        return runCatching {
            val file = File(context.filesDir, WRAP_STATE_RELATIVE_PATH)
            if (file.exists()) file.readText().trim() != "1" else true
        }.getOrDefault(true)
    }

    private fun persistWrapEnabled(context: Context, wrapEnabled: Boolean) {
        IO_EXECUTOR.execute {
            runCatching {
                val file = File(context.filesDir, WRAP_STATE_RELATIVE_PATH)
                file.parentFile?.takeIf { !it.exists() }?.mkdirs()
                file.writeText(if (wrapEnabled) "0" else "1")
            }
        }
    }

    private fun hideKeyboard(view: View) {
        runCatching {
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    @FunctionalInterface
    interface IActionEventHandler {
        fun onStart(forceStop: Runnable?)
        fun onSuccess()
        fun onCompleted()
    }

    private class ChoiceSpan(val value: String, private val onTap: (String) -> Unit) : ClickableSpan() {
        override fun onClick(widget: View) {
            onTap(value)
        }

        override fun updateDrawState(ds: TextPaint) {
            super.updateDrawState(ds)
            ds.isUnderlineText = true
        }
    }

    class MyShellHandler(
        context: Context,
        private var actionEventHandler: IActionEventHandler?,
        logView: TextView?,
        shellProgress: ProgressBar?,
        inputRow: View? = null,
        shellInput: EditText? = null,
        chooseRow: View? = null,
        chooseOptionsContainer: LinearLayout? = null
    ) : ShellHandlerBase(context) {

        // var thay vì val: khi dialog được mở lại từ thông báo (reattach), các view này cần
        // được trỏ sang bộ view mới (của dialog vừa được tạo lại) thay vì bộ view cũ đã hủy.
        private var logViewRef = WeakReference(logView)
        private var progressRef = WeakReference(shellProgress)
        private var inputRowRef = WeakReference(inputRow)
        private var shellInputRef = WeakReference(shellInput)
        private var chooseRowRef = WeakReference(chooseRow)
        private var chooseOptionsContainerRef = WeakReference(chooseOptionsContainer)

        private val errorColor = getColor(R.color.kr_shell_log_error)
        private val basicColor = getColor(R.color.kr_shell_log_basic)
        private val scriptColor = getColor(R.color.kr_shell_log_script)
        private val endColor = getColor(R.color.kr_shell_log_end)

        private val choiceLinkColor = Color.parseColor("#4FC3F7")
        private val choiceAnsweredColor = Color.parseColor("#7CFC00")
        private val choiceDisabledColor = Color.parseColor("#808080")
        private var hasError = false
        private var lineCount = 0

        private val logBuffer = SpannableStringBuilder()
        private var lineStart = 0
        private var pendingOverwrite = false

        // Dòng trạng thái kết thúc (thành công/lỗi) được tự thêm vào logBuffer trong onExit().
        // Lưu độ dài của nó để copyLogToClipboard có thể loại trừ khi cần.
        private var hasFinishLine = false
        private var finishLineLength = 0

        private var uiAppliedLength = 0
        private var uiInvalidFrom = Int.MAX_VALUE

        private val pendingUiUpdate = AtomicBoolean(false)

        private var notificationMode = false
        private var notificationId = 0
        private var notificationManager: NotificationManager? = null
        private var notificationTitle = ""
        private var iconPath = ""
        private var logoPath = ""
        private var currentConfigXml = ""
        private var notificationInterruptable = false
        private val notificationRows = ArrayList<String>()
        private var notificationRowsTrimmed = false
        private var notificationFinished = false
        private var notificationProgressCurrent = 0
        private var notificationProgressTotal = 0
        private var forceStopRunnable: Runnable? = null

        // true kể từ khi tiến trình kết thúc (dù dialog có đang hiển thị hay không) — dùng để
        // khôi phục đúng trạng thái giao diện (nút Thoát/Hủy...) khi dialog được mở lại từ
        // thông báo sau khi tiến trình đã hoàn tất trong lúc bị ẩn.
        private var hasExited = false
        val isFinished: Boolean get() = hasExited

        fun getForceStop(): Runnable? = forceStopRunnable

        // Chỉ khác null khi đang ở chế độ thông báo (đã bấm "Ẩn") — dùng để đăng ký vào
        // HiddenTaskRegistry ngay sau khi enableNotificationMode() được gọi.
        val notificationIdOrNull: Int? get() = if (notificationMode) notificationId else null

        private var stopActionName: String? = null
        private var stopReceiver: BroadcastReceiver? = null
        private var stopPendingIntent: PendingIntent? = null

        private var dismissActionName: String? = null
        private var dismissReceiver: BroadcastReceiver? = null
        private var dismissPendingIntent: PendingIntent? = null

        // Debounce Notification Rate Limit
        private val notificationHandler = Handler(Looper.getMainLooper())
        private var pendingNotificationUpdate = false
        private val updateNotificationRunnable = Runnable {
            pendingNotificationUpdate = false
            updateNotificationInternal()
        }

        init {
            logView?.setText("", TextView.BufferType.EDITABLE)
        }

        fun enableNotificationMode(nodeInfo: RunnableNode) {
            if (notificationMode) return
            notificationMode = true

            notificationTitle = nodeInfo.title
            iconPath = nodeInfo.iconPath
            logoPath = nodeInfo.logoPath
            currentConfigXml = nodeInfo.currentPageConfigPath
            notificationInterruptable = nodeInfo.interruptable
            notificationId = nextNotificationId()
            notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

            val existingLines = synchronized(logBuffer) {
                logBuffer.toString().split("\n").filter { it.isNotEmpty() }
            }
            synchronized(notificationRows) {
                existingLines.forEach { notificationRows.add("$it\n") }
                trimNotificationRows()
            }

            if (nodeInfo.interruptable) {
                val actionName = context.packageName + ".TaskStop.Hide." + notificationId
                stopActionName = actionName
                stopPendingIntent = PendingIntent.getBroadcast(
                    context, 0, Intent(actionName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                stopReceiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        forceStopRunnable?.run()
                    }
                }
                runCatching {
                    ContextCompat.registerReceiver(context, stopReceiver, IntentFilter(actionName), ContextCompat.RECEIVER_NOT_EXPORTED)
                }
            }

            val dismissAction = context.packageName + ".TaskDismiss.Hide." + notificationId
            dismissActionName = dismissAction
            dismissPendingIntent = PendingIntent.getBroadcast(
                context, 0, Intent(dismissAction),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            dismissReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    notificationManager?.cancel(notificationId)
                    cleanupNotificationReceivers()
                    HiddenTaskRegistry.unregister(notificationId)
                }
            }
            runCatching {
                ContextCompat.registerReceiver(context, dismissReceiver, IntentFilter(dismissAction), ContextCompat.RECEIVER_NOT_EXPORTED)
            }

            updateNotificationImmediately()
        }

        private fun drawableToIcon(drawable: Drawable?, targetSizePx: Int = 200): Icon? {
            if (drawable == null) return null
            val bitmap = Bitmap.createBitmap(targetSizePx, targetSizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, targetSizePx, targetSizePx)
            drawable.draw(canvas)
            return Icon.createWithBitmap(bitmap)
        }

        private fun buildCopyLogPendingIntent(): PendingIntent {
            val text = synchronized(notificationRows) { notificationRows.joinToString("") }.trim()
            val copyIntent = Intent(context, NotificationCopyLogActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(NotificationCopyLogActivity.EXTRA_LOG_TEXT, text)
            }
            return PendingIntent.getActivity(
                context, notificationId, copyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun cleanupNotificationReceivers() {
            runCatching { stopReceiver?.let { context.unregisterReceiver(it) } }
            stopReceiver = null
            runCatching { dismissReceiver?.let { context.unregisterReceiver(it) } }
            dismissReceiver = null
        }

        private fun trimNotificationRows() {
            if (notificationRows.size > 20) {
                notificationRows.removeAt(0)
                notificationRowsTrimmed = true
            }
        }

        private fun updateNotificationInternal() {
            val nm = notificationManager ?: return
            val id = notificationId

            val shortLog = notificationRows.lastOrNull()?.trim().orEmpty()

            val personIcon = (if (iconPath.isNotEmpty() || logoPath.isNotEmpty()) {
                val tempNode = RunnableNode(currentConfigXml).apply {
                    title = notificationTitle
                    this.iconPath = this@MyShellHandler.iconPath
                    this.logoPath = this@MyShellHandler.logoPath
                }
                val drawable = IconPathAnalysis().loadLogo(context, tempNode, false)
                drawableToIcon(drawable, 200)
            } else {
                // Icon mặc định cũng vẽ qua Bitmap (drawableToIcon) thay vì dùng thẳng resource,
                // tránh hiển thị vuông không đồng nhất với icon tùy chỉnh (iconPath/logoPath)
                drawableToIcon(ContextCompat.getDrawable(context, R.drawable.kr_shortcut_logo), 200)
            }) ?: Icon.createWithResource(context, R.drawable.kr_shortcut_logo)

            val sender = android.app.Person.Builder()
                .setName(notificationTitle)
                .setIcon(personIcon)
                .build()

            val messagingStyle = Notification.MessagingStyle(sender)

            val rows = synchronized(notificationRows) { notificationRows.toList() }
            if (notificationRowsTrimmed) {
                messagingStyle.addMessage(Notification.MessagingStyle.Message("……", System.currentTimeMillis(), sender))
            }
            rows.forEach { row ->
                messagingStyle.addMessage(Notification.MessagingStyle.Message(row.trim(), System.currentTimeMillis(), sender))
            }

            val notificationBuilder = Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(notificationTitle)
                .setContentText(shortLog)
                .setSmallIcon(R.drawable.kr_run)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
                .setStyle(messagingStyle)
            
            if (notificationProgressTotal != notificationProgressCurrent) {
                notificationBuilder.setProgress(notificationProgressTotal, notificationProgressCurrent, notificationProgressTotal < 0)
            }

            buildContentPendingIntent()?.let { notificationBuilder.setContentIntent(it) }

            if (notificationFinished) {
                dismissPendingIntent?.let {
                    notificationBuilder.addAction(R.drawable.kr_close, context.getString(R.string.btn_confirm), it)
                }
            } else {
                stopPendingIntent?.let {
                    notificationBuilder.addAction(R.drawable.kr_cancel, context.getString(R.string.btn_cancel), it)
                }
            }
            buildCopyLogPendingIntent().let {
                notificationBuilder.addAction(R.drawable.kr_copy, context.getString(R.string.btn_copy_output), it)
            }

            dismissPendingIntent?.let { notificationBuilder.setDeleteIntent(it) }

            if (!notificationChannelCreated) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.kr_slice_script_result),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                channel.enableLights(false)
                channel.enableVibration(false)
                channel.setSound(null, null)
                nm.createNotificationChannel(channel)
                notificationChannelCreated = true
            }
            notificationBuilder.setChannelId(NOTIFICATION_CHANNEL_ID)

            val notification = notificationBuilder.build()
            if (!notificationFinished) {
                notification.flags = Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT
            }
            nm.notify(id, notification)
        }

        private fun updateNotification() {
            if (!notificationMode) return
            if (!pendingNotificationUpdate) {
                pendingNotificationUpdate = true
                notificationHandler.postDelayed(updateNotificationRunnable, 300)
            }
        }

        private fun updateNotificationImmediately() {
            notificationHandler.removeCallbacks(updateNotificationRunnable)
            pendingNotificationUpdate = false
            updateNotificationInternal()
        }

        private fun buildContentPendingIntent(): PendingIntent? {
            // Đi thẳng vào MainActivity kèm ID thông báo thay vì dùng launch intent mặc định
            // của app (mở SplashActivity rồi chuyển tiếp mà không giữ lại extra nào) — nhờ đó
            // MainActivity biết cần mở lại dialog log nào từ HiddenTaskRegistry.
            val resumeIntent = Intent(context, com.tool.tree.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra(DialogLogFragment.EXTRA_RESUME_NOTIFICATION_ID, notificationId)
            }
            return PendingIntent.getActivity(
                context, notificationId, resumeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun finishNotification(success: Boolean, code: Int) {
            notificationFinished = true
            val finishText = if (success) {
                context.getString(R.string.kr_script_task_finished)
            } else {
                "${context.getString(R.string.kr_shell_finish_error)}"
            }
            pushNotificationLog(finishText)
            updateNotificationImmediately()
        }

        companion object {
            private const val NOTIFICATION_CHANNEL_ID = "kr_script_task_notification_hide"
            private var notificationChannelCreated = false
            private var notificationIdCounter = 54050

            @Synchronized
            private fun nextNotificationId(): Int {
                notificationIdCounter += 1
                return notificationIdCounter
            }
        }

        private fun getColor(resId: Int): Int = ContextCompat.getColor(context, resId)

        private fun findScrollViewAncestor(view: View): ScrollView? {
            var parent = view.parent
            while (parent is View) {
                if (parent is ScrollView) return parent
                parent = parent.parent
            }
            return null
        }

        private fun dpToPx(dp: Float): Int {
            return (dp * context.resources.displayMetrics.density).toInt()
        }

        /**
         * Lấy nội dung log hiện tại. Nếu [excludeFinishLine] = true và log đã kết thúc (đã có
         * dòng trạng thái thành công/lỗi tự thêm ở onExit), dòng đó sẽ được loại bỏ khỏi kết quả.
         */
        fun getLogText(excludeFinishLine: Boolean): String {
            synchronized(logBuffer) {
                val full = logBuffer.toString()
                return if (excludeFinishLine && hasFinishLine && full.length >= finishLineLength) {
                    full.substring(0, full.length - finishLineLength)
                } else {
                    full
                }
            }
        }

        fun release() {
            notificationHandler.removeCallbacks(updateNotificationRunnable)
            logViewRef.clear()
            progressRef.clear()
            inputRowRef.clear()
            shellInputRef.clear()
            chooseRowRef.clear()
            chooseOptionsContainerRef.clear()
            unbindStdin()
            actionEventHandler = null
        }

        /**
         * Gắn lại handler đang chạy (được lấy từ HiddenTaskRegistry) vào bộ view của một
         * DialogLogFragment vừa được tạo lại (khi người dùng bấm vào thông báo để mở lại
         * dialog đã ẩn). Toàn bộ log đã ghi nhận trước đó (logBuffer) được phát lại vào
         * logView mới, và thông báo hệ thống tương ứng được hủy vì dialog đã hiển thị trở lại.
         */
        fun reattach(
            logView: TextView?,
            shellProgress: ProgressBar?,
            inputRow: View?,
            shellInput: EditText?,
            chooseRow: View?,
            chooseOptionsContainer: LinearLayout?,
            actionEventHandler: IActionEventHandler?
        ) {
            logViewRef = WeakReference(logView)
            progressRef = WeakReference(shellProgress)
            inputRowRef = WeakReference(inputRow)
            shellInputRef = WeakReference(shellInput)
            chooseRowRef = WeakReference(chooseRow)
            chooseOptionsContainerRef = WeakReference(chooseOptionsContainer)
            this.actionEventHandler = actionEventHandler

            if (logView != null) {
                synchronized(logBuffer) {
                    uiAppliedLength = 0
                    uiInvalidFrom = Int.MAX_VALUE
                }
                // Đảm bảo cờ này không bị "kẹt" ở true (ví dụ nếu có 1 lần flush đang chờ xử lý
                // đúng lúc dialog bị ẩn) — nếu không, các log mới phát sinh sau khi mở lại có
                // thể không bao giờ được đẩy lên UI vì compareAndSet(false, true) luôn thất bại.
                pendingUiUpdate.set(false)
                logView.post {
                    logView.setText("", TextView.BufferType.EDITABLE)
                    flushToUi(logView)
                }
            }

            if (shellProgress != null && (notificationProgressTotal > 0 || notificationProgressCurrent < 0)) {
                shellProgress.post {
                    when {
                        notificationProgressCurrent < 0 -> {
                            shellProgress.visibility = View.VISIBLE
                            shellProgress.isIndeterminate = true
                        }
                        notificationProgressCurrent >= notificationProgressTotal -> shellProgress.visibility = View.GONE
                        else -> {
                            shellProgress.visibility = View.VISIBLE
                            shellProgress.isIndeterminate = false
                            shellProgress.max = notificationProgressTotal
                            shellProgress.progress = notificationProgressCurrent
                        }
                    }
                }
            }

            // Dialog đã hiển thị trở lại, không cần thông báo (và các receiver liên quan) nữa.
            if (notificationMode) {
                notificationManager?.cancel(notificationId)
                cleanupNotificationReceivers()
                notificationManager = null
                notificationMode = false
                stopPendingIntent = null
                dismissPendingIntent = null
                synchronized(notificationRows) {
                    notificationRows.clear()
                    notificationRowsTrimmed = false
                }
            }
        }

        override fun onKillRequest() {
            try {
                context.startService(Intent(context, WakeLockService::class.java).apply {
                    action = WakeLockService.ACTION_END_WAKELOCK
                })
            } catch (ignored: Exception) {
            }

            try {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                activityManager?.appTasks?.forEach { task -> task.finishAndRemoveTask() }
            } catch (ignored: Exception) {
            }
        }

        override fun onInputRequest(prompt: String) {
            val logView = logViewRef.get()
            val row = inputRowRef.get() ?: return
            val input = shellInputRef.get()
            (logView ?: row).post {
                chooseRowRef.get()?.visibility = View.GONE
                row.visibility = View.VISIBLE
                if (input != null) {
                    if (prompt.isNotEmpty()) {
                        input.hint = prompt
                    }
                    input.isFocusable = true
                    input.isFocusableInTouchMode = true
                    input.requestFocus()
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }

        override fun onChooseRequest(options: List<ChoiceOption>) {
            val logView = logViewRef.get()
            val container = chooseOptionsContainerRef.get() ?: return
            val row = chooseRowRef.get() ?: return
            val measureView = logView ?: row

            val gapPx = dpToPx(8f)
            val minComfortableWidthPx = dpToPx(40f)
            val buttonHeightPx = dpToPx(40f)

            val answered = AtomicBoolean(false)
            val choiceSpans = mutableListOf<ChoiceSpan>()

            fun onAnswered(value: String) {
                if (!answered.compareAndSet(false, true)) return
                writeInput(value)
                row.visibility = View.GONE
                container.removeAllViews()

                val editable = logViewRef.get()?.editableText ?: return
                for (span in choiceSpans) {
                    val start = editable.getSpanStart(span)
                    val end = editable.getSpanEnd(span)
                    if (start < 0 || end < 0) continue
                    editable.removeSpan(span)
                    editable.setSpan(
                        ForegroundColorSpan(if (span.value == value) choiceAnsweredColor else choiceDisabledColor),
                        start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }

            fun buildButtons(availableWidth: Int) {
                inputRowRef.get()?.visibility = View.GONE
                container.removeAllViews()

                val count = options.size
                if (count == 0) return

                val perButtonIfFilled = if (availableWidth > 0) {
                    (availableWidth - gapPx * (count - 1)) / count
                } else 0

                val useFillMode = availableWidth > 0 && perButtonIfFilled >= minComfortableWidthPx

                container.layoutParams = FrameLayout.LayoutParams(
                    if (useFillMode) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )

                for ((index, option) in options.withIndex()) {
                    val button = Button(row.context, null, 0, R.style.dialogChoiceBtn).apply {
                        text = option.label
                        layoutParams = if (useFillMode) {
                            LinearLayout.LayoutParams(0, buttonHeightPx, 1f)
                        } else {
                            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, buttonHeightPx)
                        }.also {
                            if (index < count - 1) it.marginEnd = gapPx
                        }
                        setOnClickListener { onAnswered(option.value) }
                    }
                    container.addView(button)
                }
                row.visibility = View.VISIBLE
            }

            if (measureView.width > 0) {
                buildButtons(measureView.width)
            } else {
                measureView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        if (measureView.width > 0) {
                            measureView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                            buildButtons(measureView.width)
                        }
                    }
                })
                measureView.requestLayout()
            }

            val builder = SpannableStringBuilder().apply { append('\n') }
            for (option in options) {
                val start = builder.length
                builder.append(option.label)
                val end = builder.length
                val span = ChoiceSpan(option.value) { value -> onAnswered(value) }
                choiceSpans.add(span)
                builder.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(ForegroundColorSpan(choiceLinkColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.append('\n')
            }
            dispatchLogUpdate(builder)
        }

        override fun onPickRequest(options: List<ChoiceOption>, vertical: Boolean) {
            val answered = AtomicBoolean(false)
            val choiceSpans = mutableListOf<ChoiceSpan>()

            fun onAnswered(value: String) {
                if (!answered.compareAndSet(false, true)) return
                writeInput(value)

                val editable = logViewRef.get()?.editableText ?: return
                for (span in choiceSpans) {
                    val start = editable.getSpanStart(span)
                    val end = editable.getSpanEnd(span)
                    if (start < 0 || end < 0) continue
                    editable.removeSpan(span)
                    editable.setSpan(
                        ForegroundColorSpan(if (span.value == value) choiceAnsweredColor else choiceDisabledColor),
                        start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }

            fun appendChoice(builder: SpannableStringBuilder, text: String, value: String) {
                val start = builder.length
                builder.append(text)
                val end = builder.length
                val span = ChoiceSpan(value) { v -> onAnswered(v) }
                choiceSpans.add(span)
                builder.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(ForegroundColorSpan(choiceLinkColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            val builder = SpannableStringBuilder().apply { append('\n') }
            if (vertical) {
                options.forEach { option ->
                    appendChoice(builder, option.label, option.value)
                    builder.append("\n\n")
                }
            } else {
                options.forEachIndexed { index, option ->
                    appendChoice(builder, option.label, option.value)
                    if (index != options.lastIndex) builder.append("  ")
                }
                builder.append('\n')
            }
            dispatchLogUpdate(builder)
        }

        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
        }

        override fun onReader(msg: Any?) {
            updateLogWithColor(msg.toString(), basicColor)
        }

        override fun onWrite(msg: Any?) {
            updateLogWithColor(msg.toString(), scriptColor)
        }

        override fun onError(msg: Any?) {
            hasError = true
            updateLogWithColor(msg.toString(), errorColor)
        }

        private fun resetLogState() {
            AnsiColorParser.reset()
            synchronized(logBuffer) {
                lineCount = 0
                lineStart = 0
                pendingOverwrite = false
                uiAppliedLength = 0
                uiInvalidFrom = Int.MAX_VALUE
                logBuffer.clear()
                hasFinishLine = false
                finishLineLength = 0
            }
            logViewRef.get()?.post {
                val tv = logViewRef.get() ?: return@post
                val editable = tv.editableText
                if (editable != null) {
                    editable.clear()
                } else {
                    tv.setText("", TextView.BufferType.EDITABLE)
                }
            }
        }

        override fun onStart(forceStop: Runnable?) {
            resetLogState()
            forceStopRunnable = forceStop
            actionEventHandler?.onStart(forceStop)
        }

        override fun onProgress(current: Int, total: Int) {
            val shellProgress = progressRef.get() ?: return
            shellProgress.post {
                when {
                    current < 0 -> shellProgress.apply {
                        visibility = View.VISIBLE
                        isIndeterminate = true
                        (layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                            params.height = dpToPx(7f)
                            params.topMargin = dpToPx(13.2f)
                            layoutParams = params
                        }
                    }
                    current >= total -> shellProgress.visibility = View.GONE
                    else -> shellProgress.apply {
                        visibility = View.VISIBLE
                        isIndeterminate = false
                        max = total
                        progress = current
                        (layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                            params.height = dpToPx(1.6f)
                            params.topMargin = dpToPx(15.8f)
                            layoutParams = params
                        }
                    }
                }
            }

            notificationProgressCurrent = current
            notificationProgressTotal = total
            if (notificationMode) updateNotification()
        }

        override fun onStart(msg: Any?) {
            resetLogState()
        }

        override fun onExit(msg: Any?) {
            hasExited = true
            val code = (msg as? Int) ?: -1
            val success = !hasError && code == 0
            val finishText = if (success) {
                context.getString(R.string.kr_shell_completed)
            } else {
                context.getString(R.string.kr_shell_finish_error)
            }
            
            val finishColor = if (success) endColor else errorColor
            val finishLine = "\n" + finishText
            updateLogWithColor(finishLine, finishColor, pushToNotification = false)
            hasFinishLine = true
            finishLineLength = finishLine.length

            if (notificationMode) {
                finishNotification(success, code)
            }

            if (success) {
                actionEventHandler?.onSuccess()
            }
            actionEventHandler?.onCompleted()
        }

        override fun updateLog(msg: SpannableString) {
            dispatchLogUpdate(msg)
        }

        private fun updateLogWithColor(text: String, forcedColor: Int?, pushToNotification: Boolean = true) {
            var parsedLog: CharSequence = AnsiColorParser.parse(text)

            if (forcedColor != null && !text.contains("\u001B[")) {
                parsedLog = SpannableStringBuilder(parsedLog).apply {
                    setSpan(
                        ForegroundColorSpan(forcedColor),
                        0,
                        length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }

            dispatchLogUpdate(parsedLog)

            if (notificationMode && pushToNotification) {
                pushNotificationLog(text)
            }
        }

        private fun pushNotificationLog(text: String) {
            val plainText = AnsiColorParser.stripToPlainText(text)
            val lines = plainText.replace("\r", "\n").split("\n").filter { it.isNotEmpty() }
            if (lines.isEmpty()) return
            synchronized(notificationRows) {
                lines.forEach { notificationRows.add("$it\n") }
                trimNotificationRows()
            }
            updateNotification()
        }

        private fun markInvalidFrom(from: Int) {
            if (from < uiInvalidFrom) uiInvalidFrom = from
        }

        private fun dispatchLogUpdate(formattedText: CharSequence) {
            // QUAN TRỌNG: không được return sớm khi chưa có logView (ví dụ lúc dialog đang ẩn,
            // hiển thị dưới dạng thông báo) — logBuffer vẫn phải được ghi nhận đầy đủ để khi
            // mở lại dialog (reattach) có thể phát lại toàn bộ log, kể cả những dòng phát sinh
            // trong lúc ẩn. Trước đây return sớm ở đây khiến log mới trong lúc ẩn bị mất hẳn,
            // dẫn đến khi mở lại chỉ thấy log cũ (tính đến thời điểm bấm "Ẩn").
            synchronized(logBuffer) {
                var i = 0
                val len = formattedText.length

                while (i < len) {
                    var j = i
                    while (j < len && formattedText[j] != '\r' && formattedText[j] != '\n') j++

                    if (j > i) {
                        val segment = formattedText.subSequence(i, j)
                        if (pendingOverwrite) {
                            if (lineStart in 0..logBuffer.length) {
                                logBuffer.delete(lineStart, logBuffer.length)
                                markInvalidFrom(lineStart)
                            }
                            pendingOverwrite = false
                        }
                        logBuffer.append(segment)
                    }

                    if (j < len) {
                        when (formattedText[j]) {
                            '\r' -> {
                                val isCRLF = j + 1 < len && formattedText[j + 1] == '\n'
                                if (!isCRLF) {
                                    pendingOverwrite = true
                                }
                            }
                            '\n' -> {
                                if (pendingOverwrite && lineStart in 0..logBuffer.length) {
                                    logBuffer.delete(lineStart, logBuffer.length)
                                    markInvalidFrom(lineStart)
                                    pendingOverwrite = false
                                }
                                logBuffer.append('\n')
                                lineCount++
                                lineStart = logBuffer.length
                            }
                        }
                        j++
                    }
                    i = j
                }

                if (lineCount > 5000) {
                    var deleteEndIndex = 0
                    var linesToTrim = lineCount - 5000
                    val currentCharSequence = logBuffer.toString()

                    for (k in currentCharSequence.indices) {
                        if (currentCharSequence[k] == '\n') {
                            linesToTrim--
                            if (linesToTrim <= 0) {
                                deleteEndIndex = k + 1
                                break
                            }
                        }
                    }
                    if (deleteEndIndex > 0) {
                        logBuffer.delete(0, deleteEndIndex)
                        lineCount = 5000
                        lineStart = (lineStart - deleteEndIndex).coerceAtLeast(0)
                        uiAppliedLength = (uiAppliedLength - deleteEndIndex).coerceAtLeast(0)
                        uiInvalidFrom = 0
                    }
                }
            }

            // Chỉ cần cập nhật UI nếu đang có view gắn vào (dialog đang hiển thị). Khi ẩn,
            // logBuffer vẫn được cập nhật ở trên để dùng cho việc phát lại log lúc mở lại sau.
            val logView = logViewRef.get() ?: return
            if (pendingUiUpdate.compareAndSet(false, true)) {
                logView.post {
                    pendingUiUpdate.set(false)
                    flushToUi(logView)
                }
            }
        }

        private fun flushToUi(logView: TextView) {
            var replaceFrom: Int
            val oldUiLength: Int
            val tail: CharSequence?

            synchronized(logBuffer) {
                oldUiLength = uiAppliedLength
                replaceFrom = if (uiInvalidFrom == Int.MAX_VALUE) oldUiLength else uiInvalidFrom
                if (replaceFrom > oldUiLength) replaceFrom = oldUiLength
                if (replaceFrom < 0) replaceFrom = 0

                tail = if (replaceFrom < logBuffer.length || replaceFrom < oldUiLength) {
                    SpannableStringBuilder(logBuffer.subSequence(replaceFrom, logBuffer.length))
                } else null

                uiAppliedLength = logBuffer.length
                uiInvalidFrom = Int.MAX_VALUE
            }

            if (tail == null) return

            var editable = logView.editableText
            if (editable == null) {
                logView.setText("", TextView.BufferType.EDITABLE)
                editable = logView.editableText ?: return
            }

            val safeOldEnd = oldUiLength.coerceIn(0, editable.length)
            val safeFrom = replaceFrom.coerceIn(0, safeOldEnd)
            editable.replace(safeFrom, safeOldEnd, tail)

            findScrollViewAncestor(logView)?.let { scrollView ->
                if (!scrollView.isAttachedToWindow) return@let
                scrollView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        scrollView.viewTreeObserver.removeOnPreDrawListener(this)
                        scrollView.fullScroll(ScrollView.FOCUS_DOWN)

                        val inputRow = inputRowRef.get()
                        val input = shellInputRef.get()
                        if (inputRow != null && inputRow.visibility == View.VISIBLE && input != null) {
                            if (!input.isFocused) input.requestFocus()
                        }
                        return true
                    }
                })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Dialog này dùng theme windowIsFloating=false (full-screen) nên tự tách thành 1 lớp
        // cửa sổ riêng, nằm TRÊN cửa sổ gốc của Activity. Banner (BannerNotificationManager)
        // vốn chỉ biết gắn vào token cửa sổ Activity (CurrentActivityHolder) nên bị dialog
        // full-screen này che mất. Đăng ký window hiện tại vào TopWindowHolder để banner biết
        // cần gắn vào token nào mới thực sự nổi lên trên cùng.
        dialog?.window?.let { window ->
            attachedWindow = window
            com.omarea.common.ui.TopWindowHolder.push(window)
        }
    }

    override fun onResume() {
        super.onResume()
        dialog?.setOnKeyListener { _, keyCode, event ->
            if (!uiVisible || !running) return@setOnKeyListener false
            event.action == KeyEvent.ACTION_DOWN && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        }
    }

    private fun offScreen() {
        dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun closeView() {
        runCatching { dismissAllowingStateLoss() }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissRunnable?.run()
    }

    override fun onDestroyView() {
        attachedWindow?.let { com.omarea.common.ui.TopWindowHolder.pop(it) }
        attachedWindow = null
        currentHandler?.release()
        currentHandler = null
        AnsiColorParser.reset()
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        offScreen()
        super.onDestroy()
    }

    companion object {
        private const val WRAP_STATE_RELATIVE_PATH = "home/usr/log/scroll_ngang"
        private val IO_EXECUTOR = Executors.newSingleThreadExecutor()

        // Extra key gửi kèm PendingIntent của thông báo tiến trình, dùng để MainActivity biết
        // cần mở lại dialog log nào (tra trong HiddenTaskRegistry) khi người dùng bấm vào.
        const val EXTRA_RESUME_NOTIFICATION_ID = "kr_script_resume_notification_id"

        /**
         * Được gọi từ MainActivity khi Activity nhận intent mở lại dialog log đã ẩn (từ
         * thông báo). Trả về null nếu tác vụ tương ứng không còn tồn tại (ví dụ tiến trình
         * app đã bị hệ thống hủy hoàn toàn, hoặc thông báo đã được mở/hủy trước đó).
         */
        fun resume(notificationId: Int): DialogLogFragment? {
            val entry = HiddenTaskRegistry.take(notificationId) ?: return null
            return DialogLogFragment().apply {
                this.nodeInfo = entry.nodeInfo
                this.onExit = entry.onExit
                this.onDismissRunnable = entry.onDismiss
                this.themeResId = if (entry.darkMode) R.style.kr_full_screen_dialog_dark else R.style.kr_full_screen_dialog_light
                this.resumeHandler = entry.handler
                this.isCancelable = false
            }
        }

        fun create(
            nodeInfo: RunnableNode,
            onExit: Runnable,
            onDismiss: Runnable,
            script: String,
            params: HashMap<String, String>?,
            darkMode: Boolean = false
        ): DialogLogFragment {
            return DialogLogFragment().apply {
                this.nodeInfo = nodeInfo
                this.onExit = onExit
                this.script = script
                this.params = params
                this.themeResId = if (darkMode) R.style.kr_full_screen_dialog_dark else R.style.kr_full_screen_dialog_light
                this.onDismissRunnable = onDismiss
            }
        }
    }
}

/**
 * Thông tin cần thiết để mở lại dialog log của một tác vụ đang chạy ẩn (đã bấm "Ẩn" và
 * đang hiển thị dưới dạng thông báo).
 */
data class HiddenTaskEntry(
    val nodeInfo: RunnableNode,
    val handler: DialogLogFragment.MyShellHandler,
    val onExit: Runnable,
    val onDismiss: Runnable,
    val darkMode: Boolean
)

/**
 * Registry (còn sống trong suốt vòng đời tiến trình app) lưu các tác vụ đang chạy ẩn, được
 * đánh index theo ID thông báo. Khi người dùng bấm vào thông báo, MainActivity tra registry
 * này để lấy lại handler đang chạy và mở lại dialog log, thay vì chạy lại script từ đầu.
 */
object HiddenTaskRegistry {
    private val tasks = java.util.concurrent.ConcurrentHashMap<Int, HiddenTaskEntry>()

    fun register(notificationId: Int, entry: HiddenTaskEntry) {
        tasks[notificationId] = entry
    }

    fun unregister(notificationId: Int) {
        tasks.remove(notificationId)
    }

    /** Lấy ra và xóa khỏi registry (dialog sẽ hiển thị lại nên không còn "ẩn" nữa). */
    fun take(notificationId: Int): HiddenTaskEntry? = tasks.remove(notificationId)
}