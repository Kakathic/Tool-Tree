package com.tool.tree

import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.system.ErrnoException
import android.system.Os
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

class TreeDocumentsProvider : DocumentsProvider() {

    private lateinit var packageName: String
    private var dataDir: File? = null
    private var userDeDataDir: File? = null
    private var androidDataDir: File? = null
    private var androidObbDir: File? = null

    override fun attachInfo(context: Context, info: ProviderInfo) {
        super.attachInfo(context, info)
        packageName = context.packageName
        val dataDirLocal = context.filesDir.parentFile
        dataDir = dataDirLocal
        val dataDirPath = dataDirLocal?.path
        if (dataDirPath != null && dataDirPath.startsWith("/data/user/")) {
            userDeDataDir = File("/data/user_de/" + dataDirPath.substring(11))
        }
        val externalFilesDir = context.getExternalFilesDir(null)
        if (externalFilesDir != null) {
            androidDataDir = externalFilesDir.parentFile
        }
        androidObbDir = context.obbDir
    }

    @Throws(FileNotFoundException::class)
    private fun getFileForDocId(docId: String): File? {
        return getFileForDocId(docId, true)
    }

    @Throws(FileNotFoundException::class)
    private fun getFileForDocId(docId: String, checkExists: Boolean): File? {
        var filename = docId
        filename = if (filename.startsWith(packageName)) {
            filename.substring(packageName.length)
        } else {
            throw FileNotFoundException("$docId not found")
        }
        if (filename.startsWith("/")) filename = filename.substring(1)
        if (filename.isEmpty()) return null

        val type: String
        val subPath: String
        val i = filename.indexOf('/')
        if (i == -1) {
            type = filename
            subPath = ""
        } else {
            type = filename.substring(0, i)
            subPath = filename.substring(i + 1)
        }

        var f: File? = null
        if (type.equals("data", ignoreCase = true)) f = File(dataDir, subPath)
        else if (type.equals("android_data", ignoreCase = true) && androidDataDir != null) f = File(androidDataDir, subPath)
        else if (type.equals("android_obb", ignoreCase = true) && androidObbDir != null) f = File(androidObbDir, subPath)
        else if (type.equals("user_de_data", ignoreCase = true) && userDeDataDir != null) f = File(userDeDataDir, subPath)

        if (f == null) throw FileNotFoundException("$docId not found")

        if (checkExists) {
            try {
                Os.lstat(f.path)
            } catch (e: Exception) {
                throw FileNotFoundException("$docId not found")
            }
        }
        return f
    }

    override fun queryRoots(projection: Array<String>?): Cursor {
        val result = MatrixCursor(DEFAULT_ROOT_PROJECTION)
        val row = result.newRow()
        row.add(Root.COLUMN_ROOT_ID, packageName)
        row.add(Root.COLUMN_DOCUMENT_ID, packageName)
        row.add(
            Root.COLUMN_FLAGS,
            Root.FLAG_SUPPORTS_CREATE or
                Root.FLAG_SUPPORTS_IS_CHILD or
                Root.FLAG_SUPPORTS_RECENTS or
                Root.FLAG_LOCAL_ONLY
        )
        row.add(Root.COLUMN_TITLE, context!!.getString(R.string.app_name))
        row.add(Root.COLUMN_MIME_TYPES, "*/*")
        row.add(Root.COLUMN_AVAILABLE_BYTES, dataDir?.freeSpace ?: 0)
        row.add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
        return result
    }

    @Throws(FileNotFoundException::class)
    override fun queryDocument(documentId: String, projection: Array<String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        includeFile(result, documentId, null)
        return result
    }

    @Throws(FileNotFoundException::class)
    override fun queryChildDocuments(parentDocumentIdArg: String, projection: Array<String>?, sortOrder: String?): Cursor {
        var parentDocumentId = parentDocumentIdArg
        if (parentDocumentId.endsWith("/")) parentDocumentId = parentDocumentId.substring(0, parentDocumentId.length - 1)
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val parent = getFileForDocId(parentDocumentId)

        if (parent == null) {
            includeFile(result, "$parentDocumentId/data", dataDir)
            val androidData = androidDataDir
            if (androidData != null && androidData.exists()) includeFile(result, "$parentDocumentId/android_data", androidData)
            val androidObb = androidObbDir
            if (androidObb != null && androidObb.exists()) includeFile(result, "$parentDocumentId/android_obb", androidObb)
            val userDeData = userDeDataDir
            if (userDeData != null && userDeData.exists()) includeFile(result, "$parentDocumentId/user_de_data", userDeData)
        } else {
            val files = parent.listFiles()
            if (files != null) {
                for (file in files) includeFile(result, "$parentDocumentId/${file.name}", file)
            }
        }
        return result
    }

