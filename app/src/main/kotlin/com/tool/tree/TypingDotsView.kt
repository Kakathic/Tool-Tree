package com.tool.tree

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View

class TypingDotsView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val radius = 3.5f * density
    private val gap = 5f * density
    private val amplitude = 5f * density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var baseAlpha = 255

    fun setDotColor(color: Int) {
        paint.color = color
        baseAlpha = color ushr 24
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val contentWidth = (6 * radius + 2 * gap).toInt()
        val contentHeight = (2 * radius + 2 * amplitude).toInt()
        val width = paddingLeft + contentWidth + paddingRight
        val height = maxOf(suggestedMinimumHeight, paddingTop + contentHeight + paddingBottom)
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val elapsed = SystemClock.uptimeMillis() % PERIOD
        val cy = paddingTop + (height - paddingTop - paddingBottom) / 2f
        for (i in 0..2) {
            val local = (((elapsed - i * STAGGER) % PERIOD + PERIOD) % PERIOD) / PERIOD.toFloat()
            val k = when {
                local < 0.3f -> ease(local / 0.3f)
                local < 0.6f -> ease(1f - (local - 0.3f) / 0.3f)
                else -> 0f
            }
            paint.alpha = (baseAlpha * (0.5f + 0.5f * k)).toInt()
            canvas.drawCircle(paddingLeft + radius + i * (2 * radius + gap), cy - amplitude * k, radius, paint)
        }
        postInvalidateOnAnimation()
    }

    private fun ease(t: Float) = t * t * (3f - 2f * t)

    private companion object {
        const val PERIOD = 1200L
        const val STAGGER = 150L
    }
}
