package com.omarea.krscript.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.ReplacementSpan

// Span cho `code` inline trong Markdown - tự vẽ nền bo góc ôm sát chữ.
// Vì là ReplacementSpan nên Android giao toàn quyền vẽ đoạn text này cho draw() bên dưới,
// bỏ qua mọi CharacterStyle khác (màu {..}(color), link [..](url), bold/italic/gạch ngang...)
// nằm lồng bên trong cùng phạm vi ký tự - nếu vẽ phẳng 1 màu như trước thì các span lồng bên
// trong (do MarkdownInlineHelper tạo ra khi nesting, vd `{OK}(green)` hay `[text](url)`) sẽ
// không hiển thị. Vì vậy draw() bên dưới tự đọc lại các CharacterStyle lồng trong [start, end)
// từ chính Spanned rồi vẽ theo từng đoạn con đúng theo span của đoạn đó, thay vì vẽ 1 màu duy nhất.
class MarkdownCodeSpan(
    context: Context,
    private val backgroundColor: Int = 0x44808080,
    cornerRadiusDp: Float = 6f,
    paddingHorizontalDp: Float = 4f,
    private val paddingVerticalDp: Float = 2f // Padding dọc để ôm sát chữ vừa vặn
) : ReplacementSpan() {

    private val density = context.resources.displayMetrics.density
    private val cornerRadiusPx = cornerRadiusDp * density
    private val paddingHorizontalPx = paddingHorizontalDp * density
    private val paddingVerticalPx = paddingVerticalDp * density

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        // Cập nhật FontMetrics để TextView tự động chừa khoảng trống dọc, tránh bị cắt (clip)
        if (fm != null) {
            val fontMetrics = paint.fontMetricsInt
            fm.ascent = fontMetrics.ascent - paddingVerticalPx.toInt()
            fm.descent = fontMetrics.descent + paddingVerticalPx.toInt()
            fm.top = fontMetrics.top - paddingVerticalPx.toInt()
            fm.bottom = fontMetrics.bottom + paddingVerticalPx.toInt()
        }
        return (paint.measureText(text, start, end) + paddingHorizontalPx * 2).toInt()
    }

    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val textWidth = paint.measureText(text, start, end)
        val fontMetrics = paint.fontMetricsInt

        // Tính toán tọa độ vẽ nền ôm sát chữ dựa trên baseline (y)
        val rectTop = y.toFloat() + fontMetrics.ascent - paddingVerticalPx
        val rectBottom = y.toFloat() + fontMetrics.descent + paddingVerticalPx

        val oldColor = paint.color
        val oldStyle = paint.style

        paint.style = Paint.Style.FILL
        paint.color = backgroundColor

        // Vẽ hình chữ nhật bo góc ôm gọn lấy chữ
        canvas.drawRoundRect(
            RectF(x, rectTop, x + textWidth + paddingHorizontalPx * 2, rectBottom),
            cornerRadiusPx, cornerRadiusPx, paint
        )

        paint.style = oldStyle
        paint.color = oldColor

        drawInnerText(canvas, text, start, end, x + paddingHorizontalPx, y.toFloat(), paint)
    }

    // Vẽ chữ bên trong code, chia thành từng đoạn con theo ranh giới của các CharacterStyle
    // lồng bên trong [start, end) (màu, link, bold/italic, gạch ngang...) rồi áp span tương ứng
    // cho từng đoạn qua updateDrawState() - thay vì vẽ nguyên khối bằng 1 màu như bản cũ.
    private fun drawInnerText(canvas: Canvas, text: CharSequence, start: Int, end: Int, startX: Float, baselineY: Float, basePaint: Paint) {
        val spanned = text as? Spanned
        if (spanned == null) {
            canvas.drawText(text, start, end, startX, baselineY, basePaint)
            return
        }

        val boundaries = sortedSetOf(start, end)
        for (span in spanned.getSpans(start, end, CharacterStyle::class.java)) {
            boundaries.add(spanned.getSpanStart(span).coerceIn(start, end))
            boundaries.add(spanned.getSpanEnd(span).coerceIn(start, end))
        }
        val cuts = boundaries.toList()

        var drawX = startX
        val workPaint = TextPaint(basePaint)
        for (i in 0 until cuts.size - 1) {
            val segStart = cuts[i]
            val segEnd = cuts[i + 1]
            if (segEnd <= segStart) continue

            workPaint.set(basePaint)
            for (span in spanned.getSpans(segStart, segEnd, CharacterStyle::class.java)) {
                span.updateDrawState(workPaint)
            }
            canvas.drawText(text, segStart, segEnd, drawX, baselineY, workPaint)
            drawX += workPaint.measureText(text, segStart, segEnd)
        }
    }
}