    @Throws(FileNotFoundException::class)
    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        val file = getFileForDocId(documentId, false) ?: throw FileNotFoundException("$documentId not found")
        return ParcelFileDescriptor.open(file, parseFileMode(mode))
    }

    override fun onCreate(): Boolean = true

    @Throws(FileNotFoundException::class)
    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val parent = getFileForDocId(parentDocumentId)
        if (parent != null) {
            var newFile = File(parent, displayName)
            var noConflictId = 2
            while (newFile.exists()) newFile = File(parent, "$displayName (${noConflictId++})")
            try {
                val succeeded = if (Document.MIME_TYPE_DIR == mimeType) newFile.mkdir() else newFile.createNewFile()
                if (succeeded) return if (parentDocumentId.endsWith("/")) parentDocumentId + newFile.name else "$parentDocumentId/${newFile.name}"
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        throw FileNotFoundException("Failed to create document in $parentDocumentId with name $displayName")
    }

    @Throws(FileNotFoundException::class)
    override fun deleteDocument(documentId: String) {
        val file = getFileForDocId(documentId)
        if (file == null || !deleteFile(file)) throw FileNotFoundException("Failed to delete document $documentId")
    }

    @Throws(FileNotFoundException::class)
    override fun removeDocument(documentId: String, parentDocumentId: String) {
        deleteDocument(documentId)
    }

    @Throws(FileNotFoundException::class)
    override fun renameDocument(documentId: String, displayName: String): String {
        val file = getFileForDocId(documentId)
        if (file != null) {
            val target = File(file.parentFile, displayName)
            if (file.renameTo(target)) {
                val i = documentId.lastIndexOf('/')
                return if (i > 0) {
                    documentId.substring(0, i) + "/" + displayName
                } else {
                    "$packageName/$displayName"
                }
            }
        }
        throw FileNotFoundException("Failed to rename document $documentId to $displayName")
    }

    @Throws(FileNotFoundException::class)
    override fun moveDocument(sourceDocumentId: String, sourceParentDocumentId: String, targetParentDocumentId: String): String {
        val sourceFile = getFileForDocId(sourceDocumentId)
        val targetDir = getFileForDocId(targetParentDocumentId)
        if (sourceFile != null && targetDir != null) {
            val targetFile = File(targetDir, sourceFile.name)
            if (!targetFile.exists() && sourceFile.renameTo(targetFile))
                return if (targetParentDocumentId.endsWith("/")) targetParentDocumentId + targetFile.name else "$targetParentDocumentId/${targetFile.name}"
        }
        throw FileNotFoundException("Failed to move document $sourceDocumentId to $targetParentDocumentId")
    }

    @Throws(FileNotFoundException::class)
    override fun getDocumentType(documentId: String): String {
        val file = getFileForDocId(documentId)
        return if (file == null) Document.MIME_TYPE_DIR else getMimeType(file)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        return documentId.startsWith("$parentDocumentId/") || documentId == parentDocumentId
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val result = super.call(method, arg, extras)
        if (result != null) return result
        if (!method.startsWith("mt:")) return null

        val out = Bundle()
        try {
            if (extras == null) return null
            val uri: Uri = extras.getParcelable("uri") ?: return null
            val pathSegments = uri.pathSegments
            val documentId = if (pathSegments.size >= 4) pathSegments[3] else pathSegments[1]
            when (method) {
                METHOD_SET_LAST_MODIFIED -> {
                    val file = getFileForDocId(documentId)
                    out.putBoolean("result", file != null && file.setLastModified(extras.getLong("time")))
                }
                METHOD_SET_PERMISSIONS -> {
                    val file = getFileForDocId(documentId)
                    if (file != null) {
                        try {
                            Os.chmod(file.path, extras.getInt("permissions"))
                            out.putBoolean("result", true)
                        } catch (e: ErrnoException) {
                            out.putBoolean("result", false)
                            out.putString("message", e.message)
                        }
                    } else out.putBoolean("result", false)
                }
                METHOD_CREATE_SYMLINK -> {
                    val file = getFileForDocId(documentId, false)
                    if (file != null) {
                        try {
                            Os.symlink(extras.getString("path"), file.path)
                            out.putBoolean("result", true)
                        } catch (e: ErrnoException) {
                            out.putBoolean("result", false)
                            out.putString("message", e.message)
                        }
                    } else out.putBoolean("result", false)
                }
                else -> {
                    out.putBoolean("result", false)
                    out.putString("message", "Unsupported method: $method")
                }
            }
        } catch (e: Exception) {
            out.putBoolean("result", false)
            out.putString("message", e.toString())
        }
        return out
    }

    @Throws(FileNotFoundException::class)
    private fun includeFile(result: MatrixCursor, docId: String, fileArg: File?) {
        val file = fileArg ?: getFileForDocId(docId)
        if (file == null) {
            val row = result.newRow()
            row.add(Document.COLUMN_DOCUMENT_ID, packageName)
            row.add(Document.COLUMN_DISPLAY_NAME, packageName)
            row.add(Document.COLUMN_SIZE, 0L)
            row.add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
            row.add(Document.COLUMN_LAST_MODIFIED, 0)
            row.add(Document.COLUMN_FLAGS, 0)
            row.add(COLUMN_MT_PATH, "")
            row.add(COLUMN_MT_EXTRAS, "")
            return
        }

        var flags = 0
        if (file.isDirectory && file.canWrite()) flags = flags or Document.FLAG_DIR_SUPPORTS_CREATE
        if (!file.isDirectory && file.canWrite()) flags = flags or Document.FLAG_SUPPORTS_WRITE
        val parentFile = file.parentFile
        if (parentFile != null && parentFile.canWrite()) {
            flags = flags or Document.FLAG_SUPPORTS_DELETE
            flags = flags or Document.FLAG_SUPPORTS_RENAME
            flags = flags or Document.FLAG_SUPPORTS_MOVE
        }

        val path = file.path
        val displayName: String
        var addExtras = false
        if (path == dataDir?.path) displayName = "data"
        else if (androidDataDir != null && path == androidDataDir?.path) displayName = "android_data"
        else if (androidObbDir != null && path == androidObbDir?.path) displayName = "android_obb"
        else if (userDeDataDir != null && path == userDeDataDir?.path) displayName = "user_de_data"
        else {
            displayName = file.name
            addExtras = true
        }

        val row = result.newRow()
        row.add(Document.COLUMN_DOCUMENT_ID, docId)
        row.add(Document.COLUMN_DISPLAY_NAME, displayName)
        row.add(Document.COLUMN_SIZE, file.length())
        row.add(Document.COLUMN_MIME_TYPE, getMimeType(file))
        row.add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
        row.add(Document.COLUMN_FLAGS, flags)
        row.add(COLUMN_MT_PATH, file.absolutePath)

        if (addExtras) {
            try {
                val stat = Os.lstat(path)
                val sb = StringBuilder()
                sb.append(stat.st_mode).append("|").append(stat.st_uid).append("|").append(stat.st_gid)
                if ((stat.st_mode and 0b1111000000000000) == 0b1010000000000000) sb.append("|").append(Os.readlink(path))
                row.add(COLUMN_MT_EXTRAS, sb.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        const val COLUMN_MT_EXTRAS = "mt_extras"
        const val COLUMN_MT_PATH = "mt_path"
        const val METHOD_SET_LAST_MODIFIED = "mt:setLastModified"
        const val METHOD_SET_PERMISSIONS = "mt:setPermissions"
        const val METHOD_CREATE_SYMLINK = "mt:createSymlink"

        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_MIME_TYPES,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_TITLE,
            Root.COLUMN_AVAILABLE_BYTES,
            Root.COLUMN_DOCUMENT_ID
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
            COLUMN_MT_EXTRAS,
            COLUMN_MT_PATH
        )

        private fun parseFileMode(mode: String): Int {
            return when (mode) {
                "r" -> ParcelFileDescriptor.MODE_READ_ONLY
                "w", "wt" -> ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
                "wa" -> ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_APPEND
                "rw" -> ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE
                "rwt" -> ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
                else -> throw IllegalArgumentException("Invalid mode: $mode")
            }
        }

        private fun deleteFile(file: File): Boolean {
            if (file.isDirectory && !isSymbolicLink(file)) {
                val children = file.listFiles()
                if (children != null) for (child in children) if (!deleteFile(child)) return false
            }
            return file.delete()
        }

        private fun isSymbolicLink(file: File): Boolean {
            return try {
                val stat = Os.lstat(file.path)
                (stat.st_mode and 0b1111000000000000) == 0b1010000000000000
            } catch (e: ErrnoException) {
                e.printStackTrace()
                false
            }
        }

        private fun getMimeType(file: File): String {
            if (file.isDirectory) return Document.MIME_TYPE_DIR
            val name = file.name
            val lastDot = name.lastIndexOf('.')
            if (lastDot >= 0) {
                val extension = name.substring(lastDot + 1).lowercase()
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                if (mime != null) return mime
            }
            return "application/octet-stream"
        }
    }
}
