package com.omarea.common.model

import java.io.Serializable

class SelectItem : Serializable {
    var title: String? = null
    var titleSh: String? = null
    var value: String? = null
    var selected: Boolean = false

    override fun toString(): String {
        return if (!title.isNullOrEmpty()) {
            title!!
        } else if (!value.isNullOrEmpty()) {
            value!!
        } else {
            ""
        }
    }
}