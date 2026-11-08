package com.tool.tree

import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.ListPopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import com.omarea.common.ui.DialogFullScreen
import com.omarea.krscript.WebLauncher
import com.omarea.krscript.ui.DownloadProgressRing
import com.omarea.krscript.ui.ListItemDownload
import com.tool.tree.ui.SpinnerPopupHelper
import java.io.File

class DialogAboutUpdate(
    private val darkMode: Boolean = false,
    private val knownInfo: AppUpdateInfo? = null,
    startOnUpdate: Boolean = false,
    private val onResult: (AppUpdateCheckResult) -> Unit = {}
) : DialogFullScreen(R.layout.dialog_about, darkMode) {

    private enum class UpdateMode { CHECK, UPDATE, INSTALL }

    private class ChangelogEntry(val version: String, val date: String, val items: List<String>)

    private companion object {
        const val REQUEST_WALLPAPER = 4101
        const val CHECKING_MIN_VISIBLE_MS = 800L
        const val PANEL_IN_MS = 220L
        const val TRIPLE_TAP_GAP_MS = 600L
        const val CHANGELOG_FIRST_CHUNK = 4
        const val CHANGELOG_NEXT_CHUNK = 3
        const val AUTHOR_URL = "https://github.com/Kakathic"
        const val PANEL_OUT_MS = 160L
        const val GITHUB_URL = "https://github.com/Kakathic/Tool-Tree"
        const val DONATE_URL = "https://Kakathic.github.io/Tool-Tree/website/Information.html"
        const val TELEGRAM_URL = "https://t.me/tool_tree"
        val VERSION_HEADER = Regex("""^\*\*\s*Version:?\s*([^\s(*]+)\s*(?:\(([^)]*)\))?\s*\*\*\s*$""", RegexOption.IGNORE_CASE)
    }

    init {
        isCancelable = true
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    // Mở thẳng màn Cập nhật (tự hiện lúc khởi động): chạm ngoài / vuốt = đóng hẳn, không lùi về Giới thiệu
    private val openedDirectly = startOnUpdate && knownInfo != null

    // About panel
    private lateinit var aboutPanel: View
    private lateinit var updateDot: View
    private lateinit var updateRowText: TextView
    private lateinit var wallpaperRow: View
    private lateinit var wallpaperPath: TextView
    private lateinit var blurRow: View
    private lateinit var blurCheck: CompoundButton
    private lateinit var directRow: View
    private lateinit var directCheck: CompoundButton
    private lateinit var themeSegments: List<TextView>
    private var textPrimary: ColorStateList? = null
    private var textSecondary: ColorStateList? = null

    // Update panel
    private lateinit var updatePanel: View
    private lateinit var updateTitle: TextView
    private lateinit var updateVersion: TextView
    private lateinit var updateRing: DownloadProgressRing
    private lateinit var updateAction: TextView
    private lateinit var updateMeta: TextView
    private lateinit var updateTimeline: ViewGroup
    private lateinit var updateLog: TextView
    private lateinit var destFile: File
    private lateinit var appContext: android.content.Context

    private var viewReady = false
    private var changelogText: String? = null
    private var renderedChangelog: String? = null
    private var latestVersion: String? = null
    private var renderGeneration = 0
    private var loadingCachedChangelog = false
    private var inUpdate = false
    private var checking = false
    private var mode = UpdateMode.CHECK
    private var loadedInfo: AppUpdateInfo? = knownInfo
    private var activeDownload: AppUpdateInstaller.DownloadState? = null
    private var closingToInstall = false
    private var closingForRecreate = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val themeRes = if (darkMode) R.style.dialog_full_screen_dark else R.style.dialog_full_screen_light
        val dialog = object : Dialog(requireActivity(), themeRes) {
            override fun onBackPressed() {
                if (!handleBack()) super.onBackPressed()
            }
        }
        dialog.window?.setWindowAnimations(R.style.windowAnim2)
        return dialog
    }

    // Trong màn Cập nhật: Back quay về Giới thiệu (đang tải thì bỏ qua)
    private fun handleBack(): Boolean {
        if (!inUpdate || openedDirectly) return false
        if (activeDownload == null) showPanel(false)
        return true
    }

    // Chạm ra ngoài hộp thoại
    private fun onOutsideTap() {
        if (activeDownload != null) return
        if (inUpdate && !openedDirectly) showPanel(false) else closeView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = requireActivity()
        appContext = activity.applicationContext
        destFile = File(activity.cacheDir, AppUpdateChecker.APK_FILE_NAME)

        aboutPanel = view.findViewById(R.id.about_panel)
        updatePanel = view.findViewById(R.id.update_panel)

        view.findViewById<View>(R.id.dialog_root).setOnClickListener { onOutsideTap() }

        view.findViewById<View>(R.id.about_version_row).setOnClickListener(tripleTap { openLink(activity, GITHUB_URL) })
        view.findViewById<View>(R.id.about_author_row).setOnClickListener(tripleTap { openLink(activity, AUTHOR_URL) })
        view.findViewById<View>(R.id.about_donate).setOnClickListener { openLink(activity, DONATE_URL) }
        view.findViewById<View>(R.id.about_telegram).setOnClickListener { openLink(activity, TELEGRAM_URL) }
        val versionName = runCatching { activity.packageManager.getPackageInfo(activity.packageName, 0).versionName }.getOrNull()
        view.findViewById<TextView>(R.id.about_version).text = versionName ?: ""

        bindSettings(view, activity)
        bindUpdate(view, activity)
        setupCardSize(view)
        if (openedDirectly) {
            inUpdate = true
            aboutPanel.visibility = View.GONE
            updatePanel.visibility = View.VISIBLE
            refreshSwipe()
            renderChangelog()
        }
        viewReady = true
    }

    override fun onDestroyView() {
        if (::aboutPanel.isInitialized) aboutPanel.animate().cancel()
        if (::updatePanel.isInitialized) updatePanel.animate().cancel()
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
            WebLauncher.open(activity, url)
        } catch (_: Exception) {
        }
    }

    private fun tripleTap(onTriple: () -> Unit): View.OnClickListener {
        var count = 0
        var lastTap = 0L
        return View.OnClickListener {
            val now = SystemClock.elapsedRealtime()
            count = if (now - lastTap <= TRIPLE_TAP_GAP_MS) count + 1 else 1
            lastTap = now
            if (count >= 3) {
                count = 0
                onTriple()
            }
        }
    }

    // ---------------------------------------------------------------- panels

    private fun showPanel(update: Boolean) {
        if (inUpdate == update) return
        inUpdate = update
        val from = if (update) aboutPanel else updatePanel
        val to = if (update) updatePanel else aboutPanel
        val dx = resources.displayMetrics.density * 32f * (if (update) 1f else -1f)

        from.animate().cancel()
        to.animate().cancel()
        to.alpha = 0f
        to.translationX = dx
        to.visibility = View.VISIBLE
        to.animate().alpha(1f).translationX(0f).setDuration(PANEL_IN_MS).start()
        if (update) to.post { if (viewReady && inUpdate) renderChangelog() }
        from.animate().alpha(0f).translationX(-dx).setDuration(PANEL_OUT_MS).withEndAction {
            from.alpha = 1f
            from.translationX = 0f
            if (from !== (if (inUpdate) updatePanel else aboutPanel)) from.visibility = View.GONE
        }.start()
        refreshSwipe()
    }

    // Vuốt để đóng: tắt khi đang tải, và ở màn Cập nhật mở từ Giới thiệu (vuốt sẽ đóng cả dialog)
    private fun refreshSwipe() {
        setSwipeBackRuntimeEnabled(activeDownload == null && (!inUpdate || openedDirectly))
    }

    // Dialog căn giữa; 2 màn có cùng chiều cao = chiều cao màn Giới thiệu (tối đa 82% màn hình)
    private fun setupCardSize(view: View) {
        val dm = resources.displayMetrics
        val cardWidth = minOf(dm.widthPixels - (48 * dm.density).toInt(), (400 * dm.density).toInt())
        val card = view.findViewById<View>(R.id.dialog_card)
        card.layoutParams = card.layoutParams.apply { width = cardWidth }

        aboutPanel.measure(
            View.MeasureSpec.makeMeasureSpec(cardWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val height = minOf(aboutPanel.measuredHeight, (dm.heightPixels * 0.82f).toInt())
        aboutPanel.layoutParams = aboutPanel.layoutParams.apply { this.height = height }
        updatePanel.layoutParams = updatePanel.layoutParams.apply { this.height = height }
    }

    private fun openUpdatePanel(activity: Activity) {
        showPanel(true)
        val info = loadedInfo
        when {
            checking -> renderCheckingUi(activity)
            activeDownload != null -> {}
            info != null -> showAvailable(info, activity)
            else -> startCheck(activity)
        }
    }

    // ---------------------------------------------------------------- about: theme

    private fun bindSettings(view: View, activity: Activity) {
        val themeConfig = ThemeConfig(activity.applicationContext)

        view.context.obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary, android.R.attr.textColorSecondary)).let {
            textPrimary = it.getColorStateList(0)
            textSecondary = it.getColorStateList(1)
            it.recycle()
        }

        blurRow = view.findViewById(R.id.blur_disable_row)
        blurCheck = view.findViewById(R.id.blur_disable_check)
        directRow = view.findViewById(R.id.direct_bg_row)
        directCheck = view.findViewById(R.id.direct_bg_check)
        wallpaperRow = view.findViewById(R.id.wallpaper_row)
        wallpaperPath = view.findViewById(R.id.wallpaper_path)
        themeSegments = listOf(R.id.theme_seg_0, R.id.theme_seg_1, R.id.theme_seg_2).map { view.findViewById<TextView>(it) }

        blurCheck.isChecked = ThemeModeState.isBlurDisabled(activity)
        directCheck.isChecked = ThemeModeState.isDirectBgEnabled(activity)
        refreshThemeUi(activity, themeConfig)

        blurRow.setOnClickListener { blurCheck.toggle() }
        directRow.setOnClickListener { directCheck.toggle() }
        blurCheck.setOnCheckedChangeListener { _, checked ->
            ThemeModeState.setBlurDisabled(activity, checked)
            applyThemeChange(activity)
        }
        directCheck.setOnCheckedChangeListener { _, checked ->
            ThemeModeState.setDirectBgEnabled(activity, checked)
            applyThemeChange(activity)
        }

        themeSegments.forEachIndexed { index, seg ->
            seg.setOnClickListener { setThemeBase(activity, themeConfig, index) }
        }
        wallpaperRow.setOnClickListener { showWallpaperMenu(activity, themeConfig) }
    }

    // Chế độ 0..2 = hệ thống/tối/sáng, 3..5 = cùng bộ đó nhưng dùng hình nền
    private fun setThemeBase(activity: Activity, themeConfig: ThemeConfig, base: Int) {
        val current = themeConfig.getThemeMode()
        val newMode = base + if (current >= 3) 3 else 0
        if (newMode == current) return
        themeConfig.setThemeMode(newMode)
        applyThemeChange(activity)
    }

    private fun refreshThemeUi(activity: Activity, themeConfig: ThemeConfig) {
        val mode = themeConfig.getThemeMode()
        val base = mode % 3
        val wallpaper = mode >= 3
        themeSegments.forEachIndexed { index, seg ->
            val selected = index == base
            seg.background = if (selected) ContextCompat.getDrawable(activity, R.drawable.about_seg_selected) else null
            seg.setTextColor(if (selected) textPrimary else textSecondary)
            seg.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        }
        val visibility = if (wallpaper) View.VISIBLE else View.GONE
        blurRow.visibility = visibility
        directRow.visibility = visibility
        view?.findViewById<View>(R.id.about_div3)?.visibility = visibility
        view?.findViewById<View>(R.id.about_div4)?.visibility = visibility
        val hasCustom = ThemeModeState.getCustomWallpaperPath(activity) != null
        wallpaperPath.setText(
            when {
                !wallpaper -> R.string.about_wallpaper_none
                hasCustom -> R.string.about_wallpaper_custom
                else -> R.string.about_wallpaper_hint
            }
        )
    }

    private fun showWallpaperMenu(activity: Activity, themeConfig: ThemeConfig) {
        val mode = themeConfig.getThemeMode()
        val base = mode % 3
        val wallpaper = mode >= 3
        val hasCustom = ThemeModeState.getCustomWallpaperPath(activity) != null

        val names = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()
        if (wallpaper) {
            names.add(getString(R.string.about_wallpaper_none))
            actions.add {
                themeConfig.setThemeMode(base)
                applyThemeChange(activity)
            }
        }
        if (!wallpaper || hasCustom) {
            names.add(getString(R.string.about_wallpaper_hint))
            actions.add {
                ThemeModeState.clearCustomWallpaper(activity)
                themeConfig.setThemeMode(base + 3)
                applyThemeChange(activity)
            }
        }
        names.add(getString(R.string.about_import_image))
        actions.add { pickWallpaper(activity) }

        val popup = ListPopupWindow(activity)
        popup.anchorView = wallpaperRow
        popup.setAdapter(ArrayAdapter(activity, R.layout.kr_spinner_dropdown, names))
        val background = ContextCompat.getDrawable(activity, R.drawable.kr_spinner_popup_bg)
        popup.setBackgroundDrawable(background)
        popup.setOnItemClickListener { _, _, position, _ ->
            popup.dismiss()
            actions[position]()
        }
        val inflater = LayoutInflater.from(activity)
        val itemViews = names.map { name ->
            inflater.inflate(R.layout.kr_spinner_dropdown, wallpaperRow.parent as? ViewGroup, false).apply {
                findViewById<TextView>(R.id.text).text = name
            }
        }
        val minWidthPx = (resources.displayMetrics.density * 220).toInt()
        SpinnerPopupHelper.applyWidthAndPosition(
            popup, wallpaperRow, itemViews, background, minWidthPx, alignRight = true
        )
        popup.show()
        SpinnerPopupHelper.applyRoundedClip(popup, resources.getDimension(R.dimen.kr_spinner_popup_radius))
    }

    @Suppress("DEPRECATION")
    private fun pickWallpaper(activity: Activity) {
        startActivityForResult(
            Intent(activity, ActivityFileSelector::class.java).apply {
                putExtra("mode", ActivityFileSelector.MODE_FILE)
                putExtra("extension", "jpg,jpeg,png,webp")
            },
            REQUEST_WALLPAPER
        )
    }

    private fun applyThemeChange(activity: Activity) {
        ThemeModeState.switchTheme(activity)
        MainActivity.reopenAbout = true
        closingForRecreate = true
        runCatching { dismissNow() }
        activity.recreate()
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
            val themeConfig = ThemeConfig(activity.applicationContext)
            val current = themeConfig.getThemeMode()
            if (current < 3) themeConfig.setThemeMode(current + 3)
            applyThemeChange(activity)
        } else {
            Toast.makeText(activity, R.string.about_wallpaper_invalid, Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------------------------------- update

    private fun bindUpdate(view: View, activity: Activity) {
        updateDot = view.findViewById(R.id.about_update_dot)
        updateRowText = view.findViewById(R.id.about_update_text)
        updateTitle = view.findViewById(R.id.update_title)
        updateVersion = view.findViewById(R.id.update_version)
        updateRing = view.findViewById(R.id.update_ring)
        updateAction = view.findViewById(R.id.update_action)
        updateMeta = view.findViewById(R.id.update_meta)
        updateTimeline = view.findViewById(R.id.update_timeline)
        updateLog = view.findViewById(R.id.update_log)

        updateLog.movementMethod = LinkMovementMethod.getInstance()

        view.findViewById<View>(R.id.about_update_row).setOnClickListener { openUpdatePanel(activity) }

        updateAction.setOnClickListener {
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

        updateMeta.text = ""

        val info = knownInfo
        // Không tự kiểm tra khi mở Giới thiệu: chỉ kiểm tra khi người dùng vào màn Cập nhật.
        // Chấm đỏ dựa trên kết quả MainActivity đã kiểm tra lúc khởi động (knownInfo)
        if (info != null) showAvailable(info, activity)
        refreshUpdateRow()
    }

    private fun refreshUpdateRow() {
        if (!::updateDot.isInitialized) return
        val available = loadedInfo != null
        updateDot.visibility = if (available) View.VISIBLE else View.GONE
        updateRowText.setText(if (available) R.string.update_text else R.string.about_check)
    }

    private fun setMode(newMode: UpdateMode) {
        mode = newMode
        renderAction()
    }

    private fun renderAction() {
        when {
            activeDownload != null -> {
                updateAction.setText(R.string.btn_cancel)
                updateAction.visibility = View.VISIBLE
            }
            checking -> updateAction.visibility = View.GONE
            else -> {
                updateAction.setText(
                    when (mode) {
                        UpdateMode.CHECK -> R.string.about_update_recheck
                        UpdateMode.UPDATE -> R.string.about_download
                        UpdateMode.INSTALL -> R.string.app_update_btn_install
                    }
                )
                updateAction.visibility = View.VISIBLE
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        updateRing.visibility = if (busy) View.VISIBLE else View.GONE
        if (busy) updateRing.setIndeterminate(true)
        renderAction()
    }

    private fun setVersionLabel(version: String?) {
        if (version.isNullOrBlank()) {
            updateVersion.visibility = View.GONE
        } else {
            updateVersion.text = version
            updateVersion.visibility = View.VISIBLE
        }
    }

    // ---- changelog (Version.md -> timeline)

    private fun parseChangelog(text: String): List<ChangelogEntry> {
        val result = ArrayList<ChangelogEntry>()
        var version: String? = null
        var date = ""
        var items = ArrayList<String>()
        fun flush() {
            version?.let { result.add(ChangelogEntry(it, date, items)) }
        }
        for (raw in text.lines()) {
            val line = raw.trim()
            val header = VERSION_HEADER.find(line)
            if (header != null) {
                flush()
                version = header.groupValues[1]
                date = normalizeDate(header.groupValues[2])
                items = ArrayList()
            } else if (version != null && (line.startsWith("+ ") || line.startsWith("- "))) {
                items.add(line.substring(2).trim())
            }
        }
        flush()
        return result
    }

    private fun normalizeDate(raw: String): String {
        val parts = raw.trim().split('-', '/', '.')
        if (parts.size != 3 || parts.any { it.isEmpty() || !it.all(Char::isDigit) }) return raw.trim()
        return parts[0].padStart(2, '0') + "/" + parts[1].padStart(2, '0') + "/" + parts[2]
    }

    // Lịch sử thay đổi chỉ được dựng khi màn Cập nhật hiện ra, và dựng dần từng đợt để không làm khựng dialog
    private fun setChangelog(text: String?) {
        if (!text.isNullOrBlank()) changelogText = text
        if (inUpdate) renderChangelog()
    }

    private fun applyVersionLabel() {
        setVersionLabel(if (loadedInfo != null) latestVersion else null)
    }

    private fun loadCachedChangelog() {
        if (loadingCachedChangelog) return
        loadingCachedChangelog = true
        val context = appContext
        Thread {
            val cached = AppUpdateChecker.cachedChangelog(context)
            mainHandler.post {
                loadingCachedChangelog = false
                if (cached != null && changelogText == null) {
                    changelogText = cached
                    if (viewReady && inUpdate) renderChangelog()
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun renderChangelog() {
        if (!::updateTimeline.isInitialized) return
        val changelog = changelogText
        if (changelog.isNullOrBlank()) {
            loadCachedChangelog()
            return
        }
        if (changelog == renderedChangelog) {
            applyVersionLabel()
            return
        }
        renderedChangelog = changelog
        val generation = ++renderGeneration
        val entries = parseChangelog(changelog)
        updateTimeline.removeAllViews()
        latestVersion = entries.firstOrNull()?.version
        applyVersionLabel()
        if (entries.isEmpty()) {
            updateTimeline.visibility = View.GONE
            updateLog.visibility = View.VISIBLE
            updateLog.text = HtmlCompat.fromHtml(AppUpdateInstaller.markdownToHtml(changelog), HtmlCompat.FROM_HTML_MODE_LEGACY)
            return
        }
        updateLog.visibility = View.GONE
        updateTimeline.visibility = View.VISIBLE
        addChangelogEntries(entries, 0, generation)
    }

    private fun addChangelogEntries(entries: List<ChangelogEntry>, from: Int, generation: Int) {
        if (generation != renderGeneration || !viewReady) return
        val to = minOf(from + (if (from == 0) CHANGELOG_FIRST_CHUNK else CHANGELOG_NEXT_CHUNK), entries.size)
        val inflater = LayoutInflater.from(updateTimeline.context)
        for (index in from until to) {
            val entry = entries[index]
            val row = inflater.inflate(R.layout.item_about_changelog, updateTimeline, false)
            val latest = index == 0
            row.findViewById<TextView>(R.id.cl_version).text = entry.version
            row.findViewById<TextView>(R.id.cl_date).apply {
                text = entry.date
                visibility = if (entry.date.isEmpty()) View.GONE else View.VISIBLE
            }
            row.findViewById<View>(R.id.cl_badge).visibility = if (latest) View.VISIBLE else View.GONE
            row.findViewById<View>(R.id.cl_dot).setBackgroundResource(
                if (latest) R.drawable.about_dot_latest else R.drawable.about_dot_old
            )
            row.findViewById<View>(R.id.cl_line).visibility = if (index == entries.lastIndex) View.GONE else View.VISIBLE
            row.findViewById<TextView>(R.id.cl_items).apply {
                val html = AppUpdateInstaller.markdownToHtml(entry.items.joinToString("\n"))
                text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY).trimEnd()
                movementMethod = LinkMovementMethod.getInstance()
                alpha = if (latest) 0.95f else 0.6f
            }
            updateTimeline.addView(row)
        }
        if (to < entries.size) {
            updateTimeline.post { addChangelogEntries(entries, to, generation) }
        }
    }

    private fun shortSha(sha: String): String =
        if (sha.length > 8) sha.take(4) + "…" + sha.takeLast(3) else sha

    private fun showDownloadProgress(downloaded: Long, total: Long, startTime: Long) {
        val elapsed = (SystemClock.elapsedRealtime() - startTime).coerceAtLeast(1L)
        updateMeta.text = ListItemDownload.formatProgress(downloaded, total, downloaded * 1000.0 / elapsed)
        if (total > 0) {
            updateRing.setIndeterminate(false)
            updateRing.setProgress(downloaded * 100f / total)
        } else {
            updateRing.setIndeterminate(true)
        }
    }

    private fun showAvailable(info: AppUpdateInfo, activity: Activity) {
        loadedInfo = info
        destFile = File(activity.cacheDir, info.apkFileName)
        updateMeta.text = buildString {
            if (info.apkSize > 0) append(AppUpdateInstaller.formatFileSize(info.apkSize)).append("  ·  ")
            append("SHA-256 ").append(shortSha(info.sha256))
        }
        setChangelog(info.changelogText)
        applyVersionLabel()
        setMode(if (destFile.exists() && destFile.length() > 0) UpdateMode.INSTALL else UpdateMode.UPDATE)
        setBusy(false)
        refreshUpdateRow()
    }

    private fun renderCheckingUi(activity: Activity) {
        setVersionLabel(null)
        updateMeta.text = activity.getString(R.string.app_update_checking)
        setBusy(true)
    }

    private fun startCheck(activity: Activity) {
        if (checking) return
        checking = true
        renderCheckingUi(activity)

        // Kiểm tra gồm tải mạng + băm SHA-256 cả APK: chờ hiệu ứng chuyển màn xong rồi mới chạy, ưu tiên luồng nền
        mainHandler.postDelayed({
            if (!viewReady || !isAdded) {
                checking = false
                return@postDelayed
            }
            val checkStart = SystemClock.elapsedRealtime()
            Thread {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
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
        }, PANEL_IN_MS + 40L)
    }

    private fun applyResult(activity: Activity, result: AppUpdateCheckResult) {
        if (activeDownload != null) return
        when (result) {
            is AppUpdateCheckResult.Available -> showAvailable(result.info, activity)
            is AppUpdateCheckResult.UpToDate -> {
                loadedInfo = null
                setVersionLabel(null)
                updateMeta.text = activity.getString(R.string.app_update_latest)
                setChangelog(result.changelogText)
                setMode(UpdateMode.CHECK)
                setBusy(false)
            }
            is AppUpdateCheckResult.Failed -> {
                setVersionLabel(null)
                updateMeta.text = if (inUpdate) activity.getString(R.string.app_update_check_fail) else ""
                setChangelog(null)
                setMode(UpdateMode.CHECK)
                setBusy(false)
            }
        }
        refreshUpdateRow()
    }

    private fun startDownload(activity: Activity) {
        val info = loadedInfo ?: return
        isCancelable = false

        val state = AppUpdateInstaller.DownloadState()
        activeDownload = state
        refreshSwipe()
        setBusy(true)
        updateMeta.text = activity.getString(R.string.about_update_downloading)
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
                        refreshSwipe()
                        setMode(UpdateMode.UPDATE)
                        setBusy(false)
                        updateMeta.text = activity.getString(R.string.app_update_download_fail) + ": " + error
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
        activeDownload = null
        refreshSwipe()
        state.cancelled = true
        try {
            state.connection?.disconnect()
        } catch (_: Exception) {
        }
        setMode(UpdateMode.UPDATE)
        setBusy(false)
        updateMeta.text = activity.getString(R.string.app_update_download_cancelled)
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
                    isCancelable = true
                    refreshSwipe()
                    setMode(UpdateMode.INSTALL)
                    setBusy(false)
                }
            }
        }.apply { isDaemon = true }.start()
    }
}
