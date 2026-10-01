package com.omarea.krscript.model

import com.omarea.common.model.SelectItem
import java.io.Serializable

class ActionParamInfo : Serializable {
    var name: String? = null

    var title: String? = null
    var titleSh: String? = null

    var label: String? = null
    var labelSh: String? = null

    var desc: String? = null
    var descSh: String? = null

    var descOn: String? = null
    var descOnSh: String? = null

    var value: String? = null
    var valueShell: String? = null
    var valueFromShell: String? = null
    var maxLength = -1
    var type: String? = null
    var max: Int = Int.MAX_VALUE
    var min: Int = Int.MIN_VALUE
    var required: Boolean = false
    var readonly: Boolean = false
    var readonlySh: String? = null

    var sort: Boolean = false
    var options: ArrayList<SelectItem>? = null
    var optionsFromShell: ArrayList<SelectItem>? = null
    var optionsSh = ""
    var multiple: Boolean = false
    var supported: Boolean = true
    var placeholder: String = ""
    var placeholderSh: String? = null
    var mime: String = ""
    var suffix: String = ""
    var pathHome: String = ""
    var editable: Boolean = false
    var separator: String = "\n"

    var dependOn: String? = null

    var dependValue: String? = null

    var dependMode: String = "show"

    var dependLogic: String = "and"

    var dependDefault: String = "show"

    var dependInitialState: String = "auto"

    var dependNegate: Boolean = false

    var dependThreshold: Int = -1

    var dependIncludeHidden: Boolean = true

    var dependCascade: Boolean = true

    var dependOnChangeCallback: String? = null

    var dependReadonly: Boolean = false

    var dependSort: Boolean = false

    var allowNoSelection: Boolean = false

    var remember: Boolean = true
}