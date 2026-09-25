package com.omarea.krscript.ui

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListPopupWindow
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.omarea.common.model.SelectItem
import com.omarea.common.ui.DialogItemChooser
import com.tool.tree.R
import com.omarea.krscript.model.ActionParamInfo
import com.tool.tree.ThemeModeState
import com.tool.tree.ui.SpinnerPopupHelper

class ParamsSingleSelect(
        private var actionParamInfo: ActionParamInfo,
        private var context: FragmentActivity,
        private val onValueChanged: (() -> Unit)? = null
) {

    private val darkMode: Boolean = ThemeModeState.isDarkMode()
    val options = actionParamInfo.optionsFromShell!!
    var selectedIndex = ActionParamsLayoutRender.getParamOptionsCurrentIndex(actionParamInfo, options)

    private var lastOpenTime: Long = 0
    private var anchorView: TextView? = null
    private var editTextView: EditText? = null

    fun getValue(): String {
        editTextView?.let {
            return it.text?.toString() ?: ""
        }
        return if (selectedIndex > -1 && selectedIndex < options.size) options[selectedIndex].value ?: "" else ""
    }

    private fun updateAnchorText(anchor: TextView) {
        if (selectedIndex > -1 && selectedIndex < options.size) {
            anchor.text = options[selectedIndex].title
            anchor.hint = null
        } else {
            anchor.text = null
            anchor.hint = context.getString(
                    if (options.isEmpty()) R.string.picker_not_item else R.string.kr_please_select
            )
        }
    }

    // Khi allowNoSelection = true, chèn thêm 1 dòng "Không chọn" ở đầu danh sách hiển thị
    // (không đụng vào `options`/optionsFromShell gốc vì đây là list dùng chung).
    // indexOffset dùng để quy đổi vị trí trong danh sách hiển thị <-> selectedIndex thực (-1 = chưa chọn).
    private val indexOffset: Int
        get() = if (actionParamInfo.allowNoSelection) 1 else 0

    private fun displayOptions(): List<SelectItem> {
        if (!actionParamInfo.allowNoSelection) {
            return options
        }
        return ArrayList<SelectItem>(options.size + 1).apply {
            add(SelectItem().apply {
                title = context.getString(R.string.kr_no_selection)
                value = ""
            })
            addAll(options)
        }
    }

    fun render(): View {
        if (actionParamInfo.editable) {
            return renderEditable()
        }
        return renderNonEditable()
    }

    private fun renderNonEditable(): View {
        val layout = LayoutInflater.from(context).inflate(R.layout.kr_param_spinner_edit, null) as ViewGroup
        val anchor = layout.findViewById<EditText>(R.id.kr_param_spinner_edit)
        val btn = layout.findViewById<View>(R.id.kr_param_spinner_btn)

        anchor.isFocusable = false
        anchor.isFocusableInTouchMode = false
        anchor.isCursorVisible = false
        anchor.isLongClickable = false
        anchor.keyListener = null

        val allowNoSelection = actionParamInfo.allowNoSelection
        if (!allowNoSelection && (selectedIndex < 0 || selectedIndex >= options.size) && options.isNotEmpty()) {
            selectedIndex = 0
        }

        val valueHolder = TextView(context).apply {
            id = R.id.kr_param_value
            tag = actionParamInfo.name
            visibility = View.GONE
            text = getValue()
        }
        layout.addView(valueHolder)

        anchorView = anchor
        updateAnchorText(anchor)

        val enabled = !actionParamInfo.readonly && options.isNotEmpty()
        anchor.isEnabled = enabled
        anchor.isClickable = enabled
        btn.isEnabled = enabled

        if (enabled) {
            val showOptions = {
                if (options.size > 6) {
                    openSingleSelectDialog(anchor, valueHolder)
                } else {
                    openSingleSelectPopup(anchor, valueHolder)
                }
            }
            anchor.setOnClickListener { showOptions() }
            btn.setOnClickListener { showOptions() }
        }

        return layout
    }

    private fun openSingleSelectPopup(anchor: TextView, valueHolder: TextView) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastOpenTime < 800) {
            return
        }
        lastOpenTime = currentTime

        val items = displayOptions()
        val adapter = ArrayAdapter(context, R.layout.kr_spinner_dropdown, R.id.text, items)
        val background = context.getDrawable(R.drawable.kr_spinner_popup_bg)

        val popup = ListPopupWindow(context)
        popup.anchorView = anchor
        popup.setAdapter(adapter)
        popup.setBackgroundDrawable(background)
        popup.isModal = true
        popup.setOnItemClickListener { _, _, position, _ ->
            selectedIndex = position - indexOffset
            updateAnchorText(anchor)
            valueHolder.text = getValue()
            onValueChanged?.invoke()
            popup.dismiss()
        }

        val inflater = LayoutInflater.from(context)
        val itemViews = items.map { item ->
            inflater.inflate(R.layout.kr_spinner_dropdown, anchor.parent as? ViewGroup, false).apply {
                findViewById<TextView>(R.id.text).text = item.title
            }
        }
        val minWidthPx = (context.resources.displayMetrics.density * 220).toInt()
        SpinnerPopupHelper.applyWidthAndPosition(
            popup, anchor, itemViews, background, minWidthPx, alignRight = false
        )

        popup.show()
        SpinnerPopupHelper.applyRoundedClip(popup, context.resources.getDimension(R.dimen.kr_spinner_popup_radius))
        if (selectedIndex > -1 && selectedIndex < options.size) {
            popup.listView?.setSelection(selectedIndex + indexOffset)
        } else if (indexOffset > 0) {
            popup.listView?.setSelection(0)
        }
    }

    private fun openSingleSelectDialog(anchor: TextView, valueHolder: TextView) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastOpenTime < 800) {
            return
        }
        lastOpenTime = currentTime

        val items = displayOptions()
        DialogItemChooser(darkMode, ArrayList(items.mapIndexed { index, item ->
            SelectItem().apply {
                title = item.title
                selected = index == selectedIndex + indexOffset
            }
        }), false, object : DialogItemChooser.Callback {
            override fun onConfirm(selected: List<SelectItem>, status: BooleanArray) {
                val rawIndex = status.indexOf(true)
                if (rawIndex == -1) {
                    return
                }
                val confirmedIndex = rawIndex - indexOffset
                if (confirmedIndex >= -1 && confirmedIndex < options.size) {
                    selectedIndex = confirmedIndex
                    updateAnchorText(anchor)
                    valueHolder.text = getValue()
                    onValueChanged?.invoke()
                }
            }
        }).show(context.supportFragmentManager, "params-single-select")
    }

    private fun renderEditable(): View {
        val layout = LayoutInflater.from(context).inflate(R.layout.kr_param_spinner_edit, null)
        val editText = layout.findViewById<EditText>(R.id.kr_param_spinner_edit)
        val btn = layout.findViewById<View>(R.id.kr_param_spinner_btn)

        editText.tag = actionParamInfo.name
        editTextView = editText

        val initialValue = actionParamInfo.valueFromShell
            ?: actionParamInfo.value
            ?: ""
        if (initialValue.isNotEmpty()) {
            editText.setText(initialValue)
            val matchIndex = options.indexOfFirst { it.value == initialValue }
            selectedIndex = if (matchIndex >= 0) matchIndex else -1
        } else if (!actionParamInfo.allowNoSelection && options.isNotEmpty()) {
            selectedIndex = 0
            editText.setText(options[0].value ?: "")
        }

        if (actionParamInfo.placeholder.isNotEmpty()) {
            editText.hint = actionParamInfo.placeholder
        }

        val enabled = !actionParamInfo.readonly && options.isNotEmpty()
        editText.isEnabled = !actionParamInfo.readonly
        btn.isEnabled = enabled
        if (enabled) {
            btn.setOnClickListener {
                if (options.size > 6) {
                    openSingleSelectDialogEditable(editText)
                } else {
                    openSingleSelectPopupEditable(editText)
                }
            }
        }

        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: ""
                val matchIndex = options.indexOfFirst { it.value == text }
                if (matchIndex >= 0) {
                    selectedIndex = matchIndex
                }
                onValueChanged?.invoke()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        return layout
    }

    private fun openSingleSelectPopupEditable(editText: EditText) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastOpenTime < 800) {
            return
        }
        lastOpenTime = currentTime

        val items = displayOptions()
        val adapter = ArrayAdapter(context, R.layout.kr_spinner_dropdown, R.id.text, items)
        val background = context.getDrawable(R.drawable.kr_spinner_popup_bg)

        val popup = ListPopupWindow(context)
        popup.anchorView = editText
        popup.setAdapter(adapter)
        popup.setBackgroundDrawable(background)
        popup.isModal = true
        popup.setOnItemClickListener { _, _, position, _ ->
            selectedIndex = position - indexOffset
            val selectedValue = if (selectedIndex > -1) options.getOrNull(selectedIndex)?.value ?: "" else ""
            editText.setText(selectedValue)
            editText.setSelection(editText.text?.length ?: 0)
            onValueChanged?.invoke()
            popup.dismiss()
        }

        val inflater = LayoutInflater.from(context)
        val itemViews = items.map { item ->
            inflater.inflate(R.layout.kr_spinner_dropdown, editText.parent as? ViewGroup, false).apply {
                findViewById<TextView>(R.id.text).text = item.title
            }
        }
        val minWidthPx = (context.resources.displayMetrics.density * 220).toInt()
        SpinnerPopupHelper.applyWidthAndPosition(
            popup, editText, itemViews, background, minWidthPx, alignRight = false
        )

        popup.show()
        SpinnerPopupHelper.applyRoundedClip(popup, context.resources.getDimension(R.dimen.kr_spinner_popup_radius))
        if (selectedIndex > -1 && selectedIndex < options.size) {
            popup.listView?.setSelection(selectedIndex + indexOffset)
        } else if (indexOffset > 0) {
            popup.listView?.setSelection(0)
        }
    }

    private fun openSingleSelectDialogEditable(editText: EditText) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastOpenTime < 800) {
            return
        }
        lastOpenTime = currentTime

        val items = displayOptions()
        DialogItemChooser(darkMode, ArrayList(items.mapIndexed { index, item ->
            SelectItem().apply {
                title = item.title
                selected = index == selectedIndex + indexOffset
            }
        }), false, object : DialogItemChooser.Callback {
            override fun onConfirm(selected: List<SelectItem>, status: BooleanArray) {
                val rawIndex = status.indexOf(true)
                if (rawIndex == -1) {
                    return
                }
                val confirmedIndex = rawIndex - indexOffset
                if (confirmedIndex >= -1 && confirmedIndex < options.size) {
                    selectedIndex = confirmedIndex
                    editText.setText(if (confirmedIndex > -1) options[confirmedIndex].value ?: "" else "")
                    editText.setSelection(editText.text?.length ?: 0)
                    onValueChanged?.invoke()
                }
            }
        }).show(context.supportFragmentManager, "params-single-select-edit")
    }
}
