package com.omarea.krscript.config

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import com.tool.tree.R
import com.omarea.krscript.model.ClickableNode
import com.omarea.krscript.model.TextNode
import java.nio.ByteBuffer

class IconPathAnalysis {
    private class CachedImage(val bitmap: Bitmap, val lastModified: Long)

    companion object {
        private const val CACHE_SIZE_BYTES = 12 * 1024 * 1024
        private val bitmapCache = object : LruCache<String, CachedImage>(CACHE_SIZE_BYTES) {
            override fun sizeOf(key: String, value: CachedImage): Int = value.bitmap.byteCount
        }
    }

    private val failedPaths = HashSet<String>()

    fun loadLogo(context: Context, clickableNode: ClickableNode): Drawable {
        return loadLogo(context, clickableNode, true)!!
    }

    fun loadLogo(context: Context, clickableNode: ClickableNode, useDefault: Boolean): Drawable? {
        if (!clickableNode.logoPath.isEmpty()) {
            decodeBitmap(context, clickableNode.pageConfigDir, clickableNode.logoPath)?.let {
                return bitmap2Drawable(it)
            }
        }
        if (!clickableNode.iconPath.isEmpty()) {
            decodeBitmap(context, clickableNode.pageConfigDir, clickableNode.iconPath)?.let {
                return bitmap2Drawable(it)
            }
        }
        return if (useDefault) context.getDrawable(R.drawable.kr_shortcut_logo)!! else null
    }

    fun loadIcon(context: Context, clickableNode: ClickableNode): Drawable? {
        if (clickableNode.iconPath.isEmpty()) return null
        val paths = splitMultiPaths(clickableNode.iconPath)
        if (paths.isEmpty()) return null
        if (clickableNode.iconRealGif) {
            decodeRealGif(context, clickableNode.pageConfigDir, paths[0])?.let { return it }
        }
        if (paths.size > 1) {
            loadAnimatedFromPaths(context, clickableNode.pageConfigDir, paths, clickableNode.iconGifTime)?.let { return it }
        } else if (clickableNode.iconGifNum > 0) {
            loadAnimatedFrames(context, clickableNode.pageConfigDir, paths[0], clickableNode.iconGifNum, clickableNode.iconGifTime)?.let { return it }
        }
        decodeBitmap(context, clickableNode.pageConfigDir, paths[0])?.let {
            return bitmap2Drawable(it)
        }
        return null
    }

    fun loadPhoto(context: Context, clickableNode: ClickableNode): Drawable? {
        if (clickableNode.photoPath.isEmpty()) return null
        val paths = splitMultiPaths(clickableNode.photoPath)
        if (paths.isEmpty()) return null
        if (clickableNode.photoRealGif) {
            decodeRealGif(context, clickableNode.pageConfigDir, paths[0])?.let { return it }
        }
        if (paths.size > 1) {
            loadAnimatedFromPaths(context, clickableNode.pageConfigDir, paths, clickableNode.photoGifTime)?.let { return it }
        } else if (clickableNode.photoGifNum > 0) {
            loadAnimatedFrames(context, clickableNode.pageConfigDir, paths[0], clickableNode.photoGifNum, clickableNode.photoGifTime)?.let { return it }
        }
        decodeBitmap(context, clickableNode.pageConfigDir, paths[0])?.let {
            return bitmap2Drawable(it)
        }
        return null
    }

    fun loadBg(context: Context, clickableNode: ClickableNode): Drawable? {
        if (!clickableNode.bgPath.isEmpty()) {
            decodeBitmap(context, clickableNode.pageConfigDir, clickableNode.bgPath)?.let {
                return bitmap2Drawable(it)
            }
        }
        return null
    }

    fun loadtextPhoto(context: Context, photoPath: String, row: TextNode.TextRow, pageDir: String): Drawable? {
        if (photoPath.isEmpty()) return null
        val paths = splitMultiPaths(photoPath)
        if (paths.isEmpty()) return null
        if (row.photoRealGif) {
            decodeRealGif(context, pageDir, paths[0])?.let { return it }
        }
        if (paths.size > 1) {
            loadAnimatedFromPaths(context, pageDir, paths, row.photoGifTime)?.let { return it }
        } else if (row.photoGifNum > 0) {
            loadAnimatedFrames(context, pageDir, paths[0], row.photoGifNum, row.photoGifTime)?.let { return it }
        }
        decodeBitmap(context, pageDir, paths[0])?.let {
            return bitmap2Drawable(it)
        }
        return null
    }

