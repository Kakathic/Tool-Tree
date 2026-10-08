package com.omarea.krscript.ui

import android.animation.TimeInterpolator
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.*
import androidx.fragment.app.FragmentActivity
import com.omarea.common.model.SelectItem
import com.tool.tree.R
import com.omarea.krscript.model.ActionParamInfo
import com.omarea.krscript.executor.ScriptEnvironmen
import androidx.core.graphics.toColorInt

class ActionParamsLayoutRender(private var linearLayout: LinearLayout, activity: FragmentActivity) {
    companion object {
        private const val ROW_ANIM_DURATION_MS = 300L

        private fun standardMotionInterpolator(): TimeInterpolator {
            return PathInterpolator(0.4f, 0f, 0.2f, 1f)
        }

        fun getParamOptionsCurrentIndex(actionParamInfo: ActionParamInfo, options: ArrayList<SelectItem>): Int {
            var selectedIndex = -1

            val valList = ArrayList<String>()
            if (actionParamInfo.valueFromShell != null)
                valList.add(actionParamInfo.valueFromShell!!)
            if (actionParamInfo.value != null) {
                valList.add(actionParamInfo.value!!)
            }
            if (valList.isNotEmpty()) {
                for (j in valList.indices) {
                    var index = 0
                    for (option in options) {
                        if (option.value == valList[j]) {
                            selectedIndex = index
                            break
                        }
                        index++
                    }
                    if (selectedIndex > -1)
                        break
                }
            }
            return selectedIndex
        }

        fun getParamOptionsSelectedStatus(actionParamInfo: ActionParamInfo, options: ArrayList<SelectItem>): BooleanArray {
            val status = BooleanArray(options.size)
            val values = getParamValues(actionParamInfo)

            for (index in 0 until options.size) {
                val option = options[index]
                status[index] = (values != null && values.contains(option.value))
            }
            return status
        }

        fun setParamOptionsSelectedStatus(actionParamInfo: ActionParamInfo, options: ArrayList<SelectItem>): ArrayList<SelectItem> {
            val values = getParamValues(actionParamInfo)

            for (index in 0 until options.size) {
                val option = options[index]
                options[index].selected = (values != null && values.contains(option.value))
            }
            return options
        }

        fun getParamValues (actionParamInfo: ActionParamInfo): List<String>? {
            val value = if (actionParamInfo.valueFromShell != null) actionParamInfo.valueFromShell else actionParamInfo.value
            val values = value?.split(actionParamInfo.separator)
            return values
        }
    }

    private var context: FragmentActivity = activity

    private val rowViews = HashMap<String, View>()
    private val valueReaders = HashMap<String, () -> String>()
    private var currentParamInfos: ArrayList<ActionParamInfo> = ArrayList()
    
    private val visibilityState = HashMap<String, Boolean>()

