package com.tool.tree

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.util.TypedValue
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.CharacterStyle
import android.text.style.ClickableSpan
import android.text.Layout
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.LineBackgroundSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.UnderlineSpan
import android.text.style.URLSpan
import android.view.View
import android.widget.Toast
import com.omarea.krscript.ui.MarkdownCodeSpan
import com.omarea.krscript.ui.MarkdownInlineCodeSpan
import com.omarea.krscript.ui.MarkdownInlineHelper

/**
 * Khối ```...``` bắt đầu ở đầu dòng được tách bằng [splitBlocks] thành khối code riêng (hiển thị thành khung có nút sao chép).
 * Văn bản trong ``` nằm giữa dòng vẫn giữ nguyên từng ký tự bằng [MarkdownCodeSpan], không parse markdown, không bị bắt link.
 */
object ChatMarkdown {
    object SelectableLinkMovement : LinkMovementMethod() {
        override fun canSelectArbitrarily(): Boolean = true
    }

    private val BARE_URL = Regex("(?i)\\b(?:https?://|www\\.)[^\\s<>\"']+")
    private const val URL_TRAILING = ".,;:!?'\"*_~"

    fun linkify(source: CharSequence): CharSequence {
        if (source.length < 4) return source
        val out = SpannableStringBuilder(source)
        var found = false
        for (match in BARE_URL.findAll(source)) {
            var end = match.range.last + 1
            val start = match.range.first
            while (end > start) {
                val c = source[end - 1]
                val opens = source.subSequence(start, end).count { it == '(' }
                val closes = source.subSequence(start, end).count { it == ')' }
                if (URL_TRAILING.indexOf(c) >= 0 || (c == ')' && closes > opens) || c == ']' || c == '}') end-- else break
            }
            if (end - start < 4) continue
            if (out.getSpans(start, end, ClickableSpan::class.java).isNotEmpty()) continue
            if (out.getSpans(start, end, MarkdownCodeSpan::class.java).isNotEmpty()) continue
            val url = source.subSequence(start, end).toString()
            val href = if (url.startsWith("www.", ignoreCase = true)) "http://$url" else url
            out.setSpan(URLSpan(href), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            found = true
        }
        return if (found) out else source
    }

    private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
    private val BULLET = Regex("^(\\s*)[-*+]\\s+(.*)$")
    private val ORDERED = Regex("^(\\s*)(\\d{1,9})[.)]\\s+(.*)$")
    private val TASK = Regex("^\\[([ xX])\\]\\s+(.*)$")
    private val QUOTE = Regex("^\\s{0,3}>[> ]*(.*)$")
    private val TILDE_FENCE = Regex("(?m)^([ \\t]*)~~~(?=[^~]|$)")
    private val SETEXT_UNDER = Regex("^\\s{0,3}(=+|-+)\\s*$")
    private val NOT_SETEXT_TITLE = Regex("^\\s{0,3}(#|>|[-*+]\\s|\\d{1,9}[.)]\\s|\\||:\\s)")
    private val REF_DEF = Regex("^\\s{0,3}\\[([^\\]^][^\\]]*)]:\\s*<?((?:https?://|mailto:|www\\.|/|\\./|\\.\\./|#)[^\\s>]*)>?(?:\\s+(?:\"[^\"]*\"|'[^']*'|\\([^)]*\\)))?\\s*$")
    private val REF_FULL = Regex("\\[([^\\[\\]]+)]\\[([^\\[\\]]*)]")
    private val REF_SHORT = Regex("\\[([^\\[\\]^][^\\[\\]]*)](?![(\\[:])")
    private val SEP_CELL = Regex("^:?-+:?$")
    private val RULE = Regex("^\\s{0,3}([-*_])(?:[ \\t]*\\1){2,}[ \\t]*$")
    private val FENCE_INFO = Regex("^[A-Za-z0-9_+#.\\-]*[ \\t\\r]*$")
    private const val INLINE_MARKERS = "*_`~[{<=^"
    private const val FENCE = "```"

    private const val SEG_NORMAL = 0
    private const val SEG_CODE = 1
    private const val SEG_MARK = 2

    private class Seg(val kind: Int, val start: Int, val end: Int, val block: Boolean = false, val info: String = "")

    class Table(val header: List<CharSequence>, val rows: List<List<CharSequence>>, val aligns: IntArray)

    class Block(val isCode: Boolean, val text: CharSequence, val lang: String = "", val table: Table? = null) {
        val isTable: Boolean get() = table != null
    }

    private fun normalizeFences(s: String): String {
        if (!s.contains("~~~") || s.contains(FENCE)) return s
        return TILDE_FENCE.replace(s) { it.groupValues[1] + FENCE }
    }

    private fun applySetext(s: String): String {
        val lines = s.split('\n')
        if (lines.size < 2) return s
        val res = ArrayList<String>(lines.size)
        var changed = false
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val next = lines.getOrNull(i + 1)
            val prevBlank = i == 0 || lines[i - 1].isBlank()
            if (next != null && prevBlank && line.isNotBlank() && !NOT_SETEXT_TITLE.containsMatchIn(line) && !RULE.matches(line.trimEnd('\r'))) {
                val m = SETEXT_UNDER.matchEntire(next.trimEnd('\r'))
                if (m != null && m.groupValues[1].length >= 3) {
                    res.add((if (m.groupValues[1][0] == '=') "# " else "## ") + line)
                    changed = true
                    i += 2
                    continue
                }
            }
            res.add(line)
            i++
        }
        return if (changed) res.joinToString("\n") else s
    }

