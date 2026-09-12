package com.omarea.krscript.executor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
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
    fun isInited(): Boolean {
        return inited
    }

    private fun init(context: Context): Boolean {
        val configSpf = context.getSharedPreferences("kr-script-config", Context.MODE_PRIVATE)

        return init(context, configSpf.getString("executor", "root/executor.sh"), configSpf.getString("toolkitDir", "home"))
    }

    @JvmStatic
    fun init(context: Context, executor: String?, toolkitDir: String?): Boolean {
        if (inited) {
            return true
        }

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
            val length = inputStream.read(bytes, 0, bytes.size)
            val envShell = String(bytes, Charset.defaultCharset()).replace("\r", "")

            envShellTemplate = envShell
            executorFileName = fileName

            inited = writeExecutorScript(context)
            if (inited) {
                lastDarkMode = ThemeModeState.isDarkMode()
            }

            val configSpf = context.getSharedPreferences("kr-script-config", Context.MODE_PRIVATE).edit()
            configSpf.putString("executor", executor)
            configSpf.putString("toolkitDir", toolkitDir)
            configSpf.apply()

            privateShell = if (rooted) KeepShellPublic.getDefaultInstance() else KeepShell(rooted)

            return inited
        } catch (ex: Exception) {
            return false
        }
    }

    private fun writeExecutorScript(context: Context): Boolean {
        if (envShellTemplate.isEmpty() || executorFileName.isEmpty()) {
            return false
        }
        try {
            var envShell = envShellTemplate

            val environment = getEnvironment(context)
            for (key in environment.keys) {
                val value = environment[key] ?: ""
                envShell = envShell.replace("\$({$key})", value)
            }
            val outputPathAbs = FileWrite.getPrivateFilePath(context, executorFileName)
            envShell = envShell.replace("\$({EXECUTOR_PATH})", outputPathAbs)

            val success = FileWrite.writePrivateFile(envShell.toByteArray(Charset.defaultCharset()), executorFileName, context)
            if (success) {
                environmentPath = outputPathAbs
            }
            return success
        } catch (ex: Exception) {
            return false
        }
    }

    @JvmStatic
    @Synchronized
    fun updateDarkMode(context: Context, isDarkMode: Boolean): Boolean {
        if (!inited) {
            return false
        }
        if (lastDarkMode != null && lastDarkMode == isDarkMode) {
            return true
        }
        val success = writeExecutorScript(context)
        if (success) {
            lastDarkMode = isDarkMode
        }
        return success
    }

    private fun md5(string: String): String {
        if (string.isEmpty()) {
            return ""
        }

        val md5: MessageDigest
        try {
            md5 = MessageDigest.getInstance("MD5")
            val bytes = md5.digest(string.toByteArray())
            val result = StringBuilder()
            for (b in bytes) {
                var temp = Integer.toHexString(b.toInt() and 0xff)
                if (temp.length == 1) {
                    temp = "0$temp"
                }
                result.append(temp)
            }
            return result.toString()
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        }

        return ""
    }

    private fun createShellCache(context: Context, script: String): String {
        val md5 = md5(script)
        val relativePath = "root/$md5.sh"
        val absolutePath = FileWrite.getPrivateFilePath(context, relativePath)
        if (File(absolutePath).exists()) {
            return absolutePath
        }
        val bytes = ("#!/data/data/com.tool.tree/files/home/bin/bash\n\n$script").toByteArray()

        if (FileWrite.writePrivateFile(bytes, relativePath, context)) {
            return absolutePath
        }
        return ""
    }

    private fun extractScript(context: Context, fileNameArg: String): String? {
        var fileName = fileNameArg
        if (fileName.startsWith(ASSETS_FILE)) {
            fileName = fileName.substring(ASSETS_FILE.length)
        }
        return FileWrite.writePrivateShellFile(fileName, fileName, context)
    }

    @JvmStatic
    @JvmOverloads
    fun executeResultRoot(context: Context, script: String?, nodeInfoBase: NodeInfoBase?, extraParams: HashMap<String, String>? = null): String {
        if (!inited) {
            init(context)
        }

        if (script.isNullOrEmpty()) {
            return ""
        }

        val script2 = script.trim()
        val path: String
        path = if (script2.startsWith(ASSETS_FILE)) {
            extractScript(context, script2) ?: ""
        } else {
            createShellCache(context, script)
        }

        if (!inited) {
            init(context)
        }

        val stringBuilder = StringBuilder()

        stringBuilder.append("\n")
        stringBuilder.append(buildDynamicExports(context))
        if (nodeInfoBase != null && nodeInfoBase.currentPageConfigPath.isNotEmpty()) {
            val parentPageConfigDir = nodeInfoBase.pageConfigDir
            val currentPageConfigPath = nodeInfoBase.currentPageConfigPath
            stringBuilder.append("export PAGE_CONFIG_DIR='").append(parentPageConfigDir).append("'\n")
            stringBuilder.append("export PAGE_CONFIG_FILE='").append(currentPageConfigPath).append("'\n")

            if (currentPageConfigPath.startsWith("file:///android_asset/")) {
                stringBuilder.append("export PAGE_WORK_DIR='").append(ExtractAssets(context).getExtractPath(parentPageConfigDir)).append("'\n")
                stringBuilder.append("export PAGE_WORK_FILE='").append(ExtractAssets(context).getExtractPath(currentPageConfigPath)).append("'\n")
            } else {
                stringBuilder.append("export PAGE_WORK_DIR='").append(parentPageConfigDir).append("'\n")
                stringBuilder.append("export PAGE_WORK_FILE='").append(currentPageConfigPath).append("'\n")
            }
        }

        if (extraParams != null) {
            for ((key, v) in extraParams) {
                val value = v?.replace("'", "'\\''") ?: ""
                stringBuilder.append("export ").append(key).append("='").append(value).append("'\n")
            }
        }

        stringBuilder.append("\n\n")
        stringBuilder.append("$environmentPath \"$path\"")
        val shell = privateShell
        return if (shellTranslation != null && shell != null) {
            shellTranslation!!.resolveRow(shell.doCmdSync(stringBuilder.toString()))
        } else {
            shell?.doCmdSync(stringBuilder.toString()) ?: ""
        }
    }

    @JvmStatic
    fun executeMultipleResultRoot(
        context: Context,
        scripts: LinkedHashMap<String, String>?,
        nodeInfoBase: NodeInfoBase?
    ): LinkedHashMap<String, String> {
        val results = LinkedHashMap<String, String>()
        if (scripts.isNullOrEmpty()) {
            return results
        }

        if (!inited) {
            init(context)
        }

        val validScripts = LinkedHashMap<String, String>()
        for ((key, script) in scripts) {
            if (script.trim().isNotEmpty()) {
                validScripts[key] = script
            } else {
                results[key] = ""
            }
        }
        if (validScripts.isEmpty()) {
            return results
        }

        if (validScripts.size == 1) {
            val only = validScripts.entries.iterator().next()
            results[only.key] = executeResultRoot(context, only.value, nodeInfoBase)
            return results
        }

        val cmd = StringBuilder()
        cmd.append("\n")
        cmd.append(buildDynamicExports(context))

        if (nodeInfoBase != null && nodeInfoBase.currentPageConfigPath.isNotEmpty()) {
            val parentPageConfigDir = nodeInfoBase.pageConfigDir
            val currentPageConfigPath = nodeInfoBase.currentPageConfigPath
            cmd.append("export PAGE_CONFIG_DIR='").append(parentPageConfigDir).append("'\n")
            cmd.append("export PAGE_CONFIG_FILE='").append(currentPageConfigPath).append("'\n")

            if (currentPageConfigPath.startsWith("file:///android_asset/")) {
                cmd.append("export PAGE_WORK_DIR='").append(ExtractAssets(context).getExtractPath(parentPageConfigDir)).append("'\n")
                cmd.append("export PAGE_WORK_FILE='").append(ExtractAssets(context).getExtractPath(currentPageConfigPath)).append("'\n")
            } else {
                cmd.append("export PAGE_WORK_DIR='").append(parentPageConfigDir).append("'\n")
                cmd.append("export PAGE_WORK_FILE='").append(currentPageConfigPath).append("'\n")
            }
        }
        cmd.append("\n")

        val orderedTags = ArrayList(validScripts.keys)

        val merged = StringBuilder()
        for (tag in orderedTags) {
            val script = validScripts[tag]!!
            val script2 = script.trim()
            val marker = "KRBATCH_" + md5(tag)

            merged.append("echo '>>>").append(marker).append("'\n")
            merged.append("(\n")
            if (script2.startsWith(ASSETS_FILE)) {
                val assetPath = extractScript(context, script2)
                if (!assetPath.isNullOrEmpty()) {
                    merged.append(". \"").append(assetPath).append("\"\n")
                }
            } else {
                merged.append(script2).append("\n")
            }
            merged.append(")\n")
            merged.append("echo '<<<").append(marker).append("'\n")
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
            if (startIdx >= 0 && endIdx > startIdx) {
                val section = rawOutput.substring(startIdx + startMarker.length, endIdx)
                results[tag] = section.trim()
            } else {
                results[tag] = "error"
            }
        }

        return results
    }

    // Các giá trị "động" - đổi liên tục theo thời gian thực (pin, mạng...), KHÔNG bake tĩnh vào
    // file executor như getEnvironment() (chỉ ghi lại 1 lần lúc init()/updateDarkMode()). Được
    // tính lại và export TƯƠI mỗi lần chạy script (xem buildDynamicExports, dùng trong
    // executeResultRoot/executeShell/executeMultipleResultRoot) - không cần ghi lại file nào.
    private fun getDynamicEnvironment(context: Context): HashMap<String, String> {
        val params = HashMap<String, String>()

        try {
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (batteryIntent != null) {
                val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    params["BATTERY_LEVEL"] = (level * 100 / scale).toString()
                }

                val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                params["BATTERY_CHARGING"] = if (
                    status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
                ) "true" else "false"

                val temperature = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                if (temperature >= 0) {
                    params["BATTERY_TEMPERATURE"] = (temperature / 10.0).toString()
                }
            }
        } catch (ignored: Exception) {
        }

        return params
    }

    // Chuỗi "export KEY='value'\n" từ getDynamicEnvironment() - chèn vào đầu mỗi lần thực thi
    // script (executeResultRoot/executeShell/executeMultipleResultRoot).
    private fun buildDynamicExports(context: Context): String {
        val sb = StringBuilder()
        for ((key, value) in getDynamicEnvironment(context)) {
            sb.append("export ").append(key).append("='").append(value.replace("'", "'\\''")).append("'\n")
        }
        return sb.toString()
    }

    private fun getStartPath(context: Context): String {
        val dir = FileWrite.getPrivateFileDir(context)
        if (dir.endsWith("/")) {
            return dir.substring(0, dir.length - 1)
        }
        return dir
    }

    private fun getEnvironment(context: Context): HashMap<String, String> {
        val params = HashMap<String, String>()

        params["TOOLKIT"] = TOOKIT_DIR
        params["START_DIR"] = getStartPath(context)
        params["TEMP_DIR"] = context.cacheDir.absolutePath
        params["LANGUAGE"] = Locale.getDefault().language
        params["COUNTRY"] = Locale.getDefault().country
        params["TIMEZONE"] = TimeZone.getDefault().id
        params["ANDROID_RELEASE"] = Build.VERSION.RELEASE
        params["ANDROID_DEVICE"] = Build.DEVICE
        params["ANDROID_BRAND"] = Build.BRAND
        params["ANDROID_MANUFACTURER"] = Build.MANUFACTURER
        params["ANDROID_FINGERPRINT"] = Build.FINGERPRINT
        params["ANDROID_MODEL"] = Build.MODEL
        params["ANDROID_ID"] = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        @Suppress("DEPRECATION")
        params["CPU_ABI"] = Build.CPU_ABI
        params["ARCH"] = System.getProperty("os.arch") ?: ""
        params["ANDROID_SDK"] = Build.VERSION.SDK_INT.toString()
        params["KERNEL_VERSION"] = System.getProperty("os.version") ?: ""
        params["SELINUX"] = getSELinuxStatus()

        val displayMetrics = context.resources.displayMetrics
        params["SCREEN_WIDTH"] = displayMetrics.widthPixels.toString()
        params["SCREEN_HEIGHT"] = displayMetrics.heightPixels.toString()
        params["BATTERY_CAPACITY"] = getBatteryCapacity(context)

        val fileOwner = FileOwner(context)
        val androidUid = fileOwner.getUserId()
        params["ANDROID_UID"] = androidUid.toString()

        try {
            params["APP_USER_ID"] = fileOwner.getFileOwner()
        } catch (ignored: Exception) {
        }

        try {
            params["DARK_MODE"] = if (ThemeModeState.isDarkMode()) "true" else "false"
        } catch (ignored: Exception) {
        }

        params["ROOT_PERMISSION"] = if (rooted) "true" else "false"
        params["SDCARD_PATH"] = Environment.getExternalStorageDirectory().absolutePath

        try {
            val packageInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            params["PACKAGE_NAME"] = context.packageName
            params["PACKAGE_VERSION_NAME"] = packageInfo.versionName ?: ""
            params["PATH_APK"] = context.applicationInfo.sourceDir
            params["APP_UID"] = android.os.Process.myUid().toString()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                params["PACKAGE_VERSION_CODE"] = packageInfo.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                params["PACKAGE_VERSION_CODE"] = packageInfo.versionCode.toString()
            }
        } catch (ex: Exception) {
        }

        return params
    }

    // Không có API công khai để lấy dung lượng pin thiết kế (mAh) - dùng reflection lên class ẩn
    // com.android.internal.os.PowerProfile (cách phổ biến, dùng cả trong nhiều app hệ thống/tuỳ
    // biến pin). Có thể thất bại trên 1 số thiết bị hạn chế truy cập hidden API -> trả "Unknown".
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

    // getenforce chạy trực tiếp (Runtime.exec) bị SELinux domain của app thường chặn đọc dù máy
    // đã root - vì tiến trình đó KHÔNG chạy qua root, chỉ mang uid/domain của chính app. Máy đã
    // root thì phải chạy lệnh này qua root shell (KeepShellPublic) mới đọc được đúng trạng thái
    // thực tế. Không root (hoặc lệnh root thất bại) mới fallback về cách cũ.
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

        if (params != null) {
            for (key in params.keys) {
                val value = params[key] ?: ""
                envp.add(key + "='" + value.replace("'", "'\\''") + "'")
            }
        }

        return envp
    }

    private fun getExecuteScript(context: Context, script: String?, tag: String?): String {
        if (!inited) {
            init(context)
        }

        if (script.isNullOrEmpty()) {
            return ""
        }

        val script2 = script.trim()
        val cachePath: String = if (script2.startsWith(ASSETS_FILE)) {
            extractScript(context, script2) ?: script
        } else {
            createShellCache(context, script)
        }

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
                if (currentPageConfigPath.startsWith("file:///android_asset/")) {
                    val workDir = ExtractAssets(context).getExtractPath(parentPageConfigDir)
                    val workFile = ExtractAssets(context).getExtractPath(currentPageConfigPath)
                    if (!workDir.isNullOrEmpty()) {
                        params["PAGE_WORK_DIR"] = workDir
                    }
                    if (!workFile.isNullOrEmpty()) {
                        params["PAGE_WORK_FILE"] = workFile
                    }
                } else {
                    params["PAGE_WORK_DIR"] = parentPageConfigDir
                    params["PAGE_WORK_FILE"] = currentPageConfigPath
                }
            }
        }

        val envp = getVariables(params)
        val envpCmds = StringBuilder()
        envpCmds.append(buildDynamicExports(context))
        if (envp.isNotEmpty()) {
            for (param in envp) {
                envpCmds.append("export ").append(param).append("\n")
            }
        }
        try {
            dataOutputStream.write(envpCmds.toString().toByteArray(StandardCharsets.UTF_8))

            val executeScript = getExecuteScript(context, cmds, tag)
            if (executeScript.isEmpty()) {
                return
            }
            if (needInput) {
                dataOutputStream.write(("$executeScript; sleep 0.2; exit\n").toByteArray(StandardCharsets.UTF_8))
            } else {
                dataOutputStream.write(("$executeScript\n\nsleep 0.2; exit\nexit\n").toByteArray(StandardCharsets.UTF_8))
            }
            dataOutputStream.flush()
        } catch (ignored: Exception) {
        }
    }
}
