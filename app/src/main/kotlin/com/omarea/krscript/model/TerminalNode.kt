package com.omarea.krscript.model

class TerminalNode(currentConfigXml: String) : ClickableNode(currentConfigXml) {
    var cwd: String = ""

    var cmd: String = ""

    var fontSize: Int = 0

    // 0: không bật su sẵn (mặc định, vẫn gõ su được), 1: bật su nếu máy có root
    var root: Int = 0
}
