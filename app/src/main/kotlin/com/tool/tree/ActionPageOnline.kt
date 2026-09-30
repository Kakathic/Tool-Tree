package com.tool.tree

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebSettingsCompat.FORCE_DARK_OFF
import androidx.webkit.WebSettingsCompat.FORCE_DARK_ON
import androidx.webkit.WebViewFeature
import com.omarea.common.shared.FilePathResolver
import com.omarea.common.ui.DialogHelper
import com.omarea.krscript.WebViewInjector
import com.omarea.krscript.ui.ParamsFileChooserRender
import com.tool.tree.databinding.ActivityActionPageOnlineBinding

class ActionPageOnline : AppCompatActivity() {
    private lateinit var binding: ActivityActionPageOnlineBinding
    private val loadProgressBar by lazy { findViewById<ProgressBar>(R.id.page_load_progress) }
    private var fileSelectedInterface: ParamsFileChooserRender.FileSelectedInterface? = null
    private val ACTION_FILE_PATH_CHOOSER = 65400
    private val MENU_FIND = 1002
    private val MENU_FIND_PREV = 1003
    private val MENU_FIND_NEXT = 1004
    private val MENU_LINK = 1001

    private var findItem: MenuItem? = null
    private var findPrevItem: MenuItem? = null
    private var findNextItem: MenuItem? = null
    private var findQuery = ""

    // Stack tab: tabs[0] là WebView gốc, tab con mở từ onCreateWindow xếp sau, chỉ tab cuối được hiện
    private val MAX_TABS = 6
    private val tabs = ArrayList<WebView>()
    private val currentWebView: WebView get() = tabs.last()
    private lateinit var tabContainer: FrameLayout
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeModeState.switchTheme(this)

        binding = ActivityActionPageOnlineBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupTabContainer()

        val toolbar: Toolbar = binding.webappbar.toolbar
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        toolbar.setNavigationOnClickListener {
            finish()
        }
        setupToolbarMenu(toolbar)
        setTitle(R.string.app_name)

        // Back: đóng bàn phím/tìm kiếm -> goBack trong tab -> đóng tab về tab trước -> finish
        onBackPressedDispatcher.addCallback(this) {
            if (findItem?.isActionViewExpanded == true) {
                handleSearchBack()
            } else if (currentWebView.canGoBack()) {
                currentWebView.goBack()
            } else if (tabs.size > 1) {
                closeTab(currentWebView)
            } else {
                finish()
            }
        }

