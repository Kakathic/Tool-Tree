package com.omarea.krscript.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.style.ReplacementSpan

// Span cho `code` inline trong Markdown (thay BackgroundColorSpan+TypefaceSpan cũ) - tự vẽ nền bo
// góc + padding ngang đều quanh chữ, thay vì tô màu sát khít từng ký tự.
class MarkdownCodeSpan(
    context: Context,
    private val backgroundColor: Int = 0x44808080,
    cornerRadiusDp: Float = 4f,
    paddingHorizontalDp: Float = 4f
) : ReplacementSpan() {

    private val cornerRadiusPx = cornerRadiusDp * context.resources.displayMetrics.density
    private val paddingPx = paddingHorizontalDp * context.resources.displayMetrics.density

    private fun measureWidth(paint: Paint, text: CharSequence, start: Int, end: Int): Float {
        val oldTypeface = paint.typeface
        paint.typeface = Typeface.MONOSPACE
        val width = paint.measureText(text, start, end)
        paint.typeface = oldTypeface
        return width
    }

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        return (measureWidth(paint, text, start, end) + paddingPx * 2).toInt()
    }

    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val textWidth = measureWidth(paint, text, start, end)
        val oldColor = paint.color
        val oldStyle = paint.style
        val oldTypeface = paint.typeface

        paint.style = Paint.Style.FILL
        paint.color = backgroundColor
        canvas.drawRoundRect(
            RectF(x, top.toFloat(), x + textWidth + paddingPx * 2, bottom.toFloat()),
            cornerRadiusPx, cornerRadiusPx, paint
        )

        paint.color = oldColor
        paint.typeface = Typeface.MONOSPACE
        canvas.drawText(text, start, end, x + paddingPx, y.toFloat(), paint)

        paint.typeface = oldTypeface
        paint.style = oldStyle
    }
}
