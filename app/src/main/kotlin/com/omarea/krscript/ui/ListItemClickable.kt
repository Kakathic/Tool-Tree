package com.omarea.krscript.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import com.tool.tree.R
import com.omarea.krscript.config.IconPathAnalysis
import com.omarea.krscript.executor.ScriptEnvironmen
import com.omarea.krscript.model.ClickableNode

open class ListItemClickable(
    context: Context,
    layoutId: Int,
    private val config: ClickableNode,
    val card: Boolean = false
) : ListItemView(context, layoutId, config) {

    protected var mOnClickListener: OnClickListener? = null
    protected var mOnLongClickListener: OnLongClickListener? = null
    protected var shortcutIconView: View? = layout.findViewById(R.id.kr_shortcut_icon)
    protected var iconView: ImageView? = layout.findViewById(R.id.kr_icon)
    protected var extraIconView: ImageView? = layout.findViewById(R.id.kr_extra_icon)
    protected var extraBgView: ImageView? = layout.findViewById(R.id.kr_extra_bg)
    
    protected var iconDrawable: Drawable? = null

    private val analyzer = IconPathAnalysis()

    protected open fun allowLongClick(): Boolean = true

    fun setOnClickListener(onClickListener: OnClickListener): ListItemClickable {
        this.mOnClickListener = onClickListener
        return this
    }

    fun setOnLongClickListener(onLongClickListener: OnLongClickListener): ListItemClickable {
        this.mOnLongClickListener = onLongClickListener
        return this
    }

    fun triggerAction() {
        if (config.disabled) return
        this.mOnClickListener?.onClick(this)
    }

    // Mờ và chặn mọi thao tác bấm khi disabled; trả lại bình thường khi tắt
    private fun applyDisabledState() {
        val off = config.disabled
        layout.alpha = if (off) 0.4f else 1f
        layout.isClickable = !off
        layout.isLongClickable = !off
        layout.findViewById<View?>(R.id.kr_switch)?.isEnabled = !off
    }

    /** Kích thước gọn riêng cho thẻ (dùng ở trang Mục lục): icon 28dp, đệm 12dp, chữ 13sp, cao tối thiểu 56dp. */
    fun applyCompactSize() {
        val density = context.resources.displayMetrics.density
        fun dp(v: Float): Int = (v * density).toInt()

        layout.minimumHeight = dp(56f)
        layout.findViewById<View>(R.id.kr_layout_ac)?.let {
            it.setPadding(it.paddingLeft, dp(12f), it.paddingRight, dp(12f))
        }
        iconView?.let { v ->
            val lp = v.layoutParams
            lp.width = dp(28f)
            lp.height = dp(28f)
            (lp as? ViewGroup.MarginLayoutParams)?.marginEnd = dp(8f)
            v.layoutParams = lp
        }
        titleView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        for (id in intArrayOf(R.id.kr_widget, R.id.kr_download_ring)) {
            layout.findViewById<View>(id)?.let { v ->
                val lp = v.layoutParams
                lp.width = dp(34f)
                lp.height = dp(34f)
                v.layoutParams = lp
            }
        }
    }

    override fun updateViewByShell() {
        super.updateViewByShell()
        if (config.disabledSh.isNotEmpty()) {
            config.disabled = ScriptEnvironmen.executeResultRoot(context, config.disabledSh, config).trim() == "1"
        }
        applyDisabledState()
        applyIconPhotoBg()
    }

    private fun applyIconPhotoBg() {
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
                    (view as? TextBgImageView)?.setScaleMode(config.bgScale)
                    view.setImageDrawable(drawable)
                    view.visibility = View.VISIBLE
                }
            }
        }
    }

    init {
        title = config.title
        desc = config.desc
        summary = config.summary

        this.layout.setOnClickListener {
            if (!config.disabled) this.mOnClickListener?.onClick(this)
        }
        
        this.layout.setOnLongClickListener {
            if (!config.disabled && allowLongClick()) {
                this.mOnLongClickListener?.onLongClick(this)
                true
            } else {
                false
            }
        }

        applyIconPhotoBg()
        applyDisabledState()
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
