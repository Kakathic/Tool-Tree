package com.omarea.krscript.model

class PageNode(currentConfigXml: String) : ClickableNode(currentConfigXml) {
    var pageConfigPath: String = ""
    var pageConfigSh: String = ""
    var onlineHtmlPage: String = ""
    var link: String = ""
    var activity: String = ""

    var beforeRead = ""
    var afterRead = ""

    var pageMenuOptions: ArrayList<PageMenuOption>? = null
    var headerActions: ArrayList<ActionNode>? = null
    var autoShowActions: ArrayList<ActionNode>? = null
    var menuIconNode: ClickableNode? = null
    var fabIconNode: ClickableNode? = null

    val rows = ArrayList<TextNode.TextRow>()

    var loadSuccess = ""
    var loadFail = ""

    var process: Boolean = false
    var placeholderCount: Int = 1
}
