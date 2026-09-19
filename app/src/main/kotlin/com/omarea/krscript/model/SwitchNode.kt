package com.omarea.krscript.model

class SwitchNode(currentConfigXml: String) : RunnableNode(currentConfigXml){
    var getState: String = ""
    var checked = false

    // Giống action.rows: cho phép switch hiển thị thêm các dòng rich-text (text/icon/toggle/photo...)
    // ngay bên dưới item, dùng chung TextNode.TextRow/RowsRenderHelper. Xem PageConfigReader.switchNodeToml()
    // và ListItemSwitch. Cần thêm sẵn view kr_rows/kr_rows_html/kr_rows_photo trong kr_switch_list_item.xml
    // vì layout của switch không dùng chung với action.
    val rows = ArrayList<TextNode.TextRow>()
}