package com.tool.tree

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

class OpenFileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val filePath = intent.getStringExtra("path") ?: run {
            showToast("No file path provided")
            finish()
            return
        }

        val file = File(filePath)
        if (!file.exists()) {
            showToast("File does not exist")
            finish()
            return
        }

        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
        val ext = MimeTypeMap.getFileExtensionFromUrl(file.name.lowercase())
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: if (file.name.endsWith(".apk", true)) "application/vnd.android.package-archive" else "*/*"

        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            startActivity(viewIntent)
        } catch (e: ActivityNotFoundException) {
            showToast("No application found to open this file")
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        finish()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
