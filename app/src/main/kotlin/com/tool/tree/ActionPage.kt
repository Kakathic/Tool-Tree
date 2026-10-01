package com.tool.tree

import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.webkit.WebView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ListPopupWindow
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.tool.tree.ui.PopupMenuListAdapter
import com.tool.tree.ui.PopupMenuRow
import com.tool.tree.ui.PopupRowTypeIcon
import com.tool.tree.ui.SwipeBackHelper
import com.tool.tree.ui.SwipeBackPreviewCache
import com.tool.tree.ui.SpinnerPopupHelper
import com.omarea.common.model.SelectItem
import com.omarea.common.shared.FilePathResolver
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.omarea.common.ui.BlurTopBarLayout
import com.omarea.common.ui.DialogHelper
import com.omarea.common.ui.DialogItemChooser
import com.omarea.common.ui.ProgressBarDialog
import com.omarea.krscript.config.ActionShowMemory
import com.omarea.krscript.TryOpenActivity
import com.omarea.krscript.WebLauncher
import com.omarea.krscript.config.IconPathAnalysis
import com.omarea.krscript.config.PageConfigReader
import com.omarea.krscript.config.PageConfigSh
import com.omarea.krscript.executor.ScriptEnvironmen
import com.omarea.krscript.model.*
import com.omarea.krscript.shortcut.ActionShortcutManager
import com.omarea.krscript.ui.ActionListFragment
import com.omarea.krscript.ui.DialogLogFragment
import com.omarea.krscript.ui.ParamsFileChooserRender
import com.omarea.krscript.ui.RowRunProgressHost
import com.tool.tree.databinding.ActivityActionPageBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

class ActionPage : AppCompatActivity(), RowRunProgressHost {
    companion object {
        private var pendingSpinIcon: android.graphics.drawable.Drawable? = null
        private const val CHECKBOX_REFRESH_DEBOUNCE_MS = 1000L
        private const val SKELETON_AFTER_DIALOG_MAX = 1
        private const val SKELETON_FIRST_FRAME_DELAY_MS = 48L
    }

    private val progressBarDialog by lazy { ProgressBarDialog(this) }
    private val loadProgressBar by lazy { findViewById<ProgressBar>(R.id.page_load_progress) }
    private var actionsLoaded = false
    private val handler = Handler(Looper.getMainLooper())

    private var currentPageConfig: PageNode? = null
    private var autoRunItemId = ""
    private lateinit var binding: ActivityActionPageBinding

    private lateinit var swipeBackHelper: SwipeBackHelper

    private var swipePreview: SwipeBackPreviewCache.Preview? = null

    private val justClickedItemIds = HashSet<Int>()
    private val justClickedRemovalRunnables = HashMap<Int, Runnable>()

    private fun checkboxItemId(option: PageMenuOption): Int = System.identityHashCode(option)

    private var fileSelectedInterface: ParamsFileChooserRender.FileSelectedInterface? = null
    private val ACTION_FILE_PATH_CHOOSER = 65400
    private val ACTION_FILE_PATH_CHOOSER_INNER = 65300

    private var menuOptions: ArrayList<PageMenuOption>? = null
    private var headerActions: ArrayList<ActionNode>? = null
    private var autoShowTriggered = false
    private var menuCheckboxRefreshing = false
    private var checkboxRefreshJob: Job? = null
    private var loadPageJob: Job? = null
    private var pendingReloadWhileLoading = false
    private var spinnerLoadJob: Job? = null
    private var lockCheckJob: Job? = null
    private var pendingDeferredBuilder: (() -> ArrayList<PageConfigReader.DeferredNodeResult>)? = null

    private var pagePrewarmIconAnalysis = IconPathAnalysis()
    private var lockCheckStarted = false

