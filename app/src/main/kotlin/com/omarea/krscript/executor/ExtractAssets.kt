package com.omarea.krscript.executor

import android.content.Context
import com.omarea.common.shared.FileWrite

class ExtractAssets(private val context: Context) {

    private fun extractScript(fileNameArg: String?): String? {
        var fileName = fileNameArg
        if (fileName.isNullOrEmpty()) {
            return null
        }

        if (extractHisotry.containsKey(fileName)) {
            return extractHisotry[fileName]
        }

        if (fileName.startsWith("file:///android_asset/")) {
            fileName = fileName.substring("file:///android_asset/".length)
        }

        val filePath = FileWrite.writePrivateShellFile(fileName, fileName, context)

        if (filePath != null) {
            extractHisotry[fileName] = filePath
        }

        return filePath
    }

    fun extractResource(fileNameArg: String?): String? {
        var fileName = fileNameArg
        if (fileName.isNullOrEmpty()) {
            return null
        }

        if (extractHisotry.containsKey(fileName)) {
            return extractHisotry[fileName]
        }

        if (fileName.endsWith(".sh")) {
            return extractScript(fileName)
        }
        if (fileName.startsWith("file:///android_asset/")) {
            fileName = fileName.substring("file:///android_asset/".length)
        }
        val filePath = FileWrite.writePrivateFile(context.assets, fileName, fileName, context)

        if (filePath != null) {
            extractHisotry[fileName] = filePath
        }

        return filePath
    }

    fun extractResources(dirArg: String?): String? {
        var dir = dirArg
        if (dir.isNullOrEmpty()) {
            return null
        }

        if (extractHisotry.containsKey(dir)) {
            return extractHisotry[dir]
        }

        dir = if (dir.startsWith("file:///android_asset/")) {
            dir.substring("file:///android_asset/".length)
        } else if (dir.endsWith("/")) {
            dir.substring(0, dir.length - 1)
        } else {
            dir
        }

        try {
            val files = context.assets.list(dir)
            if (files != null && files.isNotEmpty()) {
                for (file in files) {
                    val relativePath = "$dir/$file"
                    extractResources(relativePath)
                }
                val outputDir = getExtractPath(dir)
                extractHisotry[dir] = outputDir
                return outputDir
            } else {
                return extractResource(dir)
            }
        } catch (ignored: Exception) {
        }

        return ""
    }

    fun getExtractPath(file: String): String {
        return FileWrite.getPrivateFilePath(
            context,
            if (file.startsWith("file:///android_asset/")) file.substring("file:///android_asset/".length) else file
        )
    }

    companion object {
        private val extractHisotry = HashMap<String, String>()
    }
}
