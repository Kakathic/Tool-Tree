package com.omarea.common.ui

import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import com.tool.tree.ThemeModeState
import java.io.File
import java.lang.ref.WeakReference
import kotlin.math.max
import kotlin.math.round

class BlurController {

    private val BLUR_SCALE = 0.20f
    private val BLUR_RADIUS = 16f

    @Volatile
    private var rs: RenderScript? = null
    @Volatile
    private var blurScript: ScriptIntrinsicBlur? = null
    private var rsContext: Context? = null

    private fun getRenderScript(context: Context): RenderScript {
        val rsInstance = rs
        if (rsInstance == null || rsContext !== context) {
            rsInstance?.destroy()
            blurScript?.destroy()
            blurScript = null
            val newRs = RenderScript.create(context)
            rs = newRs
            rsContext = context
            return newRs
        }
        return rsInstance
    }

    private fun getBlurScript(rs: RenderScript): ScriptIntrinsicBlur {
        var script = blurScript
        if (script == null) {
            script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
            blurScript = script
        }
        return script
    }

    private fun blurBitmap(context: Context, bitmap: Bitmap, radius: Float): Bitmap? {
        val outBitmap = Bitmap.createBitmap(
            bitmap.width, bitmap.height,
            bitmap.config ?: Bitmap.Config.ARGB_8888
        )
        val rsInstance = getRenderScript(context)
        var input: Allocation? = null
        var output: Allocation? = null
        try {
            input = Allocation.createFromBitmap(rsInstance, bitmap)
            output = Allocation.createFromBitmap(rsInstance, outBitmap)
            val script = getBlurScript(rsInstance)
            script.setRadius(radius)
            script.setInput(input)
            script.forEach(output)
            output.copyTo(outBitmap)
        } catch (e: Exception) {
            outBitmap.recycle()
            return null
        } finally {
            input?.destroy()
            output?.destroy()
        }
        return outBitmap
    }

    fun cacheBlurBitmap(context: Context, bitmap: Bitmap, radius: Float): Bitmap? {
        return blurBitmap(context, bitmap, radius)
    }

    private fun adjustContrast(bitmap: Bitmap, contrast: Float): Bitmap {
        val out = Bitmap.createBitmap(
            bitmap.width, bitmap.height,
            bitmap.config ?: Bitmap.Config.ARGB_8888
        )
        val offset = if (ThemeModeState.isDarkMode()) 0f else (1f - 1.2f) * 128f
        val cm = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, offset,
                0f, contrast, 0f, 0f, offset,
                0f, 0f, contrast, 0f, offset,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        val canvas = Canvas(out)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return out
    }

    fun captureBackground(activity: Activity) {
        val activityRef = WeakReference(activity)

        Thread {
            val act = activityRef.get()
            if (act == null || act.isFinishing || act.isDestroyed) return@Thread

            val context = act.applicationContext
            val bgColor = BlurEngine.directBgColor

            val screenWidth = act.resources.displayMetrics.widthPixels
            val screenHeight = act.resources.displayMetrics.heightPixels
            val width = max(round(screenWidth * BLUR_SCALE).toInt(), 1)
            val height = max(round(screenHeight * BLUR_SCALE).toInt(), 1)

            val solidBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(solidBitmap).drawColor(bgColor)

            val contrastValue: Float = if (ThemeModeState.isDarkMode()) 0.9f else 1.2f
            val contrasted = adjustContrast(solidBitmap, contrastValue)
            solidBitmap.recycle()

            val blurredResult = blurBitmap(context, contrasted, BLUR_RADIUS)
            contrasted.recycle()

            if (blurredResult != null) {
                BlurEngine.blurBitmap = blurredResult
                BlurEngine.isPaused = false

                act.runOnUiThread {
                    if (!act.isFinishing && act.window != null) {
                        act.window.decorView.invalidate()
                    }
                    BlurEngine.notifyBlurReady()
                }
            } else {
                act.runOnUiThread { BlurEngine.notifyBlurReady() }
            }
        }.start()
    }

    fun captureAndBlur(activity: Activity) {
        val activityRef = WeakReference(activity)

        Thread {
            val act = activityRef.get()
            if (act == null || act.isFinishing || act.isDestroyed) return@Thread

            var source: Bitmap? = null
            val context = act.applicationContext
            val isCustomWallpaper: Boolean

            val customWallpaperFile = File(act.filesDir, "home/etc/wallpaper.jpg")
            if (customWallpaperFile.exists()) {
                isCustomWallpaper = true
                val currentLength = customWallpaperFile.length()
                val currentModified = customWallpaperFile.lastModified()

                if (currentLength == lastFileLength && currentModified == lastFileModified) {
                    val current = BlurEngine.blurBitmap
                    if (current != null && !current.isRecycled) return@Thread
                }

                lastFileLength = currentLength
                lastFileModified = currentModified
                source = BitmapFactory.decodeFile(customWallpaperFile.absolutePath)
            } else {
                isCustomWallpaper = false
                val wm = WallpaperManager.getInstance(context)
                wm.forgetLoadedWallpaper()
                val drawable = wm.drawable
                if (drawable is BitmapDrawable) {
                    source = drawable.bitmap
                }
            }

            if (source != null) {
                val contrastValue: Float = if (ThemeModeState.isDarkMode()) 0.9f else 1.2f

                val width = max(round(source.width * BLUR_SCALE).toInt(), 1)
                val height = max(round(source.height * BLUR_SCALE).toInt(), 1)
                val scaledSource = Bitmap.createScaledBitmap(source, width, height, true)

                if (isCustomWallpaper) {
                    source.recycle()
                    source = null
                }

                val contrasted = adjustContrast(scaledSource, contrastValue)
                scaledSource.recycle()

                val blurredResult = blurBitmap(context, contrasted, BLUR_RADIUS)
                contrasted.recycle()

                if (blurredResult != null) {
                    BlurEngine.blurBitmap = blurredResult
                    BlurEngine.isPaused = false

                    act.runOnUiThread {
                        if (!act.isFinishing && act.window != null) {
                            act.window.decorView.invalidate()
                        }
                        BlurEngine.notifyBlurReady()
                    }
                } else {
                    act.runOnUiThread { BlurEngine.notifyBlurReady() }
                }
            } else {
                act.runOnUiThread { BlurEngine.notifyBlurReady() }
            }
        }.start()
    }

    fun destroyRs() {
        blurScript?.destroy()
        blurScript = null
        rs?.destroy()
        rs = null
        rsContext = null
    }

    companion object {
        private var lastFileLength: Long = -1
        private var lastFileModified: Long = -1
    }
}