    private fun applyReferences(s: String): String {
        if (!s.contains("]:")) return s
        val refs = HashMap<String, String>()
        val body = ArrayList<String>()
        for (line in s.split('\n')) {
            val r = REF_DEF.matchEntire(line.trimEnd('\r'))
            if (r != null) {
                refs[r.groupValues[1].trim().lowercase()] = r.groupValues[2]
                continue
            }
            body.add(line)
        }
        if (refs.isEmpty()) return s
        var text = body.joinToString("\n")
        text = REF_FULL.replace(text) { m ->
            val label = m.groupValues[1]
            val id = m.groupValues[2].ifEmpty { label }.trim().lowercase()
            val url = refs[id]
            if (url != null) "[$label]($url)" else m.value
        }
        text = REF_SHORT.replace(text) { m ->
            val url = refs[m.groupValues[1].trim().lowercase()]
            if (url != null) "[${m.groupValues[1]}]($url)" else m.value
        }
        return text
    }

    fun preprocess(source: CharSequence): CharSequence {
        if (source.isEmpty() || hasStyle(source)) return source
        val original = source.toString()
        if (original.contains(FENCE) || original.contains("~~~")) return source
        var s = applyReferences(original)
        s = applySetext(s)
        return if (s == original) source else s
    }

    fun splitBlocks(source: CharSequence): List<Block> {
        val s = normalizeFences(source.toString())
        val out = ArrayList<Block>()
        if (!s.contains(FENCE)) {
            addTextBlock(out, source, 0, s.length)
            return out
        }
        val segs = splitFences(s)
        var textFrom = 0
        var i = 0
        while (i < segs.size) {
            val seg = segs[i]
            val code = segs.getOrNull(i + 1)
            if (seg.kind == SEG_MARK && code != null && code.kind == SEG_CODE && code.block) {
                addTextBlock(out, source, textFrom, seg.start)
                if (code.end > code.start) out.add(Block(true, source.subSequence(code.start, code.end), code.info))
                val close = segs.getOrNull(i + 2)
                if (close != null && close.kind == SEG_MARK) {
                    textFrom = close.start + FENCE.length
                    i += 3
                } else {
                    textFrom = s.length
                    i += 2
                }
            } else {
                i++
            }
        }
        addTextBlock(out, source, textFrom, s.length)
        return out
    }

