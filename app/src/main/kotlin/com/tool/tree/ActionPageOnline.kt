package com.tool.tree

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.activity.addCallback
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

    private var findItem: MenuItem? = null
    private var findPrevItem: MenuItem? = null
    private var findNextItem: MenuItem? = null
    private var findQuery = ""

    // Back trong WebView: phủ ảnh chụp trang hiện tại cho tới khi trang cũ tải + vẽ xong
    private val backHandler = Handler(Looper.getMainLooper())
    private var backOverlay: ImageView? = null
    private var backOverlayBitmap: Bitmap? = null
    private var backBusy = false
    private var backLoadStarted = false
    private var backRequestId = 0L
    private val backNoLoadRunnable = Runnable { removeBackOverlay() }
    private val backSafetyRunnable = Runnable { removeBackOverlay() }

    private companion object {
        const val BACK_NO_LOAD_TIMEOUT = 600L
        const val BACK_SAFETY_TIMEOUT = 10000L
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
            if (findItem?.isActionViewExpanded == true) {
                handleSearchBack()
            } else if (binding.krOnlineWebview.canGoBack()) {
                goBackKeepCurrentPage()
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

    // onPageFinished chưa chắc DOM đã sẵn sàng vẽ -> đợi visual state rồi mới gỡ ảnh phủ
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
