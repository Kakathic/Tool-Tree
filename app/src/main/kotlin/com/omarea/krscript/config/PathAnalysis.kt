package com.omarea.krscript.config

import android.content.Context
import com.omarea.common.shared.FileWrite
import com.omarea.common.shell.KeepShellPublic
import com.omarea.common.shell.RootFile
import com.omarea.krscript.FileOwner
import java.io.File
import java.io.InputStream
import java.net.URI
import java.util.Locale

class PathAnalysis(private var context: Context, private var parentDir: String = "") {
    companion object {
        private const val ASSETS_FILE = "file:///android_asset/"
    }

    private var currentAbsPath: String = ""

    fun getCurrentAbsPath(): String = currentAbsPath

    // Thay "{HOME}" bằng thư mục home thật của app (đúng kiểu lấy dùng chung trong project:
    // ThemeModeState/LanguageManager... File(context.filesDir, "home")), áp dụng cho MỌI path
    // đi qua parsePath (icon, html, load-key, text editor...), không chỉ riêng load-key.
    private fun resolveHomePlaceholder(filePath: String): String {
        if (!filePath.contains("{HOME}")) return filePath
        val homeDir = File(context.filesDir, "home").absolutePath
        return filePath.replace("{HOME}", homeDir)
    }

    // Thay "{ICON}" bằng thư mục icon thật của app (home/etc/icon), cùng quy ước với {HOME}.
    private fun resolveIconPlaceholder(filePath: String): String {
        if (!filePath.contains("{ICON}")) return filePath
        val iconDir = File(context.filesDir, "home/etc/icon").absolutePath
        return filePath.replace("{ICON}", iconDir)
    }

    // Flag file "Ticon" trong home/usr/log/ (cùng kiểu dissblur/directbg/language ở
    // ThemeModeState/LanguageManager), nội dung "1" thì tắt riêng các path dùng {ICON}
    // (path tĩnh khác không dùng {ICON} vẫn hiện icon bình thường).
    private fun isIconDisabled(): Boolean {
        val file = File(context.filesDir, "home/usr/log/Ticon")
        return try {
            if (file.exists()) file.readText().trim() == "1" else false
        } catch (_: Exception) {
            false
        }
    }

    // {LANG} - thử lần lượt ngôn ngữ-khu vực (vd "zh-CN") -> ngôn ngữ (vd "zh") -> "default",
    // dừng ngay khi tìm thấy file. Chuyển từ PageConfigReader.applyLoadKey() vào đây để dùng
    // được ở MỌI nơi gọi parsePath() (icon, html, load-key, text editor...), không riêng load-key.
    private fun currentLangCandidates(): List<String> {
        val locale = Locale.getDefault()
        val lang = locale.language
        val country = locale.country
        val list = mutableListOf<String>()
        if (lang.isNotEmpty() && country.isNotEmpty()) list.add("$lang-$country")
        if (lang.isNotEmpty()) list.add(lang)
        list.add("default")
        return list.distinct()
    }

    fun parsePath(filePath: String): InputStream? {
        // Ticon=1 chỉ tắt riêng các path dùng {ICON} - kiểm tra trên placeholder gốc, trước khi
        // thử {LANG}/{HOME}, để không mở nhầm file thật khi icon đã bị tắt.
        if (filePath.contains("{ICON}") && isIconDisabled()) return null

        if (filePath.contains("{LANG}")) {
            for (code in currentLangCandidates()) {
                val candidate = filePath.replace("{LANG}", code)
                openResolvedPath(candidate)?.let { return it }
            }
            return null
        }
        return openResolvedPath(filePath)
    }

    private fun openResolvedPath(filePath: String): InputStream? {
        val resolvedPath = resolveIconPlaceholder(resolveHomePlaceholder(filePath))
        return try {
            if (resolvedPath.startsWith(ASSETS_FILE)) {
                currentAbsPath = resolvedPath
                context.assets.open(resolvedPath.substring(ASSETS_FILE.length))
            } else {
                getFileByPath(resolvedPath)
            }
        } catch (ex: Exception) {
            null
        }
    }

