package com.omarea.common.shared

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

class FilePathResolver {

    /**
     * Trả về đường dẫn tuyệt đối của file từ Uri
     */
    @SuppressLint("NewApi")
    fun getPath(context: Context, uri: Uri): String? {
        // DocumentProvider
        if (DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":").toTypedArray()
                if (split.size >= 2) {
                    val type = split[0]
                    val relativePath = split[1]
                    return if ("primary".equals(type, ignoreCase = true)) {
                        "${Environment.getExternalStorageDirectory()}/$relativePath"
                    } else {
                        // Thẻ nhớ SD ngoài
                        "/storage/$type/$relativePath"
                    }
                }
            } 
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {
                val id = DocumentsContract.getDocumentId(uri)
                // Xử lý URI dạng raw path (ví dụ: raw:/storage/emulated/0/Download/...)
                if (id.startsWith("raw:")) {
                    return id.replaceFirst("raw:", "")
                }
                return if (id.matches(Regex("^[0-9]+$"))) {
                    val contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"),
                        id.toLong()
                    )
                    getDataColumn(context, contentUri, null, null)
                } else {
                    // Copy file vào cache nếu không truy cập trực tiếp được đường dẫn
                    val fileName = getFileName(context, uri)
                    val cacheDir = getDocumentCacheDir(context)
                    val file = generateFileName(fileName, cacheDir)
                    if (file != null) {
                        saveFileFromUri(context, uri, file.absolutePath)
                        file.absolutePath
                    } else null
                }
            } 
            // MediaProvider
            else if (isMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":").toTypedArray()
                if (split.size >= 2) {
                    val type = split[0]
                    val id = split[1]

                    val contentUri: Uri? = when (type) {
                        "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        else -> null
                    }

                    val selection = "_id=?"
                    val selectionArgs = arrayOf(id)

                    return getDataColumn(context, contentUri, selection, selectionArgs)
                }
            }
        } 
        // MediaStore (and general)
        else if ("content".equals(uri.scheme, ignoreCase = true)) {
            if (isGooglePhotosUri(uri)) return uri.lastPathSegment
            return getDataColumn(context, uri, null, null)
        } 
        // File
        else if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }

        return null
    }

    private fun getDataColumn(
        context: Context,
        uri: Uri?,
        selection: String?,
        selectionArgs: Array<String>?
    ): String? {
        if (uri == null) return null
        val column = "_data"
        val projection = arrayOf(column)

        try {
            // Dùng .use để tự động đóng Cursor an toàn
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(column)
                    if (columnIndex != -1) {
                        return cursor.getString(columnIndex)
                    }
                }
            }
        } catch (ignored: Exception) {
        }
        return null
    }

    private fun isExternalStorageDocument(uri: Uri) = "com.android.externalstorage.documents" == uri.authority
    private fun isDownloadsDocument(uri: Uri) = "com.android.providers.downloads.documents" == uri.authority
    private fun isMediaDocument(uri: Uri) = "com.android.providers.media.documents" == uri.authority
    private fun isGooglePhotosUri(uri: Uri) = "com.google.android.apps.photos.content" == uri.authority

    fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            result = cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (ignored: Exception) {}
        }
        
        // Bỏ việc gọi getPath() ở đây để tránh lặp vô tận
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "temp_file"
    }

    fun getName(filename: String?): String? {
        if (filename == null) return null
        val index = filename.lastIndexOf('/')
        return if (index != -1) filename.substring(index + 1) else filename
    }

    companion object {
        @JvmStatic
        fun getDocumentCacheDir(context: Context): File {
            val dir = File(context.cacheDir, "documents")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

        @JvmStatic
        fun generateFileName(name: String?, directory: File): File? {
            if (name == null) return null

            var resultName = name
            var file = File(directory, resultName)

            if (file.exists()) {
                var fileName = resultName
                var extension = ""
                val dotIndex = resultName.lastIndexOf('.')
                if (dotIndex > 0) {
                    fileName = resultName.substring(0, dotIndex)
                    extension = resultName.substring(dotIndex)
                }

                var index = 0
                while (file.exists()) {
                    index++
                    resultName = "$fileName($index)$extension"
                    file = File(directory, resultName)
                }
            }

            return try {
                if (file.createNewFile()) file else null
            } catch (e: Exception) {
                null
            }
        }

        private fun saveFileFromUri(context: Context, uri: Uri, destinationPath: String) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destinationPath).use { output ->
                        // Dùng copyTo chuẩn của Kotlin: tự tạo buffer 8KB và ghi chính xác số byte đọc được
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
