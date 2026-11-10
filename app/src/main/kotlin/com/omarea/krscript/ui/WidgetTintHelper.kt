package com.omarea.krscript.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import androidx.core.graphics.ColorUtils

/**
 * Phối màu widget theo màu chính của icon, kiểu "Phát sáng nhẹ" (giống demo HTML kiểu 6):
 * - Mũi tên: màu chính của icon, quầng sáng ôm theo hình mũi tên (drop-shadow 0 0 5px, độ đậm 75%).
 * - Switch: khi bật, track màu chính, quầng sáng quanh track (box-shadow 0 0 10px, độ đậm 55%).
 */
object WidgetTintHelper {

    private const val ARROW_GLOW_ALPHA = 0.75f
    private const val ARROW_GLOW_SIGMA_DP = 2.5f   // CSS blur 5px = độ lệch chuẩn 2.5
    private const val TRACK_GLOW_ALPHA = 0.55f
    private const val TRACK_GLOW_SIGMA_DP = 5f     // CSS blur 10px = độ lệch chuẩn 5

    fun applyTint(context: Context, widgetView: ImageView?, iconDrawable: Drawable?) {
        widgetView ?: return
        val color = resolveTintColor(context, iconDrawable)

        widgetView.imageTintList = ColorStateList.valueOf(color)
        widgetView.background = GlyphGlowDrawable(
            widgetView, color, ARROW_GLOW_ALPHA,
            ARROW_GLOW_SIGMA_DP * context.resources.displayMetrics.density
        )
        allowGlowOverflow(widgetView)
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

        switchView.background = TrackGlowDrawable(
            switchView, onColor, TRACK_GLOW_ALPHA,
            TRACK_GLOW_SIGMA_DP * context.resources.displayMetrics.density,
            10 * context.resources.displayMetrics.density
        )
        allowGlowOverflow(switchView)
    }

    /** Cho phép quầng sáng tràn ra ngoài khung view (tối đa 4 cấp cha bên trong item). */
    private fun allowGlowOverflow(view: View) {
        var p = view.parent
        repeat(4) {
            val group = p as? ViewGroup ?: return
            group.clipChildren = false
            p = group.parent
        }
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

    /** Đổi độ lệch chuẩn (px) sang bán kính của BlurMaskFilter. */
    private fun blurRadiusFor(sigmaPx: Float): Float = Math.max(0.5f, (sigmaPx - 0.5f) / 0.57735f)

    private abstract class BaseGlowDrawable(color: Int, alpha: Float, sigmaPx: Float) : Drawable() {
        protected val colorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = ColorUtils.setAlphaComponent(color, (255 * alpha).toInt())
        }
        protected val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = BlurMaskFilter(blurRadiusFor(sigmaPx), BlurMaskFilter.Blur.NORMAL)
        }

        override fun setAlpha(alpha: Int) {}

        override fun setColorFilter(colorFilter: ColorFilter?) {}

        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    /** Quầng sáng ôm theo đúng hình của icon trong ImageView (giống drop-shadow của CSS), nằm dưới icon. */
    private class GlyphGlowDrawable(
        private val view: ImageView,
        color: Int,
        alpha: Float,
        sigmaPx: Float
    ) : BaseGlowDrawable(color, alpha, sigmaPx) {

        private var cache: Bitmap? = null
        private var cacheSrc: Drawable? = null
        private var cacheW = 0
        private var cacheH = 0
        private var offX = 0f
        private var offY = 0f

        private fun rebuild(src: Drawable, w: Int, h: Int) {
            cache = null
            cacheSrc = src
            cacheW = w
            cacheH = h

            val cl = view.paddingLeft
            val ct = view.paddingTop
            val cw = w - cl - view.paddingRight
            val ch = h - ct - view.paddingBottom
            if (cw <= 0 || ch <= 0) return

            val bmp = try {
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            } catch (e: Exception) {
                return
            }

            // Vẽ icon vào đúng chỗ ImageView (fit center) rồi lấy độ trong suốt, làm mờ
            val iw = if (src.intrinsicWidth > 0) src.intrinsicWidth else cw
            val ih = if (src.intrinsicHeight > 0) src.intrinsicHeight else ch
            val scale = Math.min(cw.toFloat() / iw, ch.toFloat() / ih)
            val dw = (iw * scale).toInt()
            val dh = (ih * scale).toInt()
            val left = cl + (cw - dw) / 2
            val top = ct + (ch - dh) / 2

            val oldBounds = src.copyBounds()
            src.setBounds(left, top, left + dw, top + dh)
            src.draw(Canvas(bmp))
            src.bounds = oldBounds

            val off = IntArray(2)
            cache = bmp.extractAlpha(blurPaint, off)
            offX = off[0].toFloat()
            offY = off[1].toFloat()
            bmp.recycle()
        }

        override fun draw(canvas: Canvas) {
            val src = view.drawable ?: return
            val b = bounds
            if (b.width() <= 0 || b.height() <= 0) return
            if (cacheSrc !== src || cacheW != b.width() || cacheH != b.height()) {
                rebuild(src, b.width(), b.height())
            }
            cache?.let { canvas.drawBitmap(it, b.left + offX, b.top + offY, colorPaint) }
        }
    }

    /** Quầng sáng quanh track của Switch khi bật (giống box-shadow của CSS), nằm dưới track. */
    private class TrackGlowDrawable(
        private val view: Switch,
        color: Int,
        alpha: Float,
        sigmaPx: Float,
        private val cornerPx: Float
    ) : BaseGlowDrawable(color, alpha, sigmaPx) {

        private var cache: Bitmap? = null
        private var cacheW = 0
        private var cacheH = 0
        private var offX = 0f
        private var offY = 0f

        private fun rebuild(w: Int, h: Int) {
            cache = null
            cacheW = w
            cacheH = h
            if (w <= 0 || h <= 0) return

            val bmp = try {
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            } catch (e: Exception) {
                return
            }
            val radius = Math.min(cornerPx, Math.min(w, h) / 2f)
            Canvas(bmp).drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, Paint(Paint.ANTI_ALIAS_FLAG))

            val off = IntArray(2)
            cache = bmp.extractAlpha(blurPaint, off)
            offX = off[0].toFloat()
            offY = off[1].toFloat()
            bmp.recycle()
        }

        override fun draw(canvas: Canvas) {
            if (!view.isChecked) return
            val track = view.trackDrawable?.bounds ?: return
            if (track.width() <= 0 || track.height() <= 0) return
            if (cache == null || cacheW != track.width() || cacheH != track.height()) {
                rebuild(track.width(), track.height())
            }
            cache?.let { canvas.drawBitmap(it, track.left + offX, track.top + offY, colorPaint) }
        }
    }
}
