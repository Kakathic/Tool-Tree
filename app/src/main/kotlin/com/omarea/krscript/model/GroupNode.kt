package com.omarea.krscript.model

class GroupNode(currentPageConfigPath: String) : NodeInfoBase(currentPageConfigPath){
    var supported: Boolean = true
    // line = true: vẽ dải ngăn cách giữa các mục trong group (chỉ từ 2 mục hiển thị trở lên)
    var line: Boolean = false
    val children: ArrayList<NodeInfoBase> = ArrayList()
}
