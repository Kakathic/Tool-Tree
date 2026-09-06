package com.omarea.krscript.ui

import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.widget.ImageView
import android.widget.TextView

// Điều khiển việc phát hoạt ảnh kiểu GIF (AnimationDrawable) cho ImageView,
// dùng chung cho ListItemClickable (icon/photo) và ListItemText (photo trong dòng text).
object GifPlaybackHelper {
    /**
     * Gắn hoạt ảnh vào ImageView.
     * - autoplay = true: tự chạy ngay khi view attach vào window.
     * - autoplay = false: không tự chạy, người dùng bấm vào ảnh để phát/tạm dừng.
     * - loopCount > 0: dừng lại sau đúng số vòng lặp đó (dừng ở khung gần cuối của vòng cuối).
     * - loopCount <= 0: lặp vô hạn (mặc định).
     * Nếu drawable hiện tại không phải AnimationDrawable thì không làm gì (ảnh tĩnh bình thường).
     */
    fun bind(imageView: ImageView?, autoplay: Boolean, loopCount: Int) {
        val drawable = imageView?.drawable as? AnimationDrawable ?: return

        if (autoplay) {
            imageView.isClickable = false
            imageView.setOnClickListener(null)
            imageView.post {
                if (imageView.drawable === drawable) {
                    startWithLoopLimit(imageView, drawable, loopCount)
                }
            }
        } else {
            // Không tự chạy: hiện khung hình đầu tiên, chờ người dùng bấm vào để phát/tạm dừng
            imageView.isClickable = true
            imageView.setOnClickListener {
                if (drawable.isRunning) {
                    drawable.stop()
                } else {
                    startWithLoopLimit(imageView, drawable, loopCount)
                }
            }
        }
    }

    // Gắn hoạt ảnh cho icon inline (ImageSpan trong TextView) - KHÁC ImageView vì AnimationDrawable
    // gắn trong Span không tự nhận invalidate/schedule của View chứa nó, phải tự set Callback trỏ
    // về TextView (invalidateDrawable -> textView.invalidate(); schedule/unschedule -> post/removeCallbacks
    // của chính TextView đó) thì animation mới tự chạy được.
    // KHÔNG hỗ trợ bấm-để-phát (autoplay=false chỉ hiện khung đầu, đứng yên) - icon nằm trong Span,
    // không có vùng bấm riêng để bắt sự kiện như ImageView.
    fun bindToTextView(textView: TextView, drawable: AnimationDrawable, autoplay: Boolean, loopCount: Int) {
        drawable.callback = object : Drawable.Callback {
            override fun invalidateDrawable(who: Drawable) {
                textView.invalidate()
            }
            override fun scheduleDrawable(who: Drawable, what: Runnable, whenMs: Long) {
                textView.postDelayed(what, whenMs - SystemClock.uptimeMillis())
            }
            override fun unscheduleDrawable(who: Drawable, what: Runnable) {
                textView.removeCallbacks(what)
            }
        }
        if (!autoplay) return
        drawable.stop()
        drawable.start()
        if (loopCount > 0) {
            var totalDuration = 0L
            for (i in 0 until drawable.numberOfFrames) {
                totalDuration += drawable.getDuration(i)
            }
            val stopAfterMs = totalDuration * loopCount
            if (stopAfterMs > 0) {
                textView.postDelayed({ drawable.stop() }, stopAfterMs)
            }
        }
    }

    private fun startWithLoopLimit(imageView: ImageView, drawable: AnimationDrawable, loopCount: Int) {
        drawable.stop()
        drawable.start()
        if (loopCount > 0) {
            var totalDuration = 0L
            for (i in 0 until drawable.numberOfFrames) {
                totalDuration += drawable.getDuration(i)
            }
            val stopAfterMs = totalDuration * loopCount
            if (stopAfterMs > 0) {
                imageView.postDelayed({
                    if (imageView.drawable === drawable) {
                        drawable.stop()
                    }
                }, stopAfterMs)
            }
        }
    }
}
