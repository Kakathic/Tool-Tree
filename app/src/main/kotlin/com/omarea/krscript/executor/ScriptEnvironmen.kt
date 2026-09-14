package com.omarea.krscript.executor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.WindowManager
import com.omarea.common.shared.FileWrite
import com.omarea.common.shell.KeepShell
import com.omarea.common.shell.KeepShellPublic
import com.omarea.common.shell.ShellSession
import com.omarea.common.shell.ShellTranslation
import com.omarea.krscript.FileOwner
import com.omarea.krscript.model.NodeInfoBase
import com.tool.tree.ThemeModeState
import java.io.DataOutputStream
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.ArrayList
import java.util.HashMap
import java.util.LinkedHashMap
import java.util.Locale
import java.util.TimeZone

object ScriptEnvironmen {
    private const val ASSETS_FILE = "file:///android_asset/"
    private var inited = false
    private var environmentPath = ""
    private var TOOKIT_DIR = ""
    private var rooted = false
    private var privateShell: ShellSession? = null
    private var shellTranslation: ShellTranslation? = null

    private var envShellTemplate = ""
    private var executorFileName = ""
    private var lastDarkMode: Boolean? = null

    @JvmStatic
    fun isInited(): Boolean = inited

    private fun init(context: Context): Boolean {
        val configSpf = context.getSharedPreferences("kr-script-config", Context.MODE_PRIVATE)
        return init(context, configSpf.getString("executor", "root/executor.sh"), configSpf.getString("toolkitDir", "home"))
    }

    @JvmStatic
    fun init(context: Context, executor: String?, toolkitDir: String?): Boolean {
        if (inited) return true

        shellTranslation = ShellTranslation(context.applicationContext)
        rooted = KeepShellPublic.checkRoot()

        try {
            if (!toolkitDir.isNullOrEmpty()) {
                TOOKIT_DIR = ExtractAssets(context).extractResources(toolkitDir) ?: ""
            }

            var fileName = executor ?: ""
            if (fileName.startsWith(ASSETS_FILE)) {
                fileName = fileName.substring(ASSETS_FILE.length)
            }

            val inputStream = context.assets.open(fileName)
            val bytes = ByteArray(inputStream.available())
            inputStream.read(bytes, 0, bytes.size)
            envShellTemplate = String(bytes, Charset.defaultCharset()).replace("\r", "")
            executorFileName = fileName

            inited = writeExecutorScript(context)
            if (inited) {
                lastDarkMode = ThemeModeState.isDarkMode()
            }

            context.getSharedPreferences("kr-script-config", Context.MODE_PRIVATE).edit().apply {
                putString("executor", executor)
                putString("toolkitDir", toolkitDir)
                apply()
            }

            privateShell = if (rooted) KeepShellPublic.getDefaultInstance() else KeepShell(rooted)
            return inited
        } catch (ex: Exception) {
            return false
        }
    }

    private fun writeExecutorScript(context: Context): Boolean {
        if (envShellTemplate.isEmpty() || executorFileName.isEmpty()) return false
        return try {
            var envShell = envShellTemplate
            val environment = getEnvironment(context)
            for ((key, value) in environment) {
                envShell = envShell.replace("\$({$key})", value)
            }
            val outputPathAbs = FileWrite.getPrivateFilePath(context, executorFileName)
            envShell = envShell.replace("\$({EXECUTOR_PATH})", outputPathAbs)

            val success = FileWrite.writePrivateFile(envShell.toByteArray(Charset.defaultCharset()), executorFileName, context)
            if (success) {
                environmentPath = outputPathAbs
            }
            success
        } catch (ex: Exception) {
            false
        }
    }

    @JvmStatic
    @Synchronized
    fun updateDarkMode(context: Context, isDarkMode: Boolean): Boolean {
        if (!inited) return false
        if (lastDarkMode == isDarkMode) return true
        val success = writeExecutorScript(context)
        if (success) {
            lastDarkMode = isDarkMode
        }
        return success
    }

    private fun md5(string: String): String {
        if (string.isEmpty()) return ""
        return try {
            val md5 = MessageDigest.getInstance("MD5")
            val bytes = md5.digest(string.toByteArray())
            buildString {
                for (b in bytes) {
                    val temp = Integer.toHexString(b.toInt() and 0xff)
                    if (temp.length == 1) append("0")
                    append(temp)
                }
            }
        } catch (e: NoSuchAlgorithmException) {
            ""
        }
    }

    private fun createShellCache(context: Context, script: String): String {
        val md5Hash = md5(script)
        val relativePath = "root/$md5Hash.sh"
        val absolutePath = FileWrite.getPrivateFilePath(context, relativePath)
        if (File(absolutePath).exists()) return absolutePath

        val bytes = ("#!/data/data/com.tool.tree/files/home/bin/bash\n\n$script").toByteArray()
        return if (FileWrite.writePrivateFile(bytes, relativePath, context)) absolutePath else ""
    }

