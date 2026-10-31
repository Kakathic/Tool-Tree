package com.tool.tree

import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.text.style.ReplacementSpan

/**
 * Ba chấm chờ phản hồi kiểu "nhảy nhẹ": mỗi chấm nhún lên rồi hạ xuống, lệch pha 150ms,
 * sáng dần khi lên. Tự tính vị trí theo đồng hồ nên chỉ cần invalidate() TextView mỗi khung hình.
 * Giữ nguyên font metrics của dòng chữ nên bong bóng cao đúng bằng 1 dòng tin nhắn thường.
 */
class TypingDotsSpan(private val color: Int, density: Float) : ReplacementSpan() {
    private val radius = 3.5f * density
    private val gap = 5f * density
    private val amplitude = 5f * density
    private val startTime = SystemClock.uptimeMillis()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        if (fm != null) {
            val m = paint.fontMetricsInt
            fm.ascent = m.ascent
            fm.descent = m.descent
            fm.top = m.top
            fm.bottom = m.bottom
        }
        return (6 * radius + 2 * gap).toInt()
    }

    override fun draw(
        canvas: Canvas, text: CharSequence?, start: Int, end: Int,
        x: Float, top: Int, y: Int, bottom: Int, paint: Paint
    ) {
        val elapsed = (SystemClock.uptimeMillis() - startTime) % PERIOD
        val baseAlpha = color ushr 24
        val cy = (top + bottom) / 2f
        this.paint.color = color
        for (i in 0..2) {
            val local = (((elapsed - i * STAGGER) % PERIOD + PERIOD) % PERIOD) / PERIOD.toFloat()
            val k = when {
                local < 0.3f -> ease(local / 0.3f)
                local < 0.6f -> ease(1f - (local - 0.3f) / 0.3f)
                else -> 0f
            }
            this.paint.alpha = (baseAlpha * (0.5f + 0.5f * k)).toInt()
            canvas.drawCircle(x + radius + i * (2 * radius + gap), cy - amplitude * k, radius, this.paint)
        }
    }

    private fun ease(t: Float) = t * t * (3f - 2f * t)

    private companion object {
        const val PERIOD = 1200L
        const val STAGGER = 150L
    }
}
