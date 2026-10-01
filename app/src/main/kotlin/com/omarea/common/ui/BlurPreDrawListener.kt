package com.omarea.common.ui

import android.graphics.Bitmap
import android.view.View
import android.view.ViewTreeObserver

class BlurPreDrawListener(private val engine: BlurEngine, private val targetView: View) :
    ViewTreeObserver.OnPreDrawListener {

    private var lastBitmap: Bitmap? = null
    private var lastX = Int.MIN_VALUE
    private var lastY = Int.MIN_VALUE
    private val location = IntArray(2)

    override fun onPreDraw(): Boolean {
        if (!targetView.isShown || BlurEngine.isPaused || BlurEngine.blurBitmap == null) {
            return true
        }

        val currentBitmap = BlurEngine.blurBitmap
        val bitmapChanged = currentBitmap !== lastBitmap

        targetView.getLocationOnScreen(location)
        val locationChanged = location[0] != lastX || location[1] != lastY

        if (!bitmapChanged && !locationChanged) {
            return true
        }

        lastBitmap = currentBitmap
        lastX = location[0]
        lastY = location[1]

        targetView.invalidate()
        return true
    }
}
