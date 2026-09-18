package com.omarea.krscript.ui

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.net.Uri
import android.text.Editable
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.*
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
import java.util.Collections
import java.util.WeakHashMap

object RowsRenderHelper {

    @Volatile
    private var pageReady = true
    private val pendingRefreshViews: MutableSet<TextView> = Collections.newSetFromMap(WeakHashMap())

    fun beginPageLoad() {
        pageReady = false
        pendingRefreshViews.clear()
    }

    fun endPageLoad() {
        pageReady = true
        val views = ArrayList(pendingRefreshViews)
        pendingRefreshViews.clear()
        views.forEach { rowsView ->
            if (rowsView.isAttachedToWindow) {
                val runnable = rowsView.getTag(R.id.kr_rows_refresh_runnable) as? Runnable
                val delayMs = (rowsView.tag as? RowsRenderState)?.refreshIntervalMs ?: 0L
                if (runnable != null && delayMs > 0L) {
                    rowsView.postDelayed(runnable, delayMs)
                }
            }
        }
    }

    private class RowsRenderState(
        val animatedIcons: MutableList<Animatable>,
        val rowRanges: Map<Int, IntArray>,
        val zoneGroupRows: Set<Int>,
        val flashAnimators: MutableList<ValueAnimator> = ArrayList(),
        val lastRefreshTimes: MutableMap<Int, Long> = HashMap(),
        val refreshIntervalMs: Long = 0L
    )

    private class RowSpanResult(
        val spannable: SpannableString,
        val width: Float
    )

    private class DynamicResults(
        val text: Map<Int, String>,
        val icon: Map<Int, String>,
        val photo: Map<Int, String>,
        val progress: Map<Int, String>
    )

    fun bind(
        context: Context,
        rowsView: TextView?,
        extraIconView: ImageView?,
        rows: List<TextNode.TextRow>,
        config: NodeInfoBase,
        htmlContainer: FrameLayout? = null
    ) {
        if (rowsView == null) {
            return
        }
        RowsHtmlRenderHelper.bind(context, htmlContainer, rows, config)
        if (rows.isEmpty()) {
            rowsView.visibility = View.GONE
            extraIconView?.visibility = View.GONE
            (rowsView.getTag(R.id.kr_rows_refresh_runnable) as? Runnable)?.let { rowsView.removeCallbacks(it) }
            rowsView.setTag(R.id.kr_rows_refresh_runnable, null)
            return
        }
        val dynamic = fetchDynamicResults(context, rows, config)
        renderRows(context, rowsView, extraIconView, rows, config, htmlContainer, dynamic)
    }

    private fun fetchDynamicResults(context: Context, rows: List<TextNode.TextRow>, config: NodeInfoBase): DynamicResults {
        val scripts = LinkedHashMap<String, String>()
        rows.forEachIndexed { index, row ->
            if (row.dynamicTextSh.isNotEmpty()) scripts["text:$index"] = row.dynamicTextSh
            if (row.iconSh.isNotEmpty()) scripts["icon:$index"] = row.iconSh
            if (row.photoSh.isNotEmpty()) scripts["photo:$index"] = row.photoSh
            if (row.progressSh.isNotEmpty()) scripts["progress:$index"] = row.progressSh
        }
        val results = if (scripts.isEmpty()) emptyMap() else ScriptEnvironmen.executeMultipleResultRoot(context, scripts, config)
        val textMap = HashMap<Int, String>()
        val iconMap = HashMap<Int, String>()
        val photoMap = HashMap<Int, String>()
        val progressMap = HashMap<Int, String>()
        results.forEach { (key, value) ->
            val sepIndex = key.indexOf(':')
            val prefix = key.substring(0, sepIndex)
            val index = key.substring(sepIndex + 1).toInt()
            when (prefix) {
                "text" -> textMap[index] = value
                "icon" -> iconMap[index] = value
                "photo" -> photoMap[index] = value
                "progress" -> progressMap[index] = value
            }
        }
        return DynamicResults(textMap, iconMap, photoMap, progressMap)
    }

