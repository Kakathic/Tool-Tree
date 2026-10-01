package com.omarea.krscript.model

import com.omarea.common.model.SelectItem

class PickerNode(currentConfigXml: String) : RunnableNode(currentConfigXml) {
    var options: ArrayList<SelectItem>? = null
    var optionsSh = ""
    var value: String? = null

    var getState: String? = null

    var name: String = ""
    var multiple: Boolean = false
    var separator: String = "\n"

    val rows = ArrayList<TextNode.TextRow>()
}
