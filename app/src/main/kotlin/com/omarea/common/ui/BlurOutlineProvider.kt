package com.omarea.common.ui

import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider

class BlurOutlineProvider(private var radius: Float) : ViewOutlineProvider() {
    override fun getOutline(view: View, outline: Outline) {
        if (view.width > 0 && view.height > 0) {
            outline.setRoundRect(0, 0, view.width, view.height, radius)
        }
    }
}
