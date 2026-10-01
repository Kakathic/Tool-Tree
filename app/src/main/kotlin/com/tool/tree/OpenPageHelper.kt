package com.tool.tree

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import com.omarea.krscript.model.PageNode
import com.tool.tree.ui.SwipeBackPreviewCache

class OpenPageHelper(private val activity: Activity) {

    fun openPage(pageNode: PageNode, onNoNavigate: (() -> Unit)? = null) {
        try {
            if (!openPageDirect(pageNode)) {
                onNoNavigate?.invoke()
            }
        } catch (ex: Exception) {
            onNoNavigate?.invoke()
            Toast.makeText(activity, ex.message ?: "Unknown error", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPageDirect(pageNode: PageNode): Boolean {
        val intent = when {
            pageNode.onlineHtmlPage.isNotEmpty() -> {
                Intent(activity, ActionPageOnline::class.java).apply {
                    putExtra("config", pageNode.onlineHtmlPage)
                }
            }

            pageNode.pageConfigSh.isNotEmpty() ||
            pageNode.pageConfigPath.isNotEmpty() -> {
                Intent(activity, ActionPage::class.java)
            }

            else -> null
        } ?: return false

        intent.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("page", pageNode)
            SwipeBackPreviewCache.capture(activity) {
                activity.startActivity(this)
            }
        }
        return true
    }
}