    fun loadRowIcon(context: Context, iconPath: String, pageDir: String, gifNum: Int = 0, gifTime: Int = 300, realGif: Boolean = false): Drawable? {
        if (iconPath.isEmpty()) return null
        val paths = splitMultiPaths(iconPath)
        if (paths.isEmpty()) return null
        if (realGif) {
            decodeRealGif(context, pageDir, paths[0])?.let { return it }
        }
        if (paths.size > 1) {
            loadAnimatedFromPaths(context, pageDir, paths, gifTime)?.let { return it }
        } else if (gifNum > 0) {
            loadAnimatedFrames(context, pageDir, paths[0], gifNum, gifTime)?.let { return it }
        }
        decodeBitmap(context, pageDir, paths[0])?.let {
            return bitmap2Drawable(it)
        }
        return null
    }

    private fun decodeRealGif(context: Context, pageDir: String, path: String): Drawable? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        if (path.isEmpty()) return null
        return try {
            val inputStream = PathAnalysis(context, pageDir).parsePath(path) ?: return null
            val bytes = inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) return null
            val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
            ImageDecoder.decodeDrawable(source)
        } catch (ex: Exception) {
            null
        }
    }

    private fun splitMultiPaths(raw: String): List<String> {
        return raw.split('|', '\n', '\r')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun loadAnimatedFromPaths(context: Context, pageDir: String, paths: List<String>, frameTimeMs: Int): AnimationDrawable? {
        if (paths.size <= 1) return null
        val duration = if (frameTimeMs > 0) frameTimeMs else 300
        val anim = AnimationDrawable()
        var loadedCount = 0
        for (path in paths) {
            decodeBitmap(context, pageDir, path)?.let {
                anim.addFrame(bitmap2Drawable(it), duration)
                loadedCount++
            }
        }
        anim.isOneShot = false
        return if (loadedCount > 0) anim else null
    }

    private fun splitExt(path: String): Pair<String, String> {
        val lastDot = path.lastIndexOf('.')
        val lastSlash = path.lastIndexOf('/')
        return if (lastDot > lastSlash) {
            Pair(path.substring(0, lastDot), path.substring(lastDot))
        } else {
            Pair(path, "")
        }
    }

    private fun loadAnimatedFrames(context: Context, pageDir: String, basePath: String, frameCount: Int, frameTimeMs: Int): AnimationDrawable? {
        if (basePath.isEmpty() || frameCount <= 0) return null
        val (baseNoExt, ext) = splitExt(basePath)
        val duration = if (frameTimeMs > 0) frameTimeMs else 300
        val anim = AnimationDrawable()
        var loadedCount = 0
        for (i in 1..frameCount) {
            val framePath = "${baseNoExt}_$i$ext"
            decodeBitmap(context, pageDir, framePath)?.let {
                anim.addFrame(bitmap2Drawable(it), duration)
                loadedCount++
            }
        }
        anim.isOneShot = false
        return if (loadedCount > 0) anim else null
    }

    private fun decodeBitmap(context: Context, pageDir: String, path: String): Bitmap? {
        if (path.isEmpty()) return null
        val cacheKey = "$pageDir|$path"
        if (failedPaths.contains(cacheKey)) return null

        return try {
            val pathAnalysis = PathAnalysis(context, pageDir)
            val inputStream = pathAnalysis.parsePath(path)
            if (inputStream == null) {
                failedPaths.add(cacheKey)
                return null
            }

            val currentModified = pathAnalysis.getCurrentLastModified()
            bitmapCache.get(cacheKey)?.let { cached ->
                if (currentModified == 0L || cached.lastModified == currentModified) {
                    inputStream.close()
                    return cached.bitmap
                }
            }

            val bytes = inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) return null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val metrics = context.resources.displayMetrics
            val maxDimension = maxOf(metrics.widthPixels, metrics.heightPixels)
            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (bitmap != null) {
                bitmapCache.put(cacheKey, CachedImage(bitmap, currentModified))
            }
            bitmap
        } catch (ex: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var inSampleSize = 1
        if (maxDimension > 0 && (width > maxDimension || height > maxDimension)) {
            var halfWidth = width / 2
            var halfHeight = height / 2
            while ((halfWidth / inSampleSize) >= maxDimension && (halfHeight / inSampleSize) >= maxDimension) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    fun bitmap2Drawable(bitmap: Bitmap): Drawable {
        return BitmapDrawable(bitmap)
    }
}
