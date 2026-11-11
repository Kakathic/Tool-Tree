package com.omarea.krscript.config

import android.app.Activity
import com.tool.tree.R
import com.omarea.krscript.executor.ScriptEnvironmen
import com.omarea.krscript.model.ActionNode
import com.omarea.krscript.model.ClickableNode
import com.omarea.krscript.model.NodeInfoBase
import com.omarea.krscript.model.PageMenuOption
import com.omarea.krscript.model.PageNode
import java.io.ByteArrayInputStream

class PageConfigSh(private var activity: Activity, private var pageConfigSh: String, private var parentConfig: PageNode?) {

    private var lastReader: PageConfigReader? = null
    val pageMenuOptions: ArrayList<PageMenuOption> get() = lastReader?.pageMenuOptions ?: ArrayList()
    val headerActions: ArrayList<ActionNode> get() = lastReader?.headerActions ?: ArrayList()
    val autoShowActions: ArrayList<ActionNode> get() = lastReader?.autoShowActions ?: ArrayList()
    val menuIcon: ClickableNode? get() = lastReader?.menuIcon
    val fabIcon: ClickableNode? get() = lastReader?.fabIcon
    val autoRefresh: Int get() = lastReader?.autoRefresh ?: -1

    val hasDeferredEntries: Boolean get() = lastReader?.hasDeferredEntries ?: false
    fun buildDeferredNodes(): ArrayList<PageConfigReader.DeferredNodeResult> =
        lastReader?.buildDeferredNodes() ?: ArrayList()

    private fun looksLikeInlineToml(result: String): Boolean {
        val firstLines = result.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.take(2).toList()
        return firstLines.any { it.startsWith("[[toml]]") || it.startsWith("[[group]]") }
    }

    private fun pageConfigShError(content: String) {
        TomlErrorReporter.show(
            activity,
            activity.getString(R.string.toml_error_title),
            activity.getString(R.string.kr_page_sh_invalid) + "\n\n" + content,
            repeatable = true
        )
    }

    private fun noReadPermission(path: String) {
        TomlErrorReporter.show(
            activity,
            activity.getString(R.string.toml_error_title),
            activity.getString(R.string.toml_error_file, path) + "\n\n" + activity.getString(R.string.kr_page_sh_file_permission),
            repeatable = true
        )
    }

    fun execute(onNodeReady: ((NodeInfoBase?, Int, Int) -> Unit)? = null): ArrayList<NodeInfoBase>? {
        var items: ArrayList<NodeInfoBase>? = null

        val result = ScriptEnvironmen.executeResultRoot(activity, pageConfigSh, parentConfig)?.trim()
        if (result != null) {
            if (result.endsWith(".toml")) {
                val reader = PageConfigReader(activity, result, parentConfig?.pageConfigDir)
                lastReader = reader
                items = reader.readConfigXml(onNodeReady)
                if (items == null && !reader.loadFailed) {
                    noReadPermission(result)
                }
            } else if (looksLikeInlineToml(result)) {
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