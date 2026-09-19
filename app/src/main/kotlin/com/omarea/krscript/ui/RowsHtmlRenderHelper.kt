package com.omarea.krscript.ui

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.omarea.krscript.config.PathAnalysis
import com.omarea.krscript.model.NodeInfoBase
import com.omarea.krscript.model.TextNode
import com.tool.tree.ThemeModeState
import java.nio.charset.StandardCharsets

// Hiển thị 1 trang HTML (từ file cục bộ "html-file"/"html-path" hoặc link "html-url"/
// "html-link") ngay trong item, dùng 1 WebView đặt trong "container" (view rỗng đặt sẵn trong
// layout, xem kr_rows_html trong các layout item). Cùng giới hạn với "photo": mỗi item chỉ có 1
// khung html - nếu nhiều row cùng khai báo thì ROW CUỐI CÙNG (duyệt theo thứ tự khai báo) thắng.
object RowsHtmlRenderHelper {
    private const val DEFAULT_HEIGHT_DP = 260

    fun bind(context: Context, container: FrameLayout?, rows: List<TextNode.TextRow>, config: NodeInfoBase) {
        if (container == null) {
            return
        }
        val row = rows.lastOrNull { it.htmlUrl.isNotEmpty() || it.htmlFile.isNotEmpty() }
        if (row == null) {
            container.visibility = View.GONE
            return
        }

        val heightDp = if (row.htmlHeight > 0) row.htmlHeight else DEFAULT_HEIGHT_DP
        val heightPx = (heightDp * context.resources.displayMetrics.density).toInt()
        val params = container.layoutParams
        if (params != null && params.height != heightPx) {
            params.height = heightPx
            container.layoutParams = params
        }

        val webView = container.getChildAt(0) as? WebView ?: createWebView(context).also {
            container.removeAllViews()
            container.addView(it, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        container.visibility = View.VISIBLE

        // Tránh nạp lại trang mỗi lần bind() (rows được vẽ lại khi bấm toggle, RecyclerView
        // rebind, ...) nếu nội dung cần hiển thị không đổi - giữ nguyên trạng thái cuộn/JS
        // đang chạy trong WebView thay vì load lại từ đầu.
        val cacheKey = "${row.htmlUrl}|${row.htmlFile}"
        if (webView.tag == cacheKey) {
            return
        }
        webView.tag = cacheKey

        RowsHtmlLoadingOverlay.show(container, webView)

        if (row.htmlUrl.isNotEmpty()) {
            webView.loadUrl(row.htmlUrl)
        } else {
            loadLocalHtml(context, webView, row.htmlFile, config.pageConfigDir, cacheKey)
        }
    }

    private fun createWebView(context: Context): WebView {
        val webView = WebView(context)
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true

        // Nền sáng/tối theo theme hiện tại của app (giống ActionPageOnline - trang duyệt web
        // riêng của app) - chỉ áp dụng được trên thiết bị hỗ trợ FORCE_DARK.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            val isDark = ThemeModeState.isDarkMode()
            WebSettingsCompat.setForceDark(
                settings,
                if (isDark) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF
            )
        }

        // Nền trong suốt để khung html hoà theo nền của item (sáng/tối theo app) thay vì luôn
        // trắng - chỉ có tác dụng với phần trang HTML KHÔNG tự đặt màu nền riêng qua CSS.
        // LAYER_TYPE_SOFTWARE tránh WebView vẽ đè nền đen lên vùng trong suốt khi tăng tốc phần cứng.
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        return webView
    }

    // Đọc file html ở luồng nền (tránh chặn main thread khi dựng item), rồi nạp vào WebView trên
    // main thread. Bỏ qua kết quả nếu WebView đã được bind sang nội dung khác (tag đổi).
    private fun loadLocalHtml(context: Context, webView: WebView, htmlFile: String, pageDir: String, cacheKey: String) {
        Thread {
            val pathAnalysis = PathAnalysis(context, pageDir)
            val html = try {
                pathAnalysis.parsePath(htmlFile)?.use { it.readBytes().toString(StandardCharsets.UTF_8) }
            } catch (ex: Exception) {
                null
            }
            var baseUrl: String? = null
            if (!html.isNullOrEmpty()) {
                val absPath = pathAnalysis.getCurrentAbsPath()
                baseUrl = when {
                    absPath.startsWith("file:///android_asset/") -> absPath.substringBeforeLast('/', "") + "/"
                    absPath.isNotEmpty() -> "file://" + absPath.substringBeforeLast('/', "") + "/"
                    else -> null
                }
            }
            webView.post {
                if (webView.tag != cacheKey) return@post
                try {
                    if (html.isNullOrEmpty()) {
                        webView.loadData("", "text/html", "UTF-8")
                    } else {
                        webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
                    }
                } catch (_: Exception) {
                }
            }
        }.start()
    }
}
