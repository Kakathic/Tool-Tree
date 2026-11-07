package com.omarea.krscript.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.core.graphics.ColorUtils
import com.tool.tree.R
import com.omarea.krscript.config.IconPathAnalysis
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
        this.mOnClickListener?.onClick(this)
    }

    override fun updateViewByShell() {
        super.updateViewByShell()
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

        if (card) placeCardWidget(iconView?.visibility == View.VISIBLE)

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
            this.mOnClickListener?.onClick(this)
        }
        
        this.layout.setOnLongClickListener {
            if (allowLongClick()) {
                this.mOnLongClickListener?.onLongClick(this)
                true
            } else {
                false
            }
        }

        applyIconPhotoBg()
    }

    // Thẻ lưới: có icon thì widget nhỏ ở cuối dòng tiêu đề; không icon thì widget nằm trong khung bo ở góc trái trên
    private fun placeCardWidget(hasIcon: Boolean) {
        val box = layout.findViewById<ViewGroup>(R.id.kr_widget_box) ?: return
        val top = layout.findViewById<ViewGroup>(R.id.kr_card_top) ?: return
        val titleRow = layout.findViewById<ViewGroup>(R.id.kr_card_title_row) ?: return
        val density = context.resources.displayMetrics.density
        val target = if (hasIcon) titleRow else top
        if (box.parent !== target) {
            (box.parent as? ViewGroup)?.removeView(box)
            target.addView(box)
        }
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        if (hasIcon) {
            params.marginStart = (6 * density).toInt()
            box.background = null
            box.minimumWidth = 0
            box.minimumHeight = 0
        } else {
            params.bottomMargin = (8 * density).toInt()
            val tv = TypedValue()
            context.theme.resolveAttribute(android.R.attr.colorAccent, tv, true)
            box.background = GradientDrawable().apply {
                cornerRadius = 10 * density
                setColor(ColorUtils.setAlphaComponent(tv.data, 0x26))
            }
            box.minimumWidth = (36 * density).toInt()
            box.minimumHeight = (36 * density).toInt()
        }
        box.layoutParams = params
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