        loadIntentData()
    }

    override fun onTitleChanged(title: CharSequence?, color: Int) {
        super.onTitleChanged(title, color)
        if (::binding.isInitialized) binding.webappbar.toolbar.title = title
    }

    // Bọc WebView gốc vào FrameLayout cùng vị trí/id để thêm tab bằng code mà không đổi XML
    private fun setupTabContainer() {
        val root = binding.krOnlineWebview
        val parent = root.parent as ViewGroup
        val index = parent.indexOfChild(root)
        val originalParams = root.layoutParams

        parent.removeViewAt(index)
        tabContainer = FrameLayout(this)
        tabContainer.id = root.id
        root.id = View.NO_ID
        root.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        tabContainer.addView(root)
        parent.addView(tabContainer, index, originalParams)

        tabs.add(root)
    }

    private fun setupToolbarMenu(toolbar: Toolbar) {
        val menu = toolbar.menu

        val searchView = SearchView(toolbar.context).apply {
            queryHint = getString(R.string.online_find_in_page)
            maxWidth = Int.MAX_VALUE
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    currentWebView.findNext(true)
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    findInPage(newText.orEmpty())
                    return true
                }
            })
        }

        findItem = menu.add(0, MENU_FIND, 0, R.string.online_find_in_page).apply {
            setIcon(R.drawable.ic_search_web)
            actionView = searchView
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW)
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

        menu.add(0, MENU_LINK, 3, R.string.open_in_browser).apply {
            icon = createLinkIcon()
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_FIND_PREV -> {
                    currentWebView.findNext(false)
                    true
                }
                MENU_FIND_NEXT -> {
                    currentWebView.findNext(true)
                    true
                }
                MENU_LINK -> {
                    openInDefaultBrowser()
                    true
                }
                else -> false
            }
        }
    }

    private fun findInPage(query: String) {
        findQuery = query
        if (query.isEmpty()) {
            currentWebView.clearMatches()
            binding.webappbar.toolbar.subtitle = null
        } else {
            currentWebView.findAllAsync(query)
        }
    }

    private fun createLinkIcon(): Drawable? {
        val toolbarContext = binding.webappbar.toolbar.context
        val typedArray = toolbarContext.obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary))
        val tint = typedArray.getColor(0, Color.GRAY)
        typedArray.recycle()
        val base = ContextCompat.getDrawable(this, R.drawable.kr_link)?.mutate() ?: return null
        val wrapped = DrawableCompat.wrap(base)
        DrawableCompat.setTint(wrapped, tint)
        return wrapped
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
        currentWebView.clearMatches()
        binding.webappbar.toolbar.subtitle = null
    }

    // Phải gọi TRƯỚC khi đổi tab để highlight tìm kiếm được xoá đúng trên tab đang tìm
    private fun closeSearchIfOpen() {
        findItem?.takeIf { it.isActionViewExpanded }?.collapseActionView()
    }

    private fun openInDefaultBrowser() {
        val currentUrl = currentWebView.url
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
        val root = binding.krOnlineWebview
        root.visibility = View.VISIBLE
        setupWebView(root, url?.startsWith("file:///android_asset") == true)
        url?.let { root.loadUrl(it) }
    }

    // Cấu hình dùng chung cho WebView gốc và mọi tab con
    private fun setupWebView(wv: WebView, isAssetPage: Boolean) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            val isDark = ThemeModeState.isDarkMode()
            WebSettingsCompat.setForceDark(wv.settings, if (isDark) FORCE_DARK_ON else FORCE_DARK_OFF)
        }

        val webViewInjector = WebViewInjector(wv,
            object : ParamsFileChooserRender.FileChooserInterface {
                override fun openFileChooser(fileSelectedInterface: ParamsFileChooserRender.FileSelectedInterface): Boolean {
                    return chooseFilePath(fileSelectedInterface)
                }
            })

        wv.setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
            if (wv === currentWebView) {
                binding.webappbar.toolbar.subtitle = when {
                    findQuery.isEmpty() -> null
                    numberOfMatches > 0 -> "${activeMatchOrdinal + 1}/$numberOfMatches"
                    else -> "0/0"
                }
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (view !== currentWebView) return
                if (newProgress < 100) {
                    loadProgressBar.isIndeterminate = false
                    loadProgressBar.progress = newProgress
                    loadProgressBar.visibility = View.VISIBLE
                } else {
                    loadProgressBar.visibility = View.GONE
                }
            }

            // Trả WebView mới cho trang gọi (window.open / target=_blank) để giữ window.opener
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                if (resultMsg == null || tabs.size >= MAX_TABS) return false
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                transport.webView = openTab()
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView?) {
                window?.let { closeTab(it) }
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

        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (view !== currentWebView) return
                loadProgressBar.visibility = View.GONE
                view?.title?.let { setTitle(it) }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (view !== currentWebView) return
                loadProgressBar.isIndeterminate = true
                loadProgressBar.visibility = View.VISIBLE
                closeSearchIfOpen()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return try {
                    val requestUrl = request?.url
                    if (requestUrl != null && requestUrl.scheme?.startsWith("http") != true) {
                        val intent = Intent(Intent.ACTION_VIEW, requestUrl)
                        startActivity(intent)
                        true
                    } else if (request != null && requestUrl != null && view === currentWebView &&
                        request.isForMainFrame && request.hasGesture() && !request.isRedirect &&
                        request.method.equals("GET", true) && tabs.size < MAX_TABS
                    ) {
                        // Link người dùng bấm mở thành tab mới: trang cũ đứng yên nên Back không phải nạp lại
                        openTab().loadUrl(requestUrl.toString())
                        true
                    } else {
                        super.shouldOverrideUrlLoading(view, request)
                    }
                } catch (_: Exception) {
                    super.shouldOverrideUrlLoading(view, request)
                }
            }
        }

        webViewInjector.inject(this, isAssetPage)
        wv.settings.setSupportMultipleWindows(true)
    }

    // Tạo tab con: ẩn (không destroy) tab đang hiện để khi quay lại không tải lại, không lộ trang thô
    private fun openTab(): WebView {
        closeSearchIfOpen()

        val wv = WebView(this)
        wv.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        tabs.first().background?.constantState?.newDrawable()?.let { wv.background = it }
        setupWebView(wv, false)

        val previous = currentWebView
        previous.visibility = View.GONE
        previous.onPause()

        tabContainer.addView(wv)
        tabs.add(wv)
        syncUiToCurrentTab()
        return wv
    }

    // Không đóng tab gốc; nếu đóng tab đang hiện thì hiện lại tab ngay trước đó
    private fun closeTab(wv: WebView) {
        if (tabs.size <= 1 || wv === tabs.first() || !tabs.contains(wv)) return

        val wasCurrent = wv === currentWebView
        if (wasCurrent) closeSearchIfOpen()

        tabs.remove(wv)
        tabContainer.removeView(wv)
        wv.stopLoading()
        // destroy() hoãn qua handler vì onCloseWindow có thể đang chạy ngay trong callback của chính WebView này
        mainHandler.post {
            wv.removeAllViews()
            wv.destroy()
        }

        if (wasCurrent) {
            val top = currentWebView
            top.visibility = View.VISIBLE
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) top.onResume()
            syncUiToCurrentTab()
        }
    }

    // Đồng bộ tiêu đề, thanh tiến trình và số kết quả tìm kiếm theo tab đang hiện
    private fun syncUiToCurrentTab() {
        val wv = currentWebView
        wv.title?.takeIf { it.isNotEmpty() }?.let { setTitle(it) }
        val progress = wv.progress
        if (progress < 100) {
            loadProgressBar.isIndeterminate = progress <= 0
            if (progress > 0) loadProgressBar.progress = progress
            loadProgressBar.visibility = View.VISIBLE
        } else {
            loadProgressBar.visibility = View.GONE
        }
        binding.webappbar.toolbar.subtitle = null
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
        currentWebView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        currentWebView.onResume()
        currentWebView.resumeTimers()
    }

    override fun onDestroy() {
        loadProgressBar.visibility = View.GONE
        tabs.forEach { wv ->
            wv.stopLoading()
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.removeAllViews()
            wv.destroy()
        }
        tabs.clear()
        super.onDestroy()
    }
}