    fun renderList(actionParamInfos: ArrayList<ActionParamInfo>, fileChooser: ParamsFileChooserRender.FileChooserInterface?) {
        val seenNames = HashSet<String>()
        val duplicateNames = LinkedHashSet<String>()
        val dedupedParamInfos = ArrayList<ActionParamInfo>(actionParamInfos.size)
        for (info in actionParamInfos) {
            val name = info.name
            if (name == null) {
                dedupedParamInfos.add(info)
                continue
            }
            if (seenNames.add(name)) {
                dedupedParamInfos.add(info)
            } else {
                duplicateNames.add(name)
            }
        }
        if (duplicateNames.isNotEmpty()) {
            Toast.makeText(
                context,
                context.getString(R.string.kr_duplicate_param_name, duplicateNames.joinToString(", ")),
                Toast.LENGTH_LONG
            ).show()
        }

        currentParamInfos = dedupedParamInfos
        rowViews.clear()
        valueReaders.clear()
        visibilityState.clear()

        for (actionParamInfo in dedupedParamInfos) {
            val options = actionParamInfo.optionsFromShell
            if (options != null && !(actionParamInfo.type == "app" || actionParamInfo.type == "packages")) {
                if (actionParamInfo.multiple) {
                    val widget = ParamsMultipleSelect(actionParamInfo, context) { evaluateDependencies() }
                    val view = widget.render()
                    addToLayout(view, actionParamInfo)
                    actionParamInfo.name?.let { valueReaders[it] = { widget.getValue() } }
                } else {
                    val widget = ParamsSingleSelect(actionParamInfo, context) { evaluateDependencies() }
                    val view = widget.render()
                    addToLayout(view, actionParamInfo)
                    actionParamInfo.name?.let { valueReaders[it] = { widget.getValue() } }
                }
            }
            else if (actionParamInfo.type == "bool" || actionParamInfo.type == "checkbox") {
                val view = ParamsCheckbox(actionParamInfo, context).render()
                addToLayout(view, actionParamInfo)
                attachDefaultListener(view, actionParamInfo)
            }
            else if (actionParamInfo.type == "switch") {
                val view = ParamsSwitch(actionParamInfo, context).render()
                addToLayout(view, actionParamInfo)
                attachDefaultListener(view, actionParamInfo)
            }
            else if (actionParamInfo.type == "seekbar") {
                val layout = ParamsSeekBar(actionParamInfo, context) { evaluateDependencies() }.render()

                addToLayout(layout, actionParamInfo)
                actionParamInfo.name?.let { name ->
                    valueReaders[name] = {
                        val seekBar = linearLayout.findViewWithTag<SeekBar?>(name)
                        if (seekBar != null) (seekBar.progress + actionParamInfo.min).toString() else ""
                    }
                }
            }
            else if (actionParamInfo.type == "file" || actionParamInfo.type == "folder") {
                val layout = ParamsFileChooserRender(actionParamInfo, context, fileChooser) { evaluateDependencies() }.render()

                addToLayout(layout, actionParamInfo)
                actionParamInfo.name?.let { name ->
                    valueReaders[name] = { linearLayout.findViewWithTag<TextView?>(name)?.text?.toString() ?: "" }
                }
            }
            else if (actionParamInfo.type == "app" || actionParamInfo.type == "packages") {
                val layout = ParamsAppChooserRender(actionParamInfo, context) { evaluateDependencies() }.render()

                addToLayout(layout, actionParamInfo)
                actionParamInfo.name?.let { name ->
                    valueReaders[name] = { linearLayout.findViewWithTag<TextView?>(name)?.text?.toString() ?: "" }
                }
            }
            else if (actionParamInfo.type == "color") {
                val layout = ParamsColorPicker(actionParamInfo, context).render()

                addToLayout(layout, actionParamInfo)
                attachDefaultListener(layout, actionParamInfo)
            }
            else {
                val view = ParamsEditText(actionParamInfo, context).render()
                addToLayout(view, actionParamInfo)
                attachDefaultListener(view, actionParamInfo)
            }
        }

        applyStaticReadonlySort()

        initializeDependencyStates()
        evaluateDependencies()
    }

    private fun initializeDependencyStates() {
        for (info in currentParamInfos) {
            val name = info.name ?: continue
            val initialState = info.dependInitialState.trim().lowercase()

            val initialVisibility = when (initialState) {
                "hide" -> false
                "show" -> true
                else -> {
                    info.dependDefault.trim().lowercase() != "hide"
                }
            }

            val row = rowViews[name] ?: continue
            if (info.dependReadonly) {
                row.visibility = View.VISIBLE
                setRowInteractive(row, initialVisibility && !info.readonly)
            } else {
                row.visibility = if (initialVisibility) View.VISIBLE else View.GONE
            }
        }
    }

