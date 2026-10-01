package com.tool.tree.ui

import android.view.View
import kotlin.math.abs

class FadeScalePageTransformer(
    private val minScale: Float = 0.80f,
    private val minAlpha: Float = 0.55f
) : SwipePager.PageTransformer {

    override fun transformPage(page: View, position: Float) {
        val clamped = position.coerceIn(-1f, 1f)
        val factor = 1f - abs(clamped)

        val scale = minScale + (1f - minScale) * factor
        page.scaleX = scale
        page.scaleY = scale
        page.alpha = (minAlpha + (1f - minAlpha) * factor).coerceIn(0f, 1f)
    }
}
