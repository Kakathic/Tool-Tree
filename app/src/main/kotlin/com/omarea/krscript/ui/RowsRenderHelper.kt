package com.omarea.krscript.ui

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.*
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.omarea.common.ui.BlurEngine
import com.omarea.common.ui.DialogHelper
import com.omarea.krscript.TryOpenActivity
import com.omarea.krscript.config.IconPathAnalysis
import com.omarea.krscript.executor.ScriptEnvironmen
import com.omarea.krscript.model.NodeInfoBase
import com.omarea.krscript.model.TextNode
import com.tool.tree.R

// Tách logic hiển thị "rows" (dùng chung bởi item text và item action, ...) ra một nơi duy nhất,
// tránh lặp lại code giữa ListItemText và ListItemAction.
object RowsRenderHelper {

    fun bind(
        context: Context,
        rowsView: TextView?,
        extraIconView: ImageView?,
        rows: List<TextNode.TextRow>,
        config: NodeInfoBase
    ) {
        if (rowsView == null) {
            return
        }
        if (rows.isEmpty()) {
            rowsView.visibility = View.GONE
            extraIconView?.visibility = View.GONE
            return
        }

        rowsView.text = ""
        // Dừng các icon inline đang chạy hoạt ảnh từ LẦN bind() TRƯỚC (nếu có) - tránh chồng
        // callback/schedule cũ lên rowsView khi RecyclerView rebind lại (xem cuối hàm: lưu lại
        // danh sách animation mới vào rowsView.tag để lần bind() kế tiếp dừng đúng các drawable này).
        @Suppress("UNCHECKED_CAST")
        (rowsView.tag as? MutableList<AnimationDrawable>)?.forEach { it.stop() }
        val animatedRowIcons = ArrayList<AnimationDrawable>()
        // LinkMovementMethod mặc định: bấm bất kỳ đâu trên dòng (kể cả vùng trống do canh lề)
        // cũng tính là bấm trúng ClickableSpan của dòng đó. Dùng bản tuỳ chỉnh bên dưới để chỉ
        // nhận chạm trong vùng chữ/icon thực sự được vẽ.
        rowsView.movementMethod = BoundedLinkMovementMethod.instance // 不设置 ClickableSpan 点击没反应
        rowsView.visibility = View.VISIBLE

        // BoundedLinkMovementMethod chỉ "nuốt" (consume) sự kiện chạm khi trúng đúng ClickableSpan
        // (link/toggle/activity/script). Chạm vào phần còn lại của rowsView (chữ thường không có
        // action, hoặc khoảng trống) sẽ KHÔNG được tiêu thụ -> Android coi như rowsView "không xử
        // lý" và để sự kiện rơi xuống cho View cha (thường là toàn bộ item list) xử lý click, gây
        // bấm nhầm vào action/kênh của item. Đặt rowsView clickable + listener rỗng để nó tự nuốt
        // hết các lượt chạm còn lại, không rơi xuống item cha.
        rowsView.isClickable = true
        rowsView.setOnClickListener { }

        // bind() thường được gọi lúc RecyclerView/ListView bind item - tức TRƯỚC khi view được
        // đo/layout lần đầu (rowsView.width == 0 với view mới inflate). computeGroupLeadingMargin
        // cần width thật để tính margin nên sẽ không tính được ở lần bind đầu này -> đánh dấu để
        // sau khi layout xong thì bind lại 1 lần, tránh rơi về AlignmentSpan cũ (dính lại bug cũ).
        var needsRebindAfterLayout = false
        var hasContent = false

        // Row liền TRƯỚC có marginBottom > 0 hay không - dùng để ép row hiện tại phải bắt đầu
        // nhóm/dòng mới (xuống dòng), vì marginBottom luôn có nghĩa là "kết thúc dòng ở đây, các
        // row sau phải xuống dòng mới" dù bản thân row đó không khai báo breakRow.
        var previousHadMarginBottom = false

        // ----- Gom nhóm nhiều row liền kề vào chung 1 paragraph (1 dòng) - mặc định các row nối
        // chung dòng với nhau (giống hành vi gốc của "break": không breakRow thì không xuống
        // dòng), chỉ tách nhóm mới khi row.breakRow/row.line = true. Cả nhóm dùng chung 1 canh lề
        // (lấy từ row đầu tiên trong nhóm có khai báo align) và margin tính theo TỔNG bề rộng của
        // cả nhóm - để nhiều row (ví dụ 2 toggle) canh lề chung như 1 khối, thay vì canh lề riêng
        // từng row (dễ đè/lệch nhau, xem lịch sử: "Vấn đề 2" trong cuộc trò chuyện).
        // groupStart: vị trí bắt đầu (trong rowsView) của nhóm hiện tại.
        var groupStart = 0
        var groupAlign = Layout.Alignment.ALIGN_NORMAL
        var groupContentWidth = 0f

        // Áp canh lề (margin) cho toàn bộ nhóm [groupStart, groupEnd) - gọi khi 1 nhóm đã đầy đủ
        // (trước khi bắt đầu nhóm mới, và sau khi vòng lặp kết thúc cho nhóm cuối cùng).
        fun finalizeGroup(groupEnd: Int) {
            if (groupEnd <= groupStart || groupAlign == Layout.Alignment.ALIGN_NORMAL) {
                return
            }
            val spannable = rowsView.text as? Spannable ?: return
            if (rowsView.width == 0) {
                needsRebindAfterLayout = true
                spannable.setSpan(AlignmentSpan.Standard(groupAlign), groupStart, groupEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                return
            }
            val margin = computeGroupLeadingMargin(rowsView, groupAlign, groupContentWidth)
            if (margin != null) {
                spannable.setSpan(LeadingMarginSpan.Standard(margin, margin), groupStart, groupEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                spannable.setSpan(AlignmentSpan.Standard(groupAlign), groupStart, groupEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        // Gộp toàn bộ shell "sh" của mọi row trong LẦN bind() NÀY thành 1 lệnh
        // executeMultipleResultRoot() duy nhất (text-sh + icon-sh + photo-sh cùng lúc), thay vì
        // nhiều lệnh executeResultRoot() riêng lẻ tuần tự. bind() có thể chạy lại nhiều lần
        // (RecyclerView cuộn/rebind, sau khi bấm 1 toggle khiến toàn bộ rows được vẽ lại) nên việc
        // gộp này áp dụng lại mỗi lần bind(), không chỉ 1 lần lúc load trang - do đó icon-sh/photo-sh
        // của row KHÔNG cache như icon-sh/photo-sh cấp node (chạy lại mỗi lần rows được vẽ lại).
        val dynamicTextResults: Map<Int, String>
        val dynamicIconResults: Map<Int, String>
        val dynamicPhotoResults: Map<Int, String>
        run {
            val scripts = LinkedHashMap<String, String>()
            rows.forEachIndexed { index, row ->
                if (row.dynamicTextSh.isNotEmpty()) scripts["text:$index"] = row.dynamicTextSh
                if (row.iconSh.isNotEmpty()) scripts["icon:$index"] = row.iconSh
                if (row.photoSh.isNotEmpty()) scripts["photo:$index"] = row.photoSh
            }
            val results = if (scripts.isEmpty()) emptyMap() else ScriptEnvironmen.executeMultipleResultRoot(context, scripts, config)
            val textMap = HashMap<Int, String>()
            val iconMap = HashMap<Int, String>()
            val photoMap = HashMap<Int, String>()
            results.forEach { (key, value) ->
                val sepIndex = key.indexOf(':')
                val prefix = key.substring(0, sepIndex)
                val index = key.substring(sepIndex + 1).toInt()
                when (prefix) {
                    "text" -> textMap[index] = value
                    "icon" -> iconMap[index] = value
                    "photo" -> photoMap[index] = value
                }
            }
            dynamicTextResults = textMap
            dynamicIconResults = iconMap
            dynamicPhotoResults = photoMap
        }

        for ((rowIndex, row) in rows.withIndex()) {
            val isToggle = row.toggle == "checkbox" || row.toggle == "switch"

            // row.line = true: chèn 1 dòng chỉ chứa đường kẻ mảnh (full chiều rộng) NGAY TRƯỚC
            // nội dung row này, dùng để tách riêng phần rows (hoặc tách nhóm row) trực quan.
            // Luôn kết thúc nhóm hiện tại (không cho join qua đường kẻ).
            if (row.line) {
                finalizeGroup(rowsView.length())
                if (hasContent) {
                    rowsView.append("\n")
                }
                val dividerLine = SpannableString(" ")
                dividerLine.setSpan(DividerSpan(context), 0, dividerLine.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                rowsView.append(dividerLine)
                rowsView.append("\n")
                hasContent = true
                groupStart = rowsView.length()
                groupAlign = Layout.Alignment.ALIGN_NORMAL
                groupContentWidth = 0f
            }

            // Chỉ bắt đầu nhóm/dòng mới khi row.breakRow (hoặc row.line, hoặc row.marginTop, hoặc
            // row liền trước có marginBottom, hoặc chưa có nội dung nào trước đó) - mặc định
            // (breakRow=false) luôn nối chung dòng với row liền trước, đúng ngữ nghĩa gốc của
            // "break". Row có align != normal mà không muốn bị row kế tiếp nối chung dòng thì tự
            // khai báo break = true cho row kế tiếp đó.
            //
            // Lưu ý: marginTop/marginBottom KHÔNG còn tạo dòng trống riêng (xem TopExtraSpaceSpan/
            // BottomExtraSpaceSpan bên dưới) - chúng chỉ ép row phải nằm trên 1 paragraph riêng để
            // phần không gian nới thêm (ascent/descent) chỉ ảnh hưởng đúng dòng của row đó, không
            // lem sang các row khác đang chung dòng.
            val startsNewGroup = row.line || row.marginTop > 0 || row.breakRow || previousHadMarginBottom || !hasContent
            if (startsNewGroup) {
                finalizeGroup(rowsView.length())
                if (hasContent && !row.line) {
                    rowsView.append("\n")
                }
                groupStart = rowsView.length()
                groupAlign = row.align
                groupContentWidth = 0f
            } else {
                // Nối vào nhóm (dòng) hiện tại - lấy canh lề từ row đầu tiên trong nhóm có khai báo align.
                if (groupAlign == Layout.Alignment.ALIGN_NORMAL && row.align != Layout.Alignment.ALIGN_NORMAL) {
                    groupAlign = row.align
                }
                if (groupContentWidth > 0f) {
                    // Khoảng cách nhỏ giữa 2 row chung dòng cho dễ nhìn, không dính sát nhau
                    val gap = " "
                    rowsView.append(gap)
                    groupContentWidth += rowsView.paint.measureText(gap)
                }
            }
            previousHadMarginBottom = false
            // Nếu có khai báo "sh": lấy nội dung dòng từ kết quả shell đã gộp sẵn ở trên
            val label = if (row.dynamicTextSh.isNotEmpty()) {
                dynamicTextResults[rowIndex] ?: ""
            } else {
                row.text
            }

            // Row có "icon" (ảnh nhỏ inline, khác "photo" khối riêng): nạp ảnh trước để biết có
            // ghép được hay không, quyết định cách dựng "text" bên dưới. Nếu có "icon-sh": dùng
            // kết quả shell đã gộp sẵn ở trên (dynamicIconResults) thay cho row.icon tĩnh.
            val effectiveIcon = if (row.iconSh.isNotEmpty()) (dynamicIconResults[rowIndex] ?: "") else row.icon
            val hasIcon = !isToggle && effectiveIcon.isNotEmpty()
            val rowIconDrawableRaw = if (hasIcon) buildRowIconDrawable(context, effectiveIcon, row, config) else null
            val showIcon = rowIconDrawableRaw != null

            // Row dạng toggle (checkbox/switch nhỏ): chèn thêm 1 ký tự placeholder ở cuối (sau
            // label) để vẽ icon lên bằng ImageSpan - icon nằm ngay sau chữ. Muốn canh trái/giữa/
            // phải cho cả label+icon thì dùng field "align" ("normal"/"center"/"opposite") và
            // "break" giống hệt row text thường - không có cơ chế canh riêng cho icon. Toàn bộ
            // (label + icon) dùng chung 1 ClickableSpan để bấm đâu cũng đổi trạng thái được,
            // không dùng link/activity/script click thường.
            // Row có "icon": chèn 1 ký tự placeholder TRƯỚC hoặc SAU label (tuỳ "icon-position")
            // để vẽ ảnh nhỏ ghép ngay cạnh chữ, cùng cơ chế ImageSpan như toggle ở trên.
            val text = when {
                isToggle -> "$label \u2002 "
                showIcon && row.iconPosition == "before" -> "\u2002 $label"
                showIcon -> "$label \u2002"
                else -> label
            }
            val length = text.length
            val spannableString = SpannableString(text)

            var toggleDrawable: Drawable? = null
            if (isToggle) {
                // Vị trí ký tự placeholder: ngay trước khoảng trắng cuối cùng vừa thêm
                val iconIndex = length - 2
                toggleDrawable = buildToggleDrawable(context, row)
                spannableString.setSpan(VerticalCenterImageSpan(toggleDrawable), iconIndex, iconIndex + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            var rowIconDrawable: Drawable? = null
            if (showIcon && rowIconDrawableRaw != null) {
                rowIconDrawable = rowIconDrawableRaw
                val iconIndex = if (row.iconPosition == "before") 0 else length - 1
                spannableString.setSpan(VerticalCenterImageSpan(rowIconDrawable), iconIndex, iconIndex + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (rowIconDrawable is AnimationDrawable) {
                    GifPlaybackHelper.bindToTextView(rowsView, rowIconDrawable, row.iconGifAutoplay, row.iconGifLoopCount)
                    animatedRowIcons.add(rowIconDrawable)
                }
            }

            if (extraIconView != null) {
                extraIconView.visibility = View.GONE
                val effectivePhoto = if (row.photoSh.isNotEmpty()) (dynamicPhotoResults[rowIndex] ?: "") else row.photo
                if (effectivePhoto.isNotEmpty()) {
                    IconPathAnalysis().loadtextPhoto(context, effectivePhoto, row, config.pageConfigDir)?.run {
                        extraIconView.setImageDrawable(this)
                        extraIconView.visibility = View.VISIBLE
                        applyPhotoRealSize(extraIconView, row.photoRealSize)
                        GifPlaybackHelper.bind(extraIconView, row.photoGifAutoplay, row.photoGifLoopCount)
                    }
                }
            }

            if (row.underline) {
                spannableString.setSpan(UnderlineSpan(), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            if (row.strikethrough) {
                spannableString.setSpan(StrikethroughSpan(), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            if (row.monospace) {
                @Suppress("DEPRECATION")
                spannableString.setSpan(TypefaceSpan("monospace"), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            if (isToggle) {
                spannableString.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        row.checked = !row.checked
                        if (row.onChangeSh.isNotEmpty()) {
                            val result = ScriptEnvironmen.executeResultRoot(context, row.onChangeSh, config, object : HashMap<String, String>() {
                                init { put("state", if (row.checked) "1" else "0") }
                            })
                            if (result.trim().isNotEmpty()) {
                                DialogHelper.helpInfo(context, context.getString(R.string.kr_slice_script_result), result)
                            }
                        }
                        // Vẽ lại toàn bộ rows để cập nhật icon vừa đổi trạng thái
                        bind(context, rowsView, extraIconView, rows, config)
                    }

                    override fun updateDrawState(ds: TextPaint) {
                        ds.isUnderlineText = false
                    }
                }, 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else if (row.link.isNotEmpty()) {
                spannableString.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        if (row.link.isNotEmpty()) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(row.link))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (ex: Exception) {
                                Toast.makeText(context, context.getString(R.string.kr_slice_activity_fail), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    override fun updateDrawState(ds: TextPaint) {
                        ds.color = if (row.color != 1) ds.linkColor else row.color
                        ds.isUnderlineText = row.underline
                    }
                }, 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            if (!isToggle && row.activity.isNotEmpty()) {
                spannableString.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        TryOpenActivity(context, row.activity).tryOpen()
                    }

                    override fun updateDrawState(ds: TextPaint) {
                        ds.color = if (row.color != 1) ds.linkColor else row.color
                        ds.isUnderlineText = row.underline
                    }
                }, 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            if (!isToggle && row.onClickScript.isNotEmpty()) {
                spannableString.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        val result = ScriptEnvironmen.executeResultRoot(context, row.onClickScript, config)
                        if (result.trim().isNotEmpty()) {
                            DialogHelper.helpInfo(context, context.getString(R.string.kr_slice_script_result), result)
                        }
                    }

                    override fun updateDrawState(ds: TextPaint) {
                        ds.color = if (row.color != 1) ds.linkColor else row.color
                        ds.isUnderlineText = row.underline
                    }
                }, 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            if (row.color != -1) {
                spannableString.setSpan(ForegroundColorSpan(row.color), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            if (row.bgColor != -1) {
                spannableString.setSpan(BackgroundColorSpan(row.bgColor), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            if (row.bold && row.italic) {
                spannableString.setSpan(StyleSpan(Typeface.BOLD_ITALIC), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else if (row.bold) {
                spannableString.setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else if (row.italic) {
                spannableString.setSpan(StyleSpan(Typeface.ITALIC), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            if (row.size != -1) {
                spannableString.setSpan(AbsoluteSizeSpan(row.size, true), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            if (row.letterSpacing != 0f) {
                spannableString.setSpan(LetterSpacingSpan(row.letterSpacing), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            if (row.lineHeight != 0f) {
                spannableString.setSpan(LineHeightMultiplierSpan(row.lineHeight), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            // marginTop/marginBottom: KHÔNG tạo dòng trống riêng nữa (cách cũ gây thừa 1 dòng khi
            // row là row cuối cùng, vì "\n" ở cuối cùng của toàn bộ rowsView.text khiến TextView tự
            // vẽ thêm 1 dòng trống với chiều cao mặc định, không phải chiều cao marginBottom mong
            // muốn). Thay vào đó, nới trực tiếp ascent (phía trên) / descent (phía dưới) của ĐÚNG
            // dòng chứa row này bằng LineHeightSpan - không cần thêm ký tự "\n" hay " " nào.
            // Với marginTop: row đã được ép startsNewGroup = true ở trên nên chắc chắn nằm ở đầu 1
            // paragraph riêng, nới ascent chỉ ảnh hưởng đúng dòng đó.
            if (row.marginTop > 0) {
                spannableString.setSpan(TopExtraSpaceSpan(dpToPx(context, row.marginTop)), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (row.marginBottom > 0) {
                spannableString.setSpan(BottomExtraSpaceSpan(dpToPx(context, row.marginBottom)), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            // Đặt TextAlphaSpan SAU CÙNG (trong số các span ảnh hưởng màu/vẽ) để nó luôn được áp
            // dụng cuối, chỉ ghi đè kênh alpha của màu đã được set bởi ForegroundColorSpan/màu mặc
            // định - không đụng tới RGB, tránh mất màu chữ khi kết hợp cả color lẫn alpha.
            if (row.alpha in 0f..1f) {
                spannableString.setSpan(TextAlphaSpan(row.alpha), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            // Canh lề (trái/giữa/phải) giờ được áp dụng 1 LẦN cho cả NHÓM (finalizeGroup), không
            // set riêng cho từng row nữa - để hỗ trợ nhiều row chung 1 dòng và tránh lỗi
            // AlignmentSpan+ClickableSpan+ImageSpan (xem finalizeGroup/computeGroupLeadingMargin).
            rowsView.append(spannableString)
            hasContent = true

            // Cộng dồn bề rộng đã render của row này vào nhóm, dùng để tính margin canh lề chung.
            // Phải đo bằng paint mô phỏng ĐÚNG style thật của row (bold/italic/monospace/size) -
            // không phải paint gốc của rowsView - vì chữ đậm/monospace thường RỘNG HƠN chữ thường,
            // đo thiếu sẽ khiến margin tính thừa, đẩy cả nhóm lệch quá đà (có thể tràn ra ngoài lề).
            val measurePaint = measurePaintForRow(rowsView.paint, row)
            groupContentWidth += when {
                isToggle && toggleDrawable != null -> {
                    val placeholderIndex = text.length - 2
                    val beforeIcon = text.substring(0, placeholderIndex)
                    val afterIcon = text.substring(placeholderIndex + 1)
                    measurePaint.measureText(beforeIcon) + toggleDrawable.bounds.width() + measurePaint.measureText(afterIcon)
                }
                showIcon && rowIconDrawable != null -> {
                    val iconIdx = if (row.iconPosition == "before") 0 else text.length - 1
                    val beforeIcon = text.substring(0, iconIdx)
                    val afterIcon = text.substring(iconIdx + 1)
                    measurePaint.measureText(beforeIcon) + rowIconDrawable.bounds.width() + measurePaint.measureText(afterIcon)
                }
                else -> measurePaint.measureText(text)
            }

            // Row này có marginBottom - ghi nhớ để ép row KẾ TIẾP phải bắt đầu nhóm/dòng mới (xem
            // "previousHadMarginBottom" ở đầu vòng lặp), vì phần descent vừa nới thêm chỉ có ý
            // nghĩa "khoảng trống bên dưới dòng này" khi dòng này thực sự kết thúc tại đây.
            if (row.marginBottom > 0) {
                previousHadMarginBottom = true
            }
        }

        // Áp canh lề cho nhóm CUỐI CÙNG (vòng lặp không có row nào phía sau để kích hoạt finalizeGroup).
        finalizeGroup(rowsView.length())

        // Chặn crash Editor.touchPositionIsInSelection khi long-press vào vùng text
        // (đã bỏ tính năng copy nội dung khi long-press). Tắt luôn haptic feedback vì Android tự
        // rung khi performLongClick() được gọi, bất kể listener trả về true hay false.
        rowsView.isHapticFeedbackEnabled = false
        rowsView.setOnLongClickListener {
            true
        }
        rowsView.tag = animatedRowIcons

        if (needsRebindAfterLayout) {
            rowsView.post {
                if (rowsView.width > 0) {
                    bind(context, rowsView, extraIconView, rows, config)
                }
            }
        }
    }

    // Vẽ 1 đường kẻ mảnh ngang qua hết chiều rộng dòng - dùng cho row có "line = true". Cài qua
    // LeadingMarginSpan (không chiếm margin - getLeadingMargin trả 0) để không ảnh hưởng layout
    // ký tự, chỉ tận dụng callback drawLeadingMargin để vẽ tự do trên toàn bộ chiều rộng layout.
    private class DividerSpan(private val context: Context) : LeadingMarginSpan {
        override fun getLeadingMargin(first: Boolean): Int = 0

        override fun drawLeadingMargin(canvas: Canvas, paint: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int, text: CharSequence?, start: Int, end: Int, first: Boolean, layout: Layout?) {
            val strokePaint = BlurEngine.getStrokePaint(context)
            // Đẩy đường kẻ xuống thấp hơn giữa dòng 1 chút (30% chiều cao dòng thay vì 50%/giữa)
            val y = top + (bottom - top) * 0.65f
            val right = (layout?.width ?: 0).toFloat()
            canvas.drawLine(0f, y, right, y, strokePaint)
        }
    }

    // Span điều chỉnh khoảng cách giữa các chữ (đơn vị em, giống thuộc tính letterSpacing của
    // TextView/Paint). Kế thừa MetricAffectingSpan (thay vì CharacterStyle) vì letter-spacing làm
    // thay đổi bề rộng chữ - cần override cả updateMeasureState để layout đo đúng, không chỉ
    // updateDrawState (chỉ ảnh hưởng lúc vẽ).
    private class LetterSpacingSpan(private val spacing: Float) : MetricAffectingSpan() {
        override fun updateDrawState(tp: TextPaint) {
            tp.letterSpacing = spacing
        }

        override fun updateMeasureState(tp: TextPaint) {
            tp.letterSpacing = spacing
        }
    }

    // Span tạo 1 dòng TRỐNG có chiều cao cố định (px) - không vẽ nội dung gì, chỉ ép chiều cao
    // dòng chứa ký tự placeholder (" ") đúng bằng heightPx. Không còn dùng cho marginTop/
    // marginBottom (xem TopExtraSpaceSpan/BottomExtraSpaceSpan) nhưng vẫn giữ lại vì có thể còn
    // được dùng ở nơi khác cần 1 dòng trống độc lập thực sự.
    private class VerticalSpaceSpan(private val heightPx: Int) : LineHeightSpan {
        override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, lineHeight: Int, fm: Paint.FontMetricsInt) {
            fm.ascent = -heightPx
            fm.top = fm.ascent
            fm.descent = 0
            fm.bottom = fm.descent
        }
    }

    // Nới thêm khoảng trống PHÍA TRÊN (ascent) của đúng dòng/paragraph mà span này được gắn vào -
    // dùng cho row.marginTop. KHÔNG tạo dòng trống riêng: chỉ "kéo" ascent của dòng chứa nội dung
    // thật lên cao hơn, nên không có ký tự nào bị thêm vào text, tránh hẳn lỗi thừa dòng khi row
    // này là row cuối cùng của rowsView.
    private class TopExtraSpaceSpan(private val extraPx: Int) : LineHeightSpan {
        override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, lineHeight: Int, fm: Paint.FontMetricsInt) {
            fm.ascent -= extraPx
            fm.top -= extraPx
        }
    }

    // Nới thêm khoảng trống PHÍA DƯỚI (descent) của đúng dòng/paragraph mà span này được gắn vào -
    // dùng cho row.marginBottom. Cùng nguyên lý với TopExtraSpaceSpan: không thêm ký tự/dòng nào,
    // chỉ "đẩy" descent của dòng hiện tại xuống thấp hơn.
    private class BottomExtraSpaceSpan(private val extraPx: Int) : LineHeightSpan {
        override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, lineHeight: Int, fm: Paint.FontMetricsInt) {
            fm.descent += extraPx
            fm.bottom += extraPx
        }
    }

    // Span điều chỉnh chiều cao dòng (line height) theo hệ số nhân so với chiều cao dòng mặc định
    // của font hiện tại. Cộng thêm/bớt đều 2 bên (trên ascent/top và dưới descent/bottom) để chữ
    // vẫn nằm giữa dòng theo chiều dọc, không bị dồn lệch lên/xuống khi tăng/giảm độ cao.
    private class LineHeightMultiplierSpan(private val multiplier: Float) : LineHeightSpan {
        override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, lineHeight: Int, fm: Paint.FontMetricsInt) {
            val original = fm.descent - fm.ascent
            if (original <= 0) return
            val extra = ((multiplier - 1f) * original).toInt()
            if (extra == 0) return
            val topExtra = extra / 2
            val bottomExtra = extra - topExtra
            fm.ascent -= topExtra
            fm.top -= topExtra
            fm.descent += bottomExtra
            fm.bottom += bottomExtra
        }
    }

    // Span điều chỉnh độ trong suốt (alpha) của chữ mà KHÔNG đổi màu (RGB) - chỉ ghi đè kênh alpha
    // của paint tại thời điểm vẽ. Nhờ được add SAU CÙNG (xem nơi gọi setSpan), span này chạy sau
    // ForegroundColorSpan nên alpha luôn được áp cuối cùng, không bị màu chữ override lại thành 255.
    private class TextAlphaSpan(alpha: Float) : CharacterStyle() {
        private val alphaValue = (alpha.coerceIn(0f, 1f) * 255).toInt()

        override fun updateDrawState(tp: TextPaint) {
            tp.alpha = alphaValue
        }
    }

    // LinkMovementMethod gốc quy đổi toạ độ chạm sang offset ký tự gần nhất trong dòng
    // (Layout.getOffsetForHorizontal) mà không kiểm tra toạ độ đó có thực sự nằm trong vùng chữ
    // được vẽ hay không - nên bấm vào khoảng trống do canh lề/margin cũng bị tính là bấm trúng
    // ClickableSpan của dòng đó. Lớp này chặn trước: nếu x nằm ngoài [getLineLeft, getLineRight]
    // của dòng (tức ngoài vùng chữ/icon thực tế), bỏ qua sự kiện thay vì chuyển cho lớp cha xử lý.
    private class BoundedLinkMovementMethod : LinkMovementMethod() {
        companion object {
            val instance = BoundedLinkMovementMethod()
        }

        override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_UP) {
                val layout = widget.layout
                if (layout != null) {
                    val x = event.x.toInt() - widget.totalPaddingLeft + widget.scrollX
                    val y = event.y.toInt() - widget.totalPaddingTop + widget.scrollY
                    val line = layout.getLineForVertical(y)
                    if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) {
                        return false
                    }
                }
            }
            return super.onTouchEvent(widget, buffer, event)
        }
    }

    // ImageSpan.ALIGN_CENTER canh icon theo giữa cả dòng (line box), nhưng dòng có thể cao hơn
    // vùng chữ thật (do line spacing, dấu, ...) khiến icon bị lệch trên/dưới so với text xung
    // quanh. Class này canh icon theo giữa vùng chữ thật (ascent/descent của Paint tại vị trí
    // vẽ) để icon luôn thẳng hàng với text.
    private class VerticalCenterImageSpan(drawable: Drawable) : ImageSpan(drawable) {
        override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
            val b = drawable
            canvas.save()

            val fontMetrics = paint.fontMetricsInt
            val textCenter = y + (fontMetrics.descent + fontMetrics.ascent) / 2f
            val transY = textCenter - b.bounds.height() / 2f

            canvas.translate(x, transY)
            b.draw(canvas)
            canvas.restore()
        }
    }

    // Tạo 1 TextPaint mô phỏng đúng style thật mà row sẽ được vẽ (bold/italic/monospace/size/
    // letter-spacing) - dùng để ĐO bề rộng cho chính xác (xem nơi gọi). Giống hệt cách các Span
    // tương ứng (StyleSpan/TypefaceSpan/AbsoluteSizeSpan/LetterSpacingSpan) áp dụng lúc vẽ, chỉ
    // khác là áp trực tiếp lên paint thay vì gắn Span, để đo mà không cần vẽ thật.
    private fun measurePaintForRow(basePaint: TextPaint, row: TextNode.TextRow): TextPaint {
        if (!row.bold && !row.italic && !row.monospace && row.size == -1 && row.letterSpacing == 0f) {
            return basePaint
        }
        val paint = TextPaint(basePaint)
        if (row.monospace) {
            @Suppress("DEPRECATION")
            paint.typeface = Typeface.MONOSPACE
        }
        if (row.bold && row.italic) {
            paint.typeface = Typeface.create(paint.typeface, Typeface.BOLD_ITALIC)
        } else if (row.bold) {
            paint.typeface = Typeface.create(paint.typeface, Typeface.BOLD)
        } else if (row.italic) {
            paint.typeface = Typeface.create(paint.typeface, Typeface.ITALIC)
        }
        if (row.size != -1) {
            // AbsoluteSizeSpan(row.size, true) - true nghĩa là đơn vị dp, cần nhân density giống hệt
            paint.textSize = row.size * paint.density
        }
        if (row.letterSpacing != 0f) {
            paint.letterSpacing = row.letterSpacing
        }
        return paint
    }

    // Tính margin trái để "canh giữa/phải" thủ công cho cả 1 NHÓM row (thay AlignmentSpan - xem lý
    // do ở finalizeGroup). contentWidth là tổng bề rộng đã đo (label + icon nếu có + khoảng cách
    // giữa các row cùng dòng) của TOÀN BỘ row trong nhóm, do nơi gọi cộng dồn sẵn.
    // Trả về null nếu chưa đo được (view chưa layout xong) để nơi gọi fallback về AlignmentSpan cũ.
    private fun computeGroupLeadingMargin(rowsView: TextView, align: Layout.Alignment, contentWidth: Float): Int? {
        val available = rowsView.width - rowsView.paddingLeft - rowsView.paddingRight
        if (available <= 0) {
            return null
        }
        val extra = available - contentWidth
        if (extra <= 0) {
            return null
        }
        return when (align) {
            Layout.Alignment.ALIGN_OPPOSITE -> extra.toInt()
            Layout.Alignment.ALIGN_CENTER -> (extra / 2f).toInt()
            else -> null
        }
    }

    // Chuyển đổi dp sang px theo density hiện tại của thiết bị, dùng cho marginTop/marginBottom.
    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    // Nạp + dựng drawable cho icon inline cạnh chữ của row - kích thước lấy theo "icon-size" (dp)
    // nếu có khai báo, ngược lại mặc định 18dp (xấp xỉ 1 dòng chữ cỡ vừa). iconPath truyền tường
    // minh (thay vì đọc row.icon trực tiếp) để hỗ trợ cả icon tĩnh và icon-sh động; các field gif
    // (iconGifNum/iconGifTime) vẫn đọc từ row vì -sh chỉ thay path, không thay cấu hình gif.
    // Trả về null nếu không có icon hoặc không nạp được ảnh (ảnh lỗi/không tồn tại).
    private fun buildRowIconDrawable(context: Context, iconPath: String, row: TextNode.TextRow, config: NodeInfoBase): Drawable? {
        val loaded = IconPathAnalysis().loadRowIcon(context, iconPath, config.pageConfigDir, row.iconGifNum, row.iconGifTime) ?: return null
        val density = context.resources.displayMetrics.density
        val defaultDp = 18
        val size = ((if (row.iconSize > 0) row.iconSize else defaultDp) * density).toInt()
        val drawable = loaded.mutate()
        drawable.setBounds(0, 0, size, size)
        return drawable
    }

    // Tạo drawable icon cho row dạng toggle (checkbox/switch), kích thước nhỏ vừa 1 dòng text,
    // chọn ảnh theo loại (checkbox/switch) và trạng thái hiện tại (checked/unchecked).
    private fun buildToggleDrawable(context: Context, row: TextNode.TextRow): Drawable {
        val density = context.resources.displayMetrics.density
        val drawableRes = if (row.toggle == "switch") {
            if (row.checked) R.drawable.kr_row_switch_on else R.drawable.kr_row_switch_off
        } else {
            if (row.checked) R.drawable.checkbox_true else R.drawable.checkbox_false
        }
        val drawable = context.getDrawable(drawableRes)!!.mutate()
        val width: Int
        val height: Int
        if (row.toggle == "switch") {
            width = (28 * density).toInt()
            height = (16 * density).toInt()
        } else {
            width = (20 * density).toInt()
            height = (20 * density).toInt()
        }
        drawable.setBounds(0, 0, width, height)
        return drawable
    }

    // Nếu photoRealSize = true: hiển thị ảnh trong khung vuông (chiều rộng = chiều cao = chiều ngang màn hình),
    // căn giữa theo chiều ngang; ảnh sẽ được scale vừa vặn trong khung bằng CENTER_INSIDE mà không bị méo.
    private fun applyPhotoRealSize(imageView: ImageView, realSize: Boolean) {
        val params = imageView.layoutParams ?: return
        val maxSize = imageView.context.resources.displayMetrics.widthPixels

        if (realSize) {
            imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
            imageView.adjustViewBounds = true
            imageView.maxWidth = maxSize
            imageView.maxHeight = maxSize

            params.width = maxSize
            params.height = maxSize

            when (params) {
                is android.widget.LinearLayout.LayoutParams -> params.gravity = android.view.Gravity.CENTER_HORIZONTAL
                is android.widget.RelativeLayout.LayoutParams -> params.addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL)
            }
            imageView.layoutParams = params
        } else {
            imageView.scaleType = ImageView.ScaleType.FIT_CENTER
            imageView.adjustViewBounds = true
            imageView.maxWidth = Int.MAX_VALUE
            imageView.maxHeight = Int.MAX_VALUE

            params.width = android.widget.LinearLayout.LayoutParams.MATCH_PARENT
            params.height = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT

            when (params) {
                is android.widget.LinearLayout.LayoutParams -> params.gravity = android.view.Gravity.NO_GRAVITY
                is android.widget.RelativeLayout.LayoutParams -> params.addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL, 0)
            }
            imageView.layoutParams = params
        }
    }
}
