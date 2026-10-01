package com.omarea.krscript.config

import android.content.Context
import com.omarea.common.shared.FileWrite
import com.omarea.common.shell.KeepShellPublic
import com.omarea.common.shell.RootFile
import com.omarea.krscript.FileOwner
import java.io.File
import java.io.InputStream
import java.net.URI

class PathAnalysis(private var context: Context, private var parentDir: String = "") {
    companion object {
        private const val ASSETS_FILE = "file:///android_asset/"
    }

    private var currentAbsPath: String = ""

    fun getCurrentAbsPath(): String = currentAbsPath

    private fun resolveHomePlaceholder(filePath: String): String {
        if (!filePath.contains("{HOME}")) return filePath
        val homeDir = File(context.filesDir, "home").absolutePath
        return filePath.replace("{HOME}", homeDir)
    }

    private fun resolveIconPlaceholder(filePath: String): String {
        if (!filePath.contains("{ICON}")) return filePath
        val iconDir = File(context.filesDir, "home/etc/icon").absolutePath
        return filePath.replace("{ICON}", iconDir)
    }

    private fun isIconDisabled(): Boolean {
        val file = File(context.filesDir, "home/usr/log/Ticon")
        return try {
            if (file.exists()) file.readText().trim() == "1" else false
        } catch (_: Exception) {
            false
        }
    }

    fun parsePath(filePath: String): InputStream? {
        if (filePath.contains("{ICON}") && isIconDisabled()) return null

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

    fun getCurrentLastModified(): Long {
        if (currentAbsPath.isEmpty() || currentAbsPath.startsWith(ASSETS_FILE)) return 0L
        return try {
            File(currentAbsPath).lastModified()
        } catch (_: Exception) {
            0L
        }
    }

    private fun pathConcat(parent: String, target: String): String {
        return try {
            val isAssets = parent.startsWith(ASSETS_FILE)
            val dir = if (parent.endsWith("/")) parent else "$parent/"
            val base = if (isAssets) dir else "file://$dir"
            val uri = URI(base).resolve(target).normalize()
            
            val result = uri.toString()
            if (isAssets) result else result.removePrefix("file:")
        } catch (e: Exception) {
            if (parent.endsWith("/")) parent + target else "$parent/$target"
        }
    }

    private fun useRootOpenFile(filePath: String): InputStream? {
        if (RootFile.fileExists(filePath)) {
            val cacheDir = File(FileWrite.getPrivateFilePath(context, "icons"))
            if (!cacheDir.exists()) cacheDir.mkdirs()

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
        if (parentDir.isNotEmpty()) {
            val relativePath = pathConcat(parentDir, filePath)
            val file = File(relativePath)
            if (file.exists() && file.canRead()) {
                currentAbsPath = file.absolutePath
                return file.inputStream()
            }
            useRootOpenFile(relativePath)?.let { return it }
        }

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
