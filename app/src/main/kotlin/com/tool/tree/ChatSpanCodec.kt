package com.tool.tree

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import org.json.JSONArray

/**
 * Mã hóa/giải mã các span định dạng (màu chữ, màu nền, đậm, nghiêng, gạch chân, gạch ngang)
 * để lưu cùng tin nhắn. Mỗi span là [loại, bắt đầu, kết thúc, giá trị].
 * Link không lưu vì ChatMarkdown.linkify dựng lại lúc hiển thị.
 */
object ChatSpanCodec {
    private const val FG = 0
    private const val BG = 1
    private const val BOLD = 2
    private const val ITALIC = 3
    private const val BOLD_ITALIC = 4
    private const val UNDERLINE = 5
    private const val STRIKE = 6
    private const val MAX_SPANS = 5000

    fun encode(text: CharSequence): JSONArray? {
        val sp = text as? Spanned ?: return null
        val out = JSONArray()
        for (span in sp.getSpans(0, sp.length, Any::class.java)) {
            if (out.length() >= MAX_SPANS) break
            val start = sp.getSpanStart(span)
            val end = sp.getSpanEnd(span)
            if (start < 0 || end <= start) continue
            val entry = when (span) {
                is ForegroundColorSpan -> JSONArray().put(FG).put(start).put(end).put(span.foregroundColor)
                is BackgroundColorSpan -> JSONArray().put(BG).put(start).put(end).put(span.backgroundColor)
                is StyleSpan -> when (span.style) {
                    Typeface.BOLD -> JSONArray().put(BOLD).put(start).put(end).put(0)
                    Typeface.ITALIC -> JSONArray().put(ITALIC).put(start).put(end).put(0)
                    Typeface.BOLD_ITALIC -> JSONArray().put(BOLD_ITALIC).put(start).put(end).put(0)
                    else -> null
                }
                is UnderlineSpan -> JSONArray().put(UNDERLINE).put(start).put(end).put(0)
                is StrikethroughSpan -> JSONArray().put(STRIKE).put(start).put(end).put(0)
                else -> null
            }
            if (entry != null) out.put(entry)
        }
        return if (out.length() == 0) null else out
    }

    fun apply(target: SpannableStringBuilder, spans: JSONArray?) {
        if (spans == null) return
        val len = target.length
        for (i in 0 until spans.length()) {
            val e = spans.optJSONArray(i) ?: continue
            if (e.length() < 4) continue
            val start = e.optInt(1).coerceIn(0, len)
            val end = e.optInt(2).coerceIn(0, len)
            if (end <= start) continue
            val span: Any = when (e.optInt(0, -1)) {
                FG -> ForegroundColorSpan(e.optInt(3))
                BG -> BackgroundColorSpan(e.optInt(3))
                BOLD -> StyleSpan(Typeface.BOLD)
                ITALIC -> StyleSpan(Typeface.ITALIC)
                BOLD_ITALIC -> StyleSpan(Typeface.BOLD_ITALIC)
                UNDERLINE -> UnderlineSpan()
                STRIKE -> StrikethroughSpan()
                else -> continue
            }
            target.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
