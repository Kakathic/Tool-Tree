package com.omarea.krscript.model

import java.util.*

class ActionNode(currentConfigXml: String) : RunnableNode(currentConfigXml){
    var params: ArrayList<ActionParamInfo>? = null
    var paramsSh: String = ""
    val rows = ArrayList<TextNode.TextRow>()
    val paramsRows = ArrayList<TextNode.TextRow>()

    var menu: Boolean = false

    var show: Boolean = false

    var keepParamsDialog: Boolean = false

    var shellBy: List<String> = emptyList()
    var shellValue: List<String> = emptyList()
    var shellMode: List<String> = emptyList()
}
