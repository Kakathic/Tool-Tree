package com.omarea.common.shell

import com.omarea.common.shared.RootFileInfo
import java.io.File

object RootFile {
    private fun shellTest(flag: String, path: String): Boolean {
        return KeepShellPublic.doCmdSync("test $flag \"$path\" && echo 1 || echo 0").trim() == "1"
    }

    fun itemExists(path: String): Boolean {
        return File(path).exists() || shellTest("-e", path)
    }

    fun fileExists(path: String): Boolean {
        return File(path).isFile || shellTest("-f", path)
    }

    fun dirExists(path: String): Boolean {
        return File(path).isDirectory || shellTest("-d", path)
    }

    fun deleteDirOrFile(path: String) {
        if (dirExists(path)) File(path).deleteRecursively() else File(path).delete()
    }

    private fun shellFileInfoRow(row: String, parent: String): RootFileInfo? {
        if (row.startsWith("total ")) {
            return null
        }

        try {
            val file = RootFileInfo()

            val columns = row.trim().split(" ")
            val size = columns[0]
            file.fileSize = size.toLong() * 1024

            val fileName = row.substring(row.indexOf(size) + size.length + 1)

            if (fileName == "./" || fileName == "../") {
                return null
            }

            if (fileName.endsWith("/")) {
                file.filePath = fileName.dropLast(1)
                file.isDirectory = true
            } else if (fileName.endsWith("@") || fileName.endsWith("|") || fileName.endsWith("*")) {
                file.filePath = fileName.dropLast(1)
            } else {
                file.filePath = fileName
            }

            file.parentDir = parent

            return file
        } catch (ex: Exception) {
            return null
        }
    }

    private fun normalizePath(path: String): String {
        return if (path.length > 1 && path.endsWith("/")) path.dropLast(1) else path
    }

    fun list(path: String): ArrayList<RootFileInfo> {
        val absPath = normalizePath(path)
        val files = ArrayList<RootFileInfo>()
        if (dirExists(absPath)) {
            val outputInfo = KeepShellPublic.doCmdSync("busybox ls -1Fs \"$absPath\"")
            if (outputInfo != "error") {
                val rows = outputInfo.split("\n")
                for (row in rows) {
                    val file = shellFileInfoRow(row, absPath)
                    if (file != null) {
                        files.add(file)
                    }
                }
            }
        }

        if (absPath == "/storage/emulated") {
            val known = files.map { it.fileName }.toHashSet()
            for (userId in 0..9) {
                val name = userId.toString()
                if (name !in known && dirExists("$absPath/$name")) {
                    val info = RootFileInfo()
                    info.filePath = name
                    info.parentDir = absPath
                    info.isDirectory = true
                    files.add(info)
                }
            }
        }

        return files
    }

    fun fileInfo(path: String): RootFileInfo? {
        val absPath = normalizePath(path)
        val outputInfo = KeepShellPublic.doCmdSync("busybox ls -1dFs \"$absPath\"")
        if (outputInfo != "error") {
            val rows = outputInfo.split("\n")
            for (row in rows) {
                val file = shellFileInfoRow(row, absPath)
                if (file != null) {
                    if (absPath == "/") {
                        file.filePath = "/"
                        file.parentDir = ""
                    } else {
                        file.filePath = absPath.substring(absPath.lastIndexOf("/") + 1)
                        file.parentDir = absPath.take(absPath.lastIndexOf("/"))
                    }
                    return file
                }
            }
        }

        return null
    }
}