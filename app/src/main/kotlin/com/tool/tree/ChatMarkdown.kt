package com.tool.tree

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.CharacterStyle
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.view.View
import android.widget.Toast
import com.omarea.krscript.ui.MarkdownCodeSpan
import com.omarea.krscript.ui.MarkdownInlineCodeSpan
import com.omarea.krscript.ui.MarkdownInlineHelper

/**
 * Văn bản nằm trong ```...``` (một dòng hoặc nhiều dòng, ở bất kỳ vị trí nào) được giữ nguyên từng ký tự,
 * không parse markdown, không bị bắt link; chỉ thêm nền code.
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

    private val HEADING = Regex("^(#{1,3})\\s+(.*)$")
    private val BULLET = Regex("^(\\s*)[-*+]\\s+(.*)$")
    private val INTRA_WORD_UNDERSCORE = Regex("(?<=[\\p{L}\\p{N}])_(?=[\\p{L}\\p{N}])")
    private val FENCE_INFO = Regex("^[A-Za-z0-9_+#.\\-]*[ \\t\\r]*$")
    private const val INLINE_MARKERS = "*_`~[{\\"
    private const val FENCE = "```"

    private const val SEG_NORMAL = 0
    private const val SEG_CODE = 1
    private const val SEG_MARK = 2

    private class Seg(val kind: Int, val start: Int, val end: Int)

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
            if (atLineStart) {
                val nl = s.indexOf('\n', cs)
                val hasBreak = nl in cs until contentEnd
                val firstEnd = if (hasBreak) nl else contentEnd
                if (FENCE_INFO.matches(s.substring(cs, firstEnd)) && (hasBreak || close < 0)) {
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
            segs.add(Seg(SEG_CODE, cs, maxOf(cs, ce)))

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

    fun render(context: Context, source: CharSequence): CharSequence {
        if (source.isEmpty()) return source
        val s = source.toString()
        val out = SpannableStringBuilder()
        val pieces = ArrayList<Piece>()
        var hasMark = false
        var lineFirst = true
        var wrote = false

        fun flush(codeLineBreak: Boolean) {
            val visible = pieces.any { if (it.code) it.text.isNotEmpty() else it.text.isNotBlank() }
            if (!(hasMark && !visible && !codeLineBreak)) {
                if (wrote) out.append('\n')
                wrote = true
                renderPieces(context, pieces, out)
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

    private fun renderPieces(context: Context, pieces: List<Piece>, out: SpannableStringBuilder) {
        val lineStart = out.length
        var headingScale = 0f
        for (p in pieces) {
            if (p.code) {
                val s = out.length
                out.append(p.text)
                if (out.length > s) out.setSpan(MarkdownCodeSpan(context), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                val scale = renderNormal(context, p, out)
                if (scale != 0f) headingScale = scale
            }
        }
        if (headingScale != 0f && out.length > lineStart) {
            out.setSpan(StyleSpan(Typeface.BOLD), lineStart, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.setSpan(RelativeSizeSpan(headingScale), lineStart, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun hasStyle(text: CharSequence): Boolean {
        val sp = text as? Spanned ?: return false
        return sp.getSpans(0, text.length, CharacterStyle::class.java).isNotEmpty()
    }

    private fun renderNormal(context: Context, piece: Piece, out: SpannableStringBuilder): Float {
        if (hasStyle(piece.text)) {
            out.append(piece.text)
            return 0f
        }
        val str = piece.text.toString()
        var prefix = ""
        var body = str
        var headingScale = 0f

        if (piece.lineStart) {
            val h = HEADING.matchEntire(str)
            if (h != null) {
                body = h.groupValues[2]
                headingScale = when (h.groupValues[1].length) { 1 -> 1.3f; 2 -> 1.15f; else -> 1.05f }
            } else {
                val b = BULLET.matchEntire(str)
                if (b != null) {
                    prefix = b.groupValues[1] + "• "
                    body = b.groupValues[2]
                }
            }
        }

        out.append(prefix)

        if (body.indexOfAny(INLINE_MARKERS.toCharArray()) < 0) {
            out.append(body)
        } else {
            val safe = body.replace(INTRA_WORD_UNDERSCORE, "\\\\_")
            val (plain, spans) = MarkdownInlineHelper.parse(safe)
            val offset = out.length
            out.append(plain)
            applySpans(context, out, spans, offset)
        }
        return headingScale
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
                MarkdownInlineHelper.MarkdownSpanType.CODE -> {
                    val radius = info.href.toFloatOrNull()
                    val span = if (radius != null) MarkdownInlineCodeSpan(context, cornerRadiusDp = radius) else MarkdownInlineCodeSpan(context)
                    out.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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
