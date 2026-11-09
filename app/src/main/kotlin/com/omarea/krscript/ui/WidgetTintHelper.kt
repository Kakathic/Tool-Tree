package com.omarea.krscript.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.widget.ImageView
import android.widget.Switch
import androidx.core.graphics.ColorUtils

/**
 * Phối màu widget theo màu chính của icon, kiểu "Phát sáng nhẹ":
 * - Mũi tên: màu chính của icon (đã chuẩn hóa), có quầng sáng mờ cùng màu phía sau.
 * - Switch: track màu chính khi bật.
 */
object WidgetTintHelper {

    fun applyTint(context: Context, widgetView: ImageView?, iconDrawable: Drawable?) {
        widgetView ?: return
        val color = resolveTintColor(context, iconDrawable)
        val dark = isDarkTheme(context)

        widgetView.imageTintList = ColorStateList.valueOf(color)
        widgetView.background = GlowDrawable(color, if (dark) 0.40f else 0.30f)
    }

    fun applyTint(context: Context, switchView: Switch?, iconDrawable: Drawable?) {
        switchView ?: return

        if (iconDrawable == null) {
            switchView.trackTintList = null
            return
        }

        val onColor = resolveTintColor(context, iconDrawable)

        fun updateTrackTint() {
            switchView.trackTintList = if (switchView.isChecked) ColorStateList.valueOf(onColor) else null
        }

        updateTrackTint()
        switchView.setOnCheckedChangeListener { _, _ -> updateTrackTint() }
    }

    /** Lấy màu nổi nhất của icon, giữ nguyên tông, chuẩn hóa độ bão hòa và độ sáng. */
    private fun resolveTintColor(context: Context, iconDrawable: Drawable?): Int {
        val base = extractTopColors(iconDrawable, topN = 1).firstOrNull()?.first
            ?: resolveAccentColor(context)

        val hsv = FloatArray(3)
        Color.colorToHSV(base, hsv)
        hsv[1] = hsv[1].coerceIn(0.55f, 0.85f)
        hsv[2] = if (isDarkTheme(context)) 0.95f else 0.88f
        return Color.HSVToColor(hsv)
    }

    private fun isDarkTheme(context: Context): Boolean {
        val tv = TypedValue()
        if (!context.theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true)) return false
        val text = when {
            tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT -> tv.data
            tv.resourceId != 0 -> try {
                context.getColor(tv.resourceId)
            } catch (e: Exception) {
                return false
            }
            else -> return false
        }
        return ColorUtils.calculateLuminance(text) > 0.5
    }

    private fun resolveAccentColor(context: Context): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.colorAccent, typedValue, true)
        return typedValue.data
    }

    private fun extractTopColors(drawable: Drawable?, topN: Int = 3): List<Pair<Int, Float>> {
        drawable ?: return emptyList()

        val sampleSize = 24
        val bitmap = try {
            Bitmap.createBitmap(sampleSize, sampleSize, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            return emptyList()
        }

        val canvas = Canvas(bitmap)
        val oldBounds = drawable.copyBounds()
        drawable.setBounds(0, 0, sampleSize, sampleSize)
        drawable.draw(canvas)
        drawable.bounds = oldBounds

        val colorScores = HashMap<Int, Float>()
        val hsv = FloatArray(3)

        for (x in 0 until sampleSize) {
            for (y in 0 until sampleSize) {
                val pixel = bitmap.getPixel(x, y)

                if (Color.alpha(pixel) < 200) continue

                Color.colorToHSV(pixel, hsv)
                val sat = hsv[1]
                val valVal = hsv[2]

                if (sat < 0.20f || valVal < 0.15f) continue

                val r = (Color.red(pixel) shr 4) shl 4
                val g = (Color.green(pixel) shr 4) shl 4
                val b = (Color.blue(pixel) shr 4) shl 4

                val quantizedColor = Color.rgb(r, g, b)

                val weight = 1.0f + (sat * 2.0f) + (if (valVal > 0.15f && valVal < 0.95f) 0.5f else 0.0f)

                val currentScore = colorScores[quantizedColor] ?: 0f
                colorScores[quantizedColor] = currentScore + weight
            }
        }

        bitmap.recycle()

        return colorScores.entries
            .sortedByDescending { it.value }
            .take(topN)
            .map { Pair(it.key, it.value) }
    }

    /** Quầng sáng mờ tròn, đậm ở giữa và nhạt dần ra mép, vẽ gọn trong khung view. */
    private class GlowDrawable(color: Int, alpha: Float) : Drawable() {

        private val glowArgb = ColorUtils.setAlphaComponent(color, (255 * alpha).toInt())
        private val glowClear = ColorUtils.setAlphaComponent(color, 0)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var cx = 0f
        private var cy = 0f
        private var radius = 0f

        override fun onBoundsChange(bounds: Rect) {
            cx = bounds.exactCenterX()
            cy = bounds.exactCenterY()
            radius = Math.min(bounds.width(), bounds.height()) / 2f
            paint.shader = if (radius > 0f) {
                RadialGradient(
                    cx, cy, radius,
                    intArrayOf(glowArgb, glowClear),
                    floatArrayOf(0.3f, 1f),
                    Shader.TileMode.CLAMP
                )
            } else null
        }

        override fun draw(canvas: Canvas) {
            if (radius <= 0f) return
            canvas.drawCircle(cx, cy, radius, paint)
        }

        override fun setAlpha(alpha: Int) {}

        override fun setColorFilter(colorFilter: ColorFilter?) {}

        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
