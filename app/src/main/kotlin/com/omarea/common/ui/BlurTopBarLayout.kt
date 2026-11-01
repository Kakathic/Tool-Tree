package com.omarea.common.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import androidx.core.content.ContextCompat
import com.tool.tree.R
import com.tool.tree.ThemeModeState

open class BlurTopBarLayout(context: Context, attrs: AttributeSet?) : BlurViewLinearLayout(context, attrs) {
    protected open val sampleAbove: Boolean = false

    var blurSource: View? = null
        set(value) {
            if (field === value) return
            field = value
            updateScrollListener()
            invalidate()
        }

    private val liveBitmaps = arrayOfNulls<Bitmap>(2)
    private val liveCanvases = arrayOfNulls<Canvas>(2)
    private var liveIndex = 0
    private var livePixels = IntArray(0)
    private var captureBitmap: Bitmap? = null
    private var captureCanvas: Canvas? = null
    private var capturePixels = IntArray(0)
    private var channelA = IntArray(0)
    private var channelR = IntArray(0)
    private var channelG = IntArray(0)
    private var channelB = IntArray(0)
    private var channelTmp = IntArray(0)
    private val livePaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        alpha = LIVE_ALPHA
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(LIVE_SATURATION) })
    }
    private val liveSrcRect = Rect()
    private val liveDstRect = Rect()
    private val barLocation = IntArray(2)
    private val sourceLocation = IntArray(2)
    private var scrollListenerAttached = false

    private val scrollListener = ViewTreeObserver.OnScrollChangedListener {
        if (blurSource != null) invalidate()
    }

    init {
        this.engine.cornerRadius = 0f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateScrollListener()
    }

    override fun onDetachedFromWindow() {
        removeScrollListener()
        for (i in liveBitmaps.indices) {
            liveBitmaps[i] = null
            liveCanvases[i] = null
        }
        captureBitmap = null
        captureCanvas = null
        super.onDetachedFromWindow()
    }

    private fun updateScrollListener() {
        removeScrollListener()
        if (blurSource != null && isAttachedToWindow) {
            viewTreeObserver.addOnScrollChangedListener(scrollListener)
            scrollListenerAttached = true
        }
    }

    private fun removeScrollListener() {
        if (!scrollListenerAttached) return
        val observer = viewTreeObserver
        if (observer.isAlive) observer.removeOnScrollChangedListener(scrollListener)
        scrollListenerAttached = false
    }

    override fun drawOverBlur(canvas: Canvas) {
        val source = blurSource ?: return
        if (!source.isShown || source.width <= 0 || width <= 0 || height <= 0) return

        val scale = (BASE_SIGMA / LIVE_BLUR_SIGMA).coerceIn(0.02f, 0.5f)
        val w = maxOf(1, (width * scale).toInt())
        val visibleRows = maxOf(1, (height * scale).toInt())
        val extraRows = maxOf(1, (LIVE_BLUR_SIGMA * LIVE_SAMPLE_MARGIN * scale).toInt())
        val h = visibleRows + extraRows
        val scaleY = visibleRows.toFloat() / height

        liveIndex = 1 - liveIndex
        var bitmap = liveBitmaps[liveIndex]
        if (bitmap == null || bitmap.width != w || bitmap.height != h) {
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            liveBitmaps[liveIndex] = bitmap
            liveCanvases[liveIndex] = Canvas(bitmap)
        }
        val liveCanvas = liveCanvases[liveIndex] ?: return

        getLocationOnScreen(barLocation)
        source.getLocationOnScreen(sourceLocation)
        val offsetX = barLocation[0] - sourceLocation[0]
        val offsetY = barLocation[1] - sourceLocation[1]

        val cw = w * LIVE_SUPERSAMPLE
        val ch = h * LIVE_SUPERSAMPLE
        var capture = captureBitmap
        if (capture == null || capture.width != cw || capture.height != ch) {
            capture = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
            captureBitmap = capture
            captureCanvas = Canvas(capture)
        }
        val capCanvas = captureCanvas ?: return

        capture.eraseColor(0)
        capCanvas.save()
        capCanvas.scale(cw.toFloat() / width, scaleY * LIVE_SUPERSAMPLE)
        val shiftY = if (sampleAbove) offsetY - extraRows / scaleY else offsetY.toFloat()
        capCanvas.translate(-offsetX.toFloat(), -shiftY)
        try {
            source.draw(capCanvas)
        } finally {
            capCanvas.restore()
        }

        blurPixels(bitmap, capture, w, h)
        liveCanvas.drawColor(frostColor(), PorterDuff.Mode.SRC_ATOP)

        if (sampleAbove) liveSrcRect.set(0, extraRows, w, h) else liveSrcRect.set(0, 0, w, visibleRows)
        liveDstRect.set(0, 0, width, height)
        canvas.drawBitmap(bitmap, liveSrcRect, liveDstRect, livePaint)
    }

    private fun frostColor(): Int {
        val base = ContextCompat.getColor(
            context,
            if (ThemeModeState.isDarkMode()) R.color.colorBlurDark else R.color.colorBlurLight
        )
        val alpha = (Color.alpha(base) * LIVE_TINT_BOOST).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base))
    }

    private fun blurPixels(bitmap: Bitmap, capture: Bitmap, w: Int, h: Int) {
        val count = w * h
        if (livePixels.size < count) {
            livePixels = IntArray(count)
            channelA = IntArray(count)
            channelR = IntArray(count)
            channelG = IntArray(count)
            channelB = IntArray(count)
        }
        if (channelTmp.size < maxOf(w, h)) channelTmp = IntArray(maxOf(w, h))

        val ss = LIVE_SUPERSAMPLE
        val cw = capture.width
        if (capturePixels.size < cw * capture.height) capturePixels = IntArray(cw * capture.height)
        capture.getPixels(capturePixels, 0, cw, 0, 0, cw, capture.height)
        val area = ss * ss
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sa = 0
                var sr = 0
                var sg = 0
                var sb = 0
                for (dy in 0 until ss) {
                    val row = (y * ss + dy) * cw + x * ss
                    for (dx in 0 until ss) {
                        val p = capturePixels[row + dx]
                        val a = p ushr 24
                        sa += a
                        sr += ((p shr 16) and 0xFF) * a / 255
                        sg += ((p shr 8) and 0xFF) * a / 255
                        sb += (p and 0xFF) * a / 255
                    }
                }
                val i = y * w + x
                channelA[i] = sa / area
                channelR[i] = sr / area
                channelG[i] = sg / area
                channelB[i] = sb / area
            }
        }

        repeat(LIVE_BLUR_PASSES) {
            boxBlur(channelA, w, h)
            boxBlur(channelR, w, h)
            boxBlur(channelG, w, h)
            boxBlur(channelB, w, h)
        }

        for (i in 0 until count) {
            val a = channelA[i]
            if (a <= 0) {
                livePixels[i] = 0
            } else {
                val r = minOf(255, channelR[i] * 255 / a)
                val g = minOf(255, channelG[i] * 255 / a)
                val b = minOf(255, channelB[i] * 255 / a)
                livePixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        bitmap.setPixels(livePixels, 0, w, 0, 0, w, h)
    }

    private fun boxBlur(data: IntArray, w: Int, h: Int) {
        val radius = LIVE_BLUR_RADIUS
        val window = radius * 2 + 1
        val line = channelTmp

        for (y in 0 until h) {
            val row = y * w
            var sum = 0
            for (k in -radius..radius) sum += data[row + k.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                line[x] = sum / window
                sum += data[row + (x + radius + 1).coerceAtMost(w - 1)] - data[row + (x - radius).coerceAtLeast(0)]
            }
            for (x in 0 until w) data[row + x] = line[x]
        }

        for (x in 0 until w) {
            var sum = 0
            for (k in -radius..radius) sum += data[k.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                line[y] = sum / window
                sum += data[(y + radius + 1).coerceAtMost(h - 1) * w + x] - data[(y - radius).coerceAtLeast(0) * w + x]
            }
            for (y in 0 until h) data[y * w + x] = line[y]
        }
    }

    override fun drawStroke(canvas: Canvas) {
        val paint = BlurEngine.getStrokePaint(context)
        val strokeWidth = paint.strokeWidth

        val y = height - (strokeWidth / 2f)

        canvas.drawLine(0f, y, width.toFloat(), y, paint)
    }

    companion object {
        private const val LIVE_BLUR_SIGMA = 40f
        private const val LIVE_SATURATION = 1.6f
        private const val LIVE_TINT_BOOST = 1.4f
        private const val LIVE_ALPHA = 255
        private const val LIVE_BLUR_RADIUS = 2
        private const val LIVE_BLUR_PASSES = 3
        private const val LIVE_SAMPLE_MARGIN = 2.5f
        private const val LIVE_SUPERSAMPLE = 8
        private const val BASE_SIGMA = 2.449f
    }
}
