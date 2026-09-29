package com.tool.tree

import android.content.ActivityNotFoundException
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
    private val ACTION_FILE_PATH_CHOOSER = 65400
    private val MENU_FIND = 1002
    private val MENU_FIND_PREV = 1003
    private val MENU_FIND_NEXT = 1004
    private val MENU_LINK = 1001

    // Fix 2: callback cho trình chọn file của trang web (<input type="file">)
    private var uploadFileCallback: ValueCallback<Array<Uri>>? = null
    private val ACTION_WEB_FILE_CHOOSER = 65401

    // Fix 1: cờ đánh dấu trang đã tải xong. Sau khi xong, mọi callback progress "rác"
    // phát sinh từ iframe/XHR/SPA (GitHub Turbo...) sẽ bị bỏ qua để thanh tiến trình
    // không bị hiện lại rồi kẹt mãi trên màn hình.
    private var pageLoadFinished = false

    private var findItem: MenuItem? = null
    private var findPrevItem: MenuItem? = null
    private var findNextItem: MenuItem? = null
    private var findQuery = ""

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

        // Fix 3 (phần 1): bỏ marker "; wv" trong User-Agent để các trang web (GitHub...)
        // phục vụ bản dành cho trình duyệt Chrome thật thay vì bản degraded dành cho WebView
        val systemUa = WebSettings.getDefaultUserAgent(this)
        if (systemUa.contains("; wv)")) {
            settings.userAgentString = systemUa.replace("; wv)", ")")
        }

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
                // Fix 1: bỏ qua progress "rác" phát sinh sau khi trang đã tải xong
                // (do iframe/XHR/SPA như GitHub Turbo kích hoạt) — nguyên nhân khiến
                // thanh tiến trình hiện lại rồi kẹt mãi không ẩn
                if (pageLoadFinished) return
                if (newProgress < 100) {
                    loadProgressBar.isIndeterminate = false
                    loadProgressBar.progress = newProgress
                    loadProgressBar.visibility = View.VISIBLE
                } else {
                    pageLoadFinished = true
                    loadProgressBar.visibility = View.GONE
                }
            }

            // Fix 2 (phần 1): hỗ trợ upload file từ trang web qua thẻ <input type="file">
            // (nút "Add files" trên GitHub, upload ảnh, đính kèm...)
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: WebChromeClient.FileChooserParams
            ): Boolean {
                // Nhả callback cũ nếu còn treo để tránh lỗi "Failed to grant file permission" khi chọn lại
                uploadFileCallback?.onReceiveValue(null)
                uploadFileCallback = filePathCallback
                return try {
                    val intent = fileChooserParams.createIntent()
                    startActivityForResult(intent, ACTION_WEB_FILE_CHOOSER)
                    true
                } catch (e: ActivityNotFoundException) {
                    // Không có trình quản lý file nào xử lý được intent
                    uploadFileCallback = null
                    false
                } catch (e: Exception) {
                    uploadFileCallback = null
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
                pageLoadFinished = true
                loadProgressBar.visibility = View.GONE
                view?.title?.let { setTitle(it) }
                // Fix 3 (phần 2): vá vòng xoay vô hạn ở mục Assets trên trang phát hành GitHub
                maybeInjectGithubFragmentFix(view)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                pageLoadFinished = false
                loadProgressBar.isIndeterminate = true
                loadProgressBar.progress = 0
                loadProgressBar.visibility = View.VISIBLE
                findItem?.takeIf { it.isActionViewExpanded }?.collapseActionView()
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                // Trang SPA (GitHub Turbo...) đổi URL bằng pushState không kích hoạt
                // onPageStarted/onPageFinished — cập nhật tiêu đề cho kịp, nhưng chỉ
                // sau khi trang đầu tiên tải xong để tránh hiện URL như tiêu đề
                if (pageLoadFinished) {
                    view?.title?.takeIf { it.isNotBlank() }?.let { setTitle(it) }
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                // Lỗi frame chính cũng phải kết thúc trạng thái tải, tránh treo thanh tiến trình
                if (request?.isForMainFrame == true) {
                    pageLoadFinished = true
                    loadProgressBar.visibility = View.GONE
                }
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request?.isForMainFrame == true) {
                    pageLoadFinished = true
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

    // Fix 3 (phần 2): GitHub tải danh sách Assets (và một số khối khác) qua custom element
    // <include-fragment> — JS hiện đại của GitHub có thể không chạy/nâng cấp được element
    // này trên một số WebView, khiến vòng xoay quay mãi mà không hiện link tải.
    // Server vẫn trả fragment bình thường nên ta tự fetch nội dung đó và thay spinner
    // bằng HTML thật (link tải về). Chỉ chạy trên github.com.
    private fun maybeInjectGithubFragmentFix(view: WebView?) {
        val host = try {
            Uri.parse(view?.url ?: return).host ?: return
        } catch (_: Exception) {
            return
        }
        if (host != "github.com" && !host.endsWith(".github.com")) return
        view?.evaluateJavascript(GITHUB_INCLUDE_FRAGMENT_FIX, null)
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
            ACTION_FILE_PATH_CHOOSER -> {
                val result = if (data == null || resultCode != RESULT_OK) null else data.data
                if (fileSelectedInterface != null) {
                    val absPath = result?.let { getPath(it) }
                    fileSelectedInterface?.onFileSelected(absPath)
                }
                this.fileSelectedInterface = null
            }
            // Fix 2 (phần 2): nhận kết quả chọn file và trả về cho trang web
            ACTION_WEB_FILE_CHOOSER -> {
                val results = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
                uploadFileCallback?.onReceiveValue(results)
                uploadFileCallback = null
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
        loadProgressBar.visibility = View.GONE
        // Nhả callback upload để tránh rò rỉ bộ nhớ khi activity bị hủy giữa chừng
        uploadFileCallback?.onReceiveValue(null)
        uploadFileCallback = null
        binding.krOnlineWebview.apply {
            stopLoading()
            (parent as? ViewGroup)?.removeView(this)
            removeAllViews()
            destroy()
        }
        super.onDestroy()
    }

    private companion object {
        // Fix 3: script thuần ES5 (tương thích cả WebView cũ) quét các <include-fragment>
        // còn kẹt spinner (svg.anim-rotate) rồi tự fetch nội dung với header
        // "Accept: text/fragment+html" như chính element của GitHub vẫn làm.
        // setInterval để vẫn hoạt động khi GitHub điều hướng SPA (Turbo) không reload trang.
        private val GITHUB_INCLUDE_FRAGMENT_FIX = """
            (function () {
              if (window.__krIncludeFragmentFix) return;
              window.__krIncludeFragmentFix = true;
              var SPIN = 'svg.anim-rotate';
              var ORIGIN = location.origin || (location.protocol + '//' + location.host);
              var MAX_TRIES = 3;

              function originOf(src) {
                if (!src) return null;
                if (src.charAt(0) === '/') return ORIGIN;
                var i = src.indexOf('://');
                if (i > 0) {
                  var rest = src.substring(i + 3);
                  var slash = rest.indexOf('/');
                  var host = slash === -1 ? rest : rest.substring(0, slash);
                  return src.substring(0, i) + '://' + host;
                }
                return ORIGIN;
              }

              function fix(el) {
                if (!el || !el.getAttribute('src')) return;
                if (!el.querySelector(SPIN)) return;
                var tries = parseInt(el.getAttribute('data-kr-fix') || '0', 10);
                if (tries >= MAX_TRIES) return;
                var src = el.getAttribute('src');
                if (originOf(src) !== ORIGIN) return;
                el.setAttribute('data-kr-fix', String(tries + 1));
                el.setAttribute('data-kr-fixing', '1');
                var done = function () { el.removeAttribute('data-kr-fixing'); };
                var apply = function (text) {
                  if (el.querySelector(SPIN)) {
                    try { el.innerHTML = text; } catch (e) {}
                  }
                  done();
                };
                try {
                  var xhr = new XMLHttpRequest();
                  xhr.open('GET', src, true);
                  try { xhr.setRequestHeader('Accept', 'text/fragment+html'); } catch (e) {}
                  xhr.onload = function () {
                    if (xhr.status >= 200 && xhr.status < 400 && xhr.responseText) apply(xhr.responseText);
                    else done();
                  };
                  xhr.onerror = done;
                  xhr.ontimeout = done;
                  xhr.send();
                } catch (e) { done(); }
              }

              function sweep() {
                try {
                  var list = document.querySelectorAll('include-fragment:not([data-kr-fixing])');
                  for (var i = 0; i < list.length; i++) fix(list[i]);
                } catch (e) {}
              }

              setInterval(sweep, 3000);
              setTimeout(sweep, 2500);
            })();
        """.trimIndent()
    }
}
