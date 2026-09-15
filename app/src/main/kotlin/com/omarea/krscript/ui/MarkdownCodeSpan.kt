package com.omarea.krscript.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.style.ReplacementSpan

// Span cho `code` inline trong Markdown - tự vẽ nền bo góc ôm sát chữ
class MarkdownCodeSpan(
    context: Context,
    private val backgroundColor: Int = 0x44808080,
    cornerRadiusDp: Float = 6f,
    paddingHorizontalDp: Float = 4f,
    private val paddingVerticalDp: Float = 2f // Padding dọc để ôm sát chữ vừa vặn
) : ReplacementSpan() {

    private val density = context.resources.displayMetrics.density
    private val cornerRadiusPx = cornerRadiusDp * density
    private val paddingHorizontalPx = paddingHorizontalDp * density
    private val paddingVerticalPx = paddingVerticalDp * density

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        // Cập nhật FontMetrics để TextView tự động chừa khoảng trống dọc, tránh bị cắt (clip)
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

        // Tính toán tọa độ vẽ nền ôm sát chữ dựa trên baseline (y)
        val rectTop = y.toFloat() + fontMetrics.ascent - paddingVerticalPx
        val rectBottom = y.toFloat() + fontMetrics.descent + paddingVerticalPx

        val oldColor = paint.color
        val oldStyle = paint.style

        paint.style = Paint.Style.FILL
        paint.color = backgroundColor
        
        // Vẽ hình chữ nhật bo góc ôm gọn lấy chữ
        canvas.drawRoundRect(
            RectF(x, rectTop, x + textWidth + paddingHorizontalPx * 2, rectBottom),
            cornerRadiusPx, cornerRadiusPx, paint
        )

        paint.color = oldColor
        // Vẽ text đè lên trên nền
        canvas.drawText(text, start, end, x + paddingHorizontalPx, y.toFloat(), paint)

        paint.style = oldStyle
    }
}
