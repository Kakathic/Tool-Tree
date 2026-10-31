package com.tool.tree

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.CharacterStyle
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.Toast
import com.omarea.krscript.ui.MarkdownCodeSpan
import com.omarea.krscript.ui.MarkdownInlineHelper

/**
 * Render markdown cơ bản cho bong bóng chat. Chỉ dựng lại CharSequence để HIỂN THỊ,
 * không đụng vào buffer gốc (message.text) nên \r ghi đè dòng, cắt MAX_REPLY_CHARS,
 * copy, lưu phiên đều vẫn làm việc trên text thô.
 *
 * Hỗ trợ: # ## ### tiêu đề, - * + gạch đầu dòng, ``` khối code, **đậm**, *nghiêng*,
 * ~~gạch~~, `code`, [chữ](link), {chữ}(#màu) - phần inline dùng MarkdownInlineHelper có sẵn.
 * Dòng đã có màu/style ANSI thì giữ nguyên, không parse.
 */
object ChatMarkdown {
    /** Bấm được link nhưng vẫn cho nhấn giữ để bôi chọn chữ (LinkMovementMethod gốc chặn chọn). */
    object SelectableLinkMovement : LinkMovementMethod() {
        override fun canSelectArbitrarily(): Boolean = true
    }

    private val HEADING = Regex("^(#{1,3})\\s+(.*)$")
    private val BULLET = Regex("^(\\s*)[-*+]\\s+(.*)$")
    private val INTRA_WORD_UNDERSCORE = Regex("(?<=[\\p{L}\\p{N}])_(?=[\\p{L}\\p{N}])")
    private const val INLINE_MARKERS = "*_`~[{\\"

    fun render(context: Context, source: CharSequence): CharSequence {
        if (source.isEmpty()) return source
        val out = SpannableStringBuilder()
        val n = source.length
        var pos = 0
        var inFence = false
        var wroteLine = false

        while (pos <= n) {
            val nl = TextUtils.indexOf(source, '\n', pos)
            val end = if (nl < 0) n else nl
            val line = source.subSequence(pos, end)
            val str = line.toString()
            val trimmed = str.trimStart()

            if (trimmed.startsWith("```") && trimmed.indexOf("```", 3) < 0) {
                inFence = !inFence
            } else {
                if (wroteLine) out.append('\n')
                wroteLine = true
                when {
                    inFence -> {
                        val s = out.length
                        out.append(line)
                        if (out.length > s) out.setSpan(MarkdownCodeSpan(context), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    hasStyle(line) -> out.append(line)
                    else -> renderLine(context, str, out)
                }
            }
            if (nl < 0) break
            pos = nl + 1
        }
        return out
    }

    private fun hasStyle(line: CharSequence): Boolean {
        val sp = line as? Spanned ?: return false
        return sp.getSpans(0, line.length, CharacterStyle::class.java).isNotEmpty()
    }

    private fun renderLine(context: Context, str: String, out: SpannableStringBuilder) {
        var prefix = ""
        var body = str
        var headingScale = 0f

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

        val lineStart = out.length
        out.append(prefix)

        if (body.indexOfAny(INLINE_MARKERS.toCharArray()) < 0) {
            out.append(body)
        } else {
            // my_var_name không bị nhận nhầm là chữ nghiêng
            val safe = body.replace(INTRA_WORD_UNDERSCORE, "\\\\_")
            val (plain, spans) = MarkdownInlineHelper.parse(safe)
            val offset = out.length
            out.append(plain)
            applySpans(context, out, spans, offset)
        }

        if (headingScale != 0f && out.length > lineStart) {
            out.setSpan(StyleSpan(Typeface.BOLD), lineStart, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.setSpan(RelativeSizeSpan(headingScale), lineStart, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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
                MarkdownInlineHelper.MarkdownSpanType.CODE -> {
                    val radius = info.href.toFloatOrNull()
                    val span = if (radius != null) MarkdownCodeSpan(context, cornerRadiusDp = radius) else MarkdownCodeSpan(context)
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
