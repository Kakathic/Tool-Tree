package com.omarea.krscript.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.widget.ImageView
import androidx.core.graphics.ColorUtils

object WidgetTintHelper {

    fun applyTint(context: Context, widgetView: ImageView?, iconDrawable: Drawable?) {
        widgetView ?: return

        val defaultAccent = resolveAccentColor(context)

        // 1. Không có icon -> Giữ nguyên logic khôi phục màu mặc định
        if (iconDrawable == null) {
            widgetView.imageTintList = ColorStateList.valueOf(defaultAccent)
            return
        }

        // 2. Trích xuất Top 3 màu chủ đạo (Chỉ lọc bỏ alpha & màu xám ở đoạn trích xuất từ ảnh)
        val topColors = extractTopColors(iconDrawable, topN = 3)

        // 3. Trộn các màu theo đúng tỷ lệ trọng số xuất hiện
        val blendedColor = if (topColors.isNotEmpty()) {
            blendMultipleColors(topColors)
        } else {
            defaultAccent
        }

        // 4. Nâng nhẹ độ sáng nếu màu trộn bị quá tối
        val finalColor = brightenColor(blendedColor)

        widgetView.imageTintList = ColorStateList.valueOf(finalColor)
    }

    /**
     * Trộn danh sách nhiều màu lại với nhau dựa trên tỷ lệ điểm trọng số
     */
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

    // Giữ nguyên hàm khôi phục/lấy màu accent
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

    /**
     * Chỉ lọc bỏ Alpha và Màu Xám tại bước phân tích icon
     */
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

                // 1. LOẠI BỎ ALPHA: Chỉ duyệt pixel đục (Alpha >= 200)
                if (Color.alpha(pixel) < 200) continue

                Color.colorToHSV(pixel, hsv)
                val sat = hsv[1]    // Độ bão hòa màu
                val valVal = hsv[2] // Độ sáng

                // 2. LOẠI BỎ MÀU XÁM / ĐEN QUÁ TỐI:
                // sat < 0.20f: Nhóm màu tone xám/trắng không có màu chủ đạo
                // valVal < 0.15f: Nhóm màu quá tối
                if (sat < 0.20f || valVal < 0.15f) continue

                // Nhóm các màu gần giống nhau
                val r = (Color.red(pixel) shr 4) shl 4
                val g = (Color.green(pixel) shr 4) shl 4
                val b = (Color.blue(pixel) shr 4) shl 4
                
                // Color.rgb tạo màu có Alpha = 255 mặc định
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
