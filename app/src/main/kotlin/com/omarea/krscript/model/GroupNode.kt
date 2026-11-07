package com.omarea.krscript.model

class GroupNode(currentPageConfigPath: String) : NodeInfoBase(currentPageConfigPath){
    var supported: Boolean = true
    var line: Boolean = false
    // Lưới ô dạng thẻ: columns cố định (1..6); columnWidthDp > 0 thì số cột tự tính theo bề rộng group, kẹp trong min..max
    var columns: Int = 1
    var columnWidthDp: Int = 0
    var minColumns: Int = 1
    var maxColumns: Int = 6
    val children: ArrayList<NodeInfoBase> = ArrayList()
}
