package com.tool.tree

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.omarea.common.shared.FileWrite
import com.omarea.common.shell.KeepShellPublic
import com.omarea.common.ui.DialogHelper
import com.omarea.krscript.config.PathAnalysis
import com.omarea.krscript.model.ActionNode
import com.omarea.krscript.model.RunnableNode
import com.omarea.krscript.ui.DialogLogFragment
import com.tool.tree.databinding.ActivityTextEditorBinding
import com.tool.tree.ui.OverflowMenuPopup
import com.tool.tree.ui.PopupMenuRow
import com.tool.tree.ui.PopupRowTypeIcon
import com.tool.tree.ui.SyntaxHighlighter
import com.tool.tree.ui.SyntaxHighlighterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class TextEditorActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_FILE = "file"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_DESC = "desc"
        private const val EXTRA_WRAP = "wrap"
        private const val EXTRA_DIR = "dir"
        private const val EXTRA_PLACEHOLDER = "placeholder"
        private const val EXTRA_READONLY = "readonly"
        private const val EXTRA_NEED_INPUT = "need_input"
        private const val EXTRA_VALUE = "value"
        private const val EXTRA_VALUE_SH = "value_sh"

        // Giới hạn tối đa 10 lượt Undo/Redo, vượt quá sẽ tự động xoá lượt cũ nhất
        private const val UNDO_HISTORY_LIMIT = 20
        private const val REDO_HISTORY_LIMIT = 20
        private const val UNDO_DEBOUNCE_MS = 600L
        private const val UNDO_CACHE_DEBOUNCE_MS = 1000L
        private const val UNDO_CACHE_DIR = "cache"

        private val RUNNABLE_EXTENSIONS = mapOf(
            "sh" to "sh",
            "bash" to "bash",
            "py" to "python"
        )

        private val FIRST_LINE_LANG_PATTERN = Regex(
            """^#!?\s*(?:/usr/bin/env\s+)?(?:/data/(?:data|user/\d+)/com\.tool\.tree/\S*/)?(python3?|py|bash|sh|shell)\b""",
            RegexOption.IGNORE_CASE
        )

        private fun normalizeLangToken(token: String): String? = when (token.lowercase()) {
            "python", "python3", "py" -> "python"
            "bash" -> "bash"
            "sh", "shell" -> "sh"
            else -> null
        }

        fun start(
            context: Context,
            file: String,
            title: String? = null,
            desc: String? = null,
            wrap: Boolean = true,
            dir: String? = null,
            placeholder: String? = null,
            readonly: Boolean = false,
            needInput: Boolean = false,
            value: String? = null,
            valueSh: String? = null
        ) {
            val intent = Intent(context, TextEditorActivity::class.java).apply {
                putExtra(EXTRA_FILE, file)
                putExtra(EXTRA_TITLE, title ?: "")
                putExtra(EXTRA_DESC, desc ?: "")
                putExtra(EXTRA_WRAP, wrap)
                putExtra(EXTRA_DIR, dir ?: "")
                putExtra(EXTRA_PLACEHOLDER, placeholder ?: "")
                putExtra(EXTRA_READONLY, readonly)
                putExtra(EXTRA_NEED_INPUT, needInput)
                putExtra(EXTRA_VALUE, value ?: "")
                putExtra(EXTRA_VALUE_SH, valueSh ?: "")
                if (context !is AppCompatActivity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private lateinit var binding: ActivityTextEditorBinding
    private lateinit var filePath: String
    private lateinit var absoluteFilePath: String
    private var configDir: String = ""
    private var wrapEnabled = true
    private var monospaceEnabled = true
    private var savedContent: String = ""
    private var isSaving = false
    private var noWrapContainer: HorizontalScrollView? = null
    private var mainListTextBaseBottomMargin = 0
    private var syntaxHighlighter: SyntaxHighlighter? = null
    private var placeholderText: String = ""
    private var titleText: String = ""
    private var readonlyMode: Boolean = false
    private var needInput: Boolean = false
    private var initialValue: String = ""
    private var initialValueSh: String = ""
    private var lastLanguageOverride: String? = null
    private var lastLineCount = -1

    private data class EditorSnapshot(val text: String, val cursor: Int)

    private val undoStack = ArrayDeque<EditorSnapshot>()
    private val redoStack = ArrayDeque<EditorSnapshot>()
    private var pendingUndoSnapshot: EditorSnapshot? = null
    private var isApplyingHistory = false

    // Quản lý trạng thái các MenuItem trên Toolbar
    private var undoMenuItem: MenuItem? = null
    private var redoMenuItem: MenuItem? = null
    private var saveMenuItem: MenuItem? = null

    private val editorHandler = Handler(Looper.getMainLooper())
    private val commitPendingUndoRunnable = Runnable { commitPendingUndoSnapshot() }
    private val persistUndoCacheRunnable = Runnable { persistUndoCacheToDisk() }
    private val updateLineNumbersRunnable = Runnable { updateLineNumbersInternal() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeModeState.switchTheme(this)
        binding = ActivityTextEditorBinding.inflate(layoutInflater).also { setContentView(it.root) }

        binding.editorRoot.setDrawStrokeEnabled(false)

        setupKeyboardInsets()

        val toolbar = findViewById<Toolbar>(R.id.toolbar) ?: binding.root.findViewById(R.id.toolbar)
        toolbar.let {
            setSupportActionBar(it)
            supportActionBar?.apply {
                setHomeButtonEnabled(true)
                setDisplayHomeAsUpEnabled(true)
                setHomeAsUpIndicator(R.drawable.ic_arrow_back)
            }
            it.setNavigationOnClickListener { attemptClose() }
        }

        onBackPressedDispatcher.addCallback(this) { attemptClose() }

        filePath = intent.getStringExtra(EXTRA_FILE) ?: ""
        if (filePath.isEmpty()) {
            Toast.makeText(this, R.string.editor_file_missing, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        configDir = intent.getStringExtra(EXTRA_DIR).orEmpty()
        absoluteFilePath = resolveAbsolutePath(configDir, filePath)
        wrapEnabled = intent.getBooleanExtra(EXTRA_WRAP, true)
        placeholderText = intent.getStringExtra(EXTRA_PLACEHOLDER).orEmpty()
        titleText = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        readonlyMode = intent.getBooleanExtra(EXTRA_READONLY, false)
        needInput = intent.getBooleanExtra(EXTRA_NEED_INPUT, false)
        initialValue = intent.getStringExtra(EXTRA_VALUE).orEmpty()
        initialValueSh = intent.getStringExtra(EXTRA_VALUE_SH).orEmpty()

        applyWrapState()
        applyMonospaceState()
        setupUnifiedTextWatcher()
        setupSpecialCharsBar()
        setupEditorTouchAndFocus()
        applyReadonlyState()
        loadFileContent()
    }

    override fun onPause() {
        commitPendingUndoSnapshot()
        persistUndoCacheToDisk()
        super.onPause()
    }

    override fun onDestroy() {
        syntaxHighlighter?.detach()
        editorHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun undoCacheFile(): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(absoluteFilePath.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val dir = File(cacheDir, UNDO_CACHE_DIR).apply { mkdirs() }
        return File(dir, "$digest.json")
    }

    private fun snapshotToJson(snapshot: EditorSnapshot) = JSONObject().apply {
        put("text", snapshot.text)
        put("cursor", snapshot.cursor)
    }

    private fun jsonToSnapshot(json: JSONObject) =
        EditorSnapshot(json.optString("text", ""), json.optInt("cursor", 0))

    private fun scheduleUndoCachePersist() {
        editorHandler.removeCallbacks(persistUndoCacheRunnable)
        editorHandler.postDelayed(persistUndoCacheRunnable, UNDO_CACHE_DEBOUNCE_MS)
    }

    private fun persistUndoCacheToDisk() {
        editorHandler.removeCallbacks(persistUndoCacheRunnable)
        if (!::binding.isInitialized) return
        val baseContent = savedContent
        val undoSnapshot = undoStack.toList()
        val redoSnapshot = redoStack.toList()
        val pending = pendingUndoSnapshot

        lifecycleScope.launch(Dispatchers.IO + NonCancellable) {
            try {
                if (undoSnapshot.isEmpty() && redoSnapshot.isEmpty() && pending == null) {
                    undoCacheFile().delete()
                    return@launch
                }
                val root = JSONObject().apply {
                    put("baseContent", baseContent)
                    put("undo", JSONArray().apply { undoSnapshot.forEach { put(snapshotToJson(it)) } })
                    put("redo", JSONArray().apply { redoSnapshot.forEach { put(snapshotToJson(it)) } })
                    pending?.let { put("pending", snapshotToJson(it)) }
                }
                undoCacheFile().writeText(root.toString())
            } catch (_: Exception) {
            }
        }
    }

    private fun restoreUndoCacheFromDisk() {
        try {
            val file = undoCacheFile()
            if (!file.exists()) return
            val root = JSONObject(file.readText())
            val baseContent = if (root.has("baseContent")) root.getString("baseContent") else null
            if (baseContent == null || baseContent != savedContent) {
                file.delete()
                return
            }

            undoStack.clear()
            redoStack.clear()
            root.optJSONArray("undo")?.let { arr ->
                for (i in 0 until arr.length()) undoStack.addLast(jsonToSnapshot(arr.getJSONObject(i)))
            }
            root.optJSONArray("redo")?.let { arr ->
                for (i in 0 until arr.length()) redoStack.addLast(jsonToSnapshot(arr.getJSONObject(i)))
            }
            pendingUndoSnapshot = root.optJSONObject("pending")?.let { jsonToSnapshot(it) }
            refreshToolbarButtons()
        } catch (_: Exception) {
        }
    }

    private fun clearUndoCache() {
        editorHandler.removeCallbacks(persistUndoCacheRunnable)
        try {
            undoCacheFile().delete()
        } catch (_: Exception) {
        }
    }

    private fun setupKeyboardInsets() {
        mainListTextBaseBottomMargin = (binding.mainListText.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
        val specialCharsBaseBottomMargin =
            (binding.editorSpecialCharsScroll.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val imeInset = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val sysInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val targetBottomInset = if (imeInset > 0) imeInset else sysInset

            val charsLp = binding.editorSpecialCharsScroll.layoutParams as ViewGroup.MarginLayoutParams
            charsLp.bottomMargin = specialCharsBaseBottomMargin + targetBottomInset
            binding.editorSpecialCharsScroll.layoutParams = charsLp

            val mlp = binding.mainListText.layoutParams as ViewGroup.MarginLayoutParams
            mlp.bottomMargin = mainListTextBaseBottomMargin
            binding.mainListText.layoutParams = mlp

            if (imeInset > 0) {
                binding.mainListText.post { scrollToCursor() }
            }
            insets
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupEditorTouchAndFocus() {
        val focusAndShowKeyboard = {
            binding.editorContent.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(binding.editorContent, InputMethodManager.SHOW_IMPLICIT)
        }

        binding.mainListText.setOnClickListener {
            focusAndShowKeyboard()
            if (binding.editorContent.selectionStart < 0) {
                binding.editorContent.setSelection(binding.editorContent.text?.length ?: 0)
            }
        }

        binding.editorContentContainer.setOnClickListener {
            focusAndShowKeyboard()
            if (binding.editorContent.selectionStart < 0) {
                binding.editorContent.setSelection(binding.editorContent.text?.length ?: 0)
            }
        }

        // Xử lý khi nhấn vào khu vực số dòng -> đặt con trỏ về đầu dòng tương ứng
        binding.editorLineNumbers.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                v.performClick()
                focusAndShowKeyboard()

                val editText = binding.editorContent
                val layout = editText.layout
                val text = editText.text

                if (layout != null && !text.isNullOrEmpty()) {
                    // Quy đổi tọa độ Y tương ứng với EditText
                    val yInEditText = event.y + binding.editorLineNumbers.top - editText.top
                    val yInLayout = yInEditText - editText.paddingTop
                    val clampedY = yInLayout.coerceIn(0f, (layout.height - 1).toFloat())

                    // Xác định dòng được nhấn
                    val line = layout.getLineForVertical(clampedY.toInt())

                    // Tìm vị trí ký tự đầu dòng logic (hoạt động tốt cả khi bật Word Wrap)
                    var lineStart = layout.getLineStart(line)
                    while (lineStart > 0 && text[lineStart - 1] != '\n') {
                        lineStart--
                    }

                    // Đặt con trỏ về đầu dòng
                    editText.setSelection(lineStart.coerceIn(0, text.length))
                }
            }
            true
        }
    }

    private fun setupUnifiedTextWatcher() {
        binding.editorContent.addTextChangedListener(object : TextWatcher {
            private var startChangeIndex = 0

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (isApplyingHistory) return
                startChangeIndex = start
                if (pendingUndoSnapshot == null) {
                    val cursor = binding.editorContent.selectionStart.coerceAtLeast(0)
                    pendingUndoSnapshot = EditorSnapshot(s?.toString().orEmpty(), cursor)
                }
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                if (isApplyingHistory) return

                // 1. Quản lý Undo/Redo & Save Debounce
                redoStack.clear()
                editorHandler.removeCallbacks(commitPendingUndoRunnable)
                editorHandler.postDelayed(commitPendingUndoRunnable, UNDO_DEBOUNCE_MS)

                refreshToolbarButtons()
                scheduleUndoCachePersist()

                // 2. Chỉ kiểm tra đổi Ngôn ngữ khi người dùng chỉnh sửa dòng đầu tiên (dòng chứa Shebang)
                if (startChangeIndex < 100) {
                    refreshLanguageOverrideIfChanged()
                }

                // 3. Debounce việc tính toán Số dòng
                editorHandler.removeCallbacks(updateLineNumbersRunnable)
                editorHandler.postDelayed(updateLineNumbersRunnable, 80L)

                // 4. Auto scroll theo con trỏ
                binding.editorContent.post { scrollToCursor() }
            }
        })
    }

    private fun scrollToCursor() {
        val editText = binding.editorContent
        val layout = editText.layout ?: return
        val scrollView = binding.mainListText
        val selection = editText.selectionEnd.coerceIn(0, editText.text?.length ?: 0)
        val line = layout.getLineForOffset(selection)

        val lineTop = layout.getLineTop(line) + editText.top + editText.paddingTop
        val lineBottom = layout.getLineBottom(line) + editText.top + editText.paddingTop

        val margin = (24 * resources.displayMetrics.density).toInt()

        val visibleTop = scrollView.scrollY
        val visibleBottom = visibleTop + scrollView.height - scrollView.paddingBottom

        if (lineBottom + margin > visibleBottom) {
            val targetScrollY = (lineBottom + margin) - scrollView.height + scrollView.paddingBottom
            scrollView.smoothScrollTo(0, targetScrollY)
        } else if (lineTop - margin < visibleTop) {
            val targetScrollY = (lineTop - margin).coerceAtLeast(0)
            scrollView.smoothScrollTo(0, targetScrollY)
        }
    }

    private fun updateLineNumbersInternal() {
        val editText = binding.editorContent
        val editable = editText.text ?: return

        if (!wrapEnabled) {
            var lines = 1
            for (i in 0 until editable.length) {
                if (editable[i] == '\n') lines++
            }
            if (lines != lastLineCount) {
                lastLineCount = lines
                val sb = StringBuilder(lines * 4)
                for (i in 1..lines) {
                    sb.append(i).append('\n')
                }
                if (sb.isNotEmpty()) sb.setLength(sb.length - 1)
                binding.editorLineNumbers.text = sb.toString()
            }
            return
        }

        val layout = editText.layout ?: return
        val totalVisualLines = layout.lineCount
        val sb = StringBuilder(totalVisualLines * 4)
        var logicalLine = 1

        for (i in 0 until totalVisualLines) {
            val startOffset = layout.getLineStart(i)
            if (i == 0 || (startOffset > 0 && editable[startOffset - 1] == '\n')) {
                sb.append(logicalLine)
                logicalLine++
            }
            if (i < totalVisualLines - 1) {
                sb.append('\n')
            }
        }

        val result = sb.toString()
        if (binding.editorLineNumbers.text.toString() != result) {
            binding.editorLineNumbers.text = result
        }
    }

    private fun setupSpecialCharsBar() {
        val chars = listOf(
            "Tab" to "\t", "/" to "/", "$" to "$", "#" to "#",
            "\"" to "\"", "'" to "'", "=" to "=", "`" to "`",
            "\\" to "\\", "|" to "|", "&" to "&", "{" to "{",
            "}" to "}", "(" to "(", ")" to ")", "[" to "[",
            "]" to "]", ";" to ";", ":" to ":", "<" to "<",
            ">" to ">", "@" to "@", "!" to "!", "+" to "+",
            "-" to "-", "*" to "*", "~" to "~", "_" to "_", "%" to "%"
        )

        val container = binding.editorSpecialCharsContainer
        container.removeAllViews()

        val density = resources.displayMetrics.density
        val buttonPadding = (10 * density).toInt()
        val buttonMargin = (2 * density).toInt()
        val minWidthPx = (36 * density).toInt()

        val outValue = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)

        for ((label, insertText) in chars) {
            val button = TextView(this).apply {
                text = label
                typeface = Typeface.MONOSPACE
                textSize = 16f
                gravity = Gravity.CENTER
                minimumWidth = minWidthPx
                setPadding(buttonPadding, buttonPadding, buttonPadding, buttonPadding)
                setTextColor(binding.editorContent.currentTextColor)
                setBackgroundResource(outValue.resourceId)
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = buttonMargin
                    marginEnd = buttonMargin
                }
                setOnClickListener { insertAtCursor(insertText) }
            }
            container.addView(button)
        }
    }

    private fun insertAtCursor(text: String) {
        val editText = binding.editorContent
        val editable = editText.text ?: return
        val start = editText.selectionStart.coerceIn(0, editable.length)
        val end = editText.selectionEnd.coerceIn(0, editable.length)
        val from = minOf(start, end)
        val to = maxOf(start, end)
        editable.replace(from, to, text)
        editText.setSelection(from + text.length)
    }

    private fun resolveAbsolutePath(dir: String, file: String): String {
        if (file.startsWith("/")) return file
        val base = dir.ifBlank { FileWrite.getPrivateFileDir(applicationContext).orEmpty() }
        if (base.isBlank()) return file
        return File(base, file).absolutePath
    }

    private fun fileExtension() = File(absoluteFilePath).name.substringAfterLast('.', "").lowercase()

    private fun detectFirstLineLanguageOverride(): String? {
        val editable = binding.editorContent.text ?: return null
        if (editable.isEmpty()) return null

        val lineEnd = editable.indexOf('\n').let { if (it == -1) editable.length else it }
        val firstLine = editable.subSequence(0, lineEnd).toString().trim()
        if (firstLine.isEmpty()) return null

        val token = FIRST_LINE_LANG_PATTERN.find(firstLine)?.groupValues?.get(1) ?: return null
        return normalizeLangToken(token)
    }

    private fun runnableInterpreter(): String? =
        detectFirstLineLanguageOverride() ?: RUNNABLE_EXTENSIONS[fileExtension()]

    private fun setupSyntaxHighlighting() {
        syntaxHighlighter?.detach()
        val override = detectFirstLineLanguageOverride()
        lastLanguageOverride = override
        val highlightPath = when (override) {
            "python" -> "$absoluteFilePath.__lang_override.py"
            "bash" -> "$absoluteFilePath.__lang_override.bash"
            "sh" -> "$absoluteFilePath.__lang_override.sh"
            else -> absoluteFilePath
        }
        syntaxHighlighter = SyntaxHighlighterFactory.createForPath(
            highlightPath,
            binding.editorContent
        )?.apply { attach() }
    }

    private fun refreshLanguageOverrideIfChanged() {
        val current = detectFirstLineLanguageOverride()
        if (current != lastLanguageOverride) {
            setupSyntaxHighlighting()
            invalidateOptionsMenu()
        }
    }

    private fun commitPendingUndoSnapshot() {
        val snapshot = pendingUndoSnapshot ?: return
        pendingUndoSnapshot = null
        val current = binding.editorContent.text?.toString().orEmpty()
        if (snapshot.text == current) return

        if (undoStack.size >= UNDO_HISTORY_LIMIT) undoStack.removeFirst()
        undoStack.addLast(snapshot)
        refreshToolbarButtons()
        scheduleUndoCachePersist()
    }

    private inline fun withHighlightSuspended(block: () -> Unit) {
        val highlighter = syntaxHighlighter
        highlighter?.suspendHighlighting()
        try {
            block()
        } finally {
            highlighter?.resumeHighlighting()
        }
    }

    private fun performUndo() {
        editorHandler.removeCallbacks(commitPendingUndoRunnable)

        val currentText = binding.editorContent.text?.toString().orEmpty()
        val currentCursor = binding.editorContent.selectionStart.coerceAtLeast(0)
        val target = pendingUndoSnapshot?.also { pendingUndoSnapshot = null }
            ?: if (undoStack.isEmpty()) return else undoStack.removeLast()

        if (redoStack.size >= REDO_HISTORY_LIMIT) redoStack.removeFirst()
        redoStack.addLast(EditorSnapshot(currentText, currentCursor))
        applyHistorySnapshot(target)
        refreshToolbarButtons()
        persistUndoCacheToDisk()
    }

    private fun performRedo() {
        if (redoStack.isEmpty()) return
        editorHandler.removeCallbacks(commitPendingUndoRunnable)
        pendingUndoSnapshot = null

        val currentText = binding.editorContent.text?.toString().orEmpty()
        val currentCursor = binding.editorContent.selectionStart.coerceAtLeast(0)
        val target = redoStack.removeLast()

        if (undoStack.size >= UNDO_HISTORY_LIMIT) undoStack.removeFirst()
        undoStack.addLast(EditorSnapshot(currentText, currentCursor))
        applyHistorySnapshot(target)
        refreshToolbarButtons()
        persistUndoCacheToDisk()
    }

    private fun applyHistorySnapshot(snapshot: EditorSnapshot) {
        isApplyingHistory = true
        withHighlightSuspended {
            binding.editorContent.apply {
                val editable = text
                if (editable != null) {
                    editable.replace(0, editable.length, snapshot.text)
                } else {
                    setText(snapshot.text)
                }
                setSelection(snapshot.cursor.coerceIn(0, text?.length ?: 0))
            }
        }
        isApplyingHistory = false

        lastLineCount = -1
        updateLineNumbersInternal()
        binding.editorContent.post { scrollToCursor() }
    }

    // Quản lý đồng bộ độ sáng/mờ và trạng thái active của cả 3 nút Toolbar (Undo, Redo, Save)
    private fun refreshToolbarButtons() {
        val canUndo = undoStack.isNotEmpty() || pendingUndoSnapshot != null
        val canRedo = redoStack.isNotEmpty()
        val hasChanges = hasUnsavedChanges()

        undoMenuItem?.isEnabled = canUndo
        redoMenuItem?.isEnabled = canRedo
        saveMenuItem?.isEnabled = hasChanges
    }

    private fun loadFileContent() {
        lifecycleScope.launch(Dispatchers.IO) {
            var content = ""
            var newFile = true
            try {
                PathAnalysis(applicationContext, configDir).parsePath(filePath)?.let { stream ->
                    newFile = false
                    content = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
            } catch (_: Exception) {
            }

            // Chỉ điền nội dung khởi tạo (value / value-sh) khi file CHƯA tồn tại.
            // Nếu file đã tồn tại thì giữ nguyên, không điền/ghi đè gì thêm.
            if (newFile) {
                val computedValue = if (initialValueSh.isNotEmpty()) {
                    try {
                        com.omarea.krscript.executor.ScriptEnvironmen.executeResultRoot(
                            applicationContext,
                            initialValueSh,
                            com.omarea.krscript.model.NodeInfoBase("")
                        )
                    } catch (_: Exception) {
                        ""
                    }
                } else {
                    initialValue
                }

                if (computedValue.isNotEmpty()) {
                    content = computedValue
                    if (writeFileContent(absoluteFilePath, content)) {
                        newFile = false
                    }
                }
            }

            withContext(Dispatchers.Main) {
                savedContent = content
                isApplyingHistory = true
                withHighlightSuspended {
                    binding.editorContent.setText(content)
                    binding.editorContent.setSelection(content.length)
                }
                isApplyingHistory = false

                editorHandler.removeCallbacks(commitPendingUndoRunnable)
                pendingUndoSnapshot = null
                undoStack.clear()
                redoStack.clear()

                restoreUndoCacheFromDisk()
                refreshToolbarButtons()

                binding.editorContent.hint = when {
                    placeholderText.isNotEmpty() -> placeholderText
                    newFile -> getString(R.string.editor_hint_new_file)
                    else -> getString(R.string.editor_hint_empty)
                }
                setupSyntaxHighlighting()
                updateLineNumbersInternal()
            }
        }
    }

    // Áp dụng trạng thái chỉ đọc (readonly="true"): không cho phép gõ/sửa/dán nội dung
    private fun applyReadonlyState() {
        if (!readonlyMode) return
        binding.editorContent.keyListener = null
        binding.editorContent.isFocusable = true
        binding.editorContent.isFocusableInTouchMode = true
        binding.editorContent.isLongClickable = true
    }

    private fun applyWrapState() {
        val editText = binding.editorContent
        val container = binding.editorContentContainer
        val currentText = editText.text?.toString().orEmpty()
        val cursorStart = editText.selectionStart.coerceIn(0, currentText.length)
        val cursorEnd = editText.selectionEnd.coerceIn(0, currentText.length)

        (editText.parent as? ViewGroup)?.removeView(editText)
        noWrapContainer?.let { container.removeView(it) }
        editText.setHorizontallyScrolling(!wrapEnabled)

        if (wrapEnabled) {
            editText.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            container.addView(editText)
        } else {
            val hsv = noWrapContainer ?: HorizontalScrollView(this).also {
                it.isFillViewport = true
                it.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
                noWrapContainer = it
            }
            editText.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            hsv.apply {
                removeAllViews()
                addView(editText)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scrollTo(0, 0)
            }
            container.addView(hsv)
        }

        editText.setSelection(
            cursorStart.coerceAtMost(currentText.length),
            cursorEnd.coerceAtMost(currentText.length)
        )

        lastLineCount = -1
        editText.post { updateLineNumbersInternal() }
    }

    private fun applyMonospaceState() {
        val targetTypeface = if (monospaceEnabled) Typeface.MONOSPACE else Typeface.DEFAULT
        binding.editorContent.typeface = targetTypeface
        binding.editorLineNumbers.typeface = targetTypeface

        lastLineCount = -1
        binding.editorContent.post { updateLineNumbersInternal() }
    }

    private fun hasUnsavedChanges() = binding.editorContent.text?.toString().orEmpty() != savedContent

    private fun attemptClose() {
        if (isSaving) return
        if (readonlyMode) {
            finish()
            return
        }
        commitPendingUndoSnapshot()

        if (!hasUnsavedChanges()) {
            clearUndoCache()
            finish()
            return
        }

        DialogHelper.confirm(
            this,
            title = getString(R.string.editor_unsaved_title),
            message = getString(R.string.editor_unsaved_message),
            onConfirm = DialogHelper.DialogButton(getString(R.string.editor_save), Runnable {
                saveFile(showToast = true) { success ->
                    if (success && !isFinishing && !isDestroyed) finish()
                }
            }),
            onCancel = DialogHelper.DialogButton(getString(R.string.editor_discard), Runnable {
                clearUndoCache()
                finish()
            })
        )
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_text_editor, menu)

        undoMenuItem = menu.findItem(R.id.editor_menu_undo)
        redoMenuItem = menu.findItem(R.id.editor_menu_redo)
        saveMenuItem = menu.findItem(R.id.editor_menu_save)

        menu.findItem(R.id.editor_menu_run)?.isVisible = !readonlyMode && runnableInterpreter() != null

        if (readonlyMode) {
            menu.findItem(R.id.editor_menu_save)?.isVisible = false
            menu.findItem(R.id.editor_menu_undo)?.isVisible = false
            menu.findItem(R.id.editor_menu_redo)?.isVisible = false
        }

        // Nút "⋮" tự dựng - cùng icon/kích thước/vị trí như overflow mặc định cũ,
        // chỉ đổi nền popup bên trong sang kiểu bo góc giống ActionPage.
        val overflowItem = menu.add(Menu.NONE, Menu.NONE, 5, getString(R.string.kr_more_options))
        overflowItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        val overflowButton = OverflowMenuPopup.buildButton(this)
        overflowButton.setOnClickListener { showEditorOverflowPopup(overflowButton) }
        overflowItem.actionView = overflowButton

        refreshToolbarButtons()
        return true
    }

    // Đọc lại trạng thái checkbox mới nhất mỗi lần mở popup.
    private fun showEditorOverflowPopup(anchor: View) {
        val rows = listOf(
            PopupMenuRow(
                title = getString(R.string.editor_toggle_wrap),
                leftIcon = null,
                typeIcon = PopupRowTypeIcon.CHECKBOX,
                checked = wrapEnabled
            ) {
                wrapEnabled = !wrapEnabled
                applyWrapState()
            },
            PopupMenuRow(
                title = getString(R.string.editor_toggle_monospace),
                leftIcon = null,
                typeIcon = PopupRowTypeIcon.CHECKBOX,
                checked = monospaceEnabled
            ) {
                monospaceEnabled = !monospaceEnabled
                applyMonospaceState()
            }
        )
        OverflowMenuPopup.show(this, anchor, rows)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.editor_menu_undo -> {
                performUndo()
                true
            }
            R.id.editor_menu_redo -> {
                performRedo()
                true
            }
            R.id.editor_menu_run -> {
                runnableInterpreter()?.let { saveAndRun(it) }
                true
            }
            R.id.editor_menu_save -> {
                saveFile()
                true
            }
            android.R.id.home -> {
                attemptClose()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun saveFile(
        showToast: Boolean = true,
        onResult: ((Boolean) -> Unit)? = null
    ) {
        if (isSaving || !hasUnsavedChanges()) return
        if (readonlyMode) return
        isSaving = true
        commitPendingUndoSnapshot()

        val content = binding.editorContent.text?.toString().orEmpty()

        lifecycleScope.launch(Dispatchers.IO) {
            val success = writeFileContent(absoluteFilePath, content)
            withContext(Dispatchers.Main) {
                isSaving = false
                if (success) {
                    savedContent = content
                    persistUndoCacheToDisk()
                    refreshToolbarButtons()
                }
                if (showToast) {
                    Toast.makeText(
                        this@TextEditorActivity,
                        if (success) R.string.editor_save_success else R.string.editor_save_fail,
                        if (success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                    ).show()
                }
                onResult?.invoke(success)
            }
        }
    }

    private fun writeFileContent(path: String, content: String): Boolean {
        try {
            val file = File(path)
            val parent = file.parentFile?.apply { if (!exists()) mkdirs() }
            if (parent != null && (parent.canWrite() || (file.exists() && file.canWrite()))) {
                file.writeText(content, Charsets.UTF_8)
                return true
            }
        } catch (_: Exception) {
        }

        if (!KeepShellPublic.checkRoot()) return false

        val privateCacheDir = FileWrite.getPrivateFilePath(
            this,
            "home/tmp/tmp_${System.currentTimeMillis()}.tmp"
        )

        return try {
            val cacheFile = File(privateCacheDir).apply {
                parentFile?.mkdirs()
                writeText(content, Charsets.UTF_8)
            }
            val command = """
                mkdir -p "${File(path).parent ?: "/"}"
                cp -f "$privateCacheDir" "$path"
                chmod 644 "$path"
                if [ -f "$path" ]; then echo EDITOR_SAVE_OK; fi
            """.trimIndent()
            val result = KeepShellPublic.doCmdSync(command)
            cacheFile.delete()
            result.trim().contains("EDITOR_SAVE_OK")
        } catch (_: Exception) {
            false
        }
    }

    private fun saveAndRun(interpreter: String) {
        if (hasUnsavedChanges()) {
            saveFile(showToast = false) { success ->
                if (success) {
                    runScript(interpreter)
                } else {
                    Toast.makeText(this, R.string.editor_save_fail, Toast.LENGTH_LONG).show()
                }
            }
        } else {
            runScript(interpreter)
        }
    }

    private fun runScript(interpreter: String) {
        val runNode = ActionNode(absoluteFilePath).apply {
            title = titleText.ifEmpty { File(absoluteFilePath).name }
            interruptable = true
            shell = RunnableNode.shellModeDefault
            needInput = this@TextEditorActivity.needInput
        }
        val script = "chmod 755 \"$absoluteFilePath\" 2>/dev/null\n$interpreter \"$absoluteFilePath\"\n"
        DialogLogFragment.create(
            runNode,
            {},
            {},
            script,
            null,
            ThemeModeState.isDarkMode()
        ).apply { isCancelable = false }
            .show(supportFragmentManager, "editor-run-test")
    }
}