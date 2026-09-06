package com.tool.tree

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.TextView
import android.widget.Toast
import com.omarea.common.ui.CurrentActivityHolder
import com.omarea.common.ui.DialogHelper
import com.omarea.krscript.NotiShellTaskLauncher
import com.omarea.krscript.config.StringResRef
import com.omarea.krscript.model.PageNode
import com.omarea.krscript.model.RunnableNode
import java.io.File

/**
 * Nhận lệnh am broadcast để hiện 1 dialog XÁC NHẬN THẬT (modal, có tiêu đề + mô tả), khác với
 * BannerReceiver (banner trượt ở mép màn hình, không chặn thao tác).
 *
 * Ví dụ gọi từ shell:
 * am broadcast -a com.tool.tree.broadcast.SHOWDIALOG \
 *     --es title "Tiêu đề" \
 *     --es desc "Nội dung mô tả" \
 *     --es confirm "Xác nhận" \
 *     --es cancel "Hủy bỏ" \
 *     --es script "touch /sdcard/ok.txt" \
 *     --es config "/sdcard/Tool-Tree/page.toml" \
 *     --es config-sh "sh /sdcard/gen_page.sh" \
 *     --ei countdown 10 \
 *     --ez force 1
 *
 * Extra "title" (tùy chọn): tiêu đề dialog. Bỏ trống -> dùng tên app (R.string.app_name).
 * Extra "desc" / "text" (bắt buộc): nội dung mô tả.
 * Extra "confirm" / "cancel" (tùy chọn): nhãn 2 nút, mặc định lấy theo string resource có sẵn
 * của app (R.string.btn_confirm / R.string.btn_cancel).
 * Extra "script" (tùy chọn): script chạy khi bấm XÁC NHẬN (bấm Hủy bỏ KHÔNG chạy gì). Log/tiến
 * trình hiện qua 1 Notification thật, dùng NotiShellTaskLauncher.startTask() - giống hệt cơ chế
 * banner đang dùng cho script của nó.
 * Extra "config" / "config-sh" (tùy chọn): trang mở khi bấm XÁC NHẬN (bấm Hủy bỏ KHÔNG mở gì).
 * Ưu tiên "config" nếu đường dẫn tồn tại thật trên máy, không thì dùng "config-sh". Nếu vừa có
 * "script" vừa có "config"/"config-sh" -> chạy script trước, mở trang ngay sau đó (không đợi
 * script chạy xong).
 * Extra "countdown" (số nguyên, giây, tùy chọn): tự đóng dialog sau chừng đó giây. Nếu có
 * "script" hoặc "config"/"config-sh" (có hành động thật để chạy) -> dialog có đủ 2 nút, đếm
 * ngược hiện trên nhãn nút Hủy bỏ, dạng "Hủy bỏ (n)". Nếu KHÔNG có cả script và trang (chỉ để
 * thông báo) -> dialog chỉ hiện 1 nút xác nhận duy nhất, đếm ngược hiện luôn trên nút đó, dạng
 * "Xác nhận (n)". Hết giờ = tự đóng dialog, coi như bấm Hủy bỏ - KHÔNG chạy script/mở trang. Bỏ
 * trống hoặc <= 0 -> không tự đóng.
 * Extra "force" (boolean, --ez, tùy chọn, mặc định false): true -> chặn bấm ra ngoài dialog và
 * nút Back để thoát, bắt buộc phải bấm nút (Xác nhận/Hủy bỏ, hoặc nút xác nhận duy nhất nếu
 * không có script/trang) mới đóng được.
 *
 * Nếu app đang ở background (không có Activity foreground) -> tự rơi về hiện Toast thường
 * (trường hợp này KHÔNG hỗ trợ nút Xác nhận/Hủy bỏ, script/trang sẽ KHÔNG được chạy/mở).
 */
class ShowDialogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val rawDesc = intent.getStringExtra("desc") ?: intent.getStringExtra("text") ?: ""
        val message = StringResRef.resolve(context, rawDesc)

        val activity = CurrentActivityHolder.get()
        if (activity == null) {
            // Không có Activity foreground -> không có nơi hiện dialog thật, fallback Toast.
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            return
        }

        val rawTitle = intent.getStringExtra("title")
        val title = if (rawTitle.isNullOrEmpty()) {
            activity.getString(R.string.app_name)
        } else {
            StringResRef.resolve(context, rawTitle)
        }

        val confirmText = intent.getStringExtra("confirm")
            ?.let { StringResRef.resolve(context, it) }
            ?: activity.getString(R.string.btn_confirm)
        val cancelText = intent.getStringExtra("cancel")
            ?.let { StringResRef.resolve(context, it) }
            ?: activity.getString(R.string.btn_cancel)

        val script = intent.getStringExtra("script")
        val config = intent.getStringExtra("config")
        val configSh = intent.getStringExtra("config-sh")
        val countdownSeconds = intent.getIntExtra("countdown", 0)
        val force = intent.getBooleanExtra("force", false)

        // Có hành động thật (script và/hoặc trang) hay chỉ là dialog thông báo đơn thuần.
        val hasConfigPage = (!config.isNullOrEmpty() && File(config).isFile) || !configSh.isNullOrEmpty()
        val hasAction = !script.isNullOrEmpty() || hasConfigPage

        val handler = Handler(Looper.getMainLooper())
        // Giữ tham chiếu Runnable đếm ngược để hủy kịp thời ngay khi người dùng tự bấm nút
        // trước khi hết giờ - tránh việc nó vẫn chạy ngầm rồi đóng nhầm 1 dialog KHÁC nếu
        // dialog cũ đã bị thay bởi 1 lượt gọi showdialog mới sau đó.
        var countdownRunnable: Runnable? = null

        val dialogWrap: DialogHelper.DialogWrap
        val countdownBtnView: TextView?
        val countdownBaseLabel: String

        if (hasAction) {
            // Có script/trang để chạy -> giữ đủ 2 nút Xác nhận/Hủy bỏ, đếm ngược trên nút Hủy bỏ.
            val onConfirm = DialogHelper.DialogButton(confirmText, Runnable {
                countdownRunnable?.let { handler.removeCallbacks(it) }
                if (!script.isNullOrEmpty()) {
                    runConfirmScript(activity, title, script)
                }
                openConfigPageIfAny(activity, config, configSh)
            })
            val onCancel = DialogHelper.DialogButton(cancelText, Runnable {
                countdownRunnable?.let { handler.removeCallbacks(it) }
            })

            dialogWrap = DialogHelper.confirm(
                activity, title, message, null, onConfirm, onCancel,
                cancelable = !force
            )
            countdownBtnView = dialogWrap.dialog.findViewById(R.id.btn_cancel)
            countdownBaseLabel = cancelText
        } else {
            // Không có script lẫn trang -> chỉ là thông báo, chỉ hiện 1 nút xác nhận duy nhất,
            // đếm ngược hiện luôn trên nút đó. Bấm nút hoặc hết giờ đều chỉ đóng dialog.
            dialogWrap = DialogHelper.alert(activity, title, message, null)
            dialogWrap.setCancelable(!force)
            countdownBtnView = dialogWrap.dialog.findViewById(R.id.btn_confirm)
            countdownBaseLabel = confirmText
        }

        if (countdownSeconds > 0) {
            val deadlineElapsedMs = SystemClock.elapsedRealtime() + countdownSeconds * 1000L
            val tick = object : Runnable {
                override fun run() {
                    if (!dialogWrap.isShowing) return
                    val remaining = ((deadlineElapsedMs - SystemClock.elapsedRealtime()) / 1000L).toInt()
                    if (remaining <= 0) {
                        // Hết giờ -> tự đóng, KHÔNG chạy script/mở trang (coi như Hủy bỏ).
                        dialogWrap.dismiss()
                        return
                    }
                    countdownBtnView?.text = "$countdownBaseLabel ($remaining)"
                    handler.postDelayed(this, 1000L)
                }
            }
            countdownRunnable = tick
            handler.postDelayed(tick, 1000L)
        }
    }

    private fun runConfirmScript(activity: Activity, dialogTitle: String, script: String) {
        val nodeInfo = RunnableNode("").apply {
            title = dialogTitle
            shell = RunnableNode.shellModeBgTask
            interruptable = true
        }
        NotiShellTaskLauncher.startTask(activity.applicationContext, script, nodeInfo)
    }

    private fun openConfigPageIfAny(activity: Activity, config: String?, configSh: String?) {
        val pageNode = PageNode("")
        if (!config.isNullOrEmpty() && File(config).isFile) {
            pageNode.pageConfigPath = config
        } else if (!configSh.isNullOrEmpty()) {
            pageNode.pageConfigSh = configSh
        } else {
            return
        }
        activity.startActivity(Intent(activity, ActionPage::class.java).apply {
            putExtra("page", pageNode)
        })
    }
}
