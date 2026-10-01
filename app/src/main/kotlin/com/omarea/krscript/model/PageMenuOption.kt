package com.omarea.krscript.model

import com.omarea.common.model.SelectItem

class PageMenuOption(currentConfigXml: String) : RunnableNode(currentConfigXml) {
    var type: String = ""
    var isFab = false

    var mime: String = ""
    var suffix: String = ""
    var pathHome: String = ""
    var multiple: Boolean = false

    var checkedSh: String = ""

    @Volatile
    var checked: Boolean = false
    var silent: Boolean = false
    var link: String = ""
    var activity: String = ""
    var onlineHtmlPage: String = ""
    var pageConfigPath: String = ""
    var pageConfigSh: String = ""
    var script: String = ""

    var options: ArrayList<SelectItem>? = null
    var optionsSh: String = ""
    var spinnerGetState: String = ""
}