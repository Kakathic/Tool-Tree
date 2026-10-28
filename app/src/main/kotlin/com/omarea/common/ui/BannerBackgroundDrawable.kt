package com.omarea.common.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

class BannerBackgroundDrawable(
    private val startColor: Int,
    private val endColor: Int,
    private val radiusPx: Float,
    private val density: Float
) : Drawable() {

    private val rect = RectF()
    private val clipPath = Path()
    private val matrix = Matrix()

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = Color.argb(26, 255, 255, 255)
    }

    private val glowShader = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(Color.argb(66, 255, 255, 255), Color.argb(0, 255, 255, 255)),
        floatArrayOf(0f, 0.55f),
        Shader.TileMode.CLAMP
    )

    override fun onBoundsChange(bounds: Rect) {
        rect.set(bounds)
        clipPath.reset()
        clipPath.addRoundRect(rect, radiusPx, radiusPx, Path.Direction.CW)
        basePaint.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            startColor, endColor, Shader.TileMode.CLAMP
        )
        matrix.reset()
        matrix.setScale(rect.width() * 1.2f, rect.height() * 1.4f)
        matrix.postTranslate(rect.left, rect.top)
        glowShader.setLocalMatrix(matrix)
        glowPaint.shader = glowShader
    }

    override fun draw(canvas: Canvas) {
        if (rect.isEmpty) return
        val save = canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawRect(rect, basePaint)
        canvas.drawRect(rect, glowPaint)

        bubblePaint.color = Color.argb(28, 255, 255, 255)
        canvas.drawCircle(rect.right - 30f * density, rect.top + 14f * density, 60f * density, bubblePaint)
        bubblePaint.color = Color.argb(20, 255, 255, 255)
        canvas.drawCircle(rect.right - 92f * density, rect.bottom - 4f * density, 24f * density, bubblePaint)
        bubblePaint.color = Color.argb(14, 255, 255, 255)
        canvas.drawCircle(rect.left + 36f * density, rect.bottom + 10f * density, 34f * density, bubblePaint)
        canvas.restoreToCount(save)

        val half = strokePaint.strokeWidth / 2f
        canvas.drawRoundRect(
            rect.left + half, rect.top + half, rect.right - half, rect.bottom - half,
            radiusPx - half, radiusPx - half, strokePaint
        )
    }

    override fun getOutline(outline: Outline) {
        outline.setRoundRect(bounds, radiusPx)
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
