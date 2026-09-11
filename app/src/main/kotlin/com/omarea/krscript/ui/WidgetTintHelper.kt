package com.omarea.krscript.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.widget.ImageView
import android.widget.Switch
import androidx.core.graphics.ColorUtils
import com.tool.tree.R

object WidgetTintHelper {

    fun applyTint(context: Context, widgetView: ImageView?, iconDrawable: Drawable?) {
        widgetView ?: return
        widgetView.imageTintList = ColorStateList.valueOf(resolveTintColor(context, iconDrawable))
    }

    // Tô màu track của Switch theo màu trích xuất từ icon: bật dùng màu trích xuất, tắt giữ màu mặc định (colorPirm)
    fun applyTint(context: Context, switchView: Switch?, iconDrawable: Drawable?) {
        switchView ?: return

        if (iconDrawable == null) {
            switchView.trackTintList = null
            return
        }

        val onColor = resolveTintColor(context, iconDrawable)
        val offColor = resolveOffTrackColor(context)

        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        switchView.trackTintList = ColorStateList(states, intArrayOf(onColor, offColor))
        switchView.trackTintMode = PorterDuff.Mode.SRC_IN
    }

    private fun resolveTintColor(context: Context, iconDrawable: Drawable?): Int {
        val defaultAccent = resolveAccentColor(context)
        iconDrawable ?: return defaultAccent

        val topColors = extractTopColors(iconDrawable, topN = 3)
        val blendedColor = if (topColors.isNotEmpty()) {
            blendMultipleColors(topColors)
        } else {
            defaultAccent
        }

        return brightenColor(blendedColor)
    }

    private fun resolveOffTrackColor(context: Context): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(R.attr.colorPirm, typedValue, true)
        return typedValue.data
    }

    private fun blendMultipleColors(colorsWithScores: List<Pair<Int, Float>>): Int {
        if (colorsWithScores.isEmpty()) return Color.BLACK
        if (colorsWithScores.size == 1) return colorsWithScores[0].first

        var currentColor = colorsWithScores[0].first
        var currentScore = colorsWithScores[0].second

        for (i in 1 until colorsWithScores.size) {
            val nextColor = colorsWithScores[i].first
            val nextScore = colorsWithScores[i].second
            val totalScore = currentScore + nextScore

            val ratio = if (totalScore > 0f) nextScore / totalScore else 0.5f

            currentColor = ColorUtils.blendARGB(currentColor, nextColor, ratio)
            currentScore = totalScore
        }

        return currentColor
    }

    private fun resolveAccentColor(context: Context): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.colorAccent, typedValue, true)
        return typedValue.data
    }

    private fun brightenColor(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)

        if (hsv[2] < 0.9f) {
            hsv[2] = 0.9f
        }

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
}
