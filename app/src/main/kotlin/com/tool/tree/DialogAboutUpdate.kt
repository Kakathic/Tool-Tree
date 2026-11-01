package com.tool.tree

import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListPopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import com.omarea.common.ui.DialogFullScreen
import com.tool.tree.ui.SpinnerPopupHelper
import java.io.File

class DialogAboutUpdate(
    darkMode: Boolean,
    private val knownInfo: AppUpdateInfo?,
    private val onResult: (AppUpdateCheckResult) -> Unit
) : DialogFullScreen(R.layout.dialog_about, darkMode) {

    private enum class UpdateMode { CHECK, UPDATE, INSTALL }

    private companion object {
        const val REQUEST_WALLPAPER = 4101
    }

    init {
        isCancelable = true
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val logBuilder = SpannableStringBuilder()

    private lateinit var logText: TextView
    private lateinit var updateBtn: Button
    private lateinit var updateProgress: ProgressBar
    private lateinit var updatePercent: TextView
    private lateinit var wallpaperPath: EditText
    private lateinit var wallpaperChoose: TextView
    private lateinit var wallpaperRemove: TextView
    private lateinit var wallpaperLabel: TextView
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

        val versionName = runCatching { activity.packageManager.getPackageInfo(activity.packageName, 0).versionName }.getOrNull() ?: "1.0.0"
        view.findViewById<TextView>(R.id.about_version).text = versionName

        view.findViewById<TextView>(R.id.appliction_authorText).setOnClickListener {
            openLink(activity, "https://Kakathic.github.io/Tool-Tree/website/Information.html")
        }
        view.findViewById<TextView>(R.id.appliction_nameText).setOnClickListener {
            openLink(activity, "https://github.com/Kakathic/Tool-Tree")
        }

        bindSettings(view, activity)
        bindUpdate(view, activity)
        viewReady = true
    }

    override fun onDestroyView() {
        viewReady = false
        super.onDestroyView()
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (!closingToInstall && activeDownload == null && ::appContext.isInitialized) {
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
        val themeConfig = ThemeConfig(activity)

        blurRow = view.findViewById(R.id.blur_disable_row)
        blurLabel = view.findViewById(R.id.blur_disable_label)
        blurCheck = view.findViewById(R.id.blur_disable_check)
        directRow = view.findViewById(R.id.direct_bg_row)
        directLabel = view.findViewById(R.id.direct_bg_label)
        directCheck = view.findViewById(R.id.direct_bg_check)
        wallpaperLabel = view.findViewById(R.id.wallpaper_label)
        wallpaperPath = view.findViewById(R.id.wallpaper_path)
        wallpaperChoose = view.findViewById(R.id.wallpaper_choose)
        wallpaperRemove = view.findViewById(R.id.wallpaper_remove)

        blurCheck.isChecked = ThemeModeState.isBlurDisabled(activity)
        directCheck.isChecked = ThemeModeState.isDirectBgEnabled(activity)
        wallpaperPath.setText(ThemeModeState.getCustomWallpaperPath(activity).orEmpty())
        updateOptionStates(themeConfig.getThemeMode())

        blurRow.setOnClickListener { if (blurCheck.isEnabled) blurCheck.toggle() }
        directRow.setOnClickListener { if (directCheck.isEnabled) directCheck.toggle() }
        blurCheck.setOnCheckedChangeListener { _, checked ->
            ThemeModeState.setBlurDisabled(activity, checked)
            ThemeModeState.switchTheme(activity)
            activity.recreate()
        }
        directCheck.setOnCheckedChangeListener { _, checked ->
            ThemeModeState.setDirectBgEnabled(activity, checked)
            ThemeModeState.switchTheme(activity)
            activity.recreate()
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
            wallpaperPath.setText("")
            ThemeModeState.switchTheme(activity)
            activity.recreate()
        }
        wallpaperPath.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && wallpaperPath.isEnabled) {
                applyWallpaper(wallpaperPath.text?.toString().orEmpty())
                true
            } else {
                false
            }
        }

        bindThemeSelector(view, activity, themeConfig)
    }

    private fun updateOptionStates(themeLevel: Int) {
        val enabled = themeLevel >= 3
        val alpha = if (enabled) 0.7f else 0.35f
        blurCheck.isEnabled = enabled
        blurRow.isClickable = enabled
        blurLabel.alpha = alpha
        directCheck.isEnabled = enabled
        directRow.isClickable = enabled
        directLabel.alpha = alpha
        wallpaperPath.isEnabled = enabled
        wallpaperChoose.isEnabled = enabled
        wallpaperRemove.isEnabled = enabled
        wallpaperLabel.alpha = alpha
        wallpaperChoose.alpha = if (enabled) 1f else 0.35f
        wallpaperRemove.alpha = if (enabled) 1f else 0.35f
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
                ThemeModeState.switchTheme(activity)
                activity.recreate()
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
            wallpaperPath.setText(ThemeModeState.getCustomWallpaperPath(activity).orEmpty())
            ThemeModeState.switchTheme(activity)
            activity.recreate()
        } else {
            Toast.makeText(activity, R.string.about_wallpaper_invalid, Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindUpdate(view: View, activity: Activity) {
        logText = view.findViewById(R.id.update_log)
        logText.movementMethod = LinkMovementMethod.getInstance()
        updateBtn = view.findViewById(R.id.update_btn)
        updateProgress = view.findViewById(R.id.update_progress)
        updatePercent = view.findViewById(R.id.update_percent)

        updateBtn.setOnClickListener {
            if (activeDownload != null) {
                cancelDownload(activity)
                return@setOnClickListener
            }
            when (mode) {
                UpdateMode.CHECK -> startCheck(activity)
                UpdateMode.UPDATE -> startDownload(activity)
                UpdateMode.INSTALL -> openApk(activity, destFile)
            }
        }

        val info = knownInfo
        if (info != null) showAvailable(info, activity) else startCheck(activity)
    }

    private fun setMode(newMode: UpdateMode) {
        mode = newMode
        updateBtn.setText(
            when (newMode) {
                UpdateMode.CHECK -> R.string.about_update_recheck
                UpdateMode.UPDATE -> R.string.update_text
                UpdateMode.INSTALL -> R.string.app_update_btn_install
            }
        )
    }

    private fun resetLog() {
        logBuilder.clear()
        logText.text = logBuilder
    }

    private fun appendLog(line: CharSequence) {
        if (logBuilder.isNotEmpty()) logBuilder.append('\n')
        logBuilder.append(line)
        logText.text = logBuilder
    }

    private fun appendChangelog(activity: Activity, text: String?) {
        if (text != null) {
            appendLog(HtmlCompat.fromHtml(AppUpdateInstaller.markdownToHtml(text), HtmlCompat.FROM_HTML_MODE_LEGACY))
        } else {
            appendLog(activity.getString(R.string.app_update_changelog_fail))
        }
    }

    private fun showBusy(busy: Boolean, percent: Int? = null) {
        updateProgress.visibility = if (busy) View.VISIBLE else View.GONE
        if (percent != null) {
            updatePercent.visibility = View.VISIBLE
            updatePercent.text = "$percent%"
        } else {
            updatePercent.visibility = View.GONE
        }
    }

    private fun showAvailable(info: AppUpdateInfo, activity: Activity) {
        loadedInfo = info
        destFile = File(activity.cacheDir, info.apkFileName)
        resetLog()
        val size = if (info.apkSize > 0) " (" + AppUpdateInstaller.formatFileSize(info.apkSize) + ")" else ""
        appendLog(activity.getString(R.string.app_update_desc) + size)
        appendChangelog(activity, info.changelogText)
        setMode(if (destFile.exists() && destFile.length() > 0) UpdateMode.INSTALL else UpdateMode.UPDATE)
        updateBtn.isEnabled = true
        showBusy(false)
    }

    private fun startCheck(activity: Activity) {
        if (checking) return
        checking = true
        updateBtn.isEnabled = false
        showBusy(true)
        resetLog()
        appendLog(activity.getString(R.string.app_update_checking))

        Thread {
            val result = try {
                AppUpdateChecker.check(activity)
            } catch (_: Exception) {
                AppUpdateCheckResult.Failed
            }
            mainHandler.post {
                checking = false
                onResult(result)
                if (!viewReady || !isAdded) return@post
                applyResult(activity, result)
            }
        }.apply { isDaemon = true }.start()
    }

    private fun applyResult(activity: Activity, result: AppUpdateCheckResult) {
        when (result) {
            is AppUpdateCheckResult.Available -> showAvailable(result.info, activity)
            is AppUpdateCheckResult.UpToDate -> {
                loadedInfo = null
                resetLog()
                appendLog(activity.getString(R.string.app_update_latest))
                appendChangelog(activity, result.changelogText)
                setMode(UpdateMode.CHECK)
                updateBtn.isEnabled = true
                showBusy(false)
            }
            is AppUpdateCheckResult.Failed -> {
                resetLog()
                appendLog(activity.getString(R.string.app_update_check_fail))
                setMode(UpdateMode.CHECK)
                updateBtn.isEnabled = true
                showBusy(false)
            }
        }
    }

    private fun startDownload(activity: Activity) {
        val info = loadedInfo ?: return
        isCancelable = false
        setSwipeBackRuntimeEnabled(false)

        updateBtn.setText(R.string.btn_cancel)
        showBusy(true, 0)
        appendLog(activity.getString(R.string.about_update_downloading))

        val state = AppUpdateInstaller.DownloadState()
        activeDownload = state

        Thread {
            val error = AppUpdateInstaller.downloadApk(
                info.apkUrl,
                destFile,
                onConnectionOpened = { state.connection = it },
                onProgress = { downloaded, total ->
                    if (total > 0) {
                        mainHandler.post {
                            if (activeDownload === state && viewReady) {
                                showBusy(true, (downloaded * 100 / total).toInt())
                            }
                        }
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
                        showBusy(false)
                        setMode(UpdateMode.UPDATE)
                        appendLog(activity.getString(R.string.app_update_download_fail) + ": " + error)
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
        showBusy(false)
        setMode(UpdateMode.UPDATE)
        appendLog(activity.getString(R.string.app_update_download_cancelled))
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
                    showBusy(false)
                    setMode(UpdateMode.INSTALL)
                }
            }
        }.apply { isDaemon = true }.start()
    }
}