    private fun attachDefaultListener(view: View, info: ActionParamInfo) {
        val name = info.name ?: return
        valueReaders[name] = { readValueDeep(view, info) }

        val target: View = when (view) {
            is Spinner, is CheckBox, is Switch, is EditText, is SeekBar -> view
            else -> (view as? ViewGroup)?.let { findTypedChild(it) } ?: view
        }

        when (target) {
            is Spinner -> target.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = evaluateDependencies()
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
            is CheckBox -> {
                updateDescOnToggle(info, target.isChecked)
                target.setOnCheckedChangeListener { _, isChecked ->
                    updateDescOnToggle(info, isChecked)
                    evaluateDependencies()
                }
            }
            is Switch -> {
                updateDescOnToggle(info, target.isChecked)
                target.setOnCheckedChangeListener { _, isChecked ->
                    updateDescOnToggle(info, isChecked)
                    evaluateDependencies()
                }
            }
            is EditText -> target.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) = evaluateDependencies()
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
            is SeekBar -> target.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) = evaluateDependencies()
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
    }

    private fun updateDescOnToggle(info: ActionParamInfo, isChecked: Boolean) {
        if (info.descOn.isNullOrEmpty()) return
        val name = info.name ?: return
        val row = rowViews[name] ?: return
        val descView = row.findViewById<TextView>(R.id.kr_param_desc)
        val text = if (isChecked) info.descOn else info.desc
        if (!text.isNullOrEmpty()) {
            descView.text = text
            descView.visibility = View.VISIBLE
        } else {
            descView.visibility = View.GONE
        }
    }

    private fun findTypedChild(group: ViewGroup): View? {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child is Spinner || child is CheckBox || child is Switch || child is EditText || child is SeekBar) {
                return child
            }
            if (child is ViewGroup) {
                findTypedChild(child)?.let { return it }
            }
        }
        return null
    }

    private fun readValueDeep(view: View, info: ActionParamInfo): String {
        val target: View = when (view) {
            is Spinner, is CheckBox, is Switch, is EditText, is SeekBar -> view
            else -> (view as? ViewGroup)?.let { findTypedChild(it) } ?: view
        }
        return when (target) {
            is EditText -> target.text.toString()
            is CheckBox -> if (target.isChecked) "1" else "0"
            is Switch -> if (target.isChecked) "1" else "0"
            is SeekBar -> (target.progress + info.min).toString()
            is Spinner -> (target.selectedItem as? SelectItem)?.value ?: target.selectedItem?.toString().orEmpty()
            else -> ""
        }
    }

    private val parenPattern = Regex("\\(([^()]*)\\)")

    private fun buildValueIdentifiers(value: String, options: ArrayList<SelectItem>?): Set<String> {
        val identifiers = HashSet<String>()
        identifiers.add(value)

        val title = options?.find { it.value == value }?.title?.trim()
        if (!title.isNullOrEmpty()) {
            identifiers.add(title)
            parenPattern.findAll(title).forEach { m ->
                val inner = m.groupValues[1].trim()
                if (inner.isNotEmpty()) {
                    identifiers.add(inner)
                    identifiers.add("(" + inner + ")")
                }
            }
        }
        return identifiers
    }

    private fun matchesWanted(pattern: String, identifiers: Set<String>): Boolean {
        if (!pattern.contains('*') && !pattern.contains('?')) {
            return identifiers.contains(pattern)
        }
        val regex = globToRegex(pattern)
        return identifiers.any { regex.matches(it) }
    }

    private fun globToRegex(pattern: String): Regex {
        val sb = StringBuilder("^")
        for (c in pattern) {
            when (c) {
                '*' -> sb.append(".*")
                '?' -> sb.append('.')
                else -> sb.append(Regex.escape(c.toString()))
            }
        }
        sb.append('$')
        return Regex(sb.toString())
    }

    private fun evaluateDependencies() {
        val previousState = HashMap(visibilityState)

        val working = HashMap<String, Boolean>()
        val maxPasses = currentParamInfos.size.coerceAtLeast(1).coerceAtMost(20)

        for (pass in 0 until maxPasses) {
            var changedThisPass = false

            for (info in currentParamInfos) {
                val name = info.name ?: continue
                val shouldShow = computeShouldShow(info, working)

                if (working[name] != shouldShow) {
                    working[name] = shouldShow
                    changedThisPass = true
                }
            }

            if (!changedThisPass) break
        }

        if (previousState.isNotEmpty()) {
            TransitionManager.beginDelayedTransition(linearLayout, buildRowVisibilityTransition())
        }

        for (info in currentParamInfos) {
            val name = info.name ?: continue
            val shouldShow = working[name] ?: continue
            applyVisibility(name, shouldShow, previousState[name])
        }

        applyDependSort()
    }

    private fun applyDependSort() {
        val sortableNames = currentParamInfos
            .filter { it.dependSort && it.dependReadonly && it.name != null }
            .map { it.name!! }

        reorderRowGroup(sortableNames) { name ->
            val info = currentParamInfos.find { it.name == name } ?: return@reorderRowGroup true
            val shouldShow = visibilityState[name] ?: true
            shouldShow && info.readonly != true
        }
    }

    private fun applyStaticReadonlySort() {
        val sortableNames = currentParamInfos
            .filter { it.sort && it.name != null }
            .map { it.name!! }

        reorderRowGroup(sortableNames) { name ->
            val info = currentParamInfos.find { it.name == name } ?: return@reorderRowGroup true
            info.readonly != true
        }
    }

    private fun reorderRowGroup(sortableNames: List<String>, isBright: (String) -> Boolean) {
        if (sortableNames.isEmpty()) return

        val desiredGroupOrder = sortableNames.sortedBy { name -> if (isBright(name)) 0 else 1 }
            .mapNotNull { rowViews[it] }
        if (desiredGroupOrder.isEmpty()) return

        val sortableViews = sortableNames.mapNotNull { rowViews[it] }.toHashSet()

        val currentChildren = ArrayList<View>(linearLayout.childCount)
        for (i in 0 until linearLayout.childCount) {
            currentChildren.add(linearLayout.getChildAt(i))
        }

        var groupCursor = 0
        val newOrder = ArrayList<View>(currentChildren.size)
        for (child in currentChildren) {
            if (sortableViews.contains(child) && groupCursor < desiredGroupOrder.size) {
                newOrder.add(desiredGroupOrder[groupCursor])
                groupCursor++
            } else {
                newOrder.add(child)
            }
        }

        if (newOrder == currentChildren) return

        if (newOrder.distinct().size != newOrder.size) {
            Toast.makeText(
                context,
                context.getString(R.string.kr_duplicate_param_name, "?"),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        try {
            linearLayout.removeAllViews()
            for (view in newOrder) {
                linearLayout.addView(view)
            }
        } catch (ex: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.kr_duplicate_param_name, ex.message ?: ""),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun buildRowVisibilityTransition(): TransitionSet {
        return TransitionSet()
            .addTransition(Fade(Fade.OUT))
            .addTransition(ChangeBounds())
            .addTransition(Fade(Fade.IN))
            .setOrdering(TransitionSet.ORDERING_TOGETHER)
            .setDuration(ROW_ANIM_DURATION_MS)
            .setInterpolator(standardMotionInterpolator())
    }

    private fun computeShouldShow(info: ActionParamInfo, working: HashMap<String, Boolean>): Boolean {
        val dependOnRaw = info.dependOn?.trim()

        if (dependOnRaw.isNullOrEmpty()) {
            return info.dependDefault.trim().lowercase() != "hide"
        }

        val dependOnList = dependOnRaw.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        if (dependOnList.isEmpty()) {
            return info.dependDefault.trim().lowercase() != "hide"
        }

        if (info.dependCascade && dependOnList.any { working[it] == false }) {
            return false
        }

        val dependValueList = (info.dependValue ?: "").split("|")
        val dependModeList = info.dependMode.split("|")

        fun evalCondition(i: Int): Pair<Boolean, Boolean>? {
            val parentName = dependOnList[i]

            if (!info.dependCascade && working[parentName] == false) {
                return null
            }

            val controllerInfo = currentParamInfos.find { it.name == parentName }
            val reader = valueReaders[parentName]
            if (controllerInfo == null || reader == null) return null

            val currentValues = reader().split(controllerInfo.separator)
                .map { it.trim() }.filter { it.isNotEmpty() }
            val parentOptions = controllerInfo.optionsFromShell ?: controllerInfo.options

            val currentIdentifiers = HashSet<String>()
            for (v in currentValues) {
                currentIdentifiers.addAll(buildValueIdentifiers(v, parentOptions))
            }

            val wantedRaw = dependValueList.getOrNull(i) ?: dependValueList.lastOrNull() ?: ""
            val wanted = wantedRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val matched = wanted.isEmpty() || wanted.any { matchesWanted(it, currentIdentifiers) }

            val mode = (dependModeList.getOrNull(i) ?: dependModeList.lastOrNull() ?: "show").trim()
            val wantShow = if (mode == "hide") !matched else matched
            return Pair(matched, wantShow)
        }

        val logic = info.dependLogic.trim().lowercase()
        return when (logic) {
            "priority", "or", "priority-ltr", "or-ltr" -> {
                var result = info.dependDefault.trim().lowercase() != "hide"
                for (i in dependOnList.indices) {
                    val (matched, wantShow) = evalCondition(i) ?: continue
                    if (matched) {
                        result = wantShow
                        break
                    }
                }
                result != info.dependNegate
            }
            "priority-rtl", "or-rtl" -> {
                var result = info.dependDefault.trim().lowercase() != "hide"
                for (i in dependOnList.indices.reversed()) {
                    val (matched, wantShow) = evalCondition(i) ?: continue
                    if (matched) {
                        result = wantShow
                        break
                    }
                }
                result != info.dependNegate
            }
            "xor" -> {
                var matchCount = 0
                for (i in dependOnList.indices) {
                    val (matched, _) = evalCondition(i) ?: continue
                    if (matched) matchCount++
                }
                (matchCount == 1) != info.dependNegate
            }
            "nand" -> {
                var result = true
                for (i in dependOnList.indices) {
                    val (_, wantShow) = evalCondition(i) ?: continue
                    if (!wantShow) {
                        result = false
                        break
                    }
                }
                !result != info.dependNegate
            }
            else -> {
                var satisfiedCount = 0
                var totalCount = 0

                for (i in dependOnList.indices) {
                    val (_, wantShow) = evalCondition(i) ?: continue
                    totalCount++
                    if (wantShow) satisfiedCount++
                }

                val threshold = if (info.dependThreshold < 0) {
                    totalCount
                } else {
                    (totalCount * info.dependThreshold / 100).coerceAtLeast(1)
                }

                val result = satisfiedCount >= threshold
                result != info.dependNegate
            }
        }
    }

    private fun applyVisibility(name: String, shouldShow: Boolean, oldState: Boolean?) {
        visibilityState[name] = shouldShow

        val view = rowViews[name]
        val info = currentParamInfos.find { it.name == name }
        val isInitial = oldState == null

        if (info != null && info.dependReadonly) {
            val effectiveEnabled = shouldShow && info.readonly != true
            view?.visibility = View.VISIBLE
            view?.let { setRowInteractive(it, effectiveEnabled, animate = !isInitial) }
        } else if (view != null) {
            view.visibility = if (shouldShow) View.VISIBLE else View.GONE
        }

        if (oldState != null && oldState != shouldShow) {
            val script = info?.dependOnChangeCallback
            if (!script.isNullOrEmpty()) {
                executeDependOnChangeCallback(name, shouldShow, script)
            }
        }
    }

    private fun setRowInteractive(row: View, enabled: Boolean, animate: Boolean = false) {
        val dimTarget = row.findViewById<View>(R.id.kr_param_input) ?: row
        setEnabledRecursively(dimTarget, enabled)
    }

    private fun setEnabledRecursively(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                setEnabledRecursively(view.getChildAt(i), enabled)
            }
        }
    }

    private fun executeDependOnChangeCallback(paramName: String, visible: Boolean, script: String) {
        Thread {
            try {
                val extraParams = HashMap<String, String>()
                extraParams["PARAM_NAME"] = paramName
                extraParams["PARAM_VISIBLE"] = if (visible) "1" else "0"
                ScriptEnvironmen.executeResultRoot(context, script, null, extraParams)
            } catch (ex: Exception) {
            }
        }.start()
    }

    private val hideLabelTypes = arrayOf("bool", "checkbox", "switch")
    private fun addToLayout(inputView: View, actionParamInfo: ActionParamInfo) {
        val layout = LayoutInflater.from(context).inflate(R.layout.kr_param_row, null)
        if (!actionParamInfo.title.isNullOrEmpty()) {
            layout.findViewById<TextView>(R.id.kr_param_title).text = actionParamInfo.title
        } else {
            layout.findViewById<View>(R.id.kr_param_title_layout).visibility = View.GONE
        }
        
        if ((!actionParamInfo.label.isNullOrEmpty()) && !hideLabelTypes.contains(actionParamInfo.type)) {
            layout.findViewById<TextView>(R.id.kr_param_label).run {
                text = actionParamInfo.label
            }
        } else {
            layout.findViewById<TextView>(R.id.kr_param_label).visibility = View.GONE
        }

        if (!actionParamInfo.desc.isNullOrEmpty()) {
            layout.findViewById<TextView>(R.id.kr_param_desc).text = actionParamInfo.desc
        } else {
            layout.findViewById<View>(R.id.kr_param_desc_layout).visibility = View.GONE
        }

        layout.findViewById<FrameLayout>(R.id.kr_param_input).addView(inputView)
        linearLayout.addView(layout)

        (inputView.layoutParams as FrameLayout.LayoutParams).gravity = Gravity.CENTER_VERTICAL

        actionParamInfo.name?.let { rowViews[it] = layout }

        if (actionParamInfo.readonly) {
            setRowInteractive(layout, false)
        }
    }

    private fun getFieldTips(actionParamInfo: ActionParamInfo): String {
        val tips = StringBuilder()
        if (!actionParamInfo.title.isNullOrEmpty()) {
            tips.append(actionParamInfo.title)
            tips.append(" ")
        }
        if (!actionParamInfo.label.isNullOrEmpty()) {
            tips.append(actionParamInfo.label)
            tips.append(" ")
        }
        tips.append("(")
        tips.append(actionParamInfo.name)
        tips.append(") ")
        return tips.toString()
    }

    fun isParamHidden(name: String): Boolean = visibilityState[name] == false

    fun readParamsValue(actionParamInfos: ArrayList<ActionParamInfo>): HashMap<String, String> {
        val params = HashMap<String, String>()
        for (actionParamInfo in actionParamInfos) {
            if (actionParamInfo.name == null) {
                continue
            }

            when (val view = linearLayout.findViewWithTag<View>(actionParamInfo.name)) {
                is EditText -> {
                    val text = view.text.toString()
                    if (text.isNotEmpty()) {
                        if ((actionParamInfo.type == "int" || actionParamInfo.type == "number")) {
                            try {
                                val value = text.toInt()
                                if (value < actionParamInfo.min) {
                                    throw Exception("${getFieldTips(actionParamInfo)} $value < ${actionParamInfo.min} !!!")
                                } else if (value > actionParamInfo.max) {
                                    throw Exception("${getFieldTips(actionParamInfo)} $value > ${actionParamInfo.max} !!!")
                                }
                            } catch (ex: java.lang.NumberFormatException) {
                            }
                        } else if (actionParamInfo.type == "color") {
                            val isValidColor = if (com.omarea.krscript.config.ColorResRef.isColorRef(text)) {
                                com.omarea.krscript.config.ColorResRef.resolve(context, text) != null
                            } else {
                                try {
                                    text.toColorInt()
                                    true
                                } catch (ex: java.lang.Exception) {
                                    false
                                }
                            }
                            if (!isValidColor) {
                                throw Exception(
                                    "" + getFieldTips(actionParamInfo) + "  \n" + context.getString(
                                        R.string.kr_invalid_color
                                    )
                                )
                            }
                        }
                    }
                    actionParamInfo.value = text
                }

                is CheckBox -> {
                    actionParamInfo.value = if (view.isChecked) "1" else "0"
                }

                is Switch -> {
                    actionParamInfo.value = if (view.isChecked) "1" else "0"
                }

                is SeekBar -> {
                    val text = (view.progress + actionParamInfo.min).toString()
                    actionParamInfo.value = text
                }

                is TextView -> {
                    actionParamInfo.value = view.text.toString()
                }

                is Spinner -> {
                    val item = view.selectedItem
                    when {
                        item is SelectItem -> {
                            actionParamInfo.value = item.value
                        }

                        item != null -> actionParamInfo.value = item.toString()
                        else -> actionParamInfo.value = ""
                    }
                }
            }

            if (actionParamInfo.readonly) {
                continue
            }

            val isHiddenByDepend = visibilityState[actionParamInfo.name] == false
            
            if (isHiddenByDepend) {
                if (actionParamInfo.dependIncludeHidden) {
                    if (!actionParamInfo.value.isNullOrEmpty()) {
                        params[actionParamInfo.name!!] = actionParamInfo.value!!
                    }
                } else {
                }
                continue
            }
            
            if (actionParamInfo.value.isNullOrEmpty()) {
                if (actionParamInfo.required && !isHiddenByDepend) {
                    throw Exception(getFieldTips(actionParamInfo) + context.getString(R.string.do_not_empty))
                } else {
                    params[actionParamInfo.name!!] = ""
                }
            } else {
                params[actionParamInfo.name!!] = actionParamInfo.value!!
            }
        }
        return params
    }

    fun updateParamsView(actionParamInfos: ArrayList<ActionParamInfo>) {
        for (actionParamInfo in actionParamInfos) {
            if (actionParamInfo.name == null) {
                continue
            }

            val view = linearLayout.findViewWithTag<View>(actionParamInfo.name)
            if (view != null) {
            }
        }
    }
}