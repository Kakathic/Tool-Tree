package com.omarea.common.ui

import android.app.Activity
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import com.omarea.krscript.NotiShellTaskLauncher
import com.omarea.krscript.model.RunnableNode
import com.tool.tree.R
import java.lang.ref.WeakReference

enum class BannerType { INFO, SUCCESS, WARNING, ERROR }
enum class BannerPosition { TOP, BOTTOM }

object BannerNotificationManager {
    private val mainHandler = Handler(Looper.getMainLooper())

    private const val FALLBACK_DURATION_MS = 3500L

    private const val ENTRANCE_DURATION_MS = 260L

    private data class BannerRequest(
        val title: String?,
        val message: String,
        val type: BannerType,
        val position: BannerPosition,
        val icon: String?,
        val script: String?,
        val confirmText: String?,
        val cancelText: String?,
        val countdownSeconds: Int,
        val colorHex: String?
    )

    private val queue = ArrayDeque<BannerRequest>()
    private var isShowing = false

    private var currentView: View? = null
    private var currentWindowManager: WindowManager? = null
    private var pendingRunnable: Runnable? = null

    private var activeRequest: BannerRequest? = null
    private var activeActivity: WeakReference<Activity>? = null

    private var deadlineElapsedMs: Long? = null

    init {
        CurrentActivityHolder.addListener { newActivity ->
            mainHandler.post { migrateIfNeeded(newActivity) }
        }
    }

    fun show(
        title: String? = null,
        message: String,
        type: BannerType = BannerType.INFO,
        position: BannerPosition = BannerPosition.BOTTOM,
        icon: String? = null,
        script: String? = null,
        confirmText: String? = null,
        cancelText: String? = null,
        countdownSeconds: Int = 5,
        colorHex: String? = null,
        onNoActivity: (() -> Unit)? = null
    ) {
        if (CurrentActivityHolder.get() == null) {
            onNoActivity?.invoke()
            return
        }
        mainHandler.post {
            queue.addLast(
                BannerRequest(title, message, type, position, icon, script, confirmText, cancelText, countdownSeconds, colorHex)
            )
            if (!isShowing) showNext()
        }
    }

    private fun showNext() {
        val req = queue.removeFirstOrNull()
        if (req == null) {
            isShowing = false
            return
        }
        val activity = CurrentActivityHolder.get()
        if (activity == null) {
            showNext()
            return
        }
        isShowing = true
        try {
            showOn(activity, req)
        } catch (e: Exception) {
            showNext()
        }
    }

