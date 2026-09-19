package com.omarea.krscript.model

/**
 * Node cho thẻ <editor file="" title="" desc="" ... />
 * Khi người dùng bấm vào mục này, ứng dụng sẽ mở trang soạn thảo văn bản (TextEditorActivity)
 * để xem/sửa nội dung file được chỉ định bởi thuộc tính "file".
 * Nếu file chưa tồn tại thì trang soạn thảo sẽ tạo mới khi lưu.
 */
class EditorNode(currentConfigXml: String) : ClickableNode(currentConfigXml) {
    // Đường dẫn file cần mở để soạn thảo (bắt buộc)
    var file: String = ""
    var placeholder: String? = null

    // Trạng thái ngắt dòng mặc định khi mở trang soạn thảo (mặc định: bật ngắt dòng)
    var wrap: Boolean = true

    // Chỉ đọc: true thì không cho phép chỉnh sửa/lưu nội dung (chỉ xem)
    var readonly: Boolean = false

    // Khi bấm nút Run để chạy thử script đang soạn thảo: true nghĩa là script có gọi lệnh
    // `read` để chờ người dùng nhập dữ liệu qua bàn phím trong lúc chạy (giống thuộc tính
    // need-input của thẻ <action>) -> ứng dụng sẽ hiện ô nhập liệu trong lúc thực thi.
    var needInput: Boolean = false

    // Nội dung khởi tạo (script): kết quả trả về sẽ được dùng làm nội dung ban đầu
    // nếu file chưa tồn tại. Ưu tiên cao hơn "value" nếu cả hai đều có.
    var valueSh: String = ""

    // Nội dung khởi tạo (tĩnh): chỉ được điền vào khi file CHƯA tồn tại.
    // Nếu file đã tồn tại thì giữ nguyên nội dung hiện có, không điền gì thêm.
    var value: String = ""

    // Giống action.rows: cho phép editor hiển thị thêm các dòng rich-text (text/icon/toggle/photo...)
    // ngay bên dưới item, dùng chung TextNode.TextRow/RowsRenderHelper. Xem PageConfigReader.editorNodeToml()
    // và ListItemEditor.
    val rows = ArrayList<TextNode.TextRow>()
}
