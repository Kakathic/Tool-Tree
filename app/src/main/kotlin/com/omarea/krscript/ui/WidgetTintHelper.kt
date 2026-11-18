package com.omarea.krscript.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.widget.ImageView
import android.widget.Switch
import androidx.core.graphics.ColorUtils

/**
 * Phối màu widget theo màu chính của icon (không có quầng sáng):
 * - Mũi tên / nút chạy: tô màu chính của icon.
 * - Switch: khi bật, track màu chính.
 */
object WidgetTintHelper {


    fun applyTint(context: Context, widgetView: ImageView?, iconDrawable: Drawable?) {
        widgetView ?: return
        val color = resolveTintColor(context, iconDrawable)

        widgetView.imageTintList = ColorStateList.valueOf(color)
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
        val dominant = dominantOf(iconDrawable)
            ?: return resolveAccentColor(context).let { base ->
                val hsv = FloatArray(3)
                Color.colorToHSV(base, hsv)
                hsv[1] = hsv[1].coerceIn(0.55f, 0.85f)
                hsv[2] = if (isDarkTheme(context)) 0.95f else 0.88f
                Color.HSVToColor(hsv)
            }

        val hsv = floatArrayOf(
            dominant[0],
            dominant[1].coerceIn(0.55f, 0.85f),
            if (isDarkTheme(context)) 0.95f else 0.88f
        )
        return Color.HSVToColor(hsv)
    }

    private val dominantCache = java.util.WeakHashMap<Drawable.ConstantState, FloatArray>()

    private fun dominantOf(drawable: Drawable?): FloatArray? {
        drawable ?: return null
        val key = drawable.constantState
        if (key != null) dominantCache[key]?.let { return it }
        val result = extractDominantHueSat(drawable) ?: return null
        if (key != null) dominantCache[key] = result
        return result
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

    private fun extractDominantHueSat(drawable: Drawable): FloatArray? {
        val sampleSize = 32
        val bitmap = try {
            Bitmap.createBitmap(sampleSize, sampleSize, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            return null
        }

        val canvas = Canvas(bitmap)
        val oldBounds = drawable.copyBounds()
        drawable.setBounds(0, 0, sampleSize, sampleSize)
        drawable.draw(canvas)
        drawable.bounds = oldBounds

        val buckets = 24
        val weight = FloatArray(buckets)
        val vx = FloatArray(buckets)
        val vy = FloatArray(buckets)
        val satSum = FloatArray(buckets)
        val hsv = FloatArray(3)

        for (x in 0 until sampleSize) {
            for (y in 0 until sampleSize) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) < 200) continue

                Color.colorToHSV(pixel, hsv)
                if (hsv[1] < 0.20f || hsv[2] < 0.15f) continue

                val w = hsv[1] * (0.5f + hsv[2])
                val b = ((hsv[0] / 360f) * buckets).toInt().coerceIn(0, buckets - 1)
                val rad = Math.toRadians(hsv[0].toDouble())
                weight[b] += w
                vx[b] += (Math.cos(rad) * w).toFloat()
                vy[b] += (Math.sin(rad) * w).toFloat()
                satSum[b] += hsv[1] * w
            }
        }

        bitmap.recycle()

        var best = -1
        var bestScore = 0f
        for (b in 0 until buckets) {
            val score = weight[b] + 0.5f * (weight[(b + buckets - 1) % buckets] + weight[(b + 1) % buckets])
            if (score > bestScore) {
                bestScore = score
                best = b
            }
        }
        if (best < 0) return null

        var sx = 0f
        var sy = 0f
        var sw = 0f
        var ss = 0f
        for (d in -1..1) {
            val b = (best + d + buckets) % buckets
            sx += vx[b]
            sy += vy[b]
            sw += weight[b]
            ss += satSum[b]
        }
        if (sw <= 0f) return null

        var hue = Math.toDegrees(Math.atan2(sy.toDouble(), sx.toDouble())).toFloat()
        if (hue < 0f) hue += 360f
        return floatArrayOf(hue, ss / sw)
    }
}
