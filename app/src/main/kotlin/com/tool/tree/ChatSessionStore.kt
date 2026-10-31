package com.tool.tree

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ChatSessionStore(context: Context, pageKey: String) {
    class Stored(val fromUser: Boolean, val text: String)

    class Info(val id: String, val name: String, val updated: Long)

    companion object {
        private const val MAX_SESSIONS = 50
        private const val NAME_LIMIT = 40
        const val EMPTY_NAME = "Phiên mới"
    }

    private val dir = File(context.filesDir, "chat/" + Integer.toHexString(pageKey.hashCode())).apply { mkdirs() }

    fun newId(): String = System.currentTimeMillis().toString()

    private fun file(id: String) = File(dir, id.filter { it.isDigit() } + ".json")

    fun list(): List<Info> {
        val files = dir.listFiles { f -> f.name.endsWith(".json") } ?: return emptyList()
        return files.mapNotNull { f ->
            try {
                val messages = JSONObject(f.readText(Charsets.UTF_8)).getJSONArray("messages")
                var name = EMPTY_NAME
                for (i in 0 until messages.length()) {
                    val m = messages.getJSONObject(i)
                    if (m.getBoolean("u")) {
                        name = m.getString("t").replace('\n', ' ').trim().take(NAME_LIMIT).ifEmpty { EMPTY_NAME }
                        break
                    }
                }
                Info(f.name.removeSuffix(".json"), name, f.lastModified())
            } catch (_: Exception) {
                null
            }
        }.sortedByDescending { it.updated }
    }

    fun load(id: String): List<Stored> {
        return try {
            val messages = JSONObject(file(id).readText(Charsets.UTF_8)).getJSONArray("messages")
            (0 until messages.length()).map {
                val m = messages.getJSONObject(it)
                Stored(m.getBoolean("u"), m.getString("t"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(id: String, messages: List<Stored>) {
        val target = file(id)
        if (messages.none { it.fromUser }) {
            target.delete()
            return
        }
        try {
            val array = JSONArray()
            for (m in messages) array.put(JSONObject().put("u", m.fromUser).put("t", m.text))
            val tmp = File(dir, target.name + ".tmp")
            tmp.writeText(JSONObject().put("messages", array).toString(), Charsets.UTF_8)
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
            prune()
        } catch (_: Exception) {
        }
    }

    fun delete(id: String) {
        file(id).delete()
    }

    private fun prune() {
        val all = list()
        if (all.size > MAX_SESSIONS) all.drop(MAX_SESSIONS).forEach { delete(it.id) }
    }
}
