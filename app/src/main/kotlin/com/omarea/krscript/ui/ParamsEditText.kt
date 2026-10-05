package com.omarea.krscript.ui

import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import androidx.fragment.app.FragmentActivity
import com.omarea.common.ui.DialogTextEditor
import com.tool.tree.R
import com.omarea.krscript.model.ActionParamInfo
import com.omarea.krscript.model.ParamInfoFilter
import com.tool.tree.ThemeModeState

class ParamsEditText(private var actionParamInfo: ActionParamInfo, private var context: FragmentActivity) {
    private val darkMode: Boolean = ThemeModeState.isDarkMode()

    fun render(): View {
        if (actionParamInfo.type == "password") return renderPassword()

        val layout = LayoutInflater.from(context).inflate(R.layout.kr_param_text, null)
        val expandBtn = layout.findViewById<View>(R.id.kr_param_text_expand)
        val activity = context

        val isNumber = actionParamInfo.type == "int" || actionParamInfo.type == "number"
        val paramFilter = ParamInfoFilter(actionParamInfo)

        layout.findViewById<EditText>(R.id.kr_param_text).run {
            tag = actionParamInfo.name
            if (actionParamInfo.valueFromShell != null)
                setText(actionParamInfo.valueFromShell)
            else if (actionParamInfo.value != null) {
                setText(actionParamInfo.value)
            }
            filters = arrayOf(paramFilter)
            
            if (isNumber) {
                inputType = if (actionParamInfo.type == "int") {
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                } else {
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                }
            }

            isEnabled = !actionParamInfo.readonly

            val placeholderText = when {
                actionParamInfo.placeholder.isNotEmpty() -> actionParamInfo.placeholder
                isNumber && (actionParamInfo.min != Int.MIN_VALUE || actionParamInfo.max != Int.MAX_VALUE) -> "${actionParamInfo.min} ~ ${actionParamInfo.max}"
                else -> null
            }
            
            if (!placeholderText.isNullOrEmpty()) {
                hint = placeholderText
            }

            expandBtn.setOnClickListener {
                val editText = this
                val titleParts = listOfNotNull(
                        actionParamInfo.title?.ifEmpty { null },
                        actionParamInfo.label?.ifEmpty { null }
                )
                val dialogTitle = if (titleParts.isNotEmpty()) {
                    titleParts.joinToString(" ")
                } else {
                    null
                }

                val dialogInputType = if (isNumber) {
                    if (actionParamInfo.type == "int") {
                        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                    } else {
                        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                    }
                } else {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                }

                DialogTextEditor(
                        darkMode,
                        dialogTitle,
                        editText.text?.toString() ?: "",
                        object : DialogTextEditor.Callback {
                            override fun onConfirm(text: String) {
                                editText.setText(text)
                                editText.setSelection(editText.text?.length ?: 0)
                            }
                        }
                ).apply {
                    setInputType(dialogInputType)
                    setFilters(arrayOf(paramFilter))
                    setHint(editText.hint)
                }.show(activity.supportFragmentManager, "params-text-editor")
            }
        }

        return layout
    }

    private fun renderPassword(): View {
        val layout = LayoutInflater.from(context).inflate(R.layout.kr_param_password, null)
        val toggleBtn = layout.findViewById<View>(R.id.kr_param_password_toggle)
        val toggleIcon = layout.findViewById<ImageView>(R.id.kr_param_password_toggle_icon)
        val hiddenType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val shownType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD

        layout.findViewById<EditText>(R.id.kr_param_text).run {
            tag = actionParamInfo.name
            if (actionParamInfo.valueFromShell != null)
                setText(actionParamInfo.valueFromShell)
            else if (actionParamInfo.value != null) {
                setText(actionParamInfo.value)
            }
            filters = arrayOf(ParamInfoFilter(actionParamInfo))
            inputType = hiddenType

            isEnabled = !actionParamInfo.readonly

            if (actionParamInfo.placeholder.isNotEmpty()) {
                hint = actionParamInfo.placeholder
            }

            var visible = false
            toggleBtn.setOnClickListener {
                visible = !visible
                val cursor = selectionStart
                inputType = if (visible) shownType else hiddenType
                toggleIcon.setImageResource(if (visible) R.drawable.ic_visibility else R.drawable.ic_visibility_off)
                if (cursor >= 0) setSelection(cursor.coerceAtMost(text?.length ?: 0))
            }
        }

        return layout
    }
}
