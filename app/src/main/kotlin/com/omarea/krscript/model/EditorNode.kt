package com.omarea.krscript.model

class EditorNode(currentConfigXml: String) : ClickableNode(currentConfigXml) {
    var file: String = ""

    var fileSh: String = ""
    var placeholder: String? = null

    var wrap: Boolean = true
    var run: Boolean = true

    var readonly: Boolean = false

    var needInput: Boolean = false

    var valueSh: String = ""

    var value: String = ""

    val rows = ArrayList<TextNode.TextRow>()
}
