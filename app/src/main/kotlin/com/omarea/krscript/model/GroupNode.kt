package com.omarea.krscript.model

class GroupNode(currentPageConfigPath: String) : NodeInfoBase(currentPageConfigPath){
    var supported: Boolean = true
    var line: Boolean = false
    // columns > 1: các mục bấm được trong group xếp thành lưới ô dạng thẻ (1..4 cột)
    var columns: Int = 1
    val children: ArrayList<NodeInfoBase> = ArrayList()
}
