package com.omarea.krscript.ui

import android.animation.ObjectAnimator
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.tool.tree.R

// Lớp phủ skeleton (khối xám + shimmer) đặt đè lên WebView trong khung html của item, tự mờ đi khi
// WebView nạp xong (progress = 100) hoặc sau MAX_WAIT_MS. Chỉ dùng WebChromeClient.onProgressChanged
// để không đổi cách WebView xử lý điều hướng link.
object RowsHtmlLoadingOverlay {
    private const val MAX_WAIT_MS = 30000L
    private const val FADE_MS = 150L

    // Gọi TRƯỚC khi loadUrl/loadDataWithBaseURL. Overlay luôn nằm sau WebView (index 1) để
    // container.getChildAt(0) vẫn là WebView như RowsHtmlRenderHelper.bind() đang giả định.
    fun show(container: FrameLayout, webView: WebView) {
        var found: Overlay? = null
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is Overlay) {
                found = child
                break
            }
        }
        val overlay: Overlay = if (found != null) {
            container.bringChildToFront(found)
            found
        } else {
            Overlay(container.context).also {
                container.addView(
                    it,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                )
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress >= 100) overlay.finish()
            }
        }
        overlay.begin()
    }

    private class Overlay(context: Context) : FrameLayout(context) {
        private val shimmer = View(context)
        private var animator: ObjectAnimator? = null
        private var loading = false
        private val timeout = Runnable { finish() }

        init {
            background = ContextCompat.getDrawable(context, R.drawable.krscript_item_ripple_inactive)
            clipToOutline = true

            val bars = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(16), dp(14), dp(16))
            }
            bars.addView(bar(context, ViewGroup.LayoutParams.MATCH_PARENT, 0))
            bars.addView(bar(context, ViewGroup.LayoutParams.MATCH_PARENT, dp(10)))
            bars.addView(bar(context, dp(150), dp(10)))
            addView(bars, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            shimmer.background = ContextCompat.getDrawable(context, R.drawable.kr_skeleton_shimmer_gradient)
            addView(shimmer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

        private fun bar(context: Context, width: Int, topMargin: Int): View {
            return View(context).apply {
                background = ContextCompat.getDrawable(context, R.drawable.kr_skeleton_block_bg)
                layoutParams = LinearLayout.LayoutParams(width, dp(14)).also { it.topMargin = topMargin }
            }
        }

        fun begin() {
            loading = true
            animate().cancel()
            alpha = 1f
            visibility = View.VISIBLE
            removeCallbacks(timeout)
            postDelayed(timeout, MAX_WAIT_MS)
            if (isAttachedToWindow) startShimmer()
        }

        fun finish() {
            if (!loading) return
            loading = false
            removeCallbacks(timeout)
            if (!isAttachedToWindow) {
                alpha = 0f
                visibility = View.GONE
                stopShimmer()
                return
            }
            animate().alpha(0f).setDuration(FADE_MS).withEndAction {
                visibility = View.GONE
                stopShimmer()
            }.start()
        }

        private fun startShimmer() {
            stopShimmer()
            val width = shimmer.width
            if (width > 0) {
                runShimmer(width)
                return
            }
            shimmer.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                override fun onLayoutChange(
                    v: View, left: Int, top: Int, right: Int, bottom: Int,
                    oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
                ) {
                    if (v.width > 0) {
                        v.removeOnLayoutChangeListener(this)
                        if (loading) runShimmer(v.width)
                    }
                }
            })
        }

        private fun runShimmer(width: Int) {
            shimmer.translationX = -width.toFloat()
            animator = ObjectAnimator.ofFloat(shimmer, View.TRANSLATION_X, -width.toFloat(), width.toFloat()).apply {
                duration = 1200
                repeatCount = ObjectAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
        }

        private fun stopShimmer() {
            animator?.cancel()
            animator = null
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            if (loading) {
                removeCallbacks(timeout)
                postDelayed(timeout, MAX_WAIT_MS)
                startShimmer()
            }
        }

        override fun onDetachedFromWindow() {
            stopShimmer()
            removeCallbacks(timeout)
            super.onDetachedFromWindow()
        }
    }
}
