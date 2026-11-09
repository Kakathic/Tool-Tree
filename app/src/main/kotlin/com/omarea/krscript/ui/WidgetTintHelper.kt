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
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.widget.ImageView
import android.widget.Switch
import androidx.core.graphics.ColorUtils
import com.tool.tree.R

/**
 * Phối màu widget (mũi tên, switch) theo màu của icon: kiểu Duotone + Phát sáng nhẹ.
 * - Mũi tên: màu chính của icon, nằm trong chip tròn nền màu phụ nhạt, có quầng sáng mờ màu chính.
 * - Switch: khi bật, track chuyển màu từ màu chính sang màu phụ.
 */
object WidgetTintHelper {

    private class Palette(val primary: Int, val secondary: Int)

    fun applyTint(context: Context, widgetView: ImageView?, iconDrawable: Drawable?) {
        widgetView ?: return
        val palette = resolvePalette(context, iconDrawable)
        val dark = isDarkTheme(context)
        val density = context.resources.displayMetrics.density

        widgetView.imageTintList = ColorStateList.valueOf(palette.primary)

        // Chừa chỗ để mũi tên nằm gọn trong chip tròn
        val pad = (7 * density).toInt()
        widgetView.setPadding(pad, pad, pad, pad)

        widgetView.background = GlowChipDrawable(
            glowColor = palette.primary,
            chipBase = palette.secondary,
            chipAlpha = if (dark) 0.28f else 0.30f,
            glowAlpha = if (dark) 0.60f else 0.45f
        )
    }

    fun applyTint(context: Context, switchView: Switch?, iconDrawable: Drawable?) {
        switchView ?: return

        if (iconDrawable == null) {
            switchView.trackTintList = null
            return
        }

        val palette = resolvePalette(context, iconDrawable)
        val viewContext = switchView.context
        val density = viewContext.resources.displayMetrics.density
        val width = (66 * density).toInt()
        val height = (20 * density).toInt()
        val radius = 10 * density

        val onTrack = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(palette.primary, palette.secondary)
        ).apply {
            cornerRadius = radius
            setSize(width, height)
        }

        val offColor = resolveThemeColor(viewContext, R.attr.colorPirm, Color.argb(60, 128, 128, 128))
        val offTrack = GradientDrawable().apply {
            setColor(offColor)
            cornerRadius = radius
            setSize(width, height)
        }

        val track = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_checked), onTrack)
            addState(intArrayOf(), offTrack)
        }

        switchView.trackTintList = null
        switchView.trackDrawable = track
    }

    private fun resolvePalette(context: Context, iconDrawable: Drawable?): Palette {
        val value = if (isDarkTheme(context)) 0.95f else 0.88f
        val tops = extractTopColors(iconDrawable, topN = 6)

        val base = tops.firstOrNull()?.first ?: resolveAccentColor(context)
        val primary = normalizeColor(base, value)
        val baseHue = hueOf(base)

        // Màu phụ: màu nổi tiếp theo có tông khác rõ rệt; không có thì xoay tông màu chính 30 độ
        val second = tops.drop(1).map { it.first }.firstOrNull { hueDistance(hueOf(it), baseHue) >= 25f }
        val secondary = if (second != null) normalizeColor(second, value) else shiftHue(primary, 30f)

        return Palette(primary, secondary)
    }

    private fun isDarkTheme(context: Context): Boolean {
        val text = resolveThemeColor(context, android.R.attr.textColorPrimary, Color.BLACK)
        return ColorUtils.calculateLuminance(text) > 0.5
    }

    private fun resolveThemeColor(context: Context, attr: Int, fallback: Int): Int {
        val tv = TypedValue()
        if (!context.theme.resolveAttribute(attr, tv, true)) return fallback
        return when {
            tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT -> tv.data
            tv.resourceId != 0 -> try {
                context.getColor(tv.resourceId)
            } catch (e: Exception) {
                fallback
            }
            else -> fallback
        }
    }

    private fun resolveAccentColor(context: Context): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.colorAccent, typedValue, true)
        return typedValue.data
    }

    private fun hueOf(color: Int): Float {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv[0]
    }

    private fun hueDistance(a: Float, b: Float): Float {
        val d = Math.abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }

    private fun normalizeColor(color: Int, value: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = hsv[1].coerceIn(0.55f, 0.85f)
        hsv[2] = value
        return Color.HSVToColor(hsv)
    }

    private fun shiftHue(color: Int, degrees: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[0] = (hsv[0] + degrees) % 360f
        return Color.HSVToColor(hsv)
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

    /** Chip tròn nền màu phụ nhạt, viền ngoài có quầng sáng mờ màu chính (vẽ gọn trong khung view). */
    private class GlowChipDrawable(
        glowColor: Int,
        chipBase: Int,
        chipAlpha: Float,
        glowAlpha: Float
    ) : Drawable() {

        private val glowArgb = ColorUtils.setAlphaComponent(glowColor, (255 * glowAlpha).toInt())
        private val glowClear = ColorUtils.setAlphaComponent(glowColor, 0)
        private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(chipBase, (255 * chipAlpha).toInt())
        }
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var cx = 0f
        private var cy = 0f
        private var outer = 0f

        override fun onBoundsChange(bounds: Rect) {
            cx = bounds.exactCenterX()
            cy = bounds.exactCenterY()
            outer = Math.min(bounds.width(), bounds.height()) / 2f
            glowPaint.shader = if (outer > 0f) {
                RadialGradient(
                    cx, cy, outer,
                    intArrayOf(glowClear, glowClear, glowArgb, glowClear),
                    floatArrayOf(0f, 0.86f, 0.88f, 1f),
                    Shader.TileMode.CLAMP
                )
            } else null
        }

        override fun draw(canvas: Canvas) {
            if (outer <= 0f) return
            canvas.drawCircle(cx, cy, outer, glowPaint)
            canvas.drawCircle(cx, cy, outer * 0.86f, chipPaint)
        }

        override fun setAlpha(alpha: Int) {}

        override fun setColorFilter(colorFilter: ColorFilter?) {}

        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