    private fun addTextBlock(out: MutableList<Block>, source: CharSequence, from: Int, to: Int) {
        var a = from
        var b = to
        while (a < b && (source[a] == '\n' || source[a] == '\r')) a++
        while (b > a && (source[b - 1] == '\n' || source[b - 1] == '\r')) b--
        if (b <= a || !source.subSequence(a, b).isNotBlank()) return
        if (source.indexOf('|', a) !in a until b) {
            out.add(Block(false, source.subSequence(a, b)))
            return
        }

        val s = source.toString()
        val lines = ArrayList<IntArray>()
        var p = a
        while (true) {
            val nl = s.indexOf('\n', p)
            val e = if (nl < 0 || nl > b) b else nl
            var le = e
            if (le > p && s[le - 1] == '\r') le--
            lines.add(intArrayOf(p, le))
            if (e >= b) break
            p = e + 1
        }

        var textFrom = a
        var i = 0
        while (i < lines.size) {
            val head = if (i + 1 < lines.size && s.substring(lines[i][0], lines[i][1]).contains('|')) splitRow(source, lines[i][0], lines[i][1]) else null
            val aligns = if (head != null) parseSeparator(s, lines[i + 1][0], lines[i + 1][1]) else null
            if (head != null && aligns != null && aligns.size == head.size) {
                if (lines[i][0] > textFrom) addTextBlock(out, source, textFrom, lines[i][0])
                val rows = ArrayList<List<CharSequence>>()
                var j = i + 2
                while (j < lines.size) {
                    val line = s.substring(lines[j][0], lines[j][1])
                    if (line.isBlank() || !line.contains('|')) break
                    val cells = splitRow(source, lines[j][0], lines[j][1])
                    rows.add(List(head.size) { k -> cells.getOrElse(k) { "" } })
                    j++
                }
                out.add(Block(false, source.subSequence(lines[i][0], lines[j - 1][1]), "", Table(head, rows, aligns)))
                textFrom = lines[j - 1][1]
                i = j
            } else {
                i++
            }
        }
        if (textFrom < b) {
            if (textFrom == a) out.add(Block(false, source.subSequence(a, b)))
            else addTextBlock(out, source, textFrom, b)
        }
    }

    private fun splitRow(source: CharSequence, from: Int, to: Int): List<CharSequence> {
        var a = from
        var b = to
        while (a < b && source[a].isWhitespace()) a++
        while (b > a && source[b - 1].isWhitespace()) b--
        if (a < b && source[a] == '|') a++
        if (b > a && source[b - 1] == '|' && (b - 2 < a || source[b - 2] != '\\')) b--
        val cells = ArrayList<CharSequence>()
        var cs = a
        var k = a
        while (k <= b) {
            if (k == b || (source[k] == '|' && (k == a || source[k - 1] != '\\'))) {
                var x = cs
                var y = k
                while (x < y && source[x].isWhitespace()) x++
                while (y > x && source[y - 1].isWhitespace()) y--
                val cell = source.subSequence(x, y)
                cells.add(if (cell !is Spanned && cell.contains("\\|")) cell.toString().replace("\\|", "|") else cell)
                cs = k + 1
            }
            k++
        }
        return cells
    }

    private fun parseSeparator(s: String, from: Int, to: Int): IntArray? {
        val line = s.substring(from, to)
        if (!line.contains('-')) return null
        val cells = splitRow(line, 0, line.length)
        if (cells.isEmpty()) return null
        val aligns = IntArray(cells.size)
        for ((idx, c) in cells.withIndex()) {
            val t = c.toString()
            if (!SEP_CELL.matches(t)) return null
            aligns[idx] = if (t.startsWith(":") && t.endsWith(":")) 1 else if (t.endsWith(":")) 2 else 0
        }
        return aligns
    }

