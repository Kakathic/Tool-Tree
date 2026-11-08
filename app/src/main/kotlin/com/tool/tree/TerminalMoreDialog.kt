package com.tool.tree

import com.omarea.krscript.WebLauncher
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.omarea.common.ui.DialogHelper
import com.termux.terminal.TerminalSession

/**
 * Dialog "Thêm..." của menu nhấn giữ trong terminal:
 * WakeLock, giữ màn hình sáng, font mono thanh phím, danh sách URL.
 * Trạng thái dùng chung qua TerminalSessionStore nên đồng bộ với nút WakeLock trên thông báo.
 */
object TerminalMoreDialog {
    private const val PREFS_NAME = "terminal"
    private const val PREF_MONO_TOOLBAR = "mono_toolbar"
    private const val MAX_LIST_HEIGHT_DP = 360

    private val URL_REGEX = Regex("""(?i)\bhttps?://[^\s<>"'`\\^{|}]+""")

    fun loadMono(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(PREF_MONO_TOOLBAR, false)

    @JvmStatic
    fun show(context: Context, transcript: String?, session: TerminalSession?) {
        val ctx = unwrap(context)
        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_terminal_more, null)
        val wakeLock = view.findViewById<CheckBox>(R.id.terminal_more_wakelock)
        val keepScreen = view.findViewById<CheckBox>(R.id.terminal_more_keep_screen)
        val mono = view.findViewById<CheckBox>(R.id.terminal_more_mono)

        var syncing = false
        fun syncFromStore() {
            syncing = true
            wakeLock.isChecked = TerminalSessionStore.wakeLockOn
            keepScreen.isChecked = TerminalSessionStore.keepScreenOn
            mono.isChecked = TerminalSessionStore.monoToolbar
            syncing = false
        }
        syncFromStore()

        wakeLock.setOnCheckedChangeListener { _, checked ->
            if (!syncing) TerminalService.setWakeLock(ctx.applicationContext, checked)
        }
        keepScreen.setOnCheckedChangeListener { _, checked ->
            if (syncing) return@setOnCheckedChangeListener
            TerminalSessionStore.keepScreenOn = checked
            TerminalSessionStore.notifySettingsChanged()
        }
        mono.setOnCheckedChangeListener { _, checked ->
            if (syncing) return@setOnCheckedChangeListener
            TerminalSessionStore.monoToolbar = checked
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(PREF_MONO_TOOLBAR, checked).apply()
            TerminalSessionStore.notifySettingsChanged()
        }

        // Nút WakeLock trên thông báo đổi trạng thái khi dialog đang mở -> checkbox đổi theo
        val listener = object : TerminalSessionStore.Listener {
            override fun onSessionsChanged() {}
            override fun onSessionFinished(entry: TerminalSessionStore.Entry) {}
            override fun onSettingsChanged() = syncFromStore()
        }
        TerminalSessionStore.addListener(listener)
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                TerminalSessionStore.removeListener(listener)
            }
        })

        val dialog = DialogHelper.customDialog(ctx, view)
        view.findViewById<View>(R.id.terminal_more_urls).setOnClickListener {
            dialog.dismiss()
            showUrls(ctx, transcript)
        }
        view.findViewById<View>(R.id.terminal_more_share).setOnClickListener {
            dialog.dismiss()
            TerminalShare.shareTranscript(ctx, session, transcript)
        }
    }

    private fun showUrls(context: Context, transcript: String?) {
        val urls = extractUrls(transcript)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_terminal_urls, null)
        val list = view.findViewById<LinearLayout>(R.id.terminal_urls_list)
        val scroll = view.findViewById<ScrollView>(R.id.terminal_urls_scroll)
        val empty = view.findViewById<TextView>(R.id.terminal_urls_empty)

        if (urls.isEmpty()) {
            scroll.visibility = View.GONE
            empty.visibility = View.VISIBLE
        } else {
            val density = context.resources.displayMetrics.density
            val selectable = TypedValue().also { context.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true) }
            for (url in urls) {
                list.addView(TextView(context).apply {
                    text = url
                    textSize = 14f
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                    setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
                    if (selectable.resourceId != 0) setBackgroundResource(selectable.resourceId)
                    setOnClickListener { openUrl(context, url) }
                })
            }
            if (urls.size > 6) scroll.layoutParams.height = (MAX_LIST_HEIGHT_DP * density).toInt()
        }
        DialogHelper.customDialog(context, view)
    }

    /** Mới nhất ở trên, bỏ trùng; nội dung đã nối các dòng bị xuống hàng tự động */
    private fun extractUrls(text: String?): List<String> {
        if (text.isNullOrEmpty()) return emptyList()
        val result = LinkedHashSet<String>()
        for (match in URL_REGEX.findAll(text).toList().asReversed()) {
            val url = match.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}')
            if (url.length > "https://".length) result.add(url)
        }
        return result.toList()
    }

    private fun openUrl(context: Context, url: String) {
        try {
            WebLauncher.open(context, url)
        } catch (e: Exception) {
            Toast.makeText(context, R.string.terminal_url_open_fail, Toast.LENGTH_SHORT).show()
        }
    }

    // customDialog chỉ bật nền blur khi nhận Activity, nên bóc lớp ContextWrapper nếu có
    private fun unwrap(context: Context): Context {
        var c = context
        while (c is ContextWrapper && c !is Activity) c = c.baseContext
        return if (c is Activity) c else context
    }
}
