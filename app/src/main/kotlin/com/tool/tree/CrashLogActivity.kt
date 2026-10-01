package com.tool.tree

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashLogActivity : AppCompatActivity() {

    private var fullLog = "No log data available."

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            buildUi()
        } catch (t: Throwable) {
            Log.e(TAG, "buildUi() failed, falling back to minimal UI", t)
            showFallbackUi(t)
        }
    }

    private fun buildUi() {
        var temp: String? = null
        try {
            temp = intent?.getStringExtra("crash_log")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to read crash_log extra", t)
        }
        fullLog = if (!temp.isNullOrEmpty()) temp else "No log data available."

        val truncated = fullLog.length > MAX_DISPLAY_LENGTH
        val displayLog = if (truncated)
            fullLog.substring(0, MAX_DISPLAY_LENGTH) + "\n\n... (The log is too long, I've trimmed the display portion - use Share to get the full log.)"
        else
            fullLog

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(24, 24, 24, 24)

        val title = TextView(this)
        title.text = "Tool Tree Crash"
        title.textSize = 25f
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        title.setPadding(14, 0, 0, 16)
        root.addView(title)

        val buttonRow = LinearLayout(this)
        buttonRow.orientation = LinearLayout.HORIZONTAL
        buttonRow.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        val btnParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        val copyBtn = Button(this)
        copyBtn.text = "Copy"
        copyBtn.textSize = 14f
        copyBtn.layoutParams = btnParams

        val shareBtn = Button(this)
        shareBtn.text = "Share"
        shareBtn.textSize = 14f
        shareBtn.layoutParams = btnParams

        buttonRow.addView(copyBtn)
        buttonRow.addView(shareBtn)

        val horizontalScroll = HorizontalScrollView(this)
        horizontalScroll.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )

        val verticalScroll = ScrollView(this)

        val textView = TextView(this)
        textView.text = displayLog
        textView.setTextIsSelectable(true)
        textView.textSize = 12f
        textView.setTypeface(android.graphics.Typeface.MONOSPACE)

        textView.setHorizontallyScrolling(true)
        textView.isHorizontalScrollBarEnabled = true

        verticalScroll.addView(textView)
        horizontalScroll.addView(verticalScroll)

        root.addView(buttonRow)
        root.addView(horizontalScroll)

        if (truncated) {
            val notice = TextView(this)
            notice.text = "The log was truncated during display because it was too long. Use Share to get the full log."
            notice.textSize = 11f
            notice.setPadding(14, 8, 14, 0)
            root.addView(notice)
        }

        setContentView(root)

        copyBtn.setOnClickListener {
            try {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                if (clipboard != null) {
                    val clip = ClipData.newPlainText("Crash Log", fullLog)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this, "Copied.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Clipboard not available.", Toast.LENGTH_SHORT).show()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Copy failed", t)
                Toast.makeText(this, "Failed to copy log.", Toast.LENGTH_SHORT).show()
            }
        }

        shareBtn.setOnClickListener {
            var fos: FileOutputStream? = null
            try {
                val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "Tool-Tree_log_$time.txt"
                val file = File(cacheDir, fileName)

                fos = FileOutputStream(file)
                fos.write(fullLog.toByteArray())
                fos.flush()
                fos.close()
                fos = null

                val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)

                val shareIntent = Intent(Intent.ACTION_SEND)
                shareIntent.type = "text/plain"
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                startActivity(Intent.createChooser(shareIntent, "Share log file"))
            } catch (t: Throwable) {
                Log.e(TAG, "Share failed", t)
                Toast.makeText(this, "Failed to share file.", Toast.LENGTH_SHORT).show()
            } finally {
                if (fos != null) {
                    try {
                        fos.close()
                    } catch (ignored: Throwable) {
                    }
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                try {
                    val intent = packageManager.getLaunchIntentForPackage(packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(intent)
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Back navigation failed", t)
                } finally {
                    finish()
                }
            }
        })
    }

    private fun showFallbackUi(original: Throwable) {
        try {
            val scroll = ScrollView(this)
            val text = TextView(this)
            text.setPadding(24, 24, 24, 24)
            text.setTextIsSelectable(true)

            var safeLog = fullLog
            if (safeLog.length > 20_000) {
                safeLog = safeLog.substring(0, 20_000) + "\n\n... (amputatedt)"
            }

            text.text = "Unable to display the full crash log due to an internal error:\n" +
                original.toString() + "\n\n" + safeLog

            scroll.addView(text)
            setContentView(scroll)
        } catch (t2: Throwable) {
            Log.e(TAG, "Fallback UI also failed, finishing activity", t2)
            Toast.makeText(applicationContext, "Crash log unavailable.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    companion object {
        private const val TAG = "CrashLogActivity"

        private const val MAX_DISPLAY_LENGTH = 200_000
    }
}
