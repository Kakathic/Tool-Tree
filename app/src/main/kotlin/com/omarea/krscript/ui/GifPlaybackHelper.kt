package com.omarea.krscript.ui

import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.widget.ImageView
import android.widget.TextView

// Điều khiển việc phát hoạt ảnh kiểu GIF cho ImageView, dùng chung cho ListItemClickable
// (icon/photo) và ListItemText (photo trong dòng text). Hỗ trợ 2 loại drawable:
// - AnimationDrawable: hệ gif giả lập cũ (ghép nhiều ảnh tĩnh, icon-gif-num / danh sách path)
// - AnimatedImageDrawable: file .gif THẬT (chỉ có từ Android 9/API 28, xem IconPathAnalysis.decodeRealGif)
object GifPlaybackHelper {
    /**
     * Gắn hoạt ảnh vào ImageView.
     * - autoplay = true: tự chạy ngay khi view attach vào window.
     * - autoplay = false: không tự chạy, người dùng bấm vào ảnh để phát/tạm dừng.
     * - loopCount > 0: dừng lại sau đúng số vòng lặp đó.
     * - loopCount <= 0: lặp vô hạn (mặc định).
     * Nếu drawable hiện tại không phải loại hoạt ảnh nào ở trên thì không làm gì (ảnh tĩnh bình thường).
     */
    fun bind(imageView: ImageView?, autoplay: Boolean, loopCount: Int) {
        val drawable = imageView?.drawable ?: return
        if (drawable is AnimationDrawable) {
            bindAnimationDrawable(imageView, drawable, autoplay, loopCount)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && drawable is AnimatedImageDrawable) {
            bindAnimatedImageDrawable(imageView, drawable, autoplay, loopCount)
        }
    }

    // Gắn hoạt ảnh cho icon inline (ImageSpan trong TextView) - KHÁC ImageView vì drawable gắn
    // trong Span không tự nhận invalidate/schedule của View chứa nó, phải tự set Callback trỏ
    // về TextView (invalidateDrawable -> textView.invalidate(); schedule/unschedule -> post/removeCallbacks
    // của chính TextView đó) thì animation mới tự chạy được.
    // KHÔNG hỗ trợ bấm-để-phát (autoplay=false chỉ hiện khung đầu, đứng yên) - icon nằm trong Span,
    // không có vùng bấm riêng để bắt sự kiện như ImageView.
    fun bindToTextView(textView: TextView, drawable: Drawable, autoplay: Boolean, loopCount: Int) {
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
        when (drawable) {
            is AnimationDrawable -> {
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
            is Animatable -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && drawable is AnimatedImageDrawable && loopCount > 0) {
                    // repeatCount không tính lượt chạy đầu tiên -> trừ 1 để khớp ý nghĩa loopCount ở trên
                    drawable.repeatCount = (loopCount - 1).coerceAtLeast(0)
                }
                drawable.start()
            }
        }
    }

    private fun bindAnimationDrawable(imageView: ImageView, drawable: AnimationDrawable, autoplay: Boolean, loopCount: Int) {
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

    // AnimatedImageDrawable: tự đặt repeatCount (nếu cần) rồi start()/stop() qua interface Animatable
    // - không cần tự tính tổng thời lượng khung hình như AnimationDrawable ở trên.
    private fun bindAnimatedImageDrawable(imageView: ImageView, drawable: AnimatedImageDrawable, autoplay: Boolean, loopCount: Int) {
        if (loopCount > 0) {
            drawable.repeatCount = (loopCount - 1).coerceAtLeast(0)
        }
        if (autoplay) {
            imageView.isClickable = false
            imageView.setOnClickListener(null)
            imageView.post {
                if (imageView.drawable === drawable) {
                    drawable.start()
                }
            }
        } else {
            imageView.isClickable = true
            imageView.setOnClickListener {
                if (drawable.isRunning) {
                    drawable.stop()
                } else {
                    drawable.start()
                }
            }
        }
    }
}
