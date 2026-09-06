package com.omarea.common.ui

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.DialogInterface
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tool.tree.R
import androidx.core.graphics.drawable.toDrawable
import com.tool.tree.ThemeModeState
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable

class DialogHelper {
    class DialogButton(val text: String, val onClick: Runnable? = null, val dismiss: Boolean = true)

    class DialogWrap(private val d: AlertDialog) {
        val context: Context = dialog.context
        private var mCancelable = true
        val isCancelable: Boolean
            get () {
                return mCancelable
            }

        fun setCancelable(cancelable: Boolean): DialogWrap {
            mCancelable = cancelable
            d.setCancelable(cancelable)

            return this
        }

        fun setOnDismissListener(onDismissListener: DialogInterface.OnDismissListener): DialogWrap {
            d.setOnDismissListener(onDismissListener)

            return this
        }

        val dialog: AlertDialog
            get() {
                return d
            }

        fun dismiss() {
            try {
                d.dismiss()
            } catch (_: Exception) {
            }
        }

        fun hide() {
            try {
                d.hide()
            } catch (_: Exception) {
            }
        }

        val isShowing: Boolean
            get() {
                return d.isShowing
            }
    }

    companion object {
        // 是否禁用模糊背景
        var disableBlurBg = false

        fun animDialog(dialog: AlertDialog?): DialogWrap? {
            if (dialog != null && !dialog.isShowing) {
                dialog.window?.run {
                    setWindowAnimations(R.style.windowAnim)
                }
                dialog.show()
            }
            return if (dialog != null) DialogWrap(dialog) else null
        }

        fun animDialog(builder: AlertDialog.Builder): DialogWrap {
            val dialog = builder.create()
            animDialog(dialog)
            return DialogWrap(dialog)
        }

        fun helpInfo(context: Context, message: String, onDismiss: Runnable? = null): DialogWrap {
            return helpInfo(context, context.getString(R.string.help_title), message, onDismiss)
        }

        fun helpInfo(context: Context, title: String, message: String, onDismiss: Runnable? = null): DialogWrap {
            val layoutInflater = LayoutInflater.from(context)
            val dialog = layoutInflater.inflate(R.layout.dialog_help_info, null)

            (dialog.findViewById<TextView>(R.id.confirm_title)!!).run {
                if (title.isNotEmpty()) {
                    text = title
                    visibility = View.VISIBLE
                } else {
                    visibility = View.GONE
                }
            }

            (dialog.findViewById<TextView>(R.id.confirm_message)!!).run {
                if (message.isNotEmpty()) {
                    text = message
                    visibility = View.VISIBLE
                } else {
                    visibility = View.GONE
                }
            }

            val d = customDialog(context, dialog, onDismiss == null)
            (dialog.findViewById<View>(R.id.btn_confirm)!!).run {
                if (onDismiss != null) {
                    d.setOnDismissListener {
                        onDismiss.run()
                    }
                }
                setOnClickListener {
                    d.dismiss()
                }
            }

            return d
        }

        fun helpInfo(context: Context,
                  title: String = "",
                  message: String = "",
                  contentView: View,
                  onConfirm: Runnable? = null): DialogWrap {
            val view = getCustomDialogView(context, R.layout.dialog_help_info, title, message, contentView)

            val dialog = customDialog(context, view)
            view.findViewById<View>(R.id.btn_confirm).setOnClickListener {
                dialog.dismiss()
                onConfirm?.run()
            }

            return dialog
        }

        fun confirm(context: Context,
                    title: String = "",
                    message: String = "",
                    onConfirm: Runnable? = null,
                    onCancel: Runnable? = null): DialogWrap {
            return openContinueAlert(context, R.layout.dialog_confirm, title, message, onConfirm, onCancel)
        }

        fun warning(context: Context,
                    title: String = "",
                    message: String = "",
                    onConfirm: Runnable? = null,
                    onCancel: Runnable? = null,
                    cancelable: Boolean = true): DialogWrap {
            return openContinueAlert(context, R.layout.dialog_warning, title, message, onConfirm, onCancel, cancelable)
        }

        private fun getCustomDialogView(context: Context,
                                        layout: Int,
                                        title: String = "",
                                        message: String = "",
                                        contentView: View? = null): View {

            val view = LayoutInflater.from(context).inflate(layout, null)
            view.findViewById<TextView?>(R.id.confirm_title)?.run {
                if (title.isEmpty()) {
                    visibility = View.GONE
                } else {
                    text = title
                }
            }

            view.findViewById<TextView?>(R.id.confirm_message)?.run {
                if (message.isEmpty()) {
                    visibility = View.GONE
                } else {
                    text = message
                }
            }

            if (contentView != null) {
                view.findViewById<FrameLayout?>(R.id.confirm_custom_view)?.addView(contentView)
            }

            return view
        }

        fun confirm(context: Context,
                    title: String = "",
                    message: String = "",
                    contentView: View? = null,
                    onConfirm: Runnable? = null,
                    onCancel: Runnable? = null): DialogWrap {
            val view = getCustomDialogView(context, R.layout.dialog_confirm, title, message, contentView)

            val dialog = customDialog(context, view)
            view.findViewById<View>(R.id.btn_cancel).setOnClickListener {
                dialog.dismiss()
                onCancel?.run()
            }
            view.findViewById<View>(R.id.btn_confirm).setOnClickListener {
                dialog.dismiss()
                onConfirm?.run()
            }

            return dialog
        }

        fun confirm(context: Context,
                    title: String = "",
                    message: String = "",
                    onConfirm: DialogButton? = null,
                    onCancel: DialogButton? = null): DialogWrap {
            return confirm(context, title, message, null, onConfirm, onCancel)
        }

        fun confirm(context: Context,
                    title: String = "",
                    message: String = "",
                    contentView: View? = null,
                    onConfirm: DialogButton? = null,
                    onCancel: DialogButton? = null,
                    cancelable: Boolean = true): DialogWrap {
            val view = getCustomDialogView(context, R.layout.dialog_confirm, title, message, contentView)

            val dialog = customDialog(context, view, cancelable)

            val btnConfirm = view.findViewById<TextView?>(R.id.btn_confirm)
            if (onConfirm != null) {
                btnConfirm?.text = onConfirm.text
            }
            btnConfirm?.setOnClickListener {
                if (onConfirm != null) {
                    if (onConfirm.dismiss) {
                        dialog.dismiss()
                    }
                    onConfirm.onClick?.run()
                } else {
                    dialog.dismiss()
                }
            }


            val btnCancel = view.findViewById<TextView?>(R.id.btn_cancel)
            if (onCancel != null) {
                btnCancel?.text = onCancel.text
            }
            btnCancel?.setOnClickListener {
                if (onCancel != null) {
                    if (onCancel.dismiss) {
                        dialog.dismiss()
                    }
                    onCancel.onClick?.run()
                } else {
                    dialog.dismiss()
                }
            }

            return dialog
        }

        fun confirm(context: Context, contentView: View? = null, onConfirm: DialogButton? = null, onCancel: DialogButton? = null): DialogWrap {
            return this.confirm(context, "", "", contentView, onConfirm, onCancel)
        }

        private fun getWindowBackground(context: Context, defaultColor: Int = Color.TRANSPARENT): Int {
            val attrsArray = intArrayOf(android.R.attr.background)
            val typedArray = context.obtainStyledAttributes(attrsArray)
            val color = typedArray.getColor(0, defaultColor)
            typedArray.recycle()
            return color
        }

        private fun setOutsideTouchDismiss(view: View, dialogWrap: DialogWrap): DialogWrap {
            val dialog = dialogWrap.dialog
            val rootView = dialog.window?.decorView
            rootView?.setOnTouchListener(object : View.OnTouchListener {
                override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                    if (event != null && event.action == MotionEvent.ACTION_UP) {
                        val x = event.x.toInt()
                        val y = event.y.toInt()
                        val rect = Rect()
                        view.getGlobalVisibleRect(rect)
                        if (!rect.contains(x, y)) {
                            val mCancelable = dialogWrap.isCancelable
                            if (mCancelable) {
                                dialogWrap.dismiss()
                            }
                        }
                        return true
                    }
                    return false
                }
            })

            return dialogWrap
        }

