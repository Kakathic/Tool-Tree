package com.omarea.krscript.shortcut

import android.content.Context
import android.content.Intent
import android.os.Build
import com.omarea.krscript.model.ClickableNode
import com.omarea.krscript.model.PageNode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

// Danh sách Yêu thích của mục lục bên trái, lưu bằng JSON (filesDir/drawer_favorites.json)
class DrawerFavorites(private val context: Context) {

    class Entry(
        val id: String,
        val title: String,
        val icon: String,
        val iconSrc: String,
        val src: String,
        val config: String,
        val configSh: String,
        val html: String,
        val link: String,
        val activity: String,
        val openId: String
    ) {
        fun toPage(): PageNode = PageNode(src).also {
            it.title = title
            it.iconPath = icon
            it.pageConfigPath = config
            it.pageConfigSh = configSh
            it.onlineHtmlPage = html
            it.link = link
            it.activity = activity
            it.openItemId = openId
        }

        fun toIconNode(): ClickableNode = ClickableNode(iconSrc).also { it.iconPath = icon }

        fun isSameTarget(o: Entry) = src == o.src && config == o.config && configSh == o.configSh &&
            html == o.html && link == o.link && activity == o.activity && openId == o.openId
    }

    private val file get() = File(context.filesDir, "drawer_favorites.json")

    @Synchronized
    fun list(): List<Entry> {
        return try {
            if (!file.isFile) return emptyList()
            val array = JSONArray(file.readText(Charsets.UTF_8))
            (0 until array.length()).mapNotNull { fromJson(array.optJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // false nếu đã có mục cùng đích hoặc ghi thất bại
    @Synchronized
    fun add(entry: Entry): Boolean {
        val current = list()
        if (current.any { it.isSameTarget(entry) }) return false
        return save(current + entry)
    }

    @Synchronized
    fun remove(id: String) {
        save(list().filter { it.id != id })
    }

    // intent do addToFavorites tạo ra: "page" = trang đích, "autoRunItemId" = id mục cần chạy
    fun addFromIntent(intent: Intent, node: ClickableNode): Boolean {
        val page = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("page", PageNode::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("page") as? PageNode
        } ?: return false
        val openId = intent.getStringExtra("autoRunItemId").orEmpty().ifEmpty { page.openItemId }
        return add(Entry(
            id = UUID.randomUUID().toString(),
            title = node.title,
            icon = node.iconPath,
            iconSrc = node.currentPageConfigPath,
            src = page.currentPageConfigPath,
            config = page.pageConfigPath,
            configSh = page.pageConfigSh,
            html = page.onlineHtmlPage,
            link = page.link,
            activity = page.activity,
            openId = openId
        ))
    }

    private fun save(entries: List<Entry>): Boolean {
        return try {
            val array = JSONArray()
            entries.forEach { array.put(toJson(it)) }
            val tmp = File(file.parentFile, "drawer_favorites.json.tmp")
            tmp.writeText(array.toString(), Charsets.UTF_8)
            tmp.renameTo(file)
        } catch (_: Exception) {
            false
        }
    }

    private fun toJson(e: Entry) = JSONObject().apply {
        put("id", e.id); put("title", e.title); put("icon", e.icon); put("iconSrc", e.iconSrc)
        put("src", e.src); put("config", e.config); put("configSh", e.configSh); put("html", e.html)
        put("link", e.link); put("activity", e.activity); put("openId", e.openId)
    }

    private fun fromJson(o: JSONObject?): Entry? {
        o ?: return null
        val id = o.optString("id")
        if (id.isEmpty()) return null
        return Entry(
            id, o.optString("title"), o.optString("icon"), o.optString("iconSrc"), o.optString("src"),
            o.optString("config"), o.optString("configSh"), o.optString("html"),
            o.optString("link"), o.optString("activity"), o.optString("openId")
        )
    }
}
