package com.tool.tree

import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ListPopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import com.omarea.common.ui.DialogFullScreen
import com.omarea.krscript.ui.DownloadProgressRing
import com.omarea.krscript.ui.ListItemDownload
import com.tool.tree.ui.SpinnerPopupHelper
import java.io.File

class DialogAboutUpdate(
    darkMode: Boolean = false,
    private val knownInfo: AppUpdateInfo? = null,
    private val onResult: (AppUpdateCheckResult) -> Unit = {}
) : DialogFullScreen(R.layout.dialog_about, darkMode) {

    private enum class UpdateMode { CHECK, UPDATE, INSTALL }

    private companion object {
        const val REQUEST_WALLPAPER = 4101
        const val ABOUT_DESC_RESTORE_DELAY_MS = 5000L
        const val CHECKING_MIN_VISIBLE_MS = 800L
        const val ABOUT_DESC_FADE_OUT_MS = 150L
        const val ABOUT_DESC_FADE_IN_MS = 200L
    }

    init {
        isCancelable = true
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val logBuilder = SpannableStringBuilder()

    private lateinit var aboutDesc: TextView
    private lateinit var logText: TextView
    private lateinit var updateCard: View
    private lateinit var updateItem: View
    private lateinit var itemIcon: ImageView
    private lateinit var itemTitle: TextView
    private lateinit var itemDesc: TextView
    private lateinit var itemWidget: ImageView
    private lateinit var itemRing: DownloadProgressRing
    private lateinit var wallpaperRow: View
    private lateinit var wallpaperPath: TextView
    private lateinit var wallpaperChoose: TextView
    private lateinit var wallpaperRemove: TextView
    private lateinit var blurRow: View
    private lateinit var blurLabel: TextView
    private lateinit var blurCheck: CheckBox
    private lateinit var directRow: View
    private lateinit var directLabel: TextView
    private lateinit var directCheck: CheckBox
    private lateinit var destFile: File
    private lateinit var appContext: android.content.Context

    private var viewReady = false
    private var checking = false
    private var mode = UpdateMode.CHECK
    private var loadedInfo: AppUpdateInfo? = knownInfo
    private var activeDownload: AppUpdateInstaller.DownloadState? = null
    private var closingToInstall = false
    private var closingForRecreate = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setWindowAnimations(R.style.windowAnim2)
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = requireActivity()
        appContext = activity.applicationContext
        destFile = File(activity.cacheDir, AppUpdateChecker.APK_FILE_NAME)

        view.findViewById<View>(R.id.about_back).setOnClickListener {
            if (activeDownload == null) closeView()
        }

        val donateText = view.findViewById<TextView>(R.id.about_donate)
        val telegramText = view.findViewById<TextView>(R.id.about_telegram)
        donateText.paintFlags = donateText.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        telegramText.paintFlags = telegramText.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        donateText.setOnClickListener {
            openLink(activity, "https://Kakathic.github.io/Tool-Tree/website/Information.html")
        }
        telegramText.setOnClickListener {
            openLink(activity, "https://t.me/tool_tree")
        }
        view.findViewById<TextView>(R.id.appliction_nameText).setOnClickListener {
            openLink(activity, "https://github.com/Kakathic/Tool-Tree")
        }

        bindSettings(view, activity)
        bindUpdate(view, activity)
        viewReady = true
    }

    private val restoreAboutDescRunnable = Runnable { restoreAboutDesc() }

    private fun restoreAboutDesc() {
        mainHandler.removeCallbacks(restoreAboutDescRunnable)
        if (!viewReady || !::aboutDesc.isInitialized) return
        setAboutDescAnimated(R.string.appliction_desc)
    }

    private fun setAboutDescAnimated(resId: Int) {
        val target = getString(resId)
        aboutDesc.animate().cancel()
        if (aboutDesc.text.toString() == target) {
            aboutDesc.alpha = 1f
            return
        }
        aboutDesc.animate()
            .alpha(0f)
            .setDuration(ABOUT_DESC_FADE_OUT_MS)
            .withEndAction {
                aboutDesc.text = target
                aboutDesc.animate().alpha(1f).setDuration(ABOUT_DESC_FADE_IN_MS).start()
            }
            .start()
    }

    private fun showAboutDescTemp(resId: Int, restoreAfterMs: Long = 0L) {
        mainHandler.removeCallbacks(restoreAboutDescRunnable)
        if (!::aboutDesc.isInitialized) return
        setAboutDescAnimated(resId)
        if (restoreAfterMs > 0L) mainHandler.postDelayed(restoreAboutDescRunnable, restoreAfterMs)
    }

    override fun onDestroyView() {
        mainHandler.removeCallbacks(restoreAboutDescRunnable)
        if (::aboutDesc.isInitialized) aboutDesc.animate().cancel()
        viewReady = false
        super.onDestroyView()
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (!closingToInstall && !closingForRecreate && activeDownload == null && ::appContext.isInitialized) {
            loadedInfo?.let { AppUpdateConfig(appContext).setDismissedSha256(it.sha256) }
        }
        super.onDismiss(dialog)
    }

    private fun openLink(activity: Activity, url: String) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun bindSettings(view: View, activity: Activity) {
        val themeConfig = ThemeConfig(activity.applicationContext)

        blurRow = view.findViewById(R.id.blur_disable_row)
        blurLabel = view.findViewById(R.id.blur_disable_label)
        blurCheck = view.findViewById(R.id.blur_disable_check)
        directRow = view.findViewById(R.id.direct_bg_row)
        directLabel = view.findViewById(R.id.direct_bg_label)
        directCheck = view.findViewById(R.id.direct_bg_check)
        wallpaperRow = view.findViewById(R.id.wallpaper_row)
        wallpaperPath = view.findViewById(R.id.wallpaper_path)
        wallpaperChoose = view.findViewById(R.id.wallpaper_choose)
        wallpaperRemove = view.findViewById(R.id.wallpaper_remove)

        blurCheck.isChecked = ThemeModeState.isBlurDisabled(activity)
        directCheck.isChecked = ThemeModeState.isDirectBgEnabled(activity)
        refreshWallpaperRow(activity)
        updateOptionStates(themeConfig.getThemeMode())

        blurRow.setOnClickListener { if (blurCheck.isEnabled) blurCheck.toggle() }
        directRow.setOnClickListener { if (directCheck.isEnabled) directCheck.toggle() }
        blurCheck.setOnCheckedChangeListener { _, checked ->
            ThemeModeState.setBlurDisabled(activity, checked)
            applyThemeChange(activity)
        }
        directCheck.setOnCheckedChangeListener { _, checked ->
            ThemeModeState.setDirectBgEnabled(activity, checked)
            applyThemeChange(activity)
        }

        wallpaperChoose.setOnClickListener {
            if (!wallpaperChoose.isEnabled) return@setOnClickListener
            startActivityForResult(
                Intent(activity, ActivityFileSelector::class.java).apply {
                    putExtra("mode", ActivityFileSelector.MODE_FILE)
                    putExtra("extension", "jpg,jpeg,png,webp")
                },
                REQUEST_WALLPAPER
            )
        }
        wallpaperRemove.setOnClickListener {
            if (!wallpaperRemove.isEnabled) return@setOnClickListener
            ThemeModeState.clearCustomWallpaper(activity)
            applyThemeChange(activity)
        }
        bindThemeSelector(view, activity, themeConfig)
    }

    private fun applyThemeChange(activity: Activity) {
        ThemeModeState.switchTheme(activity)
        MainActivity.reopenAbout = true
        closingForRecreate = true
        runCatching { dismissNow() }
        activity.recreate()
    }

    private fun refreshWallpaperRow(activity: Activity) {
        val hasCustom = ThemeModeState.getCustomWallpaperPath(activity) != null
        wallpaperPath.setText(if (hasCustom) R.string.about_wallpaper_custom else R.string.about_wallpaper_hint)
        wallpaperRemove.visibility = if (hasCustom) View.VISIBLE else View.GONE
    }

    private fun updateOptionStates(themeLevel: Int) {
        val visibility = if (themeLevel >= 3) View.VISIBLE else View.GONE
        wallpaperRow.visibility = visibility
        blurRow.visibility = visibility
        directRow.visibility = visibility
    }

    private fun bindThemeSelector(view: View, activity: Activity, themeConfig: ThemeConfig) {
        val themeSelector = view.findViewById<TextView>(R.id.theme_selector)
        val themeNames = listOf(
            getString(R.string.theme_system_default), getString(R.string.theme_dark),
            getString(R.string.theme_light), getString(R.string.theme_wallpaper_system),
            getString(R.string.theme_wallpaper_dark), getString(R.string.theme_wallpaper_light)
        )

        themeSelector.text = themeNames[themeConfig.getThemeMode().coerceIn(0, themeNames.size - 1)]
        themeSelector.setOnClickListener {
            val popup = ListPopupWindow(activity)
            popup.anchorView = themeSelector
            popup.setAdapter(ArrayAdapter(activity, R.layout.kr_spinner_dropdown, themeNames))
            val background = ContextCompat.getDrawable(activity, R.drawable.kr_spinner_popup_bg)
            popup.setBackgroundDrawable(background)
            popup.setOnItemClickListener { _, _, position, _ ->
                themeConfig.setThemeMode(position)
                themeSelector.text = themeNames[position]
                updateOptionStates(position)
                popup.dismiss()
                applyThemeChange(activity)
            }
            val inflater = LayoutInflater.from(activity)
            val itemViews = themeNames.map { name ->
                inflater.inflate(R.layout.kr_spinner_dropdown, themeSelector.parent as? ViewGroup, false).apply {
                    findViewById<TextView>(R.id.text).text = name
                }
            }
            val minWidthPx = (resources.displayMetrics.density * 220).toInt()
            SpinnerPopupHelper.applyWidthAndPosition(
                popup, themeSelector, itemViews, background, minWidthPx, alignRight = true
            )
            popup.show()
            SpinnerPopupHelper.applyRoundedClip(popup, resources.getDimension(R.dimen.kr_spinner_popup_radius))
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_WALLPAPER && resultCode == Activity.RESULT_OK) {
            data?.getStringExtra("file")?.let { applyWallpaper(it) }
        }
    }

    private fun applyWallpaper(path: String) {
        val activity = activity ?: return
        if (path.isBlank()) return
        if (ThemeModeState.setCustomWallpaper(activity, path)) {
            applyThemeChange(activity)
        } else {
            Toast.makeText(activity, R.string.about_wallpaper_invalid, Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindUpdate(view: View, activity: Activity) {
        aboutDesc = view.findViewById(R.id.about_desc)
        updateCard = view.findViewById(R.id.update_card)
        updateItem = view.findViewById(R.id.update_item)
        itemIcon = view.findViewById(R.id.kr_icon)
        itemTitle = view.findViewById(R.id.kr_title)
        itemDesc = view.findViewById(R.id.kr_desc)
        itemWidget = view.findViewById(R.id.kr_widget)
        itemRing = view.findViewById(R.id.kr_download_ring)
        logText = view.findViewById(R.id.update_log)

        runCatching { itemIcon.setImageDrawable(activity.packageManager.getApplicationIcon(activity.applicationInfo)) }
        itemIcon.imageTintList = null
        itemTitle.setText(R.string.update_text)
        itemDesc.visibility = View.VISIBLE

        logText.setTextIsSelectable(true)
        logText.movementMethod = LinkMovementMethod.getInstance()
        logText.setOnClickListener { }

        updateItem.setOnClickListener {
            if (activeDownload != null) {
                cancelDownload(activity)
                return@setOnClickListener
            }
            if (checking) return@setOnClickListener
            when (mode) {
                UpdateMode.CHECK -> startCheck(activity)
                UpdateMode.UPDATE -> startDownload(activity)
                UpdateMode.INSTALL -> openApk(activity, destFile)
            }
        }

        showChangelog(AppUpdateChecker.cachedChangelog(activity))

        val info = knownInfo
        if (info != null) showAvailable(info, activity) else startCheck(activity)
    }

    private fun setMode(newMode: UpdateMode) {
        mode = newMode
        itemWidget.setImageResource(
            when (newMode) {
                UpdateMode.CHECK -> R.drawable.ic_update
                UpdateMode.UPDATE -> R.drawable.kr_download
                UpdateMode.INSTALL -> R.drawable.download_done
            }
        )
    }

    private fun setUpdateVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        updateCard.visibility = v
    }

    private fun setBusy(busy: Boolean) {
        itemRing.visibility = if (busy) View.VISIBLE else View.GONE
        itemWidget.visibility = if (busy) View.GONE else View.VISIBLE
        if (busy) itemRing.setIndeterminate(true)
    }

    private fun resetLog() {
        logBuilder.clear()
        logText.text = ""
    }

    private fun appendLog(line: CharSequence) {
        if (logBuilder.isNotEmpty()) logBuilder.append('\n')
        logBuilder.append(line)
        logText.text = logBuilder
    }

    private fun showChangelog(text: String?) {
        if (text.isNullOrBlank()) return
        resetLog()
        appendLog(HtmlCompat.fromHtml(AppUpdateInstaller.markdownToHtml(text), HtmlCompat.FROM_HTML_MODE_LEGACY))
    }

    private fun showDownloadProgress(downloaded: Long, total: Long, startTime: Long) {
        val elapsed = (SystemClock.elapsedRealtime() - startTime).coerceAtLeast(1L)
        itemDesc.text = ListItemDownload.formatProgress(downloaded, total, downloaded * 1000.0 / elapsed)
        if (total > 0) {
            itemRing.setIndeterminate(false)
            itemRing.setProgress(downloaded * 100f / total)
        } else {
            itemRing.setIndeterminate(true)
        }
    }

    private fun showAvailable(info: AppUpdateInfo, activity: Activity) {
        loadedInfo = info
        destFile = File(activity.cacheDir, info.apkFileName)
        setUpdateVisible(true)
        val size = if (info.apkSize > 0) " (" + AppUpdateInstaller.formatFileSize(info.apkSize) + ")" else ""
        itemDesc.text = activity.getString(R.string.app_update_desc) + size
        showChangelog(info.changelogText)
        setMode(if (destFile.exists() && destFile.length() > 0) UpdateMode.INSTALL else UpdateMode.UPDATE)
        setBusy(false)
    }

    private fun startCheck(activity: Activity) {
        if (checking) return
        checking = true
        setUpdateVisible(false)
        setBusy(true)
        itemDesc.text = activity.getString(R.string.app_update_checking)
        showAboutDescTemp(R.string.app_update_checking)
        val checkStart = SystemClock.elapsedRealtime()

        Thread {
            val result = try {
                AppUpdateChecker.checkCached(activity)
            } catch (_: Exception) {
                AppUpdateCheckResult.Failed
            }
            val remaining = (CHECKING_MIN_VISIBLE_MS - (SystemClock.elapsedRealtime() - checkStart)).coerceAtLeast(0L)
            mainHandler.postDelayed({
                checking = false
                onResult(result)
                if (!viewReady || !isAdded) return@postDelayed
                applyResult(activity, result)
            }, remaining)
        }.apply { isDaemon = true }.start()
    }

    private fun applyResult(activity: Activity, result: AppUpdateCheckResult) {
        when (result) {
            is AppUpdateCheckResult.Available -> {
                restoreAboutDesc()
                showAvailable(result.info, activity)
            }
            is AppUpdateCheckResult.UpToDate -> {
                showAboutDescTemp(R.string.app_update_latest, ABOUT_DESC_RESTORE_DELAY_MS)
                loadedInfo = null
                setUpdateVisible(false)
                showChangelog(result.changelogText)
                setMode(UpdateMode.CHECK)
                setBusy(false)
            }
            is AppUpdateCheckResult.Failed -> {
                restoreAboutDesc()
                setUpdateVisible(false)
                showChangelog(AppUpdateChecker.cachedChangelog(activity))
                setMode(UpdateMode.CHECK)
                setBusy(false)
            }
        }
    }

    private fun startDownload(activity: Activity) {
        val info = loadedInfo ?: return
        isCancelable = false
        setSwipeBackRuntimeEnabled(false)

        setBusy(true)
        itemDesc.text = activity.getString(R.string.about_update_downloading)

        val state = AppUpdateInstaller.DownloadState()
        activeDownload = state
        val startTime = SystemClock.elapsedRealtime()

        Thread {
            val error = AppUpdateInstaller.downloadApk(
                info.apkUrl,
                destFile,
                onConnectionOpened = { state.connection = it },
                onProgress = { downloaded, total ->
                    mainHandler.post {
                        if (activeDownload === state && viewReady) showDownloadProgress(downloaded, total, startTime)
                    }
                }
            )
            mainHandler.post {
                if (state.cancelled) {
                    destFile.delete()
                    return@post
                }
                if (activeDownload === state) activeDownload = null

                if (error != null) {
                    destFile.delete()
                    if (isAdded && viewReady) {
                        isCancelable = true
                        setSwipeBackRuntimeEnabled(true)
                        setBusy(false)
                        setMode(UpdateMode.UPDATE)
                        itemDesc.text = activity.getString(R.string.app_update_download_fail) + ": " + error
                    }
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.app_update_download_fail) + ": " + error,
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    openApk(activity, destFile)
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun cancelDownload(activity: Activity) {
        val state = activeDownload ?: return
        isCancelable = true
        setSwipeBackRuntimeEnabled(true)
        activeDownload = null
        state.cancelled = true
        try {
            state.connection?.disconnect()
        } catch (_: Exception) {
        }
        setBusy(false)
        setMode(UpdateMode.UPDATE)
        itemDesc.text = activity.getString(R.string.app_update_download_cancelled)
    }

    private fun openApk(activity: Activity, file: File) {
        Thread {
            val installed = AppUpdateInstaller.installApkWithRoot(file)
            mainHandler.post {
                if (!installed) {
                    closingToInstall = true
                    if (isAdded) runCatching { dismissAllowingStateLoss() }
                    AppUpdateInstaller.openApkViaSystemInstaller(activity, file)
                } else if (viewReady) {
                    setBusy(false)
                    setMode(UpdateMode.INSTALL)
                }
            }
        }.apply { isDaemon = true }.start()
    }
}
