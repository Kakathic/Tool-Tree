package com.omarea.krscript.config

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.util.Log
import android.widget.Toast
import com.omarea.common.model.SelectItem
import com.omarea.krscript.executor.ExtractAssets
import com.omarea.krscript.executor.ScriptEnvironmen
import com.tool.tree.R
import com.omarea.krscript.model.*
import java.io.File
import java.io.InputStream
import java.util.Locale.getDefault
import androidx.core.graphics.toColorInt
import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlTable

class PageConfigReader {
    private var context: Context
    private var pageConfig: String = ""

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

    private fun applyBuiltinVars(rawText: String): String {
        val rooted = ScriptEnvironmen.isRooted()
        return rawText
            .replace("{ROT}", if (rooted) "1" else "0")
            .replace("{LOT}", if (rooted) "0" else "1")
            .replace("{TEXT_ROOT}", if (rooted) "" else "ROOT")
    }

    private fun readConfigXml(fileInputStream: InputStream, onNodeReady: ((NodeInfoBase?, Int, Int) -> Unit)? = null): ArrayList<NodeInfoBase>? {
        return try {
            val rawText = fileInputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            readConfigToml(applyLoadKey(applyBuiltinVars(rawText)), onNodeReady)
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

    // Giá trị directive dùng chung: """...""" '''...''' "..." (có \" thoát) '...'
    private val directiveValuePattern = """(-sh)?[ \t]*=[ \t]*(?:\"\"\"([\s\S]*?)\"\"\"|'''([\s\S]*?)'''|"((?:[^"\\]|\\.)*)"|'([^']*)')[ \t]*\r?\n?"""
    private val loadKeyDirectiveRegex = Regex("""(?m)^[ \t]*load-key""" + directiveValuePattern)
    private val loadTomlDirectiveRegex = Regex("""(?m)^[ \t]*load-toml""" + directiveValuePattern)
    private val loadTextDirectiveRegex = Regex("""(?m)^[ \t]*load-text""" + directiveValuePattern)
    private val langLineRegex = Regex("""(?m)^[ \t]*([A-Za-z_][A-Za-z0-9_]*)[ \t]*=[ \t]*"((?:[^"\\]|\\.)*)"[ \t]*$""")
    private val escapeSeqRegex = Regex("""\\(.)""")

    private fun unescapeTomlBasic(value: String): String {
        return escapeSeqRegex.replace(value) { m ->
            when (val c = m.groupValues[1]) {
                "n" -> "\n"
                "t" -> "\t"
                "r" -> "\r"
                else -> c
            }
        }
    }

    private fun readTomlFileText(path: String): String {
        if (path.isEmpty()) return ""
        return try {
            PathAnalysis(context, parentDir).parsePath(path)
                ?.use { it.bufferedReader(Charsets.UTF_8).readText() }
                .orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    private fun prepareTomlPiece(piece: String): String {
        val trimmed = piece.trim()
        if (trimmed.isEmpty() || trimmed == "error") return ""
        val cleaned = loadTextDirectiveRegex.replace(loadTomlDirectiveRegex.replace(loadKeyDirectiveRegex.replace(trimmed, ""), ""), "")
        return applyBuiltinVars(cleaned).trimEnd() + "\n"
    }

    private fun textDirectiveValue(m: MatchResult): String {
        return when {
            m.groups[2] != null -> m.groupValues[2]
            m.groups[3] != null -> m.groupValues[3]
            m.groups[4] != null -> unescapeTomlBasic(m.groupValues[4])
            else -> m.groupValues[5]
        }
    }

    private val langLineSingleRegex = Regex("""^[ \t]*([A-Za-z_][A-Za-z0-9_]*)[ \t]*=[ \t]*'([^']*)'[ \t]*$""")
    private val langLineRawRegex = Regex("""^[ \t]*([A-Za-z_][A-Za-z0-9_]*)[ \t]*=[ \t]*([^"'\s][^\r\n]*?|)[ \t]*$""")

    // Luôn nhận name = "value" (có escape \n \t \r) và name = 'value'; lenient=true (load-text) nhận thêm name=value (không nháy)
    private fun parseKeyLines(text: String, into: MutableMap<String, String>, lenient: Boolean = false) {
        for (rawLine in text.split('\n')) {
            val line = rawLine.trimEnd('\r')
            val quoted = langLineRegex.matchEntire(line)
            if (quoted != null) {
                into[quoted.groupValues[1]] = unescapeTomlBasic(quoted.groupValues[2])
                continue
            }
            val single = langLineSingleRegex.matchEntire(line)
            if (single != null) {
                into[single.groupValues[1]] = single.groupValues[2]
                continue
            }
            if (!lenient) continue
            val raw = langLineRawRegex.matchEntire(line)
            if (raw != null) into[raw.groupValues[1]] = raw.groupValues[2]
        }
    }

    private fun applyLoadKey(rawText: String): String {
        val keyMatch = loadKeyDirectiveRegex.find(rawText)
        val tomlMatches = loadTomlDirectiveRegex.findAll(rawText).toList()
        val textMatches = loadTextDirectiveRegex.findAll(rawText).toList()
        if (keyMatch == null && tomlMatches.isEmpty() && textMatches.isEmpty()) return rawText

        val keyIsShell = keyMatch?.groupValues?.get(1) == "-sh"
        val keyValue = keyMatch?.let { textDirectiveValue(it).trim() }.orEmpty()
        val tomlValues = tomlMatches.map { textDirectiveValue(it).trim() }
        val textValues = textMatches.map { textDirectiveValue(it) }

        // Thứ tự chạy: load-toml-sh -> load-key-sh -> load-text-sh
        val scripts = LinkedHashMap<String, String>()
        tomlMatches.forEachIndexed { index, m ->
            if (m.groupValues[1] == "-sh" && tomlValues[index].isNotEmpty()) scripts["toml:$index"] = tomlValues[index]
        }
        if (keyIsShell && keyValue.isNotEmpty()) scripts["key"] = keyValue
        textMatches.forEachIndexed { index, m ->
            if (m.groupValues[1] == "-sh" && textValues[index].isNotBlank()) scripts["text:$index"] = textValues[index].trim()
        }
        if (vitualRootNode == null) {
            vitualRootNode = NodeInfoBase(pageConfigAbsPath)
        }
        val shellResults = ScriptEnvironmen.executeMultipleResultRoot(context, scripts, vitualRootNode)

        val patches = ArrayList<Pair<IntRange, String>>()
        loadKeyDirectiveRegex.findAll(rawText).forEach { patches.add(it.range to "") }
        textMatches.forEach { patches.add(it.range to "") }
        tomlMatches.forEachIndexed { index, m ->
            val piece = if (m.groupValues[1] == "-sh") shellResults["toml:$index"].orEmpty() else readTomlFileText(tomlValues[index])
            patches.add(m.range to prepareTomlPiece(piece))
        }
        val builder = StringBuilder()
        var cursor = 0
        for ((range, replacement) in patches.sortedBy { it.first.first }) {
            builder.append(rawText, cursor, range.first)
            builder.append(replacement)
            cursor = range.last + 1
        }
        builder.append(rawText, cursor, rawText.length)
        val merged = builder.toString()

        val keyMap = LinkedHashMap<String, String>()

        val keyPath = (if (keyIsShell) shellResults["key"].orEmpty() else keyValue).trim()
        if (keyPath.isNotEmpty() && keyPath != "error") {
            try {
                PathAnalysis(context, parentDir).parsePath(keyPath)
                    ?.use { it.bufferedReader(Charsets.UTF_8).readText() }
                    ?.let { parseKeyLines(it, keyMap) }
            } catch (_: Exception) {
            }
        }

        // load-text xử lý sau cùng: ghi đè key của load-key, dòng sau ghi đè dòng trước
        textMatches.forEachIndexed { index, m ->
            val text = if (m.groupValues[1] == "-sh") shellResults["text:$index"].orEmpty() else textValues[index]
            if (text.isNotEmpty()) parseKeyLines(text, keyMap, lenient = true)
        }
        if (keyMap.isEmpty()) return merged

        val keyRefRegex = Regex("@(" + keyMap.keys.joinToString("|") { Regex.escape(it) } + ")\\b")
        return keyRefRegex.replace(merged) { m ->
            keyMap[m.groupValues[1]]
                ?.replace("\\", "\\\\")
                ?.replace("\"", "\\\"")
                ?.replace("\r", "\\r")
                ?.replace("\n", "\\n")
                ?.replace("\t", "\\t")
                ?: m.value
        }
    }

    private val pendingSwitchStates = ArrayList<Pair<SwitchNode, String>>()
    private val pendingPickerStates = ArrayList<Pair<PickerNode, String>>()
    private val pendingRowCheckedStates = ArrayList<Pair<TextNode.TextRow, String>>()
    private val pendingRowVisibleStates = ArrayList<Triple<ArrayList<TextNode.TextRow>, TextNode.TextRow, String>>()
    private val pendingCheckboxStates = ArrayList<Pair<PageMenuOption, String>>()

    private val pendingDynamicStrings = ArrayList<Triple<Any, String, String>>()

    private fun registerDynamicString(target: Any, fieldKey: String, script: String) {
        pendingDynamicStrings.add(Triple(target, fieldKey, script))
    }

    private val pendingBoolShells = ArrayList<Triple<NodeInfoBase?, String, String>>()

    private fun resolvePendingStates() {
        if (pendingSwitchStates.isEmpty() && pendingPickerStates.isEmpty() &&
            pendingRowCheckedStates.isEmpty() && pendingRowVisibleStates.isEmpty() &&
            pendingDynamicStrings.isEmpty() && pendingBoolShells.isEmpty() &&
            pendingCheckboxStates.isEmpty()) return

        val scripts = LinkedHashMap<String, String>()
        pendingSwitchStates.forEachIndexed { index, pair -> scripts["switch:$index"] = pair.second }
        pendingPickerStates.forEachIndexed { index, pair -> scripts["picker:$index"] = pair.second }
        pendingRowCheckedStates.forEachIndexed { index, pair -> scripts["row-checked:$index"] = pair.second }
        pendingRowVisibleStates.forEachIndexed { index, triple -> scripts["row-visible:$index"] = triple.third }
        pendingDynamicStrings.forEachIndexed { index, triple -> scripts["dynstr:$index"] = triple.third }
        pendingBoolShells.forEachIndexed { index, triple -> scripts["boolsh:$index"] = triple.third }
        pendingCheckboxStates.forEachIndexed { index, pair -> scripts["checkbox:$index"] = pair.second }

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
        val dynamicParamResults = ArrayList<Pair<ActionNode, String>>()
        val dynamicRowResults = ArrayList<Pair<ArrayList<TextNode.TextRow>, String>>()
        pendingDynamicStrings.forEachIndexed { index, triple ->
            val shellResult = results["dynstr:$index"] ?: ""
            if (shellResult != "error") {
                val (target, fieldKey, _) = triple
                when {
                    target is RunnableNode && fieldKey == "warning" -> target.warning = shellResult
                    target is ActionNode && fieldKey == "params" -> dynamicParamResults.add(target to shellResult)
                    target is ArrayList<*> && fieldKey == "rows" -> {
                        @Suppress("UNCHECKED_CAST")
                        dynamicRowResults.add((target as ArrayList<TextNode.TextRow>) to shellResult)
                    }
                    target is ClickableNode -> when (fieldKey) {
                        "title" -> target.title = shellResult
                        "desc" -> target.desc = shellResult
                        "summary" -> target.summary = shellResult
                        "icon" -> target.iconPath = shellResult
                        "photo" -> target.photoPath = shellResult
                        "bg" -> target.bgPath = shellResult
                        "file" -> if (target is EditorNode && shellResult.isNotBlank()) target.file = shellResult.trim()
                    }
                    target is NodeInfoBase -> when (fieldKey) {
                        "title" -> target.title = shellResult
                        "desc" -> target.desc = shellResult
                        "summary" -> target.summary = shellResult
                    }
                    target is SelectItem && fieldKey == "title" -> target.title = shellResult
                }
            }
        }
        pendingBoolShells.forEachIndexed { index, triple ->
            val shellResult = results["boolsh:$index"] ?: ""
            val result = shellResult.trim() == "1"
            val (target, fieldKey, _) = triple
            if (fieldKey == "bool-support" && target != null) {
                if (!result) (target as GroupNode).supported = false
            }
        }
        pendingCheckboxStates.forEachIndexed { index, pair ->
            val shellResult = results["checkbox:$index"] ?: ""
            pair.first.checked = shellResult != "error" && (shellResult == "1" || shellResult.lowercase(getDefault()) == "true")
        }

        pendingSwitchStates.clear()
        pendingPickerStates.clear()
        pendingRowCheckedStates.clear()
        pendingRowVisibleStates.clear()
        pendingDynamicStrings.clear()
        pendingBoolShells.clear()
        pendingCheckboxStates.clear()

        if (dynamicParamResults.isNotEmpty() || dynamicRowResults.isNotEmpty()) {
            dynamicParamResults.forEach { (action, text) -> applyDynamicParams(action, text) }
            dynamicRowResults.forEach { (rows, text) -> applyDynamicRows(rows, text) }
            resolvePendingStates()
        }
    }

    private fun resolvePathPlaceholders(path: String): String {
        var result = path
        if (result.contains("{HOME}")) {
            result = result.replace("{HOME}", File(context.filesDir, "home").absolutePath)
        }
        return result
    }

    private fun rowsShToml(table: TomlTable, rows: ArrayList<TextNode.TextRow>) {
        tomlGet(table, "rows-sh")?.let {
            val script = it.trim()
            if (script.isNotEmpty()) registerDynamicString(rows, "rows", script)
        }
    }

    private fun applyDynamicRows(rows: ArrayList<TextNode.TextRow>, rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty()) return
        try {
            val result = Toml.parse(text)
            if (result.hasErrors()) {
                val message = result.errors().joinToString("\n") { it.toString() }
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "rows-sh: failed to parse toml\n$message", Toast.LENGTH_LONG).show()
                }
                Log.e("KrConfig rows-sh", message)
                return
            }
            val tables = ArrayList<TomlTable>()
            tables.addAll(tomlEntries(result, "rows"))
            for (nodeKey in listOf("text", "action", "page", "switch", "picker", "download", "editor")) {
                tomlEntries(result, nodeKey).forEach { tables.addAll(tomlEntries(it, "rows")) }
            }
            if (tables.isEmpty()) return
            val old = ArrayList(rows)
            rows.clear()
            for (table in tables) textRowToml(rows, table)
            if (rows.isEmpty()) rows.addAll(old)
        } catch (ex: Exception) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "rows-sh: " + ex.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun applyDynamicParams(action: ActionNode, rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty()) return
        try {
            val result = Toml.parse(text)
            if (result.hasErrors()) {
                val message = result.errors().joinToString("\n") { it.toString() }
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "params-sh: failed to parse toml\n$message", Toast.LENGTH_LONG).show()
                }
                Log.e("KrConfig params-sh", message)
                return
            }
            val tables = ArrayList<TomlTable>()
            tables.addAll(tomlEntries(result, "params"))
            tomlEntries(result, "action").forEach { tables.addAll(tomlEntries(it, "params")) }
            val params = ArrayList<ActionParamInfo>()
            for (table in tables) {
                actionParamToml(table)?.let { params.add(it) }
            }
            if (params.isNotEmpty()) action.params = params
        } catch (ex: Exception) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "params-sh: " + ex.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private val tomlNodeTypeOrder = listOf("group", "end", "text", "switch", "picker", "action", "page", "download", "editor", "terminal", "chat", "resource", "menu", "fab")

