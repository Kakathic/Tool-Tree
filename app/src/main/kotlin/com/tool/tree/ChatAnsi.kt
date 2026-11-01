package com.tool.tree

import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan

/**
 * AnsiColorParser gắn ForegroundColorSpan(LTGRAY) cho mọi chữ không có mã màu, làm chữ trong
 * bong bóng chat xám mờ cố định, không theo màu chữ của theme, và làm ChatMarkdown bỏ qua dòng đó.
 * Hàm này bỏ riêng span màu mặc định đó; màu ANSI thật (đỏ, xanh, ...) được giữ nguyên.
 */
object ChatAnsi {
    fun stripDefaultColor(source: CharSequence): CharSequence {
        if (source !is Spanned) return source
        val spans = source.getSpans(0, source.length, ForegroundColorSpan::class.java)
        if (spans.none { it.foregroundColor == Color.LTGRAY }) return source
        val out = SpannableStringBuilder(source)
        for (span in out.getSpans(0, out.length, ForegroundColorSpan::class.java)) {
            if (span.foregroundColor == Color.LTGRAY) out.removeSpan(span)
        }
        return out
    }
}