        private fun getStatusBarColor(context: Context): Int {
            val defaultColor = Color.WHITE
            val attrsArray = intArrayOf(android.R.attr.statusBarColor)
            val typedArray = context.obtainStyledAttributes(attrsArray)
            val color = typedArray.getColor(0, defaultColor)
            typedArray.recycle()
            return color
        }

        private fun openContinueAlert(context: Context,
                                      layout: Int,
                                      title: String = "",
                                      message: String = "",
                                      onConfirm: Runnable? = null,
                                      onCancel: Runnable? = null,
                                      cancelable: Boolean = true): DialogWrap {
            val view = getCustomDialogView(context, layout, title, message, null)

            val dialog = customDialog(context, view, cancelable)
            view.findViewById<View?>(R.id.btn_cancel)?.setOnClickListener {
                dialog.dismiss()
                onCancel?.run()
            }
            view.findViewById<View?>(R.id.btn_confirm)?.setOnClickListener {
                dialog.dismiss()
                onConfirm?.run()
            }

            return dialog
        }

        fun confirmBlur(context: Activity,
                        title: String = "",
                        message: String = "",
                        onConfirm: Runnable? = null,
                        onCancel: Runnable? = null): DialogWrap {
            return openContinueAlert(context, R.layout.dialog_confirm, title, message, onConfirm, onCancel)
        }

