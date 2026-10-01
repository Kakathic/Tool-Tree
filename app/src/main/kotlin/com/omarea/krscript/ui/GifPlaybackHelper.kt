package com.omarea.krscript.ui

import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.widget.ImageView
import android.widget.TextView

object GifPlaybackHelper {
    fun bind(imageView: ImageView?, autoplay: Boolean, loopCount: Int) {
        val drawable = imageView?.drawable ?: return
        if (drawable is AnimationDrawable) {
            bindAnimationDrawable(imageView, drawable, autoplay, loopCount)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && drawable is AnimatedImageDrawable) {
            bindAnimatedImageDrawable(imageView, drawable, autoplay, loopCount)
        }
    }

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
