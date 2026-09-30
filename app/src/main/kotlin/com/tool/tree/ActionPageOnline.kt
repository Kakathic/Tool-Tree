package com.tool.tree

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Menu
import android.view.MenuItem
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebSettingsCompat.FORCE_DARK_OFF
import androidx.webkit.WebSettingsCompat.FORCE_DARK_ON
import androidx.webkit.WebViewFeature
import com.omarea.common.shared.FilePathResolver
import com.omarea.common.ui.DialogHelper
import com.omarea.krscript.WebViewInjector
import com.omarea.krscript.ui.ParamsFileChooserRender
import com.tool.tree.databinding.ActivityActionPageOnlineBinding
import com.tool.tree.ui.OverflowMenuPopup
import com.tool.tree.ui.PopupMenuRow
import com.tool.tree.ui.PopupRowTypeIcon

class ActionPageOnline : AppCompatActivity() {
    private lateinit var binding: ActivityActionPageOnlineBinding
    private val loadProgressBar by lazy { findViewById<ProgressBar>(R.id.page_load_progress) }
    private var fileSelectedInterface: ParamsFileChooserRender.FileSelectedInterface? = null
    private val ACTION_FILE_PATH_CHOOSER = 65400
    private val MENU_FIND_VIEW = 1005
    private val MENU_FIND_PREV = 1003
    private val MENU_FIND_NEXT = 1004

    private var findItem: MenuItem? = null
    private var findPrevItem: MenuItem? = null
    private var findNextItem: MenuItem? = null
    private var findQuery = ""

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var videoContainer: FrameLayout? = null
    private var readingMode = false

    private val backHandler = Handler(Looper.getMainLooper())
    private var backOverlay: ImageView? = null
    private var backOverlayBitmap: Bitmap? = null
    private var backBusy = false
    private var backLoadStarted = false
    private var backRequestId = 0L
    private val backNoLoadRunnable = Runnable { removeBackOverlay() }
    private val backSafetyRunnable = Runnable { removeBackOverlay() }

    // Trạng thái tự ẩn/hiện toolbar khi cuộn (fraction: 0 = hiện, 1 = ẩn hoàn toàn)
    private var toolbarFraction = 0f
    private var toolbarHidden = false
    private var toolbarAnimator: ValueAnimator? = null
    private var toolbarShowPending = false
    private var scrollAccum = 0
    private var ignoreScrollUntil = 0L

    private companion object {
        const val BACK_NO_LOAD_TIMEOUT = 600L
        const val BACK_SAFETY_TIMEOUT = 10000L
        const val TOOLBAR_HIDE_DP = 48
        const val TOOLBAR_SHOW_DP = 24
        const val TOOLBAR_ANIM_DURATION = 200L
        const val TOOLBAR_SETTLE_MS = 150L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeModeState.switchTheme(this)

        binding = ActivityActionPageOnlineBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar: Toolbar = binding.webappbar.toolbar
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        toolbar.setNavigationOnClickListener {
            finish()
        }
        setupToolbarMenu(toolbar)
        setTitle(R.string.app_name)

        onBackPressedDispatcher.addCallback(this) {
            when {
                customView != null -> hideCustomView()
                findItem?.isActionViewExpanded == true -> handleSearchBack()
                readingMode -> exitReadingMode()
                binding.krOnlineWebview.canGoBack() -> goBackKeepCurrentPage()
                else -> finish()
            }
        }

        binding.krOnlineWebview.setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
            binding.webappbar.toolbar.subtitle = when {
                findQuery.isEmpty() -> null
                numberOfMatches > 0 -> "${activeMatchOrdinal + 1}/$numberOfMatches"
                else -> "0/0"
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            binding.krOnlineWebview.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                onWebScroll(scrollY, oldScrollY)
            }
        }

