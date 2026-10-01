package com.omarea.krscript.model

class SwitchNode(currentConfigXml: String) : RunnableNode(currentConfigXml){
    var getState: String = ""
    var checked = false

    val rows = ArrayList<TextNode.TextRow>()
}