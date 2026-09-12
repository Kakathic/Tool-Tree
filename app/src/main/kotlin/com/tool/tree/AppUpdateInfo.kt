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
    val apkSize: Long = -1,
    // Tên file apk cố định dùng khi cache ở activity.cacheDir ("Tool-Tree.apk", xem
    // AppUpdateChecker.APK_FILE_NAME) - khai báo 1 chỗ duy nhất để khớp tuyệt đối với tên file
    // AppUpdateChecker dùng khi kiểm tra/dọn cache cũ.
    val apkFileName: String,
    // Nội dung changelog (markdown thô) đã tải sẵn ở SplashActivity cùng lúc kiểm tra cập nhật
    // (AppUpdateChecker.fetchUpdateInfo) - null nếu tải thất bại. DialogAppUpdate chỉ hiển thị
    // lại giá trị này, KHÔNG tự tải lại khi mở dialog.
    val changelogText: String? = null
) : Serializable
