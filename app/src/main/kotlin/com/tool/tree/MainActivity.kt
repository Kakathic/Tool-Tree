package com.tool.tree

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.SuperscriptSpan
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import android.widget.ListPopupWindow
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import com.omarea.common.shared.FilePathResolver
import com.omarea.common.shell.KeepShellPublic
import com.omarea.common.ui.DialogAppUpdate
import com.omarea.common.ui.DialogHelper
import com.omarea.krscript.config.PageConfigReader
import com.omarea.krscript.config.PageConfigSh
import com.omarea.krscript.model.*
import com.omarea.krscript.ui.ActionListFragment
import com.omarea.krscript.ui.DialogLogFragment
import com.omarea.krscript.ui.ParamsFileChooserRender
import com.tool.tree.databinding.ActivityMainBinding
import com.tool.tree.ui.FadeScalePageTransformer
import com.tool.tree.ui.MainPagerAdapter
import com.tool.tree.ui.SwipePager
import com.tool.tree.ui.TabIconHelper
import com.tool.tree.ui.SpinnerPopupHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var krScriptConfig = KrScriptConfig()
    private val hasRoot by lazy { KeepShellPublic.checkRoot() }
    private var openedSubPage = false
    private var isFavoritesTab = false
    private var fileSelectedInterface: ParamsFileChooserRender.FileSelectedInterface? = null
    // Bản cập nhật mới nhất tìm được (từ SplashActivity) - điều khiển hiện/ẩn icon cập nhật
    // (kèm dấu chấm đỏ) ở menu item option_menu_update, bên trái icon nguồn. null = không có
    // bản mới -> ẩn icon.
    private var pendingUpdateInfo: AppUpdateInfo? = null
    private lateinit var toolbar: Toolbar

    private val ACTION_FILE_PATH_CHOOSER = 65400
    private val ACTION_FILE_PATH_CHOOSER_INNER = 65300
    private lateinit var adapter: MainPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeModeState.switchTheme(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val versionName = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "1.0.0"
        setAppTitleWithVersion(versionName)

        if (ThemeConfig(this).getAllowNotificationUI()) {
            WakeLockService.startService(applicationContext)
        }

        initAdapter()
        loadTabs()

        onBackPressedDispatcher.addCallback(this) {
            startService(Intent(this@MainActivity, WakeLockService::class.java).apply {
                action = WakeLockService.ACTION_END_WAKELOCK
            })
            // isEnabled = false
            // onBackPressedDispatcher.onBackPressed()
            finish()
        }

        handleResumeNotificationIntent(intent)
        showPendingUpdateIfAny()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleResumeNotificationIntent(intent)
    }

    /**
     * Nếu Activity được mở từ việc bấm vào thông báo tiến trình đã ẩn (nút "Ẩn" trong
     * DialogLogFragment), mở lại dialog log tương ứng thay vì chỉ đưa app lên foreground.
     */
    private fun handleResumeNotificationIntent(intent: Intent?) {
        val notificationId = intent?.getIntExtra(DialogLogFragment.EXTRA_RESUME_NOTIFICATION_ID, -1) ?: -1
        if (notificationId == -1) return

        DialogLogFragment.resume(notificationId)?.show(supportFragmentManager, "")
    }

    /**
     * SplashActivity kiểm tra cập nhật song song lúc tải data (AppUpdateChecker.fetchUpdateInfo)
     * và chuyển kết quả qua Intent extra "pendingUpdate" nếu có bản mới - ở đây chỉ cần hiện
     * dialog, không cần gọi mạng lại.
     */
    private fun showPendingUpdateIfAny() {
        @Suppress("DEPRECATION")
        val updateInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            intent.getSerializableExtra("pendingUpdate", AppUpdateInfo::class.java)
        else
            intent.getSerializableExtra("pendingUpdate") as? AppUpdateInfo

        // Icon (kèm dấu chấm đỏ) ở option_menu_update hiện theo biến này bất kể người dùng đã
        // từng bấm Hủy bỏ hay chưa - chỉ việc TỰ ĐỘNG hiện dialog mới bị chặn lại theo trạng
        // thái đã lưu.
        pendingUpdateInfo = updateInfo
        invalidateOptionsMenu()

        if (updateInfo != null && !AppUpdateConfig(this).isDismissed(updateInfo.sha256)) {
            showUpdateDialog(updateInfo)
        }
    }

    private fun showUpdateDialog(updateInfo: AppUpdateInfo) {
        DialogAppUpdate(
            darkMode = ThemeModeState.isDarkMode(),
            apkUrl = updateInfo.apkUrl,
            changelogUrl = updateInfo.changelogUrl,
            fileName = updateInfo.apkFileName,
            apkSize = updateInfo.apkSize,
            onCancel = { AppUpdateConfig(this).setDismissedSha256(updateInfo.sha256) }
        ).show(supportFragmentManager, "app_update")
    }

    private fun setAppTitleWithVersion(versionName: String) {
        val appName = getString(R.string.app_name)
        val fullTitle = "$appName $versionName"
        val spannable = SpannableString(fullTitle)

        val versionStart = appName.length + 1
        val versionEnd = fullTitle.length

        spannable.setSpan(SuperscriptSpan(), versionStart, versionEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(RelativeSizeSpan(0.55f), versionStart, versionEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        title = spannable
    }

    private fun initAdapter() {
        if (!::adapter.isInitialized) {
            adapter = MainPagerAdapter(this)
            adapter.attach(binding.viewPager)
            binding.viewPager.setPageTransformer(FadeScalePageTransformer())
        }
    }

    private fun loadTabs() {
        // Ưu tiên dùng data đã preload từ SplashActivity
        @Suppress("DEPRECATION")
        val preloaded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            intent.getSerializableExtra("preloadedTabs", MainTabsPreloadedData::class.java)
        else
            intent.getSerializableExtra("preloadedTabs") as? MainTabsPreloadedData

        if (preloaded != null) {
            // Đã có sẵn data — hiện ngay, không cần dialog
            applyTabsData(preloaded.favorites, preloaded.pages, preloaded.tab3Items, preloaded.tab4Items)
        } else {
            // Không có preload (vd: recreate, reload) — tải thẳng, không hiện dialog
            lifecycleScope.launch(Dispatchers.IO) {
                val favorites = getItems(krScriptConfig.getFavoriteConfig())
                val pages = getItems(krScriptConfig.getPageListConfig())
                val tab3Items = getItems(krScriptConfig.getCustomTab3Config())
                val tab4Items = getItems(krScriptConfig.getCustomTab4Config())

                if (!isActive) return@launch

                withContext(Dispatchers.Main) {
                    if (isFinishing || isDestroyed || !::adapter.isInitialized) return@withContext
                    applyTabsData(favorites, pages, tab3Items, tab4Items)
                }
            }
        }
    }

    private fun applyTabsData(
        favorites: ArrayList<NodeInfoBase>?,
        pages: ArrayList<NodeInfoBase>?,
        tab3Items: ArrayList<NodeInfoBase>?,
        tab4Items: ArrayList<NodeInfoBase>?
    ) {
        if (!::adapter.isInitialized) return
        val theme = ThemeModeState.getThemeMode()

        fun updateTab(pos: Int, items: ArrayList<NodeInfoBase>?, titleRes: Int, config: PageNode, isFav: Boolean) {
            items?.takeIf { it.isNotEmpty() }?.let { data ->
                val fragment = ActionListFragment.create(data, getKrScriptActionHandler(config, isFav), null, theme)
                if (adapter.getFragment(pos) == null) {
                    adapter.addFragment(fragment, getString(titleRes))
                } else {
                    adapter.replaceFragment(pos, fragment)
                }
            }
        }

        try {
            updateTab(0, favorites, R.string.tab_favorites, krScriptConfig.getFavoriteConfig(), true)
            updateTab(1, pages, R.string.tab_pages, krScriptConfig.getPageListConfig(), false)
            updateTab(2, tab3Items, R.string.tab_custom3, krScriptConfig.getCustomTab3Config(), false)
            updateTab(3, tab4Items, R.string.tab_custom4, krScriptConfig.getCustomTab4Config(), false)

            setupTabs()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "applyTabsData UI update failed", e)
        }
    }

    private fun reloadTabs() {
        lifecycleScope.launch(Dispatchers.IO) {
            val favorites = getItems(krScriptConfig.getFavoriteConfig())
            val pages = getItems(krScriptConfig.getPageListConfig())
            val tab3Items = getItems(krScriptConfig.getCustomTab3Config())
            val tab4Items = getItems(krScriptConfig.getCustomTab4Config())

            if (!isActive) return@launch

            withContext(Dispatchers.Main) {
                if (isFinishing || isDestroyed || !::adapter.isInitialized) return@withContext

                val theme = ThemeModeState.getThemeMode()

                try {
                    favorites?.let { adapter.getFragment(0)?.updateData(it, getKrScriptActionHandler(krScriptConfig.getFavoriteConfig(), true), theme) }
                    pages?.let { adapter.getFragment(1)?.updateData(it, getKrScriptActionHandler(krScriptConfig.getPageListConfig(), false), theme) }
                    tab3Items?.let { adapter.getFragment(2)?.updateData(it, getKrScriptActionHandler(krScriptConfig.getCustomTab3Config(), false), theme) }
                    tab4Items?.let { adapter.getFragment(3)?.updateData(it, getKrScriptActionHandler(krScriptConfig.getCustomTab4Config(), false), theme) }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "reloadTabs UI update failed", e)
                }
            }
        }
    }

    private fun setupTabs() {
        val tabHelper = TabIconHelper(this)

        binding.tabLayout.clearOnTabSelectedListeners()
        binding.tabLayout.removeAllTabs()

        for (position in 0 until adapter.getItemCount()) {
            val title = adapter.getTitle(position)
            val iconRes = when (position) {
                0 -> R.drawable.tab_favorites
                1 -> R.drawable.tab_pages
                2 -> R.drawable.tab_custom3
                3 -> R.drawable.tab_custom4
                else -> R.drawable.tab_favorites
            }
            val icon = getDrawable(iconRes) ?: continue
            val tab = binding.tabLayout.newTab()
            tab.customView = tabHelper.createTabView(title, icon, position == binding.viewPager.currentItem)
            binding.tabLayout.addTab(tab)
        }

        // FIX: Xử lý sự kiện click thủ công cho Custom Tab View
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                // Ép SwipePager chuyển trang khi ấn vào icon tab
                if (binding.viewPager.currentItem != tab.position) {
                    binding.viewPager.setCurrentItem(tab.position, true)
                }

                // Cập nhật hiệu ứng hiển thị (màu sắc/scale) của tab
                tabHelper.updateHighlight(binding.tabLayout, tab.position)

                isFavoritesTab = (tab.position == 0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // Đồng bộ TabLayout khi trang được chọn do vuốt (SwipePager tự settle xong mới báo,
        // tránh chọn tab liên tục theo từng pixel kéo giữa chừng).
        binding.viewPager.setOnPageChangeListener(object : SwipePager.OnPageChangeListener {
            override fun onPageSelected(position: Int) {
                binding.tabLayout.getTabAt(position)?.select()
            }

            // Đổi độ sáng icon NGAY khi vuốt qua quá nửa trang (không đợi settle xong hẳn mới
            // đổi như onPageSelected). CHỈ gọi updateHighlight() (chỉ đổi alpha icon) - KHÔNG
            // gọi tab.select() ở đây, vì .select() sẽ kích hoạt luôn setCurrentItem() ép chuyển
            // trang trong onTabSelected() bên trên, xung đột với thao tác kéo tay đang diễn ra.
            override fun onPageScrolled(position: Int, offset: Float) {
                val highlightPosition = if (offset > 0.5f) position + 1 else position
                tabHelper.updateHighlight(binding.tabLayout, highlightPosition)
            }
        })
    }

    private fun getItems(pageNode: PageNode): ArrayList<NodeInfoBase>? {
        return try {
            if (pageNode.pageConfigSh.isNotEmpty()) {
                PageConfigSh(this, pageNode.pageConfigSh, null).execute()
            } else if (pageNode.pageConfigPath.isNotEmpty()) {
                PageConfigReader(applicationContext, pageNode.pageConfigPath, null).readConfigXml()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun restartApp() {
        val intent = Intent(this, SplashActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("force_reset", true)
        }
        startActivity(intent)
        finish()
    }

    private fun getKrScriptActionHandler(pageNode: PageNode, isFavorites: Boolean): KrScriptActionHandler {
        return object : KrScriptActionHandler {
            override fun onActionCompleted(runnableNode: RunnableNode) {
                when {
                    runnableNode.autoFinish -> finishAndRemoveTask()
                    runnableNode.reloadPage -> reloadTabs()
                    runnableNode.autoRestart -> restartApp()
                    runnableNode.autoKill -> {
                        startService(Intent(this@MainActivity, WakeLockService::class.java).apply {
                            action = WakeLockService.ACTION_END_WAKELOCK
                        })
                        finishAffinity()
                    }
                }
            }

            override fun addToFavorites(clickableNode: ClickableNode, addToFavoritesHandler: KrScriptActionHandler.AddToFavoritesHandler) {
                val page = clickableNode as? PageNode ?: pageNode
                val intent = Intent().apply {
                    component = ComponentName(applicationContext, ActionPage::class.java)
                    addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_HISTORY)
                    putExtra("page", page)
                    if (clickableNode is RunnableNode) putExtra("autoRunItemId", clickableNode.key)
                }
                addToFavoritesHandler.onAddToFavorites(clickableNode, intent)
            }

            override fun onSubPageClick(pageNode: PageNode) {
                OpenPageHelper(this@MainActivity).openPage(pageNode)
            }

            override fun openFileChooser(fileSelectedInterface: ParamsFileChooserRender.FileSelectedInterface): Boolean {
                return chooseFilePath(fileSelectedInterface)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun chooseFilePath(fileSelectedInterface: ParamsFileChooserRender.FileSelectedInterface): Boolean {
        return try {
            val multiple = fileSelectedInterface.multiple()
            val pathHome = fileSelectedInterface.pathHome()
            if (fileSelectedInterface.type() == ParamsFileChooserRender.FileSelectedInterface.TYPE_FOLDER) {
                startActivityForResult(
                    Intent(this, ActivityFileSelector::class.java).apply {
                        putExtra("mode", ActivityFileSelector.MODE_FOLDER)
                        putExtra("multiple", multiple)
                        if (!pathHome.isNullOrEmpty()) putExtra("path_home", pathHome)
                    },
                    ACTION_FILE_PATH_CHOOSER_INNER
                )
            } else {
                val suffix = fileSelectedInterface.suffix()
                if (!suffix.isNullOrEmpty() || !pathHome.isNullOrEmpty()) {
                    startActivityForResult(
                        Intent(this, ActivityFileSelector::class.java).apply {
                            if (!suffix.isNullOrEmpty()) putExtra("extension", suffix)
                            putExtra("mode", ActivityFileSelector.MODE_FILE)
                            putExtra("multiple", multiple)
                            if (!pathHome.isNullOrEmpty()) putExtra("path_home", pathHome)
                        },
                        ACTION_FILE_PATH_CHOOSER_INNER
                    )
                } else {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = fileSelectedInterface.mimeType() ?: "*/*"
                        if (multiple) {
                            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        }
                    }
                    startActivityForResult(intent, ACTION_FILE_PATH_CHOOSER)
                }
            }
            this.fileSelectedInterface = fileSelectedInterface
            true
        } catch (e: Exception) {
            Toast.makeText(this, "File picker error: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            val currentInterface = fileSelectedInterface
            val separator = currentInterface?.separator() ?: "\n"
            val path = when (requestCode) {
                ACTION_FILE_PATH_CHOOSER -> {
                    val clipData = data.clipData
                    if (currentInterface?.multiple() == true && clipData != null && clipData.itemCount > 0) {
                        (0 until clipData.itemCount)
                            .mapNotNull { FilePathResolver().getPath(this, clipData.getItemAt(it).uri) }
                            .joinToString(separator)
                    } else {
                        data.data?.let { FilePathResolver().getPath(this, it) }
                    }
                }
                ACTION_FILE_PATH_CHOOSER_INNER -> {
                    val files = data.getStringArrayListExtra("files")
                    if (currentInterface?.multiple() == true && files != null) {
                        files.joinToString(separator)
                    } else {
                        data.getStringExtra("file")
                    }
                }
                else -> null
            }
            fileSelectedInterface?.onFileSelected(path)
            fileSelectedInterface = null
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.option_menu_reboot)?.isEnabled = hasRoot
        // Icon cập nhật (kèm dấu chấm đỏ ghép sẵn trong ic_update_badge) chỉ hiện khi có bản
        // mới - nằm bên trái, sát cạnh icon nguồn (xem order trong res/menu/main.xml).
        menu.findItem(R.id.option_menu_update)?.isVisible = pendingUpdateInfo != null
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.option_menu_update -> { pendingUpdateInfo?.let { showUpdateDialog(it) }; true }
            R.id.option_menu_info -> { showSettingsDialog(); true }
            R.id.option_menu_reboot -> { DialogPower(this).showPowerMenu(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // override fun onRestart() {
        // super.onRestart()
        // if (openedSubPage) {
            // openedSubPage = false
            // reloadTabs()
        // }
    // }

    private fun showSettingsDialog() {
        val layout = LayoutInflater.from(this).inflate(R.layout.dialog_about, null)
        val themeConfig = ThemeConfig(this)
        
        val themeSelector = layout.findViewById<TextView>(R.id.theme_selector)
        val themeNames = listOf(
            getString(R.string.theme_system_default), getString(R.string.theme_dark),
            getString(R.string.theme_light), getString(R.string.theme_wallpaper_system),
            getString(R.string.theme_wallpaper_dark), getString(R.string.theme_wallpaper_light)
        )
        
        themeSelector.text = themeNames[themeConfig.getThemeMode().coerceIn(0, themeNames.size - 1)]
        themeSelector.setOnClickListener {
            val popup = ListPopupWindow(this)
            popup.anchorView = themeSelector
            popup.setAdapter(ArrayAdapter(this, R.layout.kr_spinner_dropdown, themeNames))
            val background = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.kr_spinner_popup_bg)
            popup.setBackgroundDrawable(background)
            popup.setOnItemClickListener { _, _, position, _ ->
                themeConfig.setThemeMode(position)
                themeSelector.text = themeNames[position]
                popup.dismiss()
                ThemeModeState.switchTheme(this)
                recreate()
            }
            val inflater = LayoutInflater.from(this)
            val itemViews = themeNames.map { name ->
                inflater.inflate(R.layout.kr_spinner_dropdown, themeSelector.parent as? android.view.ViewGroup, false).apply {
                    findViewById<TextView>(R.id.text).text = name
                }
            }
            val minWidthPx = (resources.displayMetrics.density * 220).toInt()
            SpinnerPopupHelper.applyWidthAndPosition(
                popup, themeSelector, itemViews, background, minWidthPx, alignRight = false
            )
            popup.show()
            SpinnerPopupHelper.applyRoundedClip(popup, resources.getDimension(R.dimen.kr_spinner_popup_radius))
        }

        layout.findViewById<CheckBox>(R.id.notification_ui).apply {
            text = "$text "
            isChecked = themeConfig.getAllowNotificationUI()
            setOnCheckedChangeListener { _, isChecked ->
                themeConfig.setAllowNotificationUI(isChecked)
            }
        }


        layout.findViewById<TextView>(R.id.appliction_authorText).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://Kakathic.github.io/Tool-Tree/website/Information.html")))
        }
        layout.findViewById<TextView>(R.id.appliction_nameText).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Kakathic/Tool-Tree")))
        }

        DialogHelper.customDialog(this, layout)
    }
}