    private val collectedMenuOptions = ArrayList<PageMenuOption>()

    val pageMenuOptions: ArrayList<PageMenuOption> get() = collectedMenuOptions

    private val usedMenuOptionKeys = HashMap<String, Int>()

    private val collectedHeaderActions = ArrayList<ActionNode>()
    val headerActions: ArrayList<ActionNode> get() = collectedHeaderActions

    private val collectedAutoShowActions = ArrayList<ActionNode>()
    val autoShowActions: ArrayList<ActionNode> get() = collectedAutoShowActions

    private var collectedMenuIcon: ClickableNode? = null
    private var collectedFabIcon: ClickableNode? = null
    val menuIcon: ClickableNode? get() = collectedMenuIcon
    val fabIcon: ClickableNode? get() = collectedFabIcon

    private class DeferredEntry(val type: String, val table: TomlTable, val group: GroupNode?, val insertIndex: Int)
    private val deferredEntries = ArrayList<DeferredEntry>()

    val hasDeferredEntries: Boolean get() = deferredEntries.isNotEmpty()

    class DeferredNodeResult(val group: GroupNode?, val node: NodeInfoBase, val index: Int)

    private fun isLoadAfter(table: TomlTable): Boolean = tomlTruthy(tomlGet(table, *TomlKeys.LOAD_AFTER))