    fun renderInline(context: Context, text0: CharSequence): CharSequence {
        if (text0.isEmpty() || hasStyle(text0)) return text0
        val body = text0.toString()
        if (body.indexOfAny(INLINE_MARKERS.toCharArray()) < 0) return text0
        val (plain, spans) = MarkdownInlineHelper.parse(body)
        val out = SpannableStringBuilder(plain)
        applySpans(context, out, spans, 0)
        return out
    }

    private class LineStyle {
        var headingScale = 0f
        var quote = false
        var rule = false
        var listPrefix = ""
    }

    private class QuoteBarSpan(private val color: Int, private val barPx: Int, private val gapPx: Int) : LeadingMarginSpan {
        override fun getLeadingMargin(first: Boolean): Int = barPx + gapPx

        override fun drawLeadingMargin(
            c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
            text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout?
        ) {
            val oldStyle = p.style
            val oldColor = p.color
            p.style = Paint.Style.FILL
            p.color = color
            val x2 = x + dir * barPx
            c.drawRect(minOf(x, x2).toFloat(), top.toFloat(), maxOf(x, x2).toFloat(), bottom.toFloat(), p)
            p.style = oldStyle
            p.color = oldColor
        }
    }

    private class RuleSpan(private val color: Int, private val widthPx: Float) : LineBackgroundSpan {
        override fun drawBackground(
            c: Canvas, p: Paint, left: Int, right: Int, top: Int, baseline: Int, bottom: Int,
            text: CharSequence, start: Int, end: Int, lnum: Int
        ) {
            val oldColor = p.color
            val oldWidth = p.strokeWidth
            p.color = color
            p.strokeWidth = widthPx
            val y = (top + bottom) / 2f
            c.drawLine(left.toFloat(), y, right.toFloat(), y, p)
            p.color = oldColor
            p.strokeWidth = oldWidth
        }
    }

    private class Piece(val text: CharSequence, val code: Boolean, val lineStart: Boolean)

    private fun splitFences(s: String): List<Seg> {
        val segs = ArrayList<Seg>()
        val n = s.length
        var pos = 0
        while (pos < n) {
            val open = s.indexOf(FENCE, pos)
            if (open < 0) break
            val close = s.indexOf(FENCE, open + 3)
            val atLineStart = s.substring(s.lastIndexOf('\n', open - 1) + 1, open).isBlank()
            if (close < 0 && !atLineStart) break
            if (open > pos) segs.add(Seg(SEG_NORMAL, pos, open))
            segs.add(Seg(SEG_MARK, open, open))

            val contentEnd = if (close < 0) n else close
            var cs = open + 3
            var infoHandled = false
            var info = ""
            if (atLineStart) {
                val nl = s.indexOf('\n', cs)
                val hasBreak = nl in cs until contentEnd
                val firstEnd = if (hasBreak) nl else contentEnd
                if (FENCE_INFO.matches(s.substring(cs, firstEnd)) && (hasBreak || close < 0)) {
                    info = s.substring(cs, firstEnd).trim()
                    cs = if (hasBreak) nl + 1 else contentEnd
                    infoHandled = true
                }
            }
            if (!infoHandled && cs < contentEnd) {
                if (s[cs] == '\n') cs++
                else if (s[cs] == '\r' && cs + 1 < contentEnd && s[cs + 1] == '\n') cs += 2
            }

            var ce = contentEnd
            if (close >= 0) {
                val lb = s.lastIndexOf('\n', close - 1)
                if (lb >= cs && s.substring(lb + 1, close).isBlank()) {
                    ce = if (lb > cs && s[lb - 1] == '\r') lb - 1 else lb
                }
            }
            segs.add(Seg(SEG_CODE, cs, maxOf(cs, ce), atLineStart, info))

            if (close >= 0) {
                segs.add(Seg(SEG_MARK, close, close))
                pos = close + 3
            } else {
                pos = n
            }
        }
        if (pos < n) segs.add(Seg(SEG_NORMAL, pos, n))
        return segs
    }


