package com.omarea.krscript.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.CompoundButton
import com.tool.tree.R
import com.omarea.krscript.model.ActionParamInfo
import java.util.Locale.getDefault

class ParamsSwitch(private var actionParamInfo: ActionParamInfo, private var context: Context) {
    fun render(): View {
        val layout = LayoutInflater.from(context).inflate(R.layout.kr_param_switch, null)

        layout.findViewById<CompoundButton>(R.id.kr_param_switch).run {
            tag = actionParamInfo.name
            isChecked = getCheckState(actionParamInfo, false)
            if (!actionParamInfo.label.isNullOrEmpty()) {
                text = actionParamInfo.label
            }

            setOnClickListener {

            }
        }

        return layout
    }

    private fun getCheckState(actionParamInfo: ActionParamInfo, defaultValue: Boolean): Boolean {
        if (actionParamInfo.valueFromShell != null) {
            return actionParamInfo.valueFromShell == "1" || actionParamInfo.valueFromShell!!.lowercase(
                getDefault()
            ) == "true"
        } else if (actionParamInfo.value != null) {
            return actionParamInfo.value == "1" || actionParamInfo.value!!.lowercase(getDefault()) == "true"
        }
        return defaultValue
    }
}
