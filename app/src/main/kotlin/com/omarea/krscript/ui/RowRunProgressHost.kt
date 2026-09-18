package com.omarea.krscript.ui

// Activity chứa các trang KrScript (hiện tại là ActionPage) implement interface này để
// RowsRenderHelper có thể hiện/ẩn thanh tiến trình (loadProgressBar - thanh ngay dưới toolbar,
// vốn dùng để báo trang đang tải) trong lúc script "script"/"run" của 1 row đang chạy nền. Nếu
// context không phải Activity implement interface này (ví dụ context khác) thì chỉ đơn giản
// không hiện thanh tiến trình, không ảnh hưởng gì tới việc chạy script - xem RowsRenderHelper.
interface RowRunProgressHost {
    fun showRowRunProgress()
    fun hideRowRunProgress()
}
