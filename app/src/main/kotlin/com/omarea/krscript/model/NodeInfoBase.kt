package com.omarea.krscript.model

import java.io.File
import java.io.Serializable
import java.util.*

open class NodeInfoBase(val currentPageConfigPath: String) : Serializable {
    val pageConfigDir = (
        if (currentPageConfigPath.isNotEmpty()) {
            val dir = File(currentPageConfigPath).parent
            if (dir.startsWith("file:/android_asset/")) {
                "file:///android_asset/" + dir.substring("file:/android_asset/".length)
            } else {
                dir
            }
        } else {
            ""
        }
    )

    var key: String = ""
    // Id tự sinh từ tiêu đề gốc cho mục không có key; chỉ dùng cho Yêu thích/lối tắt
    var autoKey: String = ""
    val effectiveKey: String get() = key.ifEmpty { autoKey }
    val index: String = UUID.randomUUID().toString()
    var title: String = ""
    var titleSh: String = ""
    var desc: String = ""
    var descSh: String = ""
    var summary: String = ""
    var summarySh: String = ""

    var loadAfter: Boolean = false
}