    private fun extractScript(context: Context, fileNameArg: String): String? {
        val fileName = if (fileNameArg.startsWith(ASSETS_FILE)) fileNameArg.substring(ASSETS_FILE.length) else fileNameArg
        return FileWrite.writePrivateShellFile(fileName, fileName, context)
    }

    private fun appendNodeInfoExports(context: Context, nodeInfoBase: NodeInfoBase?, sb: StringBuilder) {
        if (nodeInfoBase == null || nodeInfoBase.currentPageConfigPath.isEmpty()) return

        val parentDir = nodeInfoBase.pageConfigDir
        val currentPath = nodeInfoBase.currentPageConfigPath
        sb.append("export PAGE_CONFIG_DIR='").append(parentDir).append("'\n")
        sb.append("export PAGE_CONFIG_FILE='").append(currentPath).append("'\n")

        val workDir = if (currentPath.startsWith(ASSETS_FILE)) ExtractAssets(context).getExtractPath(parentDir) else parentDir
        val workFile = if (currentPath.startsWith(ASSETS_FILE)) ExtractAssets(context).getExtractPath(currentPath) else currentPath

        if (!workDir.isNullOrEmpty()) sb.append("export PAGE_WORK_DIR='").append(workDir).append("'\n")
        if (!workFile.isNullOrEmpty()) sb.append("export PAGE_WORK_FILE='").append(workFile).append("'\n")
    }

    @JvmStatic
    @JvmOverloads
    fun executeResultRoot(context: Context, script: String?, nodeInfoBase: NodeInfoBase?, extraParams: HashMap<String, String>? = null): String {
        if (!inited) init(context)
        if (script.isNullOrEmpty()) return ""

        val script2 = script.trim()
        val path = if (script2.startsWith(ASSETS_FILE)) extractScript(context, script2) ?: "" else createShellCache(context, script)

        if (!inited) init(context)

        val stringBuilder = StringBuilder().apply {
            append("\n")
            append(buildDynamicExports(context))
            appendNodeInfoExports(context, nodeInfoBase, this)

            extraParams?.forEach { (key, v) ->
                val value = v?.replace("'", "'\\''") ?: ""
                append("export ").append(key).append("='").append(value).append("'\n")
            }

            append("\n\n").append(environmentPath).append(" \"").append(path).append("\"")
        }

        val shell = privateShell
        val cmdStr = stringBuilder.toString()
        return if (shellTranslation != null && shell != null) {
            shellTranslation!!.resolveRow(shell.doCmdSync(cmdStr))
        } else {
            shell?.doCmdSync(cmdStr) ?: ""
        }
    }

    @JvmStatic
    fun executeMultipleResultRoot(
        context: Context,
        scripts: LinkedHashMap<String, String>?,
        nodeInfoBase: NodeInfoBase?
    ): LinkedHashMap<String, String> {
        val results = LinkedHashMap<String, String>()
        if (scripts.isNullOrEmpty()) return results

        if (!inited) init(context)

        val validScripts = scripts.filterValues { it.trim().isNotEmpty() }
        scripts.keys.forEach { if (!validScripts.containsKey(it)) results[it] = "" }
        if (validScripts.isEmpty()) return results

        if (validScripts.size == 1) {
            val only = validScripts.entries.iterator().next()
            results[only.key] = executeResultRoot(context, only.value, nodeInfoBase)
            return results
        }

        val cmd = StringBuilder().apply {
            append("\n")
            append(buildDynamicExports(context))
            appendNodeInfoExports(context, nodeInfoBase, this)
            append("\n")
        }

        val orderedTags = ArrayList(validScripts.keys)
        val merged = StringBuilder()
        for (tag in orderedTags) {
            val script = validScripts[tag]!!
            val marker = "KRBATCH_" + md5(tag)

            merged.append("echo '>>>").append(marker).append("'\n(\n")
            if (script.trim().startsWith(ASSETS_FILE)) {
                val assetPath = extractScript(context, script.trim())
                if (!assetPath.isNullOrEmpty()) {
                    merged.append(". \"").append(assetPath).append("\"\n")
                }
            } else {
                merged.append(script).append("\n")
            }
            merged.append(")\necho '<<<").append(marker).append("'\n")
        }

        val mergedPath = createShellCache(context, merged.toString())
        if (mergedPath.isNotEmpty()) {
            cmd.append(environmentPath).append(" \"").append(mergedPath).append("\"\n")
        }

        var rawOutput = privateShell?.doCmdSync(cmd.toString()) ?: ""
        if (shellTranslation != null) {
            rawOutput = shellTranslation!!.resolveRow(rawOutput)
        }

        for (tag in orderedTags) {
            val marker = "KRBATCH_" + md5(tag)
            val startMarker = ">>>$marker"
            val endMarker = "<<<$marker"
            val startIdx = rawOutput.indexOf(startMarker)
            val endIdx = rawOutput.indexOf(endMarker)
            results[tag] = if (startIdx >= 0 && endIdx > startIdx) {
                rawOutput.substring(startIdx + startMarker.length, endIdx).trim()
            } else {
                "error"
            }
        }

        return results
    }

