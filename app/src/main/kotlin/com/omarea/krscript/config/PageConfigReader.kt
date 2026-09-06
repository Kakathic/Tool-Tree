package com.omarea.krscript.config

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.util.Log
import android.widget.Toast
import com.omarea.common.model.SelectItem
import com.omarea.krscript.executor.ExtractAssets
import com.omarea.krscript.executor.ScriptEnvironmen
import com.omarea.krscript.model.*
import java.io.InputStream
import java.util.Locale.getDefault
import androidx.core.graphics.toColorInt
import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlTable

/**
 * Created by Hello on 2018/04/01.
 */
class PageConfigReader {
    private var context: Context
    private var pageConfig: String = ""

    // 读取pageConfig时自动获得
    private var pageConfigAbsPath: String = ""
    private var pageConfigStream: InputStream? = null
    private var parentDir: String = ""

    constructor(context: Context, pageConfig: String, parentDir: String?) {
        this.context = context
        this.pageConfig = pageConfig
        this.parentDir = parentDir ?: ""
    }

    constructor(context: Context, pageConfigStream: InputStream) {
        this.context = context
        this.pageConfigStream = pageConfigStream
    }

    fun readConfigXml(onNodeReady: ((NodeInfoBase?, Int, Int) -> Unit)? = null): ArrayList<NodeInfoBase>? {
        if (pageConfigStream != null) {
            return readConfigXml(pageConfigStream!!, onNodeReady)
        } else {
            try {
                val pathAnalysis = PathAnalysis(context, parentDir)
                pathAnalysis.parsePath(pageConfig).run {
                    val fileInputStream = this ?: return ArrayList()
                    pageConfigAbsPath = pathAnalysis.getCurrentAbsPath()
                    return readConfigXml(fileInputStream, onNodeReady)
                }
            } catch (ex: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Failed to parse configuration file\n" + ex.message, Toast.LENGTH_LONG).show()
                }
                Log.e("KrConfig Fail！", "" + ex.message)
            }

        }
        return null
    }

    private fun readConfigXml(fileInputStream: InputStream, onNodeReady: ((NodeInfoBase?, Int, Int) -> Unit)? = null): ArrayList<NodeInfoBase>? {
        return try {
            val rawText = fileInputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            readConfigToml(rawText, onNodeReady)
        } catch (ex: Exception) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Failed to parse configuration file\n" + ex.message, Toast.LENGTH_LONG).show()
            }
            Log.e("KrConfig Fail！", "" + ex.message)
            null
        }
    }

    private var vitualRootNode: NodeInfoBase? = null
    private fun executeResultRoot(context: Context, scriptIn: String): String {
        if (vitualRootNode == null) {
            vitualRootNode = NodeInfoBase(pageConfigAbsPath)
        }

        return ScriptEnvironmen.executeResultRoot(context, scriptIn, vitualRootNode)
    }

    private val pendingSwitchStates = ArrayList<Pair<SwitchNode, String>>()
    private val pendingPickerStates = ArrayList<Pair<PickerNode, String>>()
    private val pendingRowCheckedStates = ArrayList<Pair<TextNode.TextRow, String>>()
    private val pendingRowVisibleStates = ArrayList<Triple<ArrayList<TextNode.TextRow>, TextNode.TextRow, String>>()

    private val pendingDynamicStrings = ArrayList<Triple<Any, String, String>>()

    private fun registerDynamicString(target: Any, fieldKey: String, script: String) {
        pendingDynamicStrings.add(Triple(target, fieldKey, script))
    }

    private val pendingBoolShells = ArrayList<Triple<NodeInfoBase?, String, String>>()

    private fun resolvePendingStates() {
        if (pendingSwitchStates.isEmpty() && pendingPickerStates.isEmpty() &&
            pendingRowCheckedStates.isEmpty() && pendingRowVisibleStates.isEmpty() &&
            pendingDynamicStrings.isEmpty() && pendingBoolShells.isEmpty()) return

        val scripts = LinkedHashMap<String, String>()
        pendingSwitchStates.forEachIndexed { index, pair -> scripts["switch:$index"] = pair.second }
        pendingPickerStates.forEachIndexed { index, pair -> scripts["picker:$index"] = pair.second }
        pendingRowCheckedStates.forEachIndexed { index, pair -> scripts["row-checked:$index"] = pair.second }
        pendingRowVisibleStates.forEachIndexed { index, triple -> scripts["row-visible:$index"] = triple.third }
        pendingDynamicStrings.forEachIndexed { index, triple -> scripts["dynstr:$index"] = triple.third }
        pendingBoolShells.forEachIndexed { index, triple -> scripts["boolsh:$index"] = triple.third }

        if (vitualRootNode == null) {
            vitualRootNode = NodeInfoBase(pageConfigAbsPath)
        }
        val results = ScriptEnvironmen.executeMultipleResultRoot(context, scripts, vitualRootNode)

        pendingSwitchStates.forEachIndexed { index, pair ->
            val shellResult = results["switch:$index"] ?: ""
            pair.first.checked = shellResult != "error" && (shellResult == "1" || shellResult.lowercase(getDefault()) == "true")
        }
        pendingPickerStates.forEachIndexed { index, pair ->
            results["picker:$index"]?.let { pair.first.value = it }
        }
        pendingRowCheckedStates.forEachIndexed { index, pair ->
            val shellResult = results["row-checked:$index"] ?: ""
            pair.first.checked = shellResult.trim() == "1"
        }
        pendingRowVisibleStates.forEachIndexed { index, triple ->
            val shellResult = results["row-visible:$index"] ?: ""
            if (shellResult.trim() != "1") {
                triple.first.remove(triple.second)
            }
        }
        // Áp dụng kết quả dynamic strings cho từng target
        pendingDynamicStrings.forEachIndexed { index, triple ->
            val shellResult = results["dynstr:$index"] ?: ""
            if (shellResult != "error") {
                val (target, fieldKey, _) = triple
                when (target) {
                    // ClickableNode phải đứng TRƯỚC NodeInfoBase (ClickableNode là subtype) để case
                    // "icon" (icon-sh) được xử lý - đồng thời lặp lại title/desc/summary vì nhánh
                    // NodeInfoBase bên dưới không còn được chạm tới với target là ClickableNode.
                    is ClickableNode -> when (fieldKey) {
                        "title" -> target.title = shellResult
                        "desc" -> target.desc = shellResult
                        "summary" -> target.summary = shellResult
                        "icon" -> target.iconPath = shellResult
                        "photo" -> target.photoPath = shellResult
                        "bg" -> target.bgPath = shellResult
                    }
                    is NodeInfoBase -> when (fieldKey) {
                        "title" -> target.title = shellResult
                        "desc" -> target.desc = shellResult
                        "summary" -> target.summary = shellResult
                    }
                    is RunnableNode -> if (fieldKey == "warning") target.warning = shellResult
                    is com.omarea.common.model.SelectItem -> if (fieldKey == "title") target.title = shellResult
                }
            }
        }
        // Áp dụng kết quả bool shells
        pendingBoolShells.forEachIndexed { index, triple ->
            val shellResult = results["boolsh:$index"] ?: ""
            val result = shellResult.trim() == "1"
            val (target, fieldKey, _) = triple
            if (fieldKey == "bool-support" && target != null) {
                if (!result) (target as GroupNode).supported = false
            }
        }

        pendingSwitchStates.clear()
        pendingPickerStates.clear()
        pendingRowCheckedStates.clear()
        pendingRowVisibleStates.clear()
        pendingDynamicStrings.clear()
        pendingBoolShells.clear()
    }

    // "group" vẫn là container như cũ. Từ bản bỏ dot-notation: tất cả loại con
    // (text/switch/.../fab) đều là mảng PHẲNG ở gốc tài liệu, không còn lồng kiểu [[group.action]] -
    // được gán vào [[group]] gần nhất phía trước theo thứ tự dòng, xem tomlChildren().
    // ("[[toml]]" chỉ là marker nhận diện inline TOML ở PageConfigSh.kt, không phải type ở đây -
    // nếu xuất hiện trong tài liệu, parser bỏ qua vô hại vì không nằm trong danh sách dưới.)
    private val tomlNodeTypeOrder = listOf("group", "text", "switch", "picker", "action", "page", "download", "editor", "resource", "menu", "fab")

    private val collectedMenuOptions = ArrayList<PageMenuOption>()

    /** Danh sách menu 3 chấm + fab gom được sau khi readConfigXml()/readConfigToml() chạy xong. */
    val pageMenuOptions: ArrayList<PageMenuOption> get() = collectedMenuOptions

    // "action" bên dưới và ActionPage.onCreateOptionsMenu().
    private val collectedHeaderActions = ArrayList<ActionNode>()
    /** Danh sách group.action có menu = true, gom được sau khi đọc xong toàn bộ trang. */
    val headerActions: ArrayList<ActionNode> get() = collectedHeaderActions

    private val collectedAutoShowActions = ArrayList<ActionNode>()
    /** Danh sách group.action có show = true, gom được sau khi đọc xong toàn bộ trang. */
    val autoShowActions: ArrayList<ActionNode> get() = collectedAutoShowActions

    // Icon khai báo TRỰC TIẾP ở cấp container [[menu]]/[[fab]] (field "icon"/"icon-path", KHÔNG
    // phải trong "items") - dùng làm icon mặc định cho nút "⋮"/FAB khi không muốn/không cần set
    // icon riêng cho từng item. Bọc trong ClickableNode chỉ để giữ đúng "pageConfigDir" (thư mục
    // của CHÍNH trang đang đọc) phục vụ IconPathAnalysis resolve đường dẫn tương đối - giống hệt
    // cách pageMenuOptionToml() làm với icon của từng item. Trang có thể khai [[menu]]/[[fab]]
    // nhiều lần (gộp chung) nên chỉ giữ giá trị KHAI BÁO ĐẦU TIÊN, tránh lần khai báo sau (không
    // set icon) vô tình xoá icon đã có.
    private var collectedMenuIcon: ClickableNode? = null
    private var collectedFabIcon: ClickableNode? = null
    val menuIcon: ClickableNode? get() = collectedMenuIcon
    val fabIcon: ClickableNode? get() = collectedFabIcon

    // ========== load-after: hoãn build 1 số mục tới SAU KHI trang đã tải xong ==========
    // Ghi lại đủ thông tin để build lại ĐÚNG NHƯ LÚC PARSE bình thường ở buildDeferredNodes():
    // loại node, bảng TOML gốc, group cha (null = ở gốc trang), và "insertIndex" = vị trí ĐÚNG
    // như trong file cấu hình gốc (tính bằng số mục ĐÃ build thành công của cùng container tại
    // thời điểm gặp entry này - xem tomlChildren()).
    private class DeferredEntry(val type: String, val table: TomlTable, val group: GroupNode?, val insertIndex: Int)
    private val deferredEntries = ArrayList<DeferredEntry>()

    /** true nếu trang có ít nhất 1 mục load-after=true - dùng để biết có cần chạy buildDeferredNodes() không. */
    val hasDeferredEntries: Boolean get() = deferredEntries.isNotEmpty()

    /** Kết quả build 1 mục hoãn: group cha (null = gốc trang), node đã build xong, và "index" là
     * vị trí CHÈN THỰC TẾ đã bù trừ theo thứ tự các mục hoãn khác cùng container (xem buildDeferredNodes()) -
     * bên gọi cần chèn các kết quả này ĐÚNG THEO THỨ TỰ TRẢ VỀ để index không bị lệch. */
    class DeferredNodeResult(val group: GroupNode?, val node: NodeInfoBase, val index: Int)

    private fun isLoadAfter(table: TomlTable): Boolean = tomlTruthy(tomlGet(table, "load-after"))

    private fun menuGroupOptionsToml(table: TomlTable, isFab: Boolean): ArrayList<PageMenuOption> {
        val handler = tomlGet(table, "handler", "handler-sh").orEmpty()
        tomlGet(table, "icon", "icon-path")?.trim()?.takeIf { it.isNotEmpty() }?.let { path ->
            val holder = ClickableNode(pageConfigAbsPath).apply { iconPath = path }
            if (isFab) {
                if (collectedFabIcon == null) collectedFabIcon = holder
            } else {
                if (collectedMenuIcon == null) collectedMenuIcon = holder
            }
        }
        val result = ArrayList<PageMenuOption>()
        for (itemTable in tomlEntries(table, "items")) {
            val option = pageMenuOptionToml(itemTable) ?: continue
            if (option.script.isEmpty() && handler.isNotEmpty()) {
                option.script = handler
            }
            option.isFab = isFab
            result.add(option)
        }
        return result
    }

    private fun tomlGet(table: TomlTable, vararg keys: String): String? {
        for (key in keys) {
            if (table.contains(key)) {
                val value = table.get(key) ?: continue
                return value.toString()
            }
        }
        return null
    }

    //                       lock = false / "0" → không đổi gì
    private fun parseLockAttr(raw: String?, node: ClickableNode) {
        if (raw == null) return
        val v = raw.trim()
        val pipeIndex = v.indexOf('|')
        if (pipeIndex >= 0) {
            // Format mới: "state|message"
            val state = v.substring(0, pipeIndex).trim()
            val message = v.substring(pipeIndex + 1).trim()
            node.locked = (state == "1")
            node.lockMessage = message
        } else {
            // Hỗ trợ cũ: boolean hoặc từ khoá
            node.locked = tomlTruthy(v, "locked")
            node.lockMessage = ""
        }
    }

    private fun tomlTruthy(raw: String?, vararg extraTruthyValues: String): Boolean {
        if (raw == null) return false
        val v = raw.lowercase(getDefault()).trim()
        return v == "1" || v == "true" || extraTruthyValues.any { it == v }
    }

    private fun resolveBoolOrShell(raw: String?, vararg extraTruthyValues: String): Boolean {
        if (raw == null) return false
        val v = raw.trim()
        val lower = v.lowercase(getDefault())
        return when {
            lower.isEmpty() -> false
            lower == "1" || lower == "true" || extraTruthyValues.any { it == lower } -> true
            lower == "0" || lower == "false" -> false
            else -> executeResultRoot(context, v).trim() == "1"
        }
    }

    private fun tomlEntries(parent: TomlTable, key: String): List<TomlTable> {
        return when {
            parent.isArray(key) -> {
                val arr = parent.getArray(key) ?: return emptyList()
                (0 until arr.size()).map { arr.getTable(it) }
            }
            parent.isTable(key) -> listOf(parent.getTable(key)!!)
            else -> emptyList()
        }
    }

    private fun readConfigToml(rawText: String, onNodeReady: ((NodeInfoBase?, Int, Int) -> Unit)? = null): ArrayList<NodeInfoBase>? {
        return try {
            val result = Toml.parse(rawText)
            if (result.hasErrors()) {
                val message = result.errors().joinToString("\n") { it.toString() }
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Failed to parse configuration file (toml)\n$message", Toast.LENGTH_LONG).show()
                }
                Log.e("KrConfig Fail！", message)
                return null
            }
            val nodes = tomlChildren(result, onNodeReady)
            resolvePendingStates()
            nodes
        } catch (ex: Exception) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Failed to parse configuration file\n" + ex.message, Toast.LENGTH_LONG).show()
            }
            Log.e("KrConfig Fail！", "" + ex.message)
            null
        }
    }

    private fun tomlEntryLine(parent: TomlTable, key: String, index: Int): Int? {
        return try {
            if (parent.isArray(key)) {
                val arr: TomlArray? = parent.getArray(key)
                arr?.inputPositionOf(index)?.line()
            } else if (parent.isTable(key)) {
                parent.inputPositionOf(key)?.line()
            } else {
                null
            }
        } catch (ex: Exception) {
            null
        }
    }

    // mục và không cần field `order`. Kể từ bản bỏ dot-notation, hàm này chỉ gọi 1 LẦN DUY NHẤT
    // ở gốc tài liệu (không còn đệ quy theo group như trước) - xem vòng lặp gán currentGroup bên dưới.
    private fun tomlChildren(parent: TomlTable, onNodeReady: ((NodeInfoBase?, Int, Int) -> Unit)? = null): ArrayList<NodeInfoBase> {
        class Entry(val line: Int, val seq: Int, val type: String, val table: TomlTable)

        val entries = ArrayList<Entry>()
        var seq = 0
        for (type in tomlNodeTypeOrder) {
            for ((index, table) in tomlEntries(parent, type).withIndex()) {
                seq++
                val line = tomlEntryLine(parent, type, index) ?: Int.MAX_VALUE
                entries.add(Entry(line, seq, type, table))
            }
        }
        val sortedEntries = entries.sortedWith(compareBy({ it.line }, { it.seq }))

        val total = sortedEntries.size
        var done = 0
        val result = ArrayList<NodeInfoBase>()
        // [[group]] gần nhất đã gặp theo thứ tự dòng - các loại con (action/text/...) phía sau,
        // trước [[group]] kế tiếp, được gán làm con của group này thay vì dot-notation lồng nhau.
        // Mục nằm trước [[group]] đầu tiên (nếu có) được thêm thẳng vào kết quả (không group).
        var currentGroup: GroupNode? = null
        // true khi đang ở trong 1 [[group]] có support=false -> bỏ hết các mục con của nó
        var groupHidden = false

        // Chỉ báo group qua onNodeReady SAU KHI đã gom xong children (ngay trước khi sang
        // [[group]] kế tiếp hoặc hết danh sách) - xem closeCurrentGroup(). Con thuộc group không
        // còn báo riêng (chỉ báo tiến độ, node=null) - trước đây báo group rỗng NGAY LÚC TẠO rồi
        // báo thêm từng con riêng khiến trang process=true (progressive) vẽ trùng: con vừa hiện
        // rời vừa hiện bọc trong group. Mục KHÔNG thuộc group nào vẫn báo như cũ.
        fun closeCurrentGroup(doneNow: Int) {
            currentGroup?.let { onNodeReady?.invoke(it, doneNow, total) }
        }

        for (entry in sortedEntries) {
            if (entry.type == "group") {
                closeCurrentGroup(done)
                val group = groupNodeToml(entry.table)
                done++
                if (group.supported) {
                    currentGroup = group
                    groupHidden = false
                    result.add(group)
                } else {
                    currentGroup = null
                    groupHidden = true
                    onNodeReady?.invoke(null, done, total)
                }
                continue
            }

            done++
            if (groupHidden) {
                onNodeReady?.invoke(null, done, total)
                continue
            }
            if (isLoadAfter(entry.table)) {
                // Chưa build ngay - ghi nhớ container + vị trí gốc, build sau ở buildDeferredNodes().
                val targetList = currentGroup?.children ?: result
                deferredEntries.add(DeferredEntry(entry.type, entry.table, currentGroup, targetList.size))
                onNodeReady?.invoke(null, done, total)
                continue
            }
            val node = tomlBuildNode(entry.type, entry.table)
            if (currentGroup != null) {
                node?.let { currentGroup!!.children.add(it) }
                onNodeReady?.invoke(null, done, total)
            } else {
                node?.let { result.add(it) }
                onNodeReady?.invoke(node, done, total)
            }
        }
        closeCurrentGroup(done)
        return result
    }

    /**
     * Build các mục bị hoãn (load-after=true) - gọi SAU KHI trang đã hiển thị xong (xem
     * ActionPage.startDeferredLoadIfNeeded()). Build lại ĐÚNG NHƯ tomlBuildNode() bình thường
     * (support-sh/title-sh/switch-picker state/...), rồi resolvePendingStates() riêng 1 lượt
     * cho các mục này (KHÔNG ảnh hưởng lượt resolve đã chạy xong lúc tải trang ban đầu).
     * "index" trong kết quả trả về đã bù trừ đúng theo thứ tự gốc trong file cấu hình - bên
     * gọi cần chèn tuần tự ĐÚNG THEO THỨ TỰ trả về (không đảo thứ tự) để vị trí chính xác.
     */
    fun buildDeferredNodes(): ArrayList<DeferredNodeResult> {
        val builtResults = ArrayList<DeferredNodeResult>()
        val insertedCountPerContainer = HashMap<GroupNode?, Int>()
        for (entry in deferredEntries) {
            val node = tomlBuildNode(entry.type, entry.table) ?: continue
            val extra = insertedCountPerContainer[entry.group] ?: 0
            val finalIndex = entry.insertIndex + extra
            insertedCountPerContainer[entry.group] = extra + 1
            builtResults.add(DeferredNodeResult(entry.group, node, finalIndex))
        }
        resolvePendingStates()
        deferredEntries.clear()
        return builtResults
    }

    private fun tomlBuildNode(type: String, table: TomlTable): NodeInfoBase? {
        return when (type) {
            "page" -> pageNodeToml(table)
            "action" -> {
                val action = actionNodeToml(table)
                if (action != null) {
                    if (action.show) collectedAutoShowActions.add(action)
                    if (action.menu) {
                        if (action.key.isEmpty()) action.key = action.title
                        collectedHeaderActions.add(action)
                        null
                    } else {
                        action
                    }
                } else {
                    null
                }
            }
            "switch" -> switchNodeToml(table)
            "picker" -> pickerNodeToml(table)
            "download" -> downloadNodeToml(table)
            "text" -> textNodeToml(table)
            "editor" -> editorNodeToml(table)
            "resource" -> {
                resourceNodeToml(table)
                null
            }
            "menu" -> {
                collectedMenuOptions.addAll(menuGroupOptionsToml(table, isFab = false))
                null
            }
            "fab" -> {
                collectedMenuOptions.addAll(menuGroupOptionsToml(table, isFab = true))
                null
            }
            else -> null
        }
    }

    private fun mainNodeToml(nodeInfoBase: NodeInfoBase, table: TomlTable): NodeInfoBase? {
        tomlGet(table, "support", "visible")?.let {
            if (!resolveBoolOrShell(it, "support", "visible")) return null
        }
        tomlGet(table, "key", "index", "id")?.let { nodeInfoBase.key = it.trim() }
        tomlGet(table, "title-sh")?.let {
            nodeInfoBase.titleSh = it
            registerDynamicString(nodeInfoBase, "title", it)
        }
        if (nodeInfoBase.title.isEmpty()) {
            tomlGet(table, "title")?.let { nodeInfoBase.title = StringResRef.resolve(context, it) }
        }
        tomlGet(table, "desc-sh")?.let {
            nodeInfoBase.descSh = it
            registerDynamicString(nodeInfoBase, "desc", it)
        }
        if (nodeInfoBase.desc.isEmpty()) {
            tomlGet(table, "desc")?.let { nodeInfoBase.desc = StringResRef.resolve(context, it) }
        }
        tomlGet(table, "summary-sh")?.let {
            nodeInfoBase.summarySh = it
            registerDynamicString(nodeInfoBase, "summary", it)
        }
        if (nodeInfoBase.summary.isEmpty()) {
            tomlGet(table, "summary")?.let { nodeInfoBase.summary = StringResRef.resolve(context, it) }
        }
        tomlGet(table, "load-after")?.let { nodeInfoBase.loadAfter = tomlTruthy(it) }
        return nodeInfoBase
    }

    private fun clickableNodeToml(node: ClickableNode, table: TomlTable): ClickableNode? {
        return (mainNodeToml(node, table) as ClickableNode?)?.apply {
            tomlGet(table, "lock", "lock-state")?.let { parseLockAttr(it, this) }
            tomlGet(table, "lock-sh")?.let { lockShell = it.trim() }
            tomlGet(table, "min-sdk", "sdk-min")?.let { minSdkVersion = it.trim().toIntOrNull() ?: minSdkVersion }
            tomlGet(table, "max-sdk", "sdk-max")?.let { maxSdkVersion = it.trim().toIntOrNull() ?: maxSdkVersion }
            tomlGet(table, "target-sdk", "sdk-target")?.let { targetSdkVersion = it.trim().toIntOrNull() ?: targetSdkVersion }
            tomlGet(table, "icon", "icon-path")?.let { iconPath = it.trim() }
            tomlGet(table, "icon-sh")?.let {
                iconSh = it.trim()
                registerDynamicString(this, "icon", iconSh)
            }
            tomlGet(table, "icon-gif-num", "icon-gif_num")?.let { iconGifNum = it.trim().toIntOrNull() ?: iconGifNum }
            tomlGet(table, "icon-gif-time", "icon-gif_time")?.let { iconGifTime = it.trim().toIntOrNull() ?: iconGifTime }
            tomlGet(table, "icon-gif-autoplay", "icon-gif_autoplay")?.let { iconGifAutoplay = tomlTruthy(it) }
            tomlGet(table, "icon-gif-loop", "icon-gif-loop-count", "icon-gif_loop_count")?.let { iconGifLoopCount = it.trim().toIntOrNull() ?: iconGifLoopCount }
            tomlGet(table, "logo", "logo-path")?.let { logoPath = it.trim() }
            tomlGet(table, "photo", "photo-path")?.let { photoPath = it.trim() }
            tomlGet(table, "photo-sh")?.let {
                photoSh = it.trim()
                registerDynamicString(this, "photo", photoSh)
            }
            tomlGet(table, "photo-real-size", "photo-original-size")?.let { photoRealSize = tomlTruthy(it, "real-size", "original-size") }
            tomlGet(table, "photo-gif-num", "gif-num", "gif_num")?.let { photoGifNum = it.trim().toIntOrNull() ?: photoGifNum }
            tomlGet(table, "photo-gif-time", "gif-time", "gif_time")?.let { photoGifTime = it.trim().toIntOrNull() ?: photoGifTime }
            tomlGet(table, "photo-gif-autoplay", "gif-autoplay", "gif_autoplay")?.let { photoGifAutoplay = tomlTruthy(it) }
            tomlGet(table, "photo-gif-loop", "photo-gif-loop-count", "gif-loop", "gif-loop-count", "gif_loop_count")?.let { photoGifLoopCount = it.trim().toIntOrNull() ?: photoGifLoopCount }
            tomlGet(table, "bg", "bg-path")?.let { bgPath = it.trim() }
            tomlGet(table, "bg-sh")?.let {
                bgSh = it.trim()
                registerDynamicString(this, "bg", bgSh)
            }
            tomlGet(table, "allow-shortcut")?.let { allowShortcut = tomlTruthy(it, "allow", "allow-shortcut") }
            if (key.isNotEmpty() && key.startsWith("@") && allowShortcut == null) {
                allowShortcut = false
            }
        }
    }

    private fun runnableNodeToml(node: RunnableNode, table: TomlTable): RunnableNode? {
        return (clickableNodeToml(node, table) as RunnableNode?)?.apply {
            tomlGet(table, "confirm")?.let { confirm = tomlTruthy(it, "confirm") }
            tomlGet(table, "warn-sh", "warning-sh")?.let {
                warningSh = it
                registerDynamicString(this, "warning", it)
            }
            if (warning.isEmpty()) {
                tomlGet(table, "warn", "warning")?.let { warning = it }
            }
            tomlGet(table, "auto-off", "auto-close")?.let { autoOff = tomlTruthy(it, "auto-close", "auto-off") }
            tomlGet(table, "auto-finish")?.let { autoFinish = tomlTruthy(it, "auto-finish") }
            tomlGet(table, "auto-kill")?.let { autoKill = tomlTruthy(it, "auto-kill") }
            tomlGet(table, "auto-restart")?.let { autoRestart = tomlTruthy(it, "auto-restart") }
            tomlGet(table, "interruptible", "interruptable")?.let {
                interruptable = it.isEmpty() || tomlTruthy(it, "interruptable")
            }
            tomlGet(table, "need-input", "needs-input", "require-input")?.let {
                needInput = it.isEmpty() || tomlTruthy(it, "need-input")
            }
            tomlGet(table, "reload-page")?.let { if (tomlTruthy(it, "reload-page")) reloadPage = true }
            tomlGet(table, "reload")?.let {
                if (tomlTruthy(it, "reload")) {
                    reloadPage = true
                } else if (it.isNotEmpty()) {
                    updateBlocks = it.split(",").map { s -> s.trim() }.dropLastWhile { s -> s.isEmpty() }.toTypedArray()
                }
            }
            tomlGet(table, "shell")?.let { shell = it }
            tomlGet(table, "bg-task", "background-task", "async-task")?.let {
                if (tomlTruthy(it, "async-task", "async", "bg-task", "background", "background-task")) {
                    shell = RunnableNode.shellModeBgTask
                }
            }
        }
    }

    private fun groupNodeToml(table: TomlTable): GroupNode {
        val group = GroupNode(pageConfigAbsPath)
        tomlGet(table, "key", "index", "id")?.let { group.key = it.trim() }
        tomlGet(table, "title-sh")?.let {
            group.titleSh = it
            registerDynamicString(group, "title", it)
        }
        if (group.title.isEmpty()) {
            tomlGet(table, "title")?.let { group.title = StringResRef.resolve(context, it) }
        }
        tomlGet(table, "support", "visible")?.let { group.supported = resolveBoolOrShell(it, "support", "visible") }
        return group
    }

    private fun pageNodeToml(table: TomlTable): PageNode? {
        val page = clickableNodeToml(PageNode(pageConfigAbsPath), table) as PageNode? ?: return null
        tomlGet(table, "config")?.let { page.pageConfigPath = it }
        tomlGet(table, "html")?.let { page.onlineHtmlPage = it }
        tomlGet(table, "before-load", "before-read")?.let { page.beforeRead = it }
        tomlGet(table, "after-load", "after-read")?.let { page.afterRead = it }
        tomlGet(table, "load-ok", "load-success")?.let { page.loadSuccess = it }
        tomlGet(table, "load-fail", "load-error")?.let { page.loadFail = it }
        tomlGet(table, "config-sh")?.let { page.pageConfigSh = it }
        tomlGet(table, "process")?.let { page.process = tomlTruthy(it, "process") }
        tomlGet(table, "link", "href")?.let { page.link = it }
        tomlGet(table, "activity", "a", "intent")?.let { page.activity = it }
        tomlGet(table, "lock", "lock-state")?.let { parseLockAttr(it, page) }
        tomlGet(table, "lock-sh")?.let { page.lockShell = it.trim() }

        // bên dưới.

        for (rowTable in tomlEntries(table, "rows")) {
            textRowToml(page.rows, rowTable)
        }

        resourceNodeToml(table)
        return page
    }

    private fun pageMenuOptionToml(table: TomlTable): PageMenuOption? {
        val option = runnableNodeToml(PageMenuOption(pageConfigAbsPath), table) as PageMenuOption? ?: return null
        tomlGet(table, "type")?.let { option.type = it }
        tomlGet(table, "style")?.let { option.isFab = it == "fab" }
        tomlGet(table, "suffix")?.let {
            val suffix = it.lowercase(getDefault()).trim()
            if (option.mime.isEmpty()) option.mime = Suffix2Mime().toMime(suffix)
            option.suffix = suffix
        }
        tomlGet(table, "mime")?.let { option.mime = it.lowercase(getDefault()) }
        tomlGet(table, "path-home", "home-path", "pathhome")?.let { option.pathHome = it.trim() }
        tomlGet(table, "multiple")?.let { option.multiple = tomlTruthy(it, "multiple") }
        tomlGet(table, "get", "getstate")?.let {
            when (option.type) {
                "checkbox" -> option.checkedSh = it
                "spinner" -> option.spinnerGetState = it
            }
        }
        tomlGet(table, "silent", "hidden")?.let { option.silent = it.isEmpty() || tomlTruthy(it, "silent", "hidden") }
        tomlGet(table, "link", "href")?.let { option.link = it }
        tomlGet(table, "activity", "a", "intent")?.let { option.activity = it }
        tomlGet(table, "html")?.let { option.onlineHtmlPage = it }
        tomlGet(table, "config")?.let { option.pageConfigPath = it }
        tomlGet(table, "config-sh")?.let { option.pageConfigSh = it }
        tomlGet(table, "script", "set", "setstate")?.let { option.script = it }
        tomlGet(table, "option-sh", "options-sh", "options-su")?.let {
            if (option.options == null) option.options = ArrayList()
            option.optionsSh = it
        }
        val spinnerOptionsToml = tomlEntries(table, "options")
        if (spinnerOptionsToml.isNotEmpty()) {
            if (option.options == null) option.options = ArrayList()
            for (optTable in spinnerOptionsToml) {
                option.options!!.add(selectItemToml(optTable))
            }
        }

        if (option.title.isEmpty()) {
            tomlGet(table, "title", "text")?.let { option.title = StringResRef.resolve(context, it) }
        }
        if (option.key.isEmpty()) option.key = option.title
        return option
    }

    private fun switchNodeToml(table: TomlTable): SwitchNode? {
        val switchNode = runnableNodeToml(SwitchNode(pageConfigAbsPath), table) as SwitchNode? ?: return null
        tomlGet(table, "get", "getstate")?.let { switchNode.getState = it }
        tomlGet(table, "set", "setstate")?.let { switchNode.setState = it }
        tomlGet(table, "lock", "lock-state")?.let { parseLockAttr(it, switchNode) }
        tomlGet(table, "lock-sh")?.let { switchNode.lockShell = it.trim() }
        resourceNodeToml(table)

        // readConfigToml() qua resolvePendingStates().
        switchNode.checked = false
        if (switchNode.getState.isNotEmpty()) {
            pendingSwitchStates.add(switchNode to switchNode.getState)
        }
        if (switchNode.setState == null) {
            switchNode.setState = ""
        }
        return switchNode
    }

    private fun selectItemToml(optTable: TomlTable): SelectItem {
        val item = SelectItem()
        tomlGet(optTable, "val", "value")?.let { item.value = it }
        tomlGet(optTable, "title-sh")?.let {
            item.titleSh = it
            registerDynamicString(item, "title", it)
        }
        if (item.title.isNullOrEmpty()) {
            tomlGet(optTable, "title", "text")?.let { item.title = StringResRef.resolve(context, it) }
        }
        if (item.value == null) item.value = item.title
        return item
    }

    private fun pickerNodeToml(table: TomlTable): PickerNode? {
        val picker = runnableNodeToml(PickerNode(pageConfigAbsPath), table) as PickerNode? ?: return null
        tomlGet(table, "option-sh", "options-sh", "options-su")?.let {
            if (picker.options == null) picker.options = ArrayList()
            picker.optionsSh = it
        }
        tomlGet(table, "multiple")?.let { picker.multiple = tomlTruthy(it, "multiple") }
        tomlGet(table, "separator")?.let { picker.separator = it }
        tomlGet(table, "get", "getstate")?.let { picker.getState = it }
        tomlGet(table, "set", "setstate")?.let { picker.setState = it }
        tomlGet(table, "lock", "lock-state")?.let { parseLockAttr(it, picker) }
        tomlGet(table, "lock-sh")?.let { picker.lockShell = it.trim() }

        val pickerOptions = tomlEntries(table, "options")
        if (pickerOptions.isNotEmpty()) {
            if (picker.options == null) picker.options = ArrayList()
            for (optTable in pickerOptions) {
                picker.options!!.add(selectItemToml(optTable))
            }
        }
        resourceNodeToml(table)

        if (picker.getState.isNullOrEmpty()) {
            picker.getState = ""
        } else {
            pendingPickerStates.add(picker to picker.getState!!)
        }
        if (picker.setState == null) picker.setState = ""
        return picker
    }

    private fun downloadNodeToml(table: TomlTable): DownloadNode? {
        val node = runnableNodeToml(DownloadNode(pageConfigAbsPath), table) as DownloadNode? ?: return null
        tomlGet(table, "url")?.let { node.url = it.trim() }
        tomlGet(table, "url-sh")?.let { node.urlSh = it.trim() }
        tomlGet(table, "script", "set", "setstate")?.let { node.setState = it.trim() }
        tomlGet(table, "lock", "lock-state")?.let { parseLockAttr(it, node) }
        tomlGet(table, "lock-sh")?.let { node.lockShell = it.trim() }
        if (node.setState == null) node.setState = ""
        // Cần ít nhất 1 trong 2: url tĩnh hoặc url-sh (chạy shell lúc bấm tải).
        if (node.url.isEmpty() && node.urlSh.isEmpty()) return null

        for (rowTable in tomlEntries(table, "rows")) {
            textRowToml(node.rows, rowTable)
        }

        return node
    }

    private fun actionNodeToml(table: TomlTable): ActionNode? {
        val action = runnableNodeToml(ActionNode(pageConfigAbsPath), table) as ActionNode? ?: return null
        tomlGet(table, "script", "set", "setstate")?.let { action.setState = it.trim() }
        tomlGet(table, "lock", "lock-state")?.let { parseLockAttr(it, action) }
        tomlGet(table, "lock-sh")?.let { action.lockShell = it.trim() }
        if (action.setState == null) action.setState = ""

        val paramTables = tomlEntries(table, "params")
        if (paramTables.isNotEmpty()) {
            val params = ArrayList<ActionParamInfo>()
            for (paramTable in paramTables) {
                actionParamToml(paramTable)?.let { params.add(it) }
            }
            action.params = params
        }

        for (rowTable in tomlEntries(table, "rows")) {
            textRowToml(action.rows, rowTable)
        }

        for (rowTable in tomlEntries(table, "params-rows")) {
            textRowToml(action.paramsRows, rowTable)
        }

        tomlGet(table, "menu")?.let { action.menu = tomlTruthy(it, "menu") }
        tomlGet(table, "show")?.let { action.show = resolveBoolOrShell(it, "show") }

        resourceNodeToml(table)
        return action
    }

    private fun actionParamToml(table: TomlTable): ActionParamInfo? {
        val p = ActionParamInfo()
        tomlGet(table, "name")?.let { p.name = it }
        tomlGet(table, "label")?.let { p.label = it }
        tomlGet(table, "label-sh")?.let { p.labelSh = it }
        tomlGet(table, "placeholder")?.let { p.placeholder = it }
        tomlGet(table, "placeholder-sh")?.let { p.placeholderSh = it }
        tomlGet(table, "title")?.let { p.title = it }
        tomlGet(table, "title-sh")?.let { p.titleSh = it }
        tomlGet(table, "desc")?.let { p.desc = it }
        tomlGet(table, "desc-sh")?.let { p.descSh = it }
        tomlGet(table, "desc-on", "on-desc", "desc-checked")?.let { p.descOn = it }
        tomlGet(table, "desc-on-sh", "on-desc-sh", "desc-checked-sh")?.let { p.descOnSh = it }
        tomlGet(table, "value")?.let { p.value = it }
        tomlGet(table, "type")?.let { p.type = it.lowercase(getDefault()).trim() }
        tomlGet(table, "suffix")?.let {
            val suffix = it.lowercase(getDefault()).trim()
            if (p.mime.isEmpty()) p.mime = Suffix2Mime().toMime(suffix)
            p.suffix = suffix
        }
        tomlGet(table, "mime")?.let { p.mime = it.lowercase(getDefault()) }
        tomlGet(table, "path-home", "home-path", "pathhome")?.let { p.pathHome = it.trim() }
        val readonlyRaw = tomlGet(table, "readonly")
        readonlyRaw?.let { raw ->
            val v = raw.trim()
            val lower = v.lowercase(getDefault())
            when {
                lower.isEmpty() -> p.readonly = false
                lower == "1" || lower == "true" || lower == "readonly" -> p.readonly = true
                lower == "0" || lower == "false" -> p.readonly = false
                else -> {
                    p.readonly = false
                    p.readonlySh = v
                }
            }
        }
        tomlGet(table, "sort")?.let { p.sort = (readonlyRaw != null) && tomlTruthy(it) }
        tomlGet(table, "maxlength")?.let { p.maxLength = it.trim().toIntOrNull() ?: p.maxLength }
        tomlGet(table, "min")?.let { p.min = it.trim().toIntOrNull() ?: p.min }
        tomlGet(table, "max")?.let { p.max = it.trim().toIntOrNull() ?: p.max }
        tomlGet(table, "required")?.let { p.required = tomlTruthy(it, "required") }
        tomlGet(table, "value-sh", "value-su")?.let { p.valueShell = it }
        tomlGet(table, "options-sh", "option-sh", "options-su")?.let {
            if (p.options == null) p.options = ArrayList()
            p.optionsSh = it
        }
        tomlGet(table, "support", "visible")?.let {
            if (!resolveBoolOrShell(it, "support", "visible")) p.supported = false
        }
        tomlGet(table, "multiple")?.let { p.multiple = tomlTruthy(it, "multiple") }
        tomlGet(table, "editable")?.let { p.editable = tomlTruthy(it, "editable") }
        tomlGet(table, "separator")?.let { p.separator = it }
        tomlGet(table, "depend-on", "depend")?.let { p.dependOn = it }
        tomlGet(table, "depend-value")?.let { p.dependValue = it }
        tomlGet(table, "depend-mode")?.let { p.dependMode = it }
        tomlGet(table, "depend-logic", "depend-priority")?.let { p.dependLogic = it }
        tomlGet(table, "depend-default")?.let { p.dependDefault = it }
        tomlGet(table, "depend-initial", "depend-initial-state")?.let { p.dependInitialState = it }
        tomlGet(table, "depend-negate")?.let { p.dependNegate = tomlTruthy(it, "negate") }
        tomlGet(table, "depend-threshold")?.let { p.dependThreshold = it.trim().toIntOrNull() ?: -1 }
        tomlGet(table, "depend-include-hidden")?.let { p.dependIncludeHidden = !(it == "false" || it == "0") }
        tomlGet(table, "depend-cascade")?.let { p.dependCascade = !(it == "false" || it == "0") }
        tomlGet(table, "depend-onchange", "depend-on-change", "depend-callback")?.let { p.dependOnChangeCallback = it }
        tomlGet(table, "depend-readonly")?.let { p.dependReadonly = tomlTruthy(it) }
        tomlGet(table, "depend-sort")?.let { p.dependSort = p.dependReadonly && tomlTruthy(it) }
        tomlGet(table, "allow-no-selection", "no-select")?.let { p.allowNoSelection = tomlTruthy(it) }

        val itemsArray = table.getArray("items")
        if (itemsArray != null) {
            if (p.options == null) p.options = ArrayList()
            for (i in 0 until itemsArray.size()) {
                val str = itemsArray.getString(i) ?: continue
                val parts = str.split("|", limit = 2)
                val item = SelectItem()
                item.value = parts[0].trim()
                item.title = if (parts.size > 1) StringResRef.resolve(context, parts[1].trim()) else parts[0].trim()
                p.options!!.add(item)
            }
        }

        val paramOptions = tomlEntries(table, "options")
        if (itemsArray == null && paramOptions.isNotEmpty()) {
            if (p.options == null) p.options = ArrayList()
            for (optTable in paramOptions) {
                p.options!!.add(selectItemToml(optTable))
            }
        }

        return if (p.supported && !p.name.isNullOrEmpty()) p else null
    }

    private fun textNodeToml(table: TomlTable): TextNode? {
        val text = mainNodeToml(TextNode(pageConfigAbsPath), table) as TextNode? ?: return null
        for (rowTable in tomlEntries(table, "rows")) {
            textRowToml(text.rows, rowTable)
        }
        resourceNodeToml(table)
        return text
    }

    private fun textRowToml(rows: ArrayList<TextNode.TextRow>, table: TomlTable) {
        val row = TextNode.TextRow()
        // khỏi danh sách sau khi resolve xong.
        var visibleShellScript: String? = null
        tomlGet(table, "support", "visible")?.let {
            val v = it.trim()
            val lower = v.lowercase(getDefault())
            when {
                lower.isEmpty() || lower == "1" || lower == "true" || lower == "support" || lower == "visible" -> {}
                lower == "0" || lower == "false" -> return
                else -> visibleShellScript = v
            }
        }

        tomlGet(table, "bold", "b")?.let { row.bold = tomlTruthy(it, "bold") }
        tomlGet(table, "italic", "i")?.let { row.italic = tomlTruthy(it, "italic") }
        tomlGet(table, "underline", "u")?.let { row.underline = tomlTruthy(it, "underline") }
        tomlGet(table, "strikethrough", "line-through", "delete-line", "del")?.let { row.strikethrough = tomlTruthy(it, "strikethrough", "line-through", "del") }
        tomlGet(table, "monospace", "mono", "code")?.let { row.monospace = tomlTruthy(it, "monospace", "mono", "code") }
        tomlGet(table, "letter-spacing", "letterspacing", "spacing")?.let { row.letterSpacing = it.trim().toFloatOrNull() ?: row.letterSpacing }
        tomlGet(table, "line-height", "lineheight", "row-height")?.let { row.lineHeight = it.trim().toFloatOrNull() ?: row.lineHeight }
        tomlGet(table, "margin-top", "spacing-top", "top-margin")?.let { row.marginTop = it.trim().toIntOrNull() ?: row.marginTop }
        tomlGet(table, "margin-bottom", "spacing-bottom", "bottom-margin")?.let { row.marginBottom = it.trim().toIntOrNull() ?: row.marginBottom }
        tomlGet(table, "alpha", "opacity")?.let {
            val v = it.trim().toFloatOrNull()
            if (v != null) {
                row.alpha = (if (v > 1f) v / 255f else v).coerceIn(0f, 1f)
            }
        }
        tomlGet(table, "foreground", "color")?.let { try { row.color = it.toColorInt() } catch (_: Exception) {} }
        tomlGet(table, "bg", "background", "bgcolor")?.let { try { row.bgColor = it.toColorInt() } catch (_: Exception) {} }
        tomlGet(table, "size")?.let { row.size = it.trim().toIntOrNull() ?: row.size }
        tomlGet(table, "break")?.let { row.breakRow = tomlTruthy(it, "break") }
        tomlGet(table, "line", "divider", "separator")?.let { row.line = tomlTruthy(it, "line", "divider", "separator") }
        tomlGet(table, "link", "href")?.let { row.link = it }
        tomlGet(table, "activity", "a", "intent")?.let { row.activity = it }
        tomlGet(table, "photo", "photo-path")?.let { row.photo = it.trim() }
        tomlGet(table, "photo-sh")?.let { row.photoSh = it.trim() }
        tomlGet(table, "photo-real-size", "photo-original-size")?.let { row.photoRealSize = tomlTruthy(it, "real-size", "original-size") }
        tomlGet(table, "photo-gif-num", "gif-num", "gif_num")?.let { row.photoGifNum = it.trim().toIntOrNull() ?: row.photoGifNum }
        tomlGet(table, "photo-gif-time", "gif-time", "gif_time")?.let { row.photoGifTime = it.trim().toIntOrNull() ?: row.photoGifTime }
        tomlGet(table, "photo-gif-autoplay", "gif-autoplay", "gif_autoplay")?.let { row.photoGifAutoplay = tomlTruthy(it) }
        tomlGet(table, "photo-gif-loop", "photo-gif-loop-count", "gif-loop", "gif-loop-count", "gif_loop_count")?.let { row.photoGifLoopCount = it.trim().toIntOrNull() ?: row.photoGifLoopCount }
        tomlGet(table, "icon", "icon-path")?.let { row.icon = it.trim() }
        tomlGet(table, "icon-sh")?.let { row.iconSh = it.trim() }
        tomlGet(table, "icon-position", "icon-pos")?.let {
            val p = it.trim().lowercase(getDefault())
            row.iconPosition = when (p) {
                "after", "end", "right" -> "after"
                else -> "before"
            }
        }
        tomlGet(table, "icon-size")?.let { row.iconSize = it.trim().toIntOrNull() ?: row.iconSize }
        tomlGet(table, "script", "run")?.let { row.onClickScript = it }
        tomlGet(table, "sh", "text-sh")?.let { row.dynamicTextSh = it }
        // Toggle nhỏ (checkbox / switch) lồng trong dòng text
        tomlGet(table, "toggle", "toggle-type")?.let {
            val t = it.trim().lowercase(getDefault())
            if (t == "checkbox" || t == "switch") row.toggle = t
        }
        tomlGet(table, "get", "getstate")?.let {
            val v = it.trim()
            val lower = v.lowercase(getDefault())
            row.checked = when {
                lower.isEmpty() -> false
                lower == "1" || lower == "true" -> true
                lower == "0" || lower == "false" -> false
                else -> {
                    pendingRowCheckedStates.add(row to v)
                    false
                }
            }
        }
        tomlGet(table, "set", "setstate")?.let { row.onChangeSh = it }
        tomlGet(table, "align")?.let {
            when (it) {
                "opposite" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) row.align = Layout.Alignment.ALIGN_OPPOSITE
                "center" -> row.align = Layout.Alignment.ALIGN_CENTER
                "normal" -> row.align = Layout.Alignment.ALIGN_NORMAL
            }
        }
        tomlGet(table, "text")?.let { row.text = StringResRef.resolve(context, it) }
        rows.add(row)
        visibleShellScript?.let { pendingRowVisibleStates.add(Triple(rows, row, it)) }
    }

    private fun editorNodeToml(table: TomlTable): EditorNode? {
        val editor = clickableNodeToml(EditorNode(pageConfigAbsPath), table) as EditorNode? ?: return null
        tomlGet(table, "file", "path")?.let { editor.file = it.trim() }
        tomlGet(table, "wrap")?.let { editor.wrap = !(it == "0" || it == "false" || it == "off" || it == "no-wrap") }
        tomlGet(table, "placeholder")?.let { editor.placeholder = it }
        tomlGet(table, "readonly")?.let { editor.readonly = resolveBoolOrShell(it) }
        tomlGet(table, "need-input")?.let { editor.needInput = (it == "true" || it == "1") }
        tomlGet(table, "value-sh")?.let { editor.valueSh = it }
        tomlGet(table, "value")?.let { editor.value = it }
        return editor
    }

    private fun resourceNodeToml(table: TomlTable) {
        tomlGet(table, "resource-file")?.let { ExtractAssets(context).extractResource(it.trim()) }
        tomlGet(table, "resource-dir")?.let { ExtractAssets(context).extractResources(it.trim()) }
        for (resTable in tomlEntries(table, "resources")) {
            tomlGet(resTable, "file")?.let { ExtractAssets(context).extractResource(it.trim()) }
            tomlGet(resTable, "dir")?.let { ExtractAssets(context).extractResources(it.trim()) }
        }
    }
}