    fun render(context: Context, source0: CharSequence): CharSequence {
        if (source0.isEmpty()) return source0
        val source = preprocess(source0)
        val s = normalizeFences(source.toString())
        val out = SpannableStringBuilder()
        val pieces = ArrayList<Piece>()
        var hasMark = false
        var lineFirst = true
        var wrote = false
        var lastQuote = false

        fun flush(codeLineBreak: Boolean) {
            val visible = pieces.any { if (it.code) it.text.isNotEmpty() else it.text.isNotBlank() }
            if (!(hasMark && !visible && !codeLineBreak)) {
                if (wrote) out.append('\n')
                wrote = true
                lastQuote = renderPieces(context, pieces, out, lastQuote)
            }
            pieces.clear()
            hasMark = false
            lineFirst = true
        }

        for (seg in splitFences(s)) {
            if (seg.kind == SEG_MARK) {
                hasMark = true
                lineFirst = false
                continue
            }
            val code = seg.kind == SEG_CODE
            var from = seg.start
            while (true) {
                val nl = if (from <= seg.end) s.indexOf('\n', from) else -1
                if (nl < 0 || nl >= seg.end) {
                    if (seg.end > from) {
                        pieces.add(Piece(source.subSequence(from, seg.end), code, lineFirst))
                        lineFirst = false
                    }
                    break
                }
                if (nl > from) {
                    pieces.add(Piece(source.subSequence(from, nl), code, lineFirst))
                    lineFirst = false
                }
                flush(code)
                from = nl + 1
            }
        }
        flush(false)
        return out
    }