    // renderRows() (build lần đầu, gọi từ ListItem*.init{}) LUÔN chạy TRƯỚC KHI rowsView (và
    // cả card cha của nó) được addView() vào rootGroup (xem PageLayoutRender.renderNode(): tạo
    // xong ListItem* - kéo theo chạy hết init{} - RỒI MỚI parent.addView()) - nên rowsView.width
    // luôn = 0 lúc này, không phải do chưa kịp layout. Ước lượng thay vì đợi 1 layout pass thật
    // (post{} + build lại lần 2 như bản cũ): lấy bề rộng MÀN HÌNH THẬT (luôn có sẵn, không phụ
    // thuộc trạng thái attach) rồi trừ dần padding/margin của từng lớp cha giữa rowsView và gốc
    // card (layout riêng của item) - phần "lơ lửng" chưa biết thật sự chỉ là card/rootGroup/
    // ScrollView đều match_parent nên cuối cùng bằng đúng bề rộng màn hình.
    private fun estimateAvailableWidth(rowsView: TextView): Int {
        val measured = rowsView.width
        if (measured > 0) {
            return measured - rowsView.paddingLeft - rowsView.paddingRight
        }
        var width = rowsView.context.resources.displayMetrics.widthPixels
        width -= rowsView.paddingLeft + rowsView.paddingRight
        var node: View = rowsView
        while (true) {
            val parent = node.parent as? ViewGroup ?: break
            width -= parent.paddingLeft + parent.paddingRight
            (node.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                width -= it.leftMargin + it.rightMargin
            }
            node = parent
        }
        return width.coerceAtLeast(0)
    }

