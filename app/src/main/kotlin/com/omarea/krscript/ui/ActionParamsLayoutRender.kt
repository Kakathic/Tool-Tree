package com.omarea.krscript.ui

import android.animation.TimeInterpolator
import android.os.Build
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
        // Thời lượng + easing dùng chung cho MỌI animation ẩn/hiện param (ChangeBounds lẫn
        // alpha depend-readonly) để cả 2 loại hiệu ứng luôn đồng bộ tốc độ/cảm giác với nhau.
        private const val ROW_ANIM_DURATION_MS = 300L

        // Đường cong "standard easing" của Material Design (cubic-bezier 0.4, 0, 0.2, 1),
        // tương đương FastOutSlowInInterpolator - đây chính là easing Android dùng cho hầu hết
        // animation chuyển layout/view gốc của hệ thống (không phải tuyến tính, không phải
        // accelerate/decelerate thô). Cần API 21+; máy cũ hơn sẽ dùng AccelerateDecelerateInterpolator.
        private fun standardMotionInterpolator(): TimeInterpolator {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                PathInterpolator(0.4f, 0f, 0.2f, 1f)
            } else {
                android.view.animation.AccelerateDecelerateInterpolator()
            }
        }

        /**
         * 获取当前选中项索引（单选）
         */
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

        /**
         * 获取当前选中项索引（多选）
         */
        fun getParamOptionsSelectedStatus(actionParamInfo: ActionParamInfo, options: ArrayList<SelectItem>): BooleanArray {
            val status = BooleanArray(options.size)
            val values = getParamValues(actionParamInfo)

            for (index in 0 until options.size) {
                val option = options[index]
                status[index] = (values != null && values.contains(option.value))
            }
            return status
        }

        /**
         * 设置列表的选中状态
         */
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

    // key = actionParamInfo.name
    private val rowViews = HashMap<String, View>()
    private val valueReaders = HashMap<String, () -> String>()
    private var currentParamInfos: ArrayList<ActionParamInfo> = ArrayList()
    
    // ========== TÍNH NĂNG MỚI: LƯỚI TRẠNG THÁI HIỆN TẠI ==========
    // Lưu trạng thái ẩn/hiện hiện tại của từng param
    private val visibilityState = HashMap<String, Boolean>()

    fun renderList(actionParamInfos: ArrayList<ActionParamInfo>, fileChooser: ParamsFileChooserRender.FileChooserInterface?) {
        // ========== FIX: name trùng nhau gây crash addView (already has a parent) ==========
        // rowViews là HashMap<String, View> khoá theo name, nên nếu 2 (hay nhiều) param dùng
        // chung 1 name, view của param sau sẽ ghi đè entry của param trước trong rowViews -
        // nhưng CẢ HAI view vẫn được add vào linearLayout. Khi reorderRowGroup() (dùng cho
        // sort/depend-sort) tra rowViews theo name, nó có thể lấy TRÙNG 1 view instance nhiều
        // lần rồi addView() nó thêm lần nữa trong khi view đó đã có parent -> IllegalStateException.
        // Cách xử lý: phát hiện name trùng NGAY TỪ ĐẦU, báo lỗi cho người dùng bằng Toast (thay
        // vì để app crash), và chỉ render mục ĐẦU TIÊN của mỗi name trùng - bỏ qua các mục sau.
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
            // 下拉框渲染
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
            // 选择框渲染
            else if (actionParamInfo.type == "bool" || actionParamInfo.type == "checkbox") {
                val view = ParamsCheckbox(actionParamInfo, context).render()
                addToLayout(view, actionParamInfo)
                attachDefaultListener(view, actionParamInfo)
            }
            // 开关渲染
            else if (actionParamInfo.type == "switch") {
                val view = ParamsSwitch(actionParamInfo, context).render()
                addToLayout(view, actionParamInfo)
                attachDefaultListener(view, actionParamInfo)
            }
            // 滑块
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
            // 文件选择
            else if (actionParamInfo.type == "file" || actionParamInfo.type == "folder") {
                val layout = ParamsFileChooserRender(actionParamInfo, context, fileChooser) { evaluateDependencies() }.render()

                addToLayout(layout, actionParamInfo)
                actionParamInfo.name?.let { name ->
                    valueReaders[name] = { linearLayout.findViewWithTag<TextView?>(name)?.text?.toString() ?: "" }
                }
            }
            // 应用选择
            else if (actionParamInfo.type == "app" || actionParamInfo.type == "packages") {
                val layout = ParamsAppChooserRender(actionParamInfo, context) { evaluateDependencies() }.render()

                addToLayout(layout, actionParamInfo)
                actionParamInfo.name?.let { name ->
                    valueReaders[name] = { linearLayout.findViewWithTag<TextView?>(name)?.text?.toString() ?: "" }
                }
            }
            // 颜色输入
            else if (actionParamInfo.type == "color") {
                val layout = ParamsColorPicker(actionParamInfo, context).render()

                addToLayout(layout, actionParamInfo)
                attachDefaultListener(layout, actionParamInfo)
            }
            // 文本框渲染
            else {
                val view = ParamsEditText(actionParamInfo, context).render()
                addToLayout(view, actionParamInfo)
                attachDefaultListener(view, actionParamInfo)
            }
        }

        // ========== TÍNH NĂNG MỚI: sort = true (readonly TĨNH) - CHỈ CHẠY 1 LẦN ==========
        // Chạy TRƯỚC initializeDependencyStates()/evaluateDependencies() vì readonly tĩnh đã
        // được xác định xong (xem ActionListFragment) và không đổi lại trong suốt phiên dialog,
        // nên không cần (và không nên) tính lại mỗi lần depend-* đánh giá lại như depend-sort.
        applyStaticReadonlySort()

        // ========== TÍNH NĂNG MỚI: ĐẶT TRẠNG THÁI KHỞI ĐỘNG ==========
        initializeDependencyStates()
        evaluateDependencies()
    }

    // ========== TÍNH NĂNG MỚI: KHỞI TẠO TRẠNG THÁI PHỤ THUỘC ==========
    private fun initializeDependencyStates() {
        for (info in currentParamInfos) {
            val name = info.name ?: continue
            val initialState = info.dependInitialState.trim().lowercase()

            val initialVisibility = when (initialState) {
                "hide" -> false
                "show" -> true
                else -> {
                    // "auto": xác định tự động dựa trên dependDefault
                    info.dependDefault.trim().lowercase() != "hide"
                }
            }

            // CHỈ set View.visibility (hoặc mờ/khóa với depend-readonly) tức thời để tránh
            // nhấp nháy lúc mở dialog. KHÔNG ghi vào visibilityState ở đây: evaluateDependencies()
            // được gọi ngay sau initializeDependencyStates() sẽ tính trạng thái thật (có cascade)
            // và mới là nơi ghi visibilityState + quyết định gọi depend-onchange. Nếu ghi ở đây,
            // lần đánh giá đầu tiên sẽ hiểu nhầm đây là "thay đổi trạng thái" và gọi nhầm
            // callback ngay khi dialog vừa mở.
            val row = rowViews[name] ?: continue
            if (info.dependReadonly) {
                // Luôn hiện, chỉ mờ/khóa tương tác theo initialVisibility - không set GONE,
                // tránh hàng bị "biến mất" rồi mới hiện lại (nhấp nháy) khi dialog vừa mở.
                // Nếu param còn có readonly="true" cố định thì giữ khóa ngay từ đầu.
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

    // ========== TÍNH NĂNG MỚI: desc-on - đổi ghi chú khi checkbox/switch được bật ==========
    // Khi param có khai báo desc-on="..." (xem ActionParamInfo.descOn), phần ghi chú
    // (kr_param_desc) sẽ tự đổi qua lại giữa desc-on (lúc bật) và desc gốc (lúc tắt) ngay
    // khi người dùng gạt/tích chọn - không cần chạy shell hay reload lại dialog. Được gọi
    // cả lúc khởi tạo (để đồng bộ đúng trạng thái ban đầu) lẫn mỗi khi checked thay đổi.
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

    // ========== TÍNH NĂNG MỚI: KHỚP depend-value KIỂU WILDCARD (*, ?) ==========
    // Trước đây depend-value bắt buộc phải trùng KHỚP TUYỆT ĐỐI với giá trị đọc được (vd:
    // "sdcard: MIUISecurityCenter.apk") - chỉ cần khác 1 ký tự (khác đường dẫn, khác phiên
    // bản...) là coi như không khớp. Giờ cho phép dùng "*" (khớp 0-nhiều ký tự bất kỳ) và "?"
    // (khớp đúng 1 ký tự bất kỳ) trong depend-value, ví dụ:
    //   depend-value = "*SecurityCenter*"     -> chỉ cần chứa "SecurityCenter" ở bất kỳ đâu
    //   depend-value = "*Security*.apk"       -> chứa "Security", kết thúc bằng ".apk"
    // Value KHÔNG chứa "*" hoặc "?" thì vẫn so khớp CHÍNH XÁC như cũ (giữ nguyên hành vi cũ,
    // tránh vô tình đổi hành vi của toàn bộ config đã viết từ trước).
    private fun matchesWanted(pattern: String, identifiers: Set<String>): Boolean {
        if (!pattern.contains('*') && !pattern.contains('?')) {
            return identifiers.contains(pattern)
        }
        val regex = globToRegex(pattern)
        return identifiers.any { regex.matches(it) }
    }

    // Chuyển pattern kiểu wildcard shell (*, ?) sang Regex, escape mọi ký tự "đặc biệt" của
    // regex khác (đặc biệt là dấu "." rất hay gặp trong tên file như ".apk") để chúng được
    // hiểu là ký tự thường, không phải cú pháp regex.
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

    // ========== TÍNH NĂNG MỚI: ĐÁNH GIÁ PHỤ THUỘC NÂNG CẤP (có depend-cascade) ==========
    private fun evaluateDependencies() {
        // Trạng thái TRƯỚC lượt đánh giá này - dùng để phát hiện thay đổi thật sự và quyết
        // định có gọi depend-onchange hay không (so với trạng thái đã áp dụng lần trước, KHÔNG
        // so giữa các lượt lặp nội bộ bên dưới).
        val previousState = HashMap(visibilityState)

        // Bộ nhớ TẠM cho lượt đánh giá này. Lặp nhiều lượt để "cha ẩn thì con ẩn theo"
        // (depend-cascade) lan truyền đúng qua các chuỗi phụ thuộc nhiều cấp, bất kể param cha
        // được khai báo trước hay sau param con trong file XML. Dừng sớm khi không còn gì
        // thay đổi giữa 2 lượt liên tiếp (hầu hết trường hợp chỉ cần 1-2 lượt).
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

        // Áp dụng kết quả cuối cùng lên UI, ghi lại visibilityState chính thức, và gọi
        // depend-onchange đúng 1 lần cho mỗi param có trạng thái thực sự thay đổi so với
        // trước khi vào hàm evaluateDependencies() này.
        //
        // ========== TÍNH NĂNG MỚI: HÀNG DƯỚI TRƯỢT LÊN/XUỐNG KHI 1 PARAM ẨN/HIỆN ==========
        // previousState.isEmpty() nghĩa là đây là lượt đánh giá ĐẦU TIÊN (ngay sau khi dialog
        // vừa mở) - không animate, set tức thời như cũ. Nếu KHÔNG rỗng, tức người dùng vừa đổi
        // 1 param cha trong lúc dialog đang mở -> bắt đầu 1 Transition DUY NHẤT cho CẢ danh sách
        // (gọi 1 lần trước khi set visibility cho từng param, không gọi riêng lẻ từng cái) để
        // Android tự tính toán và animate luôn cả việc các hàng còn lại trượt lên (khi 1 hàng
        // phía trên biến mất) hoặc trượt xuống (khi 1 hàng phía trên xuất hiện) - thay vì chỉ
        // fade tại chỗ rồi các hàng khác nhảy vị trí đột ngột.
        if (previousState.isNotEmpty()) {
            TransitionManager.beginDelayedTransition(linearLayout, buildRowVisibilityTransition())
        }

        for (info in currentParamInfos) {
            val name = info.name ?: continue
            val shouldShow = working[name] ?: continue
            applyVisibility(name, shouldShow, previousState[name])
        }

        // ========== TÍNH NĂNG MỚI: depend-sort - dồn mục xám xuống dưới, mục sáng lên trên ==========
        // Gọi SAU KHI đã áp dụng xong shouldShow/readonly cho toàn bộ danh sách (visibilityState
        // lúc này đã là dữ liệu mới nhất). Đặt trong cùng khối transition ở trên (nếu có) để
        // việc đổi chỗ view cũng được animate mượt cùng lúc với hiệu ứng mờ/khóa, thay vì giật
        // cục riêng biệt. Vẫn chạy cả ở lần đánh giá ĐẦU TIÊN (mở dialog) để danh sách hiện ra
        // đã đúng thứ tự luôn, chỉ là không animate (do chưa có beginDelayedTransition ở trên).
        applyDependSort()
    }

    // ========== TÍNH NĂNG MỚI: SẮP XẾP LẠI CÁC HÀNG depend-sort ==========
    // Trong số các param có dependSort = true (đã được đảm bảo luôn đi kèm dependReadonly =
    // true ngay từ lúc parse config), dồn các hàng đang "sáng" (đủ điều kiện, có thể tương
    // tác) lên phía trên nhóm, dồn các hàng đang "xám" (bị depend-readonly khóa) xuống phía
    // dưới nhóm - giữ nguyên thứ tự tương đối giữa các hàng cùng trạng thái (stable).
    //
    // QUAN TRỌNG: chỉ hoán đổi vị trí NỘI BỘ trong đúng những "slot" (chỉ số con trong
    // linearLayout) mà chính nhóm depend-sort đang chiếm giữ - các hàng KHÔNG tham gia sort
    // (kể cả các hàng depend-readonly khác không bật depend-sort) đứng yên tuyệt đối, không bị
    // group sort "nhảy qua mặt". Cách làm: duyệt toàn bộ danh sách con hiện có của linearLayout
    // đúng 1 lần, hàng nào KHÔNG tham gia sort thì giữ nguyên, hàng nào CÓ tham gia thì được
    // thay bằng phần tử tiếp theo lấy từ danh sách đã sắp xếp - đảm bảo không bỏ sót hàng cuối
    // cùng (bug thường gặp khi chỉ so sánh/hoán đổi từng cặp liền kề, dừng sớm ở gần cuối danh
    // sách thay vì xét toàn bộ nhóm).
    private fun applyDependSort() {
        val sortableNames = currentParamInfos
            .filter { it.dependSort && it.dependReadonly && it.name != null }
            .map { it.name!! }

        // Trạng thái "sáng" (đủ điều kiện) hiện tại của từng hàng tham gia sort. Dùng
        // visibilityState (đã ghi ở applyVisibility phía trên trong CÙNG lượt đánh giá này)
        // kết hợp readonly tĩnh - đúng công thức effectiveEnabled trong applyVisibility().
        reorderRowGroup(sortableNames) { name ->
            val info = currentParamInfos.find { it.name == name } ?: return@reorderRowGroup true
            val shouldShow = visibilityState[name] ?: true
            shouldShow && info.readonly != true
        }
    }

    // ========== TÍNH NĂNG MỚI: sort = true (dùng với readonly TĨNH, không phải depend-readonly) ==========
    // Khác với depend-sort (đánh giá lại MỖI LẦN người dùng đổi 1 param khác vì trạng thái
    // readonly phụ thuộc điều kiện động), "readonly" tĩnh (kể cả kết quả từ readonlySh) chỉ
    // được xác định ĐÚNG 1 LẦN trước khi renderList() chạy (xem ActionListFragment - readonly
    // đã được gán xong từ shellResults trước khi gọi renderList) và không đổi lại trong suốt
    // phiên dialog đang mở. Vì vậy chỉ cần sắp xếp 1 LẦN DUY NHẤT ngay sau khi toàn bộ hàng đã
    // được thêm vào layout (gọi trong renderList(), TRƯỚC initializeDependencyStates() /
    // evaluateDependencies() - để depend-sort nếu có chạy sau đó xử lý tiếp trên thứ tự đã ổn
    // định này, và không animate vì đây là lúc dialog vừa mở, chưa có gì để animate).
    //
    // Cách dùng trong config (row.toml):
    //   readonly = "true"      (hoặc readonly = "shell...")
    //   sort = "true"
    // -> Mục nào readonly = true (xám, chỉ đọc) bị dồn xuống dưới cùng nhóm các mục có
    //    sort = true; mục readonly = false (sáng, tương tác được) dồn lên trên cùng nhóm.
    private fun applyStaticReadonlySort() {
        val sortableNames = currentParamInfos
            .filter { it.sort && it.name != null }
            .map { it.name!! }

        reorderRowGroup(sortableNames) { name ->
            val info = currentParamInfos.find { it.name == name } ?: return@reorderRowGroup true
            info.readonly != true
        }
    }

    // ========== TÍNH NĂNG MỚI: SẮP XẾP LẠI 1 NHÓM HÀNG (dùng chung cho depend-sort & sort) ==========
    // Trong số các hàng có tên nằm trong `sortableNames`, dồn hàng mà `isBright(name)` trả về
    // true lên phía TRÊN nhóm, dồn hàng trả về false xuống phía DƯỚI nhóm - giữ nguyên thứ tự
    // tương đối giữa các hàng cùng trạng thái (stable).
    //
    // QUAN TRỌNG: chỉ hoán đổi vị trí NỘI BỘ trong đúng những "slot" (chỉ số con trong
    // linearLayout) mà chính nhóm sortableNames đang chiếm giữ - các hàng KHÔNG nằm trong nhóm
    // đứng yên tuyệt đối, không bị nhóm sort "nhảy qua mặt". Cách làm: duyệt toàn bộ danh sách
    // con hiện có của linearLayout đúng 1 lần, hàng nào KHÔNG thuộc nhóm thì giữ nguyên, hàng
    // nào CÓ thuộc nhóm thì được thay bằng phần tử tiếp theo lấy từ danh sách đã sắp xếp - đảm
    // bảo không bỏ sót hàng cuối cùng (bug thường gặp khi chỉ so sánh/hoán đổi từng cặp liền kề,
    // dừng sớm ở gần cuối danh sách thay vì xét toàn bộ nhóm).
    private fun reorderRowGroup(sortableNames: List<String>, isBright: (String) -> Boolean) {
        if (sortableNames.isEmpty()) return

        // Thứ tự MONG MUỐN của riêng nhóm sortable: sáng trước, xám sau, ổn định theo thứ tự
        // khai báo gốc trong nhóm (sortedBy của Kotlin là stable sort).
        val desiredGroupOrder = sortableNames.sortedBy { name -> if (isBright(name)) 0 else 1 }
            .mapNotNull { rowViews[it] }
        if (desiredGroupOrder.isEmpty()) return

        val sortableViews = sortableNames.mapNotNull { rowViews[it] }.toHashSet()

        // Danh sách TOÀN BỘ children hiện tại của linearLayout, đúng thứ tự đang hiển thị.
        val currentChildren = ArrayList<View>(linearLayout.childCount)
        for (i in 0 until linearLayout.childCount) {
            currentChildren.add(linearLayout.getChildAt(i))
        }

        // Xây danh sách MỚI: các slot không thuộc nhóm sort giữ nguyên vị trí/view, các slot
        // thuộc nhóm sort được lấp lần lượt bằng desiredGroupOrder (đã sắp xếp) - duyệt đúng
        // 1 lần qua TOÀN BỘ currentChildren nên không bỏ sót hàng nào, kể cả hàng cuối cùng.
        var groupCursor = 0
        val newOrder = ArrayList<View>(currentChildren.size)
        for (child in currentChildren) {
            // FIX: nếu groupCursor vượt quá desiredGroupOrder (VD: rowViews bị đè do 2 param
            // trùng name khiến sortableViews/desiredGroupOrder lệch số lượng thực tế so với
            // currentChildren) thì giữ nguyên child gốc thay vì đọc lố index -> tránh
            // IndexOutOfBounds và tránh add trùng 1 view nhiều lần vào newOrder.
            if (sortableViews.contains(child) && groupCursor < desiredGroupOrder.size) {
                newOrder.add(desiredGroupOrder[groupCursor])
                groupCursor++
            } else {
                newOrder.add(child)
            }
        }

        // Không có gì thay đổi thứ tự thực sự -> khỏi động vào layout (tránh xin layout thừa).
        if (newOrder == currentChildren) return

        // FIX: mọi view trong newOrder phải là DUY NHẤT (không có view nào lặp lại) trước khi
        // add lại vào linearLayout, nếu không View.addView() sẽ ném IllegalStateException
        // ("already has a parent") và làm crash app - thường xảy ra khi có 2+ param dùng
        // trùng name (xem cảnh báo Toast ở renderList()). Kiểm tra bằng distinct().size để
        // phát hiện sớm, tránh gọi removeAllViews() rồi mới phát hiện lỗi (lúc đó layout đã
        // rỗng, không còn cách phục hồi).
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
            // FIX: an toàn tối đa - dù lý do gì cũng KHÔNG để app crash vì sắp xếp lại thứ tự
            // hàng, chỉ báo lỗi bằng Toast và bỏ qua việc sắp xếp lần này.
            Toast.makeText(
                context,
                context.getString(R.string.kr_duplicate_param_name, ex.message ?: ""),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Transition dùng cho depend-on: Fade (mờ dần khi ẩn/hiện) + ChangeBounds (trượt mượt vị
    // trí/kích thước của MỌI view anh em bị ảnh hưởng bởi khoảng trống vừa mất/xuất hiện).
    // 300ms + easing "standard" (cubic-bezier 0.4,0,0.2,1 - giống FastOutSlowInInterpolator
    // Material Design) để cảm giác đúng như animation chuyển layout gốc của Android, không
    // bị "nhanh/giật" như easing tuyến tính hoặc quá ngắn.
    private fun buildRowVisibilityTransition(): TransitionSet {
        return TransitionSet()
            .addTransition(Fade(Fade.OUT))
            .addTransition(ChangeBounds())
            .addTransition(Fade(Fade.IN))
            .setOrdering(TransitionSet.ORDERING_TOGETHER)
            .setDuration(ROW_ANIM_DURATION_MS)
            .setInterpolator(standardMotionInterpolator())
    }

    // Tính shouldShow cho 1 param dựa trên trạng thái ẩn/hiện của các param cha TRONG LƯỢT
    // ĐÁNH GIÁ HIỆN TẠI (map "working"), không phải trạng thái đã chốt từ lần render trước.
    private fun computeShouldShow(info: ActionParamInfo, working: HashMap<String, Boolean>): Boolean {
        val dependOnRaw = info.dependOn?.trim()

        if (dependOnRaw.isNullOrEmpty()) {
            return info.dependDefault.trim().lowercase() != "hide"
        }

        val dependOnList = dependOnRaw.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        if (dependOnList.isEmpty()) {
            return info.dependDefault.trim().lowercase() != "hide"
        }

        // ========== TÍNH NĂNG MỚI: CHA ẨN THÌ CON ẨN THEO (depend-cascade) ==========
        // Nếu BẤT KỲ param cha nào trong depend-on đang bị ẩn (đã tính ở lượt trước/pass hiện
        // tại), param này ẩn theo luôn, không cần xét tiếp depend-value/depend-logic.
        if (info.dependCascade && dependOnList.any { working[it] == false }) {
            return false
        }

        val dependValueList = (info.dependValue ?: "").split("|")
        val dependModeList = info.dependMode.split("|")

        fun evalCondition(i: Int): Pair<Boolean, Boolean>? {
            val parentName = dependOnList[i]

            // ========== depend-cascade="false": loại cha đang ẨN khỏi phép kết hợp ==========
            // Khi dependCascade=false, KHÔNG dùng giá trị cũ còn sót lại của 1 cha đang ẩn để
            // tính điều kiện - coi như cha đó "không áp dụng" ở lượt đánh giá hiện tại, chỉ
            // (các) cha đang HIỆN mới được tính. working[parentName] == false nghĩa là cha
            // này đang ẩn ở lượt đánh giá hiện tại.
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
                // Ưu tiên trái -> phải
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
                // Ưu tiên phải -> trái
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
                // Chỉ ĐÚNG MỘT điều kiện được thỏa
                var matchCount = 0
                for (i in dependOnList.indices) {
                    val (matched, _) = evalCondition(i) ?: continue
                    if (matched) matchCount++
                }
                (matchCount == 1) != info.dependNegate
            }
            "nand" -> {
                // Phủ định của AND (KHÔNG phải tất cả điều kiện đều thỏa)
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
                // "and" (mặc định)
                var satisfiedCount = 0
                var totalCount = 0

                for (i in dependOnList.indices) {
                    val (_, wantShow) = evalCondition(i) ?: continue
                    totalCount++
                    if (wantShow) satisfiedCount++
                }

                val threshold = if (info.dependThreshold < 0) {
                    // Mặc định: 100% (tất cả phải thỏa)
                    totalCount
                } else {
                    // Tính toán % ngưỡng
                    (totalCount * info.dependThreshold / 100).coerceAtLeast(1)
                }

                val result = satisfiedCount >= threshold
                result != info.dependNegate
            }
        }
    }

    // ========== TÍNH NĂNG MỚI: ÁP DỤNG TRẠNG THÁI LÊN UI + GỌI CALLBACK ==========
    private fun applyVisibility(name: String, shouldShow: Boolean, oldState: Boolean?) {
        visibilityState[name] = shouldShow

        val view = rowViews[name]
        val info = currentParamInfos.find { it.name == name }
        // Lần đầu (lúc mở dialog, oldState == null): set tức thời, KHÔNG animate, để tránh
        // hiệu ứng lạ/nhấp nháy khi layout vừa dựng xong. Chỉ animate khi đây là thay đổi
        // thực sự do người dùng tương tác (đổi giá trị param cha) trong lúc dialog đang mở.
        val isInitial = oldState == null

        if (info != null && info.dependReadonly) {
            // ========== TÍNH NĂNG MỚI: depend-readonly ==========
            // Không ẩn (GONE) mà giữ VISIBLE, chỉ làm mờ + khóa tương tác (isEnabled = false)
            // khi điều kiện phụ thuộc không thỏa (shouldShow = false).
            // Nếu param còn có readonly="true" cố định (riêng biệt với depend-readonly), phải
            // giữ nguyên trạng thái khóa dù depend có thỏa hay không - trước đây khi shouldShow
            // = true, setRowInteractive sẽ bật lại isEnabled = true, vô tình gỡ khóa readonly cố định.
            val effectiveEnabled = shouldShow && info.readonly != true
            view?.visibility = View.VISIBLE
            view?.let { setRowInteractive(it, effectiveEnabled, animate = !isInitial) }
        } else if (view != null) {
            // Việc animate (fade + trượt vị trí các hàng khác) đã được xử lý bởi
            // TransitionManager.beginDelayedTransition() gọi 1 lần ở evaluateDependencies()
            // ngay trước vòng lặp gọi applyVisibility() cho cả danh sách - ở đây chỉ cần đổi
            // visibility bình thường, Android sẽ tự nội suy animation cho thay đổi này.
            view.visibility = if (shouldShow) View.VISIBLE else View.GONE
        }

        // Chỉ gọi callback khi ĐÃ TỪNG có trạng thái trước đó (oldState != null) VÀ trạng thái
        // thực sự đổi - tránh gọi callback ngay khi dialog vừa mở (lần đầu tiên oldState luôn
        // null vì initializeDependencyStates() không ghi vào visibilityState).
        if (oldState != null && oldState != shouldShow) {
            val script = info?.dependOnChangeCallback
            if (!script.isNullOrEmpty()) {
                executeDependOnChangeCallback(name, shouldShow, script)
            }
        }
    }

    // ========== TÍNH NĂNG MỚI: BẬT/TẮT TƯƠNG TÁC + LÀM MỜ CHO depend-readonly ==========
    // enabled = true  -> hiện bình thường, hết mờ, có thể bấm/nhập/chọn
    // enabled = false -> làm mờ cả hàng (alpha) và vô hiệu hóa toàn bộ control con
    //                     (EditText/CheckBox/Switch/SeekBar/Spinner/nút bấm...) để
    //                     người dùng không thể chỉnh sửa giá trị, nhưng vẫn nhìn thấy nó.
    // animate = true: chuyển alpha mượt (200ms) - dùng khi đây là thay đổi thực sự lúc dialog
    // đang mở; animate = false (mặc định): set tức thời - dùng lúc khởi tạo layout.
    private fun setRowInteractive(row: View, enabled: Boolean, animate: Boolean = false) {
        // Bỏ chỉnh alpha thủ công (dimTarget.alpha, buttonDrawable.alpha, compoundDrawables.alpha) -
        // setEnabledRecursively() đã đủ khiến các control (select/checkbox/switch/edittext/icon...)
        // tự chuyển sang state "disabled" của hệ thống (nhạt/xám hơn qua ColorStateList sẵn có),
        // không cần thêm lớp mờ thủ công chồng lên. Chỉ khoá đúng phạm vi kr_param_input (nơi thực
        // sự có control tương tác được) - không khoá kr_param_title/kr_param_label/kr_param_desc vì
        // chúng không gắn tương tác gì và không cần đổi trạng thái.
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

    // ========== TÍNH NĂNG MỚI: THỰC THI depend-onchange ==========
    // Chạy script/callback trên luồng nền (KHÔNG được chạy trực tiếp trên UI thread vì lệnh
    // shell root có thể mất nhiều thời gian và sẽ làm treo giao diện). Truyền tên param vừa
    // đổi trạng thái + trạng thái mới (1 = đang hiện, 0 = đang ẩn) làm biến môi trường để
    // script có thể tự xử lý theo ngữ cảnh.
    private fun executeDependOnChangeCallback(paramName: String, visible: Boolean, script: String) {
        Thread {
            try {
                val extraParams = HashMap<String, String>()
                extraParams["PARAM_NAME"] = paramName
                extraParams["PARAM_VISIBLE"] = if (visible) "1" else "0"
                ScriptEnvironmen.executeResultRoot(context, script, null, extraParams)
            } catch (ex: Exception) {
                // Lỗi khi chạy callback không được làm crash hay ảnh hưởng tới UI chính
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

        // ========== FIX: readonly tĩnh (readonly="true"/"1"/shell) không tự mờ/khóa ==========
        // Trước đây chỉ có nhánh depend-readonly gọi setRowInteractive() để làm mờ + khóa
        // tương tác. Param có readonly="true" cố định nhưng KHÔNG kèm depend-readonly thì
        // không hề bị disable ở đâu cả -> vẫn sáng bình thường và bấm/sửa được như thường.
        // Áp dụng ngay tại đây để mọi trường hợp readonly tĩnh đều bị khóa từ lúc khởi tạo,
        // không phụ thuộc vào có depend-on/depend-readonly hay không.
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
                            // Chấp nhận mã hex (#AARRGGBB/#RRGGBB) hoặc tham chiếu resource
                            // dạng "@color/xxx" / "@android:color/xxx"
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

            // ========== readonly (tĩnh, khai báo trực tiếp bằng readonly="true") ==========
            // Param chỉ xem, không cho sửa trên UI -> giờ cũng KHÔNG gửi giá trị đi khi chạy
            // action, bỏ qua hoàn toàn khỏi kết quả (giống hệt depend-include-hidden="false").
            // Lưu ý: đây KHÔNG áp dụng cho depend-readonly (readonly do phụ thuộc điều kiện),
            // depend-readonly vẫn dùng cơ chế depend-include-hidden như trước, không đổi.
            if (actionParamInfo.readonly) {
                continue
            }

            // Dùng visibilityState (kết quả logic của evaluateDependencies) thay vì đọc trực
            // tiếp View.visibility, vì với depend-readonly=true, view vẫn ở trạng thái
            // VISIBLE (chỉ bị mờ + khóa tương tác) dù về mặt logic vẫn được xem là "không
            // thỏa điều kiện" (shouldShow = false).
            val isHiddenByDepend = visibilityState[actionParamInfo.name] == false
            
            // ========== depend-include-hidden (mặc định = true) ==========
            // Mặc định, param bị ẩn bởi depend-on CHỈ ẩn về mặt giao diện, giá trị vẫn được
            // gửi đi bình thường như khi hiện (không kiểm tra required vì người dùng không
            // nhìn thấy field để nhập). Chỉ khi khai báo rõ depend-include-hidden="false" thì
            // param đang ẩn mới bị loại bỏ hoàn toàn khỏi kết quả.
            if (isHiddenByDepend) {
                if (actionParamInfo.dependIncludeHidden) {
                    if (!actionParamInfo.value.isNullOrEmpty()) {
                        params[actionParamInfo.name!!] = actionParamInfo.value!!
                    }
                } else {
                    // depend-include-hidden="false": bỏ qua HOÀN TOÀN param đang ẩn, kể cả khi
                    // nó có giá trị.
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
                // TODO: Refresh interface display
            }
        }
    }
}