package com.omarea.krscript.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.ReplacementSpan

/**
 * Code inline `như vậy`. Giống MarkdownCodeSpan nhưng nền được giới hạn trong khung dòng và
 * chừa lineGapDp ở mép trên/dưới, nên hai dòng liền nhau không bị nền dính vào nhau.
 * getSize giữ nguyên như MarkdownCodeSpan nên cách tính độ rộng/chiều cao dòng không đổi.
 */
class MarkdownInlineCodeSpan(
    context: Context,
    private val backgroundColor: Int = 0x44808080,
    cornerRadiusDp: Float = 6f,
    paddingHorizontalDp: Float = 4f,
    private val paddingVerticalDp: Float = 2f,
    lineGapDp: Float = 1.5f
) : ReplacementSpan() {

    private val density = context.resources.displayMetrics.density
    private val cornerRadiusPx = cornerRadiusDp * density
    private val paddingHorizontalPx = paddingHorizontalDp * density
    private val paddingVerticalPx = paddingVerticalDp * density
    private val lineGapPx = lineGapDp * density
    private val rect = RectF()

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        if (fm != null) {
            val fontMetrics = paint.fontMetricsInt
            fm.ascent = fontMetrics.ascent - paddingVerticalPx.toInt()
            fm.descent = fontMetrics.descent + paddingVerticalPx.toInt()
            fm.top = fontMetrics.top - paddingVerticalPx.toInt()
            fm.bottom = fontMetrics.bottom + paddingVerticalPx.toInt()
        }
        return (paint.measureText(text, start, end) + paddingHorizontalPx * 2).toInt()
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

        rect.set(x, rectTop, x + textWidth + paddingHorizontalPx * 2, rectBottom)
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint)

        paint.style = oldStyle
        paint.color = oldColor

        drawInnerText(canvas, text, start, end, x + paddingHorizontalPx, y.toFloat(), paint)
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
}
