package com.omarea.krscript.config

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import com.tool.tree.R
import com.omarea.krscript.model.ClickableNode
import com.omarea.krscript.model.TextNode

class IconPathAnalysis {
    companion object {
        // Cache Bitmap đã giải mã dùng chung cho toàn app (theo dung lượng, ~12MB).
        // Giúp tránh decode lại ảnh mỗi lần list re-render (đỡ giật/lag, đỡ tốn CPU).
        private const val CACHE_SIZE_BYTES = 12 * 1024 * 1024
        private val bitmapCache = object : LruCache<String, Bitmap>(CACHE_SIZE_BYTES) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
        }
    }

    // 获取快捷方式的图标
    fun loadLogo(context: Context, clickableNode: ClickableNode): Drawable {
        return loadLogo(context, clickableNode, true)!!
    }

    // 获取快捷方式的图标
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
        if (paths.size > 1) {
            // Danh sách nhiều đường dẫn tường minh (vd: "a.png|b.png") -> luôn ưu tiên coi là hoạt ảnh
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

    // photoPath truyền tường minh (thay vì đọc row.photo trực tiếp) để hỗ trợ cả photo tĩnh và
    // photo-sh động - row vẫn cần để đọc photoGifNum/photoGifTime (không đổi theo -sh).
    fun loadtextPhoto(context: Context, photoPath: String, row: TextNode.TextRow, pageDir: String): Drawable? {
        if (photoPath.isEmpty()) return null
        val paths = splitMultiPaths(photoPath)
        if (paths.isEmpty()) return null
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

    // Nạp ảnh nhỏ inline (field "icon" của row) - chỉ lấy 1 khung hình tĩnh (không hỗ trợ hoạt
    // ảnh/nhiều đường dẫn như photo, vì icon dùng để ghép cố định cạnh chữ qua ImageSpan).
    fun loadRowIcon(context: Context, iconPath: String, pageDir: String): Drawable? {
        if (iconPath.isEmpty()) return null
        val paths = splitMultiPaths(iconPath)
        if (paths.isEmpty()) return null
        decodeBitmap(context, pageDir, paths[0])?.let {
            return bitmap2Drawable(it)
        }
        return null
    }

    // Tách chuỗi cấu hình đường dẫn thành danh sách các đường dẫn riêng lẻ.
    // Hỗ trợ phân tách bằng dấu "|" trên 1 dòng, hoặc xuống dòng (chuỗi TOML nhiều dòng), hoặc cả hai.
    // Ví dụ: "path.png|test.png"  hoặc  "path.png\ntest.png"
    private fun splitMultiPaths(raw: String): List<String> {
        return raw.split('|', '\n', '\r')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    // Nạp danh sách đường dẫn tường minh (mỗi phần tử là 1 khung hình) thành hoạt ảnh kiểu GIF,
    // dùng chung thời gian mỗi khung (gif-time). Lặp vô hạn (do người gọi quyết định khi start/stop).
    // Khung lỗi sẽ bị bỏ qua.
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

    // Tách phần tên (không có đuôi) và phần đuôi mở rộng của đường dẫn
    // "path/photo.png" -> ("path/photo", ".png")
    private fun splitExt(path: String): Pair<String, String> {
        val lastDot = path.lastIndexOf('.')
        val lastSlash = path.lastIndexOf('/')
        return if (lastDot > lastSlash) {
            Pair(path.substring(0, lastDot), path.substring(lastDot))
        } else {
            Pair(path, "")
        }
    }

    // Nạp chuỗi ảnh dạng "path/photo_1.png" .. "path/photo_N.png" (N = frameCount)
    // và ghép thành hoạt ảnh kiểu GIF (AnimationDrawable).
    // Khung hình nào không đọc được sẽ bị bỏ qua (không làm hỏng cả hoạt ảnh).
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

    // Đọc + giải mã 1 ảnh từ đường dẫn (icon/photo/bg/...), có:
    //  - Cache theo (thư mục cấu hình + đường dẫn) để không decode lại nhiều lần cho cùng 1 ảnh.
    //  - Giới hạn kích thước giải mã (inSampleSize) theo độ phân giải màn hình để tránh OOM
    //    với ảnh gốc quá lớn (ảnh sẽ không được giải mã to hơn mức có thể hiển thị được).
    // Trả về null nếu không đọc được / không giải mã được.
    private fun decodeBitmap(context: Context, pageDir: String, path: String): Bitmap? {
        if (path.isEmpty()) return null
        val cacheKey = "$pageDir|$path"
        bitmapCache.get(cacheKey)?.let { return it }
        return try {
            val inputStream = PathAnalysis(context, pageDir).parsePath(path) ?: return null
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
                bitmapCache.put(cacheKey, bitmap)
            }
            bitmap
        } catch (ex: Exception) {
            null
        }
    }

    // Tính hệ số giảm mẫu (luỹ thừa của 2) sao cho ảnh giải mã ra không vượt quá maxDimension
    // ở cả 2 chiều, tránh tốn bộ nhớ giải mã ảnh to hơn mức màn hình có thể hiển thị.
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

    // Bitmap转换成Drawable
    fun bitmap2Drawable(bitmap: Bitmap): Drawable {
        return BitmapDrawable(bitmap)
    }
}
