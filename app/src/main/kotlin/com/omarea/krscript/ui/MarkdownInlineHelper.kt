package com.omarea.krscript.ui

// Parser Markdown inline đơn giản cho nội dung 1 dòng (text.rows) - KHÔNG hỗ trợ block-level
// (heading, list, blockquote, ...) vì rows vốn đã được cấu hình theo dòng riêng qua TOML.
// Hỗ trợ: **bold**/__bold__, *italic*/_italic_, ~~strikethrough~~, `code`, [text](url),
// escape bằng dấu \ (vd: \* hiển thị dấu * thường). Cho phép lồng nhau 1 cấp (vd:
// **bold *italic* bold**) nhờ đệ quy vào phần nội dung bên trong mỗi cặp dấu (trừ code).
object MarkdownInlineHelper {

    enum class MarkdownSpanType { BOLD, ITALIC, STRIKETHROUGH, CODE, LINK }

    data class MarkdownSpanInfo(
        val start: Int,
        val end: Int,
        val type: MarkdownSpanType,
        val href: String = ""
    )

    private const val ESCAPABLE = "\\`*_{}[]()#+-.!~"

    // Trả về (text thuần đã bỏ hết ký hiệu markdown, danh sách span cần áp - toạ độ tính theo
    // text thuần trả về).
    fun parse(raw: String): Pair<String, List<MarkdownSpanInfo>> {
        val output = StringBuilder()
        val spans = ArrayList<MarkdownSpanInfo>()
        parseInto(raw, output, spans)
        return output.toString() to spans
    }

    private fun parseInto(raw: String, output: StringBuilder, spans: MutableList<MarkdownSpanInfo>) {
        var i = 0
        val n = raw.length
        while (i < n) {
            val c = raw[i]

            // Escape: \x -> hiển thị x, không diễn giải markdown
            if (c == '\\' && i + 1 < n && ESCAPABLE.indexOf(raw[i + 1]) >= 0) {
                output.append(raw[i + 1])
                i += 2
                continue
            }

            // Code: `...` - không đệ quy vào bên trong (giữ nguyên literal)
            if (c == '`') {
                val end = raw.indexOf('`', i + 1)
                if (end > i) {
                    val inner = raw.substring(i + 1, end)
                    val start = output.length
                    output.append(inner)
                    if (output.length > start) {
                        spans.add(MarkdownSpanInfo(start, output.length, MarkdownSpanType.CODE))
                    }
                    i = end + 1
                    continue
                }
            }

            // Bold: **...** hoặc __...__
            if ((c == '*' || c == '_') && i + 1 < n && raw[i + 1] == c) {
                val marker = raw.substring(i, i + 2)
                val end = raw.indexOf(marker, i + 2)
                if (end > i) {
                    val inner = raw.substring(i + 2, end)
                    val start = output.length
                    parseInto(inner, output, spans)
                    if (output.length > start) {
                        spans.add(MarkdownSpanInfo(start, output.length, MarkdownSpanType.BOLD))
                    }
                    i = end + 2
                    continue
                }
            }

            // Strikethrough: ~~...~~
            if (c == '~' && i + 1 < n && raw[i + 1] == '~') {
                val end = raw.indexOf("~~", i + 2)
                if (end > i) {
                    val inner = raw.substring(i + 2, end)
                    val start = output.length
                    parseInto(inner, output, spans)
                    if (output.length > start) {
                        spans.add(MarkdownSpanInfo(start, output.length, MarkdownSpanType.STRIKETHROUGH))
                    }
                    i = end + 2
                    continue
                }
            }

            // Italic: *...* hoặc _..._ (1 dấu)
            if (c == '*' || c == '_') {
                val end = raw.indexOf(c, i + 1)
                if (end > i + 1) {
                    val inner = raw.substring(i + 1, end)
                    val start = output.length
                    parseInto(inner, output, spans)
                    if (output.length > start) {
                        spans.add(MarkdownSpanInfo(start, output.length, MarkdownSpanType.ITALIC))
                    }
                    i = end + 1
                    continue
                }
            }

            // Link: [text](url)
            if (c == '[') {
                val closeBracket = raw.indexOf(']', i + 1)
                if (closeBracket > i && closeBracket + 1 < n && raw[closeBracket + 1] == '(') {
                    val closeParen = raw.indexOf(')', closeBracket + 2)
                    if (closeParen > closeBracket) {
                        val label = raw.substring(i + 1, closeBracket)
                        val href = raw.substring(closeBracket + 2, closeParen).trim()
                        val start = output.length
                        parseInto(label, output, spans)
                        if (output.length > start && href.isNotEmpty()) {
                            spans.add(MarkdownSpanInfo(start, output.length, MarkdownSpanType.LINK, href))
                        }
                        i = closeParen + 1
                        continue
                    }
                }
            }

            output.append(c)
            i += 1
        }
    }
}
