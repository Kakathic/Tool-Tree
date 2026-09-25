package com.omarea.krscript.config

import android.content.Context
import com.omarea.krscript.model.ActionNode
import com.omarea.krscript.model.ActionParamInfo

// ========== TÍNH NĂNG MỚI: GHI NHỚ LỰA CHỌN CỦA PARAM (remember) ==========
// Lưu/đọc lại giá trị người dùng đã chọn/nhập lần cuối cho các param có khai "remember = true"
// trong TOML, để lần sau mở lại dialog action vẫn tự hiển thị đúng lựa chọn cũ thay vì luôn
// quay về "value" tĩnh khai trong file cấu hình.
//
// Định danh duy nhất cho mỗi giá trị được lưu = đường dẫn file config của trang
// (action.currentPageConfigPath) + "key" của action (action.key - đã có sẵn cơ chế fallback về
// title nếu TOML không khai "key"/"index"/"id", xem PageConfigReader) + tên param (param.name).
// Đủ để phân biệt 2 param trùng tên nằm ở 2 action/trang khác nhau, dùng lại đúng nguyên tắc
// định danh mà tính năng tạo shortcut màn hình chính đang dựa vào (action.key).
//
// Thứ tự ưu tiên khi mở dialog: value-sh (nếu có, đọc trạng thái THẬT từ hệ thống lúc mở) >
// giá trị đã nhớ (remember) > value tĩnh - xem điểm gọi load() trong ActionListFragment.kt.
object ActionParamMemory {
    private const val PREF_NAME = "kr-param-memory"

    private fun buildKey(action: ActionNode, paramName: String): String {
        return "${action.currentPageConfigPath}|${action.key}|$paramName"
    }

    // Đọc giá trị đã nhớ cho 1 param (null nếu param không bật "remember" hoặc chưa từng lưu).
    fun load(context: Context, action: ActionNode, param: ActionParamInfo): String? {
        if (!param.remember) return null
        val name = param.name ?: return null
        val spf = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return spf.getString(buildKey(action, name), null)
    }

    // Lưu lại giá trị cuối cùng của TẤT CẢ param có "remember = true" trong 1 action, ngay khi
    // người dùng bấm OK (values = kết quả trả về bởi ActionParamsLayoutRender.readParamsValue()).
    fun save(context: Context, action: ActionNode, actionParamInfos: List<ActionParamInfo>, values: Map<String, String>) {
        val toRemember = actionParamInfos.filter { it.remember && !it.name.isNullOrEmpty() }
        if (toRemember.isEmpty()) return

        val spf = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        spf.edit().apply {
            for (param in toRemember) {
                val name = param.name ?: continue
                val value = values[name] ?: continue
                putString(buildKey(action, name), value)
            }
            apply()
        }
    }
}
