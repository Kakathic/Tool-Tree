package com.omarea.krscript.ui

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import com.tool.tree.R
import com.omarea.krscript.config.IconPathAnalysis
import com.omarea.krscript.model.ClickableNode

open class ListItemClickable(
    context: Context,
    layoutId: Int,
    config: ClickableNode
) : ListItemView(context, layoutId, config) {

    protected var mOnClickListener: OnClickListener? = null
    protected var mOnLongClickListener: OnLongClickListener? = null
    protected var shortcutIconView: View? = layout.findViewById(R.id.kr_shortcut_icon)
    protected var iconView: ImageView? = layout.findViewById(R.id.kr_icon)
    protected var extraIconView: ImageView? = layout.findViewById(R.id.kr_extra_icon)
    protected var extraBgView: ImageView? = layout.findViewById(R.id.kr_extra_bg)
    
    protected var iconDrawable: Drawable? = null

    private val allowShortcutConfig = this.key.isNotEmpty() && config.allowShortcut != false

    private val resetPressedRunnable = Runnable { layout.isPressed = false }

    private var dragHighlightView: View? = null

    protected open fun allowLongClick(): Boolean = allowShortcutConfig

    fun setOnClickListener(onClickListener: OnClickListener): ListItemClickable {
        this.mOnClickListener = onClickListener
        return this
    }

    fun setOnLongClickListener(onLongClickListener: OnLongClickListener): ListItemClickable {
        this.mOnLongClickListener = onLongClickListener
        return this
    }

    fun triggerAction() {
        this.mOnClickListener?.onClick(this)
    }

    init {
        title = config.title
        desc = config.desc
        summary = config.summary

        this.layout.setOnClickListener {
            this.mOnClickListener?.onClick(this)
        }
        
        this.layout.setOnLongClickListener {
            if (allowLongClick()) {
                this.mOnLongClickListener?.onLongClick(this)
                layout.removeCallbacks(resetPressedRunnable)
                dragHighlightView = layout
                layout.isPressed = true
                true
            } else {
                false
            }
        }

        // Hiệu ứng nhấn chỉ hiện 1 lần khi vừa chạm, không giữ theo thời gian nhấn giữ.
        // Sau khi long-press kích hoạt (dragHighlightView != null), theo dõi ngón tay di chuyển
        // sang item khác để tô sáng item đó (chỉ để xem, không tự bấm khi thả tay).
        this.layout.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.removeCallbacks(resetPressedRunnable)
                    view.postDelayed(resetPressedRunnable, ViewConfiguration.getPressedStateDuration().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    if (dragHighlightView != null) {
                        updateDragHighlight(event.rawX.toInt(), event.rawY.toInt())
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.removeCallbacks(resetPressedRunnable)
                    dragHighlightView?.isPressed = false
                    dragHighlightView = null
                }
            }
            false
        }

        shortcutIconView?.visibility = if (allowShortcutConfig) View.VISIBLE else View.GONE

        val analyzer = IconPathAnalysis()

        iconView?.let { view ->
            view.visibility = View.GONE
            if (config.iconPath.isNotEmpty()) {
                analyzer.loadIcon(context, config)?.let { drawable ->
                    iconDrawable = drawable
                    view.setImageDrawable(drawable)
                    view.visibility = View.VISIBLE
                    GifPlaybackHelper.bind(view, config.iconGifAutoplay, config.iconGifLoopCount)
                }
            }
        }

        extraIconView?.let { view ->
            view.visibility = View.GONE
            if (config.photoPath.isNotEmpty()) {
                analyzer.loadPhoto(context, config)?.let { drawable ->
                    view.setImageDrawable(drawable)
                    view.visibility = View.VISIBLE
                    applyPhotoRealSize(view, config.photoRealSize)
                    GifPlaybackHelper.bind(view, config.photoGifAutoplay, config.photoGifLoopCount)
                }
            }
        }

        extraBgView?.let { view ->
            view.visibility = View.GONE
            if (config.bgPath.isNotEmpty()) {
                analyzer.loadBg(context, config)?.let { drawable ->
                    view.setImageDrawable(drawable)
                    view.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun updateDragHighlight(rawX: Int, rawY: Int) {
        val parent = layout.parent as? ViewGroup ?: return
        var target: View? = null
        val location = IntArray(2)
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child.visibility != View.VISIBLE || !child.isLongClickable) continue
            child.getLocationOnScreen(location)
            val rect = Rect(location[0], location[1], location[0] + child.width, location[1] + child.height)
            if (rect.contains(rawX, rawY)) {
                target = child
                break
            }
        }
        if (target !== dragHighlightView) {
            dragHighlightView?.isPressed = false
            dragHighlightView = target
            target?.isPressed = true
        }
    }

    private fun applyPhotoRealSize(imageView: ImageView?, realSize: Boolean) {
        imageView ?: return
        val params = imageView.layoutParams as? RelativeLayout.LayoutParams ?: return
        val maxSize = imageView.context.resources.displayMetrics.widthPixels

        if (realSize) {
            imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
            imageView.adjustViewBounds = true
            imageView.maxWidth = maxSize
            imageView.maxHeight = maxSize

            params.width = maxSize
            params.height = maxSize
            params.addRule(RelativeLayout.CENTER_HORIZONTAL)
        } else {
            imageView.scaleType = ImageView.ScaleType.FIT_CENTER
            imageView.adjustViewBounds = true
            imageView.maxWidth = Int.MAX_VALUE
            imageView.maxHeight = Int.MAX_VALUE

            params.width = RelativeLayout.LayoutParams.MATCH_PARENT
            params.height = RelativeLayout.LayoutParams.WRAP_CONTENT
            params.removeRule(RelativeLayout.CENTER_HORIZONTAL)
        }
        
        imageView.layoutParams = params
    }

    interface OnClickListener {
        fun onClick(listItemView: ListItemClickable)
    }

    interface OnLongClickListener {
        fun onLongClick(listItemView: ListItemClickable)
    }
}
