package com.omarea.common.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
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

    private const val ENTRANCE_DURATION_MS = 360L
    private const val EXIT_DURATION_MS = 340L
    private const val SLIDE_FACTOR = 1.12f

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
        val colorHex: String?,
        val onConfirm: ((Activity) -> Unit)?
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
        onConfirm: ((Activity) -> Unit)? = null,
        onNoActivity: (() -> Unit)? = null
    ) {
        if (CurrentActivityHolder.get() == null) {
            onNoActivity?.invoke()
            return
        }
        mainHandler.post {
            queue.addLast(
                BannerRequest(title, message, type, position, icon, script, confirmText, cancelText, countdownSeconds, colorHex, onConfirm)
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
        val gradientColors: Pair<Int, Int> = if (customColor != null) {
            Pair(blend(customColor, Color.WHITE, 0.10f), blend(customColor, Color.BLACK, 0.14f))
        } else {
            val (startRes, endRes) = when (req.type) {
                BannerType.INFO -> Pair(R.color.banner_info_start, R.color.banner_info_end)
                BannerType.SUCCESS -> Pair(R.color.banner_success_start, R.color.banner_success_end)
                BannerType.WARNING -> Pair(R.color.banner_warning_start, R.color.banner_warning_end)
                BannerType.ERROR -> Pair(R.color.banner_error_start, R.color.banner_error_end)
            }
            Pair(activity.resources.getColor(startRes, activity.theme), activity.resources.getColor(endRes, activity.theme))
        }
        applyBannerBackground(bannerRoot, gradientColors.first, gradientColors.second)
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
        if (!script.isNullOrEmpty() || req.onConfirm != null) {
            actionsRow.visibility = View.VISIBLE
            val cancelLabel = if (!req.cancelText.isNullOrEmpty()) req.cancelText else activity.getString(R.string.kr_banner_cancel)
            confirmBtn.text = if (!req.confirmText.isNullOrEmpty()) req.confirmText else activity.getString(R.string.kr_banner_confirm)

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
            fun updateCancelLabel() {
                val remaining = remainingSeconds()
                cancelBtn.text = if (remaining > 0) "$cancelLabel (${remaining}s)" else cancelLabel
            }
            cancelBtn.gravity = Gravity.CENTER
            if (req.countdownSeconds > 0) {
                val widest = cancelBtn.paint.measureText("$cancelLabel (${req.countdownSeconds}s)")
                cancelBtn.minWidth = (widest + cancelBtn.paddingStart + cancelBtn.paddingEnd).toInt()
            }
            updateCancelLabel()

            if (deadlineElapsedMs != null) {
                val tick = object : Runnable {
                    override fun run() {
                        if (remainingSeconds() <= 0) {
                            animateAutoDismiss(bannerRoot) {
                                dismissCurrent()
                                showNext()
                            }
                        } else {
                            updateCancelLabel()
                            mainHandler.postDelayed(this, 1000L)
                        }
                    }
                }
                pendingRunnable = tick
                mainHandler.postDelayed(tick, 1000L)
            }

            confirmBtn.setOnClickListener {
                dismissCurrent()
                if (!script.isNullOrEmpty()) runScript(activity, req, script)
                req.onConfirm?.invoke(activity)
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

    private fun blend(from: Int, to: Int, ratio: Float): Int {
        val inv = 1f - ratio
        return Color.argb(
            Color.alpha(from),
            (Color.red(from) * inv + Color.red(to) * ratio).toInt(),
            (Color.green(from) * inv + Color.green(to) * ratio).toInt(),
            (Color.blue(from) * inv + Color.blue(to) * ratio).toInt()
        )
    }

    private fun applyBannerBackground(bannerRoot: View, startColor: Int, endColor: Int) {
        val density = bannerRoot.resources.displayMetrics.density
        bannerRoot.background = BannerBackgroundDrawable(startColor, endColor, 12f * density, density)
        bannerRoot.clipToOutline = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            bannerRoot.forceHasOverlappingRendering(false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            bannerRoot.outlineSpotShadowColor = endColor
            bannerRoot.outlineAmbientShadowColor = endColor
        }
    }

    private fun animateAutoDismiss(bannerRoot: View, onEnd: () -> Unit) {
        val distance = bannerRoot.width * SLIDE_FACTOR
        bannerRoot.animate()
            .translationX(distance)
            .alpha(0f)
            .setDuration(EXIT_DURATION_MS)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { onEnd() }
            .start()
    }

    private fun playEntranceAnimation(bannerRoot: View) {
        bannerRoot.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                bannerRoot.viewTreeObserver.removeOnPreDrawListener(this)
                bannerRoot.translationX = -bannerRoot.width * SLIDE_FACTOR
                bannerRoot.alpha = 0f
                bannerRoot.animate()
                    .translationX(0f)
                    .alpha(1f)
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