        fun alert(context: Context,
                  title: String = "",
                  message: String = "",
                  onConfirm: Runnable? = null): DialogWrap {
            return openContinueAlert(context, R.layout.dialog_alert, title, message, onConfirm, null)
        }

        fun alert(context: Context,
                  title: String = "",
                  message: String = "",
                  contentView: View,
                  onConfirm: Runnable? = null): DialogWrap {
            val view = getCustomDialogView(context, R.layout.dialog_alert, title, message, contentView)

            val dialog = customDialog(context, view)
            view.findViewById<View>(R.id.btn_confirm).setOnClickListener {
                dialog.dismiss()
                onConfirm?.run()
            }

            return dialog
        }

        fun customDialog(context: Context, view: View, cancelable: Boolean = true): DialogWrap {
            val useBlur = (
                        context is Activity &&
                        context.window.attributes.flags and WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER == 0
                    )

            val dialog = (if (useBlur) {
                AlertDialog.Builder(context, R.style.custom_alert_dialog)
            } else {
                AlertDialog.Builder(context)
            }).setView(view).setCancelable(cancelable).create()

            if (context is Activity) {
                dialog.window?.run {
                    setWindowBlurBg(this, context)
                    decorView.run {
                        systemUiVisibility = context.window.decorView.systemUiVisibility
                    }
                    if (useBlur) {
                        applyEdgeToEdge(this, isNightMode(context))
                    }
                }
                dialog.show()

                if (useBlur && cancelable) {
                    val swipeBinding = DialogFullScreen.bindSwipeToDismiss(context, dialog) { dialog.dismiss() }
                    if (swipeBinding != null) {
                        dialog.setOnDismissListener { swipeBinding.release(dialog) }
                    }
                }
            } else {
                dialog.window?.run {
                    setWindowAnimations(R.style.windowAnim2)
                }
                dialog.show()
                dialog.window?.run {
                    setBackgroundDrawableResource(android.R.color.transparent)
                }
            }

            return setOutsideTouchDismiss(view, DialogWrap(dialog).setCancelable(cancelable))
        }

        private fun isNightMode(context: Context): Boolean {
            return ThemeModeState.isDarkMode()
        }

        fun applyEdgeToEdge(window: Window, darkMode: Boolean, contentView: View? = null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT

            val controller = WindowInsetsControllerCompat(window, window.decorView)
            val useLightIcons = !darkMode
            controller.isAppearanceLightStatusBars = useLightIcons
            controller.isAppearanceLightNavigationBars = useLightIcons

            if (contentView != null) {
                val basePaddingLeft = contentView.paddingLeft
                val basePaddingTop = contentView.paddingTop
                val basePaddingRight = contentView.paddingRight
                val basePaddingBottom = contentView.paddingBottom

                ViewCompat.setOnApplyWindowInsetsListener(contentView) { v, insets ->
                    val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                    val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
                    v.setPadding(
                        basePaddingLeft + systemBars.left,
                        basePaddingTop + systemBars.top,
                        basePaddingRight + systemBars.right,
                        basePaddingBottom + systemBars.bottom + ime.bottom
                    )
                    insets
                }
                ViewCompat.requestApplyInsets(contentView)
            }
        }
        