    private var webViewFreezeCount = 0
    private var webViewLifecyclePaused = false
    private var webViewsPaused = false
    private var webViewTimersPaused = false
    private var scrollFreezeRunnable: Runnable? = null
    private var scrollChangedListener: ViewTreeObserver.OnScrollChangedListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!ScriptEnvironmen.isInited()) {
            val initIntent = Intent(this.applicationContext, SplashActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                putExtras(this@ActionPage.intent)
                putExtra("JumpActionPage", true)
            }
            startActivity(initIntent)
            finish()
            return
        }

        ThemeModeState.switchTheme(this)
        binding = ActivityActionPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        @Suppress("DEPRECATION")
        val retainedPreview = lastCustomNonConfigurationInstance as? SwipeBackPreviewCache.Preview
        swipePreview = retainedPreview ?: SwipeBackPreviewCache.consume()
        swipePreview?.let {
            binding.swipeBackPreviewSharp.setImageBitmap(it.sharp)
            binding.swipeBackPreviewBlur.setImageBitmap(it.blurred ?: it.sharp)
        }

        swipeBackHelper = SwipeBackHelper(
            activity = this,
            contentView = binding.swipeForeground,
            onDragStateChanged = { dragging ->
                if (swipePreview != null) {
                    val visibility = if (dragging) View.VISIBLE else View.GONE
                    binding.swipeBackPreviewBlur.visibility = visibility
                    binding.swipeBackPreviewSharp.visibility = visibility
                    if (!dragging) binding.swipeBackPreviewSharp.alpha = 0f
                }
                if (dragging) freezeWebViews() else unfreezeWebViews()
            },
            onDragProgress = { progress ->
                binding.swipeBackPreviewSharp.alpha = progress * progress
            }
        )
        setupWebViewScrollFreeze()

        val toolbar = findViewById<View>(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)
        setTitle(R.string.app_name)

        supportActionBar?.apply {
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_arrow_back)
        }
        toolbar.setNavigationOnClickListener { finish() }
        setupToolbarLiveBlur()

        val extras = intent.extras
        if (extras != null) {
            currentPageConfig = if (extras.containsKey("page")) {
                extras.getSerializable("page") as? PageNode
            } else if (extras.containsKey("shortcutId")) {
                ActionShortcutManager(this).getShortcutTarget(extras.getString("shortcutId") ?: "")
            } else null

            autoRunItemId = extras.getString("autoRunItemId", "")
        }

        val config = currentPageConfig
        if (config == null) {
            Toast.makeText(this, "Invalid page information", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (config.activity.isNotEmpty()) {
            if (TryOpenActivity(this, config.activity).tryOpen()) {
                finish()
                return
            }
        }

        if (config.onlineHtmlPage.isNotEmpty()) {
            try {
                startActivity(Intent(this, ActionPageOnline::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("config", config.onlineHtmlPage)
                })
            } catch (_: Exception) {}
        }

        if (config.title.isNotEmpty()) {
            title = config.title
        }

        if (config.pageConfigPath.isEmpty() && config.pageConfigSh.isEmpty()) {
            setResult(2)
            finish()
        } else {
            checkPageLockThenLoad()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (::swipeBackHelper.isInitialized && swipeBackHelper.dispatchTouchEvent(ev)) {
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    private val actionShortClickHandler = object : KrScriptActionHandler {
        override fun onActionCompleted(runnableNode: RunnableNode) {
            when {
                runnableNode.autoFinish -> finishAndRemoveTask()
                runnableNode.reloadPage -> loadPageConfig(true)
                runnableNode.autoKill -> killApp()
                runnableNode.autoRestart -> restartApp()
            }
        }

        override fun addToFavorites(clickableNode: ClickableNode, addToFavoritesHandler: KrScriptActionHandler.AddToFavoritesHandler) {
            val page = clickableNode as? PageNode ?: currentPageConfig ?: return
            val intent = Intent(applicationContext, ActionPage::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_HISTORY)
                putExtra("page", page)
                if (clickableNode is RunnableNode) putExtra("autoRunItemId", clickableNode.key)
            }
            addToFavoritesHandler.onAddToFavorites(clickableNode, intent)
        }

        override fun onSubPageClick(pageNode: PageNode) {
            _openPage(pageNode)
        }

        override fun openFileChooser(fileSelectedInterface: ParamsFileChooserRender.FileSelectedInterface): Boolean {
            return chooseFilePath(fileSelectedInterface)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val config = currentPageConfig ?: return false
        if (menuOptions == null) {
            menuOptions = config.pageMenuOptions
        }
        if (headerActions == null) {
            headerActions = config.headerActions
        }

        menu?.clear()

        headerActions?.forEach { action ->
            val uniqueItemId = ("header:" + action.key).hashCode()
            val menuItem = menu?.add(Menu.NONE, uniqueItemId, Menu.NONE, action.title)
            menuItem?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            menuItem?.icon = ContextCompat.getDrawable(this, R.drawable.ic_menu)
        }

        binding.actionPageFab.visibility = View.GONE

        val fabOptions = ArrayList<PageMenuOption>()
        val overflowOptions = ArrayList<PageMenuOption>()
        menuOptions?.forEach { option ->
            if (option.isFab) {
                fabOptions.add(option)
            } else {
                overflowOptions.add(option)
            }
        }
        setupFab(fabOptions, config.fabIconNode)
        setupOverflowMenuButton(menu, overflowOptions, config.menuIconNode)

        if (pendingSpinIcon != null) {
            startFabSpin()
        }

        handler.post { scheduleCheckboxRefresh() }

        return true
    }

    private val refreshCheckboxRunnable = Runnable { refreshCheckboxMenuStates() }

    private fun scheduleCheckboxRefresh() {
        handler.removeCallbacks(refreshCheckboxRunnable)
        handler.postDelayed(refreshCheckboxRunnable, CHECKBOX_REFRESH_DEBOUNCE_MS)
    }

    private fun refreshCheckboxMenuStates() {
        val config = currentPageConfig ?: return

        val checkboxOptions = menuOptions?.filter { option ->
            option.type == "checkbox" &&
                    option.checkedSh.isNotEmpty() &&
                    !justClickedItemIds.contains(checkboxItemId(option))
        }.orEmpty()

        if (checkboxOptions.isEmpty() || menuCheckboxRefreshing) return

        menuCheckboxRefreshing = true
        checkboxRefreshJob?.cancel()

        checkboxRefreshJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val scripts = LinkedHashMap<String, String>()
                checkboxOptions.forEachIndexed { index, option ->
                    scripts[index.toString()] = option.checkedSh
                }

                val results = ScriptEnvironmen.executeMultipleResultRoot(this@ActionPage, scripts, config)

                withContext(Dispatchers.Main) {
                    if (isFinishing || isDestroyed) return@withContext

                    var changed = false
                    checkboxOptions.forEachIndexed { index, option ->
                        val uniqueItemId = checkboxItemId(option)
                        if (!justClickedItemIds.contains(uniqueItemId)) {
                            val result = results[index.toString()]?.trim() ?: ""
                            val newChecked = result == "1" || result.equals("true", ignoreCase = true)
                            if (option.checked != newChecked) changed = true
                            option.checked = newChecked
                        }
                    }

                    if (changed) {
                        invalidateOptionsMenu()
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    menuCheckboxRefreshing = false
                }
            }
        }
    }

    private fun setupFab(fabOptions: List<PageMenuOption>, fabIconNode: ClickableNode?) {
        when (fabOptions.size) {
            0 -> return
            1 -> addFab(fabOptions[0], fabIconNode)
            else -> {
                binding.actionPageFab.apply {
                    visibility = View.VISIBLE
                    setOnClickListener { showFabChooser(fabOptions) }
                    setImageDrawable(resolveFabIcon(fabOptions, fabIconNode))
                }
            }
        }
    }

    private fun addFab(menuOption: PageMenuOption, fabIconNode: ClickableNode?) {
        binding.actionPageFab.apply {
            visibility = View.VISIBLE
            setOnClickListener { onMenuItemClick(menuOption, this) }
            setImageDrawable(resolveFabIcon(listOf(menuOption), fabIconNode))
        }
    }

    private fun resolveFabIcon(fabOptions: List<PageMenuOption>, fabIconNode: ClickableNode?): android.graphics.drawable.Drawable? {
        if (fabOptions.size == 1) {
            val option = fabOptions[0]
            if (option.iconPath.isNotEmpty()) {
                IconPathAnalysis().loadLogo(this, option, false)?.let { return it }
            }
        }

        fabIconNode?.let { IconPathAnalysis().loadLogo(this, it, false) }?.let { return it }

        val iconRes = if (fabOptions.size == 1) {
            val option = fabOptions[0]
            if ((option.type == "file" || option.type == "folder")) R.drawable.kr_folder else R.drawable.kr_fab
        } else {
            R.drawable.kr_fab
        }
        return ContextCompat.getDrawable(this, iconRes)
    }

    private fun setupOverflowMenuButton(menu: Menu?, overflowOptions: List<PageMenuOption>, menuIconNode: ClickableNode?) {
        if (overflowOptions.isEmpty()) return

        val menuItem = menu?.add(Menu.NONE, Menu.NONE, Menu.NONE, getString(R.string.kr_more_options))
        menuItem?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)

        val button = buildOverflowMenuButton(menuIconNode)
        button.setOnClickListener { showOverflowMenuPopup(button, overflowOptions) }
        menuItem?.actionView = button
    }

    private val toolbarIconTint: android.content.res.ColorStateList? by lazy {
        val ta = obtainStyledAttributes(intArrayOf(R.attr.toolbarIconTint))
        val tint = ta.getColorStateList(0)
        ta.recycle()
        tint
    }

    private fun buildOverflowMenuButton(menuIconNode: ClickableNode?): ImageButton {
        val density = resources.displayMetrics.density
        val sizePx = (48 * density).toInt()
        val paddingPx = (12 * density).toInt()

        val backgroundResId = TypedValue().let {
            theme.resolveAttribute(android.R.attr.actionBarItemBackground, it, true)
            it.resourceId
        }

        val customIcon = menuIconNode?.takeIf { it.iconPath.isNotEmpty() }?.let { IconPathAnalysis().loadIcon(this, it) }

        return ImageButton(this).apply {
            layoutParams = ViewGroup.LayoutParams(sizePx, sizePx)
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            if (backgroundResId != 0) setBackgroundResource(backgroundResId)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            if (customIcon != null) {
                setImageDrawable(customIcon)
                imageTintList = toolbarIconTint
            } else {
                setImageResource(R.drawable.ic_more_vert)
            }
            contentDescription = getString(R.string.kr_more_options)
        }
    }

    private fun showOverflowMenuPopup(anchor: View, overflowOptions: List<PageMenuOption>) {
        showListPopup(anchor, overflowOptions.map { buildPopupRow(it, anchor) })
    }

    private fun buildPopupRow(option: PageMenuOption, anchor: View?): PopupMenuRow {
        val opensInternalPage = option.pageConfigSh.isNotEmpty() || option.pageConfigPath.isNotEmpty()
        val opensLink = option.link.isNotEmpty() || option.activity.isNotEmpty() ||
            option.onlineHtmlPage.isNotEmpty()
        val isResetType = option.type in setOf(
            "refresh", "reload", "restart", "exit", "finish", "close", "killapp"
        )

        val typeIcon = when {
            option.type == "checkbox" -> PopupRowTypeIcon.CHECKBOX
            option.type == "spinner" -> PopupRowTypeIcon.DROPDOWN
            opensInternalPage -> PopupRowTypeIcon.PAGE
            opensLink -> PopupRowTypeIcon.LINK
            isResetType -> PopupRowTypeIcon.REFRESH
            option.type == "file" -> PopupRowTypeIcon.FILE
            option.type == "folder" -> PopupRowTypeIcon.FOLDER
            else -> PopupRowTypeIcon.SCRIPT
        }

        val leftIcon = try {
            IconPathAnalysis().loadIcon(this, option)
        } catch (_: Exception) {
            null
        }

        return PopupMenuRow(
            title = option.title,
            leftIcon = leftIcon,
            typeIcon = typeIcon,
            checked = option.checked
        ) {
            if (option.type == "checkbox") {
                option.checked = !option.checked

                val uniqueItemId = checkboxItemId(option)
                justClickedItemIds.add(uniqueItemId)

                justClickedRemovalRunnables.remove(uniqueItemId)?.let { handler.removeCallbacks(it) }
                val removalRunnable = Runnable {
                    justClickedItemIds.remove(uniqueItemId)
                    justClickedRemovalRunnables.remove(uniqueItemId)
                }
                justClickedRemovalRunnables[uniqueItemId] = removalRunnable
                handler.postDelayed(removalRunnable, 1500)
            }
            onMenuItemClick(option, anchor)
        }
    }

    private fun openHeaderActionDialog(action: ActionNode) {
        val fragment = supportFragmentManager.findFragmentById(R.id.main_list) as? ActionListFragment ?: return
        fragment.onActionClick(action, Runnable {})
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val headerAction = headerActions?.find { ("header:" + it.key).hashCode() == item.itemId }
        if (headerAction != null) {
            openHeaderActionDialog(headerAction)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun onMenuItemClick(menuOption: PageMenuOption, anchor: View? = null) {
        handler.removeCallbacks(refreshCheckboxRunnable)

        if (menuOption.link.isNotEmpty() || menuOption.activity.isNotEmpty() ||
            menuOption.onlineHtmlPage.isNotEmpty() || menuOption.pageConfigSh.isNotEmpty() ||
            menuOption.pageConfigPath.isNotEmpty()
        ) {
            openMenuOptionAsPage(menuOption)
            return
        }

        when (menuOption.type) {
            "refresh", "reload" -> triggerPageRecreate()
            "restart" -> restartApp()
            "exit", "finish", "close" -> finish()
            "killapp" -> killApp()
            "file", "folder" -> menuItemChooseFile(menuOption)
            "spinner" -> menuItemSpinner(menuOption, anchor)
            else -> {
                if (menuOption.silent) {
                    menuItemExecuteSilent(menuOption)
                } else {
                    menuItemExecute(menuOption, hashMapOf("state" to menuOption.key, "menu_id" to menuOption.key))
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        webViewLifecyclePaused = true
        applyWebViewFreezeState()
    }

    override fun onResume() {
        super.onResume()
        webViewLifecyclePaused = false
        applyWebViewFreezeState()
    }

    private fun setupToolbarLiveBlur() {
        val bar = findViewById<BlurTopBarLayout>(R.id.blur_top_container) ?: return
        bar.blurSource = binding.mainList
        bar.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) applyListTopInset(null)
        }
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
                if (f is ActionListFragment) applyListTopInset(v)
            }
        }, false)
    }

    private fun applyListTopInset(target: View?) {
        val inset = findViewById<View>(R.id.blur_top_container)?.height ?: return
        val apply = { v: View ->
            if (v.paddingTop != inset) v.setPadding(v.paddingLeft, inset, v.paddingRight, v.paddingBottom)
        }
        if (target != null) {
            apply(target)
            return
        }
        for (i in 0 until binding.mainList.childCount) {
            val child = binding.mainList.getChildAt(i)
            if (child.id == R.id.kr_content) apply(child)
        }
    }

    private fun setupWebViewScrollFreeze() {
        val listener = ViewTreeObserver.OnScrollChangedListener {
            if (scrollFreezeRunnable == null) {
                freezeWebViews()
            } else {
                handler.removeCallbacks(scrollFreezeRunnable!!)
            }
            val runnable = Runnable {
                scrollFreezeRunnable = null
                unfreezeWebViews()
            }
            scrollFreezeRunnable = runnable
            handler.postDelayed(runnable, 200)
        }
        scrollChangedListener = listener
        binding.root.viewTreeObserver.addOnScrollChangedListener(listener)
    }

    private fun forEachWebView(view: View?, action: (WebView) -> Unit) {
        when (view) {
            is WebView -> action(view)
            is ViewGroup -> for (i in 0 until view.childCount) forEachWebView(view.getChildAt(i), action)
        }
    }

    private fun freezeWebViews() {
        webViewFreezeCount++
        applyWebViewFreezeState()
    }

    private fun unfreezeWebViews() {
        if (webViewFreezeCount == 0) {
            return
        }
        webViewFreezeCount--
        applyWebViewFreezeState()
    }

    private fun applyWebViewFreezeState() {
        if (!::binding.isInitialized) {
            return
        }
        val wantPaused = webViewFreezeCount > 0 || webViewLifecyclePaused
        val wantTimersPaused = webViewFreezeCount > 0 && !webViewLifecyclePaused

        if (wantPaused != webViewsPaused) {
            webViewsPaused = wantPaused
            forEachWebView(binding.root) { if (wantPaused) it.onPause() else it.onResume() }
        }
        if (wantTimersPaused != webViewTimersPaused) {
            webViewTimersPaused = wantTimersPaused
            forEachWebView(binding.root) { if (wantTimersPaused) it.pauseTimers() else it.resumeTimers() }
        }
    }

    private fun triggerPageRecreate() {
        if (pendingSpinIcon != null) {
            return
        }
        pendingSpinIcon = binding.actionPageFab.drawable
        recreate()
    }

    private fun startFabSpin() {
        val fab = binding.actionPageFab
        pendingSpinIcon?.let { fab.setImageDrawable(it) }
        fab.visibility = View.VISIBLE
        fab.isEnabled = false
        fab.clearAnimation()
        val rotate = android.view.animation.RotateAnimation(
            0f, 360f,
            android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
            android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 600
            repeatCount = android.view.animation.Animation.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
        }
        fab.startAnimation(rotate)
    }

    private fun stopFabSpinIfPending() {
        if (pendingSpinIcon == null) return
        pendingSpinIcon = null
        if (::binding.isInitialized) {
            binding.actionPageFab.apply {
                clearAnimation()
                isEnabled = true
            }
        }
        invalidateOptionsMenu()
    }

    private fun openMenuOptionAsPage(menuOption: PageMenuOption) {
        if (menuOption.link.isNotEmpty()) {
            try {
                WebLauncher.open(this, menuOption.link)
            } catch (_: Exception) {
                Toast.makeText(this, getString(R.string.kr_slice_activity_fail), Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (menuOption.activity.isNotEmpty()) {
            TryOpenActivity(this, menuOption.activity).tryOpen()
            return
        }

        if (menuOption.onlineHtmlPage.isNotEmpty() || menuOption.pageConfigSh.isNotEmpty() || menuOption.pageConfigPath.isNotEmpty()) {
            val parentConfigPath = currentPageConfig?.currentPageConfigPath ?: ""
            val page = PageNode(parentConfigPath).apply {
                title = menuOption.title
                onlineHtmlPage = menuOption.onlineHtmlPage
                pageConfigSh = menuOption.pageConfigSh
                pageConfigPath = menuOption.pageConfigPath
            }
            OpenPageHelper(this).openPage(page)
        }
    }

    private fun menuItemExecuteSilent(
        menuOption: PageMenuOption,
        params: HashMap<String, String> = hashMapOf("state" to menuOption.key, "menu_id" to menuOption.key)
    ) {
        val config = currentPageConfig ?: return
        val script = menuOption.script

        lifecycleScope.launch(Dispatchers.IO) {
           val output = ScriptEnvironmen.executeResultRoot(this@ActionPage, script, config, params)

            if (!isActive) return@launch

            withContext(Dispatchers.Main) {
                if (isFinishing || isDestroyed) return@withContext
                if (!output.isNullOrBlank()) {
                    SilentShellOutputHandler(this@ActionPage).processOutput(output)
                }
                val activityReplaced = menuOption.autoFinish || menuOption.reloadPage || menuOption.autoKill || menuOption.autoRestart
                when {
                    menuOption.autoFinish -> finish()
                    menuOption.reloadPage -> triggerPageRecreate()
                    menuOption.autoKill -> killApp()
                    menuOption.autoRestart -> restartApp()
                }
                if (!activityReplaced && menuOption.type == "checkbox") {
                    scheduleCheckboxRefresh()
                }
            }
        }
    }

    private fun checkPageLockThenLoad() {
        if (lockCheckStarted) return
        lockCheckStarted = true

        val config = currentPageConfig ?: return

        if (config.lockShell.isNotEmpty()) {
            lockCheckJob?.cancel()
            lockCheckJob = lifecycleScope.launch(Dispatchers.IO) {
                val message = ScriptEnvironmen.executeResultRoot(this@ActionPage, config.lockShell, config)
                withContext(Dispatchers.Main) {
                    if (!isActive || isFinishing || isDestroyed) return@withContext
                    val unlocked = message == "unlock" || message == "unlocked" || message == "false" || message == "0"
                    if (unlocked) {
                        loadPageConfig(true)
                    } else {
                        val msg = if (message.isNotEmpty()) message else getString(R.string.kr_lock_message)
                        showPageLockedDialog(msg)
                    }
                }
            }
        } else if (config.locked) {
            val msg = config.lockMessage.ifEmpty { getString(R.string.kr_lock_message) }
            showPageLockedDialog(msg)
        } else {
            loadPageConfig(true)
        }
    }

    private fun showPageLockedDialog(message: String) {
        DialogHelper.helpInfo(this, getString(R.string.kr_lock_title), message) {
            progressBarDialog.hideDialog()
            finish()
        }
    }

    private fun loadPageConfig(showLoading: Boolean = true) {
        val config = currentPageConfig ?: return

        if (loadPageJob?.isActive == true) {
            pendingReloadWhileLoading = true
            return
        }

        pendingDeferredBuilder = null
        pagePrewarmIconAnalysis = IconPathAnalysis()
        progressBarDialog.setCancelCallback {
            loadPageJob?.cancel()
            finish()
        }

        val useProgressiveLoad = showLoading && config.process

        loadPageJob = lifecycleScope.launch(Dispatchers.IO) {
            if (showLoading && !useProgressiveLoad) {
                withContext(Dispatchers.Main) {
                    hideLoadProgress()
                    val initialText = if (config.beforeRead.isNotEmpty())
                        getString(R.string.kr_page_before_load) else getString(R.string.kr_page_loading)
                    progressBarDialog.showDialog(initialText)
                }
            }

            if (config.beforeRead.isNotEmpty()) {
                ScriptEnvironmen.executeResultRoot(this@ActionPage, config.beforeRead, config)
                if (showLoading && !useProgressiveLoad) {
                    withContext(Dispatchers.Main) {
                        progressBarDialog.showDialog(getString(R.string.kr_page_loading))
                    }
                }
            }

            var progressiveFragment: ActionListFragment? = null
            if (useProgressiveLoad) {
                withContext(Dispatchers.Main) {
                    progressiveFragment = beginProgressiveList(config.placeholderCount)
                    loadProgressBar.apply {
                        isIndeterminate = true
                        visibility = View.VISIBLE
                    }
                }
            }

            val onNodeReady: ((NodeInfoBase?, Int, Int) -> Unit)? = if (useProgressiveLoad) {
                { node, _, _ ->
                    if (node != null) {
                        try { prewarmNodeImages(node) } catch (_: Exception) {}
                    }
                    if (!isFinishing && !isDestroyed) {
                        handler.post {
                            if (!isFinishing && !isDestroyed) {
                                if (node != null) progressiveFragment?.appendProgressiveItem(node)
                            }
                        }
                    }
                }
            } else null

            var items: ArrayList<NodeInfoBase>? = null
            var loadedMenuOptions: ArrayList<PageMenuOption>? = null
            var loadedHeaderActions: ArrayList<ActionNode>? = null
            var loadedAutoShowActions: ArrayList<ActionNode>? = null
            var loadedMenuIcon: ClickableNode? = null
            var loadedFabIcon: ClickableNode? = null
            if (config.pageConfigSh.isNotEmpty()) {
                val shReader = PageConfigSh(this@ActionPage, config.pageConfigSh, config)
                items = shReader.execute(onNodeReady)
                loadedMenuOptions = shReader.pageMenuOptions
                loadedHeaderActions = shReader.headerActions
                loadedAutoShowActions = shReader.autoShowActions
                loadedMenuIcon = shReader.menuIcon
                loadedFabIcon = shReader.fabIcon
                if (shReader.hasDeferredEntries) pendingDeferredBuilder = { shReader.buildDeferredNodes() }
            }
            if (items == null && config.pageConfigPath.isNotEmpty()) {
                val reader = PageConfigReader(applicationContext, config.pageConfigPath, config.pageConfigDir)
                items = reader.readConfigXml(onNodeReady)
                loadedMenuOptions = reader.pageMenuOptions
                loadedHeaderActions = reader.headerActions
                loadedAutoShowActions = reader.autoShowActions
                loadedMenuIcon = reader.menuIcon
                loadedFabIcon = reader.fabIcon
                if (reader.hasDeferredEntries) pendingDeferredBuilder = { reader.buildDeferredNodes() }
            }
            config.pageMenuOptions = loadedMenuOptions
            config.headerActions = loadedHeaderActions
            config.autoShowActions = loadedAutoShowActions
            config.menuIconNode = loadedMenuIcon
            config.fabIconNode = loadedFabIcon

            if (config.afterRead.isNotEmpty()) {
                ScriptEnvironmen.executeResultRoot(this@ActionPage, config.afterRead, config)
            }

            if (showLoading && !useProgressiveLoad) {
                items?.forEach { node -> try { prewarmNodeImages(node) } catch (_: Exception) {} }
            }

            withContext(Dispatchers.Main) {
                if (!isActive || isFinishing) return@withContext

                val hasMenuOrFab = loadedMenuOptions?.isNotEmpty() == true || loadedHeaderActions?.isNotEmpty() == true
                if (items != null && (items.isNotEmpty() || hasMenuOrFab)) {
                    if (config.loadSuccess.isNotEmpty()) {
                        ScriptEnvironmen.executeResultRoot(this@ActionPage, config.loadSuccess, config)
                    }
                    progressBarDialog.hideDialog()
                    val hasDeferredLoad = pendingDeferredBuilder != null
                    if (useProgressiveLoad) {
                        progressiveFragment?.finishProgressiveList()
                        actionsLoaded = true
                        if (!hasDeferredLoad) hideLoadProgress()
                        tryAutoShowActions()
                    } else if (showLoading) {
                        rebuildMenuAfterLoad()
                        loadProgressBar.apply {
                            isIndeterminate = true
                            visibility = View.VISIBLE
                        }
                        val skeletonCount = if (config.placeholderCount > 1) config.placeholderCount
                        else items.size.coerceAtMost(SKELETON_AFTER_DIALOG_MAX)
                        val fragment = beginProgressiveList(skeletonCount)
                        if (items.isNotEmpty()) delay(SKELETON_FIRST_FRAME_DELAY_MS)
                        for (node in items) {
                            if (!isActive || isFinishing || isDestroyed) return@withContext
                            fragment.appendProgressiveItem(node)
                            yield()
                        }
                        fragment.finishPrebuiltList()
                        actionsLoaded = true
                        if (!hasDeferredLoad) hideLoadProgress()
                        tryAutoShowActions()
                    } else {
                        updateActionList(items, showLoading) { tryAutoShowActions() }
                    }
                    rebuildMenuAfterLoad()
                } else {
                    handleLoadError(config)
                    hideLoadProgress()
                    progressBarDialog.hideDialog()
                }
            }

            if (pendingReloadWhileLoading) {
                pendingReloadWhileLoading = false
                withContext(Dispatchers.Main) {
                    if (!isFinishing && !isDestroyed) {
                        loadPageJob = null
                        loadPageConfig(true)
                    }
                }
            }
        }
    }

    private fun rebuildMenuAfterLoad() {
        menuOptions = null
        headerActions = null
        invalidateOptionsMenu()
        scheduleCheckboxRefresh()
    }

    private fun prewarmNodeImages(node: NodeInfoBase) {
        val iconPathAnalysis = pagePrewarmIconAnalysis
        when (node) {
            is TextNode -> {
                node.rows.forEach { row ->
                    try { iconPathAnalysis.loadtextPhoto(this, row.photo, row, node.pageConfigDir) } catch (_: Exception) {}
                }
            }
            is GroupNode -> {
                node.children.forEach { child -> prewarmNodeImages(child) }
            }
            is ClickableNode -> {
                try { iconPathAnalysis.loadIcon(this, node) } catch (_: Exception) {}
                try { iconPathAnalysis.loadLogo(this, node, false) } catch (_: Exception) {}
                try { iconPathAnalysis.loadPhoto(this, node) } catch (_: Exception) {}
                try { iconPathAnalysis.loadBg(this, node) } catch (_: Exception) {}
            }
        }
    }

    private fun tryAutoShowActions() {
        stopFabSpinIfPending()
        startDeferredLoadIfNeeded()
        if (autoShowTriggered) return
        val toShow = currentPageConfig?.autoShowActions?.filter { it.show && !ActionShowMemory.isConfirmed(this, it) }.orEmpty()
        if (toShow.isEmpty()) return
        autoShowTriggered = true
        val fragment = supportFragmentManager.findFragmentById(R.id.main_list) as? ActionListFragment ?: return
        toShow.forEach { fragment.onActionClick(it, Runnable {}, true) }
    }

    private fun startDeferredLoadIfNeeded() {
        val builder = pendingDeferredBuilder ?: return
        pendingDeferredBuilder = null
        lifecycleScope.launch(Dispatchers.IO) {
            val results: ArrayList<PageConfigReader.DeferredNodeResult> = try { builder() } catch (_: Exception) { ArrayList() }
            if (!isActive) return@launch
            results.forEach { result -> try { prewarmNodeImages(result.node) } catch (_: Exception) {} }
            withContext(Dispatchers.Main) {
                if (isFinishing || isDestroyed) return@withContext
                hideLoadProgress()
                val fragment = supportFragmentManager.findFragmentById(R.id.main_list) as? ActionListFragment ?: return@withContext
                results.forEach { fragment.appendLateItem(it.group, it.node, it.index) }
                if (results.isNotEmpty()) {
                    invalidateOptionsMenu()
                }
            }
        }
    }

    private fun buildAutoRunTask(): AutoRunTask? {
        return if (actionsLoaded) null else object : AutoRunTask {
            override val key = autoRunItemId
            override fun onCompleted(result: Boolean?) {
                if (result != true && autoRunItemId.isNotEmpty()) {
                    Toast.makeText(this@ActionPage, getString(R.string.kr_auto_run_item_losted), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun beginProgressiveList(placeholderCount: Int): ActionListFragment {
        val fragment = ActionListFragment.createProgressive(actionShortClickHandler, buildAutoRunTask(), ThemeModeState.getThemeMode(), placeholderCount)
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_list, fragment)
            .commitAllowingStateLoss()
        return fragment
    }

    private fun hideLoadProgress() {
        loadProgressBar.visibility = View.GONE
    }

    private var rowRunProgressCount = 0

    override fun showRowRunProgress() {
        rowRunProgressCount++
        loadProgressBar.apply {
            isIndeterminate = true
            visibility = View.VISIBLE
        }
    }

    override fun hideRowRunProgress() {
        rowRunProgressCount = (rowRunProgressCount - 1).coerceAtLeast(0)
        if (rowRunProgressCount == 0) {
            hideLoadProgress()
        }
    }

    private fun updateActionList(items: ArrayList<NodeInfoBase>, showLoading: Boolean, onRendered: (() -> Unit)? = null) {
        val existingFragment = supportFragmentManager.findFragmentById(R.id.main_list) as? ActionListFragment
        if (existingFragment != null && !showLoading) {
            existingFragment.updateData(items, actionShortClickHandler, ThemeModeState.getThemeMode(), onRendered)
        } else {
            val fragment = ActionListFragment.create(items, actionShortClickHandler, buildAutoRunTask(), ThemeModeState.getThemeMode(), onRendered)
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_list, fragment)
                .commitAllowingStateLoss()
        }
        actionsLoaded = true
    }

    private fun handleLoadError(config: PageNode) {
        pendingSpinIcon = null
        if (config.loadFail.isNotEmpty()) {
            ScriptEnvironmen.executeResultRoot(this, config.loadFail, config)
        }
        Toast.makeText(this, getString(R.string.kr_page_load_fail), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun restartApp() {
        val intent = Intent(this, SplashActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("force_reset", true)
        }
        startActivity(intent)
        finish()
    }

    private fun killApp() {
        startService(Intent(this, WakeLockService::class.java).apply {
            action = WakeLockService.ACTION_END_WAKELOCK
        })
        finishAffinity()
        System.exit(0)
    }

    private fun menuItemExecute(menuOption: PageMenuOption, params: HashMap<String, String>) {
        val onDismiss = Runnable {
            val activityReplaced = menuOption.autoFinish || menuOption.reloadPage || menuOption.autoKill || menuOption.autoRestart
            when {
                menuOption.autoFinish -> finish()
                menuOption.reloadPage -> triggerPageRecreate()
                menuOption.autoKill -> killApp()
                menuOption.autoRestart -> restartApp()
            }
            if (!activityReplaced && menuOption.type == "checkbox") {
                scheduleCheckboxRefresh()
            }
        }

        val script = menuOption.script
        val dialog = DialogLogFragment.create(
            menuOption,
            {},
            onDismiss,
            script,
            params,
            ThemeModeState.getThemeMode().isDarkMode
        )
        dialog.show(supportFragmentManager, "")
        dialog.isCancelable = false
    }

    private fun menuItemChooseFile(menuOption: PageMenuOption) {
        chooseFilePath(object : ParamsFileChooserRender.FileSelectedInterface {
            override fun onFileSelected(path: String?) {
                path?.let {
                    handler.post {
                        menuItemExecute(
                            menuOption,
                            hashMapOf(
                                "state" to menuOption.key,
                                "menu_id" to menuOption.key,
                                "file" to it,
                                "folder" to it
                            )
                        )
                    }
                }
            }

            override fun mimeType() = menuOption.mime.ifEmpty { null }
            override fun suffix() = menuOption.suffix.ifEmpty { null }
            override fun pathHome() = menuOption.pathHome.ifEmpty { null }
            override fun multiple() = menuOption.multiple
            override fun type() = if (menuOption.type == "folder") {
                ParamsFileChooserRender.FileSelectedInterface.TYPE_FOLDER
            } else {
                ParamsFileChooserRender.FileSelectedInterface.TYPE_FILE
            }
        })
    }

    private fun menuItemSpinner(menuOption: PageMenuOption, anchor: View? = null) {
        val config = currentPageConfig ?: return
        val resolvedAnchor = anchor ?: findViewById<View>(R.id.toolbar) ?: binding.root

        progressBarDialog.setCancelCallback { spinnerLoadJob?.cancel() }
        progressBarDialog.showDialog(getString(R.string.kr_param_options_load) + " ")

        spinnerLoadJob = lifecycleScope.launch(Dispatchers.IO) {
            val scripts = LinkedHashMap<String, String>()
            if (menuOption.spinnerGetState.isNotEmpty()) {
                scripts["state"] = menuOption.spinnerGetState
            }
            if (menuOption.optionsSh.isNotEmpty()) {
                scripts["options"] = menuOption.optionsSh
            }

            val shellResults = if (scripts.isNotEmpty()) {
                ScriptEnvironmen.executeMultipleResultRoot(this@ActionPage, scripts, config)
            } else {
                LinkedHashMap()
            }

            if (!isActive) return@launch

            val options = parseSpinnerOptions(menuOption, shellResults["options"])
            val currentValue = shellResults["state"]?.trim()

            withContext(Dispatchers.Main) {
                progressBarDialog.hideDialog()
                if (isFinishing || isDestroyed) return@withContext
                if (options.isNullOrEmpty()) {
                    Toast.makeText(this@ActionPage, getString(R.string.picker_not_item), Toast.LENGTH_SHORT).show()
                } else {
                    showSpinnerPopup(resolvedAnchor, menuOption, options, currentValue)
                }
            }
        }
    }

    private fun parseSpinnerOptions(menuOption: PageMenuOption, shellResult: String?): ArrayList<SelectItem>? {
        val result = shellResult ?: ""
        if (result == "error" || result == "null" || result.isEmpty()) {
            return menuOption.options
        }
        val options = ArrayList<SelectItem>()
        for (line in result.split("\n").filter { it.isNotEmpty() }) {
            if (line.contains("|")) {
                val split = line.split("|")
                options.add(SelectItem().apply {
                    value = split[0]
                    title = if (split.size > 1) split[1] else split[0]
                })
            } else {
                options.add(SelectItem().apply { title = line; value = line })
            }
        }
        return options
    }

    private fun showSpinnerPopup(
        anchor: View,
        menuOption: PageMenuOption,
        options: ArrayList<SelectItem>,
        currentValue: String?
    ) {
        val selectedIndex = options.indexOfFirst { it.value == currentValue }.let { if (it < 0) 0 else it }

        if (options.size > 6) {
            showSpinnerDialog(menuOption, options, selectedIndex)
            return
        }

        val adapter = ArrayAdapter(this, R.layout.kr_spinner_dropdown, R.id.text, options)
        val background = ContextCompat.getDrawable(this, R.drawable.kr_spinner_popup_bg)

        val popup = ListPopupWindow(this)
        popup.anchorView = anchor
        popup.setAdapter(adapter)
        popup.setBackgroundDrawable(background)
        popup.isModal = true
        popup.setOnItemClickListener { _, _, position, _ ->
            popup.dismiss()
            val selected = options.getOrNull(position) ?: return@setOnItemClickListener
            val value = selected.value ?: selected.title ?: ""
            val params = hashMapOf("state" to value, "menu_id" to menuOption.key)
            if (menuOption.silent) {
                menuItemExecuteSilent(menuOption, params)
            } else {
                menuItemExecute(menuOption, params)
            }
        }

        val extraTopGapPx = if (anchor === binding.actionPageFab) fabPopupGap() else 0
        val itemViews = options.map { option ->
            layoutInflater.inflate(R.layout.kr_spinner_dropdown, anchor.parent as? android.view.ViewGroup, false).apply {
                findViewById<android.widget.TextView>(R.id.text).text = option.toString()
            }
        }
        val minWidthPx = (resources.displayMetrics.density * 200).toInt()
        SpinnerPopupHelper.applyWidthAndPosition(
            popup, anchor, itemViews, background, minWidthPx, alignRight = true,
            extraTopGapPx = extraTopGapPx, applyVerticalOffset = true
        )

        popup.show()
        SpinnerPopupHelper.applyRoundedClip(popup, resources.getDimension(R.dimen.kr_spinner_popup_radius))
        if (selectedIndex in options.indices) {
            popup.listView?.setSelection(selectedIndex)
        }
    }

    private fun showSpinnerDialog(
        menuOption: PageMenuOption,
        options: ArrayList<SelectItem>,
        selectedIndex: Int
    ) {
        val darkMode = ThemeModeState.getThemeMode().isDarkMode
        DialogItemChooser(darkMode, ArrayList(options.mapIndexed { index, item ->
            SelectItem().apply {
                title = item.title
                selected = index == selectedIndex
            }
        }), false, object : DialogItemChooser.Callback {
            override fun onConfirm(selected: List<SelectItem>, status: BooleanArray) {
                val confirmedIndex = status.indexOf(true)
                val option = options.getOrNull(confirmedIndex) ?: return
                val value = option.value ?: option.title ?: ""
                val params = hashMapOf("state" to value, "menu_id" to menuOption.key)
                if (menuOption.silent) {
                    menuItemExecuteSilent(menuOption, params)
                } else {
                    menuItemExecute(menuOption, params)
                }
            }
        }).show(supportFragmentManager, "action-page-spinner")
    }

    private fun showListPopup(anchor: View, rows: List<PopupMenuRow>, extraTopGapPx: Int = 0) {
        if (rows.isEmpty()) return

        val adapter = PopupMenuListAdapter(this, rows)
        val background = ContextCompat.getDrawable(this, R.drawable.kr_spinner_popup_bg)

        val popup = ListPopupWindow(this)
        popup.anchorView = anchor
        popup.setAdapter(adapter)
        popup.setBackgroundDrawable(background)
        popup.isModal = true
        popup.setOnItemClickListener { _, _, position, _ ->
            popup.dismiss()
            rows.getOrNull(position)?.onClick?.invoke()
        }

        val parent = anchor.parent as? android.view.ViewGroup
        val itemViews = rows.indices.map { adapter.getView(it, null, parent) }
        val minWidthPx = (resources.displayMetrics.density * 200).toInt()
        SpinnerPopupHelper.applyWidthAndPosition(
            popup, anchor, itemViews, background, minWidthPx, alignRight = true,
            extraTopGapPx = extraTopGapPx, applyVerticalOffset = true
        )

        popup.show()
        SpinnerPopupHelper.applyRoundedClip(popup, resources.getDimension(R.dimen.kr_spinner_popup_radius))
    }

    private fun showFabChooser(fabOptions: List<PageMenuOption>) {
        val anchor = binding.actionPageFab
        val rows = fabOptions.map { buildPopupRow(it, anchor) }
        showListPopup(anchor, rows, fabPopupGap())
    }

    private fun fabPopupGap(): Int = (8 * resources.displayMetrics.density).toInt()

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
                    val mimeTypes = fileSelectedInterface.mimeType()
                        ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toTypedArray()
                        ?: emptyArray()

                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        if (mimeTypes.size > 1) {
                            type = "*/*"
                            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
                        } else {
                            type = mimeTypes.firstOrNull() ?: "*/*"
                        }
                        addCategory(Intent.CATEGORY_OPENABLE)
                        if (multiple) {
                            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        }
                    }
                    startActivityForResult(intent, ACTION_FILE_PATH_CHOOSER)
                }
            }
            this.fileSelectedInterface = fileSelectedInterface
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
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
        }
        fileSelectedInterface = null
        super.onActivityResult(requestCode, resultCode, data)
    }

    fun _openPage(pageNode: PageNode) {
        OpenPageHelper(this).openPage(pageNode)
    }

    @Suppress("DEPRECATION")
    override fun onRetainCustomNonConfigurationInstance(): Any? = swipePreview

    override fun onDestroy() {
        if (isFinishing) {
            pendingSpinIcon = null
        }
        checkboxRefreshJob?.cancel()
        lockCheckJob?.cancel()
        handler.removeCallbacksAndMessages(null)
        if (::binding.isInitialized) {
            scrollChangedListener?.let { binding.root.viewTreeObserver.removeOnScrollChangedListener(it) }
            scrollFreezeRunnable = null
            webViewFreezeCount = 0
            webViewLifecyclePaused = true
            applyWebViewFreezeState()
        }
        if (::swipeBackHelper.isInitialized) swipeBackHelper.release()
        if (isFinishing && ::binding.isInitialized) {
            recycleImageViewBitmap(binding.swipeBackPreviewBlur)
            recycleImageViewBitmap(binding.swipeBackPreviewSharp)
        }
        setExcludeFromRecents()
        super.onDestroy()
    }

    private fun recycleImageViewBitmap(imageView: android.widget.ImageView) {
        val drawable = imageView.drawable
        if (drawable is android.graphics.drawable.BitmapDrawable) {
            imageView.setImageDrawable(null)
            drawable.bitmap?.takeIf { !it.isRecycled }?.recycle()
        }
    }

    private fun setExcludeFromRecents() {
        if (isTaskRoot) {
            try {
                val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                am.appTasks.find { it.taskInfo?.id == taskId }?.setExcludeFromRecents(true)
            } catch (_: Exception) {}
        }
    }
}