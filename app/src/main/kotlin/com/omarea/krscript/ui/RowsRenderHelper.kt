package com.omarea.krscript.ui

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.Drawable
import android.os.Build
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

object RowsRenderHelper {

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

        rowsView.text = ""
        @Suppress("UNCHECKED_CAST")
        (rowsView.tag as? MutableList<Animatable>)?.forEach { it.stop() }
        val animatedRowIcons = ArrayList<Animatable>()
        (rowsView.getTag(R.id.kr_rows_refresh_runnable) as? Runnable)?.let { rowsView.removeCallbacks(it) }
        rowsView.movementMethod = BoundedLinkMovementMethod.instance
        rowsView.visibility = View.VISIBLE

        rowsView.isClickable = true
        rowsView.setOnClickListener { }

        var needsRebindAfterLayout = false
        var hasContent = false

        var previousHadMarginBottom = false

        var groupStart = 0
        var groupAlign = Layout.Alignment.ALIGN_NORMAL
        var groupContentWidth = 0f

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

        val pinnedRightIndices = computePinnedRightIndices(rows)

        for ((rowIndex, row) in rows.withIndex()) {
            val isToggle = row.toggle == "checkbox" || row.toggle == "switch"
            val isPinnedRight = pinnedRightIndices.contains(rowIndex)

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

            val startsNewGroup = row.line || row.marginTop > 0 || row.breakRow || previousHadMarginBottom || !hasContent
            if (startsNewGroup) {
                finalizeGroup(rowsView.length())
                if (hasContent && !row.line) {
                    rowsView.append("\n")
                }
                groupStart = rowsView.length()
                groupAlign = row.align
                groupContentWidth = 0f
            } else if (!isPinnedRight) {
                if (groupAlign == Layout.Alignment.ALIGN_NORMAL && row.align != Layout.Alignment.ALIGN_NORMAL) {
                    groupAlign = row.align
                }
                if (groupContentWidth > 0f) {
                    val gap = " "
                    rowsView.append(gap)
                    groupContentWidth += rowsView.paint.measureText(gap)
                }
            }

            if (isPinnedRight) {
                val pinnedLabel = if (row.dynamicTextSh.isNotEmpty()) (dynamicTextResults[rowIndex] ?: "") else row.text
                if (pinnedLabel.isNotEmpty()) {
                    val pinnedPaint = measurePaintForRow(rowsView.paint, row)
                    var pinnedColor = if (row.color != -1) row.color else rowsView.currentTextColor
                    if (row.alpha in 0f..1f) {
                        val a = (row.alpha * 255).toInt().coerceIn(0, 255)
                        pinnedColor = (pinnedColor and 0x00FFFFFF) or (a shl 24)
                    }
                    val availableWidth = rowsView.width - rowsView.paddingLeft - rowsView.paddingRight
                    val pinnedSpannable: SpannableString
                    val labelStart: Int
                    if (availableWidth <= 0) {
                        needsRebindAfterLayout = true
                        pinnedSpannable = SpannableString(pinnedLabel)
                        labelStart = 0
                    } else {
                        val rightWidth = pinnedPaint.measureText(pinnedLabel)
                        val spacerWidth = (availableWidth - groupContentWidth - rightWidth).toInt().coerceAtLeast(0)
                        pinnedSpannable = SpannableString("\u200B$pinnedLabel")
                        pinnedSpannable.setSpan(SpacerSpan(spacerWidth), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        labelStart = 1
                    }
                    pinnedSpannable.setSpan(ForegroundColorSpan(pinnedColor), labelStart, pinnedSpannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (row.bold && row.italic) {
                        pinnedSpannable.setSpan(StyleSpan(Typeface.BOLD_ITALIC), labelStart, pinnedSpannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    } else if (row.bold) {
                        pinnedSpannable.setSpan(StyleSpan(Typeface.BOLD), labelStart, pinnedSpannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    } else if (row.italic) {
                        pinnedSpannable.setSpan(StyleSpan(Typeface.ITALIC), labelStart, pinnedSpannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    if (row.monospace) {
                        @Suppress("DEPRECATION")
                        pinnedSpannable.setSpan(TypefaceSpan("monospace"), labelStart, pinnedSpannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    if (row.size != -1) {
                        pinnedSpannable.setSpan(AbsoluteSizeSpan(row.size, true), labelStart, pinnedSpannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    if (row.letterSpacing != 0f) {
                        pinnedSpannable.setSpan(LetterSpacingSpan(row.letterSpacing), labelStart, pinnedSpannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    rowsView.append(pinnedSpannable)
                    hasContent = true
                }
                previousHadMarginBottom = true
                continue
            }
            previousHadMarginBottom = false
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

            val text = when {
                isToggle -> "$label \u2002 "
                showIcon && row.iconPosition == "before" -> "\u2002 $label"
                showIcon -> "$label \u2002"
                else -> label
            }
            val length = text.length
            val spannableString = SpannableString(text)
            val markdownOffset = if (showIcon && row.iconPosition == "before") 2 else 0

            var toggleDrawable: Drawable? = null
            if (isToggle) {
                val iconIndex = length - 2
                toggleDrawable = buildToggleDrawable(context, row)
                spannableString.setSpan(VerticalCenterImageSpan(toggleDrawable), iconIndex, iconIndex + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            var rowIconDrawable: Drawable? = null
            if (showIcon && rowIconDrawableRaw != null) {
                rowIconDrawable = rowIconDrawableRaw
                val iconIndex = if (row.iconPosition == "before") 0 else length - 1
                spannableString.setSpan(VerticalCenterImageSpan(rowIconDrawable), iconIndex, iconIndex + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                val isRealGifDrawable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && rowIconDrawable is AnimatedImageDrawable
                if (rowIconDrawable is AnimationDrawable || isRealGifDrawable) {
                    GifPlaybackHelper.bindToTextView(rowsView, rowIconDrawable, row.iconGifAutoplay, row.iconGifLoopCount)
                    animatedRowIcons.add(rowIconDrawable as Animatable)
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

            if (!isToggle && row.onClickScript.isNotEmpty()) {
                spannableString.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        runRowAction(context, row.confirm) {
                            val result = ScriptEnvironmen.executeResultRoot(context, row.onClickScript, config)
                            if (result.trim().isNotEmpty()) {
                                DialogHelper.helpInfo(context, context.getString(R.string.kr_slice_script_result), result)
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

            rowsView.append(spannableString)
            hasContent = true

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

            if (row.marginBottom > 0) {
                previousHadMarginBottom = true
            }
        }

        finalizeGroup(rowsView.length())

        rowsView.isHapticFeedbackEnabled = false
        rowsView.setOnLongClickListener {
            true
        }
        rowsView.tag = animatedRowIcons

        if (needsRebindAfterLayout) {
            rowsView.post {
                if (rowsView.width > 0) {
                    bind(context, rowsView, extraIconView, rows, config, htmlContainer)
                }
            }
        }

        val minRefreshInterval = rows.filter { it.refreshInterval > 0 }.minOfOrNull { it.refreshInterval }
        if (minRefreshInterval != null) {
            val refreshRunnable = Runnable {
                if (rowsView.isAttachedToWindow) {
                    bind(context, rowsView, extraIconView, rows, config, htmlContainer)
                }
            }
            rowsView.setTag(R.id.kr_rows_refresh_runnable, refreshRunnable)
            rowsView.postDelayed(refreshRunnable, minRefreshInterval * 1000L)
        } else {
            rowsView.setTag(R.id.kr_rows_refresh_runnable, null)
        }
    }

    private fun runRowAction(context: Context, confirmMessage: String, action: () -> Unit) {
        if (confirmMessage.isEmpty()) {
            action()
        } else {
            DialogHelper.confirm(context, message = confirmMessage, onConfirm = Runnable { action() })
        }
    }

    private fun computePinnedRightIndices(rows: List<TextNode.TextRow>): Set<Int> {
        val result = HashSet<Int>()
        for (i in 0 until rows.size - 1) {
            val left = rows[i]
            val right = rows[i + 1]
            val joinsDefault = !right.line && right.marginTop <= 0 && !right.breakRow && left.marginBottom <= 0 && !result.contains(i)
            if (left.align == Layout.Alignment.ALIGN_NORMAL &&
                right.align == Layout.Alignment.ALIGN_OPPOSITE &&
                joinsDefault &&
                right.toggle.isEmpty() &&
                right.icon.isEmpty() &&
                right.iconSh.isEmpty() &&
                right.photo.isEmpty() &&
                right.photoSh.isEmpty() &&
                !right.markdown &&
                right.link.isEmpty() &&
                right.activity.isEmpty() &&
                right.onClickScript.isEmpty() &&
                !right.underline &&
                !right.strikethrough
            ) {
                result.add(i + 1)
            }
        }
        return result
    }

    private class SpacerSpan(private val width: Int) : ReplacementSpan() {
        override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int = width

        override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
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

    private class VerticalSpaceSpan(private val heightPx: Int) : LineHeightSpan {
        override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, lineHeight: Int, fm: Paint.FontMetricsInt) {
            fm.ascent = -heightPx
            fm.top = fm.ascent
            fm.descent = 0
            fm.bottom = fm.descent
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
