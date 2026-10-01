package com.omarea.krscript.ui

import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.omarea.common.model.SelectItem
import com.omarea.common.ui.DialogItemChooser
import com.tool.tree.R
import com.omarea.krscript.model.ActionParamInfo
import android.content.res.Configuration
import com.tool.tree.ThemeModeState

class ParamsMultipleSelect(
    private val actionParamInfo: ActionParamInfo,
    private val context: FragmentActivity,
    private val onValueChanged: (() -> Unit)? = null
) {
    private var options: ArrayList<SelectItem>? = null
    private var status = booleanArrayOf()
    private var labels: Array<String?> = arrayOf()
    private var values: Array<String?> = arrayOf()
    private val darkMode: Boolean = ThemeModeState.isDarkMode()

    private var lastOpenTime: Long = 0

    fun getValue(): String {
        val result = ArrayList<String?>()
        for (index in status.indices) {
            if (status[index]) {
                values.getOrNull(index)?.let { result.add(it) }
            }
        }
        return result.joinToString(actionParamInfo.separator)
    }

    fun render(): View {
        options = actionParamInfo.optionsFromShell
        options?.run {
            labels = map { it.title }.toTypedArray()
            values = map { it.value }.toTypedArray()
            status = ActionParamsLayoutRender.getParamOptionsSelectedStatus(actionParamInfo, this)
        }

        val layout = LayoutInflater.from(context).inflate(R.layout.kr_param_multiple_select, null)
        val textView = layout.findViewById<TextView>(R.id.kr_param_label_text)
        val valueView = layout.findViewById<TextView>(R.id.kr_param_value_text)
        val countView = layout.findViewById<TextView>(R.id.kr_param_count_text)

        valueView.tag = actionParamInfo.name

        setView(textView, valueView, countView)

        val isEmptyOptions = options.isNullOrEmpty()
        val enabled = !actionParamInfo.readonly && !isEmptyOptions

        textView.isEnabled = enabled
        textView.isClickable = enabled
        textView.isFocusable = enabled

        if (isEmptyOptions) {
            textView.text = null
            textView.hint = context.getString(R.string.picker_not_item)
        } else {
            textView.setOnClickListener {
                openDialog(textView, valueView, countView)
            }
        }

        return layout
    }

    private fun setView(textView: TextView, valueView: TextView, countView: TextView) {
        val resultValues = ArrayList<String?>()
        val resultLables = ArrayList<String?>()
        var count = 0
        for (index in status.indices) {
            if (status[index]) {
                values[index]?.run {
                    resultValues.add(this)
                }
                labels[index]?.run {
                    resultLables.add(this)
                }
                count++
            }
        }
        val resultValueStr = "" + resultValues.joinToString(actionParamInfo.separator)
        val resultLabelStr = if (resultLables.isNotEmpty()) "" + resultLables.joinToString("，") else ""

        if (resultLabelStr.isNotEmpty()) {
            textView.text = resultLabelStr
            textView.hint = null
        } else {
            textView.text = ""
            textView.hint = if (options.isNullOrEmpty()) {
                context.getString(R.string.picker_not_item)
            } else {
                context.getString(R.string.kr_please_select)
            }
        }
        
        valueView.text = resultValueStr
        countView.text = count.toString()
    }

    private fun openDialog(textView: TextView, valueView: TextView, countView: TextView) {
        val dialogTag = "params-multi-select"

        if (context.supportFragmentManager.findFragmentByTag(dialogTag) != null) {
            return
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastOpenTime < 800) {
            return
        }
        lastOpenTime = currentTime

        options?.run {
            val items = ArrayList<SelectItem>()
            for (i in labels.indices) {
                items.add(SelectItem().apply {
                    title = "" + labels[i]
                    selected = status[i]
                })
            }
            DialogItemChooser(darkMode, ArrayList(items), true, object : DialogItemChooser.Callback {
                override fun onConfirm(selected: List<SelectItem>, result: BooleanArray) {
                    result.forEachIndexed { index, value ->
                        status[index] = value
                    }
                    setView(textView, valueView, countView)
                    onValueChanged?.invoke()
                }
            }).show(context.supportFragmentManager, dialogTag)
        }
    }
}
