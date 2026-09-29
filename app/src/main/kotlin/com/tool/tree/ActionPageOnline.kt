package com.tool.tree

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
    private var webFilePathCallback: ValueCallback<Array<Uri>>? = null
    private val ACTION_FILE_PATH_CHOOSER = 65400
    private val ACTION_WEB_FILE_CHOOSER = 65401
    private val MENU_FIND = 1002
    private val MENU_FIND_PREV = 1003
    private val MENU_FIND_NEXT = 1004
    private val MENU_LINK = 1001

    private var findItem: MenuItem? = null
    private var findPrevItem: MenuItem? = null
    private var findNextItem: MenuItem? = null
    private var findQuery = ""
    private var pageLoading = false
    private var destroyed = false

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
            if (findItem?.isActionViewExpanded == true) {
                handleSearchBack()
            } else if (binding.krOnlineWebview.canGoBack()) {
                binding.krOnlineWebview.goBack()
            } else {
                finish()
            }
        }

        binding.krOnlineWebview.setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
            binding.webappbar.toolbar.subtitle = when {
                findQuery.isEmpty() -> null
                numberOfMatches > 0 -> "${activeMatchOrdinal + 1}/$numberOfMatches"
                else -> "0/0"
            }
        }

        loadIntentData()
    }

    // Toolbar độc lập (không setSupportActionBar) để AppCompat không tự đóng ô tìm kiếm khi back
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
                    binding.krOnlineWebview.findNext(false)
                    true
                }
                MENU_FIND_NEXT -> {
                    binding.krOnlineWebview.findNext(true)
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
            binding.krOnlineWebview.clearMatches()
            binding.webappbar.toolbar.subtitle = null
        } else {
            binding.krOnlineWebview.findAllAsync(query)
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

    // Back 1: ẩn bàn phím -> Back 2: đóng tìm kiếm -> Back 3: về trang web trước
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
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)

                // WebView có thể gửi thêm progress < 100 sau onPageFinished().
                // Không được để callback này bật progress bar trở lại sau khi
                // trang chính đã hoàn tất.
                if (!pageLoading || destroyed) {
                    return
                }

                if (newProgress >= 100) {
                    pageLoading = false
                    loadProgressBar.visibility = View.GONE
                } else {
                    loadProgressBar.isIndeterminate = false
                    loadProgressBar.progress = newProgress.coerceIn(0, 100)
                    loadProgressBar.visibility = View.VISIBLE
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                // Hủy callback cũ trước khi mở picker mới để WebView không bị
                // treo nếu người dùng bấm input[type=file] nhiều lần.
                webFilePathCallback?.onReceiveValue(null)
                webFilePathCallback = filePathCallback

                return try {
                    val acceptTypes = fileChooserParams?.acceptTypes
                        ?.mapNotNull { it.takeIf(String::isNotBlank) }
                        ?.flatMap { it.split(',') }
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() }
                        ?.toTypedArray()
                        ?: emptyArray()

                    val mimeType = when {
                        acceptTypes.isEmpty() -> "*/*"
                        acceptTypes.size == 1 -> acceptTypes[0]
                        else -> "*/*"
                    }

                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        type = mimeType
                        addCategory(Intent.CATEGORY_OPENABLE)
                        putExtra(
                            Intent.EXTRA_ALLOW_MULTIPLE,
                            fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE
                        )

                        if (acceptTypes.size > 1) {
                            putExtra(Intent.EXTRA_MIME_TYPES, acceptTypes)
                        }
                    }

                    startActivityForResult(intent, ACTION_WEB_FILE_CHOOSER)
                    true
                } catch (_: Exception) {
                    webFilePathCallback?.onReceiveValue(null)
                    webFilePathCallback = null
                    false
                }
            }

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                DialogHelper.animDialog(
                    AlertDialog.Builder(this@ActionPageOnline)
                        .setMessage(message)
                        .setPositiveButton(R.string.btn_confirm) { _, _ -> }
                        .setOnDismissListener { result?.confirm() }
                        .create()
                )?.setCancelable(false)
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                DialogHelper.animDialog(
                    AlertDialog.Builder(this@ActionPageOnline)
                        .setMessage(message)
                        .setPositiveButton(R.string.btn_confirm) { _, _ -> result?.confirm() }
                        .setNeutralButton(R.string.btn_cancel) { _, _ -> result?.cancel() }
                        .create()
                )?.setCancelable(false)
                return true
            }
        }

        binding.krOnlineWebview.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                pageLoading = false
                loadProgressBar.visibility = View.GONE
                view?.title?.let { setTitle(it) }

                // GitHub Releases tải danh sách Assets bằng <include-fragment>.
                // Fragment này được lazy-load trong trình duyệt và WebView có thể
                // giữ placeholder spinner vô hạn. Tự tải các fragment sau khi
                // document chính hoàn tất để Assets xuất hiện giống trình duyệt.
                if (!url.isNullOrEmpty() && isGithubUrl(url)) {
                    loadGithubFragments(view)
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                pageLoading = true
                loadProgressBar.isIndeterminate = true
                loadProgressBar.visibility = View.VISIBLE
                findItem?.takeIf { it.isActionViewExpanded }?.collapseActionView()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame != false) {
                    pageLoading = false
                    loadProgressBar.visibility = View.GONE
                }
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

    private fun isGithubUrl(url: String): Boolean {
        return try {
            Uri.parse(url).host?.equals("github.com", ignoreCase = true) == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * GitHub dùng @github/include-fragment-element để tải HTML động, trong đó
     * Release Assets thường nằm ở /releases/expanded_assets/<tag>. Trình duyệt
     * đầy đủ xử lý custom element này, nhưng một WebView có thể để nguyên
     * spinner. Ta fetch fragment bằng chính cookie/session của WebView rồi
     * thay <include-fragment> bằng HTML nhận được.
     */
    private fun loadGithubFragments(webView: WebView?) {
        if (webView == null || destroyed) return

        val script = """
            (function() {
                function loadFragment(el) {
                    if (!el || el.dataset.toolTreeLoading === '1' || !el.getAttribute('src')) return;
                    var src = el.getAttribute('src');
                    if (!/^\/[^\s]*$/.test(src) && !/^https?:\/\//i.test(src)) return;

                    el.dataset.toolTreeLoading = '1';
                    var target = new URL(src, location.href).href;

                    fetch(target, {
                        method: 'GET',
                        credentials: 'include',
                        headers: { 'X-Requested-With': 'XMLHttpRequest' },
                        cache: 'no-store'
                    }).then(function(response) {
                        if (!response.ok) throw new Error('HTTP ' + response.status);
                        return response.text();
                    }).then(function(html) {
                        if (!el.isConnected) return;
                        var template = document.createElement('template');
                        template.innerHTML = html;
                        var fragment = template.content;
                        el.replaceWith(fragment.cloneNode(true));
                    }).catch(function() {
                        el.dataset.toolTreeLoading = '0';
                    });
                }

                function scan() {
                    document.querySelectorAll('include-fragment[src]').forEach(loadFragment);
                }

                scan();
                [500, 1200, 2500, 5000, 9000].forEach(function(delay) {
                    setTimeout(scan, delay);
                });

                if (window.MutationObserver) {
                    var observer = new MutationObserver(function() { scan(); });
                    observer.observe(document.documentElement, { childList: true, subtree: true });
                    setTimeout(function() { observer.disconnect(); }, 12000);
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(script, null)
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
        when (requestCode) {
            ACTION_WEB_FILE_CHOOSER -> {
                val callback = webFilePathCallback
                webFilePathCallback = null

                if (callback != null) {
                    if (resultCode != RESULT_OK || data == null) {
                        callback.onReceiveValue(null)
                    } else {
                        val uris = ArrayList<Uri>()
                        val clipData = data.clipData

                        if (clipData != null) {
                            for (i in 0 until clipData.itemCount) {
                                clipData.getItemAt(i).uri?.let(uris::add)
                            }
                        } else {
                            data.data?.let(uris::add)
                        }

                        callback.onReceiveValue(uris.toTypedArray().takeIf { it.isNotEmpty() })
                    }
                }
            }

            ACTION_FILE_PATH_CHOOSER -> {
                val result = if (data == null || resultCode != RESULT_OK) null else data.data
                if (fileSelectedInterface != null) {
                    val absPath = result?.let { getPath(it) }
                    fileSelectedInterface?.onFileSelected(absPath)
                }
                this.fileSelectedInterface = null
            }
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
    }

    override fun onDestroy() {
        destroyed = true
        pageLoading = false
        webFilePathCallback?.onReceiveValue(null)
        webFilePathCallback = null
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