    private fun getDynamicEnvironment(context: Context): HashMap<String, String> {
        val params = HashMap<String, String>()
        try {
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            batteryIntent?.let {
                val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    params["BATTERY_LEVEL"] = (level * 100 / scale).toString()
                }

                val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                params["BATTERY_CHARGING"] = if (
                    status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
                ) "true" else "false"

                val temperature = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                if (temperature >= 0) {
                    params["BATTERY_TEMPERATURE"] = (temperature / 10.0).toString()
                }
            }
        } catch (ignored: Exception) {}
        return params
    }

    private fun buildDynamicExports(context: Context): String {
        return buildString {
            for ((key, value) in getDynamicEnvironment(context)) {
                append("export ").append(key).append("='").append(value.replace("'", "'\\''")).append("'\n")
            }
        }
    }

    private fun getStartPath(context: Context): String {
        val dir = FileWrite.getPrivateFileDir(context)
        return if (dir.endsWith("/")) dir.substring(0, dir.length - 1) else dir
    }

    private fun getSystemProperty(key: String, defaultValue: String = "unknown"): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            method.invoke(null, key, defaultValue) as? String ?: defaultValue
        } catch (e: Exception) {
            defaultValue
        }
    }

    private fun getEnvironment(context: Context): HashMap<String, String> {
        val params = HashMap<String, String>().apply {
            put("TOOLKIT", TOOKIT_DIR)
            put("START_DIR", getStartPath(context))
            put("TEMP_DIR", context.cacheDir.absolutePath)
            put("LANGUAGE", Locale.getDefault().language)
            put("COUNTRY", Locale.getDefault().country)
            put("TIMEZONE", TimeZone.getDefault().id)
            put("ANDROID_RELEASE", Build.VERSION.RELEASE)
            put("ANDROID_DEVICE", Build.DEVICE)
            put("ANDROID_BRAND", Build.BRAND)
            put("ANDROID_MANUFACTURER", Build.MANUFACTURER)
            put("ANDROID_FINGERPRINT", Build.FINGERPRINT)
            put("ANDROID_MODEL", Build.MODEL)
            put("ANDROID_ID", Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID))
            
            @Suppress("DEPRECATION")
            put("CPU_ABI", Build.CPU_ABI)
            put("ARCH", System.getProperty("os.arch") ?: "")
            put("ANDROID_SDK", Build.VERSION.SDK_INT.toString())
            put("KERNEL_VERSION", System.getProperty("os.version") ?: "")
            put("SELINUX", getSELinuxStatus())

            // Lấy kích thước màn hình chuẩn xác
            val windowManager = context.getSystemService(WindowManager::class.java)
            val width: Int
            val height: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = windowManager.currentWindowMetrics.bounds
                width = bounds.width()
                height = bounds.height()
            } else {
                val displayMetrics = android.util.DisplayMetrics()
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealMetrics(displayMetrics)
                width = displayMetrics.widthPixels
                height = displayMetrics.heightPixels
            }
            put("SCREEN_WIDTH", width.toString())
            put("SCREEN_HEIGHT", height.toString())

            put("BATTERY_CAPACITY", getBatteryCapacity(context))

            // Trạng thái bootloader và verified boot
            put("BOOTLOADER_LOCKED", getSystemProperty("ro.boot.flash.locked"))
            put("VERIFIED_BOOT_STATE", getSystemProperty("ro.boot.verifiedbootstate"))

            val fileOwner = FileOwner(context)
            put("ANDROID_UID", fileOwner.getUserId().toString())

            try {
                put("APP_USER_ID", fileOwner.getFileOwner())
            } catch (ignored: Exception) {}

            try {
                put("DARK_MODE", if (ThemeModeState.isDarkMode()) "true" else "false")
            } catch (ignored: Exception) {}

            put("ROOT_PERMISSION", if (rooted) "true" else "false")
            put("SDCARD_PATH", Environment.getExternalStorageDirectory().absolutePath

            try {
                val packageInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                put("PACKAGE_NAME", context.packageName)
                put("PACKAGE_VERSION_NAME", packageInfo.versionName ?: "")
                put("PATH_APK", context.applicationInfo.sourceDir)
                put("APP_UID", android.os.Process.myUid().toString())
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }
                put("PACKAGE_VERSION_CODE", versionCode.toString())
            } catch (ex: Exception) {}
        }
        return params
    }

    private fun getBatteryCapacity(context: Context): String {
        return try {
            val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
            val instance = powerProfileClass.getConstructor(Context::class.java).newInstance(context)
            val capacity = powerProfileClass.getMethod("getBatteryCapacity").invoke(instance) as Double
            capacity.toInt().toString()
        } catch (ex: Exception) {
            "Unknown"
        }
    }

    private fun getSELinuxStatus(): String {
        if (rooted) {
            val result = try {
                KeepShellPublic.doCmdSync("getenforce").trim()
            } catch (ignored: Exception) { "" }
            if (result.isNotEmpty() && result != "error" && !result.contains("not found", ignoreCase = true)) {
                return result
            }
        }
    
        return try {
            val isEnforced = Class.forName("android.os.SELinux")
                .getMethod("isSELinuxEnforced").invoke(null) as Boolean
            if (isEnforced) "Enforcing" else "Permissive"
        } catch (ex: Exception) {
            "Unknown"
        }
    }
    
    private fun getVariables(params: HashMap<String, String>?): ArrayList<String> {
        val envp = ArrayList<String>()
        params?.forEach { (key, value) ->
            val safeValue = (value ?: "").replace("'", "'\\''")
            envp.add("$key='$safeValue'")
        }
        return envp
    }

    private fun getExecuteScript(context: Context, script: String?, tag: String?): String {
        if (!inited) init(context)
        if (script.isNullOrEmpty()) return ""

        val script2 = script.trim()
        val cachePath = if (script2.startsWith(ASSETS_FILE)) extractScript(context, script2) ?: script else createShellCache(context, script)
        return "$environmentPath \"$cachePath\" \"$tag\""
    }

    @JvmStatic
    fun getRuntime(): Process? {
        return try {
            if (rooted) {
                try {
                    Runtime.getRuntime().exec("su")
                } catch (ignored: Exception) {
                    Runtime.getRuntime().exec("sh")
                }
            } else {
                Runtime.getRuntime().exec("sh")
            }
        } catch (ex: Exception) {
            null
        }
    }

    @JvmStatic
    @JvmOverloads
    fun executeShell(
        context: Context,
        dataOutputStream: DataOutputStream,
        cmds: String?,
        paramsArg: HashMap<String, String>?,
        nodeInfo: NodeInfoBase?,
        tag: String?,
        needInput: Boolean = false
    ) {
        val params = paramsArg ?: HashMap()

        if (nodeInfo != null) {
            val parentPageConfigDir = nodeInfo.pageConfigDir
            val currentPageConfigPath = nodeInfo.currentPageConfigPath
            if (!parentPageConfigDir.isNullOrEmpty()) {
                params["PAGE_CONFIG_DIR"] = parentPageConfigDir
            }
            if (!currentPageConfigPath.isNullOrEmpty()) {
                params["PAGE_CONFIG_FILE"] = currentPageConfigPath
                val workDir = if (currentPageConfigPath.startsWith(ASSETS_FILE)) ExtractAssets(context).getExtractPath(parentPageConfigDir) else parentPageConfigDir
                val workFile = if (currentPageConfigPath.startsWith(ASSETS_FILE)) ExtractAssets(context).getExtractPath(currentPageConfigPath) else currentPageConfigPath
                
                if (!workDir.isNullOrEmpty()) params["PAGE_WORK_DIR"] = workDir
                if (!workFile.isNullOrEmpty()) params["PAGE_WORK_FILE"] = workFile
            }
        }

        val envp = getVariables(params)
        val envpCmds = StringBuilder().apply {
            append(buildDynamicExports(context))
            if (envp.isNotEmpty()) {
                for (param in envp) {
                    append("export ").append(param).append("\n")
                }
            }
        }

        try {
            dataOutputStream.write(envpCmds.toString().toByteArray(StandardCharsets.UTF_8))
            val executeScript = getExecuteScript(context, cmds, tag)
            if (executeScript.isEmpty()) return

            if (needInput) {
                dataOutputStream.write(("$executeScript; sleep 0.2; exit\n").toByteArray(StandardCharsets.UTF_8))
            } else {
                dataOutputStream.write(("$executeScript\n\nsleep 0.2; exit\nexit\n").toByteArray(StandardCharsets.UTF_8))
            }
            dataOutputStream.flush()
        } catch (ignored: Exception) {}
    }
}
