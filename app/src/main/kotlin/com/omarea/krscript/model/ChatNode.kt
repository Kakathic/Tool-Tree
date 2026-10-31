package com.omarea.krscript.model

class ChatNode(currentConfigXml: String) : RunnableNode(currentConfigXml) {
    var script: String = ""
    var chatTitle: String = ""
    var greeting: String = ""
    var placeholder: String = ""
    var maxLines: Int = 6
    var session: Boolean = false
    var init: String = ""
    var cwd: String = ""
    var root: Int = 0
    var menuOptions: ArrayList<PageMenuOption> = ArrayList()
    var paramsNode: ActionNode? = null
}
