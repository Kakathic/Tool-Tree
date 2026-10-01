package com.omarea.common.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver

class BlurTopBarLayout(context: Context, attrs: AttributeSet?) : BlurViewLinearLayout(context, attrs) {
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
    private var channelA = IntArray(0)
    private var channelR = IntArray(0)
    private var channelG = IntArray(0)
    private var channelB = IntArray(0)
    private var channelTmp = IntArray(0)
    private val livePaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = LIVE_ALPHA }
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

        val w = maxOf(1, (width * LIVE_SCALE).toInt())
        val h = maxOf(1, (height * LIVE_SCALE).toInt())

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

        bitmap.eraseColor(0)
        liveCanvas.save()
        liveCanvas.scale(w.toFloat() / width, h.toFloat() / height)
        liveCanvas.translate(-offsetX.toFloat(), -offsetY.toFloat())
        try {
            source.draw(liveCanvas)
        } finally {
            liveCanvas.restore()
        }

        blurPixels(bitmap, w, h)

        liveSrcRect.set(0, 0, w, h)
        liveDstRect.set(0, 0, width, height)
        canvas.drawBitmap(bitmap, liveSrcRect, liveDstRect, livePaint)
    }

    private fun blurPixels(bitmap: Bitmap, w: Int, h: Int) {
        val count = w * h
        if (livePixels.size < count) {
            livePixels = IntArray(count)
            channelA = IntArray(count)
            channelR = IntArray(count)
            channelG = IntArray(count)
            channelB = IntArray(count)
        }
        if (channelTmp.size < maxOf(w, h)) channelTmp = IntArray(maxOf(w, h))

        bitmap.getPixels(livePixels, 0, w, 0, 0, w, h)
        for (i in 0 until count) {
            val p = livePixels[i]
            val a = p ushr 24
            channelA[i] = a
            channelR[i] = ((p shr 16) and 0xFF) * a / 255
            channelG[i] = ((p shr 8) and 0xFF) * a / 255
            channelB[i] = (p and 0xFF) * a / 255
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
        private const val LIVE_SCALE = 0.8f
        private const val LIVE_BLUR_RADIUS = 8
        private const val LIVE_BLUR_PASSES = 2
        private const val LIVE_ALPHA = 255
    }
}
