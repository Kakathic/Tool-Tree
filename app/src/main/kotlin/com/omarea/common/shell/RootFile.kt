package com.omarea.common.shell

import com.omarea.common.shared.RootFileInfo
import java.io.File

object RootFile {
    // Dùng "test" qua shell root thay vì java.io.File, vì File API chạy dưới quyền app
    // vẫn bị Android/SELinux chặn ở các đường dẫn ngoài sandbox (vd: /data, /system) dù
    // thiết bị đã root - trong khi lệnh shell chạy trong phiên su thì không bị chặn.
    private fun shellTest(flag: String, path: String): Boolean {
        return KeepShellPublic.doCmdSync("test $flag \"$path\" && echo 1 || echo 0").trim() == "1"
    }

    // Ưu tiên java.io.File (tức thì, không cần root) - chỉ gọi qua shell khi Java báo
    // "không có" (có thể do thật sự không tồn tại, hoặc do bị chặn quyền ngoài sandbox),
    // để không phát sinh tiến trình su cho các đường dẫn bình thường app vẫn đọc được.
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

            //  8 /data/adb/modules/scene_systemless/ => /data/adb/modules/scene_systemless/
            val fileName = row.substring(row.indexOf(size) + size.length + 1)

            if (fileName == "./" || fileName == "../") {
                return null
            }

            // -F  append /dir *exe @sym |FIFO

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

    fun list(path: String): ArrayList<RootFileInfo> {
        val absPath = if (path.endsWith("/")) path.subSequence(0, path.length - 1).toString() else path
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

        return files
    }

    fun fileInfo(path: String): RootFileInfo? {
        val absPath = if (path.endsWith("/")) path.subSequence(0, path.length - 1).toString() else path
        val outputInfo = KeepShellPublic.doCmdSync("busybox ls -1dFs \"$absPath\"")
        if (outputInfo != "error") {
            val rows = outputInfo.split("\n")
            for (row in rows) {
                val file = shellFileInfoRow(row, absPath)
                if (file != null) {
                    file.filePath = absPath.substring(absPath.lastIndexOf("/") + 1)
                    file.parentDir = absPath.take(absPath.lastIndexOf("/"))
                    return file
                }
            }
        }

        return null
    }
}