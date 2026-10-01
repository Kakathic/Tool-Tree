package com.omarea.krscript.model

import java.util.*

class ActionNode(currentConfigXml: String) : RunnableNode(currentConfigXml){
    var params: ArrayList<ActionParamInfo>? = null
    val rows = ArrayList<TextNode.TextRow>()
    val paramsRows = ArrayList<TextNode.TextRow>()

    var menu: Boolean = false

    var show: Boolean = false
}
