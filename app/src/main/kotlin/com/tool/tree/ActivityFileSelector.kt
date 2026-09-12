package com.tool.tree

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.omarea.common.ui.ProgressBarDialog
import com.tool.tree.databinding.ActivityFileSelectorBinding
import com.tool.tree.ui.AdapterFileSelector
import java.io.File

class ActivityFileSelector : AppCompatActivity() {
    companion object {
        const val MODE_FILE = 0
        const val MODE_FOLDER = 1
    }

    private var adapterFileSelector: AdapterFileSelector? = null
    var extension = ""
    var mode = MODE_FILE
    var multiple = false
    var pathHome = ""
    private lateinit var binding: ActivityFileSelectorBinding
    private var toolbar: Toolbar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeModeState.switchTheme(this)
        binding = ActivityFileSelectorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        binding.fileDrawerContainer.engine.cornerRadius = 0f
        binding.fileDrawerContainer.setDrawStrokeEnabled(false)

        val toolbar = findViewById<View>(R.id.toolbar) as Toolbar
        this.toolbar = toolbar
        setSupportActionBar(toolbar)

        supportActionBar!!.setHomeButtonEnabled(true)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setHomeAsUpIndicator(R.drawable.ic_arrow_back)
        toolbar.setNavigationOnClickListener {
            finish()
        }

        onBackPressedDispatcher.addCallback(this) {
            if (adapterFileSelector?.goParent() == true) return@addCallback
            setResult(RESULT_CANCELED, Intent())
            finish()
        }

        intent.extras?.run {
            if (containsKey("extension")) {
                extension = "" + intent.extras?.getString("extension")
                // Không còn nối "(.ext)" vào title nữa - đã chuyển sang báo bằng toast lúc vào
                // trang (xem onResume()), giống cách chế độ chọn thư mục vẫn đang báo bằng toast.
            }
            if (containsKey("mode")) {
                mode = getInt("mode")
                if (mode == MODE_FOLDER) {
                    title = getString(R.string.title_activity_folder_selector)
                }
            }
            if (containsKey("multiple")) {
                multiple = getBoolean("multiple")
            }
            if (containsKey("path_home")) {
                pathHome = "" + intent.extras?.getString("path_home")
            }
        }

        invalidateOptionsMenu()

