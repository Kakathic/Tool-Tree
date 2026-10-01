package com.omarea.krscript.model

class DownloadNode(currentConfigXml: String) : RunnableNode(currentConfigXml) {
    var url: String = ""

    var urlSh: String = ""

    var urlResolved: Boolean = false

    val rows = ArrayList<TextNode.TextRow>()
}