        loadIntentData()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        resetToolbar()
    }

    override fun onTitleChanged(title: CharSequence?, color: Int) {
        super.onTitleChanged(title, color)
        if (::binding.isInitialized) binding.webappbar.toolbar.title = title
    }

    private fun setupToolbarMenu(toolbar: Toolbar) {
        val menu = toolbar.menu

        val searchView = SearchView(toolbar.context).apply {
            queryHint = getString(R.string.online_find_in_page)
            maxWidth = Int.MAX_VALUE
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    binding.krOnlineWebview.findNext(true)
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    findInPage(newText.orEmpty())
                    return true
                }
            })
        }

        findItem = menu.add(0, MENU_FIND_VIEW, 0, R.string.online_find_in_page).apply {
            setIcon(R.drawable.ic_search_web)
            actionView = searchView
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW)
            isVisible = false
            setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                    findPrevItem?.isVisible = true
                    findNextItem?.isVisible = true
                    return true
                }

                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    findPrevItem?.isVisible = false
                    findNextItem?.isVisible = false
                    clearFind()
                    toolbar.post { item.isVisible = false }
                    return true
                }
            })
        }

        findPrevItem = menu.add(0, MENU_FIND_PREV, 1, "▲").apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            isVisible = false
        }
        findNextItem = menu.add(0, MENU_FIND_NEXT, 2, "▼").apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            isVisible = false
        }

        val overflowItem = menu.add(Menu.NONE, Menu.NONE, 10, getString(R.string.kr_more_options))
        overflowItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        val overflowButton = OverflowMenuPopup.buildButton(this)
        overflowButton.setOnClickListener { showOnlineOverflowPopup(overflowButton) }
        overflowItem.actionView = overflowButton

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_FIND_PREV -> {
                    binding.krOnlineWebview.findNext(false)
                    true
                }
                MENU_FIND_NEXT -> {
                    binding.krOnlineWebview.findNext(true)
                    true
                }
                else -> false
            }
        }
    }

    private fun showOnlineOverflowPopup(anchor: View) {
        val rows = listOf(
            PopupMenuRow(
                title = getString(R.string.online_find_in_page),
                leftIcon = ContextCompat.getDrawable(this, R.drawable.ic_search_web),
                typeIcon = PopupRowTypeIcon.NONE,
                checked = false
            ) { startFind() },
            PopupMenuRow(
                title = getString(R.string.online_fullscreen),
                leftIcon = ContextCompat.getDrawable(this, R.drawable.ic_expand_fullscreen),
                typeIcon = PopupRowTypeIcon.NONE,
                checked = false
            ) { enterReadingMode() },
            PopupMenuRow(
                title = getString(R.string.open_in_browser),
                leftIcon = null,
                typeIcon = PopupRowTypeIcon.LINK,
                checked = false
            ) { openInDefaultBrowser() }
        )
        OverflowMenuPopup.show(this, anchor, rows)
    }

    private fun startFind() {
        setToolbarHidden(false)
        val toolbar = binding.webappbar.toolbar
        toolbar.post {
            findItem?.let {
                it.isVisible = true
                it.expandActionView()
            }
        }
    }

    private fun findInPage(query: String) {
        findQuery = query
        if (query.isEmpty()) {
            binding.krOnlineWebview.clearMatches()
            binding.webappbar.toolbar.subtitle = null
        } else {
            binding.krOnlineWebview.findAllAsync(query)
        }
    }

    private fun handleSearchBack() {
        val find = findItem ?: return
        if (!find.isActionViewExpanded) return
        if (isKeyboardShowing()) {
            hideFindKeyboard()
        } else {
            find.collapseActionView()
        }
    }

    private fun isKeyboardShowing(): Boolean {
        val hasFocus = (findItem?.actionView as? SearchView)?.hasFocus() == true
        val imeShown = ViewCompat.getRootWindowInsets(binding.root)?.isVisible(WindowInsetsCompat.Type.ime())
        return hasFocus && (imeShown ?: true)
    }

    private fun hideFindKeyboard() {
        (findItem?.actionView as? SearchView)?.clearFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    private fun clearFind() {
        findQuery = ""
        binding.krOnlineWebview.clearMatches()
        binding.webappbar.toolbar.subtitle = null
    }

    private fun openInDefaultBrowser() {
        val currentUrl = binding.krOnlineWebview.url
        if (!currentUrl.isNullOrEmpty()) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, R.string.online_browser_not_found, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadIntentData() {
        val intent = this.intent
        val extras = intent.extras
        if (extras != null) {
            if (extras.containsKey("title")) {
                title = extras.getString("title")
            }

            when {
                extras.containsKey("config") -> initWebview(extras.getString("config"))
                extras.containsKey("url") -> initWebview(extras.getString("url"))
            }
        }
    }

    private fun initWebview(url: String?) {
        binding.krOnlineWebview.visibility = View.VISIBLE
        val settings = binding.krOnlineWebview.settings

        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            val isDark = ThemeModeState.isDarkMode()
            WebSettingsCompat.setForceDark(settings, if (isDark) FORCE_DARK_ON else FORCE_DARK_OFF)
        }

        val webViewInjector = WebViewInjector(binding.krOnlineWebview,
            object : ParamsFileChooserRender.FileChooserInterface {
                override fun openFileChooser(fileSelectedInterface: ParamsFileChooserRender.FileSelectedInterface): Boolean {
                    return chooseFilePath(fileSelectedInterface)
                }
            })

        binding.krOnlineWebview.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
                if (view == null || customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                showCustomView(view, callback)
            }

            override fun onHideCustomView() {
                hideCustomView()
            }

            override fun getDefaultVideoPoster(): Bitmap? {
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress < 100) {
                    loadProgressBar.isIndeterminate = false
                    loadProgressBar.progress = newProgress
                    loadProgressBar.visibility = View.VISIBLE
                } else {
                    loadProgressBar.visibility = View.GONE
                }
            }

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                DialogHelper.alert(
                    context = this@ActionPageOnline,
                    message = message.orEmpty(),
                    onConfirm = Runnable { result?.confirm() },
                    cancelable = false
                )
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                DialogHelper.confirm(
                    context = this@ActionPageOnline,
                    title = "",
                    message = message.orEmpty(),
                    contentView = null,
                    onConfirm = DialogHelper.DialogButton(getString(R.string.btn_confirm), Runnable { result?.confirm() }),
                    onCancel = DialogHelper.DialogButton(getString(R.string.btn_cancel), Runnable { result?.cancel() }),
                    cancelable = false
                )
                return true
            }
        }

        binding.krOnlineWebview.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                loadProgressBar.visibility = View.GONE
                view?.title?.let { setTitle(it) }
                if (view != null && backOverlay != null && backLoadStarted) {
                    releaseBackOverlayWhenPainted(view)
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                loadProgressBar.isIndeterminate = true
                loadProgressBar.visibility = View.VISIBLE
                if (backOverlay != null) {
                    backLoadStarted = true
                    backHandler.removeCallbacks(backNoLoadRunnable)
                }
                showToolbarOrDefer()
                findItem?.takeIf { it.isActionViewExpanded }?.collapseActionView()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return try {
                    val requestUrl = request?.url
                    if (requestUrl != null && requestUrl.scheme?.startsWith("http") != true) {
                        val intent = Intent(Intent.ACTION_VIEW, requestUrl)
                        startActivity(intent)
                        true
                    } else {
                        super.shouldOverrideUrlLoading(view, request)
                    }
                } catch (_: Exception) {
                    super.shouldOverrideUrlLoading(view, request)
                }
            }
        }

        webViewInjector.inject(this, url?.startsWith("file:///android_asset") == true)

        url?.let { binding.krOnlineWebview.loadUrl(it) }
    }

    private fun showCustomView(view: View, callback: WebChromeClient.CustomViewCallback?) {
        findItem?.takeIf { it.isActionViewExpanded }?.collapseActionView()
        val container = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            layoutParams = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(view, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
        binding.root.addView(container)
        videoContainer = container
        customView = view
        customViewCallback = callback
        setSystemBarsHidden(true)
    }

    private fun hideCustomView() {
        if (customView == null) return
        videoContainer?.let {
            it.removeAllViews()
            (it.parent as? ViewGroup)?.removeView(it)
        }
        videoContainer = null
        customView = null
        val callback = customViewCallback
        customViewCallback = null
        callback?.onCustomViewHidden()
        if (!readingMode) setSystemBarsHidden(false)
    }

    private fun enterReadingMode() {
        if (readingMode || backBusy || customView != null) return
        findItem?.takeIf { it.isActionViewExpanded }?.collapseActionView()
        readingMode = true
        resetToolbar()
        binding.webappbar.root.visibility = View.GONE
        updateWebViewTopRule(belowToolbar = false)
        setSystemBarsHidden(true)
        Toast.makeText(this, R.string.online_fullscreen_hint, Toast.LENGTH_SHORT).show()
    }

    private fun exitReadingMode() {
        if (!readingMode) return
        readingMode = false
        binding.webappbar.root.visibility = View.VISIBLE
        updateWebViewTopRule(belowToolbar = true)
        setSystemBarsHidden(false)
    }

    // Cuộn xuống quá ngưỡng thì ẩn toolbar, cuộn ngược lên một đoạn hoặc về đầu trang thì hiện lại
    private fun onWebScroll(scrollY: Int, oldScrollY: Int) {
        if (SystemClock.uptimeMillis() < ignoreScrollUntil || !canAutoHideToolbar()) return
        if (scrollY <= 0) {
            scrollAccum = 0
            setToolbarHidden(false)
            return
        }
        val dy = scrollY - oldScrollY
        if (dy == 0) return
        if ((dy > 0) != (scrollAccum > 0)) scrollAccum = 0
        scrollAccum += dy
        val density = resources.displayMetrics.density
        if (!toolbarHidden && scrollAccum > TOOLBAR_HIDE_DP * density) {
            setToolbarHidden(true)
        } else if (toolbarHidden && scrollAccum < -TOOLBAR_SHOW_DP * density) {
            setToolbarHidden(false)
        }
    }

    private fun canAutoHideToolbar(): Boolean {
        return !readingMode && customView == null && backOverlay == null &&
            findItem?.isActionViewExpanded != true
    }

    private fun setToolbarHidden(hidden: Boolean) {
        if (toolbarHidden == hidden) return
        if (binding.webappbar.root.height <= 0) return
        toolbarHidden = hidden
        scrollAccum = 0
        toolbarAnimator?.cancel()
        val animator = ValueAnimator.ofFloat(toolbarFraction, if (hidden) 1f else 0f)
        animator.duration = TOOLBAR_ANIM_DURATION
        animator.addUpdateListener { applyToolbarFraction(it.animatedValue as Float) }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                ignoreScrollUntil = SystemClock.uptimeMillis() + TOOLBAR_SETTLE_MS
            }
        })
        // Bỏ qua sự kiện cuộn do WebView đổi kích thước trong lúc/ngay sau animation (tránh nhấp nháy)
        ignoreScrollUntil = SystemClock.uptimeMillis() + TOOLBAR_ANIM_DURATION + TOOLBAR_SETTLE_MS
        toolbarAnimator = animator
        animator.start()
    }

    // Trượt toolbar lên và kéo WebView lên theo; chừa lại vùng status bar phía trên WebView
    private fun applyToolbarFraction(fraction: Float) {
        toolbarFraction = fraction
        val bar = binding.webappbar.root
        val statusTop = binding.webappbar.blurTopContainer.paddingTop
        bar.translationY = -bar.height * fraction
        val webView = binding.krOnlineWebview
        val params = webView.layoutParams as? RelativeLayout.LayoutParams ?: return
        params.topMargin = -((bar.height - statusTop) * fraction).toInt()
        webView.layoutParams = params
    }

    private fun resetToolbar() {
        toolbarAnimator?.cancel()
        toolbarAnimator = null
        toolbarHidden = false
        toolbarShowPending = false
        scrollAccum = 0
        applyToolbarFraction(0f)
    }

    // Đang có overlay của nút Back thì hoãn hiện toolbar đến khi overlay được gỡ
    private fun showToolbarOrDefer() {
        if (backOverlay != null) toolbarShowPending = true else setToolbarHidden(false)
    }

    private fun updateWebViewTopRule(belowToolbar: Boolean) {
        val webView = binding.krOnlineWebview
        val params = webView.layoutParams as? RelativeLayout.LayoutParams ?: return
        if (belowToolbar) {
            params.removeRule(RelativeLayout.ALIGN_PARENT_TOP)
            params.addRule(RelativeLayout.BELOW, R.id.webappbar)
        } else {
            params.removeRule(RelativeLayout.BELOW)
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP)
        }
        webView.layoutParams = params
    }

    private fun setSystemBarsHidden(hidden: Boolean) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (hidden) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val attrs = window.attributes
            attrs.layoutInDisplayCutoutMode = if (hidden) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            }
            window.attributes = attrs
        }
    }

    private fun goBackKeepCurrentPage() {
        if (backBusy) return
        backBusy = true
        captureWebView { bitmap ->
            if (isFinishing || isDestroyed) {
                backBusy = false
                return@captureWebView
            }
            val webView = binding.krOnlineWebview
            val shown = bitmap != null && showBackOverlay(bitmap)
            if (!shown) backBusy = false
            backLoadStarted = false
            if (shown) {
                backHandler.postDelayed(backNoLoadRunnable, BACK_NO_LOAD_TIMEOUT)
                backHandler.postDelayed(backSafetyRunnable, BACK_SAFETY_TIMEOUT)
            }
            if (webView.canGoBack()) webView.goBack() else removeBackOverlay()
        }
    }

    private fun captureWebView(onDone: (Bitmap?) -> Unit) {
        val webView = binding.krOnlineWebview
        val w = webView.width
        val h = webView.height
        if (w <= 0 || h <= 0) {
            onDone(null)
            return
        }
        val bitmap = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (_: Throwable) {
            onDone(null)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val loc = IntArray(2)
                webView.getLocationInWindow(loc)
                val rect = Rect(loc[0], loc[1], loc[0] + w, loc[1] + h)
                PixelCopy.request(window, rect, bitmap, { result ->
                    if (result == PixelCopy.SUCCESS) onDone(bitmap) else onDone(drawWebView(webView, bitmap))
                }, backHandler)
                return
            } catch (_: Exception) {
            }
        }
        onDone(drawWebView(webView, bitmap))
    }

    private fun drawWebView(webView: WebView, bitmap: Bitmap): Bitmap? {
        return try {
            webView.draw(Canvas(bitmap))
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    private fun showBackOverlay(bitmap: Bitmap): Boolean {
        val webView = binding.krOnlineWebview
        val parent = webView.parent as? ViewGroup ?: return false
        val webParams = webView.layoutParams as? RelativeLayout.LayoutParams ?: return false
        val overlay = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            isClickable = true
            setImageBitmap(bitmap)
            layoutParams = RelativeLayout.LayoutParams(webParams)
        }
        parent.addView(overlay)
        backOverlay = overlay
        backOverlayBitmap = bitmap
        return true
    }

    private fun releaseBackOverlayWhenPainted(view: WebView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            view.postVisualStateCallback(++backRequestId, object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    view.postOnAnimation { removeBackOverlay() }
                }
            })
        } else {
            backHandler.postDelayed({ removeBackOverlay() }, 100L)
        }
    }

    private fun removeBackOverlay() {
        backHandler.removeCallbacks(backNoLoadRunnable)
        backHandler.removeCallbacks(backSafetyRunnable)
        backOverlay?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.setImageDrawable(null)
        }
        backOverlay = null
        backOverlayBitmap?.takeIf { !it.isRecycled }?.recycle()
        backOverlayBitmap = null
        backLoadStarted = false
        backBusy = false
        if (toolbarShowPending) {
            toolbarShowPending = false
            if (!isFinishing && !isDestroyed) setToolbarHidden(false)
        }
    }

    private fun chooseFilePath(fileSelectedInterface: ParamsFileChooserRender.FileSelectedInterface): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(intent, ACTION_FILE_PATH_CHOOSER)
            this.fileSelectedInterface = fileSelectedInterface
            true
        } catch (ex: Exception) {
            false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == ACTION_FILE_PATH_CHOOSER) {
            val result = if (data == null || resultCode != RESULT_OK) null else data.data
            if (fileSelectedInterface != null) {
                val absPath = result?.let { getPath(it) }
                fileSelectedInterface?.onFileSelected(absPath)
            }
            this.fileSelectedInterface = null
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun getPath(uri: Uri): String? {
        return try {
            FilePathResolver().getPath(this, uri)
        } catch (_: Exception) {
            null
        }
    }

    override fun onPause() {
        binding.krOnlineWebview.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.krOnlineWebview.onResume()
        binding.krOnlineWebview.resumeTimers()
    }

    override fun onDestroy() {
        toolbarAnimator?.cancel()
        hideCustomView()
        backHandler.removeCallbacksAndMessages(null)
        removeBackOverlay()
        loadProgressBar.visibility = View.GONE
        binding.krOnlineWebview.apply {
            stopLoading()
            (parent as? ViewGroup)?.removeView(this)
            removeAllViews()
            destroy()
        }
        super.onDestroy()
    }
}
