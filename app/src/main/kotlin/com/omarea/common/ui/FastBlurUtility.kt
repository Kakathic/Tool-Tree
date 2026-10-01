package com.omarea.common.ui

import android.app.Activity
import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.view.View
import java.io.File
import kotlin.math.max
import kotlin.math.round

object FastBlurUtility {

    @JvmStatic
    fun getPageBlurBackground(activity: Activity): Bitmap? {
        val screenWidth = activity.resources.displayMetrics.widthPixels
        val screenHeight = activity.resources.displayMetrics.heightPixels
        if (screenWidth <= 0 || screenHeight <= 0) return null

        val cachedBlur = BlurEngine.blurBitmap
        if (cachedBlur == null || cachedBlur.isRecycled) return null

        return scaleWithTint(cachedBlur, screenWidth, screenHeight)
    }

    @JvmStatic
    fun getDialogBlurBackground(activity: Activity): Bitmap? {
        val screenWidth = activity.resources.displayMetrics.widthPixels
        val screenHeight = activity.resources.displayMetrics.heightPixels
        if (screenWidth <= 0 || screenHeight <= 0) return null

        val screenshot = takeScreenShot(activity)
        if (screenshot == null || screenshot.isRecycled) return null

        val result = blurViaController(activity, screenshot, screenWidth, screenHeight)
        if (!screenshot.isRecycled) {
            screenshot.recycle()
        }
        return result
    }

    @JvmStatic
    fun getWallpaperBlurBackground(activity: Activity): Bitmap? {
        val screenWidth = activity.resources.displayMetrics.widthPixels
        val screenHeight = activity.resources.displayMetrics.heightPixels
        if (screenWidth <= 0 || screenHeight <= 0) return null

        val loaded = loadWallpaperSource(activity) ?: return null
        val (source, ownsSource) = loaded
        return try {
            blurViaController(activity, source, screenWidth, screenHeight)
        } finally {
            if (ownsSource && !source.isRecycled) {
                source.recycle()
            }
        }
    }

    @JvmStatic
    fun getWallpaperRawBackground(activity: Activity): Bitmap? {
        val screenWidth = activity.resources.displayMetrics.widthPixels
        val screenHeight = activity.resources.displayMetrics.heightPixels
        if (screenWidth <= 0 || screenHeight <= 0) return null

        val loaded = loadWallpaperSource(activity) ?: return null
        val (source, ownsSource) = loaded
        return try {
            scaleWithTint(source, screenWidth, screenHeight)
        } finally {
            if (ownsSource && !source.isRecycled) {
                source.recycle()
            }
        }
    }

    private fun loadWallpaperSource(activity: Activity): Pair<Bitmap, Boolean>? {
        return try {
            val customWallpaperFile = File(activity.filesDir, "home/etc/wallpaper.jpg")
            if (customWallpaperFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(customWallpaperFile.absolutePath) ?: return null
                bitmap to true
            } else {
                val wm = WallpaperManager.getInstance(activity)
                wm.forgetLoadedWallpaper()
                val drawable = wm.drawable
                if (drawable is BitmapDrawable) drawable.bitmap to false else null
            }
        } catch (e: Exception) {
            null
        }
    }

    @JvmStatic
    fun clearCache() {
        val cached = BlurEngine.blurBitmap
        if (cached != null && !cached.isRecycled) {
            cached.recycle()
        }
        BlurEngine.blurBitmap = null
    }

    @JvmStatic
    fun blurBitmap(activity: Activity, sourceBitmap: Bitmap): Bitmap? {
        if (sourceBitmap.isRecycled) return null
        return blurViaController(activity, sourceBitmap, sourceBitmap.width, sourceBitmap.height)
    }

    private fun takeScreenShot(activity: Activity): Bitmap? {
        return try {
            val view: View = activity.window.decorView
            if (view.width <= 0 || view.height <= 0) return null

            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            view.draw(canvas)
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun scaleWithTint(blurBitmap: Bitmap, targetW: Int, targetH: Int): Bitmap? {
        return try {
            val output = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)

            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

            val isDark = com.tool.tree.ThemeModeState.isDarkMode()
            val scale = if (isDark) 0.8f else 1.1f
            val offset = if (isDark) 0f else (1f - 1.1f) * 128f
            val cm = ColorMatrix(
                floatArrayOf(
                    scale, 0f, 0f, 0f, offset,
                    0f, scale, 0f, 0f, offset,
                    0f, 0f, scale, 0f, offset,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            paint.colorFilter = ColorMatrixColorFilter(cm)

            canvas.drawBitmap(blurBitmap, null, RectF(0f, 0f, targetW.toFloat(), targetH.toFloat()), paint)
            output
        } catch (e: OutOfMemoryError) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun blurViaController(activity: Activity, screenshot: Bitmap, screenWidth: Int, screenHeight: Int): Bitmap? {
        return try {
            val scale = 0.20f
            val width = max(round(screenshot.width * scale).toInt(), 1)
            val height = max(round(screenshot.height * scale).toInt(), 1)
            val scaled = Bitmap.createScaledBitmap(screenshot, width, height, true)

            val blurred = BlurEngine.controller.cacheBlurBitmap(activity.applicationContext, scaled, 16f)

            if (!scaled.isRecycled) {
                scaled.recycle()
            }

            if (blurred == null || blurred.isRecycled) return null

            val result = scaleWithTint(blurred, screenWidth, screenHeight)

            if (blurred !== BlurEngine.blurBitmap && !blurred.isRecycled) {
                blurred.recycle()
            }

            result
        } catch (e: OutOfMemoryError) {
            null
        } catch (e: Exception) {
            null
        }
    }
}