    private fun showOn(activity: Activity, req: BannerRequest, reuseDeadline: Boolean = false) {
        val view = LayoutInflater.from(activity).inflate(R.layout.banner_notification, null, false)

        val bannerRoot = view.findViewById<View>(R.id.banner_root)
        val icon = view.findViewById<ImageView>(R.id.banner_icon)
        val titleView = view.findViewById<TextView>(R.id.banner_title)
        val messageView = view.findViewById<TextView>(R.id.banner_message)
        val actionsRow = view.findViewById<View>(R.id.banner_actions)
        val confirmBtn = view.findViewById<TextView>(R.id.banner_btn_confirm)
        val cancelBtn = view.findViewById<TextView>(R.id.banner_btn_cancel)

        val customColor = req.colorHex?.let {
            try {
                android.graphics.Color.parseColor(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
        val resolvedColor = customColor ?: run {
            val colorRes = when (req.type) {
                BannerType.INFO -> R.color.banner_info
                BannerType.SUCCESS -> R.color.banner_success
                BannerType.WARNING -> R.color.banner_warning
                BannerType.ERROR -> R.color.banner_error
            }
            activity.resources.getColor(colorRes, activity.theme)
        }
        (bannerRoot.background?.mutate() as? GradientDrawable)?.setColor(resolvedColor)
        icon.setImageDrawable(resolveIcon(activity, req.icon))

        BannerSwipeDismissHelper(activity, bannerRoot) {
            dismissCurrent()
            showNext()
        }

        if (!req.title.isNullOrEmpty()) {
            titleView.text = req.title
            titleView.visibility = View.VISIBLE
        } else {
            titleView.visibility = View.GONE
        }
        messageView.text = req.message

        val density = activity.resources.displayMetrics.density
        val windowManager = activity.windowManager
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        val anchorWindow = TopWindowHolder.current() ?: activity.window
        layoutParams.token = anchorWindow.decorView.windowToken
        when (req.position) {
            BannerPosition.TOP -> {
                layoutParams.gravity = Gravity.TOP
                layoutParams.y = (62 * density).toInt()
            }
            BannerPosition.BOTTOM -> {
                layoutParams.gravity = Gravity.BOTTOM
                layoutParams.y = (81 * density).toInt()
            }
        }

        currentView = view
        currentWindowManager = windowManager
        activeRequest = req
        activeActivity = WeakReference(activity)
        windowManager.addView(view, layoutParams)
        playEntranceAnimation(bannerRoot)

        val script = req.script
        if (!script.isNullOrEmpty()) {
            actionsRow.visibility = View.VISIBLE
            cancelBtn.text = if (!req.cancelText.isNullOrEmpty()) req.cancelText else activity.getString(R.string.kr_banner_cancel)
            val confirmLabel = if (!req.confirmText.isNullOrEmpty()) req.confirmText else activity.getString(R.string.kr_banner_confirm)

            if (!reuseDeadline) {
                deadlineElapsedMs = if (req.countdownSeconds > 0) {
                    SystemClock.elapsedRealtime() + req.countdownSeconds * 1000L
                } else {
                    null
                }
            }

            fun remainingSeconds(): Int {
                val deadline = deadlineElapsedMs ?: return 0
                val ms = deadline - SystemClock.elapsedRealtime()
                return if (ms > 0) ((ms + 999) / 1000).toInt() else 0
            }
            fun updateConfirmLabel() {
                val remaining = remainingSeconds()
                confirmBtn.text = if (remaining > 0) "$confirmLabel (${remaining}s)" else confirmLabel
            }
            updateConfirmLabel()

            if (deadlineElapsedMs != null) {
                val tick = object : Runnable {
                    override fun run() {
                        if (remainingSeconds() <= 0) {
                            animateAutoDismiss(bannerRoot) {
                                dismissCurrent()
                                showNext()
                            }
                        } else {
                            updateConfirmLabel()
                            mainHandler.postDelayed(this, 1000L)
                        }
                    }
                }
                pendingRunnable = tick
                mainHandler.postDelayed(tick, 1000L)
            }

            confirmBtn.setOnClickListener {
                dismissCurrent()
                runScript(activity, req, script)
                showNext()
            }
            cancelBtn.setOnClickListener {
                dismissCurrent()
                showNext()
            }
        } else {
            actionsRow.visibility = View.GONE
            if (!reuseDeadline) {
                val autoDismissMs = if (req.countdownSeconds > 0) req.countdownSeconds * 1000L else FALLBACK_DURATION_MS
                deadlineElapsedMs = SystemClock.elapsedRealtime() + autoDismissMs
            }
            val delay = (deadlineElapsedMs!! - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            val dismiss = Runnable {
                animateAutoDismiss(bannerRoot) {
                    dismissCurrent()
                    showNext()
                }
            }
            pendingRunnable = dismiss
            mainHandler.postDelayed(dismiss, delay)
        }
    }

    private fun detachView() {
        pendingRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingRunnable = null
        val view = currentView ?: return
        val windowManager = currentWindowManager
        currentView = null
        currentWindowManager = null
        try {
            windowManager?.removeViewImmediate(view)
        } catch (e: Exception) {
        }
    }

    private fun animateAutoDismiss(bannerRoot: View, onEnd: () -> Unit) {
        val screenWidthPx = bannerRoot.resources.displayMetrics.widthPixels
        val distance = (bannerRoot.width + screenWidthPx).toFloat()
        bannerRoot.animate()
            .translationX(distance)
            .alpha(0f)
            .setDuration(180L)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { onEnd() }
            .start()
    }

    private fun playEntranceAnimation(bannerRoot: View) {
        bannerRoot.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                bannerRoot.viewTreeObserver.removeOnPreDrawListener(this)
                val screenWidthPx = bannerRoot.resources.displayMetrics.widthPixels
                val startDistance = (bannerRoot.width + screenWidthPx).toFloat()
                bannerRoot.translationX = -startDistance
                bannerRoot.animate()
                    .translationX(0f)
                    .setDuration(ENTRANCE_DURATION_MS)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
                return true
            }
        })
    }

    private fun dismissCurrent() {
        detachView()
        activeRequest = null
        activeActivity = null
        deadlineElapsedMs = null
    }

    private fun migrateIfNeeded(newActivity: Activity) {
        val req = activeRequest ?: return
        if (currentView == null) return
        val prevActivity = activeActivity?.get()
        if (prevActivity === newActivity) return
        detachView()
        try {
            showOn(newActivity, req, reuseDeadline = true)
        } catch (e: Exception) {
            dismissCurrent()
            showNext()
        }
    }

    private fun runScript(activity: Activity, req: BannerRequest, script: String) {
        val nodeInfo = RunnableNode("").apply {
            title = if (!req.title.isNullOrEmpty()) req.title else req.message
            shell = RunnableNode.shellModeBgTask
            interruptable = true
        }
        NotiShellTaskLauncher.startTask(activity.applicationContext, script, nodeInfo)
    }

    private fun resolveIcon(activity: Activity, name: String?): android.graphics.drawable.Drawable? {
        if (!name.isNullOrEmpty()) {
            if (name.startsWith("/") || name.startsWith("file://")) {
                try {
                    val path = name.removePrefix("file://")
                    val file = java.io.File(path)
                    if (file.exists() && file.isFile) {
                        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                        if (bitmap != null) {
                            return android.graphics.drawable.BitmapDrawable(activity.resources, bitmap)
                        }
                    }
                } catch (e: Exception) {
                }
            } else {
                try {
                    var resId = activity.resources.getIdentifier(name, "drawable", activity.packageName)
                    if (resId == 0) {
                        resId = activity.resources.getIdentifier(name, "mipmap", activity.packageName)
                    }
                    if (resId != 0) {
                        return androidx.core.content.ContextCompat.getDrawable(activity, resId)
                    }
                } catch (e: Exception) {
                }
            }
        }
        return try {
            activity.packageManager.getApplicationIcon(activity.packageName)
        } catch (e: Exception) {
            null
        }
    }
}