        // Tiêu đề toolbar hiện đường dẫn thư mục hiện tại (xem updatePathTitle()) - ép 1 dòng,
        // cắt bớt và hiện "..." Ở ĐẦU khi quá dài, giữ lại phần cuối path (thường quan trọng
        // hơn phần đầu). Toolbar tự tạo TextView tiêu đề khi layout, nên phải chờ tới lúc đó
        // mới chỉnh được; chỉnh 1 lần là đủ vì Toolbar tái dùng cùng 1 TextView cho các lần
        // đổi title sau (updatePathTitle chỉ setText, không tạo lại view).
        toolbar.post {
            for (i in 0 until toolbar.childCount) {
                val child = toolbar.getChildAt(i)
                if (child is TextView && child.text?.toString() == toolbar.title?.toString()) {
                    child.isSingleLine = true
                    child.maxLines = 1
                    child.ellipsize = android.text.TextUtils.TruncateAt.START
                    break
                }
            }
        }
    }

    private fun updatePathTitle(dir: File) {
        title = dir.absolutePath
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Chọn nhiều (file/thư mục) đã có nút xác nhận từ trước. Chọn 1 thư mục (không
        // multiple) giờ cũng cần nút này vì checkbox không tự đóng màn hình như nhấn giữ.
        if (multiple || mode == MODE_FOLDER) {
            menuInflater.inflate(R.menu.menu_file_selector, menu)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_confirm_selection) {
            if (mode == MODE_FOLDER && !multiple) {
                finishWithSingleFolderSelection()
            } else {
                finishWithSelection()
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // Xác nhận thư mục đã chọn qua checkbox "1 lựa chọn" (chế độ chọn thư mục, không
    // multiple) - trả về đúng extra "file" (không phải "files") để khớp với cách long-press
    // chọn ngay vẫn đang trả về, giữ tương thích với nơi gọi màn hình này.
    private fun finishWithSingleFolderSelection() {
        val selected = adapterFileSelector?.getSelectedFiles()?.firstOrNull()
        if (selected == null) {
            showToast(R.string.msg_nothing_selected)
            return
        }
        setResult(RESULT_OK, Intent().putExtra("file", selected.absolutePath))
        finish()
    }

    private fun finishWithSelection() {
        val selected = adapterFileSelector?.getSelectedFiles()?.map { it.absolutePath }
        if (selected.isNullOrEmpty()) {
            showToast(R.string.msg_nothing_selected)
            return
        }
        setResult(RESULT_OK, Intent().putStringArrayListExtra("files", ArrayList(selected)))
        finish()
    }

    override fun onResume() {
        super.onResume()
        loadData()
        if (mode == MODE_FOLDER && !multiple) {
            showToast(R.string.msg_folder_mode)
        } else if (multiple) {
            showToast(R.string.msg_multiple_select_mode)
        } else if (mode == MODE_FILE && extension.isNotEmpty()) {
            showToast(getString(R.string.msg_file_extension_required, extension.replace(",", ", ")))
        }
    }

    private fun loadData() {
        val sdcard = Environment.getExternalStorageDirectory()
        val startDir = if (pathHome.isNotEmpty()) {
            val homeDir = File(pathHome)
            if (homeDir.exists() && homeDir.isDirectory && homeDir.canRead()) {
                homeDir
            } else {
                showToast(getString(R.string.msg_path_home_not_found, pathHome))
                sdcard
            }
        } else {
            sdcard
        }

        if (startDir.exists() && startDir.isDirectory) {
            val list = startDir.listFiles()
            if (list == null) {
                showToast("Failed to retrieve file list!")
                return
            }
            val onSelected = Runnable {
                val file = adapterFileSelector?.getSelectedFile()
                if (file != null) {
                    this.setResult(RESULT_OK, Intent().putExtra("file", file.absolutePath))
                    this.finish()
                }
            }
            adapterFileSelector = if (mode == MODE_FOLDER) {
                AdapterFileSelector.FolderChooser(startDir, onSelected, ProgressBarDialog(this), multiple)
            } else {
                AdapterFileSelector.FileChooser(startDir, onSelected, ProgressBarDialog(this), extension, multiple)
            }

            // Set ngay path khởi đầu (không đợi listener, vì loadDir() đầu tiên chạy nền và
            // có thể đã hoàn tất trước khi listener kịp gắn ở dòng dưới), rồi mỗi lần đổi
            // thư mục sau đó (mở thư mục con / bấm "..") sẽ tự cập nhật qua listener.
            updatePathTitle(startDir)
            adapterFileSelector?.setDirChangedListener(object : AdapterFileSelector.OnDirChangedListener {
                override fun onDirChanged(dir: File) {
                    updatePathTitle(dir)
                }
            })

            binding.fileSelectorList.adapter = adapterFileSelector

            adapterFileSelector?.setAccessDeniedListener(object : AdapterFileSelector.AccessDeniedListener {
                override fun onAccessDenied(dir: File) {
                    showToast(getString(R.string.msg_dir_access_denied))
                }
            })

            // Hàng "Chọn tất cả" chỉ hiện khi đang ở chế độ chọn nhiều (multiple)
            if (multiple) {
                binding.selectAllBlock.visibility = View.VISIBLE

                fun syncSelectAllCheckbox() {
                    binding.selectAll.isChecked = adapterFileSelector?.isAllCurrentDirSelected() == true
                }
                syncSelectAllCheckbox()

                val toggleSelectAll = View.OnClickListener {
                    val nextState = adapterFileSelector?.isAllCurrentDirSelected() != true
                    adapterFileSelector?.setSelectAllState(nextState)
                    binding.selectAll.isChecked = nextState
                }
                binding.selectAllBlock.setOnClickListener(toggleSelectAll)
                binding.selectAll.setOnClickListener(toggleSelectAll)

                adapterFileSelector?.setSelectionChangedListener(object : AdapterFileSelector.SelectionChangedListener {
                    override fun onSelectionChanged(selectedCount: Int) {
                        syncSelectAllCheckbox()
                    }
                })
            } else {
                binding.selectAllBlock.visibility = View.GONE
            }

        } else {
            showToast("External storage not available!")
        }
    }

    private fun showToast(message: CharSequence) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showToast(messageRes: Int) {
        showToast(getString(messageRes))
    }
}
