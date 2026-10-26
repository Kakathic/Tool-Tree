package com.omarea.krscript.model

class TerminalNode(currentConfigXml: String) : ClickableNode(currentConfigXml) {
    var cwd: String = ""

    var cmd: String = ""

    var fontSize: Int = 0
}
