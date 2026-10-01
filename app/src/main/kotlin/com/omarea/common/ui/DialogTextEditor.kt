package com.omarea.common.ui

import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.widget.EditText
import android.widget.TextView
import com.tool.tree.R

class DialogTextEditor(
    darkMode: Boolean,
    private val title: String?,
    private val initialText: String,
    private val callback: Callback? = null
) : DialogFullScreen(R.layout.dialog_text_editor, darkMode) {

    interface Callback {
        fun onConfirm(text: String)
    }

    private var editText: EditText? = null

    private var editHint: CharSequence? = null
    private var editInputType: Int? = null
    private var editFilters: List<InputFilter> = emptyList()

    fun setHint(hint: CharSequence?) {
        editHint = hint
    }

    fun setInputType(type: Int) {
        editInputType = type
    }

    fun setFilters(filters: Array<out InputFilter>) {
        editFilters = filters.toList()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val titleView = view.findViewById<TextView>(R.id.dialog_title)
        if (!title.isNullOrEmpty()) {
            titleView.text = title
            titleView.visibility = View.VISIBLE
        } else {
            titleView.visibility = View.GONE
        }

        editText = view.findViewById<EditText>(R.id.kr_text_editor_content).apply {
            editHint?.let { hint = it }
            editInputType?.let { inputType = it }
            if (editFilters.isNotEmpty()) {
                filters = editFilters.toTypedArray()
            }

            setText(initialText)
            setSelection(text?.length ?: 0)
            requestFocus()
        }

        view.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            dismiss()
        }
        view.findViewById<View>(R.id.btn_confirm).setOnClickListener {
            callback?.onConfirm(editText?.text?.toString() ?: "")
            dismiss()
        }
    }
}
