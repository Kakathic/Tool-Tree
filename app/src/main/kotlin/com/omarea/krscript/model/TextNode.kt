package com.omarea.krscript.model

import android.text.Layout
import java.io.Serializable

class TextNode(currentPageConfigPath: String) : NodeInfoBase(currentPageConfigPath) {
    val rows = ArrayList<TextRow>()

    class TextRow : Serializable {
        // 文字大小
        internal var size: Int = -1
        // 文字颜色
        internal var color: Int = -1
        // 文字背景色
        internal var bgColor: Int = -1
        // 是否加粗
        internal var bold: Boolean = false
        // 是否斜体
        internal var italic: Boolean = false
        // 是否显示下划线
        internal var underline: Boolean = false
        // Gạch ngang (strikethrough)
        internal var strikethrough: Boolean = false
        // Font đơn cách (monospace) - hữu ích khi hiện log/lệnh shell
        internal var monospace: Boolean = false
        // Khoảng cách giữa các chữ (đơn vị em, giống TextView.letterSpacing) - 0 = mặc định,
        // giá trị dương giãn chữ ra, âm thì thu hẹp lại
        internal var letterSpacing: Float = 0f
        // Độ cao dòng (chiều dọc), dạng hệ số nhân so với chiều cao dòng mặc định - 0 = không
        // thiết lập (giữ nguyên); ví dụ 1.5 = cao hơn 50%, 0.8 = thấp hơn 20%
        internal var lineHeight: Float = 0f
        // Khoảng trống thêm vào phía TRÊN row này (đơn vị dp), 0 = không thêm
        internal var marginTop: Int = 0
        // Khoảng trống thêm vào phía DƯỚI row này (đơn vị dp), 0 = không thêm
        internal var marginBottom: Int = 0
        // Độ trong suốt (alpha) của chữ, từ 0.0 (trong suốt hoàn toàn) đến 1.0 (đục hoàn toàn).
        // -1 = không thiết lập (giữ nguyên alpha mặc định của màu chữ/màu nền)
        internal var alpha: Float = -1f
        // 是否换行后显示
        internal var breakRow: Boolean = false
        // Nếu true: vẽ 1 đường kẻ mảnh ngang qua hết chiều rộng NGAY TRƯỚC row này, dùng để
        // tách riêng phần rows (hoặc tách nhóm row) khỏi nội dung phía trên
        internal var line: Boolean = false
        // 对齐方式
        internal var align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
        // 点击后要跳转的网页链接
        internal var link: String = ""
        // 点击后要打开的活动
        internal var activity: String = ""
        // 文本内容
        internal var text: String = ""
        // Script lấy text động (khai báo bằng "sh" hoặc "text-sh" trong TOML)
        internal var dynamicTextSh: String = ""
        // 点击后执行的脚本
        internal var onClickScript: String = ""
        internal var photo: String = ""
        // "photo-sh": script trả về đường dẫn photo động cho row - gộp batch cùng text-sh/icon-sh
        // trong RowsRenderHelper.bind() mỗi lần render (không cache, giống text-sh).
        internal var photoSh: String = ""
        // Nếu true: hiện ảnh (photo) đúng kích thước thật, căn giữa, không kéo dãn full chiều ngang
        internal var photoRealSize: Boolean = false
        // Nếu > 0: photo là hoạt ảnh (gif-style), số khung hình cần nạp (photo_1.png, photo_2.png, ...)
        internal var photoGifNum: Int = 0
        // Thời gian hiển thị mỗi khung hình (mili giây)
        internal var photoGifTime: Int = 300
        // true (mặc định): tự chạy hoạt ảnh; false: chỉ hiện khung đầu, bấm vào ảnh để phát/tạm dừng
        internal var photoGifAutoplay: Boolean = true
        // Số vòng lặp tối đa (<=0: lặp vô hạn, mặc định)
        internal var photoGifLoopCount: Int = 0

        // Ảnh nhỏ hiển thị NGAY CẠNH chữ (inline, cùng dòng) - khác với "photo" (khối ảnh riêng,
        // full chiều rộng, nằm dưới toàn bộ rows). "" = không có icon.
        internal var icon: String = ""
        // "icon-sh": script trả về đường dẫn icon inline động cho row - cùng cơ chế batch với photo-sh.
        internal var iconSh: String = ""
        // Vị trí icon so với chữ: "before" (trước chữ) hoặc "after" (sau chữ)
        internal var iconPosition: String = "before"
        // Kích thước icon (đơn vị dp) - 0 = tự động lấy kích thước gần bằng cỡ chữ hiện tại
        internal var iconSize: Int = 0
        // "" (mặc định) = không phải toggle; "checkbox" hoặc "switch"
        internal var toggle: String = ""
        // Trạng thái bật/tắt hiện tại (được resolveBoolOrShell tại lúc parse trang - xem "checked")
        internal var checked: Boolean = false
        // Script chạy khi người dùng bấm đổi trạng thái - nhận biến môi trường "state" = "1"/"0"
        internal var onChangeSh: String = ""
    }
}