    private fun menuGroupOptionsToml(table: TomlTable, isFab: Boolean): ArrayList<PageMenuOption> {
        val handler = tomlGet(table, "handler", "handler-sh").orEmpty()
        tomlGet(table, *TomlKeys.ICON)?.trim()?.takeIf { it.isNotEmpty() }?.let { path ->
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
            assignUniqueMenuOptionKey(option, isFab)
            if (option.type == "checkbox" && option.checkedSh.isNotEmpty()) {
                pendingCheckboxStates.add(option to option.checkedSh)
            }
            result.add(option)
        }
        return result
    }

    private fun assignUniqueMenuOptionKey(option: PageMenuOption, isFab: Boolean) {
        if (option.key.isNotEmpty()) return
        val prefix = if (isFab) "fab" else "menu"
        val baseKey = option.title.ifEmpty { prefix }
        val seen = usedMenuOptionKeys[baseKey] ?: 0
        usedMenuOptionKeys[baseKey] = seen + 1
        option.key = if (seen == 0) baseKey else "$baseKey#$prefix${seen + 1}"
    }

    private fun tomlPipeList(table: TomlTable, key: String): List<String> {
        val raw = tomlGet(table, key) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split("|").map { it.trim() }
    }

    private fun parseIntOr(raw: String, fallback: Int): Int = raw.trim().toIntOrNull() ?: fallback

