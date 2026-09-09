package com.omarea.common.shared

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class FileSha256 {
    fun getFileSha256(file: File): String? {
        if (!file.isFile) {
            return null
        }
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val `in` = FileInputStream(file)
            val buffer = ByteArray(64 * 1024)
            var len: Int
            while (`in`.read(buffer).also { len = it } != -1) {
                digest.update(buffer, 0, len)
            }
            `in`.close()
            bytesToHexString(digest.digest())
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun bytesToHexString(src: ByteArray?): String? {
        if (src == null || src.isEmpty()) {
            return null
        }
        val stringBuilder = StringBuilder()
        for (b in src) {
            val v = b.toInt() and 0xFF
            val hv = Integer.toHexString(v)
            if (hv.length < 2) {
                stringBuilder.append(0)
            }
            stringBuilder.append(hv)
        }
        return stringBuilder.toString()
    }
}