    // Thời gian sửa đổi cuối của file vừa parsePath() thành công, dùng để phát hiện file bị
    // THAY NỘI DUNG dù giữ nguyên tên/đường dẫn (vd icon đổi ảnh mới cùng path cũ). Trả về 0
    // khi không xác định được (asset - không có mtime filesystem thật; hoặc file phải mở qua
    // root - currentAbsPath không trỏ tới file gốc nên đọc mtime trực tiếp sẽ luôn ra 0/lỗi
    // do không có quyền, không cố tình chạy thêm lệnh root chỉ để lấy mtime). 0 nghĩa là bên
    // gọi nên coi cache là còn hợp lệ mãi (giữ đúng hành vi cache cũ cho 2 trường hợp này).
    fun getCurrentLastModified(): Long {
        if (currentAbsPath.isEmpty() || currentAbsPath.startsWith(ASSETS_FILE)) return 0L
        return try {
            File(currentAbsPath).lastModified()
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Tối ưu hóa việc nối đường dẫn bằng cách sử dụng java.net.URI 
     * để tự động xử lý các ký hiệu ../ và ./ một cách chuẩn xác.
     */
    private fun pathConcat(parent: String, target: String): String {
        return try {
            val isAssets = parent.startsWith(ASSETS_FILE)
            val base = if (isAssets) parent else "file://$parent"
            
            // Sử dụng URI để normalize đường dẫn (xử lý ../ và ./)
            val uri = URI(base).resolve(target).normalize()
            
            val result = uri.toString()
            if (isAssets) result else result.removePrefix("file:")
        } catch (e: Exception) {
            // Fallback nếu URI fail
            if (parent.endsWith("/")) parent + target else "$parent/$target"
        }
    }

    /**
     * Cải tiến việc mở file bằng Root: sử dụng tên file động để tránh xung đột (Collision)
     */
    private fun useRootOpenFile(filePath: String): InputStream? {
        if (RootFile.fileExists(filePath)) {
            val cacheDir = File(FileWrite.getPrivateFilePath(context, "icons"))
            if (!cacheDir.exists()) cacheDir.mkdirs()

            // Tạo tên file cache dựa trên hash đường dẫn để tránh ghi đè khi mở nhiều file cùng lúc
            val fileName = "cache_${filePath.hashCode()}"
            val cachePath = File(cacheDir, fileName).absolutePath
            val fileOwner = FileOwner(context).getFileOwner()

            val command = """
                cp -f "$filePath" "$cachePath"
                chmod 777 "$cachePath"
                chown $fileOwner:$fileOwner "$cachePath"
            """.trimIndent()

            KeepShellPublic.doCmdSync(command)
            
            File(cachePath).let {
                if (it.exists() && it.canRead()) {
                    return it.inputStream()
                }
            }
        }
        return null
    }

    private fun findAssetsResource(filePath: String): InputStream? {
        val relativePath = pathConcat(parentDir, filePath)
        return try {
            val simplePath = relativePath.substring(ASSETS_FILE.length)
            context.assets.open(simplePath).also { currentAbsPath = relativePath }
        } catch (ex: Exception) {
            try {
                context.assets.open(filePath).also { currentAbsPath = ASSETS_FILE + filePath }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun findDiskResource(filePath: String): InputStream? {
        // 1. Tìm tương đối so với parentDir
        if (parentDir.isNotEmpty()) {
            val relativePath = pathConcat(parentDir, filePath)
            val file = File(relativePath)
            if (file.exists() && file.canRead()) {
                currentAbsPath = file.absolutePath
                return file.inputStream()
            }
            useRootOpenFile(relativePath)?.let { return it }
        }

        // 2. Tìm trong thư mục riêng của ứng dụng (Private Data)
        val privateDir = FileWrite.getPrivateFileDir(context)
        val privatePath = pathConcat(privateDir, filePath)
        val pFile = File(privatePath)
        if (pFile.exists() && pFile.canRead()) {
            currentAbsPath = pFile.absolutePath
            return pFile.inputStream()
        }
        
        return useRootOpenFile(privatePath)
    }

    private fun getFileByPath(filePath: String): InputStream? {
        return try {
            if (filePath.startsWith("/")) {
                currentAbsPath = filePath
                val file = File(filePath)
                if (file.exists() && file.canRead()) file.inputStream() else useRootOpenFile(filePath)
            } else {
                if (parentDir.startsWith(ASSETS_FILE)) {
                    findAssetsResource(filePath)
                } else {
                    findDiskResource(filePath)
                }
            }
        } catch (ex: Exception) {
            null
        }
    }
}
