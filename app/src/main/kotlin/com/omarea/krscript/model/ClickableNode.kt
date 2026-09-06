package com.omarea.krscript.model

open class ClickableNode(currentPageConfigPath: String) : NodeInfoBase(currentPageConfigPath) {
    // 功能图标路径（列表中）
    var iconPath = ""
    // "icon-sh": script trả về đường dẫn icon động - chạy 1 lần lúc parse trang (gộp batch cùng
    // title-sh/desc-sh/summary-sh qua registerDynamicString), kết quả ghi đè thẳng vào iconPath.
    var iconSh: String = ""
    // Nếu > 0: icon là hoạt ảnh (gif-style), số khung hình cần nạp (icon_1.png, icon_2.png, ...)
    var iconGifNum: Int = 0
    // Thời gian hiển thị mỗi khung hình (mili giây)
    var iconGifTime: Int = 300
    // true (mặc định): tự chạy hoạt ảnh; false: chỉ hiện khung đầu, bấm vào icon để phát/tạm dừng
    var iconGifAutoplay: Boolean = true
    // Số vòng lặp tối đa (<=0: lặp vô hạn, mặc định)
    var iconGifLoopCount: Int = 0

    // 功能图标路径（桌面快捷）
    var logoPath = ""
    var photoPath = ""
    // "photo-sh": script trả về đường dẫn photo động - chạy 1 lần lúc parse trang, gộp batch cùng
    // icon-sh/title-sh/... (registerDynamicString), kết quả ghi đè thẳng vào photoPath.
    var photoSh: String = ""
    // Nếu true: hiện ảnh (photoPath) đúng kích thước thật, căn giữa, không kéo dãn full chiều ngang
    var photoRealSize: Boolean = false
    // Nếu > 0: photo là hoạt ảnh (gif-style), số khung hình cần nạp (photo_1.png, photo_2.png, ...)
    var photoGifNum: Int = 0
    // Thời gian hiển thị mỗi khung hình (mili giây)
    var photoGifTime: Int = 300
    // true (mặc định): tự chạy hoạt ảnh; false: chỉ hiện khung đầu, bấm vào ảnh để phát/tạm dừng
    var photoGifAutoplay: Boolean = true
    // Số vòng lặp tối đa (<=0: lặp vô hạn, mặc định)
    var photoGifLoopCount: Int = 0
    var bgPath = ""
    // "bg-sh": script trả về đường dẫn ảnh nền động - cùng cơ chế batch với photo-sh/icon-sh ở trên.
    var bgSh: String = ""

    // 是否允许添加快捷方式（非false，且具有key则默认允许）
    var allowShortcut:Boolean? = null

    // true = khoá (hiện thông báo tuỳ chỉnh từ lockMessage); false = mở khoá
    var locked: Boolean = false
    // Thông báo tuỳ chỉnh khi bị khoá (lock = "1|message")
    var lockMessage: String = ""
    // 锁定状态获取（脚本）— đọc từ thuộc tính "lock-sh"
    var lockShell: String = ""

    // 此功能的Android SDK版本要求
    var targetSdkVersion = 0
    var minSdkVersion = 0
    var maxSdkVersion = 100
}
