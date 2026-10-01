package com.tool.tree.ui

import android.view.animation.Interpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.exp

object SwipeBounceEffect {
    private const val MAX_PULL_DP = 56f

    fun maxPullPx(density: Float): Float = MAX_PULL_DP * density

    fun dampen(rawPull: Float, maxPull: Float): Float {
        if (maxPull <= 0f) return 0f
        return maxPull * (1f - exp(-rawPull / maxPull))
    }

    val bounceInterpolator: Interpolator = OvershootInterpolator(1.8f)
}
