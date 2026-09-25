package com.omarea.krscript.config

import android.content.Context
import com.omarea.krscript.model.ActionNode
import com.omarea.krscript.model.ActionParamInfo

// Lưu/đọc giá trị người dùng đã chọn/nhập lần cuối cho các param có "remember = true" trong
// TOML, để lần sau mở lại dialog action tự hiển thị đúng lựa chọn cũ thay vì quay về "value"
// tĩnh. Định danh 1 giá trị = configPath trang + định danh action + tên param. Thứ tự ưu tiên
// khi mở dialog: value-sh (trạng thái thật từ hệ thống) > giá trị đã nhớ > value tĩnh - xem
// điểm gọi load() trong ActionListFragment.kt.
object ActionParamMemory {
    private const val PREF_NAME = "kr-param-memory"

    private fun buildKey(action: ActionNode, paramName: String): String {
        // action.key có thể rỗng nếu TOML không khai "key"/"index"/"id" và action không phải
        // action.menu (chỉ nhánh menu mới tự fallback key = title khi đọc TOML - xem
        // PageConfigReader.tomlBuildNode(), KHÔNG đổi ở đây vì action.key còn quyết định việc
        // hiện/ẩn tính năng tạo shortcut màn hình chính, xem ListItemClickable.allowShortcutConfig
        // và ActionListFragment.onItemLongClick()). Nên CHỈ fallback sang title cục bộ ngay tại
        // chỗ tính key remember, không gán ngược lại action.key, để tránh 2 action khác nhau
        // cùng thiếu "key" (nhất là khi currentPageConfigPath cũng rỗng, do trang được đọc qua
        // InputStream) bị dùng chung 1 khoá rỗng, ghi đè giá trị nhớ của nhau.
        val actionId = action.key.ifEmpty { action.title }
        return "${action.currentPageConfigPath}|$actionId|$paramName"
    }

    // Đọc giá trị đã nhớ cho 1 param (null nếu không bật "remember" hoặc chưa từng lưu).
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