    private fun renderRows(
        context: Context,
        rowsView: TextView,
        extraIconView: ImageView?,
        rows: List<TextNode.TextRow>,
        config: NodeInfoBase,
        htmlContainer: FrameLayout?,
        dynamic: DynamicResults
    ) {
        val dynamicTextResults = dynamic.text
        val dynamicIconResults = dynamic.icon
        val dynamicPhotoResults = dynamic.photo
        val dynamicProgressResults = dynamic.progress

        rowsView.text = ""
        (rowsView.tag as? RowsRenderState)?.animatedIcons?.forEach { it.stop() }
        (rowsView.tag as? RowsRenderState)?.flashAnimators?.forEach { it.cancel() }
        val animatedRowIcons = ArrayList<Animatable>()
        (rowsView.getTag(R.id.kr_rows_refresh_runnable) as? Runnable)?.let { rowsView.removeCallbacks(it) }
        val allowCopy = rows.any { it.copy }
        rowsView.setTextIsSelectable(allowCopy)
        rowsView.movementMethod = BoundedLinkMovementMethod.instance

        rowsView.isClickable = true
        rowsView.setOnClickListener { }
        if (allowCopy) {
            rowsView.isHapticFeedbackEnabled = true
            rowsView.setOnLongClickListener(null)
        } else {
            rowsView.isHapticFeedbackEnabled = false
            rowsView.setOnLongClickListener { true }
        }

        val rowRanges = HashMap<Int, IntArray>()
        val zoneGroupRows = HashSet<Int>()

        fun appendSpacer(px: Int) {
            if (px <= 0) return
            val sp = SpannableString("\u200B")
            sp.setSpan(SpacerSpan(px), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            rowsView.append(sp)
        }

        fun buildZone(items: List<Int>): Pair<List<Pair<Int, RowSpanResult>>, Float> {
            var width = 0f
            val built = ArrayList<Pair<Int, RowSpanResult>>()
            items.forEachIndexed { i, idx ->
                if (i > 0) {
                    width += rowsView.paint.measureText(" ")
                }
                val result = buildRowSpan(context, rowsView, extraIconView, rows, config, htmlContainer, rows[idx], idx, dynamicTextResults, dynamicIconResults, dynamicProgressResults, animatedRowIcons)
                built.add(idx to result)
                width += result.width
            }
            return Pair(built, width)
        }

        fun appendZone(built: List<Pair<Int, RowSpanResult>>) {
            built.forEachIndexed { i, pair ->
                val (idx, result) = pair
                if (i > 0) {
                    rowsView.append(" ")
                }
                val start = rowsView.length()
                rowsView.append(result.spannable)
                rowRanges[idx] = intArrayOf(start, rowsView.length())
                zoneGroupRows.add(idx)
            }
        }

        val lineGroups = splitIntoLineGroups(rows)

        for (group in lineGroups) {
            val firstRow = rows[group.first()]

            if (firstRow.line) {
                if (rowsView.length() > 0) {
                    rowsView.append("\n")
                }
                val dividerLine = SpannableString(" ")
                dividerLine.setSpan(DividerSpan(context), 0, dividerLine.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                rowsView.append(dividerLine)
                rowsView.append("\n")
            } else if (rowsView.length() > 0) {
                rowsView.append("\n")
            }

            val groupTextStart = rowsView.length()

            val leftIdx = group.filter { rows[it].align == Layout.Alignment.ALIGN_NORMAL }
            val centerIdx = group.filter { rows[it].align == Layout.Alignment.ALIGN_CENTER }
            val rightIdx = group.filter { rows[it].align == Layout.Alignment.ALIGN_OPPOSITE }
            val zoneEligible = leftIdx.size <= 4 && centerIdx.size <= 4 && rightIdx.size <= 4 &&
                (centerIdx.isNotEmpty() || rightIdx.isNotEmpty())

            if (zoneEligible) {
                val (leftBuilt, leftWidth) = buildZone(leftIdx)
                val (centerBuilt, centerWidth) = buildZone(centerIdx)
                val (rightBuilt, rightWidth) = buildZone(rightIdx)
                val availableWidth = estimateAvailableWidth(rowsView)

                if (availableWidth <= 0) {
                    // Phòng hờ (thực tế gần như không xảy ra nhờ estimateAvailableWidth() luôn
                    // ước lượng được 1 số dương ngay cả lúc rowsView chưa gắn vào cây view thật)
                    // - không center/canh phải được thì vẫn hiện tạm nối liên tiếp còn hơn crash.
                    appendZone(leftBuilt)
                    if (centerBuilt.isNotEmpty()) {
                        rowsView.append(" ")
                        appendZone(centerBuilt)
                    }
                    if (rightBuilt.isNotEmpty()) {
                        rowsView.append(" ")
                        appendZone(rightBuilt)
                    }
                } else {
                    appendZone(leftBuilt)
                    if (centerBuilt.isEmpty()) {
                        if (rightBuilt.isNotEmpty()) {
                            val spacer = (availableWidth - leftWidth - rightWidth).toInt().coerceAtLeast(0)
                            appendSpacer(spacer)
                            appendZone(rightBuilt)
                        }
                    } else {
                        val centerStart = (availableWidth - centerWidth) / 2f
                        val spacer1 = (centerStart - leftWidth).toInt().coerceAtLeast(0)
                        appendSpacer(spacer1)
                        appendZone(centerBuilt)
                        if (rightBuilt.isNotEmpty()) {
                            val rightStart = availableWidth - rightWidth
                            val usedSoFar = leftWidth + spacer1 + centerWidth
                            val spacer2 = (rightStart - usedSoFar).toInt().coerceAtLeast(0)
                            appendSpacer(spacer2)
                            appendZone(rightBuilt)
                        }
                    }
                }
            } else {
                group.forEachIndexed { i, idx ->
                    if (i > 0) {
                        rowsView.append(" ")
                    }
                    val result = buildRowSpan(context, rowsView, extraIconView, rows, config, htmlContainer, rows[idx], idx, dynamicTextResults, dynamicIconResults, dynamicProgressResults, animatedRowIcons)
                    val start = rowsView.length()
                    rowsView.append(result.spannable)
                    rowRanges[idx] = intArrayOf(start, rowsView.length())
                }
                if (firstRow.align != Layout.Alignment.ALIGN_NORMAL) {
                    (rowsView.text as? Spannable)?.setSpan(AlignmentSpan.Standard(firstRow.align), groupTextStart, rowsView.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }

        if (extraIconView != null) {
            extraIconView.visibility = View.GONE
            rows.forEachIndexed { index, row ->
                val effectivePhoto = if (row.photoSh.isNotEmpty()) (dynamicPhotoResults[index] ?: "") else row.photo
                if (effectivePhoto.isNotEmpty()) {
                    IconPathAnalysis().loadtextPhoto(context, effectivePhoto, row, config.pageConfigDir)?.run {
                        extraIconView.setImageDrawable(this)
                        extraIconView.visibility = View.VISIBLE
                        applyPhotoRealSize(extraIconView, row.photoRealSize)
                        GifPlaybackHelper.bind(extraIconView, row.photoGifAutoplay, row.photoGifLoopCount)
                    }
                }
            }
        }

        val minRefreshInterval = rows.filter { it.refreshInterval > 0 }.minOfOrNull { it.refreshInterval }
        val refreshIntervalMs = (minRefreshInterval ?: 0) * 1000L

        val now = System.currentTimeMillis()
        val lastRefreshTimes = HashMap<Int, Long>()
        rows.forEachIndexed { idx, r -> if (r.refreshInterval > 0) lastRefreshTimes[idx] = now }
        rowsView.tag = RowsRenderState(animatedRowIcons, rowRanges, zoneGroupRows, lastRefreshTimes = lastRefreshTimes, refreshIntervalMs = refreshIntervalMs)

        // estimateAvailableWidth() đã cho canh zone đúng ngay từ lần dựng đầu tiên (không cần
        // đợi 1 layout pass thật + build lại lần 2 như bản cũ) nên không còn cần ẩn/rebuild gì
        // thêm ở đây nữa - hiện luôn.
        rowsView.visibility = View.VISIBLE

        if (minRefreshInterval != null) {
            lateinit var tickRunnable: Runnable
            tickRunnable = Runnable {
                if (rowsView.isAttachedToWindow && rowsView.getTag(R.id.kr_rows_refresh_runnable) === tickRunnable) {
                    val tick = System.currentTimeMillis()
                    for ((idx, r) in rows.withIndex()) {
                        if (rowsView.getTag(R.id.kr_rows_refresh_runnable) !== tickRunnable) break
                        if (r.refreshInterval > 0) {
                            val last = (rowsView.tag as? RowsRenderState)?.lastRefreshTimes?.get(idx) ?: 0L
                            if (tick - last >= r.refreshInterval * 1000L) {
                                resetRow(context, rowsView, extraIconView, rows, config, idx, htmlContainer)
                                (rowsView.tag as? RowsRenderState)?.lastRefreshTimes?.set(idx, tick)
                            }
                        }
                    }
                    if (rowsView.getTag(R.id.kr_rows_refresh_runnable) === tickRunnable) {
                        rowsView.postDelayed(tickRunnable, refreshIntervalMs)
                    }
                }
            }
            rowsView.setTag(R.id.kr_rows_refresh_runnable, tickRunnable)
            if (pageReady) {
                rowsView.postDelayed(tickRunnable, refreshIntervalMs)
            } else {
                pendingRefreshViews.add(rowsView)
            }
        } else {
            rowsView.setTag(R.id.kr_rows_refresh_runnable, null)
        }
    }

    fun resetRow(
        context: Context,
        rowsView: TextView?,
        extraIconView: ImageView?,
        rows: List<TextNode.TextRow>,
        config: NodeInfoBase,
        rowIndex: Int,
        htmlContainer: FrameLayout? = null
    ) {
        if (rowsView == null || rowIndex !in rows.indices) {
            return
        }
        val state = rowsView.tag as? RowsRenderState
        val range = state?.rowRanges?.get(rowIndex)
        val editable = rowsView.text as? Editable
        if (state == null || range == null || editable == null || state.zoneGroupRows.contains(rowIndex) ||
            range[0] < 0 || range[1] > editable.length || range[0] > range[1]
        ) {
            bind(context, rowsView, extraIconView, rows, config, htmlContainer)
            return
        }

        val row = rows[rowIndex]
        val scripts = LinkedHashMap<String, String>()
        if (row.dynamicTextSh.isNotEmpty()) scripts["text"] = row.dynamicTextSh
        if (row.iconSh.isNotEmpty()) scripts["icon"] = row.iconSh
        if (row.progressSh.isNotEmpty()) scripts["progress"] = row.progressSh
        val results = if (scripts.isEmpty()) emptyMap() else ScriptEnvironmen.executeMultipleResultRoot(context, scripts, config)
        val textMap = results["text"]?.let { mapOf(rowIndex to it) } ?: emptyMap()
        val iconMap = results["icon"]?.let { mapOf(rowIndex to it) } ?: emptyMap()
        val progressMap = results["progress"]?.let { mapOf(rowIndex to it) } ?: emptyMap()

        val animated = ArrayList<Animatable>()
        val rendered = buildRowSpan(context, rowsView, extraIconView, rows, config, htmlContainer, row, rowIndex, textMap, iconMap, progressMap, animated)

        val start = range[0]
        val end = range[1]
        val oldText = editable.subSequence(start, end).toString()
        editable.replace(start, end, rendered.spannable)
        val delta = rendered.spannable.length - (end - start)
        val newEnd = start + rendered.spannable.length

        if (row.flash && oldText != rendered.spannable.toString()) {
            val flashColor = if (row.flashColor != -1) row.flashColor else 0xFFFFC107.toInt()
            val flashSpan = FlashBackgroundSpan(rowsView, flashColor)
            editable.setSpan(flashSpan, start, newEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            flashSpan.start()
            state.flashAnimators.add(flashSpan.animator)
        }

        val newRanges = HashMap<Int, IntArray>()
        state.rowRanges.forEach { (idx, r) ->
            newRanges[idx] = when {
                idx == rowIndex -> intArrayOf(start, newEnd)
                r[0] >= end -> intArrayOf(r[0] + delta, r[1] + delta)
                else -> r
            }
        }
        state.animatedIcons.addAll(animated)
        rowsView.tag = RowsRenderState(state.animatedIcons, newRanges, state.zoneGroupRows, state.flashAnimators, state.lastRefreshTimes, state.refreshIntervalMs)

        if (extraIconView != null && row.photoSh.isNotEmpty()) {
            val effectivePhoto = ScriptEnvironmen.executeResultRoot(context, row.photoSh, config)
            if (effectivePhoto.isNotEmpty()) {
                IconPathAnalysis().loadtextPhoto(context, effectivePhoto, row, config.pageConfigDir)?.run {
                    extraIconView.setImageDrawable(this)
                    extraIconView.visibility = View.VISIBLE
                    applyPhotoRealSize(extraIconView, row.photoRealSize)
                    GifPlaybackHelper.bind(extraIconView, row.photoGifAutoplay, row.photoGifLoopCount)
                }
            }
        }
    }

    // process = true: gọi thay cho bind() ở updateViewByShell() của ListItemAction/Page/
    // Download/Text sau khi trang đã tải xong (resolvePendingStates() đã chạy, row.checked
    // trong model đã đúng) - CHỈ vẽ lại đúng icon on/off của toggle (checkbox/switch) tại chỗ,
    // không build lại cả row nên không chạy lại text-sh/icon-sh/photo-sh/progress-sh của row
    // đó lần thứ 2 (khác bind() cũ, luôn rebuild toàn bộ row kéo theo chạy lại hết các sh này).
    fun refreshToggleStates(context: Context, rowsView: TextView?, rows: List<TextNode.TextRow>) {
        if (rowsView == null) return
        val state = rowsView.tag as? RowsRenderState ?: return
        val editable = rowsView.text as? Editable ?: return
        var changed = false
        rows.forEachIndexed { idx, row ->
            if (row.toggle != "checkbox" && row.toggle != "switch") return@forEachIndexed
            val range = state.rowRanges[idx] ?: return@forEachIndexed
            val start = range[0]
            val end = range[1]
            if (start < 0 || end > editable.length || start > end) return@forEachIndexed
            val spans = editable.getSpans(start, end, ToggleIconSpan::class.java)
            val span = spans.firstOrNull() ?: return@forEachIndexed
            val spanStart = editable.getSpanStart(span)
            val spanEnd = editable.getSpanEnd(span)
            val spanFlags = editable.getSpanFlags(span)
            val newDrawable = buildToggleDrawable(context, row)
            editable.removeSpan(span)
            editable.setSpan(ToggleIconSpan(newDrawable), spanStart, spanEnd, spanFlags)
            changed = true
        }
        if (changed) {
            rowsView.invalidate()
        }
    }

    private fun splitIntoLineGroups(rows: List<TextNode.TextRow>): List<List<Int>> {
        val groups = ArrayList<List<Int>>()
        var current = ArrayList<Int>()
        for (i in rows.indices) {
            val row = rows[i]
            val startsNewGroup = i == 0 || row.line || row.marginTop > 0 || row.breakRow || rows[i - 1].marginBottom > 0
            if (startsNewGroup && current.isNotEmpty()) {
                groups.add(current)
                current = ArrayList()
            }
            current.add(i)
        }
        if (current.isNotEmpty()) {
            groups.add(current)
        }
        return groups
    }

    private fun buildRowSpan(
        context: Context,
        rowsView: TextView,
        extraIconView: ImageView?,
        rows: List<TextNode.TextRow>,
        config: NodeInfoBase,
        htmlContainer: FrameLayout?,
        row: TextNode.TextRow,
        rowIndex: Int,
        dynamicTextResults: Map<Int, String>,
        dynamicIconResults: Map<Int, String>,
        dynamicProgressResults: Map<Int, String>,
        animatedRowIcons: MutableList<Animatable>
    ): RowSpanResult {
        val isToggle = row.toggle == "checkbox" || row.toggle == "switch"
        val rawLabel = if (row.dynamicTextSh.isNotEmpty()) {
            dynamicTextResults[rowIndex] ?: ""
        } else {
            row.text
        }

        val markdownSpans: List<MarkdownInlineHelper.MarkdownSpanInfo>
        val label: String
        if (row.markdown && rawLabel.isNotEmpty()) {
            val (plain, spans) = MarkdownInlineHelper.parse(rawLabel)
            label = plain
            markdownSpans = spans
        } else {
            label = rawLabel
            markdownSpans = emptyList()
        }

        val effectiveIcon = if (row.iconSh.isNotEmpty()) (dynamicIconResults[rowIndex] ?: "") else row.icon
        val hasIcon = !isToggle && effectiveIcon.isNotEmpty()
        val rowIconDrawableRaw = if (hasIcon) buildRowIconDrawable(context, effectiveIcon, row, config) else null
        val showIcon = rowIconDrawableRaw != null

        val effectiveProgress = if (row.progressSh.isNotEmpty()) {
            dynamicProgressResults[rowIndex]?.trim()?.toFloatOrNull() ?: -1f
        } else {
            row.progress
        }
        val hasProgress = effectiveProgress >= 0f
        val textBase = when {
            isToggle -> "$label \u2002 "
            showIcon && row.iconPosition == "before" -> "\u2002 $label"
            showIcon -> "$label \u2002"
            else -> label
        }
        val text = if (hasProgress) {
            if (textBase.isEmpty()) "\u2002" else "$textBase \u2002"
        } else {
            textBase
        }
        val length = text.length
        val spannableString = SpannableString(text)
        val markdownOffset = if (showIcon && row.iconPosition == "before") 2 else 0

        var toggleIconIndex = -1
        var toggleDrawable: Drawable? = null
        if (isToggle) {
            toggleIconIndex = textBase.length - 2
            toggleDrawable = buildToggleDrawable(context, row)
            spannableString.setSpan(ToggleIconSpan(toggleDrawable), toggleIconIndex, toggleIconIndex + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        var rowIconIndex = -1
        var rowIconDrawable: Drawable? = null
        if (showIcon && rowIconDrawableRaw != null) {
            rowIconDrawable = rowIconDrawableRaw
            rowIconIndex = if (row.iconPosition == "before") 0 else textBase.length - 1
            spannableString.setSpan(VerticalCenterImageSpan(rowIconDrawable), rowIconIndex, rowIconIndex + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val isRealGifDrawable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && rowIconDrawable is AnimatedImageDrawable
            if (rowIconDrawable is AnimationDrawable || isRealGifDrawable) {
                GifPlaybackHelper.bindToTextView(rowsView, rowIconDrawable, row.iconGifAutoplay, row.iconGifLoopCount)
                animatedRowIcons.add(rowIconDrawable as Animatable)
            }
        }

        var progressIndex = -1
        var progressWidthPx = 0
        if (hasProgress) {
            progressIndex = length - 1
            progressWidthPx = dpToPx(context, row.progressWidth)
            val progressHeightPx = dpToPx(context, row.progressHeight)
            val fillColor = if (row.progressColor != -1) row.progressColor else 0xFF4CAF50.toInt()
            val trackColor = if (row.progressTrackColor != -1) row.progressTrackColor else 0x33888888
            val ratio = if (row.progressMax > 0f) effectiveProgress / row.progressMax else 0f
            spannableString.setSpan(ProgressBarSpan(progressWidthPx, progressHeightPx, ratio, fillColor, trackColor), progressIndex, progressIndex + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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
                    runRowAction(context, row.confirm) {
                        row.checked = !row.checked
                        if (row.onChangeSh.isNotEmpty()) {
                            val result = ScriptEnvironmen.executeResultRoot(context, row.onChangeSh, config, object : HashMap<String, String>() {
                                init { put("state", if (row.checked) "1" else "0") }
                            })
                            if (result.trim().isNotEmpty()) {
                                DialogHelper.helpInfo(context, context.getString(R.string.kr_slice_script_result), result)
                            }
                        }
                        bind(context, rowsView, extraIconView, rows, config, htmlContainer)
                    }
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
                    ds.color = if (row.color != -1) row.color else ds.linkColor
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
                    ds.color = if (row.color != -1) row.color else ds.linkColor
                    ds.isUnderlineText = row.underline
                }
            }, 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        if (!isToggle && (row.onClickScript.isNotEmpty() || row.resetTarget != -1)) {
            spannableString.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    runRowAction(context, row.confirm) {
                        if (row.onClickScript.isNotEmpty()) {
                            val result = ScriptEnvironmen.executeResultRoot(context, row.onClickScript, config)
                            if (result.trim().isNotEmpty()) {
                                DialogHelper.helpInfo(context, context.getString(R.string.kr_slice_script_result), result)
                            }
                        }
                        when (row.resetTarget) {
                            -1 -> {}
                            -2 -> resetRow(context, rowsView, extraIconView, rows, config, rowIndex, htmlContainer)
                            -3 -> bind(context, rowsView, extraIconView, rows, config, htmlContainer)
                            else -> resetRow(context, rowsView, extraIconView, rows, config, row.resetTarget, htmlContainer)
                        }
                    }
                }

                override fun updateDrawState(ds: TextPaint) {
                    ds.color = if (row.color != -1) row.color else ds.linkColor
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

        if (row.marginTop > 0) {
            spannableString.setSpan(TopExtraSpaceSpan(dpToPx(context, row.marginTop)), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (row.marginBottom > 0) {
            spannableString.setSpan(BottomExtraSpaceSpan(dpToPx(context, row.marginBottom)), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        if (row.alpha in 0f..1f) {
            spannableString.setSpan(TextAlphaSpan(row.alpha), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        if (markdownSpans.isNotEmpty()) {
            applyMarkdownSpans(context, spannableString, markdownSpans, markdownOffset, length)
        }

        val measurePaint = measurePaintForRow(rowsView.paint, row)
        val specialSlots = ArrayList<Pair<Int, Float>>()
        if (toggleIconIndex != -1 && toggleDrawable != null) {
            specialSlots.add(toggleIconIndex to toggleDrawable.bounds.width().toFloat())
        }
        if (rowIconIndex != -1 && rowIconDrawable != null) {
            specialSlots.add(rowIconIndex to rowIconDrawable.bounds.width().toFloat())
        }
        if (progressIndex != -1) {
            specialSlots.add(progressIndex to progressWidthPx.toFloat())
        }
        specialSlots.sortBy { it.first }

        var width = 0f
        var cursor = 0
        for ((idx, slotWidth) in specialSlots) {
            if (idx > cursor) {
                width += measurePaint.measureText(text, cursor, idx)
            }
            width += slotWidth
            cursor = idx + 1
        }
        if (cursor < text.length) {
            width += measurePaint.measureText(text, cursor, text.length)
        }

        return RowSpanResult(spannableString, width)
    }

    private fun runRowAction(context: Context, confirmMessage: String, action: () -> Unit) {
        if (confirmMessage.isEmpty()) {
            action()
        } else {
            DialogHelper.confirm(context, message = confirmMessage, onConfirm = Runnable { action() })
        }
    }

    private class SpacerSpan(private val width: Int) : ReplacementSpan() {
        override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int = width

        override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        }
    }

    private class ProgressBarSpan(
        private val widthPx: Int,
        private val heightPx: Int,
        private val progress: Float,
        private val fillColor: Int,
        private val trackColor: Int
    ) : ReplacementSpan() {
        override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int = widthPx

        override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
            val cy = (top + bottom) / 2f
            val halfH = heightPx / 2f
            val radius = halfH
            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = trackColor }
            canvas.drawRoundRect(RectF(x, cy - halfH, x + widthPx, cy + halfH), radius, radius, trackPaint)
            val fillWidth = widthPx * progress.coerceIn(0f, 1f)
            if (fillWidth > 0f) {
                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor }
                canvas.drawRoundRect(RectF(x, cy - halfH, x + fillWidth, cy + halfH), radius, radius, fillPaint)
            }
        }
    }

    private class FlashBackgroundSpan(private val view: TextView, color: Int) : CharacterStyle(), UpdateAppearance {
        private val baseColor = color and 0x00FFFFFF
        private var currentAlpha = 0
        val animator: ValueAnimator = ValueAnimator.ofInt(0x80, 0).apply {
            duration = 700
            addUpdateListener {
                currentAlpha = it.animatedValue as Int
                view.invalidate()
            }
        }

        fun start() {
            animator.start()
        }

        override fun updateDrawState(tp: TextPaint) {
            if (currentAlpha > 0) {
                tp.bgColor = (currentAlpha shl 24) or baseColor
            }
        }
    }

    private class DividerSpan(private val context: Context) : LeadingMarginSpan {
        override fun getLeadingMargin(first: Boolean): Int = 0

        override fun drawLeadingMargin(canvas: Canvas, paint: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int, text: CharSequence?, start: Int, end: Int, first: Boolean, layout: Layout?) {
            val strokePaint = BlurEngine.getStrokePaint(context)
            val y = top + (bottom - top) * 0.65f
            val right = (layout?.width ?: 0).toFloat()
            canvas.drawLine(0f, y, right, y, strokePaint)
        }
    }

    private class LetterSpacingSpan(private val spacing: Float) : MetricAffectingSpan() {
        override fun updateDrawState(tp: TextPaint) {
            tp.letterSpacing = spacing
        }

        override fun updateMeasureState(tp: TextPaint) {
            tp.letterSpacing = spacing
        }
    }

    private class TopExtraSpaceSpan(private val extraPx: Int) : LineHeightSpan {
        override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, lineHeight: Int, fm: Paint.FontMetricsInt) {
            fm.ascent -= extraPx
            fm.top -= extraPx
        }
    }

    private class BottomExtraSpaceSpan(private val extraPx: Int) : LineHeightSpan {
        override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, lineHeight: Int, fm: Paint.FontMetricsInt) {
            fm.descent += extraPx
            fm.bottom += extraPx
        }
    }

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

    private class TextAlphaSpan(alpha: Float) : CharacterStyle() {
        private val alphaValue = (alpha.coerceIn(0f, 1f) * 255).toInt()

        override fun updateDrawState(tp: TextPaint) {
            tp.alpha = alphaValue
        }
    }

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

    // Đánh dấu riêng span icon của toggle (checkbox/switch trong row) để refreshToggleStates()
    // tìm lại đúng vị trí của nó trong editable mà không cần build lại cả row - xem
    // refreshToggleStates().
    private class ToggleIconSpan(drawable: Drawable) : VerticalCenterImageSpan(drawable)

    private open class VerticalCenterImageSpan(drawable: Drawable) : ImageSpan(drawable) {
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
            paint.textSize = row.size * paint.density
        }
        if (row.letterSpacing != 0f) {
            paint.letterSpacing = row.letterSpacing
        }
        return paint
    }


    private fun applyMarkdownSpans(
        context: Context,
        spannableString: SpannableString,
        spans: List<MarkdownInlineHelper.MarkdownSpanInfo>,
        offset: Int,
        maxLength: Int
    ) {
        for (info in spans) {
            val start = info.start + offset
            val end = (info.end + offset).coerceAtMost(maxLength)
            if (start < 0 || end <= start || start >= maxLength) {
                continue
            }
            when (info.type) {
                MarkdownInlineHelper.MarkdownSpanType.BOLD ->
                    spannableString.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                MarkdownInlineHelper.MarkdownSpanType.ITALIC ->
                    spannableString.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                MarkdownInlineHelper.MarkdownSpanType.STRIKETHROUGH ->
                    spannableString.setSpan(StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                MarkdownInlineHelper.MarkdownSpanType.CODE -> {
                    val customRadius = info.href.toFloatOrNull()
                    val codeSpan = if (customRadius != null) MarkdownCodeSpan(context, cornerRadiusDp = customRadius) else MarkdownCodeSpan(context)
                    spannableString.setSpan(codeSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                MarkdownInlineHelper.MarkdownSpanType.COLOR -> {
                    val colorInt = parseMarkdownColor(info.href)
                    if (colorInt != null) {
                        spannableString.setSpan(ForegroundColorSpan(colorInt), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                MarkdownInlineHelper.MarkdownSpanType.LINK -> {
                    val href = info.href
                    spannableString.setSpan(object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(href))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (ex: Exception) {
                                Toast.makeText(context, context.getString(R.string.kr_slice_activity_fail), Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun updateDrawState(ds: TextPaint) {
                            ds.isUnderlineText = true
                        }
                    }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
    }

    private fun parseMarkdownColor(value: String): Int? {
        return try {
            Color.parseColor(value)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    private fun buildRowIconDrawable(context: Context, iconPath: String, row: TextNode.TextRow, config: NodeInfoBase): Drawable? {
        val loaded = IconPathAnalysis().loadRowIcon(context, iconPath, config.pageConfigDir, row.iconGifNum, row.iconGifTime, row.iconRealGif) ?: return null
        val density = context.resources.displayMetrics.density
        val defaultDp = 18
        val size = ((if (row.iconSize > 0) row.iconSize else defaultDp) * density).toInt()
        val drawable = loaded.mutate()
        drawable.setBounds(0, 0, size, size)
        return drawable
    }

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
