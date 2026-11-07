package com.tool.tree

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.SuperscriptSpan
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import android.view.Choreographer
import androidx.activity.addCallback
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import android.widget.ListPopupWindow
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import com.omarea.common.shared.FilePathResolver
import com.omarea.common.shell.KeepShellPublic
import com.omarea.common.ui.BlurEngine
import com.omarea.common.ui.DialogHelper
import com.omarea.krscript.config.PageConfigReader
import com.omarea.krscript.config.PageConfigSh
import com.omarea.krscript.TryOpenActivity
import com.omarea.krscript.WebLauncher
import com.omarea.krscript.executor.ScriptEnvironmen
import com.omarea.krscript.shortcut.DrawerFavorites
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
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var krScriptConfig = KrScriptConfig()
    private val hasRoot by lazy { KeepShellPublic.checkRoot() }
    private var isFavoritesTab = false
    private var fileSelectedInterface: ParamsFileChooserRender.FileSelectedInterface? = null
    
    private var pendingUpdateInfo: AppUpdateInfo? = null
    private var deferredUpdateInfo: AppUpdateInfo? = null
    private var deferredStartOnUpdate = false
    private var aboutPending = false

    companion object {
        private var updateChecked = false
        private var cachedUpdateInfo: AppUpdateInfo? = null
        @Volatile var reopenAbout = false
    }
    private lateinit var toolbar: Toolbar

    private val ACTION_FILE_PATH_CHOOSER = 65400
    private val ACTION_FILE_PATH_CHOOSER_INNER = 65300
    private lateinit var adapter: MainPagerAdapter
    private lateinit var mainDrawer: MainDrawer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            (supportFragmentManager.findFragmentByTag("app_update") as? androidx.fragment.app.DialogFragment)?.dismissAllowingStateLoss()
        }
        ThemeModeState.switchTheme(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val versionName = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "1.0.0"
        setAppTitleWithVersion(versionName)

        initAdapter()
        loadTabs()
        setupDrawer(versionName)
        loadDrawer()

        onBackPressedDispatcher.addCallback(this) {
            if (binding.mainDrawer.isDrawerOpen(GravityCompat.START)) {
                binding.mainDrawer.closeDrawer(GravityCompat.START)
            } else {
                finish()
            }
        }

        handleResumeNotificationIntent(intent)
        showPendingUpdateIfAny()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleResumeNotificationIntent(intent)
    }

    private fun handleResumeNotificationIntent(intent: Intent?) {
        val notificationId = intent?.getIntExtra(DialogLogFragment.EXTRA_RESUME_NOTIFICATION_ID, -1) ?: -1
        if (notificationId == -1) return

        DialogLogFragment.resume(notificationId)?.show(supportFragmentManager, "")
    }

    private fun showPendingUpdateIfAny() {
        if (updateChecked) {
            applyUpdateInfo(cachedUpdateInfo)
            return
        }
        binding.root.post {
            if (isFinishing || isDestroyed) return@post
            lifecycleScope.launch(Dispatchers.IO) {
                val info = withTimeoutOrNull(8000) { AppUpdateChecker.fetchUpdateInfo(this@MainActivity) }
                withContext(Dispatchers.Main) {
                    if (isFinishing || isDestroyed) return@withContext
                    updateChecked = true
                    cachedUpdateInfo = info
                    applyUpdateInfo(info)
                }
            }
        }
    }

    private fun applyUpdateInfo(updateInfo: AppUpdateInfo?) {
        pendingUpdateInfo = updateInfo
        invalidateOptionsMenu()

        if (updateInfo != null && !AppUpdateConfig(this).isDismissed(updateInfo.sha256)) {
            showAboutDialog(updateInfo, startOnUpdate = true)
        }
    }

    private fun showAboutDialog(info: AppUpdateInfo?, startOnUpdate: Boolean = false) {
        if (aboutPending) return
        aboutPending = true
        BlurEngine.runWhenBlurReady {
            aboutPending = false
            if (isFinishing || isDestroyed) return@runWhenBlurReady
            if (supportFragmentManager.isStateSaved) {
                if (info != null) {
                    deferredUpdateInfo = info
                    deferredStartOnUpdate = startOnUpdate
                }
                return@runWhenBlurReady
            }
            if (supportFragmentManager.findFragmentByTag("app_update") != null) return@runWhenBlurReady
            DialogAboutUpdate(ThemeModeState.isDarkMode(), info, startOnUpdate) { result -> onUpdateResult(result) }
                .show(supportFragmentManager, "app_update")
        }
    }

    private fun onUpdateResult(result: AppUpdateCheckResult) {
        if (isFinishing || isDestroyed) return
        when (result) {
            is AppUpdateCheckResult.Available -> {
                updateChecked = true
                cachedUpdateInfo = result.info
                pendingUpdateInfo = result.info
                invalidateOptionsMenu()
            }
            is AppUpdateCheckResult.UpToDate -> {
                updateChecked = true
                cachedUpdateInfo = null
                pendingUpdateInfo = null
                invalidateOptionsMenu()
            }
            is AppUpdateCheckResult.Failed -> {}
        }
    }

    override fun onResumeFragments() {
        super.onResumeFragments()
        if (reopenAbout) {
            reopenAbout = false
            showAboutDialog(pendingUpdateInfo ?: cachedUpdateInfo)
            return
        }
        val info = deferredUpdateInfo ?: return
        deferredUpdateInfo = null
        val direct = deferredStartOnUpdate
        deferredStartOnUpdate = false
        showAboutDialog(info, direct)
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

    private fun setupDrawer(versionName: String) {
        val drawer = binding.mainDrawer
        val panel = binding.mainDrawerPanel
        binding.mainDrawerTitle.text = "${getString(R.string.app_name)} $versionName"
        mainDrawer = MainDrawer(
            binding.mainDrawerList,
            { page -> onDrawerPageClick(page) },
            { entry ->
                DialogHelper.confirm(this, entry.title, getString(R.string.kr_favorite_remove_desc).format(entry.title), Runnable {
                    DrawerFavorites(this).remove(entry.id)
                    mainDrawer.render()
                })
            }
        )

        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.START)
        drawer.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)
            }

            override fun onDrawerClosed(drawerView: View) {
                drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.START)
            }
        })

        ViewCompat.setOnApplyWindowInsetsListener(panel) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, sys.top, 0, sys.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(panel)

        toolbar.post {
            for (i in 0 until toolbar.childCount) {
                val child = toolbar.getChildAt(i)
                if (child is TextView) child.setOnClickListener { openDrawer() }
            }
        }
        binding.viewPager.onOverscrollStart = { openDrawer() }
    }

    private fun openDrawer() {
        mainDrawer.render()
        if (binding.mainDrawerList.childCount == 0) return
        binding.mainDrawer.openDrawer(GravityCompat.START)
    }

    private fun loadDrawer() {
        lifecycleScope.launch(Dispatchers.IO) {
            val items = getItems(krScriptConfig.getDrawerConfig())
            withContext(Dispatchers.Main) {
                if (isFinishing || isDestroyed || !::mainDrawer.isInitialized) return@withContext
                mainDrawer.refresh(items)
            }
        }
    }

    private fun onDrawerPageClick(page: PageNode) {
        binding.mainDrawer.closeDrawer(GravityCompat.START, false)
        // Chờ 2 frame để ảnh chụp xem trước (swipe-back) không còn thấy ngăn kéo
        Choreographer.getInstance().postFrameCallback {
            Choreographer.getInstance().postFrameCallback {
                if (isFinishing || isDestroyed) return@postFrameCallback
                runIfUnlocked(page) {
                    when {
                        page.link.isNotEmpty() -> try {
                            WebLauncher.open(this, page.link)
                        } catch (ex: Exception) {
                            Toast.makeText(this, getString(R.string.kr_slice_activity_fail), Toast.LENGTH_SHORT).show()
                        }
                        page.activity.isNotEmpty() -> TryOpenActivity(this, page.activity).tryOpen()
                        else -> OpenPageHelper(this).openPage(page)
                    }
                }
            }
        }
    }

    private fun runIfUnlocked(node: ClickableNode, onUnlocked: () -> Unit) {
        val sdk = Build.VERSION.SDK_INT
        val sdkMessage = getString(R.string.kr_sdk_message).format(node.minSdkVersion, node.maxSdkVersion)
        when {
            node.targetSdkVersion > 0 && sdk != node.targetSdkVersion -> {
                DialogHelper.helpInfo(this, getString(R.string.kr_sdk_discrepancy), getString(R.string.kr_sdk_discrepancy_message).format(node.targetSdkVersion))
                return
            }
            sdk > node.maxSdkVersion -> {
                DialogHelper.helpInfo(this, getString(R.string.kr_sdk_overtop), sdkMessage)
                return
            }
            sdk < node.minSdkVersion -> {
                DialogHelper.helpInfo(this, getString(R.string.kr_sdk_too_low), sdkMessage)
                return
            }
        }

        if (node.lockShell.isEmpty()) {
            if (node.locked) {
                Toast.makeText(this, node.lockMessage.ifEmpty { getString(R.string.kr_lock_message) }, Toast.LENGTH_SHORT).show()
            } else {
                onUnlocked()
            }
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val message = ScriptEnvironmen.executeResultRoot(this@MainActivity, node.lockShell, node)
            withContext(Dispatchers.Main) {
                if (isFinishing || isDestroyed) return@withContext
                if (message == "unlock" || message == "unlocked" || message == "false" || message == "0") {
                    onUnlocked()
                } else {
                    Toast.makeText(this@MainActivity, message.ifEmpty { getString(R.string.kr_lock_message) }, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun initAdapter() {
        if (!::adapter.isInitialized) {
            adapter = MainPagerAdapter(this)
            adapter.attach(binding.viewPager)
            binding.viewPager.setPageTransformer(FadeScalePageTransformer())
        }
    }

    private fun loadTabs() {
        @Suppress("DEPRECATION")
        val preloaded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            intent.getSerializableExtra("preloadedTabs", MainTabsPreloadedData::class.java)
        else
            intent.getSerializableExtra("preloadedTabs") as? MainTabsPreloadedData

        if (preloaded != null) {
            applyTabsData(preloaded.favorites, preloaded.pages, preloaded.tab3Items, preloaded.tab4Items)
        } else {
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
        loadDrawer()
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

        attachTabTouchSelect(tabHelper)

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (binding.viewPager.currentItem != tab.position) {
                    binding.viewPager.setCurrentItem(tab.position, true)
                }
                tabHelper.updateHighlight(binding.tabLayout, tab.position)
                isFavoritesTab = (tab.position == 0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        var userSwiping = false
        binding.viewPager.setOnPageChangeListener(object : SwipePager.OnPageChangeListener {
            override fun onPageSelected(position: Int) {
                binding.tabLayout.getTabAt(position)?.select()
            }

            override fun onPageScrollStateChanged(state: Int) {
                if (state == SwipePager.SCROLL_STATE_DRAGGING) userSwiping = true
                if (state == SwipePager.SCROLL_STATE_IDLE) userSwiping = false
                tabHelper.setPagerDragging(binding.tabLayout, state == SwipePager.SCROLL_STATE_DRAGGING)
            }

            override fun onPageScrolled(position: Int, offset: Float) {
                if (!userSwiping) return
                val highlightPosition = if (offset > 0.5f) position + 1 else position
                tabHelper.updateHighlight(binding.tabLayout, highlightPosition)
            }
        })
    }

    private fun attachTabTouchSelect(tabHelper: TabIconHelper) {
        for (position in 0 until binding.tabLayout.tabCount) {
            val customView = binding.tabLayout.getTabAt(position)?.customView ?: continue
            customView.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        tabHelper.setTabTouching(binding.tabLayout, true)
                        binding.tabLayout.getTabAt(position)?.select()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        findTabAt(event.rawX)?.let { target ->
                            if (binding.tabLayout.selectedTabPosition != target) {
                                binding.tabLayout.getTabAt(target)?.select()
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        tabHelper.setTabTouching(binding.tabLayout, false)
                        v.performClick()
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun findTabAt(rawX: Float): Int? {
        val loc = IntArray(2)
        for (i in 0 until binding.tabLayout.tabCount) {
            val view = binding.tabLayout.getTabAt(i)?.customView ?: continue
            view.getLocationOnScreen(loc)
            if (rawX >= loc[0] && rawX < loc[0] + view.width) return i
        }
        return null
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
                    if (clickableNode is RunnableNode || clickableNode is TerminalNode || clickableNode is EditorNode) putExtra("autoRunItemId", clickableNode.effectiveKey)
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
        val rebootItem = menu.findItem(R.id.option_menu_reboot)
        rebootItem?.isEnabled = hasRoot
        rebootItem?.icon?.alpha = if (hasRoot) 255 else 97
        menu.findItem(R.id.option_menu_info)?.setIcon(
            if (pendingUpdateInfo != null) R.drawable.info_badge else R.drawable.info
        )
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.option_menu_info -> { showAboutDialog(pendingUpdateInfo); true }
            R.id.option_menu_reboot -> { DialogPower(this).showPowerMenu(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
