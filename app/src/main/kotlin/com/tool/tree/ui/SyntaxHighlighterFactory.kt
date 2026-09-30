package com.tool.tree.ui

import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.widget.EditText
import androidx.annotation.ColorInt
import java.io.File
import java.lang.ref.WeakReference
import java.util.Locale

/**
 * Lightweight syntax highlighter for plain EditText.
 *
 * Features:
 * - Debounced highlighting
 * - Support for temporary suspend/resume during bulk edits
 * - Works with shell / xml / toml / properties / python
 */
object SyntaxHighlighterFactory {
    fun create(extension: String?, editText: EditText): SyntaxHighlighter? {
        val ext = extension.orEmpty().lowercase(Locale.ROOT)
        return when (ext) {
            "sh", "bash", "zsh", "ksh" -> ShellSyntaxHighlighter(editText)
            "xml" -> XmlSyntaxHighlighter(editText)
            "toml" -> TomlSyntaxHighlighter(editText)
            "prop", "properties" -> PropSyntaxHighlighter(editText)
            "py" -> PythonSyntaxHighlighter(editText)
            else -> null
        }
    }

    fun createForPath(path: String?, editText: EditText): SyntaxHighlighter? {
        val ext = path.orEmpty().let { File(it).name }.substringAfterLast('.', "")
        return create(ext, editText)
    }
}

interface SyntaxHighlighter {
    fun attach()
    fun detach()
    fun highlight()

    /**
     * Temporarily stop reacting to text changes.
     * Call [resumeHighlighting] after a batch edit.
     */
    fun suspendHighlighting()

    /**
     * Resume highlighting after [suspendHighlighting].
     */
    fun resumeHighlighting()
}

