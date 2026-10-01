package com.omarea.common.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Shader
import android.view.View
import android.view.ViewTreeObserver
import androidx.core.content.ContextCompat
import com.tool.tree.R
import com.tool.tree.ThemeModeState

class BlurEngine(private val targetView: View) {
    var cornerRadius: Float = DEFAULT_CORNER_RADIUS

    private val location = IntArray(2)
    private var cachedBitmap: Bitmap? = null
    private var cachedCanvas: Canvas? = null

    private var cachedShader: BitmapShader? = null
    private var cachedShaderBitmap: Bitmap? = null

    private var cachedTintColor: Int = 0
    private var cachedTintColorForDark: Boolean? = null

    private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shaderMatrix = Matrix()

    private var preDrawListener: ViewTreeObserver.OnPreDrawListener? = null
    private var attachListener: View.OnAttachStateChangeListener? = null

    fun setup() {
        if (cornerRadius > 0) {
            targetView.outlineProvider = BlurOutlineProvider(cornerRadius)
            targetView.clipToOutline = true
        } else {
            targetView.outlineProvider = null
            targetView.clipToOutline = false
        }

        removePreDrawListener()

        val listener = BlurPreDrawListener(this, targetView)
        preDrawListener = listener

        if (targetView.isAttachedToWindow) {
            targetView.viewTreeObserver.addOnPreDrawListener(listener)
        }

        if (attachListener == null) {
            attachListener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    preDrawListener?.let {
                        val observer = v.viewTreeObserver
                        if (observer.isAlive) {
                            observer.removeOnPreDrawListener(it)
                            observer.addOnPreDrawListener(it)
                        }
                    }
                }

                override fun onViewDetachedFromWindow(v: View) {
                    removePreDrawListener()
                }
            }
            targetView.addOnAttachStateChangeListener(attachListener)
        }
    }

    private fun removePreDrawListener() {
        val listener = preDrawListener ?: return
        val observer = targetView.viewTreeObserver
        if (observer.isAlive) {
            observer.removeOnPreDrawListener(listener)
        }
        preDrawListener = null
    }

    fun getUpdatedBlurBitmap(): Bitmap? {
        val blurBitmap = BlurEngine.blurBitmap
        if (isPaused || blurBitmap == null || blurBitmap.isRecycled ||
            targetView.width <= 0 || targetView.height <= 0
        ) {
            return null
        }

        val rootView = targetView.rootView
        if (rootView == null || rootView.width <= 0 || rootView.height <= 0) {
            return null
        }

        targetView.getLocationOnScreen(location)

        val scaleX = blurBitmap.width.toFloat() / rootView.width
        val scaleY = blurBitmap.height.toFloat() / rootView.height

        val w = (targetView.width * scaleX).toInt()
        val h = (targetView.height * scaleY).toInt()

        if (w <= 0 || h <= 0) return null

        val x = (location[0] * scaleX).toInt()
        val y = (location[1] * scaleY).toInt()

        try {
            var cached = cachedBitmap
            if (cached == null || cached.width != w || cached.height != h) {
                if (cached != null && !cached.isRecycled) {
                    cached.recycle()
                }
                cached = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                cachedBitmap = cached
                cachedCanvas = Canvas(cached)
                cachedShader = null
                cachedShaderBitmap = null
            }

            val canvas = cachedCanvas!!
            canvas.drawColor(0, PorterDuff.Mode.CLEAR)

            if (cachedShaderBitmap !== blurBitmap || cachedShader == null) {
                if (blurBitmap.isRecycled) return null
                cachedShader = BitmapShader(blurBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                cachedShaderBitmap = blurBitmap
            }

            shaderMatrix.reset()
            shaderMatrix.postTranslate(-x.toFloat(), -y.toFloat())
            cachedShader!!.setLocalMatrix(shaderMatrix)

            shaderPaint.shader = cachedShader
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), shaderPaint)

            canvas.drawColor(getBlurTintColorCached())

            return cached
        } catch (e: Exception) {
            return null
        }
    }

    private fun getBlurTintColorCached(): Int {
        val isDark = ThemeModeState.isDarkMode()
        if (cachedTintColorForDark != isDark) {
            cachedTintColorForDark = isDark
            val colorRes = if (isDark) R.color.colorBlurDark else R.color.colorBlurLight
            cachedTintColor = ContextCompat.getColor(targetView.context, colorRes)
        }
        return cachedTintColor
    }

    fun destroy() {
        removePreDrawListener()

        attachListener?.let {
            targetView.removeOnAttachStateChangeListener(it)
            attachListener = null
        }

        val cached = cachedBitmap
        if (cached != null && !cached.isRecycled) {
            cached.recycle()
            cachedBitmap = null
        }
        cachedCanvas = null
        cachedShader = null
        cachedShaderBitmap = null
        cachedTintColorForDark = null
    }

    companion object {
        @JvmField
        var controller: BlurController = BlurController()

        @Volatile
        @JvmField
        var blurBitmap: Bitmap? = null

        @JvmField
        var isPaused = false

        @JvmField
        var isDirectBgMode = false

        @JvmField
        var DEFAULT_CORNER_RADIUS = 30.0f

        @JvmField
        var directBgColor = 0xFF0f0f0f.toInt()

        private var strokePaint: Paint? = null

        private val readyListeners = mutableListOf<() -> Unit>()

        @JvmStatic
        fun runWhenBlurReady(callback: () -> Unit) {
            val bitmap = blurBitmap
            if (isPaused || (bitmap != null && !bitmap.isRecycled)) {
                callback()
            } else {
                readyListeners.add(callback)
            }
        }

        @JvmStatic
        fun notifyBlurReady() {
            if (readyListeners.isEmpty()) return
            val pending = readyListeners.toList()
            readyListeners.clear()
            pending.forEach { it() }
        }

        @JvmStatic
        fun getStrokePaint(context: Context): Paint {
            var paint = strokePaint
            if (paint == null) {
                paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3.0f
                strokePaint = paint
            }
            val colorRes = if (ThemeModeState.isDarkMode()) R.color.colorPirmLight else R.color.colorPirmDark
            val color = ContextCompat.getColor(context, colorRes)
            if (paint.color != color) paint.color = color
            return paint
        }
    }
}
