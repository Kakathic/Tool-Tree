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

    /**
     * Tỉ lệ thu nhỏ wallpaper để xử lý blur.
     * 20% = nhiều pixel hơn 1.78x so với 15% cũ, blur mượt hơn,
     * chi phí RenderScript tăng không đáng kể (vài ms).
     */
    private val BLUR_SCALE = 0.20f
    private val BLUR_RADIUS = 16f

    // ─── Cache RenderScript ────────────────────────────────────────
    // Tránh tạo/huỷ RS context mỗi lần capture (tốn ~30-50ms/lần).
    @Volatile
    private var rs: RenderScript? = null
    @Volatile
    private var blurScript: ScriptIntrinsicBlur? = null
    private var rsContext: Context? = null

    private fun getRenderScript(context: Context): RenderScript {
        val rsInstance = rs
        // Tạo RS context mới chỉ khi chưa có hoặc đổi context (hiếm khi xảy ra)
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

    /**
     * Làm mờ bitmap bằng RenderScript (cache RS context + script).
     * Allocation được tạo/huỷ mỗi lần vì kích thước bitmap đầu vào có thể khác.
     *
     * @return bitmap mới đã blur, hoặc null nếu lỗi. Caller phải recycle bitmap trả về
     *         khi không còn dùng (trừ khi gán vào BlurEngine.blurBitmap).
     */
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

    /**
     * Làm mờ bitmap bất kỳ bằng RenderScript (công khai, dùng cho FastBlurUtility).
     *
     * Tận dụng RS context + script đã cache, nên gọi từ FastBlurUtility không tốn
     * chi phí khởi tạo RenderScript.
     *
     * @param context Application context
     * @param bitmap bitmap đầu vào (đã scale sẵn). Sẽ KHÔNG bị modify/recycle bởi hàm này.
     * @param radius bán kính blur (建议 16f, tương đương BlurController.BLUR_RADIUS)
     * @return bitmap mới đã blur (cùng kích thước với đầu vào), hoặc null nếu lỗi.
     *         Caller phải recycle bitmap trả về khi không còn dùng.
     */
    fun cacheBlurBitmap(context: Context, bitmap: Bitmap, radius: Float): Bitmap? {
        return blurBitmap(context, bitmap, radius)
    }

    /**
     * Điều chỉnh độ tương phản (Contrast) của Bitmap.
     * Tạo bitmap mới, KHÔNG sửa bitmap đầu vào.
     *
     * @return bitmap mới đã áp dụng contrast. Caller phải recycle khi không dùng.
     */
    private fun adjustContrast(bitmap: Bitmap, contrast: Float): Bitmap {
        val out = Bitmap.createBitmap(
            bitmap.width, bitmap.height,
            bitmap.config ?: Bitmap.Config.ARGB_8888
        )
        // val offset = (1f - contrast) * 128f
        val offset = if (ThemeModeState.isDarkMode()) 0f else (1f - 1.2f) * 128f // -12.8f
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

    /**
     * Chụp màu background solid (dùng khi directbg=1).
     *
     * Pipeline tối ưu:
     *   solid (20% size) → contrast (20% size, bitmap mới) → blur → kết quả
     *
     * Tất cả bitmap ở kích thước 20% màn hình → rất nhỏ, xử lý nhanh.
     */
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

            // Bitmap #1: solid color ở kích thước nhỏ
            val solidBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(solidBitmap).drawColor(bgColor)

            // Bitmap #2: áp dụng contrast (tạo mới, nhỏ)
            val contrastValue: Float = if (ThemeModeState.isDarkMode()) 0.9f else 1.2f
            val contrasted = adjustContrast(solidBitmap, contrastValue)
            solidBitmap.recycle()  // #1 đã dùng xong, recycle ngay

            // Bitmap #3: blur output (sẽ lưu vào BlurEngine.blurBitmap)
            val blurredResult = blurBitmap(context, contrasted, BLUR_RADIUS)
            contrasted.recycle()  // #2 đã dùng xong, recycle ngay

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

    /**
     * Chụp wallpaper, làm mờ và lưu vào BlurEngine.blurBitmap.
     *
     * Pipeline tối ưu:
     *   wallpaper (full size) → scale 20% (nhỏ) → contrast (nhỏ) → blur → kết quả
     *
     * So với bản gốc: contrast được áp dụng trên bitmap nhỏ (20%) thay vì
     * bitmap full-size → tiết kiệm ~96% memory cho bitmap contrast.
     */
    fun captureAndBlur(activity: Activity) {
        val activityRef = WeakReference(activity)

        Thread {
            val act = activityRef.get()
            if (act == null || act.isFinishing || act.isDestroyed) return@Thread

            var source: Bitmap? = null
            val context = act.applicationContext
            val isCustomWallpaper: Boolean

            // 1. Lấy Wallpaper gốc
            val customWallpaperFile = File(act.filesDir, "home/etc/wallpaper.jpg")
            if (customWallpaperFile.exists()) {
                isCustomWallpaper = true
                val currentLength = customWallpaperFile.length()
                val currentModified = customWallpaperFile.lastModified()

                // Cache check: wallpaper chưa đổi + bitmap còn hiệu lực → skip
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

            // 2. Scale → Contrast → Blur
            if (source != null) {
                val contrastValue: Float = if (ThemeModeState.isDarkMode()) 0.9f else 1.2f

                // Bitmap #1: scale xuống 20% (nhỏ)
                val width = max(round(source.width * BLUR_SCALE).toInt(), 1)
                val height = max(round(source.height * BLUR_SCALE).toInt(), 1)
                val scaledSource = Bitmap.createScaledBitmap(source, width, height, true)

                // Source từ custom file: tự decode → được recycle.
                // Source từ system wallpaper: thuộc hệ thống → KHÔNG recycle.
                if (isCustomWallpaper) {
                    source.recycle()
                    source = null
                }

                // Bitmap #2: contrast (nhỏ, tạo mới)
                val contrasted = adjustContrast(scaledSource, contrastValue)
                scaledSource.recycle()  // #1 đã dùng xong

                // Bitmap #3: blur output (sẽ lưu vào BlurEngine.blurBitmap)
                val blurredResult = blurBitmap(context, contrasted, BLUR_RADIUS)
                contrasted.recycle()  // #2 đã dùng xong

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
                // Không lấy được wallpaper nguồn - vẫn phải giải phóng các callback đang
                // chờ qua runWhenBlurReady(), nếu không sẽ kẹt vô thời hạn.
                act.runOnUiThread { BlurEngine.notifyBlurReady() }
            }
        }.start()
    }

    /**
     * Giải phóng tài nguyên RenderScript.
     * Gọi khi app kết thúc (không cần gọi mỗi lần chuyển trang).
     */
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
