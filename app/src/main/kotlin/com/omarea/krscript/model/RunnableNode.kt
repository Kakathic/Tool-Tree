package com.omarea.krscript.model

open class RunnableNode(currentConfigXml: String) : ClickableNode(currentConfigXml) {

    var confirm: Boolean = false
    var warning: String = ""
    var warningSh: String = ""
    var autoOff: Boolean = false
    var interruptable: Boolean = true
    var reloadPage: Boolean = false
    var updateBlocks: Array<String>? = null
    var autoFinish = false
    var autoKill = false
    var autoRestart = false
    var needInput: Boolean = false

    var shell = shellModeDefault

    companion object {
        val shellModeDefault = "default"
        val shellModeBgTask = "bg-task"
        val shellModeHidden = "hidden"
    }

    var setState: String? = null
}
