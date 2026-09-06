package com.omarea.krscript.config

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.tool.tree.R
import com.omarea.krscript.executor.ScriptEnvironmen
import com.omarea.krscript.model.ActionNode
import com.omarea.krscript.model.ClickableNode
import com.omarea.krscript.model.NodeInfoBase
import com.omarea.krscript.model.PageMenuOption
import com.omarea.krscript.model.PageNode
import java.io.ByteArrayInputStream

class PageConfigSh(private var activity: Activity, private var pageConfigSh: String, private var parentConfig: PageNode?) {
    private var handler = Handler(Looper.getMainLooper())

    // Reader được dùng ở lần execute() gần nhất - dùng để lấy pageMenuOptions ([[menu]]/[[fab]])
    // được gom trong lúc parse, GIỐNG hệt cách ActionPage lấy từ PageConfigReader ở nhánh
    // pageConfigPath (file .toml tĩnh). execute() có thể không tạo reader nào (vd script lỗi/rỗng)
    // nên luôn fallback về danh sách rỗng thay vì null.
    private var lastReader: PageConfigReader? = null
    val pageMenuOptions: ArrayList<PageMenuOption> get() = lastReader?.pageMenuOptions ?: ArrayList()
    val headerActions: ArrayList<ActionNode> get() = lastReader?.headerActions ?: ArrayList()
    val autoShowActions: ArrayList<ActionNode> get() = lastReader?.autoShowActions ?: ArrayList()
    // Icon container-level của [[menu]]/[[fab]] (field "icon"/"icon-path" ở NGOÀI "items") - xem
    // PageConfigReader.menuIcon/fabIcon.
    val menuIcon: ClickableNode? get() = lastReader?.menuIcon
    val fabIcon: ClickableNode? get() = lastReader?.fabIcon

    // load-after: xem PageConfigReader.hasDeferredEntries/buildDeferredNodes().
    val hasDeferredEntries: Boolean get() = lastReader?.hasDeferredEntries ?: false
    fun buildDeferredNodes(): ArrayList<PageConfigReader.DeferredNodeResult> =
        lastReader?.buildDeferredNodes() ?: ArrayList()

    // Nhận diện nội dung TOML inline khi dòng 1 hoặc dòng 2 (bỏ qua dòng trống) là
    // header bắt đầu bằng "[[toml]]" (marker đánh dấu inline TOML, tuỳ chọn - dùng khi
    // nội dung không mở đầu bằng [[group]], vd chỉ có [[action]]/[[page]] đứng lẻ) hoặc
    // "[[group]]" như cũ (chỉ khớp đúng để tránh nhận nhầm output lỗi/không liên quan).
    private fun looksLikeInlineToml(result: String): Boolean {
        val firstLines = result.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.take(2).toList()
        return firstLines.any { it.startsWith("[[toml]]") || it.startsWith("[[group]]") }
    }

    private fun pageConfigShError(content: String) {
        handler.post {
            Toast.makeText(activity, activity.getString(R.string.kr_page_sh_invalid) + "\n" + content, Toast.LENGTH_LONG).show()
        }
    }

    private fun noReadPermission() {
        handler.post {
            Toast.makeText(activity, activity.getString(R.string.kr_page_sh_file_permission), Toast.LENGTH_LONG).show()
        }
    }

    fun execute(onNodeReady: ((NodeInfoBase?, Int, Int) -> Unit)? = null): ArrayList<NodeInfoBase>? {
        var items: ArrayList<NodeInfoBase>? = null

        val result = ScriptEnvironmen.executeResultRoot(activity, pageConfigSh, parentConfig)?.trim()
        if (result != null) {
            if (result.endsWith(".toml")) {
                val reader = PageConfigReader(activity, result, parentConfig?.pageConfigDir)
                lastReader = reader
                items = reader.readConfigXml(onNodeReady)
                if (items == null) {
                    noReadPermission()
                }
            } else if (looksLikeInlineToml(result)) {
                // Nội dung TOML trả về trực tiếp (không phải đường dẫn file):
                // nhận diện qua header [[toml]] hoặc [[group]] ở dòng 1 hoặc dòng 2.
                val inputStream = ByteArrayInputStream(result.toByteArray())
                val reader = PageConfigReader(activity, inputStream)
                lastReader = reader
                items = reader.readConfigXml(onNodeReady)
            } else if (result.isNotEmpty()) {
                pageConfigShError(result)
            }
        }
        return items
    }
}