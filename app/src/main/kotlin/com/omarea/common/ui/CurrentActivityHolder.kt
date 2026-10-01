package com.omarea.common.ui

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

object CurrentActivityHolder : Application.ActivityLifecycleCallbacks {
    private var current: WeakReference<Activity>? = null

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<(Activity) -> Unit>()

    fun get(): Activity? = current?.get()?.takeIf { !it.isFinishing && !it.isDestroyed }

    fun addListener(listener: (Activity) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (Activity) -> Unit) {
        listeners.remove(listener)
    }

    override fun onActivityResumed(activity: Activity) {
        current = WeakReference(activity)
        listeners.forEach { it(activity) }
    }

    override fun onActivityPaused(activity: Activity) {
        if (current?.get() == activity) {
            current = null
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
