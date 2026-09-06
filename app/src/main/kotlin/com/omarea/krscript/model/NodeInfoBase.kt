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

    // 唯一标识（如果需要将功能添加到桌面作为快捷方式，则需要此标识来区分）
    var key: String = ""
    // 索引（自动生成）
    val index: String = UUID.randomUUID().toString()
    // 标题
    var title: String = ""
    // 标题（脚本）
    var titleSh: String = ""
    // 描述
    var desc: String = ""
    // 描述（脚本）
    var descSh: String = ""
    // 摘要信息
    var summary: String = ""
    // 摘要信息(脚本)
    var summarySh: String = ""

    // load-after = true: mục này không build/hiện cùng lượt tải trang ban đầu, mà được build
    // và chèn vào ĐÚNG vị trí gốc trong danh sách (root hoặc trong group chứa nó) SAU KHI trang
    // đã tải xong (không chặn trang hiện ra) - xem PageConfigReader.buildDeferredNodes() và
    // ActionPage.startDeferredLoadIfNeeded().
    var loadAfter: Boolean = false
}
