package com.omarea.krscript.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import com.tool.tree.R
import com.omarea.krscript.model.ActionParamInfo

class ParamsFileChooserRender(
    private var actionParamInfo: ActionParamInfo,
    private var context: Context,
    private var fileChooser: FileChooserInterface?,
    private val onValueChanged: (() -> Unit)? = null) {
    interface FileChooserInterface {
        fun openFileChooser(fileSelectedInterface: FileSelectedInterface): Boolean
    }

    interface FileSelectedInterface {
        companion object {
            val TYPE_FILE: Int
                get() = 0
            val TYPE_FOLDER: Int
                get() = 1
        }

        fun onFileSelected(path: String?)
        fun mimeType():String?
        fun suffix():String?
        fun type(): Int
        fun multiple(): Boolean = false
        fun separator(): String = "\n"
        fun pathHome(): String? = null
    }

    fun setEditTextReadOnly(view: TextView) {
        if (view is EditText) {
            view.isCursorVisible = false
            view.setFocusable(false)
            view.isFocusableInTouchMode = false
        }
    }

    fun render(): View {
        val layout = LayoutInflater.from(context).inflate(R.layout.kr_param_file, null)
        val textView = layout.findViewById<TextView>(R.id.kr_param_file_text)
        val pathView = layout.findViewById<EditText>(R.id.kr_param_file_path)
        val btn = layout.findViewById<View>(R.id.kr_param_file_btn)

        val hintText = if (actionParamInfo.type == "folder") {
            context.getString(R.string.kr_please_choose_folder)
        } else {
            context.getString(R.string.kr_please_choose_file)
        }
        
        if (actionParamInfo.editable) {
            textView.visibility = View.GONE
            pathView.visibility = View.VISIBLE
            pathView.hint = hintText
        } else {
            textView.visibility = View.GONE
            pathView.visibility = View.VISIBLE
            pathView.hint = hintText
            setEditTextReadOnly(pathView)
        }

        val openChooser = {
            fileChooser?.openFileChooser(object : FileSelectedInterface {
                override fun onFileSelected(path: String?) {
                    if (path.isNullOrEmpty()) {
                        if (type() == FileSelectedInterface.TYPE_FOLDER) {
                            textView.text = context.getString(R.string.kr_please_choose_folder)
                        } else {
                            textView.text = context.getString(R.string.kr_please_choose_file)
                        }
                        pathView.setText("")
                    } else {
                        textView.text = path
                        pathView.setText(path)
                    }
                    onValueChanged?.invoke()
                }

                override fun mimeType(): String? {
                    if (actionParamInfo.mime.isNotEmpty()) {
                        return actionParamInfo.mime
                    }
                    return null
                }

                override fun suffix(): String? {
                    if (actionParamInfo.suffix.isNotEmpty()) {
                        return actionParamInfo.suffix
                    }
                    return null
                }

                override fun type(): Int {
                    return when(actionParamInfo.type) {
                        "folder" -> FileSelectedInterface.TYPE_FOLDER
                        else -> FileSelectedInterface.TYPE_FILE
                    }
                }

                override fun multiple(): Boolean {
                    return actionParamInfo.multiple
                }

                override fun separator(): String {
                    return actionParamInfo.separator
                }

                override fun pathHome(): String? {
                    return actionParamInfo.pathHome.ifEmpty { null }
                }
            })
        }

        btn.setOnClickListener { openChooser() }
        if (!actionParamInfo.editable) {
            pathView.setOnClickListener { openChooser() }
        }

        if (actionParamInfo.valueFromShell != null) {
            textView.text = actionParamInfo.valueFromShell
            pathView.setText(actionParamInfo.valueFromShell)
        } else if (!actionParamInfo.value.isNullOrEmpty()) {
            textView.text = actionParamInfo.value
            pathView.setText(actionParamInfo.value)
        }

        pathView.tag = actionParamInfo.name

        if (actionParamInfo.editable) {
            pathView.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) = onValueChanged?.invoke() ?: Unit
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }

        return layout
    }
}