abstract class BaseSyntaxHighlighter(
    editText: EditText,
    @param:ColorInt private val keywordColor: Int,
    @param:ColorInt private val builtinColor: Int,
    @param:ColorInt private val stringColor: Int,
    @param:ColorInt private val commentColor: Int,
    @param:ColorInt private val numberColor: Int,
    @param:ColorInt private val punctuationColor: Int,
) : SyntaxHighlighter {

    private val editTextRef = WeakReference(editText)
    private var watcher: TextWatcher? = null
    private var isHighlighting = false
    private var highlightSuspended = false
    private var pendingHighlight = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val highlightRunnable = Runnable { highlight() }

    companion object {
        private const val MAX_HIGHLIGHT_LENGTH = 150_000
        private const val DEBOUNCE_DELAY_MS = 150L
    }

    protected val editText: EditText?
        get() = editTextRef.get()

    protected abstract fun applyHighlight(text: Editable)

    override fun attach() {
        val et = editText ?: return
        if (watcher != null) return

        watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (s == null) return
                if (isHighlighting || highlightSuspended) {
                    pendingHighlight = true
                    return
                }
                mainHandler.removeCallbacks(highlightRunnable)
                mainHandler.postDelayed(highlightRunnable, DEBOUNCE_DELAY_MS)
            }
        }
        et.addTextChangedListener(watcher)
        highlight()
    }

    override fun detach() {
        mainHandler.removeCallbacks(highlightRunnable)
        pendingHighlight = false
        val et = editText ?: return
        watcher?.let { et.removeTextChangedListener(it) }
        watcher = null
    }

    override fun highlight() {
        val et = editText ?: return
        val text = et.text ?: return

        if (isHighlighting) return
        if (highlightSuspended) {
            pendingHighlight = true
            return
        }
        if (text.length > MAX_HIGHLIGHT_LENGTH) return

        isHighlighting = true
        try {
            applyHighlight(text)
        } catch (_: Throwable) {
        } finally {
            isHighlighting = false
        }
    }

    override fun suspendHighlighting() {
        highlightSuspended = true
    }

    override fun resumeHighlighting() {
        if (!highlightSuspended) return
        highlightSuspended = false
        if (pendingHighlight) {
            pendingHighlight = false
            highlight()
        }
    }

    protected fun clearSpans(text: Editable) {
        val colorSpans = text.getSpans(0, text.length, ForegroundColorSpan::class.java)
        for (span in colorSpans) {
            text.removeSpan(span)
        }
        val styleSpans = text.getSpans(0, text.length, StyleSpan::class.java)
        for (span in styleSpans) {
            if (span.style == Typeface.BOLD) {
                text.removeSpan(span)
            }
        }
    }

    protected fun color(text: Editable, start: Int, end: Int, @ColorInt color: Int) {
        if (start < 0 || end <= start || start >= text.length) return
        val safeEnd = end.coerceAtMost(text.length)
        text.setSpan(
            ForegroundColorSpan(color),
            start,
            safeEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    protected fun bold(text: Editable, start: Int, end: Int) {
        if (start < 0 || end <= start || start >= text.length) return
        val safeEnd = end.coerceAtMost(text.length)
        text.setSpan(
            StyleSpan(Typeface.BOLD),
            start,
            safeEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    protected fun keywordColor(): Int = keywordColor
    protected fun builtinColor(): Int = builtinColor
    protected fun stringColor(): Int = stringColor
    protected fun commentColor(): Int = commentColor
    protected fun numberColor(): Int = numberColor
    protected fun punctuationColor(): Int = punctuationColor
}

class ShellSyntaxHighlighter(editText: EditText) : BaseSyntaxHighlighter(
    editText = editText,
    keywordColor = 0xFF3D8BFF.toInt(),
    builtinColor = 0xFFFFA000.toInt(),
    stringColor = 0xFF2EAD4B.toInt(),
    commentColor = 0xFF8A8A8A.toInt(),
    numberColor = 0xFFAA66CC.toInt(),
    punctuationColor = 0xFF808080.toInt(),
) {
    private val keywords = setOf(
        "if", "then", "else", "elif", "fi",
        "for", "while", "until", "do", "done",
        "case", "esac", "in", "function", "select",
        "time", "coproc"
    )

    private val builtins = setOf(
        "echo", "cd", "pwd", "export", "unset", "alias", "exec",
        "source", ".", "printf", "test", "read", "shift", "set",
        "local", "return", "trap", "kill", "wait", "jobs", "fg", "bg",
        "true", "false", "type", "command", "eval", "let"
    )

    // "#" chỉ được coi là bắt đầu comment khi đứng đầu dòng hoặc có khoảng trắng
    // ngay bên trái (vd: "  # note", "cmd # note"). Nếu dính liền ký tự khác
    // (vd: "$#", "a#b", "$#1") thì không phải comment, không tô màu xám.
    private val shellRegex = Regex(
        "(?<COMMENT>(?m)(?:^|(?<=\\s))#.*\$)" +
            "|(?<STRING>\"(?:\\\\.|[^\"\\\\])*\"|'(?:[^']*)'|`(?:\\\\.|[^`\\\\])*`)" +
            // Dùng possessive quantifier (*+) thay vì (?:[^()]*|\([^()]*\))* để tránh
            // catastrophic backtracking (ReDoS) khiến app bị đơ/ANR khi gõ dở "$(..."
            // mà chưa đóng ngoặc ")". Với quantifier thường, mỗi ký tự thêm vào làm số
            // cách backtrack tăng theo cấp số mũ (2^n); possessive quantifier không
            // backtrack nên thời gian khớp luôn tuyến tính.
            "|(?<COMMAND>\\$\\((?:[^()]*+|\\([^()]*+\\))*+\\))" +
            // Possessive quantifier để tránh backtracking bậc hai (quadratic) khi gõ
            // dở "${..." dài mà chưa đóng dấu "}"
            "|(?<VARIABLE>\\$\\{[A-Za-z_][A-Za-z0-9_]*+[^}]*+\\}|\\$[A-Za-z_][A-Za-z0-9_]*)" +
            "|(?<NUMBER>(?<![A-Za-z0-9_])(?:0x[0-9A-Fa-f]+|[0-9]+)(?![A-Za-z0-9_]))" +
            "|(?<WORD>(?<![A-Za-z0-9_])[A-Za-z_][A-Za-z0-9_]*(?![A-Za-z0-9_]))" +
            "|(?<PUNCTUATION>&&|\\|\\||\\||;|\\(|\\)|\\{|\\}|\\[\\[|\\]\\]|<|>)"
    )

    override fun applyHighlight(text: Editable) {
        clearSpans(text)
        val s = text.toString()

        shellRegex.findAll(s).forEach { result ->
            val groups = result.groups
            val start = result.range.first
            val end = result.range.last + 1

            when {
                groups["COMMENT"] != null -> color(text, start, end, commentColor())
                groups["STRING"] != null -> color(text, start, end, stringColor())
                groups["COMMAND"] != null -> color(text, start, end, builtinColor())
                groups["VARIABLE"] != null -> color(text, start, end, numberColor())
                groups["NUMBER"] != null -> color(text, start, end, numberColor())
                groups["PUNCTUATION"] != null -> color(text, start, end, punctuationColor())
                groups["WORD"] != null -> {
                    val word = result.value
                    if (keywords.contains(word)) {
                        color(text, start, end, keywordColor())
                    } else if (builtins.contains(word)) {
                        color(text, start, end, builtinColor())
                    }
                }
            }
        }
    }
}

class XmlSyntaxHighlighter(editText: EditText) : BaseSyntaxHighlighter(
    editText = editText,
    keywordColor = 0xFF3D8BFF.toInt(),
    builtinColor = 0xFFFFA000.toInt(),
    stringColor = 0xFF2EAD4B.toInt(),
    commentColor = 0xFF8A8A8A.toInt(),
    numberColor = 0xFFAA66CC.toInt(),
    punctuationColor = 0xFF808080.toInt(),
) {
    private val xmlRegex = Regex(
        "(?<COMMENT>(?s)<!--.*?-->)" +
            "|(?<CDATA>(?s)<!\\[CDATA\\[.*?\\]\\]>)" +
            "|(?<TAG></?[A-Za-z_][A-Za-z0-9_:\\-\\.]*)" +
            "|(?<ATTR>\\b[A-Za-z_][A-Za-z0-9_:\\-\\.]*(?=\\s*=))" +
            "|(?<VALUE>\"(?:\\\\.|[^\"\\\\])*\")" +
            "|(?<ENTITY>&(?:amp|lt|gt|apos|quot|#x?[0-9A-Fa-f]+);)" +
            "|(?<PUNCTUATION></?|/>|>[^<]*)"
    )

    override fun applyHighlight(text: Editable) {
        clearSpans(text)
        val s = text.toString()

        xmlRegex.findAll(s).forEach { result ->
            val groups = result.groups
            val start = result.range.first
            val end = result.range.last + 1

            when {
                groups["COMMENT"] != null -> color(text, start, end, commentColor())
                groups["CDATA"] != null -> color(text, start, end, stringColor())
                groups["TAG"] != null -> color(text, start, end, keywordColor())
                groups["ATTR"] != null -> color(text, start, end, builtinColor())
                groups["VALUE"] != null -> color(text, start, end, stringColor())
                groups["ENTITY"] != null -> color(text, start, end, numberColor())
                groups["PUNCTUATION"] != null -> {
                    val rawValue = result.value
                    val punctEnd = when {
                        rawValue.startsWith("</") -> start + 2
                        rawValue.startsWith("/>") -> start + 2
                        rawValue.startsWith("<") || rawValue.startsWith(">") -> start + 1
                        else -> end
                    }
                    color(text, start, punctEnd, punctuationColor())
                }
            }
        }
    }
}

/**
 * TOML syntax highlighter.
 *
 * Supports:
 * - Comments (#...)
 * - Table headers: [table] và [[array of tables]]
 * - Key = Value (bare key, "quoted key", 'literal key')
 * - Strings: "basic", 'literal', """multi-line basic""", '''multi-line literal'''
 * - Numbers: int, float, hex/oct/bin, +/-inf, nan
 * - Booleans: true / false
 * - Dates/times (RFC 3339)
 * - Punctuation: = , . [ ] { }
 */
class TomlSyntaxHighlighter(editText: EditText) : BaseSyntaxHighlighter(
    editText = editText,
    keywordColor = 0xFFE5C07B.toInt(),     // Vàng: Table headers [[section]]
    builtinColor = 0xFF61AFEF.toInt(),     // Xanh dương: Key names (title, desc, script...)
    stringColor = 0xFF98C379.toInt(),      // Cam đào/Hồng đỏ: Chuỗi ("...")
    commentColor = 0xFF7F848E.toInt(),     // Xám mờ: Chú thích (#)
    numberColor = 0xFFE06C75.toInt(),      // Xanh lá: Số (0, 60...) và DateTime
    punctuationColor = 0xFFABB2BF.toInt(), // Xám sáng: Dấu câu (=, ., [, ], {, })
) {
    private val bareKey = "[A-Za-z0-9_-]+"
    private val quotedKey = "\"(?:\\\\.|[^\"\\\\])*\"|'[^']*'"
    private val keyPart = "(?:$bareKey|$quotedKey)"
    // key có thể là dotted key: a.b."c d".e
    private val dottedKey = "$keyPart(?:\\s*\\.\\s*$keyPart)*"

    private val tomlRegex = Regex(
        "(?<COMMENT>(?m)#.*\$)" +
            "|(?<TABLE>(?m)^\\s*\\[\\[?\\s*$dottedKey\\s*]]?)" +
            "|(?<KEYVALUE>(?m)^\\s*($dottedKey)(\\s*=))" +
            "|(?<STRING>\"\"\"(?s).*?\"\"\"|'''(?s).*?'''|\"(?:\\\\.|[^\"\\\\])*\"|'[^']*')" +
            "|(?<BOOLEAN>(?<![A-Za-z0-9_])(?:true|false)(?![A-Za-z0-9_]))" +
            "|(?<DATETIME>\\d{4}-\\d{2}-\\d{2}(?:[Tt ]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:[Zz]|[+-]\\d{2}:\\d{2})?)?|\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?)" +
            "|(?<NUMBER>[+-]?(?:0x[0-9A-Fa-f_]+|0o[0-7_]+|0b[01_]+|(?:inf|nan)|(?:\\d[\\d_]*)?\\.\\d[\\d_]*(?:[eE][+-]?\\d+)?|\\d[\\d_]*(?:[eE][+-]?\\d+)?))" +
            "|(?<PUNCTUATION>[=,.\\[\\]{}])"
    )

    override fun applyHighlight(text: Editable) {
        clearSpans(text)
        val s = text.toString()

        tomlRegex.findAll(s).forEach { result ->
            val groups = result.groups
            val start = result.range.first
            val end = result.range.last + 1

            when {
                groups["COMMENT"] != null -> color(text, start, end, commentColor())
                groups["TABLE"] != null -> color(text, start, end, keywordColor())
                groups["KEYVALUE"] != null -> {
                    val line = result.value
                    val eqIndex = line.lastIndexOf('=')
                    if (eqIndex > 0) {
                        color(text, start, start + eqIndex, builtinColor())
                        color(text, start + eqIndex, start + eqIndex + 1, punctuationColor())
                    }
                }
                groups["STRING"] != null -> color(text, start, end, stringColor())
                groups["BOOLEAN"] != null -> color(text, start, end, 0xFF56B6C2.toInt()) // Cyan sáng cho boolean
                groups["DATETIME"] != null -> color(text, start, end, numberColor())
                groups["NUMBER"] != null -> color(text, start, end, numberColor())
                groups["PUNCTUATION"] != null -> color(text, start, end, punctuationColor())
            }
        }
    }
}

class PropSyntaxHighlighter(editText: EditText) : BaseSyntaxHighlighter(
    editText = editText,
    keywordColor = 0xFF3D8BFF.toInt(),
    builtinColor = 0xFFFFA000.toInt(),
    stringColor = 0xFF2EAD4B.toInt(),
    commentColor = 0xFF8A8A8A.toInt(),
    numberColor = 0xFFAA66CC.toInt(),
    punctuationColor = 0xFF808080.toInt(),
) {
    private val propRegex = Regex(
        "(?<COMMENT>(?m)^\\s*[#!].*\$)" +
            "|(?<KEYVALUE>(?m)^\\s*([A-Za-z0-9_.-]+)(\\s*=))" +
            "|(?<STRING>\"(?:\\\\.|[^\"\\\\])*\")" +
            "|(?<NUMBER>(?<![A-Za-z0-9_])(?:0x[0-9A-Fa-f]+|[0-9]+)(?![A-Za-z0-9_]))" +
            "|(?<SEPARATOR>=)"
    )

    override fun applyHighlight(text: Editable) {
        clearSpans(text)
        val s = text.toString()

        propRegex.findAll(s).forEach { result ->
            val groups = result.groups
            val start = result.range.first
            val end = result.range.last + 1

            when {
                groups["COMMENT"] != null -> color(text, start, end, commentColor())
                groups["KEYVALUE"] != null -> {
                    val line = result.value
                    val keyEnd = line.indexOf('=')
                    if (keyEnd > 0) {
                        color(text, start, start + keyEnd, keywordColor())
                        color(text, start + keyEnd, start + keyEnd + 1, punctuationColor())
                    }
                }
                groups["STRING"] != null -> color(text, start, end, stringColor())
                groups["NUMBER"] != null -> color(text, start, end, numberColor())
                groups["SEPARATOR"] != null -> color(text, start, end, punctuationColor())
            }
        }
    }
}

/**
 * Python syntax highlighter optimized with named-group regex.
 */
class PythonSyntaxHighlighter(editText: EditText) : BaseSyntaxHighlighter(
    editText = editText,
    keywordColor = 0xFF3D8BFF.toInt(),
    builtinColor = 0xFFFFA000.toInt(),
    stringColor = 0xFF2EAD4B.toInt(),
    commentColor = 0xFF8A8A8A.toInt(),
    numberColor = 0xFFAA66CC.toInt(),
    punctuationColor = 0xFF808080.toInt(),
) {
    private val keywords = setOf(
        "False", "None", "True", "and", "as", "assert", "async", "await",
        "break", "class", "continue", "def", "del", "elif", "else", "except",
        "finally", "for", "from", "global", "if", "import", "in", "is", "lambda",
        "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with", "yield"
    )

    private val builtins = setOf(
        "print", "input", "len", "type", "int", "float", "str", "list", "dict", "set",
        "tuple", "range", "open", "abs", "max", "min", "sum", "sorted", "enumerate",
        "zip", "isinstance", "map", "filter", "any", "all", "dir", "id", "help"
    )

    // "#" chỉ được coi là bắt đầu comment khi đứng đầu dòng hoặc có khoảng trắng
    // ngay bên trái. Nếu dính liền ký tự khác thì không phải comment (tránh tô
    // nhầm màu xám cho những trường hợp như "a#b" hay các toán tử ghép "#").
    private val pythonRegex = Regex(
        "(?<COMMENT>(?m)(?:^|(?<=\\s))#.*\$)" +
            "|(?<STRING>\"{3}(?s).*?\"{3}|'{3}(?s).*?'{3}|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*')" +
            "|(?<NUMBER>(?<![A-Za-z0-9_])(?:0x[0-9A-Fa-f]+|\\d+\\.\\d+|\\d+)(?![A-Za-z0-9_]))" +
            "|(?<DECORATOR>@[A-Za-z_][A-Za-z0-9_]*)" +
            "|(?<WORD>(?<![A-Za-z0-9_])[A-Za-z_][A-Za-z0-9_]*(?![A-Za-z0-9_]))" +
            "|(?<PUNCTUATION>[:.,;()\\[\\]{}@+\\-*/%=<>!&|^~])"
    )

    override fun applyHighlight(text: Editable) {
        clearSpans(text)
        val s = text.toString()

        pythonRegex.findAll(s).forEach { result ->
            val groups = result.groups
            val start = result.range.first
            val end = result.range.last + 1

            when {
                groups["COMMENT"] != null -> color(text, start, end, commentColor())
                groups["STRING"] != null -> color(text, start, end, stringColor())
                groups["NUMBER"] != null -> color(text, start, end, numberColor())
                groups["DECORATOR"] != null -> color(text, start, end, builtinColor())
                groups["PUNCTUATION"] != null -> color(text, start, end, punctuationColor())
                groups["WORD"] != null -> {
                    val word = result.value
                    if (keywords.contains(word)) {
                        color(text, start, end, keywordColor())
                    } else if (builtins.contains(word)) {
                        color(text, start, end, builtinColor())
                    }
                }
            }
        }
    }
}