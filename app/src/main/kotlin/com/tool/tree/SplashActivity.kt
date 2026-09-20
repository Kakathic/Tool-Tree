package com.tool.tree

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.TypedValue
import android.animation.ObjectAnimator
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.omarea.common.shell.KeepShellPublic
import com.omarea.common.shell.ShellExecutor
import com.omarea.common.shell.shizuku.ShizukuShellManager
import com.omarea.common.ui.DialogHelper
import com.omarea.krscript.config.StringResRef
import com.omarea.krscript.executor.ScriptEnvironmen
import com.omarea.krscript.config.PageConfigReader
import com.omarea.krscript.config.PageConfigSh
import com.omarea.krscript.model.NodeInfoBase
import com.omarea.krscript.model.PageNode
import com.omarea.krscript.model.SilentShellOutputHandler
import com.tool.tree.databinding.ActivitySplashBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.util.Locale

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val REQUEST_CODE_PERMISSIONS = 1001
    private val REQUEST_CODE_ALL_FILES = 1002

    private var hasRoot = false
    private var started = false
    private var logoAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        LanguageManager.init(this)
        super.onCreate(savedInstanceState)
        ThemeModeState.switchTheme(this)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ShellExecutor.setTmpDir(cacheDir.absolutePath)

        logoAnimator = ObjectAnimator.ofFloat(binding.startLogoXml, "rotation", 0f, 360f).apply {
            duration = 3000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

        if (ScriptEnvironmen.isInited() && isTaskRoot &&
            !intent.getBooleanExtra("force_reset", false)) {
            loadTabsThenHome()
            return
        }

        if (!hasRequiredPermissions()) {
            showAgreementDialog()
        } else {
            checkPermissionsNextStep()
        }
    }

    private fun showAgreementDialog() {
        DialogHelper.warning(
            this,
            getString(R.string.permission_dialog_title),
            getString(R.string.permission_dialog_message),
            Runnable {
                requestRequiredPermissions()
            },
            Runnable { finish() }
        ).setCancelable(false)
    }

    // Android 11+ (API 30+): READ/WRITE_EXTERNAL_STORAGE không còn cấp quyền ghi file chung,
    // phải dùng quyền "Quản lý tất cả các tệp" (MANAGE_EXTERNAL_STORAGE). Android 10 trở xuống
    // giữ nguyên cách xin quyền cũ.
    private fun useAllFilesAccess(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    private fun hasRequiredPermissions(): Boolean {
        if (useAllFilesAccess()) return Environment.isExternalStorageManager()
        val permissions = listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestRequiredPermissions() {
        if (useAllFilesAccess()) {
            requestAllFilesAccess()
            return
        }
        val permissions = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_PERMISSIONS)
    }

    // Mở trang cài đặt quyền: ưu tiên trang riêng của app, ROM không hỗ trợ (vd MIUI) thì lùi
    // dần về danh sách chung, rồi trang thông tin ứng dụng.
    private fun requestAllFilesAccess() {
        val packageUri = Uri.parse("package:$packageName")
        val candidates = listOf(
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri),
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        )
        for (candidate in candidates) {
            try {
                startActivityForResult(candidate, REQUEST_CODE_ALL_FILES)
                return
            } catch (_: Exception) {
            }
        }
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_ALL_FILES) {
            if (hasRequiredPermissions()) checkPermissionsNextStep() else finish()
        }
    }

    private fun checkPermissionsNextStep() {
        startWakeLockServiceOnce()
        checkRootAndStart()
    }

    private fun startWakeLockServiceOnce() {
        val prefs = getSharedPreferences("kr-script-config", MODE_PRIVATE)
        if (!prefs.getBoolean("wakelock_service_started_once", false)) {
            createNotificationChannel()
            prefs.edit().putBoolean("wakelock_service_started_once", true).apply()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "WakeLockServiceChannel",
                getString(R.string.wakelock_service_running),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableLights(false)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                checkPermissionsNextStep()
            } else finish()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    @Synchronized
    private fun checkRootAndStart() {
        if (started) return
        started = true

        lifecycleScope.launch(Dispatchers.IO) {
            hasRoot = KeepShellPublic.checkRoot()
            if (!hasRoot && ShizukuShellManager.isInstalled()) {
                ShizukuShellManager.requestPermissionAndAwait(this@SplashActivity)
                val shizukuSession = ShizukuShellManager.tryUseGrantedSession(this@SplashActivity)
                if (shizukuSession != null) {
                    KeepShellPublic.useSession(shizukuSession)
                    hasRoot = shizukuSession.checkRoot()
                }
            }
            withContext(Dispatchers.Main) {
                startToFinish()
            }
        }
    }

    private fun startToFinish() {
        binding.startStateText.text = getString(R.string.pop_started)
        val config = KrScriptConfig().init(this)

        if (config.getBeforeStartSh().isNotEmpty()) {
            runBeforeStartSh(config, hasRoot)
        } else {
            loadTabsThenHome()
        }
    }

    private fun loadTabsThenHome() {
        lifecycleScope.launch(Dispatchers.IO) {
            val config = KrScriptConfig()
            config.init(this@SplashActivity)

            val updateInfoDeferred = async {
                withTimeoutOrNull(8000) { AppUpdateChecker.fetchUpdateInfo(this@SplashActivity) }
            }

            val favorites = getItems(config.getFavoriteConfig())
            val pages = getItems(config.getPageListConfig())
            val tab3Items = getItems(config.getCustomTab3Config())
            val tab4Items = getItems(config.getCustomTab4Config())

            if (!isActive) return@launch

            val preloaded = MainTabsPreloadedData(favorites, pages, tab3Items, tab4Items)
            val updateInfo = try {
                updateInfoDeferred.await()
            } catch (_: Exception) {
                null
            }

            withContext(Dispatchers.Main) {
                if (!isActive || isFinishing || isDestroyed) return@withContext
                gotoHome(preloaded, updateInfo)
            }
        }
    }

    private fun gotoHome(preloadedTabs: MainTabsPreloadedData? = null, updateInfo: AppUpdateInfo? = null) {
        logoAnimator?.cancel()
        logoAnimator = null

        val targetIntent =
            if (intent?.getBooleanExtra("JumpActionPage", false) == true)
                Intent(this, ActionPage::class.java).apply { putExtras(intent!!) }
            else
                Intent(this, MainActivity::class.java).apply {
                    if (preloadedTabs != null) putExtra("preloadedTabs", preloadedTabs)
                    if (updateInfo != null) putExtra("pendingUpdate", updateInfo)
                }
        startActivity(targetIntent)
        finish()
    }

    private fun getItems(pageNode: PageNode?): ArrayList<NodeInfoBase>? {
        if (pageNode == null) return null
        return try {
            if (pageNode.pageConfigSh.isNotEmpty()) {
                PageConfigSh(this, pageNode.pageConfigSh, null).execute()
            } else if (pageNode.pageConfigPath.isNotEmpty()) {
                PageConfigReader(applicationContext, pageNode.pageConfigPath, null).readConfigXml()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun runBeforeStartSh(config: KrScriptConfig, hasRoot: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val process = if (hasRoot) ShellExecutor.getSuperUserRuntime() else ShellExecutor.getRuntime()
                process?.let {
                    DataOutputStream(it.outputStream).use { os ->
                        ScriptEnvironmen.executeShell(this@SplashActivity, os, config.getBeforeStartSh(), config.getVariables(), null, "pio-splash")
                    }
                    launch { readStreamAsync(it.inputStream.bufferedReader()) }
                    launch { readStreamAsync(it.errorStream.bufferedReader()) }
                    it.waitFor()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    loadTabsThenHome()
                }
            }
        }
    }

    private val rows = mutableListOf<String>()
    private var ignored = false
    private val maxLines = 5
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private val splashLogHandler: SplashLogOutputHandler by lazy { SplashLogOutputHandler() }

    private var currentLineIsProgress = false

    private inner class SplashLogOutputHandler : SilentShellOutputHandler(this@SplashActivity) {
        override fun onReader(msg: Any?) {
            val line = msg?.toString() ?: return
            onLogOutput(StringResRef.resolve(this@SplashActivity, line), currentLineIsProgress)
        }
    }

    private fun readStreamAsync(reader: BufferedReader) {
        try {
            val buffer = StringBuilder()
            var lastWasCR = false
            var c: Int
            while (reader.read().also { c = it } != -1) {
                when (c) {
                    '\r'.code -> {
                        handleRawLogLine(buffer.toString(), isProgress = true)
                        buffer.setLength(0)
                        lastWasCR = true
                    }
                    '\n'.code -> {
                        if (lastWasCR) {
                            lastWasCR = false
                        } else {
                            handleRawLogLine(buffer.toString(), isProgress = false)
                            buffer.setLength(0)
                        }
                    }
                    else -> {
                        buffer.append(c.toChar())
                        lastWasCR = false
                    }
                }
            }
            if (buffer.isNotEmpty()) {
                handleRawLogLine(buffer.toString(), isProgress = false)
            }
        } catch (e: Exception) {}
    }

    private fun handleRawLogLine(line: String, isProgress: Boolean) {
        handler.post {
            currentLineIsProgress = isProgress
            splashLogHandler.processOutput(line)
        }
    }

    private fun onLogOutput(log: String, isProgress: Boolean) {
        synchronized(rows) {
            if (isProgress && rows.isNotEmpty()) {
                if (log.isEmpty()) return
                rows[rows.size - 1] = log
            } else {
                if (rows.size >= maxLines) {
                    rows.removeAt(0)
                    ignored = true
                }
                rows.add(log)
            }
            binding.startStateText.text = rows.joinToString("\n", if (ignored) "……………\n" else "")
        }
    }
}
