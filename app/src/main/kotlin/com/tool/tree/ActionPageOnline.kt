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

    private val loadProgressBar by lazy {
        findViewById<ProgressBar>(R.id.page_load_progress)
    }

    private var fileSelectedInterface:
        ParamsFileChooserRender.FileSelectedInterface? = null

    private val ACTION_FILE_PATH_CHOOSER = 65400

    private val MENU_FIND = 1002
    private val MENU_FIND_PREV = 1003
    private val MENU_FIND_NEXT = 1004
    private val MENU_LINK = 1001

    private var findItem: MenuItem? = null
    private var findPrevItem: MenuItem? = null
    private var findNextItem: MenuItem? = null

    private var findQuery = ""

    /*
     * true khi WebView đang quay lại trang trước.
     *
     * Trong quá trình WebView restore history, HTML có thể xuất hiện
     * trước CSS/JS. Tạm ẩn bằng alpha để người dùng không nhìn thấy
     * trạng thái "trang thô".
     */
    private var isGoingBack = false

    /*
     * Thời gian chờ ngắn sau onPageFinished().
     *
     * onPageFinished() không đảm bảo rằng mọi thao tác DOM/JS/CSS
     * đã hoàn toàn ổn định trên màn hình.
     */
    private val backRevealDelay = 80L

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

                /*
                 * Đánh dấu trước khi gọi goBack().
                 *
                 * onPageStarted() có thể được gọi gần như ngay lập tức
                 * sau thao tác này.
                 */
                isGoingBack = true

                /*
                 * Không dùng INVISIBLE/GONE.
                 *
                 * Alpha = 0 giúp giữ nguyên kích thước/layout WebView
                 * và tránh hiện tượng layout nhảy.
                 */
                binding.krOnlineWebview.animate()
                    .alpha(0f)
                    .setDuration(0L)
                    .start()

                binding.krOnlineWebview.goBack()

            } else {
                finish()
            }
        }

        /*
         * WebView.findAllAsync() trả kết quả thông qua FindListener.
         *
         * activeMatchOrdinal:
         *   vị trí kết quả hiện tại, bắt đầu từ 0
         *
         * numberOfMatches:
         *   tổng số kết quả tìm được
         *
         * Hiển thị:
         *
         * 1/10
         * 2/10
         * 3/10
         */
        binding.krOnlineWebview.setFindListener {
                activeMatchOrdinal,
                numberOfMatches,
                _ ->

            binding.webappbar.toolbar.subtitle = when {
                findQuery.isEmpty() -> {
                    null
                }

                numberOfMatches > 0 -> {
                    "${activeMatchOrdinal + 1}/$numberOfMatches"
                }

                else -> {
                    "0/0"
                }
            }
        }

        loadIntentData()
    }

    override fun onTitleChanged(
        title: CharSequence?,
        color: Int
    ) {
        super.onTitleChanged(title, color)

        if (::binding.isInitialized) {
            binding.webappbar.toolbar.title = title
        }
    }

    private fun setupToolbarMenu(toolbar: Toolbar) {
        val menu = toolbar.menu

        val searchView = SearchView(toolbar.context).apply {
            queryHint = getString(R.string.online_find_in_page)
            maxWidth = Int.MAX_VALUE

            setOnQueryTextListener(
                object : SearchView.OnQueryTextListener {

                    override fun onQueryTextSubmit(
                        query: String?
                    ): Boolean {
                        binding.krOnlineWebview.findNext(true)
                        return true
                    }

                    override fun onQueryTextChange(
                        newText: String?
                    ): Boolean {
                        findInPage(newText.orEmpty())
                        return true
                    }
                }
            )
        }

        findItem = menu.add(
            0,
            MENU_FIND,
            0,
            R.string.online_find_in_page
        ).apply {

            setIcon(R.drawable.ic_search_web)

            actionView = searchView

            setShowAsAction(
                MenuItem.SHOW_AS_ACTION_ALWAYS or
                    MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW
            )

            setOnActionExpandListener(
                object : MenuItem.OnActionExpandListener {

                    override fun onMenuItemActionExpand(
                        item: MenuItem
                    ): Boolean {

                        findPrevItem?.isVisible = true
                        findNextItem?.isVisible = true

                        return true
                    }

                    override fun onMenuItemActionCollapse(
                        item: MenuItem
                    ): Boolean {

                        findPrevItem?.isVisible = false
                        findNextItem?.isVisible = false

                        clearFind()

                        return true
                    }
                }
            )
        }

        findPrevItem = menu.add(
            0,
            MENU_FIND_PREV,
            1,
            "▲"
        ).apply {

            setShowAsAction(
                MenuItem.SHOW_AS_ACTION_ALWAYS
            )

            isVisible = false
        }

        findNextItem = menu.add(
            0,
            MENU_FIND_NEXT,
            2,
            "▼"
        ).apply {

            setShowAsAction(
                MenuItem.SHOW_AS_ACTION_ALWAYS
            )

            isVisible = false
        }

        menu.add(
            0,
            MENU_LINK,
            3,
            R.string.open_in_browser
        ).apply {

            icon = createLinkIcon()

            setShowAsAction(
                MenuItem.SHOW_AS_ACTION_ALWAYS
            )
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

                else -> {
                    false
                }
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

        val typedArray = toolbarContext.obtainStyledAttributes(
            intArrayOf(android.R.attr.textColorPrimary)
        )

        val tint = typedArray.getColor(
            0,
            Color.GRAY
        )

        typedArray.recycle()

        val base = ContextCompat.getDrawable(
            this,
            R.drawable.kr_link
        )?.mutate() ?: return null

        val wrapped = DrawableCompat.wrap(base)

        DrawableCompat.setTint(
            wrapped,
            tint
        )

        return wrapped
    }

    /*
     * Back 1: ẩn bàn phím
     * Back 2: đóng tìm kiếm
     * Back 3: về trang web trước
     */
    private fun handleSearchBack() {

        val find = findItem ?: return

        if (!find.isActionViewExpanded) {
            return
        }

        if (isKeyboardShowing()) {
            hideFindKeyboard()
        } else {
            find.collapseActionView()
        }
    }

    private fun isKeyboardShowing(): Boolean {

        val hasFocus =
            (findItem?.actionView as? SearchView)?.hasFocus() == true

        val imeShown =
            ViewCompat
                .getRootWindowInsets(binding.root)
                ?.isVisible(
                    WindowInsetsCompat.Type.ime()
                )

        return hasFocus && (imeShown ?: true)
    }

    private fun hideFindKeyboard() {

        (findItem?.actionView as? SearchView)?.clearFocus()

        val imm =
            getSystemService(
                Context.INPUT_METHOD_SERVICE
            ) as? InputMethodManager

        imm?.hideSoftInputFromWindow(
            binding.root.windowToken,
            0
        )
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

                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(currentUrl)
                )

                startActivity(intent)

            } catch (e: Exception) {

                Toast.makeText(
                    this,
                    R.string.online_browser_not_found,
                    Toast.LENGTH_SHORT
                ).show()
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

                extras.containsKey("config") -> {
                    initWebview(
                        extras.getString("config")
                    )
                }

                extras.containsKey("url") -> {
                    initWebview(
                        extras.getString("url")
                    )
                }
            }
        }
    }

    private fun initWebview(url: String?) {

        binding.krOnlineWebview.visibility = View.VISIBLE

        /*
         * Trang mới luôn hiển thị bình thường.
         *
         * Chỉ khi Back mới alpha = 0.
         */
        binding.krOnlineWebview.alpha = 1f

        val settings = binding.krOnlineWebview.settings

        if (
            WebViewFeature.isFeatureSupported(
                WebViewFeature.FORCE_DARK
            )
        ) {

            val isDark =
                ThemeModeState.isDarkMode()

            WebSettingsCompat.setForceDark(
                settings,
                if (isDark) {
                    FORCE_DARK_ON
                } else {
                    FORCE_DARK_OFF
                }
            )
        }

        val webViewInjector = WebViewInjector(
            binding.krOnlineWebview,
            object : ParamsFileChooserRender.FileChooserInterface {

                override fun openFileChooser(
                    fileSelectedInterface:
                    ParamsFileChooserRender.FileSelectedInterface
                ): Boolean {

                    return chooseFilePath(
                        fileSelectedInterface
                    )
                }
            }
        )

        binding.krOnlineWebview.webChromeClient =
            object : WebChromeClient() {

                override fun onProgressChanged(
                    view: WebView?,
                    newProgress: Int
                ) {

                    super.onProgressChanged(
                        view,
                        newProgress
                    )

                    if (newProgress < 100) {

                        loadProgressBar.isIndeterminate = false

                        loadProgressBar.progress =
                            newProgress

                        loadProgressBar.visibility =
                            View.VISIBLE

                    } else {

                        loadProgressBar.visibility =
                            View.GONE
                    }
                }

                override fun onJsAlert(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: JsResult?
                ): Boolean {

                    DialogHelper.alert(
                        context = this@ActionPageOnline,
                        message = message.orEmpty(),
                        onConfirm = Runnable {
                            result?.confirm()
                        },
                        cancelable = false
                    )

                    return true
                }

                override fun onJsConfirm(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: JsResult?
                ): Boolean {

                    DialogHelper.confirm(
                        context = this@ActionPageOnline,
                        title = "",
                        message = message.orEmpty(),
                        contentView = null,
                        onConfirm = DialogHelper.DialogButton(
                            getString(R.string.btn_confirm),
                            Runnable {
                                result?.confirm()
                            }
                        ),
                        onCancel = DialogHelper.DialogButton(
                            getString(R.string.btn_cancel),
                            Runnable {
                                result?.cancel()
                            }
                        ),
                        cancelable = false
                    )

                    return true
                }
            }

        binding.krOnlineWebview.webViewClient =
            object : WebViewClient() {

                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {

                    super.onPageFinished(
                        view,
                        url
                    )

                    loadProgressBar.visibility =
                        View.GONE

                    view?.title?.let {
                        setTitle(it)
                    }

                    /*
                     * Chỉ reveal khi đây là navigation Back.
                     *
                     * Delay rất ngắn cho WebView hoàn thành thêm một
                     * vòng render sau khi document/history đã được restore.
                     */
                    if (isGoingBack) {

                        binding.krOnlineWebview.postDelayed(
                            {

                                if (
                                    !isFinishing &&
                                    !isDestroyed
                                ) {

                                    binding.krOnlineWebview
                                        .animate()
                                        .alpha(1f)
                                        .setDuration(100L)
                                        .start()
                                }

                                isGoingBack = false

                            },
                            backRevealDelay
                        )
                    }
                }

                override fun onPageStarted(
                    view: WebView?,
                    url: String?,
                    favicon: Bitmap?
                ) {

                    super.onPageStarted(
                        view,
                        url,
                        favicon
                    )

                    loadProgressBar.isIndeterminate =
                        true

                    loadProgressBar.visibility =
                        View.VISIBLE

                    /*
                     * Không alpha = 0 ở đây.
                     *
                     * Việc này đã được thực hiện ngay trước goBack().
                     * Nếu đặt ở đây có thể gây thêm một frame nhấp nháy.
                     */
                    findItem
                        ?.takeIf {
                            it.isActionViewExpanded
                        }
                        ?.collapseActionView()
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {

                    return try {

                        val requestUrl =
                            request?.url

                        if (
                            requestUrl != null &&
                            requestUrl.scheme
                                ?.startsWith("http") != true
                        ) {

                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                requestUrl
                            )

                            startActivity(intent)

                            true

                        } else {

                            super.shouldOverrideUrlLoading(
                                view,
                                request
                            )
                        }

                    } catch (_: Exception) {

                        super.shouldOverrideUrlLoading(
                            view,
                            request
                        )
                    }
                }
            }

        webViewInjector.inject(
            this,
            url?.startsWith(
                "file:///android_asset"
            ) == true
        )

        url?.let {
            binding.krOnlineWebview.loadUrl(it)
        }
    }

    private fun chooseFilePath(
        fileSelectedInterface:
        ParamsFileChooserRender.FileSelectedInterface
    ): Boolean {

        return try {

            val intent =
                Intent(Intent.ACTION_GET_CONTENT).apply {

                    type = "*/*"

                    addCategory(
                        Intent.CATEGORY_OPENABLE
                    )
                }

            startActivityForResult(
                intent,
                ACTION_FILE_PATH_CHOOSER
            )

            this.fileSelectedInterface =
                fileSelectedInterface

            true

        } catch (ex: Exception) {

            false
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        if (
            requestCode ==
            ACTION_FILE_PATH_CHOOSER
        ) {

            val result =
                if (
                    data == null ||
                    resultCode != RESULT_OK
                ) {
                    null
                } else {
                    data.data
                }

            if (fileSelectedInterface != null) {

                val absPath =
                    result?.let {
                        getPath(it)
                    }

                fileSelectedInterface?.onFileSelected(
                    absPath
                )
            }

            this.fileSelectedInterface = null
        }

        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )
    }

    private fun getPath(uri: Uri): String? {

        return try {

            FilePathResolver().getPath(
                this,
                uri
            )

        } catch (_: Exception) {

            null
        }
    }

    override fun onPause() {

        /*
         * Chỉ pause chính WebView này.
         *
         * Không gọi pauseTimers() vì phương thức đó có phạm vi
         * rộng và trước đây đã gây ảnh hưởng đến WebView khác.
         */
        binding.krOnlineWebview.onPause()

        super.onPause()
    }

    override fun onResume() {

        super.onResume()

        binding.krOnlineWebview.onResume()

        /*
         * Không gọi resumeTimers().
         *
         * resumeTimers() có phạm vi rộng tương ứng với WebView
         * timers và không cần thiết cho lifecycle thông thường
         * của Activity này.
         */
    }

    override fun onDestroy() {

        /*
         * Hủy callback reveal nếu Activity bị destroy giữa lúc Back.
         */
        binding.krOnlineWebview.animate().cancel()

        loadProgressBar.visibility =
            View.GONE

        binding.krOnlineWebview.apply {

            stopLoading()

            (parent as? ViewGroup)
                ?.removeView(this)

            removeAllViews()

            destroy()
        }

        super.onDestroy()
    }
}