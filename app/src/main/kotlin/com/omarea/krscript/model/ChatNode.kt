package com.omarea.krscript.model

class ChatNode(currentConfigXml: String) : RunnableNode(currentConfigXml) {
    var script: String = ""
    var chatTitle: String = ""
    var userTitle: String = ""
    var greeting: String = ""
    var placeholder: String = ""
    var maxLines: Int = 6
    var menuOptions: ArrayList<PageMenuOption> = ArrayList()
    var paramsNode: ActionNode? = null
}