    private fun renderPieces(context: Context, pieces: List<Piece>, out: SpannableStringBuilder, afterQuote: Boolean): Boolean {
        val lineStart = out.length
        val style = LineStyle()
        for (p in pieces) {
            if (p.code) {
                val s = out.length
                out.append(p.text)
                if (out.length > s) out.setSpan(MarkdownCodeSpan(context), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                renderNormal(context, p, out, style, afterQuote)
            }
        }
        if (out.length > lineStart) {
            val density = context.resources.displayMetrics.density
            if (style.headingScale != 0f) {
                out.setSpan(StyleSpan(Typeface.BOLD), lineStart, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(RelativeSizeSpan(style.headingScale), lineStart, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (style.quote) {
                out.setSpan(QuoteBarSpan(0xFF9E9E9E.toInt(), (3 * density).toInt().coerceAtLeast(2), (8 * density).toInt()), lineStart, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (style.rule) {
                out.setSpan(RuleSpan(0x80808080.toInt(), (1 * density).coerceAtLeast(1f)), lineStart, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (style.listPrefix.isNotEmpty()) {
                val paint = TextPaint()
                paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15f, context.resources.displayMetrics)
                val hang = paint.measureText(style.listPrefix).toInt()
                out.setSpan(LeadingMarginSpan.Standard(0, hang), lineStart, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return style.quote
    }

    private fun hasStyle(text: CharSequence): Boolean {
        val sp = text as? Spanned ?: return false
        return sp.getSpans(0, text.length, CharacterStyle::class.java).isNotEmpty()
    }

    private fun bulletFor(indent: String): String {
        var depth = 0
        for (ch in indent) depth += if (ch == '\t') 4 else 1
        return when {
            depth < 2 -> "\u2022"
            depth < 4 -> "\u25E6"
            else -> "\u25AA"
        }
    }

    private fun renderNormal(context: Context, piece: Piece, out: SpannableStringBuilder, style: LineStyle, afterQuote: Boolean) {
        if (hasStyle(piece.text)) {
            out.append(piece.text)
            return
        }
        val str = piece.text.toString()
        var prefix = ""
        var body = str
        var block = true

        if (piece.lineStart) {
            val h = HEADING.matchEntire(str)
            if (h != null) {
                body = h.groupValues[2]
                style.headingScale = when (h.groupValues[1].length) { 1 -> 1.3f; 2 -> 1.15f; 3 -> 1.05f; else -> 1f }
            } else if (RULE.matches(str)) {
                body = "\u00A0"
                style.rule = true
            } else {
                val q = QUOTE.matchEntire(str)
                if (q != null) {
                    body = q.groupValues[1]
                    style.quote = true
                } else {
                    val b = BULLET.matchEntire(str)
                    if (b != null) {
                        val indent = b.groupValues[1]
                        val rest = b.groupValues[2]
                        val t = TASK.matchEntire(rest)
                        if (t != null) {
                            prefix = indent + (if (t.groupValues[1] == " ") "\u2610 " else "\u2611 ")
                            body = t.groupValues[2]
                        } else {
                            prefix = indent + bulletFor(indent) + " "
                            body = rest
                        }
                    } else {
                        val o = ORDERED.matchEntire(str)
                        if (o != null) {
                            prefix = o.groupValues[1] + o.groupValues[2] + ". "
                            body = o.groupValues[3]
                        } else {
                            block = false
                        }
                    }
                }
            }
            if (!block && afterQuote && str.isNotBlank()) style.quote = true
            if (prefix.isNotBlank() && !style.quote) style.listPrefix = prefix
        }

        out.append(prefix)

        if (body.indexOfAny(INLINE_MARKERS.toCharArray()) < 0) {
            out.append(body)
        } else {
            val (plain, spans) = MarkdownInlineHelper.parse(body)
            val offset = out.length
            out.append(plain)
            applySpans(context, out, spans, offset)
        }
    }

    private fun applySpans(
        context: Context,
        out: SpannableStringBuilder,
        spans: List<MarkdownInlineHelper.MarkdownSpanInfo>,
        offset: Int
    ) {
        for (info in spans) {
            val start = info.start + offset
            val end = (info.end + offset).coerceAtMost(out.length)
            if (start < 0 || end <= start) continue
            when (info.type) {
                MarkdownInlineHelper.MarkdownSpanType.BOLD ->
                    out.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                MarkdownInlineHelper.MarkdownSpanType.ITALIC ->
                    out.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                MarkdownInlineHelper.MarkdownSpanType.STRIKETHROUGH ->
                    out.setSpan(StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                MarkdownInlineHelper.MarkdownSpanType.HIGHLIGHT ->
                    out.setSpan(BackgroundColorSpan(0x66FFEB3B), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                MarkdownInlineHelper.MarkdownSpanType.UNDERLINE ->
                    out.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                MarkdownInlineHelper.MarkdownSpanType.SUPERSCRIPT -> {
                    out.setSpan(SuperscriptSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    out.setSpan(RelativeSizeSpan(0.75f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                MarkdownInlineHelper.MarkdownSpanType.SUBSCRIPT -> {
                    out.setSpan(SubscriptSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    out.setSpan(RelativeSizeSpan(0.75f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                MarkdownInlineHelper.MarkdownSpanType.CODE -> {
                    val radius = info.href.toFloatOrNull()
                    MarkdownInlineCodeSpan.apply(context, out, start, end, radius)
                }
                MarkdownInlineHelper.MarkdownSpanType.COLOR -> {
                    val color = try { Color.parseColor(info.href) } catch (e: IllegalArgumentException) { null }
                    if (color != null) out.setSpan(ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                MarkdownInlineHelper.MarkdownSpanType.LINK -> {
                    val href = info.href
                    out.setSpan(object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(href)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (ex: Exception) {
                                Toast.makeText(context, href, Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun updateDrawState(ds: TextPaint) {
                            ds.isUnderlineText = true
                        }
                    }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
    }
}
