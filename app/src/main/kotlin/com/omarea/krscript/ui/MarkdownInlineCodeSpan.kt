package com.omarea.krscript.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Spannable
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.ReplacementSpan

/**
 * Code inline `như vậy`. Giống MarkdownCodeSpan nhưng nền được giới hạn trong khung dòng và
 * chừa lineGapDp ở mép trên/dưới, nên hai dòng liền nhau không bị nền dính vào nhau.
 * getSize giữ nguyên như MarkdownCodeSpan nên cách tính độ rộng/chiều cao dòng không đổi.
 *
 * ReplacementSpan là một khối không thể ngắt dòng, nên code inline dài sẽ không xuống dòng.
 * Vì vậy dùng [apply] để cắt đoạn code thành nhiều span nhỏ (theo từ, từ quá dài thì cắt theo cụm ký tự);
 * các span liền nhau được vẽ nối liền thành một khối, chỉ bo góc/đệm ở hai đầu.
 */
class MarkdownInlineCodeSpan(
    context: Context,
    private val backgroundColor: Int = 0x44808080,
    cornerRadiusDp: Float = 6f,
    paddingHorizontalDp: Float = 4f,
    private val paddingVerticalDp: Float = 2f,
    lineGapDp: Float = 1.5f,
    private val isFirst: Boolean = true,
    private val isLast: Boolean = true
) : ReplacementSpan() {

    private val density = context.resources.displayMetrics.density
    private val cornerRadiusPx = cornerRadiusDp * density
    private val paddingHorizontalPx = paddingHorizontalDp * density
    private val paddingVerticalPx = paddingVerticalDp * density
    private val lineGapPx = lineGapDp * density
    private val rect = RectF()
    private val path = Path()
    private val radii = FloatArray(8)
    private val padLeftPx get() = if (isFirst) paddingHorizontalPx else 0f
    private val padRightPx get() = if (isLast) paddingHorizontalPx else 0f

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        if (fm != null) {
            val fontMetrics = paint.fontMetricsInt
            fm.ascent = fontMetrics.ascent - paddingVerticalPx.toInt()
            fm.descent = fontMetrics.descent + paddingVerticalPx.toInt()
            fm.top = fontMetrics.top - paddingVerticalPx.toInt()
            fm.bottom = fontMetrics.bottom + paddingVerticalPx.toInt()
        }
        return (paint.measureText(text, start, end) + padLeftPx + padRightPx).toInt()
    }

    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val textWidth = paint.measureText(text, start, end)
        val fontMetrics = paint.fontMetricsInt

        var rectTop = y.toFloat() + fontMetrics.ascent - paddingVerticalPx
        var rectBottom = y.toFloat() + fontMetrics.descent + paddingVerticalPx

        // Nền không được chạm mép khung dòng, chừa khe để dòng trên/dưới không dính nhau
        val limitTop = top + lineGapPx
        val limitBottom = bottom - lineGapPx
        if (limitBottom > limitTop) {
            if (rectTop < limitTop) rectTop = limitTop
            if (rectBottom > limitBottom) rectBottom = limitBottom
        }

        val oldColor = paint.color
        val oldStyle = paint.style

        paint.style = Paint.Style.FILL
        paint.color = backgroundColor

        rect.set(x, rectTop, x + textWidth + padLeftPx + padRightPx, rectBottom)
        if (isFirst && isLast) {
            canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint)
        } else {
            val l = if (isFirst) cornerRadiusPx else 0f
            val r = if (isLast) cornerRadiusPx else 0f
            radii[0] = l; radii[1] = l; radii[2] = r; radii[3] = r
            radii[4] = r; radii[5] = r; radii[6] = l; radii[7] = l
            path.reset()
            path.addRoundRect(rect, radii, Path.Direction.CW)
            canvas.drawPath(path, paint)
        }

        paint.style = oldStyle
        paint.color = oldColor

        drawInnerText(canvas, text, start, end, x + padLeftPx, y.toFloat(), paint)
    }

    private fun drawInnerText(canvas: Canvas, text: CharSequence, start: Int, end: Int, startX: Float, baselineY: Float, basePaint: Paint) {
        val spanned = text as? Spanned
        if (spanned == null) {
            canvas.drawText(text, start, end, startX, baselineY, basePaint)
            return
        }

        val boundaries = sortedSetOf(start, end)
        for (span in spanned.getSpans(start, end, CharacterStyle::class.java)) {
            boundaries.add(spanned.getSpanStart(span).coerceIn(start, end))
            boundaries.add(spanned.getSpanEnd(span).coerceIn(start, end))
        }
        val cuts = boundaries.toList()

        var drawX = startX
        val workPaint = TextPaint(basePaint)
        for (i in 0 until cuts.size - 1) {
            val segStart = cuts[i]
            val segEnd = cuts[i + 1]
            if (segEnd <= segStart) continue

            workPaint.set(basePaint)
            for (span in spanned.getSpans(segStart, segEnd, CharacterStyle::class.java)) {
                span.updateDrawState(workPaint)
            }
            canvas.drawText(text, segStart, segEnd, drawX, baselineY, workPaint)
            drawX += workPaint.measureText(text, segStart, segEnd)
        }
    }

    companion object {
        /** Số ký tự tối đa của một span khi gặp từ không có khoảng trắng (đường dẫn, URL dài...) */
        private const val MAX_CHUNK = 8

        /**
         * Gắn code inline cho [start, end) của [target], tách thành nhiều span để cho phép ngắt dòng.
         */
        fun apply(context: Context, target: Spannable, start: Int, end: Int, cornerRadiusDp: Float? = null) {
            if (end <= start) return
            val cuts = ArrayList<Int>()
            cuts.add(start)
            var i = start
            var chunkLen = 0
            while (i < end) {
                val c = target[i]
                chunkLen++
                val next = i + 1
                if (next < end) {
                    val boundary = if (c.isWhitespace()) !target[next].isWhitespace() else chunkLen >= MAX_CHUNK
                    if (boundary) {
                        cuts.add(next)
                        chunkLen = 0
                    }
                }
                i = next
            }
            cuts.add(end)

            for (k in 0 until cuts.size - 1) {
                val first = k == 0
                val last = k == cuts.size - 2
                val span = if (cornerRadiusDp != null) {
                    MarkdownInlineCodeSpan(context, cornerRadiusDp = cornerRadiusDp, isFirst = first, isLast = last)
                } else {
                    MarkdownInlineCodeSpan(context, isFirst = first, isLast = last)
                }
                target.setSpan(span, cuts[k], cuts[k + 1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }
}
