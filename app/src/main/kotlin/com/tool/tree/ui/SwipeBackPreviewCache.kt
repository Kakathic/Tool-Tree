package com.tool.tree.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import com.omarea.common.ui.FastBlurUtility

object SwipeBackPreviewCache {

    class Preview(val sharp: Bitmap, val blurred: Bitmap?)

    private var preview: Preview? = null

    private const val SCALE = 1f

    fun capture(activity: Activity, onCaptured: () -> Unit) {
        try {
            val window = activity.window
            val decorView = window.decorView
            val width = decorView.width
            val height = decorView.height
            if (width <= 0 || height <= 0) {
                recycle()
                onCaptured()
                return
            }

            val scaledWidth = (width * SCALE).toInt().coerceAtLeast(1)
            val scaledHeight = (height * SCALE).toInt().coerceAtLeast(1)
            val sharp = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PixelCopy.request(window, sharp, { copyResult ->
                    if (copyResult != PixelCopy.SUCCESS) {
                        drawFallback(decorView, sharp)
                    }
                    finishCapture(activity, sharp, onCaptured)
                }, Handler(Looper.getMainLooper()))
            } else {
                drawFallback(decorView, sharp)
                finishCapture(activity, sharp, onCaptured)
            }
        } catch (_: Exception) {
            recycle()
            onCaptured()
        }
    }

    private fun drawFallback(decorView: View, sharp: Bitmap) {
        try {
            val canvas = Canvas(sharp)
            canvas.scale(SCALE, SCALE)
            decorView.draw(canvas)
        } catch (_: Exception) {
        }
    }

    private fun finishCapture(activity: Activity, sharp: Bitmap, onCaptured: () -> Unit) {
        val blurred = try {
            FastBlurUtility.blurBitmap(activity, sharp)
        } catch (_: Exception) {
            null
        }

        recycle()
        preview = Preview(sharp, blurred)
        onCaptured()
    }

    fun consume(): Preview? {
        val result = preview
        preview = null
        return result
    }

    private fun recycle() {
        preview?.let {
            if (!it.sharp.isRecycled) it.sharp.recycle()
            it.blurred?.takeIf { b -> !b.isRecycled }?.recycle()
        }
        preview = null
    }
}