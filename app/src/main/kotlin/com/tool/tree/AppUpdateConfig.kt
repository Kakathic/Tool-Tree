package com.tool.tree

import android.content.Context
import androidx.core.content.edit

/**
 * Lưu trạng thái người dùng đã bấm Hủy bỏ với 1 bản cập nhật cụ thể (nhận diện qua sha256 của
 * file apk trên GitHub) - lần sau vào MainActivity sẽ không tự động hiện lại AppUpdateDialog cho
 * đúng bản đó nữa. Nếu GitHub có bản mới hơn (sha256 khác) thì dialog sẽ tự hiện lại bình thường.
 *
 * Icon cập nhật trên toolbar không phụ thuộc vào trạng thái này - vẫn hiện kèm dấu chấm đỏ khi
 * còn bản mới, người dùng có thể bấm icon để tự mở lại dialog bất cứ lúc nào.
 */
class AppUpdateConfig(context: Context) {
    private val config = context.getSharedPreferences("kr-script-config", Context.MODE_PRIVATE)

    companion object {
        const val KEY_DISMISSED_SHA256 = "UpdateDismissedSha256"
    }

    fun getDismissedSha256(): String? {
        return config.getString(KEY_DISMISSED_SHA256, null)
    }

    fun setDismissedSha256(sha256: String) {
        config.edit { putString(KEY_DISMISSED_SHA256, sha256) }
    }

    fun isDismissed(sha256: String): Boolean {
        return getDismissedSha256()?.equals(sha256, ignoreCase = true) == true
    }
}
