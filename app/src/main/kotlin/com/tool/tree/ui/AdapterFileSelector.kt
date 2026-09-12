package com.tool.tree.ui

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import com.omarea.common.shared.RootFileInfo
import com.omarea.common.shell.RootFile
import com.omarea.common.ui.DialogHelper
import com.omarea.common.ui.ProgressBarDialog
import com.tool.tree.R
import java.io.File
import java.io.FileFilter
import java.util.ArrayList
import java.util.LinkedHashSet
import java.util.Locale

class AdapterFileSelector private constructor(
    rootDir: File,
    private var fileSelected: Runnable,
    private var progressBarDialog: ProgressBarDialog,
    extension: String?
) : BaseAdapter() {

    private var fileArray: Array<File>? = null
    private var currentDir: File? = null
    private var selectedFile: File? = null
    private val handler = Handler(Looper.getMainLooper())

    // Danh sách đuôi file được phép (đã có dấu chấm ở đầu, chữ thường), null/rỗng = không giới hạn
    private var extensions: Array<String>? = null
    private var hasParent = false // 是否还有父级
    var folderChooserMode = false // 是否是目录选择模式（目录选择模式下不显示文件，长按目录选中）
        private set

    // Chế độ chọn nhiều mục (nhiều file, hoặc nhiều thư mục)
    private var multipleMode = false

    // Giữ thứ tự đã chọn
    private val selectedFiles = LinkedHashSet<File>()

    // Thông tin (isDirectory/size) lấy được qua root cho các mục mà java.io.File không tự
    // stat được (thư mục ngoài sdcard) - khoá theo absolutePath, ghi đè lên File API thường.
    private val rootInfoMap = HashMap<String, RootFileInfo>()

    private fun isDir(file: File): Boolean {
        return rootInfoMap[file.absolutePath]?.isDirectory ?: file.isDirectory
    }

    private fun sizeOf(file: File): Long {
        return rootInfoMap[file.absolutePath]?.length() ?: file.length()
    }

    // file.exists() luôn trả về false với đường dẫn ngoài sdcard mà java.io không stat
    // được, dù mục đó vừa được liệt kê qua root - nên coi các mục có trong rootInfoMap
    // là đang tồn tại, không dò lại bằng File API thường.
    private fun existsSafe(file: File): Boolean {
        return rootInfoMap.containsKey(file.absolutePath) || file.exists()
    }

    // Được gọi mỗi khi danh sách đã chọn thay đổi (để activity cập nhật nút "Xong"/số lượng đã chọn)
    private var selectionChangedListener: SelectionChangedListener? = null

    interface SelectionChangedListener {
        fun onSelectionChanged(selectedCount: Int)
    }

    // Báo riêng khi 1 thư mục không đọc được (thiếu quyền) - khác với thư mục đọc được
    // nhưng thực sự rỗng (no_files_in_directory), để người dùng biết vì sao danh sách trống
    private var accessDeniedListener: AccessDeniedListener? = null

    interface AccessDeniedListener {
        fun onAccessDenied(dir: File)
    }

    fun setAccessDeniedListener(listener: AccessDeniedListener?) {
        this.accessDeniedListener = listener
    }

    // Báo mỗi khi thư mục hiện tại thay đổi (mở thư mục con / quay lại thư mục cha) -
    // dùng để activity cập nhật đường dẫn hiện tại lên tiêu đề toolbar.
    private var dirChangedListener: OnDirChangedListener? = null

    interface OnDirChangedListener {
        fun onDirChanged(dir: File)
    }

    fun setDirChangedListener(listener: OnDirChangedListener?) {
        this.dirChangedListener = listener
    }

    init {
        init(rootDir, fileSelected, progressBarDialog, extension)
    }

    private fun init(rootDir: File, fileSelected: Runnable, progressBarDialog: ProgressBarDialog, extension: String?) {
        this.fileSelected = fileSelected
        this.progressBarDialog = progressBarDialog
        // Hỗ trợ nhiều đuôi file, phân cách bằng dấu phẩy, ví dụ: "zip,apk,7z"
        if (!extension.isNullOrEmpty() && extension.trim().isNotEmpty()) {
            val parts = extension.split(",")
            val list = ArrayList<String>()
            for (part in parts) {
                var trimmed = part.trim().lowercase(Locale.getDefault())
                if (trimmed.isEmpty()) {
                    continue
                }
                if (!trimmed.startsWith(".")) {
                    trimmed = ".$trimmed"
                }
                list.add(trimmed)
            }
            this.extensions = list.toTypedArray()
        } else {
            this.extensions = null
        }
        loadDir(rootDir)
    }

    private fun matchesExtension(file: File): Boolean {
        val exts = extensions
        if (exts == null || exts.isEmpty()) {
            return true
        }
        val name = file.name.lowercase(Locale.getDefault())
        for (ext in exts) {
            if (name.endsWith(ext)) {
                return true
            }
        }
        return false
    }

    // Sắp xếp: thư mục trước, rồi theo tên (không phân biệt hoa/thường). isDirOf cho phép
    // dùng thông tin isDirectory lấy qua root thay vì file.isDirectory (không sửa được field
    // dùng chung nếu gọi từ background thread trước khi notifyDataSetChanged).
    private fun sortFiles(files: Array<File>, isDirOf: (File) -> Boolean) {
        for (i in files.indices) {
            for (j in i + 1 until files.size) {
                val iIsDir = isDirOf(files[i])
                val jIsDir = isDirOf(files[j])
                if (jIsDir && !iIsDir) {
                    val t = files[i]; files[i] = files[j]; files[j] = t
                } else if (jIsDir == iIsDir && files[j].name.lowercase() < files[i].name.lowercase()) {
                    val t = files[i]; files[i] = files[j]; files[j] = t
                }
            }
        }
    }

    private fun loadDir(dir: File) {
        Thread {
            val parent = dir.parentFile
            val newHasParent = parent != null

            var newFileArray: Array<File> = emptyArray()
            var accessDenied = false
            val newRootInfo = HashMap<String, RootFileInfo>()

            if (dir.exists() && dir.canRead()) {
                val files = dir.listFiles(FileFilter { fileItem ->
                    if (folderChooserMode) {
                        fileItem.isDirectory
                    } else {
                        fileItem.exists() && (fileItem.isDirectory || matchesExtension(fileItem))
                    }
                })

                if (files == null) {
                    accessDenied = true
                } else {
                    sortFiles(files) { it.isDirectory }
                    newFileArray = files
                }
            } else {
                accessDenied = true
            }

            // java.io.File bị chặn (thường gặp ngoài sdcard, vd /data, /system) - thử lại qua
            // shell (root nếu có, tự rơi về sh nếu không). Chỉ coi là thành công khi shell
            // xác nhận thư mục thực sự tồn tại - nếu không (kể cả khi thiết bị không root),
            // giữ nguyên accessDenied để báo đúng như trước, tránh hiện nhầm "thư mục rỗng".
            if (accessDenied && RootFile.dirExists(dir.absolutePath)) {
                val entries = ArrayList<File>()
                for (info in RootFile.list(dir.absolutePath)) {
                    if (folderChooserMode && !info.isDirectory) {
                        continue
                    }
                    val childFile = File(dir, info.fileName)
                    if (!folderChooserMode && !info.isDirectory && !matchesExtension(childFile)) {
                        continue
                    }
                    newRootInfo[childFile.absolutePath] = info
                    entries.add(childFile)
                }
                val array = entries.toTypedArray()
                sortFiles(array) { newRootInfo[it.absolutePath]?.isDirectory ?: it.isDirectory }
                newFileArray = array
                accessDenied = false
            }

            val finalFileArray = newFileArray
            val finalAccessDenied = accessDenied
            handler.post {
                hasParent = newHasParent
                fileArray = finalFileArray
                currentDir = dir
                rootInfoMap.clear()
                rootInfoMap.putAll(newRootInfo)
                dirChangedListener?.onDirChanged(dir)
                notifyDataSetChanged()
                progressBarDialog.hideDialog()
                selectionChangedListener?.onSelectionChanged(selectedFiles.size)
                if (finalAccessDenied) {
                    accessDeniedListener?.onAccessDenied(dir)
                }
            }
        }.start()
    }

    fun goParent(): Boolean {
        val current = currentDir
        if (hasParent && current != null) {
            loadDir(File(current.parent))
            return true
        }
        return false
    }

    override fun getCount(): Int {
        val array = fileArray
        return if (hasParent) {
            if (array == null) {
                1
            } else {
                array.size + 1
            }
        } else {
            array?.size ?: 0
        }
    }

    fun refresh() {
        val current = this.currentDir
        if (current != null) {
            this.loadDir(current)
        }
    }

    override fun getItem(position: Int): Any {
        val array = fileArray
        return if (hasParent) {
            if (position == 0) {
                File(currentDir?.parent)
            } else {
                array!![position - 1]
            }
        } else {
            array!![position]
        }
    }

    override fun getItemId(position: Int): Long {
        return 0
    }

    private fun toggleSelection(file: File) {
        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file)
        } else {
            selectedFiles.add(file)
        }
        notifyDataSetChanged()
        selectionChangedListener?.onSelectionChanged(selectedFiles.size)
    }

    // Chọn 1 thư mục duy nhất kiểu radio (chọn mục mới tự bỏ chọn mục trước đó, không tự
    // đóng màn hình) - dùng cho chế độ chọn thư mục KHÔNG multiple. Bấm lại đúng mục đang
    // chọn thì bỏ chọn. Tái dùng chung tập selectedFiles với chế độ multiple.
    private fun setSingleChecked(file: File) {
        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file)
        } else {
            selectedFiles.clear()
            selectedFiles.add(file)
        }
        notifyDataSetChanged()
        selectionChangedListener?.onSelectionChanged(selectedFiles.size)
    }

    // Một tệp/thư mục có phải là đối tượng "có thể chọn" (hiện checkbox) trong danh sách hiện tại không.
    // Ở chế độ chọn thư mục: mọi mục trong fileArray đều là thư mục -> có thể chọn.
    // Ở chế độ chọn tệp: chỉ tệp mới có thể chọn, thư mục chỉ dùng để điều hướng.
    private fun isSelectable(file: File): Boolean {
        return folderChooserMode || !isDir(file)
    }

    // Thư mục hiện tại đã được chọn hết (mọi mục có thể chọn) hay chưa - dùng để đồng bộ checkbox "Chọn tất cả".
    fun isAllCurrentDirSelected(): Boolean {
        val array = fileArray
        if (array == null || array.isEmpty()) {
            return false
        }
        var hasSelectable = false
        for (file in array) {
            if (isSelectable(file)) {
                hasSelectable = true
                if (!selectedFiles.contains(file)) {
                    return false
                }
            }
        }
        return hasSelectable
    }

    // Chọn/bỏ chọn tất cả các mục có thể chọn trong thư mục đang hiển thị.
    fun setSelectAllState(selectAll: Boolean) {
        val array = fileArray ?: return
        for (file in array) {
            if (!isSelectable(file)) {
                continue
            }
            if (selectAll) {
                selectedFiles.add(file)
            } else {
                selectedFiles.remove(file)
            }
        }
        notifyDataSetChanged()
        selectionChangedListener?.onSelectionChanged(selectedFiles.size)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View
        if (hasParent && position == 0) {
            view = View.inflate(parent.context, R.layout.list_item_dir, null)
            (view.findViewById<View>(R.id.ItemTitle) as TextView).text = "..."
            val checkBox = view.findViewById<View>(R.id.ItemCheckBox)
            if (checkBox != null) {
                checkBox.visibility = View.GONE
            }
            view.setOnClickListener { goParent() }
            return view
        } else {
            val file = getItem(position) as File
            if (isDir(file)) {
                view = View.inflate(parent.context, R.layout.list_item_dir, null)
                view.setOnClickListener {
                    if (!existsSafe(file)) {
                        Toast.makeText(view.context, "The selected file has been deleted. Please select again!", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    // Luôn vào thẳng loadDir() (chạy nền, tự xử lý rỗng/bị từ chối) - không
                    // dò listFiles() trước trên UI thread nữa, vì với thư mục ngoài sdcard nó
                    // rất dễ trả null (bị từ chối tạm thời) và chặn đứng việc đi sâu hơn dù
                    // thư mục thực ra đọc được.
                    loadDir(file)
                }
                if (folderChooserMode) {
                    val checkBox = view.findViewById<CheckBox>(R.id.ItemCheckBox)
                    if (multipleMode) {
                        if (checkBox != null) {
                            checkBox.visibility = View.VISIBLE
                            checkBox.setButtonDrawable(android.R.drawable.btn_checkbox)
                            checkBox.isChecked = selectedFiles.contains(file)
                            checkBox.setOnClickListener {
                                if (!existsSafe(file)) {
                                    Toast.makeText(view.context, "The selected directory has been deleted. Please select another one!", Toast.LENGTH_SHORT).show()
                                    return@setOnClickListener
                                }
                                toggleSelection(file)
                            }
                        }
                        // Nhấn giữ vẫn dùng để chọn nhanh 1 thư mục (giữ hành vi cũ, thêm vào danh sách đã chọn)
                        view.setOnLongClickListener {
                            if (!existsSafe(file)) {
                                Toast.makeText(view.context, "The selected directory has been deleted. Please select another one!", Toast.LENGTH_SHORT).show()
                                return@setOnLongClickListener true
                            }
                            toggleSelection(file)
                            true
                        }
                    } else {
                        // Chế độ chọn 1 thư mục (không multiple): giữ nguyên nhấn giữ để chọn
                        // ngay + đóng màn hình (hành vi cũ), đồng thời thêm checkbox dạng "1
                        // lựa chọn" (radio) - chọn thư mục nào thì tự bỏ chọn thư mục khác,
                        // KHÔNG tự đóng, phải bấm nút xác nhận riêng ở toolbar.
                        if (checkBox != null) {
                            checkBox.visibility = View.VISIBLE
                            checkBox.setButtonDrawable(android.R.drawable.btn_radio)
                            checkBox.isChecked = selectedFiles.contains(file)
                            checkBox.setOnClickListener {
                                if (!existsSafe(file)) {
                                    Toast.makeText(view.context, "The selected directory has been deleted. Please select another one!", Toast.LENGTH_SHORT).show()
                                    return@setOnClickListener
                                }
                                setSingleChecked(file)
                            }
                        }
                        view.setOnLongClickListener {
                            DialogHelper.confirm(view.context, view.context.getString(R.string.dialog_title_select_directory), file.absolutePath, Runnable {
                                if (!existsSafe(file)) {
                                    Toast.makeText(view.context, "The selected directory has been deleted. Please select another one!", Toast.LENGTH_SHORT).show()
                                    return@Runnable
                                }
                                selectedFile = file
                                fileSelected.run()
                            }, Runnable {})
                            true
                        }
                    }
                } else {
                    val checkBox = view.findViewById<View>(R.id.ItemCheckBox)
                    if (checkBox != null) {
                        checkBox.visibility = View.GONE
                    }
                }
            } else {
                view = View.inflate(parent.context, R.layout.list_item_file, null)
                val fileLength = sizeOf(file)
                val fileSize: String = if (fileLength < 1024) {
                    fileLength.toString() + "B"
                } else if (fileLength < 1048576) {
                    String.format("%sKB", String.format("%.2f", (fileLength / 1024.0)))
                } else if (fileLength < 1073741824) {
                    String.format("%sMB", String.format("%.2f", (fileLength / 1048576.0)))
                } else {
                    String.format("%sGB", String.format("%.2f", (fileLength / 1073741824.0)))
                }

                (view.findViewById<View>(R.id.ItemText) as TextView).text = fileSize

                val checkBox = view.findViewById<CheckBox>(R.id.ItemCheckBox)
                if (multipleMode) {
                    if (checkBox != null) {
                        checkBox.visibility = View.VISIBLE
                        checkBox.setButtonDrawable(android.R.drawable.btn_checkbox)
                        checkBox.isChecked = selectedFiles.contains(file)
                    }
                    val toggleListener = View.OnClickListener {
                        if (!existsSafe(file)) {
                            Toast.makeText(view.context, "The selected file has been deleted. Please select again!", Toast.LENGTH_SHORT).show()
                            return@OnClickListener
                        }
                        toggleSelection(file)
                    }
                    view.setOnClickListener(toggleListener)
                    checkBox?.setOnClickListener(toggleListener)
                } else {
                    if (checkBox != null) {
                        checkBox.visibility = View.GONE
                    }
                    view.setOnClickListener {
                        DialogHelper.confirm(view.context, view.context.getString(R.string.dialog_title_select_file), file.absolutePath, Runnable {
                            if (!existsSafe(file)) {
                                Toast.makeText(view.context, "The selected file has been deleted. Please select again!", Toast.LENGTH_SHORT).show()
                                return@Runnable
                            }
                            selectedFile = file
                            fileSelected.run()
                        }, Runnable {})
                    }
                }
            }
            (view.findViewById<View>(R.id.ItemTitle) as TextView).text = file.name
            return view
        }
    }

    fun getSelectedFile(): File? {
        return this.selectedFile
    }

    fun isMultipleMode(): Boolean {
        return multipleMode
    }

    fun getSelectedFiles(): List<File> {
        return ArrayList(selectedFiles)
    }

    fun getSelectedCount(): Int {
        return selectedFiles.size
    }

    fun setSelectionChangedListener(listener: SelectionChangedListener?) {
        this.selectionChangedListener = listener
    }

    companion object {
        @JvmStatic
        fun FolderChooser(rootDir: File, fileSelected: Runnable, progressBarDialog: ProgressBarDialog): AdapterFileSelector {
            val adapterFileSelector = AdapterFileSelector(rootDir, fileSelected, progressBarDialog, null)
            adapterFileSelector.folderChooserMode = true
            return adapterFileSelector
        }

        @JvmStatic
        fun FolderChooser(rootDir: File, fileSelected: Runnable, progressBarDialog: ProgressBarDialog, multiple: Boolean): AdapterFileSelector {
            val adapterFileSelector = FolderChooser(rootDir, fileSelected, progressBarDialog)
            adapterFileSelector.multipleMode = multiple
            return adapterFileSelector
        }

        @JvmStatic
        fun FileChooser(rootDir: File, fileSelected: Runnable, progressBarDialog: ProgressBarDialog, extension: String?): AdapterFileSelector {
            val adapterFileSelector = AdapterFileSelector(rootDir, fileSelected, progressBarDialog, extension)
            adapterFileSelector.folderChooserMode = false
            return adapterFileSelector
        }

        @JvmStatic
        fun FileChooser(rootDir: File, fileSelected: Runnable, progressBarDialog: ProgressBarDialog, extension: String?, multiple: Boolean): AdapterFileSelector {
            val adapterFileSelector = FileChooser(rootDir, fileSelected, progressBarDialog, extension)
            adapterFileSelector.multipleMode = multiple
            return adapterFileSelector
        }
    }
}