    private fun parseColorOr(raw: String, fallback: Int): Int = try {
        raw.toColorInt()
    } catch (_: Exception) {
        fallback
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

    private fun parseLockAttr(raw: String?, node: ClickableNode) {
        if (raw == null) return
        val v = raw.trim()
        val pipeIndex = v.indexOf('|')
        if (pipeIndex >= 0) {
            val state = v.substring(0, pipeIndex).trim()
            val message = v.substring(pipeIndex + 1).trim()
            node.locked = (state == "1")
            node.lockMessage = StringResRef.resolve(context, message)
        } else {
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

    // items = ["v|Title"] hoặc options = ["v|Title"] (mảng chuỗi) -> cùng một danh sách lựa chọn
    private fun itemsOrStringOptions(table: TomlTable): TomlArray? {
        table.getArray("items")?.let { return it }
        if (!table.isArray("options")) return null
        val arr = table.getArray("options") ?: return null
        if (arr.size() == 0) return null
        for (i in 0 until arr.size()) {
            if (arr.get(i) !is String) return null
        }
        return arr
    }

    private fun tomlEntries(parent: TomlTable, key: String): List<TomlTable> {
        return when {
            parent.isArray(key) -> {
                val arr = parent.getArray(key) ?: return emptyList()
                (0 until arr.size()).mapNotNull { if (arr.get(it) is TomlTable) arr.getTable(it) else null }
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
        var currentGroup: GroupNode? = null
        var groupHidden = false

        fun closeCurrentGroup(doneNow: Int) {
            currentGroup?.let { onNodeReady?.invoke(it, doneNow, total) }
        }

        for (entry in sortedEntries) {
            if (entry.type == "end") {
                closeCurrentGroup(done)
                done++
                currentGroup = null
                groupHidden = false
                onNodeReady?.invoke(null, done, total)
                continue
            }
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
            "terminal" -> terminalNodeToml(table)
            "chat" -> chatNodeToml(table)
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
        tomlGet(table, *TomlKeys.SUPPORT)?.let {
            if (!resolveBoolOrShell(it, "support", "visible")) return null
        }
        tomlGet(table, *TomlKeys.KEY)?.let { nodeInfoBase.key = it.trim() }
        tomlGet(table, *TomlKeys.TITLE_SH)?.let {
            nodeInfoBase.titleSh = it
            registerDynamicString(nodeInfoBase, "title", it)
        }
        if (nodeInfoBase.title.isEmpty()) {
            tomlGet(table, "title")?.let { nodeInfoBase.title = StringResRef.resolve(context, it) }
        }
        tomlGet(table, *TomlKeys.DESC_SH)?.let {
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
        tomlGet(table, *TomlKeys.LOAD_AFTER)?.let { nodeInfoBase.loadAfter = tomlTruthy(it) }
        return nodeInfoBase
    }

    private fun clickableNodeToml(node: ClickableNode, table: TomlTable): ClickableNode? {
        return (mainNodeToml(node, table) as ClickableNode?)?.apply {
            tomlGet(table, *TomlKeys.LOCK)?.let { parseLockAttr(it, this) }
            tomlGet(table, *TomlKeys.LOCK_SH)?.let { lockShell = it.trim() }
            tomlGet(table, "min-sdk", "sdk-min")?.let { minSdkVersion = parseIntOr(it, minSdkVersion) }
            tomlGet(table, "max-sdk", "sdk-max")?.let { maxSdkVersion = parseIntOr(it, maxSdkVersion) }
            tomlGet(table, "target-sdk", "sdk-target")?.let { targetSdkVersion = parseIntOr(it, targetSdkVersion) }
            tomlGet(table, *TomlKeys.ICON)?.let { iconPath = it.trim() }
            tomlGet(table, *TomlKeys.ICON_SH)?.let {
                iconSh = it.trim()
                registerDynamicString(this, "icon", iconSh)
            }
            tomlGet(table, *TomlKeys.ICON_GIF_NUM)?.let { iconGifNum = parseIntOr(it, iconGifNum) }
            tomlGet(table, *TomlKeys.ICON_GIF_TIME)?.let { iconGifTime = parseIntOr(it, iconGifTime) }
            tomlGet(table, *TomlKeys.ICON_GIF_AUTOPLAY)?.let { iconGifAutoplay = tomlTruthy(it) }
            tomlGet(table, *TomlKeys.ICON_GIF_LOOP)?.let { iconGifLoopCount = parseIntOr(it, iconGifLoopCount) }
            tomlGet(table, *TomlKeys.ICON_REAL_GIF)?.let { iconRealGif = tomlTruthy(it, "real-gif", "gif-real") }
            tomlGet(table, "logo", "logo-path")?.let { logoPath = it.trim() }
            tomlGet(table, *TomlKeys.PHOTO)?.let { photoPath = it.trim() }
            tomlGet(table, *TomlKeys.PHOTO_SH)?.let {
                photoSh = it.trim()
                registerDynamicString(this, "photo", photoSh)
            }
            tomlGet(table, *TomlKeys.PHOTO_REAL_SIZE)?.let { photoRealSize = tomlTruthy(it, "real-size", "original-size") }
            tomlGet(table, *TomlKeys.PHOTO_GIF_NUM)?.let { photoGifNum = parseIntOr(it, photoGifNum) }
            tomlGet(table, *TomlKeys.PHOTO_GIF_TIME)?.let { photoGifTime = parseIntOr(it, photoGifTime) }
            tomlGet(table, *TomlKeys.PHOTO_GIF_AUTOPLAY)?.let { photoGifAutoplay = tomlTruthy(it) }
            tomlGet(table, *TomlKeys.PHOTO_GIF_LOOP)?.let { photoGifLoopCount = parseIntOr(it, photoGifLoopCount) }
            tomlGet(table, *TomlKeys.PHOTO_REAL_GIF)?.let { photoRealGif = tomlTruthy(it, "real-gif", "gif-real") }
            tomlGet(table, *TomlKeys.BG_IMAGE_NODE)?.let { bgPath = it.trim() }
            tomlGet(table, *TomlKeys.BG_IMAGE_SH_NODE)?.let {
                bgSh = it.trim()
                registerDynamicString(this, "bg", bgSh)
            }
            tomlGet(table, *TomlKeys.BG_SCALE)?.let { bgScale = it.trim().lowercase(getDefault()) }
            tomlGet(table, "allow-shortcut")?.let { allowShortcut = tomlTruthy(it, "allow", "allow-shortcut") }
            if (key.isNotEmpty() && key.startsWith("@") && allowShortcut == null) {
                allowShortcut = false
            }
        }
    }

    private fun runnableNodeToml(node: RunnableNode, table: TomlTable): RunnableNode? {
        return (clickableNodeToml(node, table) as RunnableNode?)?.apply {
            tomlGet(table, "confirm")?.let {
                val v = it.trim().lowercase(getDefault())
                when {
                    v == "true" || v == "1" || v == "yes" || v == "on" -> confirm = true
                    v == "false" || v == "0" || v == "no" || v == "off" || v.isEmpty() -> confirm = false
                    else -> {
                        confirm = true
                        confirmText = StringResRef.resolve(context, it.trim())
                    }
                }
            }
            tomlGet(table, *TomlKeys.CONFIRM_TEXT)?.let {
                confirm = true
                confirmText = StringResRef.resolve(context, it.trim())
            }
            tomlGet(table, "warn-sh", "warning-sh")?.let {
                warningSh = it
                registerDynamicString(this, "warning", it)
            }
            if (warning.isEmpty()) {
                tomlGet(table, "warn", "warning")?.let { warning = StringResRef.resolve(context, it) }
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
        tomlGet(table, *TomlKeys.KEY)?.let { group.key = it.trim() }
        tomlGet(table, *TomlKeys.TITLE_SH)?.let {
            group.titleSh = it
            registerDynamicString(group, "title", it)
        }
        if (group.title.isEmpty()) {
            tomlGet(table, "title")?.let { group.title = StringResRef.resolve(context, it) }
        }
        tomlGet(table, *TomlKeys.SUPPORT)?.let { group.supported = resolveBoolOrShell(it, "support", "visible") }
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
        tomlGet(table, *TomlKeys.CONFIG_SH)?.let { page.pageConfigSh = it }
        tomlGet(table, "process")?.let { page.process = tomlTruthy(it, "process") }
        tomlGet(table, "placeholder-count", "placeholder_count")?.let { page.placeholderCount = parseIntOr(it, page.placeholderCount) }
        tomlGet(table, *TomlKeys.LINK)?.let { page.link = it }
        tomlGet(table, *TomlKeys.INTENT)?.let { page.activity = it }
        tomlGet(table, *TomlKeys.LOCK)?.let { parseLockAttr(it, page) }
        tomlGet(table, *TomlKeys.LOCK_SH)?.let { page.lockShell = it.trim() }

        for (rowTable in tomlEntries(table, "rows")) {
            textRowToml(page.rows, rowTable)
        }
        rowsShToml(table, page.rows)

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
        tomlGet(table, *TomlKeys.PATH_HOME)?.let { option.pathHome = resolvePathPlaceholders(it.trim()) }
        tomlGet(table, "multiple")?.let { option.multiple = tomlTruthy(it, "multiple") }
        tomlGet(table, *TomlKeys.GET)?.let {
            when (option.type) {
                "checkbox" -> option.checkedSh = it
                "spinner" -> option.spinnerGetState = it
            }
        }
        tomlGet(table, "silent", "hidden")?.let { option.silent = it.isEmpty() || tomlTruthy(it, "silent", "hidden") }
        tomlGet(table, "fab-spin")?.let { option.fabSpin = tomlTruthy(it, "fab-spin") }
        tomlGet(table, *TomlKeys.LINK)?.let { option.link = it }
        tomlGet(table, *TomlKeys.INTENT)?.let { option.activity = it }
        tomlGet(table, "html")?.let { option.onlineHtmlPage = it }
        tomlGet(table, "config")?.let { option.pageConfigPath = it }
        tomlGet(table, *TomlKeys.CONFIG_SH)?.let { option.pageConfigSh = it }
        tomlGet(table, *TomlKeys.SCRIPT)?.let { option.script = it }
        tomlGet(table, *TomlKeys.OPTION_SH)?.let {
            if (option.options == null) option.options = ArrayList()
            option.optionsSh = it
        }
        val spinnerItemsArray = itemsOrStringOptions(table)
        if (spinnerItemsArray != null) {
            if (option.options == null) option.options = ArrayList()
            for (i in 0 until spinnerItemsArray.size()) {
                val str = spinnerItemsArray.getString(i) ?: continue
                val parts = str.split("|", limit = 2)
                val item = SelectItem()
                item.value = parts[0].trim()
                item.title = if (parts.size > 1) StringResRef.resolve(context, parts[1].trim()) else parts[0].trim()
                option.options!!.add(item)
            }
        }
        val spinnerOptionsToml = tomlEntries(table, "options")
        if (spinnerItemsArray == null && spinnerOptionsToml.isNotEmpty()) {
            if (option.options == null) option.options = ArrayList()
            for (optTable in spinnerOptionsToml) {
                option.options!!.add(selectItemToml(optTable))
            }
        }

        if (option.title.isEmpty()) {
            tomlGet(table, *TomlKeys.TITLE_TEXT)?.let { option.title = StringResRef.resolve(context, it) }
        }
        return option
    }

    private fun switchNodeToml(table: TomlTable): SwitchNode? {
        val switchNode = runnableNodeToml(SwitchNode(pageConfigAbsPath), table) as SwitchNode? ?: return null
        tomlGet(table, *TomlKeys.GET)?.let { switchNode.getState = it }
        tomlGet(table, *TomlKeys.SET)?.let { switchNode.setState = it }
        tomlGet(table, *TomlKeys.LOCK)?.let { parseLockAttr(it, switchNode) }
        tomlGet(table, *TomlKeys.LOCK_SH)?.let { switchNode.lockShell = it.trim() }
        resourceNodeToml(table)

        switchNode.checked = false
        if (switchNode.getState.isNotEmpty()) {
            pendingSwitchStates.add(switchNode to switchNode.getState)
        }
        if (switchNode.setState == null) {
            switchNode.setState = ""
        }

        for (rowTable in tomlEntries(table, "rows")) {
            textRowToml(switchNode.rows, rowTable)
        }
        rowsShToml(table, switchNode.rows)

        return switchNode
    }

    private fun selectItemToml(optTable: TomlTable): SelectItem {
        val item = SelectItem()
        tomlGet(optTable, "val", "value")?.let { item.value = it }
        tomlGet(optTable, *TomlKeys.TITLE_SH)?.let {
            item.titleSh = it
            registerDynamicString(item, "title", it)
        }
        if (item.title.isNullOrEmpty()) {
            tomlGet(optTable, *TomlKeys.TITLE_TEXT)?.let { item.title = StringResRef.resolve(context, it) }
        }
        if (item.value == null) item.value = item.title
        return item
    }

    private fun pickerNodeToml(table: TomlTable): PickerNode? {
        val picker = runnableNodeToml(PickerNode(pageConfigAbsPath), table) as PickerNode? ?: return null
        tomlGet(table, *TomlKeys.OPTION_SH)?.let {
            if (picker.options == null) picker.options = ArrayList()
            picker.optionsSh = it
        }
        tomlGet(table, "multiple")?.let { picker.multiple = tomlTruthy(it, "multiple") }
        tomlGet(table, "separator")?.let { picker.separator = it }
        tomlGet(table, *TomlKeys.GET)?.let { picker.getState = it }
        tomlGet(table, *TomlKeys.SET)?.let { picker.setState = it }
        tomlGet(table, *TomlKeys.LOCK)?.let { parseLockAttr(it, picker) }
        tomlGet(table, *TomlKeys.LOCK_SH)?.let { picker.lockShell = it.trim() }

        val pickerItemsArray = itemsOrStringOptions(table)
        if (pickerItemsArray != null) {
            if (picker.options == null) picker.options = ArrayList()
            for (i in 0 until pickerItemsArray.size()) {
                val str = pickerItemsArray.getString(i) ?: continue
                val parts = str.split("|", limit = 2)
                val item = SelectItem()
                item.value = parts[0].trim()
                item.title = if (parts.size > 1) StringResRef.resolve(context, parts[1].trim()) else parts[0].trim()
                picker.options!!.add(item)
            }
        }
        val pickerOptions = tomlEntries(table, "options")
        if (pickerItemsArray == null && pickerOptions.isNotEmpty()) {
            if (picker.options == null) picker.options = ArrayList()
            for (optTable in pickerOptions) {
                picker.options!!.add(selectItemToml(optTable))
            }
        }
        resourceNodeToml(table)

        for (rowTable in tomlEntries(table, "rows")) {
            textRowToml(picker.rows, rowTable)
        }
        rowsShToml(table, picker.rows)

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
        tomlGet(table, *TomlKeys.SCRIPT)?.let { node.setState = it.trim() }
        tomlGet(table, *TomlKeys.LOCK)?.let { parseLockAttr(it, node) }
        tomlGet(table, *TomlKeys.LOCK_SH)?.let { node.lockShell = it.trim() }
        if (node.setState == null) node.setState = ""
        if (node.url.isEmpty() && node.urlSh.isEmpty()) return null

        for (rowTable in tomlEntries(table, "rows")) {
            textRowToml(node.rows, rowTable)
        }
        rowsShToml(table, node.rows)

        return node
    }

    private fun chatNodeToml(table: TomlTable): ChatNode? {
        val chat = runnableNodeToml(ChatNode(pageConfigAbsPath), table) as ChatNode? ?: return null
        tomlGet(table, *TomlKeys.SCRIPT)?.let { chat.script = it }
        tomlGet(table, "chat-title", "bot-title")?.let { chat.chatTitle = StringResRef.resolve(context, it) }
        tomlGet(table, "user-title", "me-title")?.let { chat.userTitle = StringResRef.resolve(context, it) }
        tomlGet(table, "greeting", "welcome")?.let { chat.greeting = StringResRef.resolve(context, it) }
        tomlGet(table, "placeholder", "hint")?.let { chat.placeholder = StringResRef.resolve(context, it) }
        tomlGet(table, "max-lines")?.let { chat.maxLines = parseIntOr(it, chat.maxLines).coerceAtLeast(1) }
        tomlGet(table, "markdown", "md")?.let { chat.markdown = tomlTruthy(it) }

        for (menuTable in tomlEntries(table, "menu")) {
            val option = pageMenuOptionToml(menuTable) ?: continue
            option.isFab = false
            assignUniqueMenuOptionKey(option, false)
            chat.menuOptions.add(option)
        }

        val params = ArrayList<ActionParamInfo>()
        for (paramTable in tomlEntries(table, "params")) {
            actionParamToml(paramTable)?.let { params.add(it) }
        }
        if (params.isNotEmpty()) {
            val paramsScript = tomlGet(table, "params-script", "apply")?.trim().orEmpty()
            chat.paramsNode = ActionNode(pageConfigAbsPath).apply {
                key = "chat-params:" + chat.title
                title = tomlGet(table, "params-title")?.let { StringResRef.resolve(context, it) }.orEmpty()
                this.params = params
                setState = paramsScript
                paramsOnly = paramsScript.isEmpty()
                tomlGet(table, "params-icon")?.let { iconPath = it.trim() }
            }
        }
        return chat
    }

    private fun actionNodeToml(table: TomlTable): ActionNode? {
        val action = runnableNodeToml(ActionNode(pageConfigAbsPath), table) as ActionNode? ?: return null
        tomlGet(table, *TomlKeys.SCRIPT)?.let { action.setState = it.trim() }
        tomlGet(table, *TomlKeys.LOCK)?.let { parseLockAttr(it, action) }
        tomlGet(table, *TomlKeys.LOCK_SH)?.let { action.lockShell = it.trim() }
        if (action.setState == null) action.setState = ""
        action.shellBy = tomlPipeList(table, "shell-by")
        action.shellValue = tomlPipeList(table, "shell-value")
        action.shellMode = tomlPipeList(table, "shell-mode")

        val paramTables = tomlEntries(table, "params")
        if (paramTables.isNotEmpty()) {
            val params = ArrayList<ActionParamInfo>()
            for (paramTable in paramTables) {
                actionParamToml(paramTable)?.let { params.add(it) }
            }
            action.params = params
        }
        tomlGet(table, "params-sh")?.let {
            action.paramsSh = it.trim()
            if (action.paramsSh.isNotEmpty()) registerDynamicString(action, "params", action.paramsSh)
        }

        for (rowTable in tomlEntries(table, "rows")) {
            textRowToml(action.rows, rowTable)
        }
        rowsShToml(table, action.rows)

        for (rowTable in tomlEntries(table, "params-rows")) {
            textRowToml(action.paramsRows, rowTable)
        }

        tomlGet(table, "menu")?.let { action.menu = tomlTruthy(it, "menu") }
        tomlGet(table, "show")?.let { action.show = resolveBoolOrShell(it, "show") }
        tomlGet(table, "keep-dialog")?.let { action.keepParamsDialog = tomlTruthy(it) }

        resourceNodeToml(table)
        return action
    }

    private fun actionParamToml(table: TomlTable): ActionParamInfo? {
        val p = ActionParamInfo()
        tomlGet(table, "name")?.let { p.name = it }
        tomlGet(table, "label")?.let { p.label = StringResRef.resolve(context, it) }
        tomlGet(table, "label-sh")?.let { p.labelSh = it }
        tomlGet(table, "apply", "param-script")?.let { p.applyScript = it }
        tomlGet(table, "placeholder")?.let { p.placeholder = StringResRef.resolve(context, it) }
        tomlGet(table, "placeholder-sh")?.let { p.placeholderSh = it }
        tomlGet(table, "title")?.let { p.title = StringResRef.resolve(context, it) }
        tomlGet(table, *TomlKeys.TITLE_SH)?.let { p.titleSh = it }
        tomlGet(table, "desc")?.let { p.desc = StringResRef.resolve(context, it) }
        tomlGet(table, *TomlKeys.DESC_SH)?.let { p.descSh = it }
        tomlGet(table, "desc-on", "on-desc", "desc-checked")?.let { p.descOn = StringResRef.resolve(context, it) }
        tomlGet(table, "desc-on-sh", "on-desc-sh", "desc-checked-sh")?.let { p.descOnSh = it }
        tomlGet(table, "separator")?.let { p.separator = it }
        if (table.isArray("value")) {
            val valueArray = table.getArray("value")
            if (valueArray != null) {
                val parts = ArrayList<String>()
                for (i in 0 until valueArray.size()) {
                    valueArray.getString(i)?.let { parts.add(it) }
                }
                p.value = parts.joinToString(p.separator)
            }
        } else {
            tomlGet(table, "value")?.let { p.value = it }
        }
        tomlGet(table, "type")?.let { p.type = it.lowercase(getDefault()).trim() }
        tomlGet(table, "suffix")?.let {
            val suffix = it.lowercase(getDefault()).trim()
            if (p.mime.isEmpty()) p.mime = Suffix2Mime().toMime(suffix)
            p.suffix = suffix
        }
        tomlGet(table, "mime")?.let { p.mime = it.lowercase(getDefault()) }
        tomlGet(table, *TomlKeys.PATH_HOME)?.let { p.pathHome = resolvePathPlaceholders(it.trim()) }
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
        tomlGet(table, "maxlength")?.let { p.maxLength = parseIntOr(it, p.maxLength) }
        tomlGet(table, "min")?.let { p.min = parseIntOr(it, p.min) }
        tomlGet(table, "max")?.let { p.max = parseIntOr(it, p.max) }
        tomlGet(table, "max-lines")?.let { p.maxLines = parseIntOr(it, p.maxLines).coerceAtLeast(1) }
        tomlGet(table, "required")?.let { p.required = tomlTruthy(it, "required") }
        tomlGet(table, "remember", "remember-value")?.let { p.remember = tomlTruthy(it, "remember") }
        tomlGet(table, *TomlKeys.VALUE_SH)?.let { p.valueShell = it }
        tomlGet(table, "options-sh", "option-sh")?.let {
            if (p.options == null) p.options = ArrayList()
            p.optionsSh = it
        }
        tomlGet(table, *TomlKeys.SUPPORT)?.let {
            if (!resolveBoolOrShell(it, "support", "visible")) p.supported = false
        }
        tomlGet(table, "multiple")?.let { p.multiple = tomlTruthy(it, "multiple") }
        tomlGet(table, "editable")?.let { p.editable = tomlTruthy(it, "editable") }
        tomlGet(table, "depend-on", "depend")?.let { p.dependOn = it }
        tomlGet(table, "depend-value")?.let { p.dependValue = it }
        tomlGet(table, "depend-mode")?.let { p.dependMode = it }
        tomlGet(table, "depend-logic", "depend-priority")?.let { p.dependLogic = it }
        tomlGet(table, "depend-default")?.let { p.dependDefault = it }
        tomlGet(table, "depend-initial", "depend-initial-state")?.let { p.dependInitialState = it }
        tomlGet(table, "depend-negate")?.let { p.dependNegate = tomlTruthy(it, "negate") }
        tomlGet(table, "depend-threshold")?.let { p.dependThreshold = parseIntOr(it, -1) }
        tomlGet(table, "depend-include-hidden")?.let { p.dependIncludeHidden = !(it == "false" || it == "0") }
        tomlGet(table, "depend-cascade")?.let { p.dependCascade = !(it == "false" || it == "0") }
        tomlGet(table, "depend-onchange", "depend-on-change", "depend-callback")?.let { p.dependOnChangeCallback = it }
        tomlGet(table, "depend-readonly")?.let { p.dependReadonly = tomlTruthy(it) }
        tomlGet(table, "depend-sort")?.let { p.dependSort = p.dependReadonly && tomlTruthy(it) }
        tomlGet(table, "allow-no-selection", "no-select")?.let { p.allowNoSelection = tomlTruthy(it) }

        val itemsArray = itemsOrStringOptions(table)
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
        tomlGet(table, *TomlKeys.BG_IMAGE)?.let { text.bgImage = it.trim() }
        tomlGet(table, *TomlKeys.BG_IMAGE_SH)?.let { text.bgImageSh = it.trim() }
        tomlGet(table, *TomlKeys.BG_SCALE)?.let { text.bgScale = it.trim().lowercase(getDefault()) }
        for (rowTable in tomlEntries(table, "rows")) {
            textRowToml(text.rows, rowTable)
        }
        rowsShToml(table, text.rows)
        resourceNodeToml(table)
        return text
    }

    private fun textRowToml(rows: ArrayList<TextNode.TextRow>, table: TomlTable) {
        val row = TextNode.TextRow()
        var visibleShellScript: String? = null
        tomlGet(table, *TomlKeys.SUPPORT)?.let {
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
        tomlGet(table, "margin-top", "spacing-top", "top-margin")?.let { row.marginTop = parseIntOr(it, row.marginTop) }
        tomlGet(table, "margin-bottom", "spacing-bottom", "bottom-margin")?.let { row.marginBottom = parseIntOr(it, row.marginBottom) }
        tomlGet(table, "alpha", "opacity")?.let {
            val v = it.trim().toFloatOrNull()
            if (v != null) {
                row.alpha = (if (v > 1f) v / 255f else v).coerceIn(0f, 1f)
            }
        }
        tomlGet(table, "foreground", "color")?.let { row.color = parseColorOr(it, row.color) }
        tomlGet(table, *TomlKeys.BG_COLOR)?.let { row.bgColor = parseColorOr(it, row.bgColor) }
        tomlGet(table, "size")?.let { row.size = parseIntOr(it, row.size) }
        tomlGet(table, "break")?.let { row.breakRow = tomlTruthy(it, "break") }
        tomlGet(table, *TomlKeys.LINE)?.let { row.line = tomlTruthy(it, "line", "divider", "separator") }
        tomlGet(table, "copy")?.let { row.copy = tomlTruthy(it, "copy") }
        tomlGet(table, *TomlKeys.LINK)?.let { row.link = it }
        tomlGet(table, *TomlKeys.INTENT)?.let { row.activity = it }
        tomlGet(table, *TomlKeys.PHOTO)?.let { row.photo = it.trim() }
        tomlGet(table, *TomlKeys.PHOTO_SH)?.let { row.photoSh = it.trim() }
        tomlGet(table, *TomlKeys.BG_IMAGE)?.let { row.bgImage = it.trim() }
        tomlGet(table, *TomlKeys.BG_IMAGE_SH)?.let { row.bgImageSh = it.trim() }
        tomlGet(table, *TomlKeys.BG_SCALE)?.let { row.bgScale = it.trim().lowercase(getDefault()) }
        tomlGet(table, *TomlKeys.PHOTO_REAL_SIZE)?.let { row.photoRealSize = tomlTruthy(it, "real-size", "original-size") }
        tomlGet(table, *TomlKeys.PHOTO_GIF_NUM)?.let { row.photoGifNum = parseIntOr(it, row.photoGifNum) }
        tomlGet(table, *TomlKeys.PHOTO_GIF_TIME)?.let { row.photoGifTime = parseIntOr(it, row.photoGifTime) }
        tomlGet(table, *TomlKeys.PHOTO_GIF_AUTOPLAY)?.let { row.photoGifAutoplay = tomlTruthy(it) }
        tomlGet(table, *TomlKeys.PHOTO_GIF_LOOP)?.let { row.photoGifLoopCount = parseIntOr(it, row.photoGifLoopCount) }
        tomlGet(table, *TomlKeys.PHOTO_REAL_GIF)?.let { row.photoRealGif = tomlTruthy(it, "real-gif", "gif-real") }
        tomlGet(table, *TomlKeys.ICON)?.let { row.icon = it.trim() }
        tomlGet(table, *TomlKeys.ICON_SH)?.let { row.iconSh = it.trim() }
        tomlGet(table, "icon-position", "icon-pos")?.let {
            val p = it.trim().lowercase(getDefault())
            row.iconPosition = when (p) {
                "after", "end", "right" -> "after"
                else -> "before"
            }
        }
        tomlGet(table, "icon-size")?.let { row.iconSize = parseIntOr(it, row.iconSize) }
        tomlGet(table, *TomlKeys.ICON_GIF_NUM)?.let { row.iconGifNum = parseIntOr(it, row.iconGifNum) }
        tomlGet(table, *TomlKeys.ICON_GIF_TIME)?.let { row.iconGifTime = parseIntOr(it, row.iconGifTime) }
        tomlGet(table, *TomlKeys.ICON_GIF_AUTOPLAY)?.let { row.iconGifAutoplay = tomlTruthy(it) }
        tomlGet(table, *TomlKeys.ICON_GIF_LOOP)?.let { row.iconGifLoopCount = parseIntOr(it, row.iconGifLoopCount) }
        tomlGet(table, *TomlKeys.ICON_REAL_GIF)?.let { row.iconRealGif = tomlTruthy(it, "real-gif", "gif-real") }
        tomlGet(table, "script", "run")?.let { row.onClickScript = it }
        tomlGet(table, "toast", "toast-result")?.let { row.toastResult = tomlTruthy(it, "toast", "toast-result") }
        tomlGet(table, "sh", "text-sh")?.let { row.dynamicTextSh = it }
        tomlGet(table, "confirm")?.let {
            val v = it.trim()
            when (v.lowercase(getDefault())) {
                "true", "1", "yes", "on" -> row.confirm = context.getString(R.string.kr_confirm_default)
                "false", "0", "no", "off", "" -> row.confirm = ""
                else -> row.confirm = StringResRef.resolve(context, v)
            }
        }
        tomlGet(table, *TomlKeys.CONFIRM_TEXT)?.let { row.confirm = StringResRef.resolve(context, it) }
        tomlGet(table, "refresh-interval", "refresh", "interval")?.let { row.refreshInterval = parseIntOr(it, row.refreshInterval) }
        tomlGet(table, "reset")?.let {
            val v = it.trim()
            row.resetTarget = when {
                v.equals("self", ignoreCase = true) || v.equals("this", ignoreCase = true) -> -2
                v.equals("all", ignoreCase = true) -> -3
                else -> v.toIntOrNull() ?: row.resetTarget
            }
        }
        tomlGet(table, "progress")?.let { row.progress = it.trim().toFloatOrNull() ?: row.progress }
        tomlGet(table, "progress-sh")?.let { row.progressSh = it }
        tomlGet(table, "progress-max", "progressmax")?.let { row.progressMax = it.trim().toFloatOrNull() ?: row.progressMax }
        tomlGet(table, "progress-color")?.let { row.progressColor = parseColorOr(it, row.progressColor) }
        tomlGet(table, *TomlKeys.PROGRESS_TRACK_COLOR)?.let { row.progressTrackColor = parseColorOr(it, row.progressTrackColor) }
        tomlGet(table, "progress-width")?.let { row.progressWidth = parseIntOr(it, row.progressWidth) }
        tomlGet(table, "progress-height")?.let { row.progressHeight = parseIntOr(it, row.progressHeight) }
        tomlGet(table, "flash")?.let { row.flash = tomlTruthy(it, "flash") }
        tomlGet(table, "flash-color")?.let { row.flashColor = parseColorOr(it, row.flashColor) }
        tomlGet(table, "markdown", "md")?.let { row.markdown = tomlTruthy(it, "markdown", "md") }
        tomlGet(table, "html-file", "html-path")?.let { row.htmlFile = it.trim() }
        tomlGet(table, "html-url", "html-link")?.let { row.htmlUrl = it.trim() }
        tomlGet(table, "html-height")?.let { row.htmlHeight = parseIntOr(it, row.htmlHeight) }
        tomlGet(table, "toggle", "toggle-type")?.let {
            val t = it.trim().lowercase(getDefault())
            if (t == "checkbox" || t == "switch") row.toggle = t
        }
        tomlGet(table, *TomlKeys.GET)?.let {
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
        tomlGet(table, *TomlKeys.SET)?.let { row.onChangeSh = it }
        tomlGet(table, "align")?.let {
            when (it) {
                "right" -> row.align = Layout.Alignment.ALIGN_OPPOSITE
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
        tomlGet(table, "file-sh", "path-sh")?.let {
            editor.fileSh = it.trim()
            registerDynamicString(editor, "file", editor.fileSh)
        }
        tomlGet(table, "wrap")?.let { editor.wrap = !(it == "0" || it == "false" || it == "off" || it == "no-wrap") }
        tomlGet(table, "run", "runnable")?.let { editor.run = !(it == "0" || it == "false" || it == "off" || it == "no") }
        tomlGet(table, "placeholder")?.let { editor.placeholder = StringResRef.resolve(context, it) }
        tomlGet(table, "readonly")?.let { editor.readonly = resolveBoolOrShell(it) }
        tomlGet(table, "need-input")?.let { editor.needInput = (it == "true" || it == "1") }
        tomlGet(table, *TomlKeys.VALUE_SH)?.let { editor.valueSh = it }
        tomlGet(table, "value")?.let { editor.value = it }

        for (rowTable in tomlEntries(table, "rows")) {
            textRowToml(editor.rows, rowTable)
        }
        rowsShToml(table, editor.rows)

        return editor
    }

    private fun terminalNodeToml(table: TomlTable): TerminalNode? {
        val terminal = clickableNodeToml(TerminalNode(pageConfigAbsPath), table) as TerminalNode? ?: return null
        tomlGet(table, "cwd", "dir")?.let { terminal.cwd = it.trim() }
        tomlGet(table, "cmd", "command")?.let { terminal.cmd = it }
        tomlGet(table, "font-size")?.let { terminal.fontSize = parseIntOr(it, 0) }
        tomlGet(table, "exit-on-back", "back-exit")?.let {
            terminal.exitOnBack = it.trim().lowercase().let { v -> v == "true" || v == "1" }
        }
        tomlGet(table, "close-on-exit", "auto-close")?.let {
            terminal.closeOnExit = it.trim().lowercase().let { v -> v == "true" || v == "1" }
        }
        tomlGet(table, "keep-screen-on", "keep-awake")?.let {
            terminal.keepScreenOn = it.trim().lowercase().let { v -> v == "true" || v == "1" }
        }
        tomlGet(table, "root", "su")?.let {
            terminal.root = when (it.trim().lowercase()) {
                "true", "1" -> 1
                else -> 0
            }
        }
        return terminal
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