        fun setWindowBlurBg(window: Window, activity: Activity) {
            window.run {
                // 2 khái niệm KHÁC NHAU, trước đây bị gộp chung vào 1 biến "wallpaperMode" nên
                // đọc sai tín hiệu cho trường hợp ảnh nền TĨNH (file tùy chỉnh / wallpaper hệ
                // thống tĩnh):
                //
                // 1. isLiveWallpaperSurface: có đúng là live wallpaper (hệ thống vẽ ở surface
                //    riêng, ngoài cây View của activity) hay không - CHỈ dùng để quyết định có
                //    bỏ qua bước chụp screenshot decorView hay không (screenshot sẽ không chứa
                //    ảnh nền thật trong trường hợp này). Đọc từ FLAG_SHOW_WALLPAPER - cờ này
                //    (xem ThemeModeState.applyWallpaperMode) CHỈ được bật khi thực sự là live
                //    wallpaper, ảnh tĩnh sẽ luôn đọc ra false ở đây.
                //
                // 2. imageBgMode: có đang ở "chế độ ảnh nền" nói chung hay không (bao gồm CẢ
                //    ảnh tĩnh lẫn live wallpaper, loại trừ directBg màu phẳng) - dùng để quyết
                //    định có chạy 3 tầng dự phòng (cache/blur-tại-chỗ/ảnh gốc) trước khi rơi
                //    xuống màu đặc hay không. Đọc từ ThemeModeState.isImageBackgroundMode()
                //    (biến RIÊNG, không phải FLAG_SHOW_WALLPAPER) - trước đây dùng nhầm
                //    "wallpaperMode" (= isLiveWallpaperSurface) cho việc này, khiến ảnh tĩnh
                //    không bao giờ được vào 3 tầng dự phòng: hễ getDialogBlurBackground() (chụp
                //    screenshot) fail (timing lúc chuyển trang, bitmap HARDWARE trên MIUI...) là
                //    rơi thẳng xuống màu đặc dù đang dùng ảnh nền.
                val isLiveWallpaperSurface = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER != 0
                val imageBgMode = ThemeModeState.isImageBackgroundMode()

                val dialogBlur = if (!disableBlurBg && !isLiveWallpaperSurface) FastBlurUtility.getDialogBlurBackground(activity) else null
                if (dialogBlur != null) {
                    setBackgroundDrawable(dialogBlur.toDrawable(activity.resources))
                    return
                }
                val backgroundDrawable = try {
                    val bg = getWindowBackground(activity)
                    when {
                        bg != Color.TRANSPARENT -> bg.toDrawable()
                        isFloating -> {
                            setDimAmount(0.8f)
                            bg.toDrawable()
                        }
                        else -> {
                            // Chế độ ảnh nền (imageBgMode, bao gồm cả ảnh tĩnh): 3 tầng dự
                            // phòng theo thứ tự ưu tiên, trước khi rơi xuống màu đặc.
                            // 1. Ảnh đã chụp + blur sẵn trong cache (nhanh nhất).
                            val pageBlur = if (!disableBlurBg && imageBgMode) {
                                FastBlurUtility.getPageBlurBackground(activity)
                            } else null
                            // 2. Cache chưa sẵn sàng (đang chụp async nơi khác) -> tự lấy ảnh
                            // nền + blur ngay tại chỗ.
                            val freshBlur = pageBlur ?: if (!disableBlurBg && imageBgMode) {
                                FastBlurUtility.getWallpaperBlurBackground(activity)
                            } else null
                            // 3. Blur cũng lỗi -> dùng ảnh nền gốc (không blur), còn hơn màu đặc.
                            val finalBlur = freshBlur ?: if (!disableBlurBg && imageBgMode) {
                                FastBlurUtility.getWallpaperRawBackground(activity)
                            } else null
                            finalBlur?.toDrawable(activity.resources) ?: run {
                                // 4. Cuối cùng: màu đặc.
                                val color = if (imageBgMode || isNightMode(context)) {
                                    Color.argb(255, 18, 18, 18)
                                } else {
                                    Color.argb(255, 245, 245, 245)
                                }
                                color.toDrawable()
                            }
                        }
                    }
                } catch (_: Exception) {
                    Color.TRANSPARENT.toDrawable()
                }
                setBackgroundDrawable(backgroundDrawable)
            }
        }
    }
}