package com.tool.tree

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.*
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebSettingsCompat.FORCE_DARK_OFF
import androidx.webkit.WebSettingsCompat.FORCE_DARK_ON
import androidx.webkit.WebViewFeature
import com.omarea.common.shared.FilePathResolver
import com.omarea.common.ui.DialogHelper
import com.omarea.common.ui.ThemeMode
import com.omarea.krscript.WebViewInjector
import com.omarea.krscript.ui.ParamsFileChooserRender
import com.tool.tree.databinding.ActivityActionPageOnlineBinding

class ActionPageOnline : AppCompatActivity() {
    private lateinit var themeMode: ThemeMode
    private lateinit var binding: ActivityActionPageOnlineBinding
    private val loadProgressBar by lazy { findViewById<ProgressBar>(R.id.page_load_progress) }
    private var fileSelectedInterface: ParamsFileChooserRender.FileSelectedInterface? = null
    private var skipInterceptForBackNav = false
    private val ACTION_FILE_PATH_CHOOSER = 65400
    private val MENU_OPEN_BROWSER = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        themeMode = ThemeModeState.switchTheme(this)
        
        binding = ActivityActionPageOnlineBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar: Toolbar = binding.webappbar.toolbar
        setSupportActionBar(toolbar)
        setTitle(R.string.app_name)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeButtonEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_arrow_back)
        }

        toolbar.setNavigationOnClickListener {
            finish()
        }

        binding.krOnlineWebview.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        onBackPressedDispatcher.addCallback(this) {
            if (binding.krOnlineWebview.canGoBack()) {
                skipInterceptForBackNav = true
                binding.krOnlineWebview.goBack()
            } else {
                finish()
            }
        }

        loadIntentData()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, MENU_OPEN_BROWSER, 0, R.string.open_in_browser)?.apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            MENU_OPEN_BROWSER -> {
                openInDefaultBrowser()
                true
            }
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openInDefaultBrowser() {
        val currentUrl = binding.krOnlineWebview.url
        if (!currentUrl.isNullOrEmpty()) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "No suitable browser found.", Toast.LENGTH_SHORT).show()
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
        
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        
        settings.blockNetworkImage = false
        settings.loadsImagesAutomatically = true

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
                loadProgressBar.visibility = View.GONE
                view?.title?.let { setTitle(it) }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                loadProgressBar.isIndeterminate = true
                loadProgressBar.visibility = View.VISIBLE
                // Lưới an toàn: nếu WebView phục hồi hẳn từ cache nội bộ (không gọi tới
                // shouldInterceptRequest lần nào) thì cờ vẫn phải tắt ở đây, tránh lỡ bỏ qua
                // interception của 1 điều hướng khác sau này.
                skipInterceptForBackNav = false
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

            // Chỉ can thiệp request HTML gốc (main frame) qua http/https: tự fetch rồi ép luôn
            // trả về Content-Type "text/html" nếu server báo thiếu/mập mờ (rỗng hoặc
            // "text/plain") - đây chính là nguyên nhân WebView đôi lúc tự hiện nguyên trang dưới
            // dạng văn bản thô thay vì render, hoặc tự bật hộp thoại tải về (setDownloadListener)
            // thay vì hiển thị. KHÔNG đụng tới file:// (mở bằng file vẫn load như cũ), KHÔNG đụng
            // tài nguyên phụ (ảnh/css/js/xhr) hay request không phải GET (form submit...) để tránh
            // ảnh hưởng ngoài ý muốn - các trường hợp đó fallback về hành vi mặc định của WebView.
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val requestUrl = request?.url ?: return null
                if (request.isForMainFrame != true) return null
                if (requestUrl.scheme?.startsWith("http") != true) return null
                if (request.method != "GET") return null

                // Đang back (goBack()) - để WebView tự phục hồi trang trước từ cache/lịch sử
                // của chính nó thay vì ép fetch lại thủ công qua mạng, tránh chậm/nháy hình
                // không cần thiết. Chỉ bỏ qua đúng 1 lần cho điều hướng back này.
                if (skipInterceptForBackNav) {
                    skipInterceptForBackNav = false
                    return null
                }

                return try {
                    val connection = (java.net.URL(requestUrl.toString()).openConnection() as java.net.HttpURLConnection).apply {
                        instanceFollowRedirects = true
                        connectTimeout = 15000
                        readTimeout = 15000
                        request.requestHeaders.forEach { (key, value) -> setRequestProperty(key, value) }
                        CookieManager.getInstance().getCookie(requestUrl.toString())?.let {
                            setRequestProperty("Cookie", it)
                        }
                    }
                    connection.connect()

                    // Giữ lại cookie server set qua request thủ công này để không mất session so
                    // với việc để WebView tự tải như trước.
                    connection.headerFields["Set-Cookie"]?.forEach {
                        CookieManager.getInstance().setCookie(requestUrl.toString(), it)
                    }

                    val declaredType = connection.contentType ?: ""
                    // Chỉ ép về "text/html" khi kiểu khai báo mập mờ (rỗng/text/plain) - kiểu tải
                    // về thật sự (pdf/apk/zip/octet-stream...) vẫn giữ nguyên, không can thiệp.
                    val ambiguous = declaredType.isEmpty() ||
                        declaredType.substringBefore(";").trim().equals("text/plain", ignoreCase = true)
                    if (!ambiguous) {
                        connection.disconnect()
                        return null
                    }
                    val charset = Regex("charset=([^;]+)", RegexOption.IGNORE_CASE)
                        .find(declaredType)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() } ?: "utf-8"

                    WebResourceResponse("text/html", charset, connection.inputStream)
                } catch (_: Exception) {
                    null
                }
            }
        }

        webViewInjector.inject(this, url?.startsWith("file:///android_asset") == true)

        url?.let { binding.krOnlineWebview.loadUrl(it) }
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

    override fun onDestroy() {
        loadProgressBar.visibility = View.GONE
        binding.krOnlineWebview.apply {
            stopLoading()
            removeAllViews()
            destroy()
        }
        super.onDestroy()
    }

}