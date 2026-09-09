package com.tool.tree

import java.io.Serializable

/**
 * Thông tin bản cập nhật mới, lấy được ở SplashActivity (qua AppUpdateChecker.fetchUpdateInfo)
 * trong lúc đang tải dữ liệu, truyền qua Intent extra "pendingUpdate" để MainActivity hiện
 * AppUpdateDialog khi vừa vào màn hình chính - giống cách "preloadedTabs" đang được dùng.
 */
class AppUpdateInfo(
    val apkUrl: String,
    val changelogUrl: String,
    val sha256: String,
    // Dung lượng file apk (byte), lấy từ field "size" của GitHub release asset. -1 nếu không
    // xác định được.
    val apkSize: Long = -1
) : Serializable
