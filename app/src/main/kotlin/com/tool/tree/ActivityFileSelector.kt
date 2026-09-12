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

        // Cho phép tiêu đề toolbar xuống dòng (tối đa 2 dòng) thay vì bị cắt hiện dấu "..."
        // Toolbar tự tạo TextView tiêu đề khi layout, nên phải chờ tới lúc đó mới chỉnh được.
        toolbar.post {
            for (i in 0 until toolbar.childCount) {
                val child = toolbar.getChildAt(i)
                if (child is TextView && child.text?.toString() == toolbar.title?.toString()) {
                    child.isSingleLine = false
                    child.maxLines = 2
                    child.ellipsize = null
                    break
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (multiple) {
            menuInflater.inflate(R.menu.menu_file_selector, menu)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_confirm_selection) {
            finishWithSelection()
            return true
        }
        return super.onOptionsItemSelected(item)
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
