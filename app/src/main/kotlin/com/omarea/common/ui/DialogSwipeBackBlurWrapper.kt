package com.omarea.common.ui

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.graphics.drawable.toDrawable

object DialogSwipeBackBlurWrapper {
    fun wrap(activity: Activity, window: Window): View? {
        val contentRoot = window.findViewById<ViewGroup>(android.R.id.content) ?: return null
        if (contentRoot.childCount == 0) return null
        val realRoot = contentRoot.getChildAt(0)

        val blurBitmap = if (DialogHelper.disableBlurBg) {
            null
        } else {
            FastBlurUtility.getDialogBlurBackground(activity)
        } ?: return null

        val originalLayoutParams = realRoot.layoutParams

        window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        contentRoot.removeView(realRoot)

        val blurImage = ImageView(activity).apply {
            setImageBitmap(blurBitmap)
            scaleType = ImageView.ScaleType.FIT_XY
        }

        val wrapper = FrameLayout(activity)
        wrapper.addView(blurImage, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        wrapper.addView(realRoot, originalLayoutParams)

        contentRoot.addView(wrapper, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        return wrapper
    }
}