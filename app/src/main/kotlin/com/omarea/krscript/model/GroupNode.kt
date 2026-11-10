package com.omarea.krscript.model

class GroupNode(currentPageConfigPath: String) : NodeInfoBase(currentPageConfigPath){
    var supported: Boolean = true
    var line: Boolean = false
    // Lưới ô dạng thẻ: columns cố định (1..6); columnWidthDp > 0 thì số cột tự tính theo bề rộng group, kẹp trong min..max
    var card: Boolean = false
    var columns: Int = 1
    var columnWidthDp: Int = 0
    var minColumns: Int = 1
    var maxColumns: Int = 6
    // Kích thước gọn riêng cho các thẻ trong group (do code đặt, không đọc từ toml)
    var compact: Boolean = false
    val children: ArrayList<NodeInfoBase> = ArrayList()
}
